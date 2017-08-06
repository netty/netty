/*
 * Copyright 2017 The Netty Project
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

import io.netty.util.internal.PlatformDependent;

import javax.net.ssl.SSLEngine;
import java.lang.reflect.Method;

/**
 * Contains methods that can be used to detect if conscrypt is usable.
 */
final class Conscrypt {
    // This class exists to avoid loading other conscrypt related classes using features only available in JDK8+,
    // because we need to maintain JDK6+ runtime compatibility.
    private static final Class<?> ENGINES_CLASS = getEnginesClass();

    /**
     * Indicates whether or not conscrypt is available on the current system.
     */
    static boolean isAvailable() {
        return ENGINES_CLASS != null && PlatformDependent.javaVersion() >= 8;
    }

    static boolean isEngineSupported(SSLEngine engine) {
        return isAvailable() && isConscryptEngine(engine, ENGINES_CLASS);
    }

    private static Class<?> getEnginesClass() {
        try {
            // Always use bootstrap class loader.
            Class<?> engineClass = Class.forName("org.conscrypt.Conscrypt$Engines", true,
                    ConscryptAlpnSslEngine.class.getClassLoader());
            // Ensure that it also has the isConscrypt method.
            getIsConscryptMethod(engineClass);
            return engineClass;
        } catch (Throwable ignore) {
            // Conscrypt was not loaded.
            return null;
        }
    }

    private static boolean isConscryptEngine(SSLEngine engine, Class<?> enginesClass) {
        try {
            Method method = getIsConscryptMethod(enginesClass);
            return (Boolean) method.invoke(null, engine);
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static Method getIsConscryptMethod(Class<?> enginesClass) throws NoSuchMethodException {
        return enginesClass.getMethod("isConscrypt", SSLEngine.class);
    }

    private Conscrypt() { }
}
