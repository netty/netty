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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
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
 * {@link KeyManagerFactory} that can be used to support custom key signing via {@link BoringSSLAsyncPrivateKeyMethod}.
 */
public final class BoringSSLKeylessManagerFactory extends KeyManagerFactory {

    final BoringSSLAsyncPrivateKeyMethod privateKeyMethod;

    private BoringSSLKeylessManagerFactory(KeyManagerFactory keyManagerFactory,
                                           BoringSSLAsyncPrivateKeyMethod privateKeyMethod) {
        super(new KeylessManagerFactorySpi(keyManagerFactory),
                keyManagerFactory.getProvider(), keyManagerFactory.getAlgorithm());
        this.privateKeyMethod = requireNonNull(privateKeyMethod, "privateKeyMethod");
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
        return newKeyless(privateKeyMethod, Files.newInputStream(chain.toPath()));
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

    private static final class KeylessManagerFactorySpi extends KeyManagerFactorySpi {

        private final KeyManagerFactory keyManagerFactory;

        KeylessManagerFactorySpi(KeyManagerFactory keyManagerFactory) {
            this.keyManagerFactory = requireNonNull(keyManagerFactory, "keyManagerFactory");
        }

        @Override
        protected void engineInit(KeyStore ks, char[] password)
                throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
            keyManagerFactory.init(ks, password);
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        protected KeyManager[] engineGetKeyManagers() {
            return keyManagerFactory.getKeyManagers();
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
