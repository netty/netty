/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * <p>Enables <a href="https://tools.ietf.org/html/rfc3546#section-3.1">SNI
 * (Server Name Indication)</a> extension for server side SSL. For clients
 * support SNI, the server could have multiple host name bound on a single IP.
 * The client will send host name in the handshake data so server could decide
 * which certificate to choose for the host name.</p>
 */
public abstract class AbstractSniHandler<T> extends SslClientHelloHandler<T> {

    /**
     * Extracts the host name from the {@code server_name} extension of the given {@code ClientHello}.
     * <p>
     * Package-private for testing purposes.
     *
     * @return the lower-cased host name, or {@code null} if the {@code ClientHello} carries no usable
     *         {@code server_name} extension (e.g. because the extension is absent, it only holds name types other
     *         than {@code host_name}, or the {@code ClientHello} itself can not be parsed).
     * @throws NotSslRecordException if a {@code server_name} extension is present but malformed. Callers must
     *         fail closed in this case rather than fall back to a default as the {@code SSLEngine} may
     *         interpret the same bytes differently.
     */
    static String extractSniHostname(ByteBuf in) throws NotSslRecordException {
        // See https://tools.ietf.org/html/rfc5246#section-7.4.1.2
        //
        // Decode the ssl client hello packet.
        //
        // struct {
        //    ProtocolVersion client_version;
        //    Random random;
        //    SessionID session_id;
        //    CipherSuite cipher_suites<2..2^16-2>;
        //    CompressionMethod compression_methods<1..2^8-1>;
        //    select (extensions_present) {
        //        case false:
        //            struct {};
        //        case true:
        //            Extension extensions<0..2^16-1>;
        //    };
        // } ClientHello;
        //

        // We have to skip bytes until SessionID (which sum to 34 bytes in this case).
        int offset = in.readerIndex();
        int endOffset = in.writerIndex();
        offset += 34;

        if (endOffset - offset >= 6) {
            final int sessionIdLength = in.getUnsignedByte(offset);
            offset += sessionIdLength + 1;

            if (endOffset - offset >= 2) {
                final int cipherSuitesLength = in.getUnsignedShort(offset);
                offset += cipherSuitesLength + 2;

                if (endOffset - offset >= 1) {
                    final int compressionMethodLength = in.getUnsignedByte(offset);
                    offset += compressionMethodLength + 1;

                    if (endOffset - offset >= 2) {
                        final int extensionsLength = in.getUnsignedShort(offset);
                        offset += 2;
                        final int extensionsLimit = offset + extensionsLength;

                        // Extensions should never exceed the record boundary.
                        if (extensionsLimit <= endOffset) {
                            while (extensionsLimit - offset >= 4) {
                                final int extensionType = in.getUnsignedShort(offset);
                                offset += 2;

                                final int extensionLength = in.getUnsignedShort(offset);
                                offset += 2;

                                if (extensionsLimit - offset < extensionLength) {
                                    if (extensionType == 0) {
                                        throw malformedServerName("extension length exceeds the extensions");
                                    }
                                    break;
                                }

                                // SNI
                                // See https://tools.ietf.org/html/rfc6066#page-6
                                if (extensionType == 0) {
                                    // The ServerNameList must be fully contained in the extension data,
                                    // so all bounds checks below use the end of this extension rather
                                    // than the end of all extensions.
                                    final int extensionLimit = offset + extensionLength;
                                    if (extensionLength < 2) {
                                        throw malformedServerName("extension too short for the server_name_list");
                                    }
                                    final int serverNameListLength = in.getUnsignedShort(offset);
                                    offset += 2;
                                    // ServerName server_name_list<1..2^16-1>, which has to fill the extension data.
                                    // A ServerName is at least 3 bytes: name_type (1) + HostName length (2).
                                    if (serverNameListLength != extensionLength - 2 || serverNameListLength < 3) {
                                        throw malformedServerName("invalid server_name_list length " +
                                                serverNameListLength + " for extension length " + extensionLength);
                                    }

                                    // Walk the whole list like the JDK does, so we select based on the same host_name
                                    // as the SSLEngine will see.
                                    String hostname = null;
                                    // Name types other than host_name (0) seen so far, one bit per 8-bit name_type.
                                    long[] seenNameTypes = null;
                                    while (offset < extensionLimit) {
                                        // ServerName: name_type (1) + 16-bit length prefixed name. RFC 6066, Section 3
                                        // requires the data of future name types to begin with a 16-bit length too, so
                                        // unknown name types can be skipped.
                                        if (extensionLimit - offset < 3) {
                                            throw malformedServerName("truncated server_name entry");
                                        }
                                        final int serverNameType = in.getUnsignedByte(offset);
                                        final int serverNameLength = in.getUnsignedShort(offset + 1);
                                        offset += 3;

                                        if (extensionLimit - offset < serverNameLength) {
                                            throw malformedServerName("server_name entry length " + serverNameLength +
                                                    " exceeds the extension");
                                        }

                                        if (serverNameType == 0) {
                                            // HostName is opaque<1..2^16-1>, see RFC 6066, Section 3.
                                            if (serverNameLength == 0) {
                                                throw malformedServerName("empty host_name");
                                            }
                                            // RFC 6066, Section 3: no more than one name of the same name_type.
                                            if (hostname != null) {
                                                throw malformedServerName("duplicate host_name");
                                            }
                                            hostname = in.toString(offset, serverNameLength, CharsetUtil.US_ASCII);
                                        } else {
                                            // Like the JDK, reject a repeated name_type of any kind. The bitmap is
                                            // allocated lazily, so the common host_name-only case does not need it.
                                            if (seenNameTypes == null) {
                                                seenNameTypes = new long[4];
                                            }
                                            final int word = serverNameType >>> 6;
                                            final long bit = 1L << (serverNameType & 63);
                                            if ((seenNameTypes[word] & bit) != 0) {
                                                throw malformedServerName("duplicate name_type " + serverNameType);
                                            }
                                            seenNameTypes[word] |= bit;
                                        }
                                        offset += serverNameLength;
                                    }
                                    return hostname == null ? null : hostname.toLowerCase(Locale.US);
                                }

                                offset += extensionLength;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static NotSslRecordException malformedServerName(String reason) {
        return new NotSslRecordException("malformed server_name extension: " + reason);
    }

    static final long DEFAULT_HANDSHAKE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);
    protected final long handshakeTimeoutMillis;
    private ScheduledFuture<?> timeoutFuture;
    private String hostname;

    /**
     * @param handshakeTimeoutMillis    the handshake timeout in milliseconds
     */
    protected AbstractSniHandler(long handshakeTimeoutMillis) {
        this(DEFAULT_MAX_CLIENT_HELLO_LENGTH, handshakeTimeoutMillis);
    }

    /**
     * @param maxClientHelloLength     the maximum length of the client hello message.
     * @param handshakeTimeoutMillis    the handshake timeout in milliseconds
     */
    protected AbstractSniHandler(int maxClientHelloLength, long handshakeTimeoutMillis) {
        super(maxClientHelloLength);
        this.handshakeTimeoutMillis = checkPositiveOrZero(handshakeTimeoutMillis, "handshakeTimeoutMillis");
    }

    public AbstractSniHandler() {
        this(DEFAULT_MAX_CLIENT_HELLO_LENGTH, DEFAULT_HANDSHAKE_TIMEOUT_MILLIS);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            checkStartTimeout(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
        checkStartTimeout(ctx);
    }

    private void checkStartTimeout(final ChannelHandlerContext ctx) {
        if (handshakeTimeoutMillis <= 0 || timeoutFuture != null) {
            return;
        }
        timeoutFuture = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (ctx.channel().isActive()) {
                    SslHandshakeTimeoutException exception = new SslHandshakeTimeoutException(
                        "handshake timed out after " + handshakeTimeoutMillis + "ms");
                    ctx.fireUserEventTriggered(new SniCompletionEvent(exception));
                    ctx.close();
                }
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    protected Future<T> lookup(ChannelHandlerContext ctx, ByteBuf clientHello) throws Exception {
        hostname = clientHello == null ? null : extractSniHostname(clientHello);

        return lookup(ctx, hostname);
    }

    @Override
    protected void onLookupComplete(ChannelHandlerContext ctx, Future<? extends T> future) throws Exception {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }
        try {
            onLookupComplete(ctx, hostname, future);
        } finally {
            fireSniCompletionEvent(ctx, hostname, future);
        }
    }

    /**
     * Kicks off a lookup for the given SNI value and returns a {@link Future} which in turn will
     * notify the {@link #onLookupComplete(ChannelHandlerContext, String, Future)} on completion.
     *
     * @see #onLookupComplete(ChannelHandlerContext, String, Future)
     */
    protected abstract Future<T> lookup(ChannelHandlerContext ctx, String hostname) throws Exception;

    /**
     * Called upon completion of the {@link #lookup(ChannelHandlerContext, String)} {@link Future}.
     *
     * @see #lookup(ChannelHandlerContext, String)
     */
    protected abstract void onLookupComplete(ChannelHandlerContext ctx,
                                             String hostname, Future<? extends T> future) throws Exception;

    private static void fireSniCompletionEvent(ChannelHandlerContext ctx, String hostname, Future<?> future) {
        Throwable cause = future.cause();
        if (cause == null) {
            ctx.fireUserEventTriggered(new SniCompletionEvent(hostname));
        } else {
            ctx.fireUserEventTriggered(new SniCompletionEvent(hostname, cause));
        }
    }
}
