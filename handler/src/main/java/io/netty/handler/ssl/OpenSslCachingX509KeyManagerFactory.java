/*
 * Copyright 2018 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;

/**
 * Wraps another {@link KeyManagerFactory} and caches its chains / certs for an alias for better performance when using
 * {@link SslProvider#OPENSSL} or {@link SslProvider#OPENSSL_REFCNT}.
 *
 * Because of the caching its important that the wrapped {@link KeyManagerFactory}s {@link X509KeyManager}s always
 * return the same {@link X509Certificate} chain and {@link PrivateKey} for the same alias.
 */
public final class OpenSslCachingX509KeyManagerFactory extends KeyManagerFactory {

    private final int maxCachedEntries;

    public OpenSslCachingX509KeyManagerFactory(final KeyManagerFactory factory) {
        this(factory, 1024);
    }

    public OpenSslCachingX509KeyManagerFactory(final KeyManagerFactory factory, int maxCachedEntries) {
        super(new KeyManagerFactorySpi() {
            @Override
            protected void engineInit(KeyStore keyStore, char[] chars)
                    throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
                factory.init(keyStore, chars);
            }

            @Override
            protected void engineInit(ManagerFactoryParameters managerFactoryParameters)
                    throws InvalidAlgorithmParameterException {
                factory.init(managerFactoryParameters);
            }

            @Override
            protected KeyManager[] engineGetKeyManagers() {
                return factory.getKeyManagers();
            }
        }, factory.getProvider(), factory.getAlgorithm());
        this.maxCachedEntries = ObjectUtil.checkPositive(maxCachedEntries, "maxCachedEntries");
    }

    OpenSslKeyMaterialProvider newProvider(String password) {
        X509KeyManager keyManager = ReferenceCountedOpenSslContext.chooseX509KeyManager(getKeyManagers());
        if ("sun.security.ssl.X509KeyManagerImpl".equals(keyManager.getClass().getName())) {
            // Don't do caching if X509KeyManagerImpl is used as the returned aliases are not stable and will change
            // between invocations.
            return new OpenSslKeyMaterialProvider(keyManager, password);
        }
        return new OpenSslCachingKeyMaterialProvider(
                ReferenceCountedOpenSslContext.chooseX509KeyManager(getKeyManagers()), password, maxCachedEntries);
    }
}
