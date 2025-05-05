/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.util.LazyJavaxX509Certificate;
import io.netty.handler.ssl.util.LazyX509Certificate;
import io.netty.util.NetUtil;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongFunction;

final class QuicheQuicSslEngine extends QuicSslEngine {
    QuicheQuicSslContext ctx;
    private final String peerHost;
    private final int peerPort;
    private final QuicheQuicSslSession session = new QuicheQuicSslSession();
    private volatile Certificate[] localCertificateChain;
    private List<SNIServerName> sniHostNames;
    private boolean handshakeFinished;
    private String applicationProtocol;
    private boolean sessionReused;
    final String tlsHostName;
    volatile QuicheQuicConnection connection;

    volatile Consumer<String> sniSelectedCallback;

    QuicheQuicSslEngine(QuicheQuicSslContext ctx, @Nullable String peerHost, int peerPort) {
        this.ctx = ctx;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        // Use SNI if peerHost was specified and a valid hostname
        // See https://github.com/netty/netty/issues/4746
        if (ctx.isClient() && isValidHostNameForSNI(peerHost)) {
            tlsHostName = peerHost;
            sniHostNames = Collections.singletonList(new SNIHostName(tlsHostName));
        } else {
            tlsHostName = null;
        }
    }

    long moveTo(String hostname, QuicheQuicSslContext ctx) {
        // First of remove the engine from its previous QuicheQuicSslContext.
        this.ctx.remove(this);
        this.ctx = ctx;
        long added = ctx.add(this);
        Consumer<String> sniSelectedCallback = this.sniSelectedCallback;
        if (sniSelectedCallback != null) {
            sniSelectedCallback.accept(hostname);
        }
        return added;
    }

    @Nullable
    QuicheQuicConnection createConnection(LongFunction<Long> connectionCreator) {
        return ctx.createConnection(connectionCreator, this);
    }

    void setLocalCertificateChain(Certificate[] localCertificateChain) {
        this.localCertificateChain = localCertificateChain;
    }

    /**
     * Validate that the given hostname can be used in SNI extension.
     */
    static boolean isValidHostNameForSNI(@Nullable String hostname) {
        return hostname != null &&
                hostname.indexOf('.') > 0 &&
                !hostname.endsWith(".") &&
                !NetUtil.isValidIpV4Address(hostname) &&
                !NetUtil.isValidIpV6Address(hostname);
    }

    @Override
    public SSLParameters getSSLParameters() {
        SSLParameters parameters = super.getSSLParameters();
        parameters.setServerNames(sniHostNames);
        return parameters;
    }

    // These method will override the method defined by Java 8u251 and later. As we may compile with an earlier
    // java8 version we don't use @Override annotations here.
    public synchronized String getApplicationProtocol() {
        return applicationProtocol;
    }

