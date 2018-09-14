/*
 * Copyright 2018 The Netty Project
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

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;

/**
 * Delegates all operations to a wrapped {@link OpenSslSession} except the methods defined by {@link ExtendedSSLSession}
 * itself.
 */
abstract class ExtendedOpenSslSession extends ExtendedSSLSession implements OpenSslSession {

    // TODO: use OpenSSL API to actually fetch the real data but for now just do what Conscrypt does:
    // https://github.com/google/conscrypt/blob/1.2.0/common/
    // src/main/java/org/conscrypt/Java7ExtendedSSLSession.java#L32
    private static final String[] LOCAL_SUPPORTED_SIGNATURE_ALGORITHMS = {
            "SHA512withRSA", "SHA512withECDSA", "SHA384withRSA", "SHA384withECDSA", "SHA256withRSA",
            "SHA256withECDSA", "SHA224withRSA", "SHA224withECDSA", "SHA1withRSA", "SHA1withECDSA",
    };

    private final OpenSslSession wrapped;

    ExtendedOpenSslSession(OpenSslSession wrapped) {
        assert !(wrapped instanceof ExtendedSSLSession);
        this.wrapped = wrapped;
    }

    // Use rawtypes an unchecked override to be able to also work on java7.
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public abstract List getRequestedServerNames();

    @Override
    public void handshakeFinished() throws SSLException {
        wrapped.handshakeFinished();
    }

    @Override
    public void tryExpandApplicationBufferSize(int packetLengthDataOnly) {
        wrapped.tryExpandApplicationBufferSize(packetLengthDataOnly);
    }

    @Override
    public String[] getLocalSupportedSignatureAlgorithms() {
        return LOCAL_SUPPORTED_SIGNATURE_ALGORITHMS.clone();
    }

    @Override
    public String[] getPeerSupportedSignatureAlgorithms() {
        // Always return empty for now.
        return EmptyArrays.EMPTY_STRINGS;
    }

    @Override
    public byte[] getId() {
        return wrapped.getId();
    }

    @Override
    public SSLSessionContext getSessionContext() {
        return wrapped.getSessionContext();
    }

    @Override
    public long getCreationTime() {
        return wrapped.getCreationTime();
    }

    @Override
    public long getLastAccessedTime() {
        return wrapped.getLastAccessedTime();
    }

    @Override
    public void invalidate() {
        wrapped.invalidate();
    }

    @Override
    public boolean isValid() {
        return wrapped.isValid();
    }

    @Override
    public void putValue(String s, Object o) {
        wrapped.putValue(s, o);
    }

    @Override
    public Object getValue(String s) {
        return wrapped.getValue(s);
    }

    @Override
    public void removeValue(String s) {
        wrapped.removeValue(s);
    }

    @Override
    public String[] getValueNames() {
        return wrapped.getValueNames();
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return wrapped.getPeerCertificates();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return wrapped.getLocalCertificates();
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        return wrapped.getPeerCertificateChain();
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return wrapped.getPeerPrincipal();
    }

    @Override
    public Principal getLocalPrincipal() {
        return wrapped.getLocalPrincipal();
    }

    @Override
    public String getCipherSuite() {
        return wrapped.getCipherSuite();
    }

    @Override
    public String getProtocol() {
        return wrapped.getProtocol();
    }

    @Override
    public String getPeerHost() {
        return wrapped.getPeerHost();
    }

    @Override
    public int getPeerPort() {
        return wrapped.getPeerPort();
    }

    @Override
    public int getPacketBufferSize() {
        return wrapped.getPacketBufferSize();
    }

    @Override
    public int getApplicationBufferSize() {
        return wrapped.getApplicationBufferSize();
    }
}
