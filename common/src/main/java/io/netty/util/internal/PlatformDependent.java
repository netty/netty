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

import io.netty.util.internal.chmv8.ConcurrentHashMapV8;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;


/**
 * Utility that detects various properties specific to the current runtime
 * environment, such as Java version and the availability of the
 * {@code sun.misc.Unsafe} object.
 * <p>
 * You can disable the use of {@code sun.misc.Unsafe} if you specify
 * the system property <strong>io.netty.noUnsafe</strong>.
 */
public final class PlatformDependent {

    private static final boolean IS_ANDROID = isAndroid0();
    private static final boolean IS_WINDOWS = isWindows0();
    private static final boolean IS_ROOT = isRoot0();

    private static final int JAVA_VERSION = javaVersion0();

    private static final boolean CAN_ENABLE_TCP_NODELAY_BY_DEFAULT = !isAndroid();

    private static final boolean HAS_UNSAFE = hasUnsafe0();
    private static final boolean CAN_USE_CHM_V8 = HAS_UNSAFE && JAVA_VERSION < 8;
    private static final long ARRAY_BASE_OFFSET = arrayBaseOffset0();

    private static final boolean HAS_JAVASSIST = hasJavassist0();

    /**
     * Returns {@code true} if and only if the current platform is Android
     */
    public static boolean isAndroid() {
        return IS_ANDROID;
    }

    /**
     * Return {@code true} if the JVM is running on Windows
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

    /**
     * Return the version of Java under which this library is used.
     */
    public static int javaVersion() {
        return JAVA_VERSION;
    }

    /**
     * Returns {@code true} if and only if it is fine to enable TCP_NODELAY socket option by default.
     */
    public static boolean canEnableTcpNoDelayByDefault() {
        return CAN_ENABLE_TCP_NODELAY_BY_DEFAULT;
    }

    /**
     * Return {@code true} if {@code sun.misc.Unsafe} was found on the classpath and can be used for acclerated
     * direct memory access.
     */
    public static boolean hasUnsafe() {
        return HAS_UNSAFE;
    }

    /**
     * Returns {@code true} if and only if Javassist is available.
     */
    public static boolean hasJavassist() {
        return HAS_JAVASSIST;
    }

    /**
     * Creates a new fastest {@link ConcurrentMap} implementaion for the current platform.
     */
    public static <K, V> ConcurrentMap<K, V> newConcurrentHashMap() {
        if (CAN_USE_CHM_V8) {
            return new ConcurrentHashMapV8<K, V>();
        } else {
            return new ConcurrentHashMap<K, V>();
        }
    }

    /**
     * Creates a new fastest {@link ConcurrentMap} implementaion for the current platform.
     */
    public static <K, V> ConcurrentMap<K, V> newConcurrentHashMap(int initialCapacity) {
        if (CAN_USE_CHM_V8) {
            return new ConcurrentHashMapV8<K, V>(initialCapacity);
        } else {
            return new ConcurrentHashMap<K, V>(initialCapacity);
        }
    }

    /**
     * Creates a new fastest {@link ConcurrentMap} implementaion for the current platform.
     */
    public static <K, V> ConcurrentMap<K, V> newConcurrentHashMap(int initialCapacity, float loadFactor) {
        if (CAN_USE_CHM_V8) {
            return new ConcurrentHashMapV8<K, V>(initialCapacity, loadFactor);
        } else {
            return new ConcurrentHashMap<K, V>(initialCapacity, loadFactor);
        }
    }

    /**
     * Creates a new fastest {@link ConcurrentMap} implementaion for the current platform.
     */
    public static <K, V> ConcurrentMap<K, V> newConcurrentHashMap(
            int initialCapacity, float loadFactor, int concurrencyLevel) {
        if (CAN_USE_CHM_V8) {
            return new ConcurrentHashMapV8<K, V>(initialCapacity, loadFactor, concurrencyLevel);
        } else {
            return new ConcurrentHashMap<K, V>(initialCapacity, loadFactor, concurrencyLevel);
        }
    }

    /**
     * Creates a new fastest {@link ConcurrentMap} implementaion for the current platform.
     */
    public static <K, V> ConcurrentMap<K, V> newConcurrentHashMap(Map<? extends K, ? extends V> map) {
        if (CAN_USE_CHM_V8) {
            return new ConcurrentHashMapV8<K, V>(map);
        } else {
            return new ConcurrentHashMap<K, V>(map);
        }
    }

