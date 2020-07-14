/*
 * Copyright 2020 The Netty Project
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

import io.netty.util.AsciiString;

import javax.net.ssl.SSLException;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link OpenSslSessionCache} that is used by the client-side.
 */
final class OpenSslClientSessionCache extends OpenSslSessionCache {
    // TODO: Should we support to have a List of OpenSslSessions for a Host/Port key and so be able to
    // support sessions for different protocols / ciphers to the same remote peer ?
    private final Map<HostPort, OpenSslSession> sessions = new HashMap<HostPort, OpenSslSession>();

    OpenSslClientSessionCache(OpenSslEngineMap engineMap) {
        super(engineMap);
    }

    @Override
    protected boolean sessionCreated(OpenSslSession session) {
        assert Thread.holdsLock(this);
        String host = session.getPeerHost();
        int port = session.getPeerPort();
        if (host == null || port == -1) {
            return false;
        }
        HostPort hostPort = new HostPort(host, port);
        if (sessions.containsKey(hostPort)) {
            return false;
        }
        sessions.put(hostPort, session);
        return true;
    }

    @Override
    protected void sessionRemoved(OpenSslSession session) {
        assert Thread.holdsLock(this);
        String host = session.getPeerHost();
        int port = session.getPeerPort();
        if (host == null || port == -1) {
            return;
        }
        sessions.remove(new HostPort(host, port));
    }

    private static boolean isProtocolEnabled(OpenSslSession session, String[] enabledProtocols) {
        return arrayContains(session.getProtocol(), enabledProtocols);
    }

    private static boolean isCipherSuiteEnabled(OpenSslSession session, String[] enabledCipherSuites) {
        return arrayContains(session.getCipherSuite(), enabledCipherSuites);
    }

    private static boolean arrayContains(String expected, String[] array) {
        for (int i = 0; i < array.length; ++i) {
            String value = array[i];
            if (value.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    void setSession(ReferenceCountedOpenSslEngine engine) throws SSLException {
        String host = engine.getPeerHost();
        int port = engine.getPeerPort();
        if (host == null || port == -1) {
            return;
        }
        HostPort hostPort = new HostPort(host, port);
        synchronized (this) {
            OpenSslSession session = sessions.get(hostPort);

            if (session == null) {
                return;
            }
            if (!session.isValid()) {
                removeSession(session);
                return;
            }

            // Ensure the protocol and ciphersuite can be used.
            if (!isProtocolEnabled(session, engine.getEnabledProtocols()) ||
                    !isCipherSuiteEnabled(session, engine.getEnabledCipherSuites())) {
                return;
            }

            // Try to set the session, if true is returned we retained the session and incremented the reference count
            // of the underlying SSL_SESSION*.
            if (engine.setSession(session)) {
                session.updateLastAccessedTime();

                if (io.netty.internal.tcnative.SSLSession.shouldBeSingleUse(session.nativeAddr())) {
                    // Should only be re-used once so remove it from the cache
                    removeSession(session);
                }
            }
        }
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
    }
}
