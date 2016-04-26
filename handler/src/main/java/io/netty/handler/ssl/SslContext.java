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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.util.internal.EmptyArrays;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

/**
 * A secure socket protocol implementation which acts as a factory for {@link SSLEngine} and {@link SslHandler}.
 * Internally, it is implemented via JDK's {@link SSLContext} or OpenSSL's {@code SSL_CTX}.
 *
 * <h3>Making your server support SSL/TLS</h3>
 * <pre>
 * // In your {@link ChannelInitializer}:
 * {@link ChannelPipeline} p = channel.pipeline();
 * {@link SslContext} sslCtx = {@link SslContextBuilder#forServer(File, File) SslContextBuilder.forServer(...)}.build();
 * p.addLast("ssl", {@link #newHandler(ByteBufAllocator) sslCtx.newHandler(channel.alloc())});
 * ...
 * </pre>
 *
 * <h3>Making your client support SSL/TLS</h3>
 * <pre>
 * // In your {@link ChannelInitializer}:
 * {@link ChannelPipeline} p = channel.pipeline();
 * {@link SslContext} sslCtx = {@link SslContextBuilder#forClient() SslContextBuilder.forClient()}.build();
 * p.addLast("ssl", {@link #newHandler(ByteBufAllocator, String, int) sslCtx.newHandler(channel.alloc(), host, port)});
 * ...
 * </pre>
 */
public abstract class SslContext {
    static final CertificateFactory X509_CERT_FACTORY;
    static {
        try {
            X509_CERT_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new IllegalStateException("unable to instance X.509 CertificateFactory", e);
        }
    }

    private final boolean startTls;

    /**
     * Returns the default server-side implementation provider currently in use.
     *
     * @return {@link SslProvider#OPENSSL} if OpenSSL is available. {@link SslProvider#JDK} otherwise.
     */
    public static SslProvider defaultServerProvider() {
        return defaultProvider();
    }

    /**
     * Returns the default client-side implementation provider currently in use.
     *
     * @return {@link SslProvider#OPENSSL} if OpenSSL is available. {@link SslProvider#JDK} otherwise.
     */
    public static SslProvider defaultClientProvider() {
        return defaultProvider();
    }

