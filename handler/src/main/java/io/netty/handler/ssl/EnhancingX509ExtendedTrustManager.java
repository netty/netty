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
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

/**
 * Wraps an existing {@link X509ExtendedTrustManager} and enhances the {@link CertificateException} that is thrown
 * because of hostname validation.
 */
final class EnhancingX509ExtendedTrustManager extends X509ExtendedTrustManager {

    // Constants for subject alt names of type DNS and IP. See X509Certificate#getSubjectAlternativeNames() javadocs.
    static final int ALTNAME_DNS = 2;
    static final int ALTNAME_URI = 6;
    static final int ALTNAME_IP = 7;
    private static final String SEPARATOR = ", ";

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
            throwEnhancedCertificateException(e, chain,
                    socket instanceof SSLSocket ? ((SSLSocket) socket).getHandshakeSession() : null);
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
            throwEnhancedCertificateException(e, chain, engine != null ? engine.getHandshakeSession() : null);
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
            throwEnhancedCertificateException(e, chain, null);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return wrapped.getAcceptedIssuers();
    }

    private static void throwEnhancedCertificateException(CertificateException e, X509Certificate[] chain,
                                                          SSLSession session) throws CertificateException {
        // Matching the message is the best we can do sadly.
        String message = e.getMessage();
        if (message != null &&
                (message.startsWith("No subject alternative") || message.startsWith("No name matching"))) {
            StringBuilder sb = new StringBuilder(128);
            sb.append(message);
            // Some exception messages from sun.security.util.HostnameChecker may end with a dot that we don't need
            if (message.charAt(message.length() - 1) == '.') {
                sb.setLength(sb.length() - 1);
            }
            if (session != null) {
                sb.append(" for SNIHostName=").append(getSNIHostName(session))
                        .append(" and peerHost=").append(session.getPeerHost());
            }
            sb.append(" in the chain of ").append(chain.length).append(" certificate(s):");
            for (int i = 0; i < chain.length; i++) {
                X509Certificate cert = chain[i];
                Collection<List<?>> collection = cert.getSubjectAlternativeNames();
                sb.append(' ').append(i + 1).append(". subjectAlternativeNames=[");
                if (collection != null) {
                    boolean hasNames = false;
                    for (List<?> altNames : collection) {
                        if (altNames.size() < 2) {
                            // We expect at least a pair of 'nameType:value' in that list.
                            continue;
                        }
                        final int nameType = ((Integer) altNames.get(0)).intValue();
                        if (nameType == ALTNAME_DNS) {
                            sb.append("DNS");
                        } else if (nameType == ALTNAME_IP) {
                            sb.append("IP");
                        } else if (nameType == ALTNAME_URI) {
                            // URI names are common in some environments with gRPC services that use SPIFFEs.
                            // Though the hostname matcher won't be looking at them, having them there can help
                            // debugging cases where hostname verification was enabled when it shouldn't be.
                            sb.append("URI");
                        } else {
                            continue;
                        }
                        sb.append(':').append((String) altNames.get(1)).append(SEPARATOR);
                        hasNames = true;
                    }
                    if (hasNames) {
                        // Strip of the last separator
                        sb.setLength(sb.length() - SEPARATOR.length());
                    }
                }
                sb.append("], CN=").append(getCommonName(cert)).append('.');
            }
            throw new CertificateException(sb.toString(), e);
        }
        throw e;
    }

    private static String getSNIHostName(SSLSession session) {
        if (!(session instanceof ExtendedSSLSession)) {
            return null;
        }
        List<SNIServerName> names = ((ExtendedSSLSession) session).getRequestedServerNames();
        for (SNIServerName sni : names) {
            if (sni instanceof SNIHostName) {
                SNIHostName hostName = (SNIHostName) sni;
                return hostName.getAsciiName();
            }
        }
        return null;
    }

    private static String getCommonName(X509Certificate cert) {
        try {
            // 1. Get the X500Principal (better than getSubjectDN which is implementation dependent and deprecated)
            X500Principal principal = cert.getSubjectX500Principal();
            // 2. Parse the DN using LdapName
            LdapName ldapName = new LdapName(principal.getName());
            // 3. Iterate over the Relative Distinguished Names (RDNs) to find CN
            for (Rdn rdn : ldapName.getRdns()) {
                if (rdn.getType().equalsIgnoreCase("CN")) {
                    return rdn.getValue().toString();
                }
            }
        } catch (Exception ignore) {
            // ignore
        }
        return "null";
    }
}
