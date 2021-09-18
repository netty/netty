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

import java.util.HashMap;
import java.util.Map;

/**
 * {@link OpenSslSessionCache} that is used by the client-side.
 */
final class OpenSslClientSessionCache extends OpenSslSessionCache {
    // TODO: Should we support to have a List of OpenSslSessions for a Host/Port key and so be able to
    // support sessions for different protocols / ciphers to the same remote peer ?
    private final Map<HostPort, NativeSslSession> sessions = new HashMap<HostPort, NativeSslSession>();

    OpenSslClientSessionCache(OpenSslEngineMap engineMap) {
        super(engineMap);
    }

    @Override
    protected boolean sessionCreated(NativeSslSession session) {
        assert Thread.holdsLock(this);
        HostPort hostPort = keyFor(session.getPeerHost(), session.getPeerPort());
        if (hostPort == null || sessions.containsKey(hostPort)) {
            return false;
        }
        sessions.put(hostPort, session);
        return true;
    }

    @Override
    protected void sessionRemoved(NativeSslSession session) {
        assert Thread.holdsLock(this);
        HostPort hostPort = keyFor(session.getPeerHost(), session.getPeerPort());
        if (hostPort == null) {
            return;
        }
        sessions.remove(hostPort);
    }

    @Override
    void setSession(long ssl, String host, int port) {
        HostPort hostPort = keyFor(host, port);
        if (hostPort == null) {
            return;
        }
        final NativeSslSession session;
        final boolean reused;
        synchronized (this) {
            session = sessions.get(hostPort);
            if (session == null) {
                return;
            }
            if (!session.isValid()) {
                removeSessionWithId(session.sessionId());
                return;
            }
            // Try to set the session, if true is returned OpenSSL incremented the reference count
            // of the underlying SSL_SESSION*.
            reused = SSL.setSession(ssl, session.session());
        }

        if (reused) {
            if (session.shouldBeSingleUse()) {
                // Should only be used once
                session.invalidate();
            }
            session.updateLastAccessedTime();
        }
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
     * Host / Port tuple used to find a {@link OpenSslSession} in the cache.
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
