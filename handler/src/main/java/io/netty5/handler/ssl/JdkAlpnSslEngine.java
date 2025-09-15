/*
 * Copyright 2017 The Netty Project
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

import io.netty5.util.internal.StringUtil;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static io.netty5.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelectionListener;
import static io.netty5.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelector;
import static io.netty5.handler.ssl.SslUtils.toSSLHandshakeException;

class JdkAlpnSslEngine extends JdkSslEngine {
    private final ProtocolSelectionListener selectionListener;
    private final AlpnSelector alpnSelector;

    final class AlpnSelector implements BiFunction<SSLEngine, List<String>, String> {
        private final ProtocolSelector selector;
        private boolean called;

        AlpnSelector(ProtocolSelector selector) {
            this.selector = selector;
        }

        @Override
        public String apply(SSLEngine sslEngine, List<String> strings) {
            assert !called;
            called = true;

            try {
                String selected = selector.select(strings);
                return selected == null ? StringUtil.EMPTY_STRING : selected;
            } catch (Exception cause) {
                // Returning null means we want to fail the handshake.
                //
                // See https://download.java.net/java/jdk9/docs/api/javax/net/ssl/
                // SSLEngine.html#setHandshakeApplicationProtocolSelector-java.util.function.BiFunction-
                return null;
            }
        }

        void checkUnsupported() {
            if (called) {
                // ALPN message was received by peer and so apply(...) was called.
                // See:
                // https://hg.openjdk.java.net/jdk9/dev/jdk/file/65464a307408/src/
                // java.base/share/classes/sun/security/ssl/ServerHandshaker.java#l933
                return;
            }
            String protocol = getApplicationProtocol();
            assert protocol != null;

            if (protocol.isEmpty()) {
                // ALPN is not supported
                selector.unsupported();
            }
        }
    }

    JdkAlpnSslEngine(SSLEngine engine,
                     @SuppressWarnings("deprecation") JdkApplicationProtocolNegotiator applicationNegotiator,
                     boolean isServer, BiConsumer<SSLEngine, AlpnSelector> setHandshakeApplicationProtocolSelector,
                     BiConsumer<SSLEngine, List<String>> setApplicationProtocols) {
        super(engine);
        if (isServer) {
            selectionListener = null;
            alpnSelector = new AlpnSelector(applicationNegotiator.protocolSelectorFactory().
                    newSelector(this, new LinkedHashSet<>(applicationNegotiator.protocols())));
            setHandshakeApplicationProtocolSelector.accept(engine, alpnSelector);
        } else {
            selectionListener = applicationNegotiator.protocolListenerFactory()
                    .newListener(this, applicationNegotiator.protocols());
            alpnSelector = null;
            setApplicationProtocols.accept(engine, applicationNegotiator.protocols());
        }
    }

    JdkAlpnSslEngine(SSLEngine engine,
                     @SuppressWarnings("deprecation") JdkApplicationProtocolNegotiator applicationNegotiator,
                     boolean isServer) {
       this(engine, applicationNegotiator, isServer,
               SSLEngine::setHandshakeApplicationProtocolSelector,
               JdkAlpnSslUtils::setApplicationProtocols);
    }

    private SSLEngineResult verifyProtocolSelection(SSLEngineResult result) throws SSLException {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
            if (alpnSelector == null) {
                // This means we are using client-side and
                try {
                    String protocol = getApplicationProtocol();
                    assert protocol != null;
                    if (protocol.isEmpty()) {
                        // If empty the server did not announce ALPN:
                        // See:
                        // https://hg.openjdk.java.net/jdk9/dev/jdk/file/65464a307408/src/java.base/
                        // share/classes/sun/security/ssl/ClientHandshaker.java#l741
                        selectionListener.unsupported();
                    } else {
                        selectionListener.selected(protocol);
                    }
                } catch (Throwable e) {
                    throw toSSLHandshakeException(e);
                }
            } else {
                assert selectionListener == null;
                alpnSelector.checkUnsupported();
            }
        }
        return result;
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return verifyProtocolSelection(super.wrap(src, dst));
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, ByteBuffer dst) throws SSLException {
        return verifyProtocolSelection(super.wrap(srcs, dst));
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int len, ByteBuffer dst) throws SSLException {
        return verifyProtocolSelection(super.wrap(srcs, offset, len, dst));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return verifyProtocolSelection(super.unwrap(src, dst));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts) throws SSLException {
        return verifyProtocolSelection(super.unwrap(src, dsts));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dst, int offset, int len) throws SSLException {
        return verifyProtocolSelection(super.unwrap(src, dst, offset, len));
    }

    @Override
    void setNegotiatedApplicationProtocol(String applicationProtocol) {
        // Do nothing as this is handled internally by the Java8u251+ implementation of SSLEngine.
    }

    @Override
    public String getNegotiatedApplicationProtocol() {
        String protocol = getApplicationProtocol();
        if (protocol != null) {
            return protocol.isEmpty() ? null : protocol;
        }
        return null;
    }

    @Override
    public String getApplicationProtocol() {
        return getWrappedEngine().getApplicationProtocol();
    }

    @Override
    public String getHandshakeApplicationProtocol() {
        return getWrappedEngine().getHandshakeApplicationProtocol();
    }

    @Override
    public void setHandshakeApplicationProtocolSelector(BiFunction<SSLEngine, List<String>, String> selector) {
        getWrappedEngine().setHandshakeApplicationProtocolSelector(selector);
    }

    @Override
    public BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector() {
        return getWrappedEngine().getHandshakeApplicationProtocolSelector();
    }
}
