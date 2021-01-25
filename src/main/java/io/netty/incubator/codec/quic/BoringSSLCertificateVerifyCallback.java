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
package io.netty.incubator.codec.quic;

import io.netty.handler.ssl.OpenSslCertificateException;

import javax.net.ssl.X509ExtendedTrustManager;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509Certificate;

final class BoringSSLCertificateVerifyCallback {

    private final QuicheQuicSslEngineMap engineMap;
    private final X509ExtendedTrustManager manager;

    BoringSSLCertificateVerifyCallback(QuicheQuicSslEngineMap engineMap, X509ExtendedTrustManager manager) {
        this.engineMap = engineMap;
        this.manager = manager;
    }

    @SuppressWarnings("unused")
    int verify(long ssl, byte[][] x509, String authAlgorithm) {
        final QuicheQuicSslEngine engine = engineMap.get(ssl);
        if (engine == null) {
            // May be null if it was destroyed in the meantime.
            return BoringSSL.X509_V_ERR_UNSPECIFIED;
        }

        if (manager == null) {
            engineMap.remove(ssl);
            return BoringSSL.X509_V_ERR_UNSPECIFIED;
        }

        X509Certificate[] peerCerts = BoringSSL.certificates(x509);
        try {
            if (engine.getUseClientMode()) {
                manager.checkServerTrusted(peerCerts, authAlgorithm, engine);
            } else {
                manager.checkClientTrusted(peerCerts, authAlgorithm, engine);
            }
            return BoringSSL.X509_V_OK;
        } catch (Throwable cause) {
            engineMap.remove(ssl);
            // Try to extract the correct error code that should be used.
            if (cause instanceof OpenSslCertificateException) {
                // This will never return a negative error code as its validated when constructing the
                // OpenSslCertificateException.
                return ((OpenSslCertificateException) cause).errorCode();
            }
            if (cause instanceof CertificateExpiredException) {
                return BoringSSL.X509_V_ERR_CERT_HAS_EXPIRED;
            }
            if (cause instanceof CertificateNotYetValidException) {
                return BoringSSL.X509_V_ERR_CERT_NOT_YET_VALID;
            }
            return translateToError(cause);
        }
    }

    private static int translateToError(Throwable cause) {
        if (cause instanceof CertificateRevokedException) {
            return BoringSSL.X509_V_ERR_CERT_REVOKED;
        }

        // The X509TrustManagerImpl uses a Validator which wraps a CertPathValidatorException into
        // an CertificateException. So we need to handle the wrapped CertPathValidatorException to be
        // able to send the correct alert.
        Throwable wrapped = cause.getCause();
        while (wrapped != null) {
            if (wrapped instanceof CertPathValidatorException) {
                CertPathValidatorException ex = (CertPathValidatorException) wrapped;
                CertPathValidatorException.Reason reason = ex.getReason();
                if (reason == CertPathValidatorException.BasicReason.EXPIRED) {
                    return BoringSSL.X509_V_ERR_CERT_HAS_EXPIRED;
                }
                if (reason == CertPathValidatorException.BasicReason.NOT_YET_VALID) {
                    return BoringSSL.X509_V_ERR_CERT_NOT_YET_VALID;
                }
                if (reason == CertPathValidatorException.BasicReason.REVOKED) {
                    return BoringSSL.X509_V_ERR_CERT_REVOKED;
                }
            }
            wrapped = wrapped.getCause();
        }
        return BoringSSL.X509_V_ERR_UNSPECIFIED;
    }
}
