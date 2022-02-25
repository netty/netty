/*
 * Copyright 2017 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty5.util.internal.PlatformDependent;

import javax.net.ssl.SSLEngine;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Contains methods that can be used to detect if conscrypt is usable.
 */
final class Conscrypt {
    // This class exists to avoid loading other conscrypt related classes using features only available in JDK8+,
    // because we need to maintain JDK6+ runtime compatibility.

    private static final MethodHandle IS_CONSCRYPT_SSLENGINE;

    static {
        MethodHandle isConscryptSSLEngine = null;

        // Only works on Java14 and earlier for now. See https://github.com/google/conscrypt/issues/838
        if (PlatformDependent.javaVersion() < 15 || PlatformDependent.isAndroid()) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Class<?> providerClass = Class.forName("org.conscrypt.OpenSSLProvider", true,
                        PlatformDependent.getClassLoader(ConscryptAlpnSslEngine.class));
                lookup.findConstructor(providerClass, MethodType.methodType(void.class)).invoke();

                Class<?> conscryptClass = Class.forName("org.conscrypt.Conscrypt", true,
                        PlatformDependent.getClassLoader(ConscryptAlpnSslEngine.class));
                isConscryptSSLEngine = lookup.findStatic(conscryptClass, "isConscrypt",
                        MethodType.methodType(boolean.class, SSLEngine.class));
            } catch (Throwable ignore) {
                // ignore
            }
        }
        IS_CONSCRYPT_SSLENGINE = isConscryptSSLEngine;
    }

    /**
     * Indicates whether or not conscrypt is available on the current system.
     */
    static boolean isAvailable() {
        return IS_CONSCRYPT_SSLENGINE != null;
    }

    /**
     * Returns {@code true} if the passed in {@link SSLEngine} is handled by Conscrypt, {@code false} otherwise.
     */
    static boolean isEngineSupported(SSLEngine engine) {
        try {
            return IS_CONSCRYPT_SSLENGINE != null && (boolean) IS_CONSCRYPT_SSLENGINE.invokeExact(engine);
        } catch (IllegalAccessException ignore) {
            return false;
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    private Conscrypt() { }
}
