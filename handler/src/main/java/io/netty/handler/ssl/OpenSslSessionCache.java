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
package io.netty.handler.ssl;

import io.netty.internal.tcnative.SSLSession;
import io.netty.internal.tcnative.SSLSessionCache;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.SystemPropertyUtil;

import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link SSLSessionCache} implementation for our native SSL implementation.
 */
class OpenSslSessionCache implements SSLSessionCache {
    private static final OpenSslSession[] EMPTY_SESSIONS = new OpenSslSession[0];

    private static final int DEFAULT_CACHE_SIZE;
    static {
        // Respect the same system property as the JDK implementation to make it easy to switch between implementations.
        int cacheSize = SystemPropertyUtil.getInt("javax.net.ssl.sessionCacheSize", 20480);
        if (cacheSize >= 0) {
            DEFAULT_CACHE_SIZE = cacheSize;
        } else {
            DEFAULT_CACHE_SIZE = 20480;
        }
    }
    private final OpenSslEngineMap engineMap;

    private final Map<OpenSslSessionId, NativeSslSession> sessions =
            new LinkedHashMap<OpenSslSessionId, NativeSslSession>() {

                private static final long serialVersionUID = -7773696788135734448L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<OpenSslSessionId, NativeSslSession> eldest) {
                    int maxSize = maximumCacheSize.get();
                    if (maxSize >= 0 && size() > maxSize) {
                        removeSessionWithId(eldest.getKey());
                    }
                    // We always need to return false as we modify the map directly.
                    return false;
                }
            };

    private final AtomicInteger maximumCacheSize = new AtomicInteger(DEFAULT_CACHE_SIZE);

    // Let's use the same default value as OpenSSL does.
    // See https://www.openssl.org/docs/man1.1.1/man3/SSL_get_default_timeout.html
    private final AtomicInteger sessionTimeout = new AtomicInteger(300);
    private int sessionCounter;

    OpenSslSessionCache(OpenSslEngineMap engineMap) {
        this.engineMap = engineMap;
    }

    final void setSessionTimeout(int seconds) {
        int oldTimeout = sessionTimeout.getAndSet(seconds);
        if (oldTimeout > seconds) {
            // Drain the whole cache as this way we can use the ordering of the LinkedHashMap to detect early
            // if there are any other sessions left that are invalid.
            clear();
        }
    }

    final int getSessionTimeout() {
        return sessionTimeout.get();
    }

    /**
     * Called once a new {@link OpenSslSession} was created.
     *
     * @param session the new session.
     * @return {@code true} if the session should be cached, {@code false} otherwise.
     */
    protected boolean sessionCreated(NativeSslSession session) {
        return true;
    }

    /**
     * Called once an {@link OpenSslSession} was removed from the cache.
     *
     * @param session the session to remove.
     */
    protected void sessionRemoved(NativeSslSession session) { }

    final void setSessionCacheSize(int size) {
        long oldSize = maximumCacheSize.getAndSet(size);
        if (oldSize > size || size == 0) {
            // Just keep it simple for now and drain the whole cache.
            clear();
        }
    }

    final int getSessionCacheSize() {
        return maximumCacheSize.get();
    }

    private void expungeInvalidSessions() {
        if (sessions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<OpenSslSessionId, NativeSslSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            NativeSslSession session = iterator.next().getValue();
            // As we use a LinkedHashMap we can break the while loop as soon as we find a valid session.
            // This is true as we always drain the cache as soon as we change the timeout to a smaller value as
            // it was set before. This way its true that the insertion order matches the timeout order.
            if (session.isValid(now)) {
                break;
            }
            iterator.remove();

            notifyRemovalAndFree(session);
        }
    }

    @Override
    public final boolean sessionCreated(long ssl, long sslSession) {
        ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
        if (engine == null) {
            // We couldn't find the engine itself.
            return false;
        }
        NativeSslSession session = new NativeSslSession(sslSession, engine.getPeerHost(), engine.getPeerPort(),
                getSessionTimeout() * 1000L);
        engine.setSessionId(session.sessionId());
        synchronized (this) {
            // Mimic what OpenSSL is doing and expunge every 255 new sessions
            // See https://www.openssl.org/docs/man1.0.2/man3/SSL_CTX_flush_sessions.html
            if (++sessionCounter == 255) {
                sessionCounter = 0;
                expungeInvalidSessions();
            }

            if (!sessionCreated(session)) {
                // Should not be cached, return false. In this case we also need to call close() to ensure we
                // close the ResourceLeakTracker.
                session.close();
                return false;
            }

            final NativeSslSession old = sessions.put(session.sessionId(), session);
            if (old != null) {
                notifyRemovalAndFree(old);
            }
        }
        return true;
    }

    @Override
    public final long getSession(long ssl, byte[] sessionId) {
        OpenSslSessionId id = new OpenSslSessionId(sessionId);
        final NativeSslSession session;
        synchronized (this) {
            session = sessions.get(id);
            if (session == null) {
                return -1;
            }

            // If the session is not valid anymore we should remove it from the cache and just signal back
            // that we couldn't find a session that is re-usable.
            if (!session.isValid() ||
                    // This needs to happen in the synchronized block so we ensure we never destroy it before we
                    // incremented the reference count. If we cant increment the reference count there is something
                    // wrong. In this case just remove the session from the cache and signal back that we couldn't
                    // find a session for re-use.
                    !session.upRef()) {
                // Remove the session from the cache. This will also take care of calling SSL_SESSION_free(...)
                removeSessionWithId(session.sessionId());
                return -1;
            }

            // At this point we already incremented the reference count via SSL_SESSION_up_ref(...).
            if (session.shouldBeSingleUse()) {
                // Should only be used once. In this case invalidate the session which will also ensure we remove it
                // from the cache and call SSL_SESSION_free(...).
                removeSessionWithId(session.sessionId());
            }
        }
        session.updateLastAccessedTime();
        return session.session();
    }

    void setSession(long ssl, String host, int port) {
        // Do nothing by default as this needs special handling for the client side.
    }

    /**
     * Remove the session with the given id from the cache
     */
    final synchronized void removeSessionWithId(OpenSslSessionId id) {
        NativeSslSession sslSession = sessions.remove(id);
        if (sslSession != null) {
            notifyRemovalAndFree(sslSession);
        }
    }

    /**
     * Returns {@code true} if there is a session for the given id in the cache.
     */
    final synchronized boolean containsSessionWithId(OpenSslSessionId id) {
        return sessions.containsKey(id);
    }

    private void notifyRemovalAndFree(NativeSslSession session) {
        sessionRemoved(session);
        session.free();
    }

    /**
     * Return the {@link OpenSslSession} which is cached for the given id.
     */
    final synchronized OpenSslSession getSession(OpenSslSessionId id) {
        NativeSslSession session = sessions.get(id);
        if (session != null && !session.isValid()) {
            // The session is not valid anymore, let's remove it and just signal back that there is no session
            // with the given ID in the cache anymore. This also takes care of calling SSL_SESSION_free(...)
            removeSessionWithId(session.sessionId());
            return null;
        }
        return session;
    }

    /**
     * Returns a snapshot of the session ids of the current valid sessions.
     */
    final List<OpenSslSessionId> getIds() {
        final OpenSslSession[] sessionsArray;
        synchronized (this) {
            sessionsArray = sessions.values().toArray(EMPTY_SESSIONS);
        }
        List<OpenSslSessionId> ids = new ArrayList<OpenSslSessionId>(sessionsArray.length);
        for (OpenSslSession session: sessionsArray) {
            if (session.isValid()) {
                ids.add(session.sessionId());
            }
        }
        return ids;
    }

    /**
     * Clear the cache and free all cached SSL_SESSION*.
     */
    synchronized void clear() {
        Iterator<Map.Entry<OpenSslSessionId, NativeSslSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            NativeSslSession session = iterator.next().getValue();
            iterator.remove();

            // Notify about removal. This also takes care of calling SSL_SESSION_free(...).
            notifyRemovalAndFree(session);
        }
    }

    /**
     * {@link OpenSslSession} implementation which wraps the native SSL_SESSION* while in cache.
     */
    static final class NativeSslSession implements OpenSslSession {
        static final ResourceLeakDetector<NativeSslSession> LEAK_DETECTOR = ResourceLeakDetectorFactory.instance()
                .newResourceLeakDetector(NativeSslSession.class);
        private final ResourceLeakTracker<NativeSslSession> leakTracker;
        private final long session;
        private final String peerHost;
        private final int peerPort;
        private final OpenSslSessionId id;
        private final long timeout;
        private final long creationTime = System.currentTimeMillis();
        private volatile long lastAccessedTime = creationTime;
        private volatile boolean valid = true;
        private boolean freed;

        NativeSslSession(long session, String peerHost, int peerPort, long timeout) {
            this.session = session;
            this.peerHost = peerHost;
            this.peerPort = peerPort;
            this.timeout = timeout;
            this.id = new OpenSslSessionId(io.netty.internal.tcnative.SSLSession.getSessionId(session));
            leakTracker = LEAK_DETECTOR.track(this);
        }

        @Override
        public void setSessionId(OpenSslSessionId id) {
            throw new UnsupportedOperationException();
        }

        boolean shouldBeSingleUse() {
            assert !freed;
            return SSLSession.shouldBeSingleUse(session);
        }

        long session() {
            assert !freed;
            return session;
        }

        boolean upRef() {
            assert !freed;
            return SSLSession.upRef(session);
        }

        synchronized void free() {
            close();
            SSLSession.free(session);
        }

        void close() {
            assert !freed;
            freed = true;
            invalidate();
            if (leakTracker != null) {
                leakTracker.close(this);
            }
        }

        @Override
        public OpenSslSessionId sessionId() {
            return id;
        }

        boolean isValid(long now) {
            return creationTime + timeout >= now && valid;
        }

        @Override
        public void setLocalCertificate(Certificate[] localCertificate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OpenSslSessionContext getSessionContext() {
            return null;
        }

        @Override
        public void tryExpandApplicationBufferSize(int packetLengthDataOnly) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void handshakeFinished(byte[] id, String cipher, String protocol, byte[] peerCertificate,
                                      byte[][] peerCertificateChain, long creationTime, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getId() {
            return id.cloneBytes();
        }

        @Override
        public long getCreationTime() {
            return creationTime;
        }

        void updateLastAccessedTime() {
            lastAccessedTime = System.currentTimeMillis();
        }

        @Override
        public long getLastAccessedTime() {
            return lastAccessedTime;
        }

        @Override
        public void invalidate() {
            valid = false;
        }

        @Override
        public boolean isValid() {
            return isValid(System.currentTimeMillis());
        }

        @Override
        public void putValue(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public void removeValue(String name) {
            // NOOP
        }

        @Override
        public String[] getValueNames() {
            return EmptyArrays.EMPTY_STRINGS;
        }

        @Override
        public Certificate[] getPeerCertificates() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Certificate[] getLocalCertificates() {
            throw new UnsupportedOperationException();
        }

        @Override
        public X509Certificate[] getPeerCertificateChain() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Principal getPeerPrincipal() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Principal getLocalPrincipal() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCipherSuite() {
            return null;
        }

        @Override
        public String getProtocol() {
            return null;
        }

        @Override
        public String getPeerHost() {
            return peerHost;
        }

        @Override
        public int getPeerPort() {
            return peerPort;
        }

        @Override
        public int getPacketBufferSize() {
            return ReferenceCountedOpenSslEngine.MAX_RECORD_SIZE;
        }

        @Override
        public int getApplicationBufferSize() {
            return ReferenceCountedOpenSslEngine.MAX_PLAINTEXT_LENGTH;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof OpenSslSession)) {
                return false;
            }
            OpenSslSession session1 = (OpenSslSession) o;
            return id.equals(session1.sessionId());
        }
    }
}
