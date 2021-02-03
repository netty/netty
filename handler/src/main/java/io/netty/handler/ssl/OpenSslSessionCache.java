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

import io.netty.internal.tcnative.SSLSessionCache;
import io.netty.util.internal.SystemPropertyUtil;

import javax.net.ssl.SSLSession;
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

    private final Map<OpenSslSessionId, OpenSslSession> sessions =
            new LinkedHashMap<OpenSslSessionId, OpenSslSession>() {

                private static final long serialVersionUID = -7773696788135734448L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<OpenSslSessionId, OpenSslSession> eldest) {
                    int maxSize = maximumCacheSize.get();
                    if (maxSize >= 0 && this.size() > maxSize) {
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
    protected boolean sessionCreated(OpenSslSession session) {
        return true;
    }

    /**
     * Called once an {@link OpenSslSession} was removed from the cache.
     *
     * @param session the session to remove.
     */
    protected void sessionRemoved(OpenSslSession session) { }

    final void setSessionCacheSize(int size) {
        long oldSize = maximumCacheSize.getAndSet(size);
        if (oldSize > size) {
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
        Iterator<Map.Entry<OpenSslSessionId, OpenSslSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            OpenSslSession session = iterator.next().getValue();
            // As we use a LinkedHashMap we can break the while loop as soon as we find a valid session.
            // This is true as we always drain the cache as soon as we change the timeout to a smaller value as
            // it was set before. This way its true that the insertation order matches the timeout order.
            if (session.isValid(now)) {
                break;
            }
            iterator.remove();

            notifyRemovalAndRelease(session);
        }
    }

    @Override
    public final boolean sessionCreated(long ssl, long sslSession) {
        ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
        if (engine == null) {
            // We couldn't find the engine itself.
            return false;
        }
        final OpenSslSession session = engine.sessionCreated(sslSession);
        if (session == null) {
            return false;
        }

        synchronized (this) {
            // Mimic what OpenSSL is doing and expunge every 255 new sessions
            // See https://www.openssl.org/docs/man1.0.2/man3/SSL_CTX_flush_sessions.html
            if (++sessionCounter == 255) {
                sessionCounter = 0;
                expungeInvalidSessions();
            }

            assert session.refCnt() >= 1;
            if (!sessionCreated(session)) {
                // Should not be cached, return false.
                return false;
            }

            final OpenSslSession old = sessions.put(session.sessionId(), session.retain());
            if (old != null) {
                notifyRemovalAndRelease(old);
            }
        }
        return true;
    }

    @Override
    public final long getSession(long ssl, byte[] sessionId) {
        OpenSslSessionId id = new OpenSslSessionId(sessionId);
        final OpenSslSession session;
        synchronized (this) {
            session = sessions.get(id);
            if (session == null) {
                return -1;
            }
            assert session.refCnt() >= 1;

            // If the session is not valid anymore we should remove it from the cache and just signal back
            // that we couldn't find a session that is re-usable.
            if (!session.isValid() ||
                    // This needs to happen in the synchronized block so we ensure we never destroy it before we
                    // incremented the reference count. If we cant increment the reference count there is something
                    // wrong. In this case just remove the session from the cache and signal back that we couldn't
                    // find a session for re-use.
                    !session.upRef()) {
                removeSessionWithId(session.sessionId());
                return -1;
            }
        }

        // Now that we incremented the reference count of the native SSL_SESSION we should also retain the reference
        // count of our java session.
        session.retain();

        if (session.shouldBeSingleUse()) {
            // Should only be used once. In this case invalidate the session which will also ensure we remove it
            // from the cache and not use it again in the future.
            session.invalidate();
        }
        session.updateLastAccessedTime();
        return session.nativeAddr();
    }

    final synchronized void removeSessionWithId(OpenSslSessionId id) {
        OpenSslSession sslSession = sessions.remove(id);
        if (sslSession != null) {
            notifyRemovalAndRelease(sslSession);
        }
    }

    private void notifyRemovalAndRelease(OpenSslSession session) {
        sessionRemoved(session);
        session.release();
    }

    final SSLSession getSession(byte[] bytes) {
        OpenSslSessionId id = new OpenSslSessionId(bytes);
        synchronized (this) {
            OpenSslSession session = sessions.get(id);
            if (session != null && !session.isValid()) {
                // The session is not valid anymore, let's remove it and just signale back that there is no session
                // with the given ID in the cache anymore.
                removeSessionWithId(session.sessionId());
                return null;
            }
            return session;
        }
    }

    final List<byte[]> getIds() {
        final OpenSslSession[] sessionsArray;
        synchronized (this) {
            sessionsArray = sessions.values().toArray(EMPTY_SESSIONS);
        }
        List<byte[]> ids = new ArrayList<byte[]>(sessionsArray.length);
        for (OpenSslSession session: sessionsArray) {
            if (session.isValid()) {
                ids.add(session.getId());
            }
        }
        return ids;
    }

    synchronized void clear() {
        Iterator<Map.Entry<OpenSslSessionId, OpenSslSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            OpenSslSession session = iterator.next().getValue();
            iterator.remove();
            notifyRemovalAndRelease(session);
        }
    }
}
