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

import java.security.KeyStore;
import java.security.Provider;
import javax.net.ssl.KeyManager;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * A server-side {@link SslContext} which uses JDK's SSL/TLS implementation.
 *
 * @deprecated Use {@link SslContextBuilder} to create {@link JdkSslContext} instances and only
 * use {@link JdkSslContext} in your code.
 */
@Deprecated
public final class JdkSslServerContext extends JdkSslContext {

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
                keyPassword, null, sessionCacheSize, sessionTimeout, keyStore), false,
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
                keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout, null), false,
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
                keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout, KeyStore.getDefaultType()), false,
                ciphers, cipherFilter, apn, ClientAuth.NONE, null, false);
    }

    JdkSslServerContext(Provider provider,
                        X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
                        X509Certificate[] keyCertChain, PrivateKey key, String keyPassword,
                        KeyManagerFactory keyManagerFactory, Iterable<String> ciphers, CipherSuiteFilter cipherFilter,
                        ApplicationProtocolConfig apn, long sessionCacheSize, long sessionTimeout,
                        ClientAuth clientAuth, String[] protocols, boolean startTls,
                        String keyStore) throws SSLException {
        super(newSSLContext(provider, trustCertCollection, trustManagerFactory, keyCertChain, key,
                keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout, keyStore), false,
                ciphers, cipherFilter, toNegotiator(apn, true), clientAuth, protocols, startTls);
    }

    private static SSLContext newSSLContext(Provider sslContextProvider, X509Certificate[] trustCertCollection,
                                     TrustManagerFactory trustManagerFactory, X509Certificate[] keyCertChain,
                                     PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
                                     long sessionCacheSize, long sessionTimeout, String keyStore)
            throws SSLException {
        if (key == null && keyManagerFactory == null) {
            throw new NullPointerException("key, keyManagerFactory");
        }

        try {
            if (trustCertCollection != null) {
                trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory, keyStore);
            }
            if (key != null) {
                keyManagerFactory = buildKeyManagerFactory(keyCertChain, null,
                        key, keyPassword, keyManagerFactory, null);
            }

            // Initialize the SSLContext to work with our key managers.
            SSLContext ctx = sslContextProvider == null ? SSLContext.getInstance(PROTOCOL)
                : SSLContext.getInstance(PROTOCOL, sslContextProvider);
            ctx.init(keyManagerFactory.getKeyManagers(),
                     trustManagerFactory == null ? null : trustManagerFactory.getTrustManagers(),
                     null);

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

}
