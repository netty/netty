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

import io.netty.internal.tcnative.SSLSession;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

final class DefaultOpenSslSession extends AbstractReferenceCounted implements ReferenceCounted, OpenSslSession {

    private final OpenSslSessionContext sessionContext;
    private final String peerHost;
    private final int peerPort;
    private final OpenSslSessionId id;
    private final X509Certificate[] x509PeerCerts;
    private final Certificate[] peerCerts;
    private final String protocol;
    private final String cipher;
    private final long sslSession;
    private final long creationTime;
    private final long timeout;

    private volatile int applicationBufferSize = ReferenceCountedOpenSslEngine.MAX_PLAINTEXT_LENGTH;
    private volatile int packetBufferSize = ReferenceCountedOpenSslEngine.MAX_RECORD_SIZE;
    private volatile long lastAccessed;
    private volatile Certificate[] localCertificateChain;
    private volatile boolean invalid;

    // Guarded by synchronized(this)
    // lazy init for memory reasons
    private Map<String, Object> values;

    DefaultOpenSslSession(OpenSslSessionContext sessionContext, String peerHost, int peerPort, long sslSession,
                          String version,
                          String cipher,
                          OpenSslJavaxX509Certificate[] x509PeerCerts,
                          long creationTime, long timeout) {
        this.sessionContext = sessionContext;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.id = new OpenSslSessionId(id(sslSession));
        this.sslSession = sslSession;
        this.cipher = cipher == null ? SslUtils.INVALID_CIPHER : cipher;
        this.x509PeerCerts = x509PeerCerts;
        if (x509PeerCerts != null) {
            peerCerts = new Certificate[x509PeerCerts.length];
            for (int i = 0; i < peerCerts.length; i++) {
                peerCerts[i] = new OpenSslX509Certificate(x509PeerCerts[i].getBytes());
            }
        } else {
            peerCerts = null;
        }
        this.protocol = version == null ? StringUtil.EMPTY_STRING : version;
        this.creationTime = creationTime;
        this.lastAccessed = creationTime;
        this.timeout = timeout;
    }

    private static byte[] id(long sslSession) {
        if (sslSession == -1) {
            return EmptyArrays.EMPTY_BYTES;
        }
        byte[] id = io.netty.internal.tcnative.SSLSession.getSessionId(sslSession);
        return id == null ? EmptyArrays.EMPTY_BYTES : id;
    }

    @Override
    public OpenSslSessionContext getSessionContext() {
        return sessionContext;
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
                values = this.values = new HashMap<String, Object>(2);
            }
            old = values.put(name, value);
        }

        if (value instanceof SSLSessionBindingListener) {
            ((SSLSessionBindingListener) value).valueBound(new SSLSessionBindingEvent(this, name));
        }
        notifyUnbound(old, name);
    }

    @Override
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
            return values.keySet().toArray(EmptyArrays.EMPTY_STRINGS);
        }
    }

    private void notifyUnbound(Object value, String name) {
        if (value instanceof SSLSessionBindingListener) {
            ((SSLSessionBindingListener) value).valueUnbound(new SSLSessionBindingEvent(this, name));
        }
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
    public OpenSslSessionId sessionId() {
        return id;
    }

    @Override
    public byte[] getId() {
        return sessionId().cloneBytes();
    }

    @Override
    public int hashCode() {
        return sessionId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OpenSslSession)) {
            return false;
        }
        return sessionId().equals(((OpenSslSession) obj).sessionId());
    }

    @Override
    public boolean isNullSession() {
        return false;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public long getLastAccessedTime() {
        return lastAccessed;
    }

    @Override
    public void invalidate() {
        invalid = true;
    }

    @Override
    public boolean isValid() {
        if (sslSession == -1 || invalid) {
            return false;
        }

        // Never timeout
        if (timeout == 0) {
            return true;
        }

        long current = System.currentTimeMillis();
        return current - timeout < creationTime;
    }

    @Override
    public long nativeAddr() {
        return sslSession;
    }

    @Override
    public void setLocalCertificate(Certificate[] localCertificate) {
        this.localCertificateChain = localCertificate;
    }

    @Override
    public void setPacketBufferSize(int packetBufferSize) {
        this.packetBufferSize = packetBufferSize;
    }

    @Override
    public void updateLastAccessedTime() {
        lastAccessed = System.currentTimeMillis();
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        if (SslUtils.isEmpty(peerCerts)) {
            throw new SSLPeerUnverifiedException("peer not verified");
        }
        return peerCerts.clone();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        Certificate[] localCerts = localCertificateChain;
        if (localCerts == null) {
            return null;
        }
        return localCerts.clone();
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        if (SslUtils.isEmpty(x509PeerCerts)) {
            throw new SSLPeerUnverifiedException("peer not verified");
        }
        return x509PeerCerts.clone();
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        Certificate[] peer = getPeerCertificates();
        // No need for null or length > 0 is needed as this is done in getPeerCertificates()
        // already.
        return ((java.security.cert.X509Certificate) peer[0]).getSubjectX500Principal();
    }

    @Override
    public Principal getLocalPrincipal() {
        Certificate[] local = localCertificateChain;
        if (SslUtils.isEmpty(local)) {
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
    public int getPacketBufferSize() {
        return packetBufferSize;
    }

    @Override
    public int getApplicationBufferSize() {
        return applicationBufferSize;
    }

    @Override
    public void tryExpandApplicationBufferSize(int packetLengthDataOnly) {
        if (packetLengthDataOnly > ReferenceCountedOpenSslEngine.MAX_PLAINTEXT_LENGTH &&
                applicationBufferSize != ReferenceCountedOpenSslEngine.MAX_RECORD_SIZE) {
            applicationBufferSize = ReferenceCountedOpenSslEngine.MAX_RECORD_SIZE;
        }
    }

    @Override
    protected void deallocate() {
        if (sslSession != -1) {
            SSLSession.free(sslSession);
        }
    }

    @Override
    public DefaultOpenSslSession touch() {
        super.touch();
        return this;
    }

    @Override
    public DefaultOpenSslSession touch(Object hint) {
        return this;
    }

    @Override
    public DefaultOpenSslSession retain() {
        super.retain();
        return this;
    }

    @Override
    public DefaultOpenSslSession retain(int increment) {
        super.retain(increment);
        return this;
    }
}
