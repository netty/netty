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

import io.netty.util.internal.EmptyArrays;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;

/**
 * Special {@link OpenSslSession} which represent a {@code NULL} session. This will be used prior the handshake is
 * started.
 */
final class OpenSslNullSession implements OpenSslSession {

    private final OpenSslSessionContext sessionContext;
    private final Certificate[] localCertificate;

    // The given array will never be mutated and so its ok to not clone it.
    OpenSslNullSession(OpenSslSessionContext sessionContext, Certificate[] localCertificate) {
        this.sessionContext = sessionContext;
        this.localCertificate = localCertificate;
    }

    @Override
    public OpenSslSessionId sessionId() {
        return OpenSslSessionId.NULL_ID;
    }

    @Override
    public OpenSslSessionContext getSessionContext() {
        return sessionContext;
    }

    @Override
    public byte[] getId() {
        return sessionId().cloneBytes();
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
    public String getPeerHost() {
        return null;
    }

    @Override
    public int getPeerPort() {
        return -1;
    }

    @Override
    public boolean isNullSession() {
        return true;
    }

    @Override
    public long nativeAddr() {
        return -1;
    }

    @Override
    public void setLocalCertificate(Certificate[] localCertificate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void tryExpandApplicationBufferSize(int packetLengthDataOnly) {
        // NOOP
    }

    @Override
    public void setPacketBufferSize(int packetBufferSize) {
        // NOOP
    }

    @Override
    public void updateLastAccessedTime() {
        // NOOP
    }

    @Override
    public long getCreationTime() {
        return 0;
    }

    @Override
    public long getLastAccessedTime() {
        return 0;
    }

    @Override
    public void invalidate() {
        // NOOP
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        throw new SSLPeerUnverifiedException("NULL session");
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return localCertificate == null ? null : localCertificate.clone();
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        throw new SSLPeerUnverifiedException("NULL session");
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        throw new SSLPeerUnverifiedException("NULL session");
    }

    @Override
    public Principal getLocalPrincipal() {
        Certificate[] local = getLocalCertificates();
        if (SslUtils.isEmpty(local)) {
            return null;
        }
        return ((java.security.cert.X509Certificate) local[0]).getIssuerX500Principal();
    }

    @Override
    public String getCipherSuite() {
        return SslUtils.INVALID_CIPHER;
    }

    @Override
    public String getProtocol() {
        return "NONE";
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
    public OpenSslSession retain() {
        return this;
    }

    @Override
    public OpenSslSession retain(int increment) {
        return this;
    }

    @Override
    public OpenSslSession touch() {
        return this;
    }

    @Override
    public OpenSslSession touch(Object hint) {
        return this;
    }

    @Override
    public int refCnt() {
        return 1;
    }

    @Override
    public boolean release() {
        return false;
    }

    @Override
    public boolean release(int decrement) {
        return false;
    }
}
