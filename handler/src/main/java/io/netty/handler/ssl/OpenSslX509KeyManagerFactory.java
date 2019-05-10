/*
 * Copyright 2018 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.internal.tcnative.SSL;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.X509KeyManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Special {@link KeyManagerFactory} that pre-compute the keymaterial used when {@link SslProvider#OPENSSL} or
 * {@link SslProvider#OPENSSL_REFCNT} is used and so will improve handshake times and its performance.
 *
 *
 *
 * Because the keymaterial is pre-computed any modification to the {@link KeyStore} is ignored after
 * {@link #init(KeyStore, char[])} is called.
 *
 * {@link #init(ManagerFactoryParameters)} is not supported by this implementation and so a call to it will always
 * result in an {@link InvalidAlgorithmParameterException}.
 */
public final class OpenSslX509KeyManagerFactory extends KeyManagerFactory {

    private final OpenSslKeyManagerFactorySpi spi;

    public OpenSslX509KeyManagerFactory() {
        this(newOpenSslKeyManagerFactorySpi(null));
    }

    public OpenSslX509KeyManagerFactory(Provider provider) {
        this(newOpenSslKeyManagerFactorySpi(provider));
    }

    public OpenSslX509KeyManagerFactory(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        this(newOpenSslKeyManagerFactorySpi(algorithm, provider));
    }

    private OpenSslX509KeyManagerFactory(OpenSslKeyManagerFactorySpi spi) {
        super(spi, spi.kmf.getProvider(), spi.kmf.getAlgorithm());
        this.spi = spi;
    }

    private static OpenSslKeyManagerFactorySpi newOpenSslKeyManagerFactorySpi(Provider provider) {
        try {
            return newOpenSslKeyManagerFactorySpi(null, provider);
        } catch (NoSuchAlgorithmException e) {
            // This should never happen as we use the default algorithm.
            throw new IllegalStateException(e);
        }
    }

    private static OpenSslKeyManagerFactorySpi newOpenSslKeyManagerFactorySpi(String algorithm, Provider provider)
            throws NoSuchAlgorithmException {
        if (algorithm == null) {
            algorithm = KeyManagerFactory.getDefaultAlgorithm();
        }
        return new OpenSslKeyManagerFactorySpi(
                provider == null ? KeyManagerFactory.getInstance(algorithm) :
                        KeyManagerFactory.getInstance(algorithm, provider));
    }

    OpenSslKeyMaterialProvider newProvider() {
        return spi.newProvider();
    }

    private static final class OpenSslKeyManagerFactorySpi extends KeyManagerFactorySpi {
        final KeyManagerFactory kmf;
        private volatile ProviderFactory providerFactory;

        OpenSslKeyManagerFactorySpi(KeyManagerFactory kmf) {
            this.kmf = ObjectUtil.checkNotNull(kmf, "kmf");
        }

        @Override
        protected synchronized void engineInit(KeyStore keyStore, char[] chars)
                throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
            if (providerFactory != null) {
                throw new KeyStoreException("Already initialized");
            }
            if (!keyStore.aliases().hasMoreElements()) {
                throw new KeyStoreException("No aliases found");
            }

            kmf.init(keyStore, chars);
            providerFactory = new ProviderFactory(ReferenceCountedOpenSslContext.chooseX509KeyManager(
                    kmf.getKeyManagers()), password(chars), Collections.list(keyStore.aliases()));
        }

