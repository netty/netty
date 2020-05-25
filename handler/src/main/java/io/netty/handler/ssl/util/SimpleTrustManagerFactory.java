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

package io.netty.handler.ssl.util;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;

/**
 * Helps to implement a custom {@link TrustManagerFactory}.
 */
public abstract class SimpleTrustManagerFactory extends TrustManagerFactory {

    private static final Provider PROVIDER = new Provider("", 0.0, "") {
        private static final long serialVersionUID = -2680540247105807895L;
    };

    /**
     * {@link SimpleTrustManagerFactorySpi} must have a reference to {@link SimpleTrustManagerFactory}
     * to delegate its callbacks back to {@link SimpleTrustManagerFactory}.  However, it is impossible to do so,
     * because {@link TrustManagerFactory} requires {@link TrustManagerFactorySpi} at construction time and
     * does not provide a way to access it later.
     *
     * To work around this issue, we use an ugly hack which uses a {@link ThreadLocal}.
     */
    private static final FastThreadLocal<SimpleTrustManagerFactorySpi> CURRENT_SPI =
            new FastThreadLocal<SimpleTrustManagerFactorySpi>() {
                @Override
                protected SimpleTrustManagerFactorySpi initialValue() {
                    return new SimpleTrustManagerFactorySpi();
                }
            };

    /**
     * Creates a new instance.
     */
    protected SimpleTrustManagerFactory() {
        this("");
    }

    /**
     * Creates a new instance.
     *
     * @param name the name of this {@link TrustManagerFactory}
     */
    protected SimpleTrustManagerFactory(String name) {
        super(CURRENT_SPI.get(), PROVIDER, name);
        CURRENT_SPI.get().init(this);
        CURRENT_SPI.remove();

        ObjectUtil.checkNotNull(name, "name");
    }

    /**
     * Initializes this factory with a source of certificate authorities and related trust material.
     *
     * @see TrustManagerFactorySpi#engineInit(KeyStore)
     */
    protected abstract void engineInit(KeyStore keyStore) throws Exception;

    /**
     * Initializes this factory with a source of provider-specific key material.
     *
     * @see TrustManagerFactorySpi#engineInit(ManagerFactoryParameters)
     */
    protected abstract void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception;

    /**
     * Returns one trust manager for each type of trust material.
     *
     * @see TrustManagerFactorySpi#engineGetTrustManagers()
     */
    protected abstract TrustManager[] engineGetTrustManagers();

    static final class SimpleTrustManagerFactorySpi extends TrustManagerFactorySpi {

        private SimpleTrustManagerFactory parent;
        private volatile TrustManager[] trustManagers;

        void init(SimpleTrustManagerFactory parent) {
            this.parent = parent;
        }

        @Override
        protected void engineInit(KeyStore keyStore) throws KeyStoreException {
            try {
                parent.engineInit(keyStore);
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
        protected TrustManager[] engineGetTrustManagers() {
            TrustManager[] trustManagers = this.trustManagers;
            if (trustManagers == null) {
                trustManagers = parent.engineGetTrustManagers();
                if (PlatformDependent.javaVersion() >= 7) {
                    wrapIfNeeded(trustManagers);
                }
                this.trustManagers = trustManagers;
            }
            return trustManagers.clone();
        }

        @SuppressJava6Requirement(reason = "Usage guarded by java version check")
        private static void wrapIfNeeded(TrustManager[] trustManagers) {
            for (int i = 0; i < trustManagers.length; i++) {
                final TrustManager tm = trustManagers[i];
                if (tm instanceof X509TrustManager && !(tm instanceof X509ExtendedTrustManager)) {
                    trustManagers[i] = new X509TrustManagerWrapper((X509TrustManager) tm);
                }
            }
        }
    }
}