    private static SslProvider defaultProvider() {
        if (OpenSsl.isAvailable()) {
            return SslProvider.OPENSSL;
        } else {
            return SslProvider.JDK;
        }
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(File certChainFile, File keyFile) throws SSLException {
        return newServerContext(certChainFile, keyFile, null);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(
            File certChainFile, File keyFile, String keyPassword) throws SSLException {
        return newServerContext(null, certChainFile, keyFile, keyPassword);
    }

    /**
     * Creates a new server-side {@link SslContext}.
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
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        return newServerContext(
                null, certChainFile, keyFile, keyPassword,
                ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new server-side {@link SslContext}.
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
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newServerContext(
                null, certChainFile, keyFile, keyPassword,
                ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(
            SslProvider provider, File certChainFile, File keyFile) throws SSLException {
        return newServerContext(provider, certChainFile, keyFile, null);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(
            SslProvider provider, File certChainFile, File keyFile, String keyPassword) throws SSLException {
        return newServerContext(provider, certChainFile, keyFile, keyPassword, null, IdentityCipherSuiteFilter.INSTANCE,
                                null, 0, 0);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
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
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(
            SslProvider provider,
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newServerContext(provider, certChainFile, keyFile, keyPassword,
                ciphers, IdentityCipherSuiteFilter.INSTANCE,
                toApplicationProtocolConfig(nextProtocols), sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link javax.net.ssl.TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(
            SslProvider provider,
            File certChainFile, File keyFile, String keyPassword, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        return newServerContext(
                provider, null, trustManagerFactory, certChainFile, keyFile, keyPassword,
                null, ciphers, IdentityCipherSuiteFilter.INSTANCE,
                toApplicationProtocolConfig(nextProtocols), sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     *                Only required if {@code provider} is {@link SslProvider#JDK}
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(SslProvider provider,
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newServerContext(provider, null, null, certChainFile, keyFile, keyPassword, null,
                ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param trustCertCollectionFile an X.509 certificate collection file in PEM format.
     *                      This provides the certificate collection used for mutual authentication.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from clients.
     *                            {@code null} to use the default or the results of parsing
     *                            {@code trustCertCollectionFile}.
     *                            This parameter is ignored if {@code provider} is not {@link SslProvider#JDK}.
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param keyManagerFactory the {@link KeyManagerFactory} that provides the {@link KeyManager}s
     *                          that is used to encrypt data being sent to clients.
     *                          {@code null} to use the default or the results of parsing
     *                          {@code keyCertChainFile} and {@code keyFile}.
     *                          This parameter is ignored if {@code provider} is not {@link SslProvider#JDK}.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     *                Only required if {@code provider} is {@link SslProvider#JDK}
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @return a new server-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newServerContext(
            SslProvider provider,
            File trustCertCollectionFile, TrustManagerFactory trustManagerFactory,
            File keyCertChainFile, File keyFile, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        try {
            return newServerContextInternal(provider, toX509Certificates(trustCertCollectionFile), trustManagerFactory,
                                            toX509Certificates(keyCertChainFile),
                                            toPrivateKey(keyFile, keyPassword),
                                            keyPassword, keyManagerFactory, ciphers, cipherFilter, apn,
                                            sessionCacheSize, sessionTimeout, ClientAuth.NONE, false);
        } catch (Exception e) {
            if (e instanceof SSLException) {
                throw (SSLException) e;
            }
            throw new SSLException("failed to initialize the server-side SSL context", e);
        }
    }

    static SslContext newServerContextInternal(
            SslProvider provider,
            X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
            X509Certificate[] keyCertChain, PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout, ClientAuth clientAuth, boolean startTls) throws SSLException {

        if (provider == null) {
            provider = defaultServerProvider();
        }

        switch (provider) {
        case JDK:
            return new JdkSslServerContext(
                    trustCertCollection, trustManagerFactory, keyCertChain, key, keyPassword,
                    keyManagerFactory, ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout,
                    clientAuth, startTls);
        case OPENSSL:
            return new OpenSslServerContext(
                    trustCertCollection, trustManagerFactory, keyCertChain, key, keyPassword,
                    keyManagerFactory, ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout,
                    clientAuth, startTls);
        case OPENSSL_REFCNT:
            return new ReferenceCountedOpenSslServerContext(
                    trustCertCollection, trustManagerFactory, keyCertChain, key, keyPassword,
                    keyManagerFactory, ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout,
                    clientAuth, startTls);
        default:
            throw new Error(provider.toString());
        }
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext() throws SSLException {
        return newClientContext(null, null, null);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(File certChainFile) throws SSLException {
        return newClientContext(null, certChainFile);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(null, null, trustManagerFactory);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(
            File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(null, certChainFile, trustManagerFactory);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(
            File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newClientContext(
                null, certChainFile, trustManagerFactory,
                ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(
            File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newClientContext(
                null, certChainFile, trustManagerFactory,
                ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(SslProvider provider) throws SSLException {
        return newClientContext(provider, null, null);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(SslProvider provider, File certChainFile) throws SSLException {
        return newClientContext(provider, certChainFile, null);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(
            SslProvider provider, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(provider, null, trustManagerFactory);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(
            SslProvider provider, File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(provider, certChainFile, trustManagerFactory, null, IdentityCipherSuiteFilter.INSTANCE,
                null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(
            SslProvider provider,
            File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newClientContext(
                provider, certChainFile, trustManagerFactory, null, null, null, null,
                ciphers, IdentityCipherSuiteFilter.INSTANCE,
                toApplicationProtocolConfig(nextProtocols), sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(
            SslProvider provider,
            File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        return newClientContext(
                provider, certChainFile, trustManagerFactory, null, null, null, null,
                ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param trustCertCollectionFile an X.509 certificate collection file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default or the results of parsing
     *                            {@code trustCertCollectionFile}.
     *                            This parameter is ignored if {@code provider} is not {@link SslProvider#JDK}.
     * @param keyCertChainFile an X.509 certificate chain file in PEM format.
     *                      This provides the public key for mutual authentication.
     *                      {@code null} to use the system default
     * @param keyFile a PKCS#8 private key file in PEM format.
     *                      This provides the private key for mutual authentication.
     *                      {@code null} for no mutual authentication.
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     *                    Ignored if {@code keyFile} is {@code null}.
     * @param keyManagerFactory the {@link KeyManagerFactory} that provides the {@link KeyManager}s
     *                          that is used to encrypt data being sent to servers.
     *                          {@code null} to use the default or the results of parsing
     *                          {@code keyCertChainFile} and {@code keyFile}.
     *                          This parameter is ignored if {@code provider} is not {@link SslProvider#JDK}.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param cipherFilter a filter to apply over the supplied list of ciphers
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     *
     * @return a new client-side {@link SslContext}
     * @deprecated Replaced by {@link SslContextBuilder}
     */
    @Deprecated
    public static SslContext newClientContext(
            SslProvider provider,
            File trustCertCollectionFile, TrustManagerFactory trustManagerFactory,
            File keyCertChainFile, File keyFile, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        try {
            return newClientContextInternal(provider, toX509Certificates(trustCertCollectionFile), trustManagerFactory,
                                            toX509Certificates(keyCertChainFile), toPrivateKey(keyFile, keyPassword),
                                            keyPassword, keyManagerFactory, ciphers, cipherFilter,
                                            apn,
                                            sessionCacheSize, sessionTimeout);
        } catch (Exception e) {
            if (e instanceof SSLException) {
                throw (SSLException) e;
            }
            throw new SSLException("failed to initialize the client-side SSL context", e);
        }
    }

    static SslContext newClientContextInternal(
            SslProvider provider,
            X509Certificate[] trustCert, TrustManagerFactory trustManagerFactory,
            X509Certificate[] keyCertChain, PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        if (provider == null) {
            provider = defaultClientProvider();
        }
        switch (provider) {
            case JDK:
                return new JdkSslClientContext(
                        trustCert, trustManagerFactory, keyCertChain, key, keyPassword,
                        keyManagerFactory, ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
            case OPENSSL:
                return new OpenSslClientContext(
                        trustCert, trustManagerFactory, keyCertChain, key, keyPassword,
                        keyManagerFactory, ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
            case OPENSSL_REFCNT:
                return new ReferenceCountedOpenSslClientContext(
                        trustCert, trustManagerFactory, keyCertChain, key, keyPassword,
                        keyManagerFactory, ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
            default:
                throw new Error(provider.toString());
        }
    }

    static ApplicationProtocolConfig toApplicationProtocolConfig(Iterable<String> nextProtocols) {
        ApplicationProtocolConfig apn;
        if (nextProtocols == null) {
            apn = ApplicationProtocolConfig.DISABLED;
        } else {
            apn = new ApplicationProtocolConfig(
                    Protocol.NPN_AND_ALPN, SelectorFailureBehavior.CHOOSE_MY_LAST_PROTOCOL,
                    SelectedListenerFailureBehavior.ACCEPT, nextProtocols);
        }
        return apn;
    }

    /**
     * Creates a new instance (startTls set to <code>false</code>).
     */
    protected SslContext() {
        this(false);
    }

    /**
     * Creates a new instance.
     */
    protected SslContext(boolean startTls) {
        this.startTls = startTls;
    }

    /**
     * Returns {@code true} if and only if this context is for server-side.
     */
    public final boolean isServer() {
        return !isClient();
    }

    /**
     * Returns the {@code true} if and only if this context is for client-side.
     */
    public abstract boolean isClient();

    /**
     * Returns the list of enabled cipher suites, in the order of preference.
     */
    public abstract List<String> cipherSuites();

    /**
     * Returns the size of the cache used for storing SSL session objects.
     */
    public abstract long sessionCacheSize();

    /**
     * Returns the timeout for the cached SSL session objects, in seconds.
     */
    public abstract long sessionTimeout();

    /**
     * @deprecated Use {@link #applicationProtocolNegotiator()} instead.
     */
    @Deprecated
    public final List<String> nextProtocols() {
        return applicationProtocolNegotiator().protocols();
    }

    /**
     * Returns the object responsible for negotiating application layer protocols for the TLS NPN/ALPN extensions.
     */
    public abstract ApplicationProtocolNegotiator applicationProtocolNegotiator();

    /**
     * Creates a new {@link SSLEngine}.
     * <p>If {@link SslProvider#OPENSSL_REFCNT} is used then the object must be released. One way to do this is to
     * wrap in a {@link SslHandler} and insert it into a pipeline. See {@link #newHandler(ByteBufAllocator)}.
     * @return a new {@link SSLEngine}
     */
    public abstract SSLEngine newEngine(ByteBufAllocator alloc);

    /**
     * Creates a new {@link SSLEngine} using advisory peer information.
     * <p>If {@link SslProvider#OPENSSL_REFCNT} is used then the object must be released. One way to do this is to
     * wrap in a {@link SslHandler} and insert it into a pipeline.
     * See {@link #newHandler(ByteBufAllocator, String, int)}.
     * @param peerHost the non-authoritative name of the host
     * @param peerPort the non-authoritative port
     *
     * @return a new {@link SSLEngine}
     */
    public abstract SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort);

    /**
     * Returns the {@link SSLSessionContext} object held by this context.
     */
    public abstract SSLSessionContext sessionContext();

    /**
     * Creates a new {@link SslHandler}.
     * <p>If {@link SslProvider#OPENSSL_REFCNT} is used then the returned {@link SslHandler} will release the engine
     * that is wrapped. If the returned {@link SslHandler} is not inserted into a pipeline then you may leak native
     * memory!
     * @return a new {@link SslHandler}
     */
    public final SslHandler newHandler(ByteBufAllocator alloc) {
        return new SslHandler(newEngine(alloc), startTls);
    }

    /**
     * Creates a new {@link SslHandler} with advisory peer information.
     * <p>If {@link SslProvider#OPENSSL_REFCNT} is used then the returned {@link SslHandler} will release the engine
     * that is wrapped. If the returned {@link SslHandler} is not inserted into a pipeline then you may leak native
     * memory!
     * @param peerHost the non-authoritative name of the host
     * @param peerPort the non-authoritative port
     *
     * @return a new {@link SslHandler}
     */
    public final SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort) {
        return new SslHandler(newEngine(alloc, peerHost, peerPort), startTls);
    }

    /**
     * Generates a key specification for an (encrypted) private key.
     *
     * @param password characters, if {@code null} an unencrypted key is assumed
     * @param key bytes of the DER encoded private key
     *
     * @return a key specification
     *
     * @throws IOException if parsing {@code key} fails
     * @throws NoSuchAlgorithmException if the algorithm used to encrypt {@code key} is unkown
     * @throws NoSuchPaddingException if the padding scheme specified in the decryption algorithm is unkown
     * @throws InvalidKeySpecException if the decryption key based on {@code password} cannot be generated
     * @throws InvalidKeyException if the decryption key based on {@code password} cannot be used to decrypt
     *                             {@code key}
     * @throws InvalidAlgorithmParameterException if decryption algorithm parameters are somehow faulty
     */
    protected static PKCS8EncodedKeySpec generateKeySpec(char[] password, byte[] key)
            throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException,
            InvalidKeyException, InvalidAlgorithmParameterException {

        if (password == null) {
            return new PKCS8EncodedKeySpec(key);
        }

        EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(key);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
        SecretKey pbeKey = keyFactory.generateSecret(pbeKeySpec);

        Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters());

        return encryptedPrivateKeyInfo.getKeySpec(cipher);
    }

    /**
     * Generates a new {@link KeyStore}.
     *
     * @param certChain a X.509 certificate chain
     * @param key a PKCS#8 private key
     * @param keyPasswordChars the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @return generated {@link KeyStore}.
     */
    static KeyStore buildKeyStore(X509Certificate[] certChain, PrivateKey key, char[] keyPasswordChars)
            throws KeyStoreException, NoSuchAlgorithmException,
                   CertificateException, IOException {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry("key", key, keyPasswordChars, certChain);
        return ks;
    }

    static PrivateKey toPrivateKey(File keyFile, String keyPassword) throws NoSuchAlgorithmException,
                                                                NoSuchPaddingException, InvalidKeySpecException,
                                                                InvalidAlgorithmParameterException,
                                                                KeyException, IOException {
        if (keyFile == null) {
            return null;
        }
        return getPrivateKeyFromByteBuffer(PemReader.readPrivateKey(keyFile), keyPassword);
    }

    static PrivateKey toPrivateKey(InputStream keyInputStream, String keyPassword) throws NoSuchAlgorithmException,
                                                                NoSuchPaddingException, InvalidKeySpecException,
                                                                InvalidAlgorithmParameterException,
                                                                KeyException, IOException {
        if (keyInputStream == null) {
            return null;
        }
        return getPrivateKeyFromByteBuffer(PemReader.readPrivateKey(keyInputStream), keyPassword);
    }

    private static PrivateKey getPrivateKeyFromByteBuffer(ByteBuf encodedKeyBuf, String keyPassword)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException,
            InvalidAlgorithmParameterException, KeyException, IOException {

        byte[] encodedKey = new byte[encodedKeyBuf.readableBytes()];
        encodedKeyBuf.readBytes(encodedKey).release();

        PKCS8EncodedKeySpec encodedKeySpec = generateKeySpec(
                keyPassword == null ? null : keyPassword.toCharArray(), encodedKey);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(encodedKeySpec);
        } catch (InvalidKeySpecException ignore) {
            try {
                return KeyFactory.getInstance("DSA").generatePrivate(encodedKeySpec);
            } catch (InvalidKeySpecException ignore2) {
                try {
                    return  KeyFactory.getInstance("EC").generatePrivate(encodedKeySpec);
                } catch (InvalidKeySpecException e) {
                    throw new InvalidKeySpecException("Neither RSA, DSA nor EC worked", e);
                }
            }
        }
    }

    /**
     * Build a {@link TrustManagerFactory} from a certificate chain file.
     * @param certChainFile The certificate file to build from.
     * @param trustManagerFactory The existing {@link TrustManagerFactory} that will be used if not {@code null}.
     * @return A {@link TrustManagerFactory} which contains the certificates in {@code certChainFile}
     */
    @Deprecated
    protected static TrustManagerFactory buildTrustManagerFactory(
            File certChainFile, TrustManagerFactory trustManagerFactory)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException {
        X509Certificate[] x509Certs = toX509Certificates(certChainFile);

        return buildTrustManagerFactory(x509Certs, trustManagerFactory);
    }

    static X509Certificate[] toX509Certificates(File file) throws CertificateException {
        if (file == null) {
            return null;
        }
        return getCertificatesFromBuffers(PemReader.readCertificates(file));
    }

    static X509Certificate[] toX509Certificates(InputStream in) throws CertificateException {
        if (in == null) {
            return null;
        }
        return getCertificatesFromBuffers(PemReader.readCertificates(in));
    }

    private static X509Certificate[] getCertificatesFromBuffers(ByteBuf[] certs) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate[] x509Certs = new X509Certificate[certs.length];

        try {
            for (int i = 0; i < certs.length; i++) {
                x509Certs[i] = (X509Certificate) cf.generateCertificate(new ByteBufInputStream(certs[i]));
            }
        } finally {
            for (ByteBuf buf: certs) {
                buf.release();
            }
        }
        return x509Certs;
    }

