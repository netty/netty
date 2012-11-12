/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.util.internal;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility that detects various properties specific to the current runtime
 * environment, such as Java version and the availability of the
 * {@code sun.misc.Unsafe} object.
 *
 * <br>
 * You can disable the use of {@code sun.misc.Unsafe} if you specify
 * the System property <strong>org.jboss.netty.tryUnsafe</strong> with
 * value of {@code false}. Default is {@code true}.
 */
public final class DetectionUtil {

    private static final int JAVA_VERSION = javaVersion0();
    private static final boolean HAS_UNSAFE = hasUnsafe(AtomicInteger.class.getClassLoader());
    private static final boolean IS_WINDOWS;
    static {
        String os = SystemPropertyUtil.get("os.name", "").toLowerCase();
        // windows
        IS_WINDOWS =  os.contains("win");
    }

    /**
     * Return {@code true} if the JVM is running on Windows
     *
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static boolean hasUnsafe() {
        return HAS_UNSAFE;
    }

    public static int javaVersion() {
        return JAVA_VERSION;
    }

    private static boolean hasUnsafe(ClassLoader loader) {
        boolean noUnsafe = SystemPropertyUtil.getBoolean("io.netty.noUnsafe", false);
        if (noUnsafe) {
            return false;
        }

        // Legacy properties
        boolean tryUnsafe;
        if (SystemPropertyUtil.contains("io.netty.tryUnsafe")) {
            tryUnsafe = SystemPropertyUtil.getBoolean("io.netty.tryUnsafe", true);
        } else {
            tryUnsafe = SystemPropertyUtil.getBoolean("org.jboss.netty.tryUnsafe", true);
        }

        if (!tryUnsafe) {
            return false;
        }

        try {
            Class<?> unsafeClazz = Class.forName("sun.misc.Unsafe", true, loader);
            return hasUnsafeField(unsafeClazz);
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    private static boolean hasUnsafeField(final Class<?> unsafeClass) throws PrivilegedActionException {
        return AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>() {
            public Boolean run() throws Exception {
                unsafeClass.getDeclaredField("theUnsafe");
                return true;
            }
        });
    }

    private static int javaVersion0() {
        try {
            // Check if its android, if so handle it the same way as java6.
            //
            // See https://github.com/netty/netty/issues/282
            Class.forName("android.app.Application");
            return 6;
        } catch (ClassNotFoundException e) {
            //Ignore
        }

        try {
            Class.forName(
                    "java.util.concurrent.LinkedTransferQueue", false,
                    BlockingQueue.class.getClassLoader());
            return 7;
        } catch (Exception e) {
            // Ignore
        }

        try {
            Class.forName(
                    "java.util.ArrayDeque", false, Queue.class.getClassLoader());
            return 6;
        } catch (Exception e) {
            // Ignore
        }

        return 5;
    }

    private DetectionUtil() {
        // only static method supported
    }
}
