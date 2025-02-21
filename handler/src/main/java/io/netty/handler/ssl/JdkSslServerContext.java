/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.CharsetUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import static io.netty.handler.ssl.SslUtils.PROBING_CERT;
import static io.netty.handler.ssl.SslUtils.PROBING_KEY;

/**
 * A server-side {@link SslContext} which uses JDK's SSL/TLS implementation.
 *
 * @deprecated Use {@link SslContextBuilder} to create {@link JdkSslContext} instances and only
 * use {@link JdkSslContext} in your code.
 */
@Deprecated
public final class JdkSslServerContext extends JdkSslContext {

    private static final boolean WRAP_TRUST_MANAGER;
    static {
        boolean wrapTrustManager = false;
        try {
            checkIfWrappingTrustManagerIsSupported();
            wrapTrustManager = true;
        } catch (Throwable ignore) {
            // Just don't wrap as we might not be able to do so because of FIPS:
            // See https://github.com/netty/netty/issues/13840
        }
        WRAP_TRUST_MANAGER = wrapTrustManager;
    }

    // Package-private for testing.
    static void checkIfWrappingTrustManagerIsSupported() throws CertificateException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeySpecException, IOException, KeyException, KeyStoreException, UnrecoverableKeyException {
        X509Certificate[] certs = toX509Certificates(
                new ByteArrayInputStream(PROBING_CERT.getBytes(CharsetUtil.US_ASCII)));
        PrivateKey privateKey = toPrivateKey(new ByteArrayInputStream(
                PROBING_KEY.getBytes(CharsetUtil.UTF_8)), null);
        char[] keyStorePassword = keyStorePassword(null);
        KeyStore ks = buildKeyStore(certs, privateKey, keyStorePassword, null);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyStorePassword);

        SSLContext ctx = SSLContext.getInstance(PROTOCOL);
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init((KeyStore) null);
        TrustManager[] managers = tm.getTrustManagers();

