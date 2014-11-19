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
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.net.IDN;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SniSslHandler enables <a href="https://tools.ietf.org/html/rfc3546#section-3.1">
 * SNI, Server Name Indication</a> extension for server side SSL. For clients
 * support SNI, the server could have multiple host name bound on a single IP.
 * The client will send host name in the handshake data so server could decide
 * which certificate to choose for the host name.
 *
 * DNS wildcard is supported as hostname, so you can use <code>*.netty.io</code> to match both <code>netty.io</code>
 * and <code>downloads.netty.io</code>.
 */
public class SniSslHandler extends SslHandler {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SniSslHandler.class);

    private static final Pattern DNS_WILDCARD_PATTERN = Pattern.compile("^\\*\\..*");

    private final Map<String, SSLEngine> engines;
    private String hostname;
    private boolean handshaken;
    private SSLEngine selectedEngine;

    /**
     * SniSslHandler builder. Use this class to generate SniSslHandler for each pipeline.
     */
    public static class Builder {

        private final Map<String, SSLEngine> userProvidedEngines;

        private SSLEngine defaultEngine;

        /**
         * Create a default, order-sensitive builder. If your hostnames are in conflict, the handler
         * will choose the one you add first.
         */
        public Builder() {
            this(4);
        }

        /**
         * Create a default, order-sensitive builder. If your hostnames are in conflict, the handler
         * will choose the one you add first.
         *
         * @param initialSize initial size for internal map
         */
        public Builder(int initialSize) {
            userProvidedEngines = new LinkedHashMap<String, SSLEngine>(initialSize);
        }

        /**
         * Create the builder with predefined certificates.
         *
         * @param userMap a map of configured ssl engines
         */
        public Builder(Map<String, SSLEngine> userMap) {
            if (userMap == null) {
                throw new NullPointerException("userMap is null");
            }
            userProvidedEngines = new LinkedHashMap<String, SSLEngine>(userMap.size());
            for (Map.Entry<String, SSLEngine> entry : userMap.entrySet()) {
                addEngine(entry.getKey(), entry.getValue());
            }
        }

        /**
         * Add a certificate to the handler.
         *
         * <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a> is supported as hostname, you can
         * use "*.netty.io" to match "netty.io" and "downloads.netty.io".
         *
         * @param hostname hostname for the certificate.
         * @param engine
         */
        public Builder addEngine(String hostname, SSLEngine engine) {
            if (hostname == null) {
                throw new NullPointerException("hostname is null");
            }

            if (engine == null) {
                throw new NullPointerException("engine is null");
            }

            userProvidedEngines.put(normalizeHostname(hostname), engine);
            return this;
        }

        public Builder defaultEngine(SSLEngine engine) {
            if (engine == null) {
                throw new NullPointerException("default engine is null");
            }
            defaultEngine = engine;
            return this;
        }

        public SniSslHandler build() {
             return new SniSslHandler(userProvidedEngines, defaultEngine);
        }
    }

    /**
     * IDNA ASCII conversion and case normalization
     */
    private static String normalizeHostname(String hostname) {
        return IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED).toLowerCase();
    }

    /**
     * Create a SNI enabled handler with a set of SSLEngines.
     *
     * <p>Currently we are matching host name by iterating the map. If you need order-sensitive matching, you can use
     * LinkedHashMap here. </p>
     *
     * @param engines       hostname:SSLEngine map with DNS wildcard support
     * @param defaultEngine default SSLEngine when the client doesn't support SNI
     */
    SniSslHandler(Map<String, SSLEngine> engines, SSLEngine defaultEngine) {
        super(defaultEngine);
        if (engines == null) {
            throw new NullPointerException("engines");
        }
        this.engines = engines;

        handshaken = false;
    }

    /**
     * <p>Simple function to match <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a>.
     * </p>
     *
     * <p><code>*.netty.io</code> will match both <code>netty.io</code> and <code>downloads.netty.io</code></p>
     */
    private static boolean matches(String hostNameTemplate, String hostName) {
        // note that inputs are converted and lowercased already
        if (DNS_WILDCARD_PATTERN.matcher(hostNameTemplate).matches()) {
            return hostNameTemplate.substring(2).equals(hostName) ||
                    hostName.endsWith(hostNameTemplate.substring(1));
        } else {
            return hostNameTemplate.equals(hostName);
        }
    }

    /**
     * @return the selected hostname
     */
    public String hostname() {
        return hostname;
    }

    @Override
    public SSLEngine engine() {
        // use default engine when SNI is not available
        return selectedEngine != null ? selectedEngine : super.engine();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws SSLException {
        if (!handshaken && in.readableBytes() > 5) {
            String hostname = getSNIHostNameFromHandshakeInfo(in);

            if (hostname != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Using hostname: {}", hostname);
                }

                // toASCII conversion and case normalization
                hostname = normalizeHostname(hostname);
                this.hostname = hostname;

                for (Map.Entry<String, SSLEngine> entry : engines.entrySet()) {
                    if (matches(entry.getKey(), hostname)) {
                        selectedEngine = entry.getValue();
                        break;
                    }
                }
            }
        }

        super.decode(ctx, in, out);
    }

    private String getSNIHostNameFromHandshakeInfo(ByteBuf in) {
        try {
            int command = in.getUnsignedByte(0);

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

            int majorVersion = in.getUnsignedByte(1);

            // SSLv3 or TLS
            if (majorVersion == 3) {

                int packetLength = in.getUnsignedShort(3) + 5;

                if (in.readableBytes() >= packetLength) {
                    // decode the ssl client hello packet
                    // we have to skip some var-length fields
                    int offset = 43;

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
