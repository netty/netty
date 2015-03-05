/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * A client-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 */
public final class OpenSslClientContext extends OpenSslContext {
    private final OpenSslSessionContext sessionContext;
    private final OpenSslEngineMap engineMap;

    /**
     * Creates a new instance.
     */
    public OpenSslClientContext() throws SSLException {
        this(null, null, null, IdentityCipherSuiteFilter.INSTANCE, null, 0, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     */
    public OpenSslClientContext(File certChainFile) throws SSLException {
        this(certChainFile, null);
    }

    /**
     * Creates a new instance.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     */
    public OpenSslClientContext(TrustManagerFactory trustManagerFactory) throws SSLException {
        this(null, trustManagerFactory);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     */
    public OpenSslClientContext(File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        this(certChainFile, trustManagerFactory, null, IdentityCipherSuiteFilter.INSTANCE, null, 0, 0);
    }

    /**
     * @deprecated use {@link #OpenSslClientContext(File, TrustManagerFactory, Iterable,
     *             CipherSuiteFilter, ApplicationProtocolConfig, long, long)}
     *
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default..
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     */
    @Deprecated
    public OpenSslClientContext(File certChainFile, TrustManagerFactory trustManagerFactory, Iterable<String> ciphers,
                                ApplicationProtocolConfig apn, long sessionCacheSize, long sessionTimeout)
            throws SSLException {
        this(certChainFile, trustManagerFactory, ciphers, IdentityCipherSuiteFilter.INSTANCE,
                apn, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default..
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     */
    public OpenSslClientContext(File certChainFile, TrustManagerFactory trustManagerFactory, Iterable<String> ciphers,
                                CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
                                long sessionCacheSize, long sessionTimeout)
            throws SSLException {
        super(ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout, SSL.SSL_MODE_CLIENT);
        boolean success = false;
        try {
            if (certChainFile != null && !certChainFile.isFile()) {
                throw new IllegalArgumentException("certChainFile is not a file: " + certChainFile);
            }

            synchronized (OpenSslContext.class) {
                if (certChainFile != null) {
                    /* Load the certificate chain. We must skip the first cert when server mode */
                    if (!SSLContext.setCertificateChainFile(ctx, certChainFile.getPath(), true)) {
                        long error = SSL.getLastErrorNumber();
                        if (OpenSsl.isError(error)) {
                            throw new SSLException(
                                    "failed to set certificate chain: "
                                            + certChainFile + " (" + SSL.getErrorString(error) + ')');
                        }
                    }
                }
                SSLContext.setVerify(ctx, SSL.SSL_VERIFY_NONE, VERIFY_DEPTH);

                try {
                    // Set up trust manager factory to use our key store.
                    if (trustManagerFactory == null) {
                        trustManagerFactory = TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());
                    }
                    initTrustManagerFactory(certChainFile, trustManagerFactory);
                    final X509TrustManager manager = chooseTrustManager(trustManagerFactory.getTrustManagers());

                    engineMap = newEngineMap(manager);

                    // Use this to prevent an error when running on java < 7
                    if (useExtendedTrustManager(manager)) {
                        final X509ExtendedTrustManager extendedManager = (X509ExtendedTrustManager) manager;
                        SSLContext.setCertVerifyCallback(ctx, new AbstractCertificateVerifier() {
                            @Override
                            void verify(long ssl, X509Certificate[] peerCerts, String auth) throws Exception {
                                OpenSslEngine engine = engineMap.remove(ssl);
                                extendedManager.checkServerTrusted(peerCerts, auth, engine);
                            }
                        });
                    } else {
                        SSLContext.setCertVerifyCallback(ctx, new AbstractCertificateVerifier() {
                            @Override
                            void verify(long ssl, X509Certificate[] peerCerts, String auth) throws Exception {
                                manager.checkServerTrusted(peerCerts, auth);
                            }
                        });
                    }
                } catch (Exception e) {
                    throw new SSLException("unable to setup trustmanager", e);
                }
            }
            sessionContext = new OpenSslClientSessionContext(ctx);
            success = true;
        } finally {
            if (!success) {
                destroyPools();
            }
        }
    }

    private static void initTrustManagerFactory(File certChainFile, TrustManagerFactory trustManagerFactory)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        if (certChainFile != null) {
            ByteBuf[] certs = PemReader.readCertificates(certChainFile);
            try {
                for (ByteBuf buf: certs) {
                    X509Certificate cert = (X509Certificate) X509_CERT_FACTORY.generateCertificate(
                            new ByteBufInputStream(buf));
                    X500Principal principal = cert.getSubjectX500Principal();
                    ks.setCertificateEntry(principal.getName("RFC2253"), cert);
                }
            } finally {
                for (ByteBuf buf: certs) {
                    buf.release();
                }
            }
        }
        trustManagerFactory.init(ks);
    }

    @Override
    public OpenSslSessionContext sessionContext() {
        return sessionContext;
    }

    @Override
    OpenSslEngineMap engineMap() {
        return engineMap;
    }

    // No cache is currently supported for client side mode.
    private static final class OpenSslClientSessionContext extends OpenSslSessionContext {
        private OpenSslClientSessionContext(long context) {
            super(context);
        }

        @Override
        public void setSessionTimeout(int seconds) {
            if (seconds < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public int getSessionTimeout() {
            return 0;
        }

        @Override
        public void setSessionCacheSize(int size)  {
            if (size < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public int getSessionCacheSize() {
            return 0;
        }

        @Override
        public void setSessionCacheEnabled(boolean enabled) {
            // ignored
        }

        @Override
        public boolean isSessionCacheEnabled() {
            return false;
        }
    }
}
