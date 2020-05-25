/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.internal.UnstableApi;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.EmptyArrays.EMPTY_STRINGS;
import static io.netty.util.internal.EmptyArrays.EMPTY_X509_CERTIFICATES;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Builder for configuring a new SslContext for creation.
 */
public final class SslContextBuilder {

    /**
     * Creates a builder for new client-side {@link SslContext}.
     */
    public static SslContextBuilder forClient() {
        return new SslContextBuilder(false);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @see #keyManager(File, File)
     */
    public static SslContextBuilder forServer(File keyCertChainFile, File keyFile) {
        return new SslContextBuilder(true).keyManager(keyCertChainFile, keyFile);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param keyCertChainInputStream an input stream for an X.509 certificate chain in PEM format
     * @param keyInputStream an input stream for a PKCS#8 private key in PEM format
     * @see #keyManager(InputStream, InputStream)
     */
    public static SslContextBuilder forServer(InputStream keyCertChainInputStream, InputStream keyInputStream) {
        return new SslContextBuilder(true).keyManager(keyCertChainInputStream, keyInputStream);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param key a PKCS#8 private key
     * @param keyCertChain the X.509 certificate chain
     * @see #keyManager(PrivateKey, X509Certificate[])
     */
    public static SslContextBuilder forServer(PrivateKey key, X509Certificate... keyCertChain) {
        return new SslContextBuilder(true).keyManager(key, keyCertChain);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param key a PKCS#8 private key
     * @param keyCertChain the X.509 certificate chain
     * @see #keyManager(PrivateKey, X509Certificate[])
     */
    public static SslContextBuilder forServer(PrivateKey key, Iterable<? extends X509Certificate> keyCertChain) {
        return forServer(key, toArray(keyCertChain, EMPTY_X509_CERTIFICATES));
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     * @see #keyManager(File, File, String)
     */
    public static SslContextBuilder forServer(
            File keyCertChainFile, File keyFile, String keyPassword) {
        return new SslContextBuilder(true).keyManager(keyCertChainFile, keyFile, keyPassword);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param keyCertChainInputStream an input stream for an X.509 certificate chain in PEM format
     * @param keyInputStream an input stream for a PKCS#8 private key in PEM format
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     * @see #keyManager(InputStream, InputStream, String)
     */
    public static SslContextBuilder forServer(
            InputStream keyCertChainInputStream, InputStream keyInputStream, String keyPassword) {
        return new SslContextBuilder(true).keyManager(keyCertChainInputStream, keyInputStream, keyPassword);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param key a PKCS#8 private key
     * @param keyCertChain the X.509 certificate chain
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     * @see #keyManager(File, File, String)
     */
    public static SslContextBuilder forServer(
            PrivateKey key, String keyPassword, X509Certificate... keyCertChain) {
        return new SslContextBuilder(true).keyManager(key, keyPassword, keyCertChain);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param key a PKCS#8 private key
     * @param keyCertChain the X.509 certificate chain
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     * @see #keyManager(File, File, String)
     */
    public static SslContextBuilder forServer(
            PrivateKey key, String keyPassword, Iterable<? extends X509Certificate> keyCertChain) {
        return forServer(key, keyPassword, toArray(keyCertChain, EMPTY_X509_CERTIFICATES));
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * If you use {@link SslProvider#OPENSSL} or {@link SslProvider#OPENSSL_REFCNT} consider using
     * {@link OpenSslX509KeyManagerFactory} or {@link OpenSslCachingX509KeyManagerFactory}.
     *
     * @param keyManagerFactory non-{@code null} factory for server's private key
     * @see #keyManager(KeyManagerFactory)
     */
    public static SslContextBuilder forServer(KeyManagerFactory keyManagerFactory) {
        return new SslContextBuilder(true).keyManager(keyManagerFactory);
    }

    /**
     * Creates a builder for new server-side {@link SslContext} with {@link KeyManager}.
     *
     * @param KeyManager non-{@code null} KeyManager for server's private key
     */
    public static SslContextBuilder forServer(KeyManager keyManager) {
        return new SslContextBuilder(true).keyManager(keyManager);
    }

    private final boolean forServer;
    private SslProvider provider;
    private Provider sslContextProvider;
    private X509Certificate[] trustCertCollection;
    private TrustManagerFactory trustManagerFactory;
    private X509Certificate[] keyCertChain;
    private PrivateKey key;
    private String keyPassword;
    private KeyManagerFactory keyManagerFactory;
    private Iterable<String> ciphers;
    private CipherSuiteFilter cipherFilter = IdentityCipherSuiteFilter.INSTANCE;
    private ApplicationProtocolConfig apn;
    private long sessionCacheSize;
    private long sessionTimeout;
    private ClientAuth clientAuth = ClientAuth.NONE;
    private String[] protocols;
    private boolean startTls;
    private boolean enableOcsp;
    private String keyStoreType = KeyStore.getDefaultType();

    private SslContextBuilder(boolean forServer) {
        this.forServer = forServer;
    }

    /**
     * The {@link SslContext} implementation to use. {@code null} uses the default one.
     */
    public SslContextBuilder sslProvider(SslProvider provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Sets the {@link KeyStore} type that should be used. {@code null} uses the default one.
     */
    public SslContextBuilder keyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
        return this;
    }

    /**
     * The SSLContext {@link Provider} to use. {@code null} uses the default one. This is only
     * used with {@link SslProvider#JDK}.
     */
    public SslContextBuilder sslContextProvider(Provider sslContextProvider) {
        this.sslContextProvider = sslContextProvider;
        return this;
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The file should
     * contain an X.509 certificate collection in PEM format. {@code null} uses the system default.
     */
    public SslContextBuilder trustManager(File trustCertCollectionFile) {
        try {
            return trustManager(SslContext.toX509Certificates(trustCertCollectionFile));
        } catch (Exception e) {
            throw new IllegalArgumentException("File does not contain valid certificates: "
                    + trustCertCollectionFile, e);
        }
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The input stream should
     * contain an X.509 certificate collection in PEM format. {@code null} uses the system default.
     */
    public SslContextBuilder trustManager(InputStream trustCertCollectionInputStream) {
        try {
            return trustManager(SslContext.toX509Certificates(trustCertCollectionInputStream));
        } catch (Exception e) {
            throw new IllegalArgumentException("Input stream does not contain valid certificates.", e);
        }
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate, {@code null} uses the system default.
     */
    public SslContextBuilder trustManager(X509Certificate... trustCertCollection) {
        this.trustCertCollection = trustCertCollection != null ? trustCertCollection.clone() : null;
        trustManagerFactory = null;
        return this;
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate, {@code null} uses the system default.
     */
    public SslContextBuilder trustManager(Iterable<? extends X509Certificate> trustCertCollection) {
        return trustManager(toArray(trustCertCollection, EMPTY_X509_CERTIFICATES));
    }

    /**
     * Trusted manager for verifying the remote endpoint's certificate. {@code null} uses the system default.
     */
    public SslContextBuilder trustManager(TrustManagerFactory trustManagerFactory) {
        trustCertCollection = null;
        this.trustManagerFactory = trustManagerFactory;
        return this;
    }

    /**
     * A single trusted manager for verifying the remote endpoint's certificate.
     * This is helpful when custom implementation of {@link TrustManager} is needed.
     * Internally, a simple wrapper of {@link TrustManagerFactory} that only produces this
     * specified {@link TrustManager} will be created, thus all the requirements specified in
     * {@link #trustManager(TrustManagerFactory trustManagerFactory)} also apply here.
     */
    public SslContextBuilder trustManager(TrustManager trustManager) {
        this.trustManagerFactory = new TrustManagerFactoryWrapper(trustManager);
        trustCertCollection = null;
        return this;
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainFile} and {@code keyFile} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     */
    public SslContextBuilder keyManager(File keyCertChainFile, File keyFile) {
        return keyManager(keyCertChainFile, keyFile, null);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyCertChainInputStream an input stream for an X.509 certificate chain in PEM format
     * @param keyInputStream an input stream for a PKCS#8 private key in PEM format
     */
    public SslContextBuilder keyManager(InputStream keyCertChainInputStream, InputStream keyInputStream) {
        return keyManager(keyCertChainInputStream, keyInputStream, null);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChain} and {@code key} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param key a PKCS#8 private key
     * @param keyCertChain an X.509 certificate chain
     */
    public SslContextBuilder keyManager(PrivateKey key, X509Certificate... keyCertChain) {
        return keyManager(key, null, keyCertChain);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChain} and {@code key} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param key a PKCS#8 private key
     * @param keyCertChain an X.509 certificate chain
     */
    public SslContextBuilder keyManager(PrivateKey key, Iterable<? extends X509Certificate> keyCertChain) {
        return keyManager(key, toArray(keyCertChain, EMPTY_X509_CERTIFICATES));
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainFile} and {@code keyFile} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     */
    public SslContextBuilder keyManager(File keyCertChainFile, File keyFile, String keyPassword) {
        X509Certificate[] keyCertChain;
        PrivateKey key;
        try {
            keyCertChain = SslContext.toX509Certificates(keyCertChainFile);
        } catch (Exception e) {
            throw new IllegalArgumentException("File does not contain valid certificates: " + keyCertChainFile, e);
        }
        try {
            key = SslContext.toPrivateKey(keyFile, keyPassword);
        } catch (Exception e) {
            throw new IllegalArgumentException("File does not contain valid private key: " + keyFile, e);
        }
        return keyManager(key, keyPassword, keyCertChain);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainInputStream} and {@code keyInputStream} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyCertChainInputStream an input stream for an X.509 certificate chain in PEM format
     * @param keyInputStream an input stream for a PKCS#8 private key in PEM format
     * @param keyPassword the password of the {@code keyInputStream}, or {@code null} if it's not
     *     password-protected
     */
    public SslContextBuilder keyManager(InputStream keyCertChainInputStream, InputStream keyInputStream,
            String keyPassword) {
        X509Certificate[] keyCertChain;
        PrivateKey key;
        try {
            keyCertChain = SslContext.toX509Certificates(keyCertChainInputStream);
        } catch (Exception e) {
            throw new IllegalArgumentException("Input stream not contain valid certificates.", e);
        }
        try {
            key = SslContext.toPrivateKey(keyInputStream, keyPassword);
        } catch (Exception e) {
            throw new IllegalArgumentException("Input stream does not contain valid private key.", e);
        }
        return keyManager(key, keyPassword, keyCertChain);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChain} and {@code key} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param key a PKCS#8 private key file
     * @param keyPassword the password of the {@code key}, or {@code null} if it's not
     *     password-protected
     * @param keyCertChain an X.509 certificate chain
     */
    public SslContextBuilder keyManager(PrivateKey key, String keyPassword, X509Certificate... keyCertChain) {
        if (forServer) {
            checkNotNull(keyCertChain, "keyCertChain required for servers");
            if (keyCertChain.length == 0) {
                throw new IllegalArgumentException("keyCertChain must be non-empty");
            }
            checkNotNull(key, "key required for servers");
        }
        if (keyCertChain == null || keyCertChain.length == 0) {
            this.keyCertChain = null;
        } else {
            for (X509Certificate cert: keyCertChain) {
                if (cert == null) {
                    throw new IllegalArgumentException("keyCertChain contains null entry");
                }
            }
            this.keyCertChain = keyCertChain.clone();
        }
        this.key = key;
        this.keyPassword = keyPassword;
        keyManagerFactory = null;
        return this;
    }

    /**
     * Identifying certificate for this host. {@code keyCertChain} and {@code key} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param key a PKCS#8 private key file
     * @param keyPassword the password of the {@code key}, or {@code null} if it's not
     *     password-protected
     * @param keyCertChain an X.509 certificate chain
     */
    public SslContextBuilder keyManager(PrivateKey key, String keyPassword,
                                        Iterable<? extends X509Certificate> keyCertChain) {
        return keyManager(key, keyPassword, toArray(keyCertChain, EMPTY_X509_CERTIFICATES));
    }

    /**
     * Identifying manager for this host. {@code keyManagerFactory} may be {@code null} for
     * client contexts, which disables mutual authentication. Using a {@link KeyManagerFactory}
     * is only supported for {@link SslProvider#JDK} or {@link SslProvider#OPENSSL} / {@link SslProvider#OPENSSL_REFCNT}
     * if the used openssl version is 1.0.1+. You can check if your openssl version supports using a
     * {@link KeyManagerFactory} by calling {@link OpenSsl#supportsKeyManagerFactory()}. If this is not the case
     * you must use {@link #keyManager(File, File)} or {@link #keyManager(File, File, String)}.
     *
     * If you use {@link SslProvider#OPENSSL} or {@link SslProvider#OPENSSL_REFCNT} consider using
     * {@link OpenSslX509KeyManagerFactory} or {@link OpenSslCachingX509KeyManagerFactory}.
     */
    public SslContextBuilder keyManager(KeyManagerFactory keyManagerFactory) {
        if (forServer) {
            checkNotNull(keyManagerFactory, "keyManagerFactory required for servers");
        }
        keyCertChain = null;
        key = null;
        keyPassword = null;
        this.keyManagerFactory = keyManagerFactory;
        return this;
    }

    /**
     * A single key manager managing the identity information of this host.
     * This is helpful when custom implementation of {@link KeyManager} is needed.
     * Internally, a wrapper of {@link KeyManagerFactory} that only produces this specified
     * {@link KeyManager} will be created, thus all the requirements specified in
     * {@link #keyManager(KeyManagerFactory keyManagerFactory)} also apply here.
     */
    public SslContextBuilder keyManager(KeyManager keyManager) {
        if (forServer) {
            checkNotNull(keyManager, "keyManager required for servers");
        }
        if (keyManager != null) {
            this.keyManagerFactory = new KeyManagerFactoryWrapper(keyManager);
        } else {
            this.keyManagerFactory = null;
        }
        keyCertChain = null;
        key = null;
        keyPassword = null;
        return this;
    }

    /**
     * The cipher suites to enable, in the order of preference. {@code null} to use default
     * cipher suites.
     */
    public SslContextBuilder ciphers(Iterable<String> ciphers) {
        return ciphers(ciphers, IdentityCipherSuiteFilter.INSTANCE);
    }

    /**
     * The cipher suites to enable, in the order of preference. {@code cipherFilter} will be
     * applied to the ciphers before use. If {@code ciphers} is {@code null}, then the default
     * cipher suites will be used.
     */
    public SslContextBuilder ciphers(Iterable<String> ciphers, CipherSuiteFilter cipherFilter) {
        this.cipherFilter = checkNotNull(cipherFilter, "cipherFilter");
        this.ciphers = ciphers;
        return this;
    }

    /**
     * Application protocol negotiation configuration. {@code null} disables support.
     */
    public SslContextBuilder applicationProtocolConfig(ApplicationProtocolConfig apn) {
        this.apn = apn;
        return this;
    }

    /**
     * Set the size of the cache used for storing SSL session objects. {@code 0} to use the
     * default value.
     */
    public SslContextBuilder sessionCacheSize(long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
        return this;
    }

    /**
     * Set the timeout for the cached SSL session objects, in seconds. {@code 0} to use the
     * default value.
     */
    public SslContextBuilder sessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    /**
     * Sets the client authentication mode.
     */
    public SslContextBuilder clientAuth(ClientAuth clientAuth) {
        this.clientAuth = checkNotNull(clientAuth, "clientAuth");
        return this;
    }

    /**
     * The TLS protocol versions to enable.
     * @param protocols The protocols to enable, or {@code null} to enable the default protocols.
     * @see SSLEngine#setEnabledCipherSuites(String[])
     */
    public SslContextBuilder protocols(String... protocols) {
        this.protocols = protocols == null ? null : protocols.clone();
        return this;
    }

    /**
     * The TLS protocol versions to enable.
     * @param protocols The protocols to enable, or {@code null} to enable the default protocols.
     * @see SSLEngine#setEnabledCipherSuites(String[])
     */
    public SslContextBuilder protocols(Iterable<String> protocols) {
        return protocols(toArray(protocols, EMPTY_STRINGS));
    }

    /**
     * {@code true} if the first write request shouldn't be encrypted.
     */
    public SslContextBuilder startTls(boolean startTls) {
        this.startTls = startTls;
        return this;
    }

    /**
     * Enables OCSP stapling. Please note that not all {@link SslProvider} implementations support OCSP
     * stapling and an exception will be thrown upon {@link #build()}.
     *
     * @see OpenSsl#isOcspSupported()
     */
    @UnstableApi
    public SslContextBuilder enableOcsp(boolean enableOcsp) {
        this.enableOcsp = enableOcsp;
        return this;
    }

    /**
     * Create new {@code SslContext} instance with configured settings.
     * <p>If {@link #sslProvider(SslProvider)} is set to {@link SslProvider#OPENSSL_REFCNT} then the caller is
     * responsible for releasing this object, or else native memory may leak.
     */
    public SslContext build() throws SSLException {
        if (forServer) {
            return SslContext.newServerContextInternal(provider, sslContextProvider, trustCertCollection,
                trustManagerFactory, keyCertChain, key, keyPassword, keyManagerFactory,
                ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout, clientAuth, protocols, startTls,
                enableOcsp, keyStoreType);
        } else {
            return SslContext.newClientContextInternal(provider, sslContextProvider, trustCertCollection,
                trustManagerFactory, keyCertChain, key, keyPassword, keyManagerFactory,
                ciphers, cipherFilter, apn, protocols, sessionCacheSize, sessionTimeout, enableOcsp, keyStoreType);
        }
    }

    private static <T> T[] toArray(Iterable<? extends T> iterable, T[] prototype) {
        if (iterable == null) {
            return null;
        }
        final List<T> list = new ArrayList<T>();
        for (T element : iterable) {
            list.add(element);
        }
        return list.toArray(prototype);
    }
}
