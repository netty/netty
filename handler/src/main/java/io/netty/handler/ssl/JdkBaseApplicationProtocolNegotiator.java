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

import static io.netty.handler.ssl.ApplicationProtocolUtil.toList;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;

/**
 * Common base class for {@link JdkApplicationProtocolNegotiator} classes to inherit from.
 */
class JdkBaseApplicationProtocolNegotiator implements JdkApplicationProtocolNegotiator {
    private final List<String> protocols;
    private final ProtocolSelectorFactory selectorFactory;
    private final ProtocolSelectionListenerFactory listenerFactory;
    private final SslEngineWrapperFactory wrapperFactory;

    /**
     * Create a new instance.
     * @param wrapperFactory Determines which application protocol will be used by wrapping the SSLEngine in use.
     * @param selectorFactory How the peer selecting the protocol should behave.
     * @param listenerFactory How the peer being notified of the selected protocol should behave.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    protected JdkBaseApplicationProtocolNegotiator(SslEngineWrapperFactory wrapperFactory,
            ProtocolSelectorFactory selectorFactory, ProtocolSelectionListenerFactory listenerFactory,
            Iterable<String> protocols) {
        this(wrapperFactory, selectorFactory, listenerFactory, toList(protocols));
    }

    /**
     * Create a new instance.
     * @param wrapperFactory Determines which application protocol will be used by wrapping the SSLEngine in use.
     * @param selectorFactory How the peer selecting the protocol should behave.
     * @param listenerFactory How the peer being notified of the selected protocol should behave.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    protected JdkBaseApplicationProtocolNegotiator(SslEngineWrapperFactory wrapperFactory,
            ProtocolSelectorFactory selectorFactory, ProtocolSelectionListenerFactory listenerFactory,
            String... protocols) {
        this(wrapperFactory, selectorFactory, listenerFactory, toList(protocols));
    }

    /**
     * Create a new instance.
     * @param wrapperFactory Determines which application protocol will be used by wrapping the SSLEngine in use.
     * @param selectorFactory How the peer selecting the protocol should behave.
     * @param listenerFactory How the peer being notified of the selected protocol should behave.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    private JdkBaseApplicationProtocolNegotiator(SslEngineWrapperFactory wrapperFactory,
            ProtocolSelectorFactory selectorFactory, ProtocolSelectionListenerFactory listenerFactory,
            List<String> protocols) {
        this.wrapperFactory = checkNotNull(wrapperFactory, "wrapperFactory");
        this.selectorFactory = checkNotNull(selectorFactory, "selectorFactory");
        this.listenerFactory = checkNotNull(listenerFactory, "listenerFactory");
        this.protocols = Collections.unmodifiableList(checkNotNull(protocols, "protocols"));
    }

    @Override
    public List<String> protocols() {
        return protocols;
    }

    @Override
    public ProtocolSelectorFactory protocolSelectorFactory() {
        return selectorFactory;
    }

    @Override
    public ProtocolSelectionListenerFactory protocolListenerFactory() {
        return listenerFactory;
    }

    @Override
    public SslEngineWrapperFactory wrapperFactory() {
        return wrapperFactory;
    }

    static final ProtocolSelectorFactory FAIL_SELECTOR_FACTORY = new ProtocolSelectorFactory() {
        @Override
        public ProtocolSelector newSelector(SSLEngine engine, Set<String> supportedProtocols) {
            return new FailProtocolSelector((JdkSslEngine) engine, supportedProtocols);
        }
    };

    static final ProtocolSelectorFactory NO_FAIL_SELECTOR_FACTORY = new ProtocolSelectorFactory() {
        @Override
        public ProtocolSelector newSelector(SSLEngine engine, Set<String> supportedProtocols) {
            return new NoFailProtocolSelector((JdkSslEngine) engine, supportedProtocols);
        }
    };

    static final ProtocolSelectionListenerFactory FAIL_SELECTION_LISTENER_FACTORY =
            new ProtocolSelectionListenerFactory() {
        @Override
        public ProtocolSelectionListener newListener(SSLEngine engine, List<String> supportedProtocols) {
            return new FailProtocolSelectionListener((JdkSslEngine) engine, supportedProtocols);
        }
    };

    static final ProtocolSelectionListenerFactory NO_FAIL_SELECTION_LISTENER_FACTORY =
            new ProtocolSelectionListenerFactory() {
        @Override
        public ProtocolSelectionListener newListener(SSLEngine engine, List<String> supportedProtocols) {
            return new NoFailProtocolSelectionListener((JdkSslEngine) engine, supportedProtocols);
        }
    };

    protected static class NoFailProtocolSelector implements ProtocolSelector {
        private final JdkSslEngine jettyWrapper;
        private final Set<String> supportedProtocols;

        public NoFailProtocolSelector(JdkSslEngine jettyWrapper, Set<String> supportedProtocols) {
            this.jettyWrapper = jettyWrapper;
            this.supportedProtocols = supportedProtocols;
        }

        @Override
        public void unsupported() {
            jettyWrapper.getSession().setApplicationProtocol(null);
        }

        @Override
        public String select(List<String> protocols) throws Exception {
            for (String p : supportedProtocols) {
                if (protocols.contains(p)) {
                    jettyWrapper.getSession().setApplicationProtocol(p);
                    return p;
                }
            }
            return noSelectMatchFound();
        }

        public String noSelectMatchFound() throws Exception {
            jettyWrapper.getSession().setApplicationProtocol(null);
            return null;
        }
    }

    protected static final class FailProtocolSelector extends NoFailProtocolSelector {
        public FailProtocolSelector(JdkSslEngine jettyWrapper, Set<String> supportedProtocols) {
            super(jettyWrapper, supportedProtocols);
        }

        @Override
        public String noSelectMatchFound() throws Exception {
            throw new SSLHandshakeException("Selected protocol is not supported");
        }
    }

    protected static class NoFailProtocolSelectionListener implements ProtocolSelectionListener {
        private final JdkSslEngine jettyWrapper;
        private final List<String> supportedProtocols;

        public NoFailProtocolSelectionListener(JdkSslEngine jettyWrapper, List<String> supportedProtocols) {
            this.jettyWrapper = jettyWrapper;
            this.supportedProtocols = supportedProtocols;
        }

        @Override
        public void unsupported() {
            jettyWrapper.getSession().setApplicationProtocol(null);
        }

        @Override
        public void selected(String protocol) throws Exception {
            if (supportedProtocols.contains(protocol)) {
                jettyWrapper.getSession().setApplicationProtocol(protocol);
            } else {
                noSelectedMatchFound(protocol);
            }
        }

        public void noSelectedMatchFound(String protocol) throws Exception {
        }
    }

    protected static final class FailProtocolSelectionListener extends NoFailProtocolSelectionListener {
        public FailProtocolSelectionListener(JdkSslEngine jettyWrapper, List<String> supportedProtocols) {
            super(jettyWrapper, supportedProtocols);
        }

        @Override
        public void noSelectedMatchFound(String protocol) throws Exception {
            throw new SSLHandshakeException("No compatible protocols found");
        }
    }
}