    /**
     * Try to allocate a new direct {@link ByteBuffer} using the best available allocator.
     */
    public static ByteBuffer newDirectBuffer(int capacity) {
        return PlatformDependent0.newDirectBuffer(capacity);
    }

    /**
     * Try to deallocate the specified direct {@link ByteBuffer}.  Please note this method does nothing if
     * the current platform does not support this operation or the specified buffer is not a direct buffer.
     */
    public static void freeDirectBuffer(ByteBuffer buffer) {
        if (buffer.isDirect()) {
            PlatformDependent0.freeDirectBuffer(buffer);
        }
    }

    public static long directBufferAddress(ByteBuffer buffer) {
        return PlatformDependent0.directBufferAddress(buffer);
    }

    public static Object getObject(Object object, long fieldOffset) {
        return PlatformDependent0.getObject(object, fieldOffset);
    }

    public static long objectFieldOffset(Field field) {
        return PlatformDependent0.objectFieldOffset(field);
    }

    public static byte getByte(long address) {
        return PlatformDependent0.getByte(address);
    }

    public static short getShort(long address) {
        return PlatformDependent0.getShort(address);
    }

    public static int getInt(long address) {
        return PlatformDependent0.getInt(address);
    }

    public static long getLong(long address) {
        return PlatformDependent0.getLong(address);
    }

    public static void putByte(long address, byte value) {
        PlatformDependent0.putByte(address, value);
    }

    public static void putShort(long address, short value) {
        PlatformDependent0.putShort(address, value);
    }

    public static void putInt(long address, int value) {
        PlatformDependent0.putInt(address, value);
    }

    public static void putLong(long address, long value) {
        PlatformDependent0.putLong(address, value);
    }

    public static void copyMemory(long srcAddr, long dstAddr, long length) {
        PlatformDependent0.copyMemory(srcAddr, dstAddr, length);
    }

    public static void copyMemory(byte[] src, int srcIndex, long dstAddr, long length) {
        PlatformDependent0.copyMemory(src, ARRAY_BASE_OFFSET + srcIndex, null, dstAddr, length);
    }

    public static void copyMemory(long srcAddr, byte[] dst, int dstIndex, long length) {
        PlatformDependent0.copyMemory(null, srcAddr, dst, ARRAY_BASE_OFFSET + dstIndex, length);
    }

    private static boolean isAndroid0() {
        boolean android;
        try {
            Class.forName("android.app.Application", false, ClassLoader.getSystemClassLoader());
            android = true;
        } catch (Exception e) {
            android = false;
        }
        return android;
    }

    private static boolean isWindows0() {
        return SystemPropertyUtil.get("os.name", "").toLowerCase(Locale.US).contains("win");
    }

    private static boolean isRoot0() {
        Pattern PERMISSION_DENIED = Pattern.compile(".*permission.*denied.*");
        boolean root = false;
        if (!isWindows()) {
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
        return root;
    }

    private static int javaVersion0() {
        // Android
        if (isAndroid()) {
            return 6;
        }

        try {
            Class.forName("java.time.Clock", false, Object.class.getClassLoader());
            return 8;
        } catch (Exception e) {
            // Ignore
        }

        try {
            Class.forName("java.util.concurrent.LinkedTransferQueue", false, BlockingQueue.class.getClassLoader());
            return 7;
        } catch (Exception e) {
            // Ignore
        }

        return 6;
    }

    private static boolean hasUnsafe0() {
        if (isAndroid()) {
            return false;
        }

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
            return PlatformDependent0.hasUnsafe();
        } catch (Throwable t) {
            return false;
        }
    }

    private static long arrayBaseOffset0() {
        if (!hasUnsafe()) {
            return -1;
        }

        return PlatformDependent0.arrayBaseOffset();
    }

    private static boolean hasJavassist0() {
        boolean noJavassist = SystemPropertyUtil.getBoolean("io.netty.noJavassist", false);
        if (noJavassist) {
            return false;
        }

        try {
            JavassistTypeParameterMatcherGenerator.generate(Object.class, PlatformDependent.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private PlatformDependent() {
        // only static method supported
    }
}
