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
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.X509KeyManager;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Special {@link KeyManagerFactory} that pre-compute the keymaterial used when {@link SslProvider#OPENSSL} or
 * {@link SslProvider#OPENSSL_REFCNT} is used and so will improve handshake times and its performance.
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
            providerFactory = new ProviderFactory(
                    ReferenceCountedOpenSslContext.chooseX509KeyManager(kmf.getKeyManagers()),
                    password(chars), Collections.list(keyStore.aliases()));
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
}