    // These method will override the method defined by Java 8u251 and later. As we may compile with an earlier
    // java8 version we don't use @Override annotations here.
    public synchronized String getHandshakeApplicationProtocol() {
        return applicationProtocol;
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Nullable
    public Runnable getDelegatedTask() {
        return null;
    }

    @Override
    public void closeInbound() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInboundDone() {
        return false;
    }

    @Override
    public void closeOutbound() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOutboundDone() {
        return false;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return ctx.cipherSuites().toArray(new String[0]);
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return getSupportedCipherSuites();
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getSupportedProtocols() {
        // QUIC only supports TLSv1.3
        return new String[] { "TLSv1.3" };
    }

    @Override
    public String[] getEnabledProtocols() {
        return getSupportedProtocols();
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLSession getSession() {
        return session;
    }

    @Override
    @Nullable
    public SSLSession getHandshakeSession() {
        if (handshakeFinished) {
            return null;
        }
        return session;
    }

    @Override
    public void beginHandshake() {
        // NOOP
    }

    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        if (handshakeFinished) {
            return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        }
        return SSLEngineResult.HandshakeStatus.NEED_WRAP;
    }

    @Override
    public void setUseClientMode(boolean clientMode) {
        if (clientMode != ctx.isClient()) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getUseClientMode() {
        return ctx.isClient();
    }

    @Override
    public void setNeedClientAuth(boolean b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getNeedClientAuth() {
        return ctx.clientAuth == ClientAuth.REQUIRE;
    }

    @Override
    public void setWantClientAuth(boolean b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getWantClientAuth() {
        return ctx.clientAuth == ClientAuth.OPTIONAL;
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getEnableSessionCreation() {
        return false;
    }

    synchronized void handshakeFinished(byte[] id, String cipher, String protocol, byte[] peerCertificate,
                                        byte[][] peerCertificateChain,
                                        long creationTime, long timeout,
                                        byte @Nullable [] applicationProtocol, boolean sessionReused) {
        if (applicationProtocol == null) {
            this.applicationProtocol = null;
        } else {
            this.applicationProtocol = new String(applicationProtocol);
        }
        session.handshakeFinished(id, cipher, protocol, peerCertificate, peerCertificateChain, creationTime, timeout);
        this.sessionReused = sessionReused;
        handshakeFinished = true;
    }

    void removeSessionFromCacheIfInvalid() {
        session.removeFromCacheIfInvalid();
    }

    synchronized boolean isSessionReused() {
        return sessionReused;
    }

    private final class QuicheQuicSslSession implements SSLSession {
        private X509Certificate[] x509PeerCerts;
        private Certificate[] peerCerts;
        private String protocol;
        private String cipher;
        private byte[] id;
        private long creationTime = -1;
        private long timeout = -1;
        private boolean invalid;
        private long lastAccessedTime = -1;

        // lazy init for memory reasons
        private Map<String, Object> values;

        private boolean isEmpty(Object @Nullable [] arr) {
            return arr == null || arr.length == 0;
        }
        private boolean isEmpty(byte @Nullable [] arr) {
            return arr == null || arr.length == 0;
        }

        void handshakeFinished(byte[] id, String cipher, String protocol, byte[] peerCertificate,
                               byte[][] peerCertificateChain, long creationTime, long timeout) {
            synchronized (QuicheQuicSslEngine.this) {
                initPeerCerts(peerCertificateChain, peerCertificate);
                this.id = id;
                this.cipher = cipher;
                this.protocol = protocol;
                this.creationTime = creationTime * 1000L;
                this.timeout = timeout * 1000L;
                lastAccessedTime = System.currentTimeMillis();
            }
        }

        void removeFromCacheIfInvalid() {
            if (!isValid()) {
                // Shouldn't be re-used again
                removeFromCache();
            }
        }

        private void removeFromCache() {
            // Shouldn't be re-used again
            QuicClientSessionCache cache = ctx.getSessionCache();
            if (cache != null) {
                cache.removeSession(getPeerHost(), getPeerPort());
            }
        }

        /**
         * Init peer certificates that can be obtained via {@link #getPeerCertificateChain()}
         * and {@link #getPeerCertificates()}.
         */
        private void initPeerCerts(byte[][] chain, byte[] clientCert) {
            // Return the full chain from the JNI layer.
            if (getUseClientMode()) {
                if (isEmpty(chain)) {
                    peerCerts = EmptyArrays.EMPTY_CERTIFICATES;
                    x509PeerCerts = EmptyArrays.EMPTY_JAVAX_X509_CERTIFICATES;
                } else {
                    peerCerts = new Certificate[chain.length];
                    x509PeerCerts = new X509Certificate[chain.length];
                    initCerts(chain, 0);
                }
            } else {
                // if used on the server side SSL_get_peer_cert_chain(...) will not include the remote peer
                // certificate. We use SSL_get_peer_certificate to get it in this case and add it to our
                // array later.
                //
                // See https://www.openssl.org/docs/ssl/SSL_get_peer_cert_chain.html
                if (isEmpty(clientCert)) {
                    peerCerts = EmptyArrays.EMPTY_CERTIFICATES;
                    x509PeerCerts = EmptyArrays.EMPTY_JAVAX_X509_CERTIFICATES;
                } else {
                    if (isEmpty(chain)) {
                        peerCerts = new Certificate[] {new LazyX509Certificate(clientCert)};
                        x509PeerCerts = new X509Certificate[] {new LazyJavaxX509Certificate(clientCert)};
                    } else {
                        peerCerts = new Certificate[chain.length + 1];
                        x509PeerCerts = new X509Certificate[chain.length + 1];
                        peerCerts[0] = new LazyX509Certificate(clientCert);
                        x509PeerCerts[0] = new LazyJavaxX509Certificate(clientCert);
                        initCerts(chain, 1);
                    }
                }
            }
        }

        private void initCerts(byte[][] chain, int startPos) {
            for (int i = 0; i < chain.length; i++) {
                int certPos = startPos + i;
                peerCerts[certPos] = new LazyX509Certificate(chain[i]);
                x509PeerCerts[certPos] = new LazyJavaxX509Certificate(chain[i]);
            }
        }

        @Override
        public byte[] getId() {
            synchronized (QuicheQuicSslSession.this) {
                if (id == null) {
                    return EmptyArrays.EMPTY_BYTES;
                }
                return id.clone();
            }
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return ctx.sessionContext();
        }

        @Override
        public long getCreationTime() {
            synchronized (QuicheQuicSslEngine.this) {
                return creationTime;
            }
        }

        @Override
        public long getLastAccessedTime() {
            return lastAccessedTime;
        }

        @Override
        public void invalidate() {
            boolean removeFromCache;
            synchronized (this) {
                removeFromCache = !invalid;
                invalid = true;
            }
            if (removeFromCache) {
                removeFromCache();
            }
        }

        @Override
        public boolean isValid() {
            synchronized (QuicheQuicSslEngine.this) {
                return !invalid && System.currentTimeMillis() - timeout < creationTime;
            }
        }

        @Override
        public void putValue(String name, Object value) {
            ObjectUtil.checkNotNull(name, "name");
            ObjectUtil.checkNotNull(value, "value");

            final Object old;
            synchronized (this) {
                Map<String, Object> values = this.values;
                if (values == null) {
                    // Use size of 2 to keep the memory overhead small
                    values = this.values = new HashMap<>(2);
                }
                old = values.put(name, value);
            }

            if (value instanceof SSLSessionBindingListener) {
                // Use newSSLSessionBindingEvent so we alway use the wrapper if needed.
                ((SSLSessionBindingListener) value).valueBound(newSSLSessionBindingEvent(name));
            }
            notifyUnbound(old, name);
        }

        @Override
        @Nullable
        public Object getValue(String name) {
            ObjectUtil.checkNotNull(name, "name");
            synchronized (this) {
                if (values == null) {
                    return null;
                }
                return values.get(name);
            }
        }

        @Override
        public void removeValue(String name) {
            ObjectUtil.checkNotNull(name, "name");

            final Object old;
            synchronized (this) {
                Map<String, Object> values = this.values;
                if (values == null) {
                    return;
                }
                old = values.remove(name);
            }

            notifyUnbound(old, name);
        }

        @Override
        public String[] getValueNames() {
            synchronized (this) {
                Map<String, Object> values = this.values;
                if (values == null || values.isEmpty()) {
                    return EmptyArrays.EMPTY_STRINGS;
                }
                return values.keySet().toArray(new String[0]);
            }
        }

        private SSLSessionBindingEvent newSSLSessionBindingEvent(String name) {
            return new SSLSessionBindingEvent(session, name);
        }

        private void notifyUnbound(@Nullable Object value, String name) {
            if (value instanceof SSLSessionBindingListener) {
                // Use newSSLSessionBindingEvent so we alway use the wrapper if needed.
                ((SSLSessionBindingListener) value).valueUnbound(newSSLSessionBindingEvent(name));
            }
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            synchronized (QuicheQuicSslEngine.this) {
                if (isEmpty(peerCerts)) {
                    throw new SSLPeerUnverifiedException("peer not verified");
                }
                return peerCerts.clone();
            }
        }

        @Override
        public Certificate @Nullable [] getLocalCertificates() {
            Certificate[] localCerts = localCertificateChain;
            if (localCerts == null) {
                return null;
            }
            return localCerts.clone();
        }

        @Override
        public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
            synchronized (QuicheQuicSslEngine.this) {
                if (isEmpty(x509PeerCerts)) {
                    throw new SSLPeerUnverifiedException("peer not verified");
                }
                return x509PeerCerts.clone();
            }
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            Certificate[] peer = getPeerCertificates();
            // No need for null or length > 0 is needed as this is done in getPeerCertificates()
            // already.
            return ((java.security.cert.X509Certificate) peer[0]).getSubjectX500Principal();
        }

        @Override
        @Nullable
        public Principal getLocalPrincipal() {
            Certificate[] local = localCertificateChain;
            if (local == null || local.length == 0) {
                return null;
            }
            return ((java.security.cert.X509Certificate) local[0]).getIssuerX500Principal();
        }

        @Override
        public String getCipherSuite() {
            return cipher;
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        @Nullable
        public String getPeerHost() {
            return peerHost;
        }

        @Override
        public int getPeerPort() {
            return peerPort;
        }

        @Override
        public int getPacketBufferSize() {
            return -1;
        }

        @Override
        public int getApplicationBufferSize() {
            return -1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            QuicheQuicSslSession that = (QuicheQuicSslSession) o;
            return Arrays.equals(getId(), that.getId());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(getId());
        }
    }
}
