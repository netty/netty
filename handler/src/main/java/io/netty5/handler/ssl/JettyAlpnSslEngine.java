/*
 * Copyright 2014 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty5.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelectionListener;
import io.netty5.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelector;
import org.eclipse.jetty.alpn.ALPN;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.util.LinkedHashSet;
import java.util.List;

import static io.netty5.handler.ssl.SslUtils.toSSLHandshakeException;
import static java.util.Objects.requireNonNull;

@Deprecated(forRemoval = true)
abstract class JettyAlpnSslEngine extends JdkSslEngine {
    private static final boolean available = initAvailable();

    static boolean isAvailable() {
        return available;
    }

    private static boolean initAvailable() {
        return false;
    }

    static JettyAlpnSslEngine newClientEngine(SSLEngine engine,
            JdkApplicationProtocolNegotiator applicationNegotiator) {
        return new ClientEngine(engine, applicationNegotiator);
    }

    static JettyAlpnSslEngine newServerEngine(SSLEngine engine,
            JdkApplicationProtocolNegotiator applicationNegotiator) {
        return new ServerEngine(engine, applicationNegotiator);
    }

    private JettyAlpnSslEngine(SSLEngine engine) {
        super(engine);
    }

    private static final class ClientEngine extends JettyAlpnSslEngine {
        ClientEngine(SSLEngine engine, final JdkApplicationProtocolNegotiator applicationNegotiator) {
            super(engine);
            requireNonNull(applicationNegotiator, "applicationNegotiator");
            final ProtocolSelectionListener protocolListener = requireNonNull(applicationNegotiator
                            .protocolListenerFactory().newListener(this, applicationNegotiator.protocols()),
                    "protocolListener");
            ALPN.put(engine, new ALPN.ClientProvider() {
                @Override
                public List<String> protocols() {
                    return applicationNegotiator.protocols();
                }

                @Override
                public void selected(String protocol) throws SSLException {
                    try {
                        protocolListener.selected(protocol);
                    } catch (Throwable t) {
                        throw toSSLHandshakeException(t);
                    }
                }

                @Override
                public void unsupported() {
                    protocolListener.unsupported();
                }
            });
        }

        @Override
        public void closeInbound() throws SSLException {
            try {
                ALPN.remove(getWrappedEngine());
            } finally {
                super.closeInbound();
            }
        }

        @Override
        public void closeOutbound() {
            try {
                ALPN.remove(getWrappedEngine());
            } finally {
                super.closeOutbound();
            }
        }
    }

    private static final class ServerEngine extends JettyAlpnSslEngine {
        ServerEngine(SSLEngine engine, final JdkApplicationProtocolNegotiator applicationNegotiator) {
            super(engine);
            requireNonNull(applicationNegotiator, "applicationNegotiator");
            final ProtocolSelector protocolSelector = requireNonNull(applicationNegotiator.protocolSelectorFactory()
                            .newSelector(this, new LinkedHashSet<>(applicationNegotiator.protocols())),
                    "protocolSelector");
            ALPN.put(engine, new ALPN.ServerProvider() {
                @Override
                public String select(List<String> protocols) throws SSLException {
                    try {
                        return protocolSelector.select(protocols);
                    } catch (Throwable t) {
                        throw toSSLHandshakeException(t);
                    }
                }

                @Override
                public void unsupported() {
                    protocolSelector.unsupported();
                }
            });
        }

        @Override
        public void closeInbound() throws SSLException {
            try {
                ALPN.remove(getWrappedEngine());
            } finally {
                super.closeInbound();
            }
        }

        @Override
        public void closeOutbound() {
            try {
                ALPN.remove(getWrappedEngine());
            } finally {
                super.closeOutbound();
            }
        }
    }
}
