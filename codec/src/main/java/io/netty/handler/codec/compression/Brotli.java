/*
 * Copyright 2021 The Netty Project
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

package io.netty.handler.codec.compression;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public final class Brotli {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Brotli.class);
    private static final ClassNotFoundException CNFE;
    private static Throwable cause;

    static {
        ClassNotFoundException cnfe = null;

        try {
            Class.forName("com.aayushatharva.brotli4j.Brotli4jLoader", false,
                PlatformDependent.getClassLoader(Brotli.class));
        } catch (ClassNotFoundException t) {
            cnfe = t;
            logger.debug(
                "brotli4j not in the classpath; Brotli support will be unavailable.");
        }

        CNFE = cnfe;

        // If in the classpath, try to load the native library and initialize brotli4j.
        if (cnfe == null) {
            cause = Brotli4jLoader.getUnavailabilityCause();
            if (cause != null) {
                logger.debug("Failed to load brotli4j; Brotli support will be unavailable.", cause);
            }
        }
    }

    /**
     *
     * @return true when brotli4j is in the classpath
     * and native library is available on this platform and could be loaded
     */
    public static boolean isAvailable() {
        return CNFE == null && Brotli4jLoader.isAvailable();
    }

    /**
     * Throws when brotli support is missing from the classpath or is unavailable on this platform
     * @throws Throwable a ClassNotFoundException if brotli4j is missing
     * or a UnsatisfiedLinkError if brotli4j native lib can't be loaded
     */
    public static void ensureAvailability() throws Throwable {
        if (CNFE != null) {
            throw CNFE;
        }
        Brotli4jLoader.ensureAvailability();
    }

    /**
     * Returns {@link Throwable} of unavailability cause
     */
    public static Throwable cause() {
        return cause;
    }

    private Brotli() {
    }
}
