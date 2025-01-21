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

import io.netty.internal.tcnative.SSL;
import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link OpenSslSessionCache} that is used by the client-side.
 */
final class OpenSslClientSessionCache extends OpenSslSessionCache {
    private final Map<HostPort, Set<NativeSslSession>> sessions = new HashMap<HostPort, Set<NativeSslSession>>();

    OpenSslClientSessionCache(OpenSslEngineMap engineMap) {
        super(engineMap);
    }

    @Override
    protected boolean sessionCreated(NativeSslSession session) {
        assert Thread.holdsLock(this);
        HostPort hostPort = keyFor(session.getPeerHost(), session.getPeerPort());
        if (hostPort == null) {
            return false;
        }
        Set<NativeSslSession> sessionsForHost = sessions.get(hostPort);
        if (sessionsForHost == null) {
            // Let's start with something small as usually the server does not provide too many of these per hostPort
            // mapping.
            sessionsForHost = new HashSet<NativeSslSession>(4);
            sessions.put(hostPort, sessionsForHost);
        }
        sessionsForHost.add(session);
        return true;
    }

    @Override
    protected void sessionRemoved(NativeSslSession session) {
        assert Thread.holdsLock(this);
        HostPort hostPort = keyFor(session.getPeerHost(), session.getPeerPort());
        if (hostPort == null) {
            return;
        }
        Set<NativeSslSession> sessionsForHost = sessions.get(hostPort);
        if (sessionsForHost != null) {
            sessionsForHost.remove(session);
            if (sessionsForHost.isEmpty()) {
                sessions.remove(hostPort);
            }
        }
    }

    @Override
    boolean setSession(long ssl, OpenSslInternalSession session, String host, int port) {
        HostPort hostPort = keyFor(host, port);
        if (hostPort == null) {
            return false;
        }
        NativeSslSession nativeSslSession = null;
        final boolean reused;
        boolean singleUsed = false;
        synchronized (this) {
            Set<NativeSslSession> sessionsForHost = sessions.get(hostPort);
            if (sessionsForHost == null) {
                return false;
            }
            if (sessionsForHost.isEmpty()) {
                sessions.remove(hostPort);
                // There is no session that we can use.
                return false;
            }

            List<NativeSslSession> toBeRemoved = null;
            // Loop through all the sessions that might be usable and check if we can use one of these.
            for (NativeSslSession sslSession : sessionsForHost) {
                if (sslSession.isValid()) {
                    nativeSslSession = sslSession;
                    break;
                } else {
                    if (toBeRemoved == null) {
                        toBeRemoved = new ArrayList<NativeSslSession>(2);
                    }
                    toBeRemoved.add(sslSession);
                }
            }

            // Remove everything that is not valid anymore
            if (toBeRemoved != null) {
                for (NativeSslSession sslSession : toBeRemoved) {
                    removeSessionWithId(sslSession.sessionId());
                }
            }
            if (nativeSslSession == null) {
                // Couldn't find a valid session that could be used.
                return false;
            }

            // Try to set the session, if true is returned OpenSSL incremented the reference count
            // of the underlying SSL_SESSION*.
            reused = SSL.setSession(ssl, nativeSslSession.session());
            if (reused) {
                singleUsed = nativeSslSession.shouldBeSingleUse();
            }
        }

        if (reused) {
            if (singleUsed) {
                // Should only be used once
                nativeSslSession.invalidate();
                session.invalidate();
            }
            nativeSslSession.setLastAccessedTime(System.currentTimeMillis());
            session.setSessionDetails(nativeSslSession.getCreationTime(), nativeSslSession.getLastAccessedTime(),
                    nativeSslSession.sessionId(), nativeSslSession.keyValueStorage);
        }
        return reused;
    }

    private static HostPort keyFor(String host, int port) {
        if (host == null && port < 1) {
            return null;
        }
        return new HostPort(host, port);
    }

    @Override
    synchronized void clear() {
        super.clear();
        sessions.clear();
    }

    /**
     * Host / Port tuple used to find a {@link OpenSslInternalSession} in the cache.
     */
    private static final class HostPort {
        private final int hash;
        private final String host;
        private final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
            // Calculate a hashCode that does ignore case.
            this.hash = 31 * AsciiString.hashCode(host) + port;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof HostPort)) {
                return false;
            }
            HostPort other = (HostPort) obj;
            return port == other.port && host.equalsIgnoreCase(other.host);
        }

        @Override
        public String toString() {
            return "HostPort{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    '}';
        }
    }
}
