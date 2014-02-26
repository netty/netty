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

import io.netty.util.CharsetUtil;
import io.netty.util.internal.chmv8.ConcurrentHashMapV8;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.regex.Matcher;
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

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent.class);

    private static final Pattern MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN = Pattern.compile(
            "\\s*-XX:MaxDirectMemorySize\\s*=\\s*([0-9]+)\\s*([kKmMgG]?)\\s*$");

    private static final boolean IS_ANDROID = isAndroid0();
    private static final boolean IS_WINDOWS = isWindows0();
    private static final boolean IS_ROOT = isRoot0();

    private static final int JAVA_VERSION = javaVersion0();

    private static final boolean CAN_ENABLE_TCP_NODELAY_BY_DEFAULT = !isAndroid();

    private static final boolean HAS_UNSAFE = hasUnsafe0();
    private static final boolean CAN_USE_CHM_V8 = HAS_UNSAFE && JAVA_VERSION < 8;
    private static final boolean DIRECT_BUFFER_PREFERRED =
            HAS_UNSAFE && !SystemPropertyUtil.getBoolean("io.netty.noPreferDirect", false);
    private static final long MAX_DIRECT_MEMORY = maxDirectMemory0();

    private static final long ARRAY_BASE_OFFSET = arrayBaseOffset0();

    private static final boolean HAS_JAVASSIST = hasJavassist0();

    private static final File TMPDIR = tmpdir0();

    private static final int BIT_MODE = bitMode0();

    static {
        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.noPreferDirect: {}", !DIRECT_BUFFER_PREFERRED);
        }

        if (!hasUnsafe() && !isAndroid()) {
            logger.info(
                    "Your platform does not provide complete low-level API for accessing direct buffers reliably. " +
                    "Unless explicitly requested, heap buffer will always be preferred to avoid potential system " +
                    "unstability.");
        }
    }

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
     * Returns {@code true} if the platform has reliable low-level direct buffer access API and a user specified
     * {@code -Dio.netty.preferDirect} option.
     */
    public static boolean directBufferPreferred() {
        return DIRECT_BUFFER_PREFERRED;
    }

    /**
     * Returns the maximum memory reserved for direct buffer allocation.
     */
    public static long maxDirectMemory() {
        return MAX_DIRECT_MEMORY;
    }

    /**
     * Returns {@code true} if and only if Javassist is available.
     */
    public static boolean hasJavassist() {
        return HAS_JAVASSIST;
    }

    /**
     * Returns the temporary directory.
     */
    public static File tmpdir() {
        return TMPDIR;
    }

    /**
     * Returns the bit mode of the current VM (usually 32 or 64.)
     */
    public static int bitMode() {
        return BIT_MODE;
    }

    /**
     * Raises an exception bypassing compiler checks for checked exceptions.
     */
    public static void throwException(Throwable t) {
        if (hasUnsafe()) {
            PlatformDependent0.throwException(t);
        } else {
            PlatformDependent.<RuntimeException>throwException0(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwException0(Throwable t) throws E {
        throw (E) t;
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
     * Try to deallocate the specified direct {@link ByteBuffer}.  Please note this method does nothing if
     * the current platform does not support this operation or the specified buffer is not a direct buffer.
     */
    public static void freeDirectBuffer(ByteBuffer buffer) {
        if (buffer.isDirect()) {
            if (hasUnsafe()) {
                PlatformDependent0.freeDirectBufferUnsafe(buffer);
            } else {
                PlatformDependent0.freeDirectBuffer(buffer);
            }
        }
    }

    public static long directBufferAddress(ByteBuffer buffer) {
        return PlatformDependent0.directBufferAddress(buffer);
    }

    public static Object getObject(Object object, long fieldOffset) {
        return PlatformDependent0.getObject(object, fieldOffset);
    }

    public static Object getObjectVolatile(Object object, long fieldOffset) {
        return PlatformDependent0.getObjectVolatile(object, fieldOffset);
    }

    public static int getInt(Object object, long fieldOffset) {
        return PlatformDependent0.getInt(object, fieldOffset);
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

    public static void putOrderedObject(Object object, long address, Object value) {
        PlatformDependent0.putOrderedObject(object, address, value);
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

    /**
     * Create a new optimized {@link AtomicReferenceFieldUpdater} or {@code null} if it
     * could not be created. Because of this the caller need to check for {@code null} and if {@code null} is returned
     * use {@link AtomicReferenceFieldUpdater#newUpdater(Class, Class, String)} as fallback.
     */
    public static <U, W> AtomicReferenceFieldUpdater<U, W> newAtomicReferenceFieldUpdater(
            Class<U> tclass, String fieldName) {
        if (hasUnsafe()) {
            try {
                return PlatformDependent0.newAtomicReferenceFieldUpdater(tclass, fieldName);
            } catch (Throwable ignore) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Create a new optimized {@link AtomicIntegerFieldUpdater} or {@code null} if it
     * could not be created. Because of this the caller need to check for {@code null} and if {@code null} is returned
     * use {@link AtomicIntegerFieldUpdater#newUpdater(Class, String)} as fallback.
     */
    public static <T> AtomicIntegerFieldUpdater<T> newAtomicIntegerFieldUpdater(
            Class<?> tclass, String fieldName) {
        if (hasUnsafe()) {
            try {
                return PlatformDependent0.newAtomicIntegerFieldUpdater(tclass, fieldName);
            } catch (Throwable ignore) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Create a new optimized {@link AtomicLongFieldUpdater} or {@code null} if it
     * could not be created. Because of this the caller need to check for {@code null} and if {@code null} is returned
     * use {@link AtomicLongFieldUpdater#newUpdater(Class, String)} as fallback.
     */
    public static <T> AtomicLongFieldUpdater<T> newAtomicLongFieldUpdater(
            Class<?> tclass, String fieldName) {
        if (hasUnsafe()) {
            try {
                return PlatformDependent0.newAtomicLongFieldUpdater(tclass, fieldName);
            } catch (Throwable ignore) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     */
    public static Queue<Runnable> newMpscQueue() {
        if (hasUnsafe()) {
            return new MpscLinkedQueue();
        } else {
            return new ConcurrentLinkedQueue<Runnable>();
        }
    }

    private static boolean isAndroid0() {
        boolean android;
        try {
            Class.forName("android.app.Application", false, ClassLoader.getSystemClassLoader());
            android = true;
        } catch (Exception e) {
            // Failed to load the class uniquely available in Android.
            android = false;
        }

        if (android) {
            logger.debug("Platform: Android");
        }
        return android;
    }

    private static boolean isWindows0() {
        boolean windows = SystemPropertyUtil.get("os.name", "").toLowerCase(Locale.US).contains("win");
        if (windows) {
            logger.debug("Platform: Windows");
        }
        return windows;
    }

    private static boolean isRoot0() {
        if (isWindows()) {
            return false;
        }

        String[] ID_COMMANDS = { "/usr/bin/id", "/bin/id", "id", "/usr/xpg4/bin/id"};
        Pattern UID_PATTERN = Pattern.compile("^(?:0|[1-9][0-9]*)$");
        for (String idCmd: ID_COMMANDS) {
            Process p = null;
            BufferedReader in = null;
            String uid = null;
            try {
                p = Runtime.getRuntime().exec(new String[] { idCmd, "-u" });
                in = new BufferedReader(new InputStreamReader(p.getInputStream(), CharsetUtil.US_ASCII));
                uid = in.readLine();
                in.close();

                for (;;) {
                    try {
                        int exitCode = p.waitFor();
                        if (exitCode != 0) {
                            uid = null;
                        }
                        break;
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            } catch (Exception e) {
                // Failed to run the command.
                uid = null;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
                if (p != null) {
                    try {
                        p.destroy();
                    } catch (Exception e) {
                        // Android sometimes triggers an ErrnoException.
                    }
                }
            }

            if (uid != null && UID_PATTERN.matcher(uid).matches()) {
                logger.debug("UID: {}", uid);
                return "0".equals(uid);
            }
        }

        logger.debug("Could not determine the current UID using /usr/bin/id; attempting to bind at privileged ports.");

        Pattern PERMISSION_DENIED = Pattern.compile(".*(?:denied|not.*permitted).*");
        for (int i = 1023; i > 0; i --) {
            ServerSocket ss = null;
            try {
                ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(i));
                if (logger.isDebugEnabled()) {
                    logger.debug("UID: 0 (succeded to bind at port {})", i);
                }
                return true;
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

        logger.debug("UID: non-root (failed to bind at any privileged ports)");
        return false;
    }

    @SuppressWarnings("LoopStatementThatDoesntLoop")
    private static int javaVersion0() {
        int javaVersion;

        // Not really a loop
        for (;;) {
            // Android
            if (isAndroid()) {
                javaVersion = 6;
                break;
            }

            try {
                Class.forName("java.time.Clock", false, Object.class.getClassLoader());
                javaVersion = 8;
                break;
            } catch (Exception e) {
                // Ignore
            }

            try {
                Class.forName("java.util.concurrent.LinkedTransferQueue", false, BlockingQueue.class.getClassLoader());
                javaVersion = 7;
                break;
            } catch (Exception e) {
                // Ignore
            }

            javaVersion = 6;
            break;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Java version: {}", javaVersion);
        }
        return javaVersion;
    }

    private static boolean hasUnsafe0() {
        boolean noUnsafe = SystemPropertyUtil.getBoolean("io.netty.noUnsafe", false);
        logger.debug("-Dio.netty.noUnsafe: {}", noUnsafe);

        if (isAndroid()) {
            logger.debug("sun.misc.Unsafe: unavailable (Android)");
            return false;
        }

        if (noUnsafe) {
            logger.debug("sun.misc.Unsafe: unavailable (io.netty.noUnsafe)");
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
            logger.debug("sun.misc.Unsafe: unavailable (io.netty.tryUnsafe/org.jboss.netty.tryUnsafe)");
            return false;
        }

        try {
            boolean hasUnsafe = PlatformDependent0.hasUnsafe();
            logger.debug("sun.misc.Unsafe: {}", hasUnsafe ? "available" : "unavailable");
            return hasUnsafe;
        } catch (Throwable t) {
            // Probably failed to initialize PlatformDependent0.
            return false;
        }
    }

    private static long arrayBaseOffset0() {
        if (!hasUnsafe()) {
            return -1;
        }

        return PlatformDependent0.arrayBaseOffset();
    }

    private static long maxDirectMemory0() {
        long maxDirectMemory = 0;
        try {
            // Try to get from sun.misc.VM.maxDirectMemory() which should be most accurate.
            Class<?> vmClass = Class.forName("sun.misc.VM", true, ClassLoader.getSystemClassLoader());
            Method m = vmClass.getDeclaredMethod("maxDirectMemory");
            maxDirectMemory = ((Number) m.invoke(null)).longValue();
        } catch (Throwable t) {
            // Ignore
        }

        if (maxDirectMemory > 0) {
            return maxDirectMemory;
        }

        try {
            // Now try to get the JVM option (-XX:MaxDirectMemorySize) and parse it.
            // Note that we are using reflection because Android doesn't have these classes.
            Class<?> mgmtFactoryClass = Class.forName(
                    "java.lang.management.ManagementFactory", true, ClassLoader.getSystemClassLoader());
            Class<?> runtimeClass = Class.forName(
                    "java.lang.management.RuntimeMXBean", true, ClassLoader.getSystemClassLoader());

            Object runtime = mgmtFactoryClass.getDeclaredMethod("getRuntimeMXBean").invoke(null);

            @SuppressWarnings("unchecked")
            List<String> vmArgs = (List<String>) runtimeClass.getDeclaredMethod("getInputArguments").invoke(runtime);
            for (int i = vmArgs.size() - 1; i >= 0; i --) {
                Matcher m = MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN.matcher(vmArgs.get(i));
                if (!m.matches()) {
                    continue;
                }

                maxDirectMemory = Long.parseLong(m.group(1));
                switch (m.group(2).charAt(0)) {
                    case 'k': case 'K':
                        maxDirectMemory *= 1024;
                        break;
                    case 'm': case 'M':
                        maxDirectMemory *= 1024 * 1024;
                        break;
                    case 'g': case 'G':
                        maxDirectMemory *= 1024 * 1024 * 1024;
                        break;
                }
                break;
            }
        } catch (Throwable t) {
            // Ignore
        }

        if (maxDirectMemory <= 0) {
            maxDirectMemory = Runtime.getRuntime().maxMemory();
            logger.debug("maxDirectMemory: {} bytes (maybe)", maxDirectMemory);
        } else {
            logger.debug("maxDirectMemory: {} bytes", maxDirectMemory);
        }

        return maxDirectMemory;
    }

    private static boolean hasJavassist0() {
        if (isAndroid()) {
            return false;
        }

        boolean noJavassist = SystemPropertyUtil.getBoolean("io.netty.noJavassist", false);
        logger.debug("-Dio.netty.noJavassist: {}", noJavassist);

        if (noJavassist) {
            logger.debug("Javassist: unavailable (io.netty.noJavassist)");
            return false;
        }

        try {
            JavassistTypeParameterMatcherGenerator.generate(Object.class, PlatformDependent.class.getClassLoader());
            logger.debug("Javassist: available");
            return true;
        } catch (Throwable t) {
            // Failed to generate a Javassist-based matcher.
            logger.debug("Javassist: unavailable");
            logger.debug(
                    "You don't have Javassist in your class path or you don't have enough permission " +
                    "to load dynamically generated classes.  Please check the configuration for better performance.");
            return false;
        }
    }

    private static File tmpdir0() {
        File f;
        try {
            f = toDirectory(SystemPropertyUtil.get("io.netty.tmpdir"));
            if (f != null) {
                logger.debug("-Dio.netty.tmpdir: {}", f);
                return f;
            }

            f = toDirectory(SystemPropertyUtil.get("java.io.tmpdir"));
            if (f != null) {
                logger.debug("-Dio.netty.tmpdir: {} (java.io.tmpdir)", f);
                return f;
            }

            // This shouldn't happen, but just in case ..
            if (isWindows()) {
                f = toDirectory(System.getenv("TEMP"));
                if (f != null) {
                    logger.debug("-Dio.netty.tmpdir: {} (%TEMP%)", f);
                    return f;
                }

                String userprofile = System.getenv("USERPROFILE");
                if (userprofile != null) {
                    f = toDirectory(userprofile + "\\AppData\\Local\\Temp");
                    if (f != null) {
                        logger.debug("-Dio.netty.tmpdir: {} (%USERPROFILE%\\AppData\\Local\\Temp)", f);
                        return f;
                    }

                    f = toDirectory(userprofile + "\\Local Settings\\Temp");
                    if (f != null) {
                        logger.debug("-Dio.netty.tmpdir: {} (%USERPROFILE%\\Local Settings\\Temp)", f);
                        return f;
                    }
                }
            } else {
                f = toDirectory(System.getenv("TMPDIR"));
                if (f != null) {
                    logger.debug("-Dio.netty.tmpdir: {} ($TMPDIR)", f);
                    return f;
                }
            }
        } catch (Exception ignored) {
            // Environment variable inaccessible
        }

        // Last resort.
        if (isWindows()) {
            f = new File("C:\\Windows\\Temp");
        } else {
            f = new File("/tmp");
        }

        logger.warn("Failed to get the temporary directory; falling back to: {}", f);
        return f;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static File toDirectory(String path) {
        if (path == null) {
            return null;
        }

        File f = new File(path);
        if (!f.exists()) {
            f.mkdirs();
        }

        if (!f.isDirectory()) {
            return null;
        }

        try {
            return f.getAbsoluteFile();
        } catch (Exception ignored) {
            return f;
        }
    }

    private static int bitMode0() {
        // Check user-specified bit mode first.
        int bitMode = SystemPropertyUtil.getInt("io.netty.bitMode", 0);
        if (bitMode > 0) {
            logger.debug("-Dio.netty.bitMode: {}", bitMode);
            return bitMode;
        }

        // And then the vendor specific ones which is probably most reliable.
        bitMode = SystemPropertyUtil.getInt("sun.arch.data.model", 0);
        if (bitMode > 0) {
            logger.debug("-Dio.netty.bitMode: {} (sun.arch.data.model)", bitMode);
            return bitMode;
        }
        bitMode = SystemPropertyUtil.getInt("com.ibm.vm.bitmode", 0);
        if (bitMode > 0) {
            logger.debug("-Dio.netty.bitMode: {} (com.ibm.vm.bitmode)", bitMode);
            return bitMode;
        }

        // os.arch also gives us a good hint.
        String arch = SystemPropertyUtil.get("os.arch", "").toLowerCase(Locale.US).trim();
        if ("amd64".equals(arch) || "x86_64".equals(arch)) {
            bitMode = 64;
        } else if ("i386".equals(arch) || "i486".equals(arch) || "i586".equals(arch) || "i686".equals(arch)) {
            bitMode = 32;
        }

        if (bitMode > 0) {
            logger.debug("-Dio.netty.bitMode: {} (os.arch: {})", bitMode, arch);
        }

        // Last resort: guess from VM name and then fall back to most common 64-bit mode.
        String vm = SystemPropertyUtil.get("java.vm.name", "").toLowerCase(Locale.US);
        Pattern BIT_PATTERN = Pattern.compile("([1-9][0-9]+)-?bit");
        Matcher m = BIT_PATTERN.matcher(vm);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        } else {
            return 64;
        }
    }

    private PlatformDependent() {
        // only static method supported
    }
}
