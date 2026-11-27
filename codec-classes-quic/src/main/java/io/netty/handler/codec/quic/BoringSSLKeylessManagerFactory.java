/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.codec.quic;

import org.jetbrains.annotations.Nullable;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * {@link KeyManagerFactory} that uses a {@link BoringSSLAsyncPrivateKeyMethod} to sign TLS handshake data without
 * exposing the corresponding private key to the QUIC TLS context.
 * <p>
 * A static certificate chain can be provided when creating the factory, or an
 * {@link X509ExtendedKeyManager} can supply certificate chains dynamically for individual handshakes.
 * The private key operations are handled by the supplied signing method in either case.
 */
public final class BoringSSLKeylessManagerFactory extends KeyManagerFactory {

    final BoringSSLAsyncPrivateKeyMethod privateKeyMethod;
    final boolean dynamicKeyManager;

    private BoringSSLKeylessManagerFactory(KeyManagerFactory keyManagerFactory,
                                           BoringSSLAsyncPrivateKeyMethod privateKeyMethod) {
        this(keyManagerFactory, privateKeyMethod, null);
    }

    private BoringSSLKeylessManagerFactory(KeyManagerFactory keyManagerFactory,
                                           BoringSSLAsyncPrivateKeyMethod privateKeyMethod,
                                           @Nullable X509ExtendedKeyManager keyManager) {
        super(new KeylessManagerFactorySpi(keyManagerFactory, keyManager),
                keyManagerFactory.getProvider(), keyManagerFactory.getAlgorithm());
        this.privateKeyMethod = requireNonNull(privateKeyMethod, "privateKeyMethod");
        dynamicKeyManager = keyManager != null;
    }

