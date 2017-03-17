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

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.min;

import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelectionListener;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelector;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.conscrypt.OpenSSLEngineImpl;
import org.conscrypt.OpenSSLEngineImpl.HandshakeListener;

/**
 * A {@link JdkSslEngine} that uses the Conscrypt provider or SSL with ALPN.
 */
abstract class ConscryptAlpnSslEngine extends JdkSslEngine {
    private static final Class<?> ENGINE_CLASS = getEngineClass();

    /**
     * Indicates whether or not conscrypt is available on the current system.
     */
    static boolean isAvailable() {
        return ENGINE_CLASS != null;
    }

    static boolean isEngineSupported(SSLEngine engine) {
        return isAvailable() && ENGINE_CLASS.isInstance(engine);
    }

    static ConscryptAlpnSslEngine newClientEngine(SSLEngine engine,
            JdkApplicationProtocolNegotiator applicationNegotiator) {
        return new ClientEngine((OpenSSLEngineImpl) engine, applicationNegotiator);
    }

    static ConscryptAlpnSslEngine newServerEngine(SSLEngine engine,
            JdkApplicationProtocolNegotiator applicationNegotiator) {
        return new ServerEngine((OpenSSLEngineImpl) engine, applicationNegotiator);
    }

    private ConscryptAlpnSslEngine(OpenSSLEngineImpl engine, List<String> protocols) {
        super(engine);

        // Register for completion of the handshake.
        engine.setHandshakeListener(new HandshakeListener() {
            @Override
            public void onHandshakeFinished() throws SSLException {
                ConscryptAlpnSslEngine.this.onHandshakeFinished();
            }
        });

        // Set the list of supported ALPN protocols on the engine.
        engine.setAlpnProtocols(protocols.toArray(new String[protocols.size()]));
    }

    OpenSSLEngineImpl getWrappedConscryptEngine() {
        return (OpenSSLEngineImpl) getWrappedEngine();
    }

    /**
     * Calculates the maximum size of the encrypted output buffer required to wrap the given plaintext bytes. Assumes
     * as a worst case that there is one TLS record per buffer.
     *
     * @param plaintextBytes the number of plaintext bytes to be wrapped.
     * @param numBuffers the number of buffers that the plaintext bytes are spread across.
     * @return the maximum size of the encrypted output buffer required for the wrap operation.
     */
    final int calculateOutNetBufSize(int plaintextBytes, int numBuffers) {
        // Assuming a max of one frame per component in a composite buffer.
        long maxOverhead = (long) getWrappedConscryptEngine().maxSealOverhead() * numBuffers;
        return (int) min(Integer.MAX_VALUE, plaintextBytes + maxOverhead);
    }

    abstract void onHandshakeFinished() throws SSLException;

    private static String getAlpnSelectedProtocol(OpenSSLEngineImpl engine) {
        byte[] data = engine.getAlpnSelectedProtocol();
        return data == null ? null : new String(data, CharsetUtil.US_ASCII);
    }

    private static final class ClientEngine extends ConscryptAlpnSslEngine {
        private final ProtocolSelectionListener protocolListener;

        ClientEngine(OpenSSLEngineImpl engine,
                JdkApplicationProtocolNegotiator applicationNegotiator) {
            super(engine, applicationNegotiator.protocols());
            protocolListener = checkNotNull(applicationNegotiator
                            .protocolListenerFactory().newListener(this, applicationNegotiator.protocols()),
                    "protocolListener");
        }

        @Override
        public void onHandshakeFinished() throws SSLException {
            String protocol = getAlpnSelectedProtocol(getWrappedConscryptEngine());
            try {
                protocolListener.selected(protocol);
            } catch (Throwable e) {
                throw toSSLHandshakeException(e);
            }
        }
    }

    private static final class ServerEngine extends ConscryptAlpnSslEngine {
        private final ProtocolSelector protocolSelector;

        ServerEngine(OpenSSLEngineImpl engine, JdkApplicationProtocolNegotiator applicationNegotiator) {
            super(engine, applicationNegotiator.protocols());
            protocolSelector = checkNotNull(applicationNegotiator.protocolSelectorFactory()
                            .newSelector(this,
                                    new LinkedHashSet<String>(applicationNegotiator.protocols())),
                    "protocolSelector");
        }

        @Override
        public void onHandshakeFinished() throws SSLException {
            try {
                String protocol = getAlpnSelectedProtocol(getWrappedConscryptEngine());
                protocolSelector.select(protocol != null ? Collections.singletonList(protocol)
                        : Collections.<String>emptyList());
            } catch (Throwable e) {
                throw toSSLHandshakeException(e);
            }
        }
    }

    private static SSLHandshakeException toSSLHandshakeException(Throwable e) {
        if (e instanceof SSLHandshakeException) {
            return (SSLHandshakeException) e;
        }

        return (SSLHandshakeException) new SSLHandshakeException(e.getMessage()).initCause(e);
    }

    private static Class<?> getEngineClass() {
        try {
            // Always use bootstrap class loader.
            return Class.forName("org.conscrypt.OpenSSLEngineImpl", true,
                    ConscryptAlpnSslEngine.class.getClassLoader());
        } catch (Throwable ignore) {
            // Conscrypt was not loaded.
            return null;
        }
    }
}
