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
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;

import java.util.Locale;

/**
 * <p>Enables <a href="https://tools.ietf.org/html/rfc3546#section-3.1">SNI
 * (Server Name Indication)</a> extension for server side SSL. For clients
 * support SNI, the server could have multiple host name bound on a single IP.
 * The client will send host name in the handshake data so server could decide
 * which certificate to choose for the host name.</p>
 */
public abstract class AbstractSniHandler<T> extends SslClientHelloHandler<T> {

    private static String extractSniHostname(ByteBuf in) {
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

            final int cipherSuitesLength = in.getUnsignedShort(offset);
            offset += cipherSuitesLength + 2;

            final int compressionMethodLength = in.getUnsignedByte(offset);
            offset += compressionMethodLength + 1;

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
                        break;
                    }

                    // SNI
                    // See https://tools.ietf.org/html/rfc6066#page-6
                    if (extensionType == 0) {
                        offset += 2;
                        if (extensionsLimit - offset < 3) {
                            break;
                        }

                        final int serverNameType = in.getUnsignedByte(offset);
                        offset++;

                        if (serverNameType == 0) {
                            final int serverNameLength = in.getUnsignedShort(offset);
                            offset += 2;

                            if (extensionsLimit - offset < serverNameLength) {
                                break;
                            }

                            final String hostname = in.toString(offset, serverNameLength, CharsetUtil.US_ASCII);
                            return hostname.toLowerCase(Locale.US);
                        } else {
                            // invalid enum value
                            break;
                        }
                    }

                    offset += extensionLength;
                }
            }
        }
        return null;
    }

    private String hostname;

    @Override
    protected Future<T> lookup(ChannelHandlerContext ctx, ByteBuf clientHello) throws Exception {
        hostname = clientHello == null ? null : extractSniHostname(clientHello);

        return lookup(ctx, hostname);
    }

    @Override
    protected void onLookupComplete(ChannelHandlerContext ctx, Future<T> future) throws Exception {
        fireSniCompletionEvent(ctx, hostname, future);
        onLookupComplete(ctx, hostname, future);
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

    private void fireSniCompletionEvent(ChannelHandlerContext ctx, String hostname, Future<T> future) {
        Throwable cause = future.cause();
        if (cause == null) {
            ctx.fireUserEventTriggered(new SniCompletionEvent(hostname));
        } else {
            ctx.fireUserEventTriggered(new SniCompletionEvent(hostname, cause));
        }
    }
}
