/*
 * Copyright 2014 The Netty Project
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
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.DomainNameMapping;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.IDN;
import java.util.List;
import java.util.Locale;

/**
 * <p>Enables <a href="https://tools.ietf.org/html/rfc3546#section-3.1">SNI
 * (Server Name Indication)</a> extension for server side SSL. For clients
 * support SNI, the server could have multiple host name bound on a single IP.
 * The client will send host name in the handshake data so server could decide
 * which certificate to choose for the host name. </p>
 */
public class SniHandler extends ByteToMessageDecoder {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SniHandler.class);

    private final DomainNameMapping<SslContext> mapping;

    private boolean handshaken;
    private volatile String hostname;
    private volatile SslContext selectedContext;

    /**
     * Create a SNI detection handler with configured {@link SslContext}
     * maintained by {@link DomainNameMapping}
     *
     * @param mapping the mapping of domain name to {@link SslContext}
     */
    @SuppressWarnings("unchecked")
    public SniHandler(DomainNameMapping<? extends SslContext> mapping) {
        if (mapping == null) {
            throw new NullPointerException("mapping");
        }

        this.mapping = (DomainNameMapping<SslContext>) mapping;
        handshaken = false;
    }

    /**
     * @return the selected hostname
     */
    public String hostname() {
        return hostname;
    }

    /**
     * @return the selected sslcontext
     */
    public SslContext sslContext() {
        return selectedContext;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!handshaken && in.readableBytes() >= 5) {
            String hostname = sniHostNameFromHandshakeInfo(in);
            if (hostname != null) {
                hostname = IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.US);
            }
            this.hostname = hostname;

            // the mapping will return default context when this.hostname is null
            selectedContext = mapping.map(hostname);
        }

        if (handshaken) {
            SslHandler sslHandler = selectedContext.newHandler(ctx.alloc());
            ctx.pipeline().replace(this, SslHandler.class.getName(), sslHandler);
        }
    }

    private String sniHostNameFromHandshakeInfo(ByteBuf in) {
        int readerIndex = in.readerIndex();
        try {
            int command = in.getUnsignedByte(readerIndex);

            // tls, but not handshake command
            switch (command) {
                case SslConstants.SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
                case SslConstants.SSL_CONTENT_TYPE_ALERT:
                case SslConstants.SSL_CONTENT_TYPE_APPLICATION_DATA:
                    return null;
                case SslConstants.SSL_CONTENT_TYPE_HANDSHAKE:
                    break;
                default:
                    //not tls or sslv3, do not try sni
                    handshaken = true;
                    return null;
            }

            int majorVersion = in.getUnsignedByte(readerIndex + 1);

            // SSLv3 or TLS
            if (majorVersion == 3) {

                int packetLength = in.getUnsignedShort(readerIndex + 3) + 5;

                if (in.readableBytes() >= packetLength) {
                    // decode the ssl client hello packet
                    // we have to skip some var-length fields
                    int offset = readerIndex + 43;

                    int sessionIdLength = in.getUnsignedByte(offset);
                    offset += sessionIdLength + 1;

                    int cipherSuitesLength = in.getUnsignedShort(offset);
                    offset += cipherSuitesLength + 2;

                    int compressionMethodLength = in.getUnsignedByte(offset);
                    offset += compressionMethodLength + 1;

                    int extensionsLength = in.getUnsignedShort(offset);
                    offset += 2;
                    int extensionsLimit = offset + extensionsLength;

                    while (offset < extensionsLimit) {
                        int extensionType = in.getUnsignedShort(offset);
                        offset += 2;

                        int extensionLength = in.getUnsignedShort(offset);
                        offset += 2;

                        // SNI
                        if (extensionType == 0) {
                            handshaken = true;
                            int serverNameType = in.getUnsignedByte(offset + 2);
                            if (serverNameType == 0) {
                                int serverNameLength = in.getUnsignedShort(offset + 3);
                                return in.toString(offset + 5, serverNameLength,
                                        CharsetUtil.UTF_8);
                            } else {
                                // invalid enum value
                                return null;
                            }
                        }

                        offset += extensionLength;
                    }

                    handshaken = true;
                    return null;
                } else {
                    // client hello incomplete
                    return null;
                }
            } else {
                handshaken = true;
                return null;
            }
        } catch (Throwable e) {
            // unexpected encoding, ignore sni and use default
            if (logger.isDebugEnabled()) {
                logger.debug("Unexpected client hello packet: " + ByteBufUtil.hexDump(in), e);
            }
            handshaken = true;
            return null;
        }
    }
}
