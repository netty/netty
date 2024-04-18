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

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public final class Zstd {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Zstd.class);
    private static final Throwable cause;

    static {
        Throwable t = null;

        try {
            Class.forName("com.github.luben.zstd.Zstd", false,
                PlatformDependent.getClassLoader(Zstd.class));
        } catch (ClassNotFoundException e) {
            t = e;
            logger.debug(
                "zstd-jni not in the classpath; Zstd support will be unavailable.");
        }

        // If in the classpath, try to load the native library and initialize zstd.
        if (t == null) {
            try {
                com.github.luben.zstd.util.Native.load();
            } catch (Throwable e) {
                t = e;
                logger.debug("Failed to load zstd-jni; Zstd support will be unavailable.", t);
            }
        }
        cause = t;
    }

    /**
     *
     * @return true when zstd-jni is in the classpath
     * and native library is available on this platform and could be loaded
     */
    public static boolean isAvailable() {
        return cause == null;
    }

    /**
     * Throws when zstd support is missing from the classpath or is unavailable on this platform
     * @throws Throwable a ClassNotFoundException if zstd-jni is missing
     * or a ExceptionInInitializerError if zstd native lib can't be loaded
     */
    public static void ensureAvailability() throws Throwable {
        if (cause != null) {
            throw cause;
        }
    }

    /**
     * Returns {@link Throwable} of unavailability cause
     */
    public static Throwable cause() {
        return cause;
    }

    private Zstd() {
    }
}