    static TrustManagerFactory buildTrustManagerFactory(
            X509Certificate[] certCollection, TrustManagerFactory trustManagerFactory)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        int i = 1;
        for (X509Certificate cert: certCollection) {
            String alias = Integer.toString(i);
            ks.setCertificateEntry(alias, cert);
            i++;
        }

        // Set up trust manager factory to use our key store.
        if (trustManagerFactory == null) {
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        }
        trustManagerFactory.init(ks);

        return trustManagerFactory;
    }

    static PrivateKey toPrivateKeyInternal(File keyFile, String keyPassword) throws SSLException {
        try {
            return toPrivateKey(keyFile, keyPassword);
        } catch (Exception e) {
            throw new SSLException(e);
        }
    }

    static X509Certificate[] toX509CertificatesInternal(File file) throws SSLException {
        try {
            return toX509Certificates(file);
        } catch (CertificateException e) {
            throw new SSLException(e);
        }
    }

    static KeyManagerFactory buildKeyManagerFactory(X509Certificate[] certChain, PrivateKey key, String keyPassword,
                                                    KeyManagerFactory kmf)
            throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException,
            CertificateException, IOException {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        return buildKeyManagerFactory(certChain, algorithm, key, keyPassword, kmf);
    }

    static KeyManagerFactory buildKeyManagerFactory(X509Certificate[] certChainFile,
                                                    String keyAlgorithm, PrivateKey key,
                                                    String keyPassword, KeyManagerFactory kmf)
            throws KeyStoreException, NoSuchAlgorithmException, IOException,
            CertificateException, UnrecoverableKeyException {
        char[] keyPasswordChars = keyPassword == null ? EmptyArrays.EMPTY_CHARS : keyPassword.toCharArray();
        KeyStore ks = buildKeyStore(certChainFile, key, keyPasswordChars);
        // Set up key manager factory to use our key store
        if (kmf == null) {
            kmf = KeyManagerFactory.getInstance(keyAlgorithm);
        }
        kmf.init(ks, keyPasswordChars);

        return kmf;
    }

    static KeyManagerFactory buildDefaultKeyManagerFactory()
            throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(null, null);
        return keyManagerFactory;
    }
}
