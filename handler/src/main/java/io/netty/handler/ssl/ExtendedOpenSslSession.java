/*
 * Copyright 2018 The Netty Project
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

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.SuppressJava6Requirement;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;

/**
 * Delegates all operations to a wrapped {@link OpenSslSession} except the methods defined by {@link ExtendedSSLSession}
 * itself.
 */
@SuppressJava6Requirement(reason = "Usage guarded by java version check")
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
        this.wrapped = wrapped;
    }

    // Use rawtypes an unchecked override to be able to also work on java7.
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public abstract List getRequestedServerNames();

    // Do not mark as override so we can compile on java8.
    public List<byte[]> getStatusResponses() {
        // Just return an empty list for now until we support it as otherwise we will fail in java9
        // because of their sun.security.ssl.X509TrustManagerImpl class.
        return Collections.emptyList();
    }

    @Override
    public OpenSslSessionId sessionId() {
        return wrapped.sessionId();
    }

    @Override
    public void setSessionId(OpenSslSessionId id) {
        wrapped.setSessionId(id);
    }

    @Override
    public final void setLocalCertificate(Certificate[] localCertificate) {
        wrapped.setLocalCertificate(localCertificate);
    }

    @Override
    public String[] getPeerSupportedSignatureAlgorithms() {
        return EmptyArrays.EMPTY_STRINGS;
    }

    @Override
    public final void tryExpandApplicationBufferSize(int packetLengthDataOnly) {
        wrapped.tryExpandApplicationBufferSize(packetLengthDataOnly);
    }

    @Override
    public final String[] getLocalSupportedSignatureAlgorithms() {
        return LOCAL_SUPPORTED_SIGNATURE_ALGORITHMS.clone();
    }

    @Override
    public final byte[] getId() {
        return wrapped.getId();
    }

    @Override
    public final OpenSslSessionContext getSessionContext() {
        return wrapped.getSessionContext();
    }

    @Override
    public final long getCreationTime() {
        return wrapped.getCreationTime();
    }

    @Override
    public final long getLastAccessedTime() {
        return wrapped.getLastAccessedTime();
    }

    @Override
    public final void invalidate() {
        wrapped.invalidate();
    }

    @Override
    public final boolean isValid() {
        return wrapped.isValid();
    }

    @Override
    public final void putValue(String name, Object value) {
        if (value instanceof SSLSessionBindingListener) {
            // Decorate the value if needed so we submit the correct SSLSession instance
            value = new SSLSessionBindingListenerDecorator((SSLSessionBindingListener) value);
        }
        wrapped.putValue(name, value);
    }

    @Override
    public final Object getValue(String s) {
        Object value =  wrapped.getValue(s);
        if (value instanceof SSLSessionBindingListenerDecorator) {
            // Unwrap as needed so we return the original value
            return ((SSLSessionBindingListenerDecorator) value).delegate;
        }
        return value;
    }

    @Override
    public final void removeValue(String s) {
        wrapped.removeValue(s);
    }

    @Override
    public final String[] getValueNames() {
        return wrapped.getValueNames();
    }

    @Override
    public final Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return wrapped.getPeerCertificates();
    }

    @Override
    public final Certificate[] getLocalCertificates() {
        return wrapped.getLocalCertificates();
    }

    @Override
    public final X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        return wrapped.getPeerCertificateChain();
    }

    @Override
    public final Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return wrapped.getPeerPrincipal();
    }

    @Override
    public final Principal getLocalPrincipal() {
        return wrapped.getLocalPrincipal();
    }

    @Override
    public final String getCipherSuite() {
        return wrapped.getCipherSuite();
    }

    @Override
    public String getProtocol() {
        return wrapped.getProtocol();
    }

    @Override
    public final String getPeerHost() {
        return wrapped.getPeerHost();
    }

    @Override
    public final int getPeerPort() {
        return wrapped.getPeerPort();
    }

    @Override
    public final int getPacketBufferSize() {
        return wrapped.getPacketBufferSize();
    }

    @Override
    public final int getApplicationBufferSize() {
        return wrapped.getApplicationBufferSize();
    }

    private final class SSLSessionBindingListenerDecorator implements SSLSessionBindingListener {

        final SSLSessionBindingListener delegate;

        SSLSessionBindingListenerDecorator(SSLSessionBindingListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void valueBound(SSLSessionBindingEvent event) {
            delegate.valueBound(new SSLSessionBindingEvent(ExtendedOpenSslSession.this, event.getName()));
        }

        @Override
        public void valueUnbound(SSLSessionBindingEvent event) {
            delegate.valueUnbound(new SSLSessionBindingEvent(ExtendedOpenSslSession.this, event.getName()));
        }
    }

    @Override
    public void handshakeFinished(byte[] id, String cipher, String protocol, byte[] peerCertificate,
                                  byte[][] peerCertificateChain, long creationTime, long timeout) throws SSLException {
        wrapped.handshakeFinished(id, cipher, protocol, peerCertificate, peerCertificateChain, creationTime, timeout);
    }

    @Override
    public String toString() {
        return "ExtendedOpenSslSession{" +
                "wrapped=" + wrapped +
                '}';
    }
}
