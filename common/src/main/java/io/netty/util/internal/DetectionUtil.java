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
package io.netty.util.internal;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;


/**
 * Utility that detects various properties specific to the current runtime
 * environment, such as Java version and the availability of the
 * {@code sun.misc.Unsafe} object.
 * <p>
 * You can disable the use of {@code sun.misc.Unsafe} if you specify
 * the system property <strong>io.netty.noUnsafe</strong>.
 */
public final class DetectionUtil {

    private static final int JAVA_VERSION = javaVersion0();
    private static final boolean HAS_UNSAFE = hasUnsafe(AtomicInteger.class.getClassLoader());
    private static final boolean IS_WINDOWS;
    private static final boolean IS_ROOT;

    static {

        Pattern PERMISSION_DENIED = Pattern.compile(".*permission.*denied.*");
        String os = SystemPropertyUtil.get("os.name", "").toLowerCase(Locale.UK);
        // windows
        IS_WINDOWS = os.contains("win");

        boolean root = false;
        if (!IS_WINDOWS) {
            for (int i = 1023; i > 0; i --) {
                ServerSocket ss = null;
                try {
                    ss = new ServerSocket();
                    ss.setReuseAddress(true);
                    ss.bind(new InetSocketAddress(i));
                    root = true;
                    break;
                } catch (Exception e) {
                    // Failed to bind.
                    // Check the error message so that we don't always need to bind 1023 times.
                    String message = e.getMessage();
                    if (message == null) {
                        message = "";
                    }
                    message = message.toLowerCase();
                    if (PERMISSION_DENIED.matcher(message).matches()) {
                        break;
                    }
                } finally {
                    if (ss != null) {
                        try {
                            ss.close();
                        } catch (Exception e) {
                            // Ignore.
                        }
                    }
                }
            }
        }

        IS_ROOT = root;
    }

    /**
     * Return <code>true</code> if the JVM is running on Windows
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Return {@code true} if the current user is root.  Note that this method returns
     * {@code false} if on Windows.
     */
    public static boolean isRoot() {
        return IS_ROOT;
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
        boolean tryUnsafe = false;
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
            @Override
            public Boolean run() throws Exception {
                unsafeClass.getDeclaredField("theUnsafe");
                return true;
            }
        });
    }

    private static int javaVersion0() {
        // Android
        try {
            Class.forName("android.app.Application", false, ClassLoader.getSystemClassLoader());
            return 6;
        } catch (Exception e) {
            // Ignore
        }

        try {
            Class.forName(
                    "java.util.concurrent.LinkedTransferQueue", false,
                    BlockingQueue.class.getClassLoader());
            return 7;
        } catch (Exception e) {
            // Ignore
        }

        return 6;
    }

    private DetectionUtil() {
        // only static method supported
    }
}
