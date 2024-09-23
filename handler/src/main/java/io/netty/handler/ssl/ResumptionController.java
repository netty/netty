/*
 * Copyright 2024 The Netty Project
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

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

final class ResumptionController {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ResumptionController.class);
    private static final Object ENTRY = new Object();
    private final Map<SSLEngine, Object> confirmedValidations;
    private final AtomicReference<ResumableX509ExtendedTrustManager> resumableTm;
    private final boolean isClient;
    private final boolean logResumptionFailure;

    ResumptionController(boolean isClient, boolean logResumptionFailure) {
        this.isClient = isClient;
        this.logResumptionFailure = logResumptionFailure;
        confirmedValidations = Collections.synchronizedMap(new WeakHashMap<SSLEngine, Object>());
        resumableTm = new AtomicReference<ResumableX509ExtendedTrustManager>();
    }

    @SuppressJava6Requirement(reason = "Guarded by version check")
    public TrustManager wrapIfNeeded(TrustManager tm) {
        if (tm instanceof ResumableX509ExtendedTrustManager) {
            if (PlatformDependent.javaVersion() < 7 || !(tm instanceof X509ExtendedTrustManager)) {
                throw new IllegalStateException(
                        "Resumable trust manager must be a subclass of X509ExtendedTrustManager");
            }
            if (!resumableTm.compareAndSet(null, (ResumableX509ExtendedTrustManager) tm)) {
                throw new IllegalStateException("Only one TrustManager can be configured for resumed sessions");
            }
            return new X509ExtendedWrapTrustManager((X509ExtendedTrustManager) tm);
        } else if (logResumptionFailure) {
            logger.debug("Not wrapping trust manager {} of type {}", tm, tm != null ? tm.getClass() : null);
        }
        return tm;
    }

    public void remove(SSLEngine engine) {
        if (resumableTm.get() != null) {
            confirmedValidations.remove(unwrapEngine(engine));
        }
    }

    public boolean validateResumeIfNeeded(SSLEngine engine)
            throws CertificateException, SSLPeerUnverifiedException {
        ResumableX509ExtendedTrustManager tm;
        boolean valid = engine.getSession().isValid();
        if ((tm = resumableTm.get()) != null && valid) {
            Certificate[] peerCertificates = engine.getSession().getPeerCertificates();
            engine = unwrapEngine(engine);
            if (confirmedValidations.remove(engine) == null) {
                // This is a resumed session.
                if (isClient) {
                    tm.resumeClientTrusted(chainOf(peerCertificates), engine);
                } else {
                    tm.resumeServerTrusted(chainOf(peerCertificates), engine);
                }
                return true;
            }
        } else if (logResumptionFailure) {
            logger.debug("Not resuming session, tm={}, valid={}", tm, valid); // TODO remove this logging
        }
        return false;
    }

    private static SSLEngine unwrapEngine(SSLEngine engine) {
        if (engine instanceof JdkSslEngine) {
            return ((JdkSslEngine) engine).getWrappedEngine();
        }
        return engine;
    }

    private static X509Certificate[] chainOf(Certificate[] peerCertificates) {
        if (peerCertificates instanceof X509Certificate[]) {
            //noinspection SuspiciousArrayCast
            return (X509Certificate[]) peerCertificates;
        }
        X509Certificate[] chain = new X509Certificate[peerCertificates.length];
        for (int i = 0; i < peerCertificates.length; i++) {
            chain[i] = (X509Certificate) peerCertificates[i];
        }
        return chain;
    }

    @SuppressJava6Requirement(reason = "Guarded by version check")
    private class X509ExtendedWrapTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager trustManager;

        X509ExtendedWrapTrustManager(X509ExtendedTrustManager trustManager) {
            this.trustManager = ObjectUtil.checkNotNull(trustManager, "trustManager");
        }

        private void unsupported() throws CertificateException {
            throw new CertificateException("Resumable trust managers require the SSLEngine parameter");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            unsupported();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            unsupported();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            unsupported();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            unsupported();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            trustManager.checkClientTrusted(chain, authType, engine);
            confirmedValidations.put(engine, ENTRY);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            trustManager.checkServerTrusted(chain, authType, engine);
            confirmedValidations.put(engine, ENTRY);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }
    }
}
