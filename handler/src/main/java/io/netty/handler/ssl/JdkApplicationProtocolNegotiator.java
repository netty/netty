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

import io.netty.buffer.ByteBufAllocator;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.util.List;

import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.util.internal.PlatformDependent;

/**
 * JDK extension methods to support {@link ApplicationProtocolNegotiator}
 */
class JdkApplicationProtocolNegotiator implements ApplicationProtocolNegotiator {
    private static final boolean ALPN_AVAILABLE = Conscrypt.isAvailable()
            || JettyAlpnSslEngine.isAvailable()
            || PlatformDependent.javaVersion() >= 9;
    private static final boolean NPN_AVAILABLE = JettyNpnSslEngine.isAvailable();
    static final JdkApplicationProtocolNegotiator DEFAULT =
            new JdkApplicationProtocolNegotiator(null, false, Protocol.NONE);

    private final List<String> supportedProtocols;
    private final boolean failIfNoCommonProtocols;
    private final Protocol protocol;

    JdkApplicationProtocolNegotiator(
        List<String> supportedProtocols,
        boolean failIfNoCommonProtocols,
        Protocol protocol
    ) {
        this.supportedProtocols = supportedProtocols;
        this.failIfNoCommonProtocols = failIfNoCommonProtocols;
        this.protocol = protocol;

        if (protocol == Protocol.ALPN && !ALPN_AVAILABLE) {
            throw new RuntimeException("ALPN unsupported. Is your classpath configured correctly?"
                    + " For Conscrypt, add the appropriate Conscrypt JAR to classpath and set the security provider."
                    + " For Jetty-ALPN, see "
                    + "http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-starting");
        }

        if (protocol == Protocol.NPN && !NPN_AVAILABLE) {
            throw new RuntimeException("NPN unsupported. Is your classpath configured correctly?"
                    + " See https://wiki.eclipse.org/Jetty/Feature/NPN");
        }
    }

    SSLEngine wrapSslEngine(SSLEngine engine, ByteBufAllocator alloc, boolean isServer) {
        switch (protocol) {
            case NONE:
                return engine;
            case ALPN:
                if (Conscrypt.isEngineSupported(engine)) {
                    return isServer ? ConscryptAlpnSslEngine.newServerEngine(engine, alloc, this)
                            : ConscryptAlpnSslEngine.newClientEngine(engine, alloc, this);
                }
                if (JettyAlpnSslEngine.isAvailable()) {
                    return isServer ? JettyAlpnSslEngine.newServerEngine(engine, this)
                            : JettyAlpnSslEngine.newClientEngine(engine, this);
                }
                if (Java9SslUtils.supportsAlpn(engine)) {
                    return Java9SslUtils.configureAlpn(engine, this, isServer, failIfNoCommonProtocols);
                }
                throw new RuntimeException("Unable to wrap SSLEngine of type " + engine.getClass().getName());
            case NPN:
                return new JettyNpnSslEngine(engine, this, isServer);
            default:
                throw new UnsupportedOperationException("Got unknown protocol " + protocol);
        }
    }

    @Override
    public List<String> protocols() {
        return supportedProtocols;
    }

    EngineConfigurator newEngineConfigurator(JdkSslEngine engine) {
        return new EngineConfigurator(engine);
    }

    class EngineConfigurator {
        private final JdkSslEngine engineWrapper;

        EngineConfigurator(JdkSslEngine engineWrapper) {
            this.engineWrapper = engineWrapper;
        }

        String select(List<String> protocols) throws Exception {
            for (String p : supportedProtocols) {
                if (protocols.contains(p)) {
                    engineWrapper.getSession().setApplicationProtocol(p);
                    return p;
                }
            }

            if (failIfNoCommonProtocols) {
                throw new SSLHandshakeException("Selected protocol is not supported");
            } else {
                unsupported();
                return null;
            }
        }

        void selected(String protocol) throws Exception {
            if (supportedProtocols.contains(protocol)) {
                engineWrapper.getSession().setApplicationProtocol(protocol);
            } else if (failIfNoCommonProtocols) {
                throw new SSLHandshakeException("No compatible protocols found");
            }
        }

        void unsupported() {
            engineWrapper.getSession().setApplicationProtocol(null);
        }
    }
}
