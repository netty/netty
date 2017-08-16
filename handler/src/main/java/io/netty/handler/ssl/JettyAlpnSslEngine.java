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

import static io.netty.handler.ssl.SslUtils.toSSLHandshakeException;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.util.List;

import org.eclipse.jetty.alpn.ALPN;

import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.EngineConfigurator;
import io.netty.util.internal.PlatformDependent;

final class JettyAlpnSslEngine extends JdkSslEngine {
    private static final boolean available = initAvailable();

    static boolean isAvailable() {
        return available;
    }

    private static boolean initAvailable() {
        if (PlatformDependent.javaVersion() <= 8) {
            try {
                // Always use bootstrap class loader.
                Class.forName("sun.security.ssl.ALPNExtension", true, null);
                return true;
            } catch (Throwable ignore) {
                // alpn-boot was not loaded.
            }
        }
        return false;
    }

    static JettyAlpnSslEngine newClientEngine(SSLEngine engine,
            final JdkApplicationProtocolNegotiator applicationNegotiator) {
        JettyAlpnSslEngine clientEngine = new JettyAlpnSslEngine(engine);
        final EngineConfigurator engineConfigurator = checkNotNull(applicationNegotiator, "applicationNegotiator")
                .newEngineConfigurator(clientEngine);
        ALPN.put(engine, new ALPN.ClientProvider() {
            @Override
            public List<String> protocols() {
                return applicationNegotiator.protocols();
            }

            @Override
            public void selected(String protocol) throws SSLException {
                try {
                    engineConfigurator.selected(protocol);
                } catch (Throwable t) {
                    throw toSSLHandshakeException(t);
                }
            }

            @Override
            public void unsupported() {
                engineConfigurator.unsupported();
            }
        });

        return clientEngine;
    }

    static JettyAlpnSslEngine newServerEngine(SSLEngine engine,
            JdkApplicationProtocolNegotiator applicationNegotiator) {
        JettyAlpnSslEngine serverEngine = new JettyAlpnSslEngine(engine);
        final EngineConfigurator engineConfigurator = checkNotNull(applicationNegotiator, "applicationNegotiator")
                .newEngineConfigurator(serverEngine);
        ALPN.put(engine, new ALPN.ServerProvider() {
            @Override
            public String select(List<String> protocols) throws SSLException {
                try {
                    return engineConfigurator.select(protocols);
                } catch (Throwable t) {
                    throw toSSLHandshakeException(t);
                }
            }

            @Override
            public void unsupported() {
                engineConfigurator.unsupported();
            }
        });

        return serverEngine;
    }

    private JettyAlpnSslEngine(SSLEngine engine) {
        super(engine);
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