        ctx.init(kmf.getKeyManagers(), wrapTrustManagerIfNeeded(managers, null), null);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @deprecated use {@link SslContextBuilder}
     */
    @Deprecated
    public JdkSslServerContext(File certChainFile, File keyFile) throws SSLException {
        this(null, certChainFile, keyFile, null, null, IdentityCipherSuiteFilter.INSTANCE,
                JdkDefaultApplicationProtocolNegotiator.INSTANCE, 0, 0, null);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @deprecated use {@link SslContextBuilder}
     */
    @Deprecated
    public JdkSslServerContext(File certChainFile, File keyFile, String keyPassword) throws SSLException {
        this(certChainFile, keyFile, keyPassword, null, IdentityCipherSuiteFilter.INSTANCE,
                JdkDefaultApplicationProtocolNegotiator.INSTANCE, 0, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @deprecated use {@link SslContextBuilder}
     */
    @Deprecated
    public JdkSslServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        this(null, certChainFile, keyFile, keyPassword, ciphers, IdentityCipherSuiteFilter.INSTANCE,
                toNegotiator(toApplicationProtocolConfig(nextProtocols), true), sessionCacheSize,
                sessionTimeout, KeyStore.getDefaultType());
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @deprecated use {@link SslContextBuilder}
     */
    @Deprecated
    public JdkSslServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        this(null, certChainFile, keyFile, keyPassword, ciphers, cipherFilter,
                toNegotiator(apn, true), sessionCacheSize, sessionTimeout, KeyStore.getDefaultType());
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     * @param apn Application Protocol Negotiator object.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @deprecated use {@link SslContextBuilder}
     */
    @Deprecated
    public JdkSslServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, JdkApplicationProtocolNegotiator apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        this(null, certChainFile, keyFile, keyPassword, ciphers, cipherFilter, apn,
                sessionCacheSize, sessionTimeout, KeyStore.getDefaultType());
    }

    JdkSslServerContext(Provider provider,
                        File certChainFile, File keyFile, String keyPassword,
                        Iterable<String> ciphers, CipherSuiteFilter cipherFilter, JdkApplicationProtocolNegotiator apn,
                        long sessionCacheSize, long sessionTimeout, String keyStore) throws SSLException {
        super(newSSLContext(provider, null, null,
                toX509CertificatesInternal(certChainFile), toPrivateKeyInternal(keyFile, keyPassword),
                keyPassword, null, sessionCacheSize, sessionTimeout, null, keyStore, null), false,
                ciphers, cipherFilter, apn, ClientAuth.NONE, null, false);
    }

    /**
     * Creates a new instance.
     * @param trustCertCollectionFile an X.509 certificate collection file in PEM format.
     *                      This provides the certificate collection used for mutual authentication.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from clients.
     *                            {@code null} to use the default or the results of parsing
     *                            {@code trustCertCollectionFile}.
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param keyManagerFactory the {@link KeyManagerFactory} that provides the {@link KeyManager}s
     *                          that is used to encrypt data being sent to clients.
     *                          {@code null} to use the default or the results of parsing
     *                          {@code keyCertChainFile} and {@code keyFile}.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     *                Only required if {@code provider} is {@link SslProvider#JDK}
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @deprecated use {@link SslContextBuilder}
     */
    @Deprecated
    public JdkSslServerContext(File trustCertCollectionFile, TrustManagerFactory trustManagerFactory,
                               File keyCertChainFile, File keyFile, String keyPassword,
                               KeyManagerFactory keyManagerFactory,
                               Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
                               long sessionCacheSize, long sessionTimeout) throws SSLException {
        super(newSSLContext(null, toX509CertificatesInternal(trustCertCollectionFile), trustManagerFactory,
                toX509CertificatesInternal(keyCertChainFile), toPrivateKeyInternal(keyFile, keyPassword),
                keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout, null, null, null), false,
                ciphers, cipherFilter, apn, ClientAuth.NONE, null, false);
    }

    /**
     * Creates a new instance.
     * @param trustCertCollectionFile an X.509 certificate collection file in PEM format.
     *                      This provides the certificate collection used for mutual authentication.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from clients.
     *                            {@code null} to use the default or the results of parsing
     *                            {@code trustCertCollectionFile}
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param keyManagerFactory the {@link KeyManagerFactory} that provides the {@link KeyManager}s
     *                          that is used to encrypt data being sent to clients.
     *                          {@code null} to use the default or the results of parsing
     *                          {@code keyCertChainFile} and {@code keyFile}.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     *                Only required if {@code provider} is {@link SslProvider#JDK}
     * @param apn Application Protocol Negotiator object.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value
     * @deprecated use {@link SslContextBuilder}
     */
    @Deprecated
    public JdkSslServerContext(File trustCertCollectionFile, TrustManagerFactory trustManagerFactory,
                               File keyCertChainFile, File keyFile, String keyPassword,
                               KeyManagerFactory keyManagerFactory,
                               Iterable<String> ciphers, CipherSuiteFilter cipherFilter,
                                JdkApplicationProtocolNegotiator apn,
                               long sessionCacheSize, long sessionTimeout) throws SSLException {
        super(newSSLContext(null, toX509CertificatesInternal(trustCertCollectionFile), trustManagerFactory,
                toX509CertificatesInternal(keyCertChainFile), toPrivateKeyInternal(keyFile, keyPassword),
                keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout,
                        null, KeyStore.getDefaultType(), null), false,
                ciphers, cipherFilter, apn, ClientAuth.NONE, null, false);
    }

    JdkSslServerContext(Provider provider,
                        X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
                        X509Certificate[] keyCertChain, PrivateKey key, String keyPassword,
                        KeyManagerFactory keyManagerFactory, Iterable<String> ciphers, CipherSuiteFilter cipherFilter,
                        ApplicationProtocolConfig apn, long sessionCacheSize, long sessionTimeout,
                        ClientAuth clientAuth, String[] protocols, boolean startTls,
                        SecureRandom secureRandom, String keyStore, ResumptionController resumptionController)
            throws SSLException {
        super(newSSLContext(provider, trustCertCollection, trustManagerFactory, keyCertChain, key,
                        keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout, secureRandom, keyStore,
                        resumptionController),
                false, ciphers, cipherFilter, toNegotiator(apn, true), clientAuth, protocols, startTls, null,
                null, resumptionController);
    }

    private static SSLContext newSSLContext(Provider sslContextProvider, X509Certificate[] trustCertCollection,
                                            TrustManagerFactory trustManagerFactory, X509Certificate[] keyCertChain,
                                            PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
                                            long sessionCacheSize, long sessionTimeout, SecureRandom secureRandom,
                                            String keyStore, ResumptionController resumptionController)
            throws SSLException {
        if (key == null && keyManagerFactory == null) {
            throw new NullPointerException("key, keyManagerFactory");
        }

        try {
            if (trustCertCollection != null) {
                trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory, keyStore);
            } else if (trustManagerFactory == null) {
                // Mimic the way SSLContext.getInstance(KeyManager[], null, null) works
                trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
            }

            if (key != null) {
                keyManagerFactory = buildKeyManagerFactory(keyCertChain, null,
                        key, keyPassword, keyManagerFactory, null);
            }

            // Initialize the SSLContext to work with our key managers.
            SSLContext ctx = sslContextProvider == null ? SSLContext.getInstance(PROTOCOL)
                : SSLContext.getInstance(PROTOCOL, sslContextProvider);
            ctx.init(keyManagerFactory.getKeyManagers(),
                    wrapTrustManagerIfNeeded(trustManagerFactory.getTrustManagers(), resumptionController),
                     secureRandom);

            SSLSessionContext sessCtx = ctx.getServerSessionContext();
            if (sessionCacheSize > 0) {
                sessCtx.setSessionCacheSize((int) Math.min(sessionCacheSize, Integer.MAX_VALUE));
            }
            if (sessionTimeout > 0) {
                sessCtx.setSessionTimeout((int) Math.min(sessionTimeout, Integer.MAX_VALUE));
            }
            return ctx;
        } catch (Exception e) {
            if (e instanceof SSLException) {
                throw (SSLException) e;
            }
            throw new SSLException("failed to initialize the server-side SSL context", e);
        }
    }

    private static TrustManager[] wrapTrustManagerIfNeeded(
            TrustManager[] trustManagers, ResumptionController resumptionController) {
        if (WRAP_TRUST_MANAGER) {
            for (int i = 0; i < trustManagers.length; i++) {
                TrustManager tm = trustManagers[i];
                if (resumptionController != null) {
                    tm = resumptionController.wrapIfNeeded(tm);
                }
                if (tm instanceof X509ExtendedTrustManager) {
                    // Wrap the TrustManager to provide a better exception message for users to debug hostname
                    // validation failures.
                    trustManagers[i] = new EnhancingX509ExtendedTrustManager((X509ExtendedTrustManager) tm);
                }
            }
        }
        return trustManagers;
    }
}