    /**
     * Creates a new factory instance.
     *
     * @param privateKeyMethod              the {@link BoringSSLAsyncPrivateKeyMethod} that is used for key signing.
     * @param chain                         the {@link File} that contains the {@link X509Certificate} chain.
     * @return                              a new factory instance.
     * @throws CertificateException         on error.
     * @throws IOException                  on error.
     * @throws KeyStoreException            on error.
     * @throws NoSuchAlgorithmException     on error.
     * @throws UnrecoverableKeyException    on error.
     */
    public static BoringSSLKeylessManagerFactory newKeyless(BoringSSLAsyncPrivateKeyMethod privateKeyMethod, File chain)
            throws CertificateException, IOException,
            KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        try (InputStream chainInputStream = Files.newInputStream(chain.toPath())) {
            return newKeyless(privateKeyMethod, chainInputStream);
        }
    }

    /**
     * Creates a new factory instance.
     *
     * @param privateKeyMethod              the {@link BoringSSLAsyncPrivateKeyMethod} that is used for key signing.
     * @param chain                         the {@link InputStream} that contains the {@link X509Certificate} chain.
     * @return                              a new factory instance.
     * @throws CertificateException         on error.
     * @throws IOException                  on error.
     * @throws KeyStoreException            on error.
     * @throws NoSuchAlgorithmException     on error.
     * @throws UnrecoverableKeyException    on error.
     */
    public static BoringSSLKeylessManagerFactory newKeyless(BoringSSLAsyncPrivateKeyMethod privateKeyMethod,
                                                            InputStream chain)
            throws CertificateException, IOException,
            KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        return newKeyless(privateKeyMethod, QuicSslContext.toX509Certificates0(chain));
    }

    /**
     * Creates a new factory instance.
     *
     * @param privateKeyMethod              the {@link BoringSSLAsyncPrivateKeyMethod} that is used for key signing.
     * @param certificateChain              the {@link X509Certificate} chain.
     * @return                              a new factory instance.
     * @throws CertificateException         on error.
     * @throws IOException                  on error.
     * @throws KeyStoreException            on error.
     * @throws NoSuchAlgorithmException     on error.
     * @throws UnrecoverableKeyException    on error.
     */
    public static BoringSSLKeylessManagerFactory newKeyless(BoringSSLAsyncPrivateKeyMethod privateKeyMethod,
                                                            X509Certificate... certificateChain)
            throws CertificateException, IOException,
            KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        checkNotNull(certificateChain, "certificateChain");
        KeyStore store = new KeylessKeyStore(certificateChain.clone());
        store.load(null, null);
        BoringSSLKeylessManagerFactory factory = new BoringSSLKeylessManagerFactory(
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()), privateKeyMethod);
        factory.init(store, null);
        return factory;
    }

    /**
     * Creates a keyless factory that delegates alias selection and certificate-chain lookup to the supplied
     * {@link X509ExtendedKeyManager}. The delegate's {@code getPrivateKey(String)} method is never called;
     * signing is performed exclusively through {@code privateKeyMethod}.
     * <p>
     * On a QUIC server, the delegate can inspect the requested hostname using
     * {@link QuicSslEngine#getRequestedSniHostname()} in {@code chooseEngineServerAlias(...)}.
     * Certificate selection and chain retrieval take place during the TLS handshake, so the delegate must
     * support concurrent calls and must return a chain matching the key used by {@code privateKeyMethod}.
     * To load certificates using blocking I/O, configure a dedicated offloading executor with
     * {@link QuicCodecBuilder#sslTaskExecutor(java.util.concurrent.Executor)}. Without an offloading
     * executor, blocking key manager operations may block the QUIC event loop. This factory does not provide
     * a future-based certificate loading API. The returned factory is already configured and cannot be
     * reinitialized with another key store.
     *
     * @param privateKeyMethod              the method used for key signing.
     * @param keyManager                    the key manager used for certificate selection and chain retrieval.
     * @return                              a new factory instance.
     * @throws NoSuchAlgorithmException     if the default key manager factory algorithm is unavailable.
     */
    public static BoringSSLKeylessManagerFactory newKeyless(BoringSSLAsyncPrivateKeyMethod privateKeyMethod,
                                                            X509ExtendedKeyManager keyManager)
            throws NoSuchAlgorithmException {
        checkNotNull(keyManager, "keyManager");
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        return new BoringSSLKeylessManagerFactory(keyManagerFactory, privateKeyMethod,
                new KeylessKeyManager(keyManager));
    }

    private static final class KeylessManagerFactorySpi extends KeyManagerFactorySpi {

        private final KeyManagerFactory keyManagerFactory;
        private final X509ExtendedKeyManager keyManager;

        KeylessManagerFactorySpi(KeyManagerFactory keyManagerFactory,
                                 @Nullable X509ExtendedKeyManager keyManager) {
            this.keyManagerFactory = requireNonNull(keyManagerFactory, "keyManagerFactory");
            this.keyManager = keyManager;
        }

        @Override
        protected void engineInit(KeyStore ks, char[] password)
                throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
            if (keyManager != null) {
                throw new KeyStoreException("A custom key manager is already configured");
            }
            keyManagerFactory.init(ks, password);
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        protected KeyManager[] engineGetKeyManagers() {
            return keyManager == null ? keyManagerFactory.getKeyManagers() : new KeyManager[] { keyManager };
        }
    }

    private static final class KeylessKeyManager extends X509ExtendedKeyManager {
        private final X509ExtendedKeyManager delegate;

        KeylessKeyManager(X509ExtendedKeyManager delegate) {
            this.delegate = requireNonNull(delegate, "delegate");
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return delegate.getClientAliases(keyType, issuers);
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return delegate.chooseClientAlias(keyType, issuers, socket);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return delegate.getServerAliases(keyType, issuers);
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return delegate.chooseServerAlias(keyType, issuers, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return delegate.getCertificateChain(alias);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return alias == null ? null : BoringSSLKeylessPrivateKey.INSTANCE;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            return delegate.chooseEngineClientAlias(keyType, issuers, engine);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return delegate.chooseEngineServerAlias(keyType, issuers, engine);
        }
    }

    private static final class KeylessKeyStore extends KeyStore {
        private static final String ALIAS = "key";
        private KeylessKeyStore(final X509Certificate[] certificateChain) {
            super(new KeyStoreSpi() {

                private final Date creationDate = new Date();

                @Override
                @Nullable
                public Key engineGetKey(String alias, char[] password) {
                    if (engineContainsAlias(alias)) {
                        return BoringSSLKeylessPrivateKey.INSTANCE;
                    }
                    return null;
                }

                @Override
                public Certificate @Nullable [] engineGetCertificateChain(String alias) {
                    return engineContainsAlias(alias)? certificateChain.clone() : null;
                }

                @Override
                @Nullable
                public Certificate engineGetCertificate(String alias) {
                    return engineContainsAlias(alias)? certificateChain[0] : null;
                }

                @Override
                @Nullable
                public Date engineGetCreationDate(String alias) {
                    return engineContainsAlias(alias)? creationDate : null;
                }

                @Override
                public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain)
                        throws KeyStoreException {
                    throw new KeyStoreException("Not supported");
                }

                @Override
                public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
                    throw new KeyStoreException("Not supported");
                }

                @Override
                public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
                    throw new KeyStoreException("Not supported");
                }

                @Override
                public void engineDeleteEntry(String alias) throws KeyStoreException {
                    throw new KeyStoreException("Not supported");
                }

                @Override
                public Enumeration<String> engineAliases() {
                    return Collections.enumeration(Collections.singleton(ALIAS));
                }

                @Override
                public boolean engineContainsAlias(String alias) {
                    return ALIAS.equals(alias);
                }

                @Override
                public int engineSize() {
                    return 1;
                }

                @Override
                public boolean engineIsKeyEntry(String alias) {
                    return engineContainsAlias(alias);
                }

                @Override
                public boolean engineIsCertificateEntry(String alias) {
                    return engineContainsAlias(alias);
                }

                @Override
                @Nullable
                public String engineGetCertificateAlias(Certificate cert) {
                    if (cert instanceof X509Certificate) {
                        for (X509Certificate x509Certificate : certificateChain) {
                            if (x509Certificate.equals(cert)) {
                                return ALIAS;
                            }
                        }
                    }
                    return null;
                }

                @Override
                public void engineStore(OutputStream stream, char[] password) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void engineLoad(@Nullable InputStream stream, char @Nullable [] password) {
                    if (stream != null && password != null) {
                        throw new UnsupportedOperationException();
                    }
                }
            }, null, "keyless");
        }
    }
}
