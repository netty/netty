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

import javax.net.ssl.SSLEngine;

/**
 * The {@link JdkApplicationProtocolNegotiator} to use if you need NPN and are using {@link SslProvider#JDK}.
 */
public final class JdkNpnApplicationProtocolNegotiator extends JdkBaseApplicationProtocolNegotiator {
    private static final SslEngineWrapperFactory NPN_WRAPPER = new SslEngineWrapperFactory() {
        {
            if (!JdkNpnSslEngine.isAvailable()) {
                throw new RuntimeException("NPN unsupported. Is your classpatch configured correctly?"
                        + " See https://wiki.eclipse.org/Jetty/Feature/NPN");
            }
        }

        @Override
        public SSLEngine wrapSslEngine(SSLEngine engine, JdkApplicationProtocolNegotiator applicationNegotiator,
                boolean isServer) {
            return new JdkNpnSslEngine(engine, applicationNegotiator, isServer);
        }
    };

    /**
     * Create a new instance.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkNpnApplicationProtocolNegotiator(Iterable<String> protocols) {
        this(false, protocols);
    }

    /**
     * Create a new instance.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkNpnApplicationProtocolNegotiator(String... protocols) {
        this(false, protocols);
    }

    /**
     * Create a new instance.
     * @param failIfNoCommonProtocols Fail with a fatal alert if not common protocols are detected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkNpnApplicationProtocolNegotiator(boolean failIfNoCommonProtocols, Iterable<String> protocols) {
        this(failIfNoCommonProtocols, failIfNoCommonProtocols, protocols);
    }

    /**
     * Create a new instance.
     * @param failIfNoCommonProtocols Fail with a fatal alert if not common protocols are detected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkNpnApplicationProtocolNegotiator(boolean failIfNoCommonProtocols, String... protocols) {
        this(failIfNoCommonProtocols, failIfNoCommonProtocols, protocols);
    }

    /**
     * Create a new instance.
     * @param clientFailIfNoCommonProtocols Client side fail with a fatal alert if not common protocols are detected.
     * @param serverFailIfNoCommonProtocols Server side fail with a fatal alert if not common protocols are detected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkNpnApplicationProtocolNegotiator(boolean clientFailIfNoCommonProtocols,
            boolean serverFailIfNoCommonProtocols, Iterable<String> protocols) {
        this(clientFailIfNoCommonProtocols ? FAIL_SELECTOR_FACTORY : NO_FAIL_SELECTOR_FACTORY,
                serverFailIfNoCommonProtocols ? FAIL_SELECTION_LISTENER_FACTORY : NO_FAIL_SELECTION_LISTENER_FACTORY,
                protocols);
    }

    /**
     * Create a new instance.
     * @param clientFailIfNoCommonProtocols Client side fail with a fatal alert if not common protocols are detected.
     * @param serverFailIfNoCommonProtocols Server side fail with a fatal alert if not common protocols are detected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkNpnApplicationProtocolNegotiator(boolean clientFailIfNoCommonProtocols,
            boolean serverFailIfNoCommonProtocols, String... protocols) {
        this(clientFailIfNoCommonProtocols ? FAIL_SELECTOR_FACTORY : NO_FAIL_SELECTOR_FACTORY,
                serverFailIfNoCommonProtocols ? FAIL_SELECTION_LISTENER_FACTORY : NO_FAIL_SELECTION_LISTENER_FACTORY,
                protocols);
    }

    /**
     * Create a new instance.
     * @param selectorFactory The factory which provides classes responsible for selecting the protocol.
     * @param listenerFactory The factory which provides to be notified of which protocol was selected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkNpnApplicationProtocolNegotiator(ProtocolSelectorFactory selectorFactory,
            ProtocolSelectionListenerFactory listenerFactory, Iterable<String> protocols) {
        super(NPN_WRAPPER, selectorFactory, listenerFactory, protocols);
    }

    /**
     * Create a new instance.
     * @param selectorFactory The factory which provides classes responsible for selecting the protocol.
     * @param listenerFactory The factory which provides to be notified of which protocol was selected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkNpnApplicationProtocolNegotiator(ProtocolSelectorFactory selectorFactory,
            ProtocolSelectionListenerFactory listenerFactory, String... protocols) {
        super(NPN_WRAPPER, selectorFactory, listenerFactory, protocols);
    }
}
