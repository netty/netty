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

import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLEngine;

/**
 * The {@link JdkApplicationProtocolNegotiator} to use if you do not care about NPN or ALPN and are using
 * {@link SslProvider#JDK}.
 */
final class JdkDefaultApplicationProtocolNegotiator implements JdkApplicationProtocolNegotiator {
    public static final JdkDefaultApplicationProtocolNegotiator INSTANCE =
            new JdkDefaultApplicationProtocolNegotiator();
    private static final SslEngineWrapperFactory DEFAULT_SSL_ENGINE_WRAPPER_FACTORY = new SslEngineWrapperFactory() {
        @Override
        public SSLEngine wrapSslEngine(SSLEngine engine,
                                       JdkApplicationProtocolNegotiator applicationNegotiator, boolean isServer) {
            return engine;
        }
    };

    private JdkDefaultApplicationProtocolNegotiator() {
    }

    @Override
    public SslEngineWrapperFactory wrapperFactory() {
        return DEFAULT_SSL_ENGINE_WRAPPER_FACTORY;
    }

    @Override
    public ProtocolSelectorFactory protocolSelectorFactory() {
        throw new UnsupportedOperationException("Application protocol negotiation unsupported");
    }

    @Override
    public ProtocolSelectionListenerFactory protocolListenerFactory() {
        throw new UnsupportedOperationException("Application protocol negotiation unsupported");
    }

    @Override
    public List<String> protocols() {
        return Collections.emptyList();
    }
}