        private static String password(char[] password) {
            if (password == null || password.length == 0) {
                return null;
            }
            return new String(password);
        }

        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters)
                throws InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException("Not supported");
        }

        @Override
        protected KeyManager[] engineGetKeyManagers() {
            ProviderFactory providerFactory = this.providerFactory;
            if (providerFactory == null) {
                throw new IllegalStateException("engineInit(...) not called yet");
            }
            return new KeyManager[] { providerFactory.keyManager };
        }

        OpenSslKeyMaterialProvider newProvider() {
            ProviderFactory providerFactory = this.providerFactory;
            if (providerFactory == null) {
                throw new IllegalStateException("engineInit(...) not called yet");
            }
            return providerFactory.newProvider();
        }

        private static final class ProviderFactory {
            private final X509KeyManager keyManager;
            private final String password;
            private final Iterable<String> aliases;

            ProviderFactory(X509KeyManager keyManager, String password, Iterable<String> aliases) {
                this.keyManager = keyManager;
                this.password = password;
                this.aliases = aliases;
            }

            OpenSslKeyMaterialProvider newProvider() {
                return new OpenSslPopulatedKeyMaterialProvider(keyManager,
                        password, aliases);
            }

            /**
             * {@link OpenSslKeyMaterialProvider} implementation that pre-compute the {@link OpenSslKeyMaterial} for
             * all aliases.
             */
            private static final class OpenSslPopulatedKeyMaterialProvider extends OpenSslKeyMaterialProvider {
                private final Map<String, Object> materialMap;

                OpenSslPopulatedKeyMaterialProvider(
                        X509KeyManager keyManager, String password, Iterable<String> aliases) {
                    super(keyManager, password);
                    materialMap = new HashMap<String, Object>();
                    boolean initComplete = false;
                    try {
                        for (String alias: aliases) {
                            if (alias != null && !materialMap.containsKey(alias)) {
                                try {
                                    materialMap.put(alias, super.chooseKeyMaterial(
                                            UnpooledByteBufAllocator.DEFAULT, alias));
                                } catch (Exception e) {
                                    // Just store the exception and rethrow it when we try to choose the keymaterial
                                    // for this alias later on.
                                    materialMap.put(alias, e);
                                }
                            }
                        }
                        initComplete = true;
                    } finally {
                        if (!initComplete) {
                            destroy();
                        }
                    }
                    if (materialMap.isEmpty()) {
                        throw new IllegalArgumentException("aliases must be non-empty");
                    }
                }

                @Override
                OpenSslKeyMaterial chooseKeyMaterial(ByteBufAllocator allocator, String alias) throws Exception {
                    Object value = materialMap.get(alias);
                    if (value == null) {
                        // There is no keymaterial for the requested alias, return null
                        return null;
                    }
                    if (value instanceof OpenSslKeyMaterial) {
                        return ((OpenSslKeyMaterial) value).retain();
                    }
                    throw (Exception) value;
                }

                @Override
                void destroy() {
                    for (Object material: materialMap.values()) {
                        ReferenceCountUtil.release(material);
                    }
                    materialMap.clear();
                }
            }
        }
    }

    /**
     * Create a new initialized {@link OpenSslX509KeyManagerFactory} which loads its {@link PrivateKey} directly from
     * an {@code OpenSSL engine} via the
     * <a href="https://www.openssl.org/docs/man1.1.0/crypto/ENGINE_load_private_key.html">ENGINE_load_private_key</a>
     * function.
     */
    public static OpenSslX509KeyManagerFactory newEngineBased(File certificateChain, String password)
            throws CertificateException, IOException,
                   KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        return newEngineBased(SslContext.toX509Certificates(certificateChain), password);
    }

    /**
     * Create a new initialized {@link OpenSslX509KeyManagerFactory} which loads its {@link PrivateKey} directly from
     * an {@code OpenSSL engine} via the
     * <a href="https://www.openssl.org/docs/man1.1.0/crypto/ENGINE_load_private_key.html">ENGINE_load_private_key</a>
     * function.
     */
    public static OpenSslX509KeyManagerFactory newEngineBased(X509Certificate[] certificateChain, String password)
            throws CertificateException, IOException,
                   KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore store = new OpenSslKeyStore(certificateChain.clone(), false);
        store.load(null, null);
        OpenSslX509KeyManagerFactory factory = new OpenSslX509KeyManagerFactory();
        factory.init(store, password == null ? null : password.toCharArray());
        return factory;
    }

    /**
     * See {@link OpenSslX509KeyManagerFactory#newEngineBased(X509Certificate[], String)}.
     */
    public static OpenSslX509KeyManagerFactory newKeyless(File chain)
            throws CertificateException, IOException,
            KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        return newKeyless(SslContext.toX509Certificates(chain));
    }

    /**
     * See {@link OpenSslX509KeyManagerFactory#newEngineBased(X509Certificate[], String)}.
     */
    public static OpenSslX509KeyManagerFactory newKeyless(InputStream chain)
            throws CertificateException, IOException,
            KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        return newKeyless(SslContext.toX509Certificates(chain));
    }

    /**
     * Returns a new initialized {@link OpenSslX509KeyManagerFactory} which will provide its private key by using the
     * {@link OpenSslPrivateKeyMethod}.
     */
    public static OpenSslX509KeyManagerFactory newKeyless(X509Certificate... certificateChain)
            throws CertificateException, IOException,
            KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore store = new OpenSslKeyStore(certificateChain.clone(), true);
        store.load(null, null);
        OpenSslX509KeyManagerFactory factory = new OpenSslX509KeyManagerFactory();
        factory.init(store, null);
        return factory;
    }

    private static final class OpenSslKeyStore extends KeyStore {
        private OpenSslKeyStore(final X509Certificate[] certificateChain, final boolean keyless) {
            super(new KeyStoreSpi() {

                private final Date creationDate = new Date();

                @Override
                public Key engineGetKey(String alias, char[] password) throws UnrecoverableKeyException {
                    if (engineContainsAlias(alias)) {
                        final long privateKeyAddress;
                        if (keyless) {
                            privateKeyAddress = 0;
                        } else {
                            try {
                                privateKeyAddress = SSL.loadPrivateKeyFromEngine(
                                        alias, password == null ? null : new String(password));
                            } catch (Exception e) {
                                UnrecoverableKeyException keyException =
                                        new UnrecoverableKeyException("Unable to load key from engine");
                                keyException.initCause(e);
                                throw keyException;
                            }
                        }
                        return new OpenSslPrivateKey(privateKeyAddress);
                    }
                    return null;
                }

                @Override
                public Certificate[] engineGetCertificateChain(String alias) {
                    return engineContainsAlias(alias)? certificateChain.clone() : null;
                }

                @Override
                public Certificate engineGetCertificate(String alias) {
                    return engineContainsAlias(alias)? certificateChain[0] : null;
                }

                @Override
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
                    return Collections.enumeration(Collections.singleton(SslContext.ALIAS));
                }

                @Override
                public boolean engineContainsAlias(String alias) {
                    return SslContext.ALIAS.equals(alias);
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
                public String engineGetCertificateAlias(Certificate cert) {
                    if (cert instanceof X509Certificate) {
                        for (X509Certificate x509Certificate : certificateChain) {
                            if (x509Certificate.equals(cert)) {
                                return SslContext.ALIAS;
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
                public void engineLoad(InputStream stream, char[] password) {
                    if (stream != null && password != null) {
                        throw new UnsupportedOperationException();
                    }
                }
            }, null, "native");

            OpenSsl.ensureAvailability();
        }
    }
}
