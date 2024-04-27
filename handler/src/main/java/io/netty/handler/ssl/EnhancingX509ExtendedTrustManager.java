/*
 * Copyright 2023 The Netty Project
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

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Wraps an existing {@link X509ExtendedTrustManager} and enhances the {@link CertificateException} that is thrown
 * because of hostname validation.
 */
final class EnhancingX509ExtendedTrustManager extends X509ExtendedTrustManager {
    private final X509ExtendedTrustManager wrapped;

    EnhancingX509ExtendedTrustManager(X509TrustManager wrapped) {
        this.wrapped = (X509ExtendedTrustManager) wrapped;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        wrapped.checkClientTrusted(chain, authType, socket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        try {
            wrapped.checkServerTrusted(chain, authType, socket);
        } catch (CertificateException e) {
            throwEnhancedCertificateException(chain, e);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        wrapped.checkClientTrusted(chain, authType, engine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        try {
            wrapped.checkServerTrusted(chain, authType, engine);
        } catch (CertificateException e) {
            throwEnhancedCertificateException(chain, e);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        wrapped.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        try {
            wrapped.checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
            throwEnhancedCertificateException(chain, e);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return wrapped.getAcceptedIssuers();
    }

    private static void throwEnhancedCertificateException(X509Certificate[] chain, CertificateException e)
            throws CertificateException {
        // Matching the message is the best we can do sadly.
        String message = e.getMessage();
        if (message != null && e.getMessage().startsWith("No subject alternative DNS name matching")) {
            StringBuilder names = new StringBuilder(64);
            for (int i = 0; i < chain.length; i++) {
                X509Certificate cert = chain[i];
                Collection<List<?>> collection = cert.getSubjectAlternativeNames();
                if (collection != null) {
                    for (List<?> altNames : collection) {
                        // 2 is dNSName. See X509Certificate javadocs.
                        if (altNames.size() >= 2 && ((Integer) altNames.get(0)).intValue() == 2) {
                            names.append((String) altNames.get(1)).append(",");
                        }
                    }
                }
            }
            if (names.length() != 0) {
                // Strip of ,
                names.setLength(names.length() - 1);
                throw new CertificateException(message +
                        " Subject alternative DNS names in the certificate chain of " + chain.length +
                        " certificate(s): " + names, e);
            }
        }
        throw e;
    }
}
