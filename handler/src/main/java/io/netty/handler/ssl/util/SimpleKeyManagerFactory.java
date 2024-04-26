/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.ssl.util;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

/**
 * Helps to implement a custom {@link KeyManagerFactory}.
 */
public abstract class SimpleKeyManagerFactory extends KeyManagerFactory {

    private static final Provider PROVIDER = new Provider("", 0.0, "") {
        private static final long serialVersionUID = -2680540247105807895L;
    };

    /**
     * {@link SimpleKeyManagerFactorySpi} must have a reference to {@link SimpleKeyManagerFactory}
     * to delegate its callbacks back to {@link SimpleKeyManagerFactory}.  However, it is impossible to do so,
     * because {@link KeyManagerFactory} requires {@link KeyManagerFactorySpi} at construction time and
     * does not provide a way to access it later.
     *
     * To work around this issue, we use an ugly hack which uses a {@link FastThreadLocal }.
     */
    private static final FastThreadLocal<SimpleKeyManagerFactorySpi> CURRENT_SPI =
            new FastThreadLocal<SimpleKeyManagerFactorySpi>() {
                @Override
                protected SimpleKeyManagerFactorySpi initialValue() {
                    return new SimpleKeyManagerFactorySpi();
                }
            };

    /**
     * Creates a new instance.
     */
    protected SimpleKeyManagerFactory() {
        this(StringUtil.EMPTY_STRING);
    }

    /**
     * Creates a new instance.
     *
     * @param name the name of this {@link KeyManagerFactory}
     */
    protected SimpleKeyManagerFactory(String name) {
        super(CURRENT_SPI.get(), PROVIDER, ObjectUtil.checkNotNull(name, "name"));
        CURRENT_SPI.get().init(this);
        CURRENT_SPI.remove();
    }

    /**
     * Initializes this factory with a source of certificate authorities and related key material.
     *
     * @see KeyManagerFactorySpi#engineInit(KeyStore, char[])
     */
    protected abstract void engineInit(KeyStore keyStore, char[] var2) throws Exception;

    /**
     * Initializes this factory with a source of provider-specific key material.
     *
     * @see KeyManagerFactorySpi#engineInit(ManagerFactoryParameters)
     */
    protected abstract void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception;

    /**
     * Returns one key manager for each type of key material.
     *
     * @see KeyManagerFactorySpi#engineGetKeyManagers()
     */
    protected abstract KeyManager[] engineGetKeyManagers();

    private static final class SimpleKeyManagerFactorySpi extends KeyManagerFactorySpi {

        private SimpleKeyManagerFactory parent;
        private volatile KeyManager[] keyManagers;

        void init(SimpleKeyManagerFactory parent) {
            this.parent = parent;
        }

        @Override
        protected void engineInit(KeyStore keyStore, char[] pwd) throws KeyStoreException {
            try {
                parent.engineInit(keyStore, pwd);
            } catch (KeyStoreException e) {
                throw e;
            } catch (Exception e) {
                throw new KeyStoreException(e);
            }
        }

        @Override
        protected void engineInit(
                ManagerFactoryParameters managerFactoryParameters) throws InvalidAlgorithmParameterException {
            try {
                parent.engineInit(managerFactoryParameters);
            } catch (InvalidAlgorithmParameterException e) {
                throw e;
            } catch (Exception e) {
                throw new InvalidAlgorithmParameterException(e);
            }
        }

        @Override
        protected KeyManager[] engineGetKeyManagers() {
            KeyManager[] keyManagers = this.keyManagers;
            if (keyManagers == null) {
                keyManagers = parent.engineGetKeyManagers();
                wrapIfNeeded(keyManagers);
                this.keyManagers = keyManagers;
            }
            return keyManagers.clone();
        }

        private static void wrapIfNeeded(KeyManager[] keyManagers) {
            for (int i = 0; i < keyManagers.length; i++) {
                final KeyManager tm = keyManagers[i];
                if (tm instanceof X509KeyManager && !(tm instanceof X509ExtendedKeyManager)) {
                    keyManagers[i] = new X509KeyManagerWrapper((X509KeyManager) tm);
                }
            }
        }
    }
}
