/*
 * Copyright 2025 The Netty Project
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
package io.netty5.handler.ssl.util;

import io.netty5.util.internal.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Provider;
import java.security.Security;
import javax.net.ssl.SSLEngine;

/**
 * Contains methods that can be used to detect if BouncyCastle is available.
 */
public final class BouncyCastleUtil {
    private static final Logger logger = LoggerFactory.getLogger(BouncyCastleUtil.class);

    private static final String BC_PROVIDER_NAME = "BC";
    private static final String BC_PROVIDER = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    private static final String BC_FIPS_PROVIDER_NAME = "BCFIPS";
    private static final String BC_FIPS_PROVIDER = "org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider";
    private static final String BC_JSSE_PROVIDER_NAME = "BCJSSE";
    private static final String BC_JSSE_PROVIDER = "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider";
    private static final String BC_PEMPARSER = "org.bouncycastle.openssl.PEMParser";
    private static final String BC_JSSE_SSLENGINE = "org.bouncycastle.jsse.BCSSLEngine";
    private static final String BC_JSSE_ALPN_SELECTOR = "org.bouncycastle.jsse.BCApplicationProtocolSelector";

    private static volatile Throwable unavailabilityCauseBcProv;
    private static volatile Throwable unavailabilityCauseBcPkix;
    private static volatile Throwable unavailabilityCauseBcTls;
    private static volatile Provider bcProviderJce;
    private static volatile Provider bcProviderJsse;
    private static volatile Class<? extends SSLEngine> bcSSLEngineClass;
    private static volatile boolean attemptedLoading;

    /**
     * Indicate whether the BouncyCastle Java Crypto Extensions provider is available.
     */
    public static boolean isBcProvAvailable() {
        ensureLoaded();
        return unavailabilityCauseBcProv == null;
    }

    /**
     * Indicate whether the BouncyCastle Public-Key Infrastructure utilities are available.
     */
    public static boolean isBcPkixAvailable() {
        ensureLoaded();
        return unavailabilityCauseBcPkix == null;
    }

    /**
     * Indicate whether the BouncyCastle Java Secure Socket Extensions provider is available.
     */
    public static boolean isBcTlsAvailable() {
        ensureLoaded();
        return unavailabilityCauseBcTls == null;
    }

    /**
     * @return the cause if unavailable. {@code null} if available.
     */
    public static Throwable unavailabilityCauseBcProv() {
        ensureLoaded();
        return unavailabilityCauseBcProv;
    }

    /**
     * @return the cause if unavailable. {@code null} if available.
     */
    public static Throwable unavailabilityCauseBcPkix() {
        ensureLoaded();
        return unavailabilityCauseBcPkix;
    }

    /**
     * @return the cause if unavailable. {@code null} if available.
     */
    public static Throwable unavailabilityCauseBcTls() {
        ensureLoaded();
        return unavailabilityCauseBcTls;
    }

    /**
     * Indicates whether the given SSLEngine is implemented by BouncyCastle.
     */
    public static boolean isBcJsseInUse(SSLEngine engine) {
        ensureLoaded();
        Class<? extends SSLEngine> bcEngineClass = bcSSLEngineClass;
        return bcEngineClass != null && bcEngineClass.isInstance(engine);
    }

    /**
     * Get the BouncyCastle Java Crypto Extensions provider, or throw an exception if it is unavailable.
     */
    public static Provider getBcProviderJce() {
        ensureLoaded();
        Throwable cause = unavailabilityCauseBcProv;
        Provider provider = bcProviderJce;
        if (cause != null || provider == null) {
            throw new IllegalStateException(cause);
        }
        return provider;
    }

    /**
     * Get the BouncyCastle Java Secure Socket Extensions provider, or throw an exception if it is unavailable.
     */
    public static Provider getBcProviderJsse() {
        ensureLoaded();
        Throwable cause = unavailabilityCauseBcTls;
        Provider provider = bcProviderJsse;
        if (cause != null || provider == null) {
            throw new IllegalStateException(cause);
        }
        return provider;
    }

    /**
     * Reset the loaded providers. Useful for testing, to redo the loading under different conditions.
     */
    static void reset() {
        attemptedLoading = false;
        unavailabilityCauseBcProv = null;
        unavailabilityCauseBcPkix = null;
        unavailabilityCauseBcTls = null;
        bcProviderJce = null;
        bcProviderJsse = null;
        bcSSLEngineClass = null;
    }

    private static void ensureLoaded() {
        if (!attemptedLoading) {
            tryLoading();
        }
    }

    @SuppressWarnings("unchecked")
    private static void tryLoading() {
        try {
            // Check for bcprov-jdk18on or bc-fips:
            Provider provider = Security.getProvider(BC_PROVIDER_NAME);
            if (provider == null) {
                provider = Security.getProvider(BC_FIPS_PROVIDER_NAME);
            }
            if (provider == null) {
                ClassLoader classLoader = BouncyCastleUtil.class.getClassLoader();
                Class<Provider> bcProviderClass;
                try {
                    bcProviderClass = (Class<Provider>) Class.forName(BC_PROVIDER, true, classLoader);
                } catch (ClassNotFoundException e) {
                    try {
                        bcProviderClass = (Class<Provider>) Class.forName(BC_FIPS_PROVIDER, true, classLoader);
                    } catch (ClassNotFoundException ex) {
                        ThrowableUtil.addSuppressed(e, ex);
                        throw e;
                    }
                }
                provider = bcProviderClass.getConstructor().newInstance();
            }
            bcProviderJce = provider;
            logger.debug("Bouncy Castle provider available");
        } catch (Throwable e) {
            logger.debug("Cannot load Bouncy Castle provider", e);
            unavailabilityCauseBcProv = e;
        }

        try {
            // Check for bcpkix-jdk18on:
            ClassLoader classLoader = BouncyCastleUtil.class.getClassLoader();
            Provider provider = bcProviderJce;
            if (provider != null) {
                // Use provider class loader in case it was loaded by the system loader.
                classLoader = provider.getClass().getClassLoader();
            }
            Class.forName(BC_PEMPARSER, true, classLoader);
            logger.debug("Bouncy Castle PKIX available");
        } catch (Throwable e) {
            logger.debug("Cannot load Bouncy Castle PKIX", e);
            unavailabilityCauseBcPkix = e;
        }

        try {
            // Check for bctls-jdk18on:
            ClassLoader classLoader = BouncyCastleUtil.class.getClassLoader();
            Provider provider = Security.getProvider(BC_JSSE_PROVIDER_NAME);
            if (provider != null) {
                // Use provider class loader in case it was loaded by the system loader.
                classLoader = provider.getClass().getClassLoader();
            } else {
                Class<?> providerClass = Class.forName(BC_JSSE_PROVIDER, true, classLoader);
                provider = (Provider) providerClass.getConstructor().newInstance();
            }
            bcSSLEngineClass = (Class<? extends SSLEngine>) Class.forName(BC_JSSE_SSLENGINE, true, classLoader);
            Class.forName(BC_JSSE_ALPN_SELECTOR, true, classLoader);
            bcProviderJsse = provider;
            logger.debug("Bouncy Castle JSSE available");
        } catch (Throwable e) {
            logger.debug("Cannot load Bouncy Castle TLS", e);
            unavailabilityCauseBcTls = e;
        }
        attemptedLoading = true;
    }

    private BouncyCastleUtil() {
    }
}
