/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.List;
import java.util.Locale;

/**
 * <p>Enables <a href="https://tools.ietf.org/html/rfc3546#section-3.1">SNI
 * (Server Name Indication)</a> extension for server side SSL. For clients
 * support SNI, the server could have multiple host name bound on a single IP.
 * The client will send host name in the handshake data so server could decide
 * which certificate to choose for the host name.</p>
 */
public abstract class AbstractSniHandler<T> extends ByteToMessageDecoder implements ChannelOutboundHandler {

    // Maximal number of ssl records to inspect before fallback to the default SslContext.
    private static final int MAX_SSL_RECORDS = 4;

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractSniHandler.class);

    private boolean handshakeFailed;
    private boolean suppressRead;
    private boolean readPending;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!suppressRead && !handshakeFailed) {
            final int writerIndex = in.writerIndex();
            try {
                loop:
                for (int i = 0; i < MAX_SSL_RECORDS; i++) {
                    final int readerIndex = in.readerIndex();
                    final int readableBytes = writerIndex - readerIndex;
                    if (readableBytes < SslUtils.SSL_RECORD_HEADER_LENGTH) {
                        // Not enough data to determine the record type and length.
                        return;
                    }

                    final int command = in.getUnsignedByte(readerIndex);

                    // tls, but not handshake command
                    switch (command) {
                        case SslUtils.SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
                        case SslUtils.SSL_CONTENT_TYPE_ALERT:
                            final int len = SslUtils.getEncryptedPacketLength(in, readerIndex);

                            // Not an SSL/TLS packet
                            if (len == SslUtils.NOT_ENCRYPTED) {
                                handshakeFailed = true;
                                NotSslRecordException e = new NotSslRecordException(
                                        "not an SSL/TLS record: " + ByteBufUtil.hexDump(in));
                                in.skipBytes(in.readableBytes());
                                ctx.fireUserEventTriggered(new SniCompletionEvent(e));
                                SslUtils.handleHandshakeFailure(ctx, e, true);
                                throw e;
                            }
                            if (len == SslUtils.NOT_ENOUGH_DATA ||
                                    writerIndex - readerIndex - SslUtils.SSL_RECORD_HEADER_LENGTH < len) {
                                // Not enough data
                                return;
                            }
                            // increase readerIndex and try again.
                            in.skipBytes(len);
                            continue;
                        case SslUtils.SSL_CONTENT_TYPE_HANDSHAKE:
                            final int majorVersion = in.getUnsignedByte(readerIndex + 1);

                            // SSLv3 or TLS
                            if (majorVersion == 3) {
                                final int packetLength = in.getUnsignedShort(readerIndex + 3) +
                                        SslUtils.SSL_RECORD_HEADER_LENGTH;

                                if (readableBytes < packetLength) {
                                    // client hello incomplete; try again to decode once more data is ready.
                                    return;
                                }

                                // See https://tools.ietf.org/html/rfc5246#section-7.4.1.2
                                //
                                // Decode the ssl client hello packet.
                                // We have to skip bytes until SessionID (which sum to 43 bytes).
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

                                final int endOffset = readerIndex + packetLength;
                                int offset = readerIndex + 43;

                                if (endOffset - offset < 6) {
                                    break loop;
                                }

                                final int sessionIdLength = in.getUnsignedByte(offset);
                                offset += sessionIdLength + 1;

                                final int cipherSuitesLength = in.getUnsignedShort(offset);
                                offset += cipherSuitesLength + 2;

                                final int compressionMethodLength = in.getUnsignedByte(offset);
                                offset += compressionMethodLength + 1;

                                final int extensionsLength = in.getUnsignedShort(offset);
                                offset += 2;
                                final int extensionsLimit = offset + extensionsLength;

                                if (extensionsLimit > endOffset) {
                                    // Extensions should never exceed the record boundary.
                                    break loop;
                                }

                                for (;;) {
                                    if (extensionsLimit - offset < 4) {
                                        break loop;
                                    }

                                    final int extensionType = in.getUnsignedShort(offset);
                                    offset += 2;

                                    final int extensionLength = in.getUnsignedShort(offset);
                                    offset += 2;

                                    if (extensionsLimit - offset < extensionLength) {
                                        break loop;
                                    }

                                    // SNI
                                    // See https://tools.ietf.org/html/rfc6066#page-6
                                    if (extensionType == 0) {
                                        offset += 2;
                                        if (extensionsLimit - offset < 3) {
                                            break loop;
                                        }

                                        final int serverNameType = in.getUnsignedByte(offset);
                                        offset++;

                                        if (serverNameType == 0) {
                                            final int serverNameLength = in.getUnsignedShort(offset);
                                            offset += 2;

                                            if (extensionsLimit - offset < serverNameLength) {
                                                break loop;
                                            }

                                            final String hostname = in.toString(offset, serverNameLength,
                                                    CharsetUtil.US_ASCII);

                                            try {
                                                select(ctx, hostname.toLowerCase(Locale.US));
                                            } catch (Throwable t) {
                                                PlatformDependent.throwException(t);
                                            }
                                            return;
                                        } else {
                                            // invalid enum value
                                            break loop;
                                        }
                                    }

                                    offset += extensionLength;
                                }
                            }
                            // Fall-through
                        default:
                            //not tls, ssl or application data, do not try sni
                            break loop;
                    }
                }
            } catch (NotSslRecordException e) {
                // Just rethrow as in this case we also closed the channel and this is consistent with SslHandler.
                throw e;
            } catch (Exception e) {
                // unexpected encoding, ignore sni and use default
                if (logger.isDebugEnabled()) {
                    logger.debug("Unexpected client hello packet: " + ByteBufUtil.hexDump(in), e);
                }
            }
            // Just select the default SslContext
            select(ctx, null);
        }
    }

    private void select(final ChannelHandlerContext ctx, final String hostname) throws Exception {
        Future<T> future = lookup(ctx, hostname);
        if (future.isDone()) {
            fireSniCompletionEvent(ctx, hostname, future);
            onLookupComplete(ctx, hostname, future);
        } else {
            suppressRead = true;
            future.addListener(new FutureListener<T>() {
                @Override
                public void operationComplete(Future<T> future) throws Exception {
                    try {
                        suppressRead = false;
                        try {
                            fireSniCompletionEvent(ctx, hostname, future);
                            onLookupComplete(ctx, hostname, future);
                        } catch (DecoderException err) {
                            ctx.fireExceptionCaught(err);
                        } catch (Exception cause) {
                            ctx.fireExceptionCaught(new DecoderException(cause));
                        } catch (Throwable cause) {
                            ctx.fireExceptionCaught(cause);
                        }
                    } finally {
                        if (readPending) {
                            readPending = false;
                            ctx.read();
                        }
                    }
                }
            });
        }
    }

    private void fireSniCompletionEvent(ChannelHandlerContext ctx, String hostname, Future<T> future) {
        Throwable cause = future.cause();
        if (cause == null) {
            ctx.fireUserEventTriggered(new SniCompletionEvent(hostname));
        } else {
            ctx.fireUserEventTriggered(new SniCompletionEvent(hostname, cause));
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
                                             String hostname, Future<T> future) throws Exception;

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (suppressRead) {
            readPending = true;
        } else {
            ctx.read();
        }
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
