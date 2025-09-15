/*
 * Copyright 2012 The Netty Project
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
package io.netty5.util.internal;

import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.MpscChunkedArrayQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.jctools.queues.atomic.MpscAtomicArrayQueue;
import org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue;
import org.jctools.queues.atomic.MpscUnboundedAtomicArrayQueue;
import org.jctools.queues.atomic.SpscLinkedAtomicQueue;
import org.jctools.queues.unpadded.SpscLinkedUnpaddedQueue;
import org.jctools.util.Pow2;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.StackWalker.Option;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty5.util.internal.PlatformDependent0.HASH_CODE_ASCII_SEED;
import static io.netty5.util.internal.PlatformDependent0.HASH_CODE_C1;
import static io.netty5.util.internal.PlatformDependent0.HASH_CODE_C2;
import static io.netty5.util.internal.PlatformDependent0.hashCodeAsciiSanitize;
import static io.netty5.util.internal.PlatformDependent0.unalignedAccess;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Utility that detects various properties specific to the current runtime
 * environment, such as Java version and the availability of the
 * {@code sun.misc.Unsafe} object.
 * <p>
 * You can disable the use of {@code sun.misc.Unsafe} if you specify
 * the system property <strong>io.netty5.noUnsafe</strong>.
 */
public final class PlatformDependent {

    private static final Logger logger = LoggerFactory.getLogger(PlatformDependent.class);

    private static Pattern MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN;

    private static final boolean MAYBE_SUPER_USER;

    private static final boolean CAN_ENABLE_TCP_NODELAY_BY_DEFAULT = !isAndroid();

    private static final Throwable UNSAFE_UNAVAILABILITY_CAUSE = unsafeUnavailabilityCause0();
    private static final boolean DIRECT_BUFFER_PREFERRED;
    private static final boolean EXPLICIT_NO_PREFER_DIRECT;
    private static final long MAX_DIRECT_MEMORY = estimateMaxDirectMemory();

    private static final int MPSC_CHUNK_SIZE =  1024;
    private static final int MIN_MAX_MPSC_CAPACITY =  MPSC_CHUNK_SIZE * 2;
    private static final int MAX_ALLOWED_MPSC_CAPACITY = Pow2.MAX_POW2;

    private static final long BYTE_ARRAY_BASE_OFFSET = byteArrayBaseOffset0();

    private static final File TMPDIR = tmpdir0();

    private static final int BIT_MODE = bitMode0();
    private static final String NORMALIZED_ARCH = normalizeArch(SystemPropertyUtil.get("os.arch", ""));
    private static final String NORMALIZED_OS = normalizeOs(SystemPropertyUtil.get("os.name", ""));

    private static final Set<String> LINUX_OS_CLASSIFIERS;

    private static final boolean IS_WINDOWS = isWindows0();
    private static final boolean IS_OSX = isOsx0();
    private static final boolean IS_J9_JVM = isJ9Jvm0();
    private static final boolean IS_IVKVM_DOT_NET = isIkvmDotNet0();

    private static final int ADDRESS_SIZE = addressSize0();
    private static final boolean USE_DIRECT_BUFFER_NO_CLEANER;
    private static final AtomicLong DIRECT_MEMORY_COUNTER;
    private static final long DIRECT_MEMORY_LIMIT;
    private static final Cleaner CLEANER;
    private static final int UNINITIALIZED_ARRAY_ALLOCATION_THRESHOLD;
    private static final String LINUX_ID_PREFIX = "ID=";
    private static final String LINUX_ID_LIKE_PREFIX = "ID_LIKE=";
    public static final boolean BIG_ENDIAN_NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    private static final Cleaner NOOP = buffer -> {
        // NOOP
    };

    static {
        // Here is how the system property is used:
        //
        // * <  0  - Don't use cleaner, and inherit max direct memory from java. In this case the
        //           "practical max direct memory" would be 2 * max memory as defined by the JDK.
        // * == 0  - Use cleaner, Netty will not enforce max memory, and instead will defer to JDK.
        // * >  0  - Don't use cleaner. This will limit Netty's total direct memory
        //           (note: that JDK's direct memory limit is independent of this).
        long maxDirectMemory = SystemPropertyUtil.getLong("io.netty5.maxDirectMemory", -1);

        if (maxDirectMemory == 0 || !hasUnsafe() || !PlatformDependent0.hasDirectBufferNoCleanerConstructor()) {
            USE_DIRECT_BUFFER_NO_CLEANER = false;
            DIRECT_MEMORY_COUNTER = null;
        } else {
            USE_DIRECT_BUFFER_NO_CLEANER = true;
            if (maxDirectMemory < 0) {
                maxDirectMemory = MAX_DIRECT_MEMORY;
                if (maxDirectMemory <= 0) {
                    DIRECT_MEMORY_COUNTER = null;
                } else {
                    DIRECT_MEMORY_COUNTER = new AtomicLong();
                }
            } else {
                DIRECT_MEMORY_COUNTER = new AtomicLong();
            }
        }
        logger.debug("-Dio.netty5.maxDirectMemory: {} bytes", maxDirectMemory);
        DIRECT_MEMORY_LIMIT = maxDirectMemory >= 1 ? maxDirectMemory : MAX_DIRECT_MEMORY;

        int tryAllocateUninitializedArray =
                SystemPropertyUtil.getInt("io.netty5.uninitializedArrayAllocationThreshold", 1024);
        UNINITIALIZED_ARRAY_ALLOCATION_THRESHOLD = PlatformDependent0.hasAllocateArrayMethod() ?
                tryAllocateUninitializedArray : -1;
        logger.debug("-Dio.netty5.uninitializedArrayAllocationThreshold: {}", UNINITIALIZED_ARRAY_ALLOCATION_THRESHOLD);

        MAYBE_SUPER_USER = maybeSuperUser0();

        if (!isAndroid()) {
            // only direct to method if we are not running on android.
            // See https://github.com/netty/netty/issues/2604
            CLEANER = CleanerJava9.isSupported() ? new CleanerJava9() : NOOP;
        } else {
            CLEANER = NOOP;
        }

        EXPLICIT_NO_PREFER_DIRECT = SystemPropertyUtil.getBoolean("io.nett5y.noPreferDirect", false);
        // We should always prefer direct buffers by default if we can use a Cleaner to release direct buffers.
        DIRECT_BUFFER_PREFERRED = CLEANER != NOOP
                                  && !EXPLICIT_NO_PREFER_DIRECT;
        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty5.noPreferDirect: {}", EXPLICIT_NO_PREFER_DIRECT);
        }

        /*
         * We do not want to log this message if unsafe is explicitly disabled. Do not remove the explicit no unsafe
         * guard.
         */
        if (CLEANER == NOOP && !PlatformDependent0.isExplicitNoUnsafe()) {
            logger.info(
                    "Your platform does not provide complete low-level API for accessing direct buffers reliably. " +
                    "Unless explicitly requested, heap buffer will always be preferred to avoid potential system " +
                    "instability.");
        }

        final Set<String> availableClassifiers = new LinkedHashSet<>();

        if (!addPropertyOsClassifiers(availableClassifiers)) {
            addFilesystemOsClassifiers(availableClassifiers);
        }
        LINUX_OS_CLASSIFIERS = Collections.unmodifiableSet(availableClassifiers);
    }

    // For specifications, see https://www.freedesktop.org/software/systemd/man/os-release.html
    static void addFilesystemOsClassifiers(final Set<String> availableClassifiers) {
        if (processOsReleaseFile("/etc/os-release", availableClassifiers)) {
            return;
        }
        processOsReleaseFile("/usr/lib/os-release", availableClassifiers);
    }

    private static boolean processOsReleaseFile(String osReleaseFileName, Set<String> availableClassifiers) {
        Path file = Paths.get(osReleaseFileName);
        if (Files.exists(file)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new BoundedInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(LINUX_ID_PREFIX)) {
                        String id = normalizeOsReleaseVariableValue(
                                line.substring(LINUX_ID_PREFIX.length()));
                        addClassifier(availableClassifiers, id);
                    } else if (line.startsWith(LINUX_ID_LIKE_PREFIX)) {
                        line = normalizeOsReleaseVariableValue(
                                line.substring(LINUX_ID_LIKE_PREFIX.length()));
                        addClassifier(availableClassifiers, line.split(" "));
                    }
                }
            } catch (SecurityException e) {
                logger.debug("Unable to read {}", osReleaseFileName, e);
            } catch (IOException e) {
                logger.debug("Error while reading content of {}", osReleaseFileName, e);
            }
            // specification states we should only fall back if /etc/os-release does not exist
            return true;
        }
        return false;
    }

    /**
     * Package private for testing purposes only!
     */
    @VisibleForTesting
    static boolean addPropertyOsClassifiers(Set<String> availableClassifiers) {
        // empty: -Dio.netty5.osClassifiers (no distro specific classifiers for native libs)
        // single ID: -Dio.netty5.osClassifiers=ubuntu
        // pair ID, ID_LIKE: -Dio.netty5.osClassifiers=ubuntu,debian
        // illegal otherwise
        String osClassifiersPropertyName = "io.netty5.osClassifiers";
        String osClassifiers = SystemPropertyUtil.get(osClassifiersPropertyName);
        if (osClassifiers == null) {
            return false;
        }
        if (osClassifiers.isEmpty()) {
            // let users omit classifiers with just -Dio.netty5.osClassifiers
            return true;
        }
        String[] classifiers = osClassifiers.split(",");
        if (classifiers.length == 0) {
            throw new IllegalArgumentException(
                    osClassifiersPropertyName + " property is not empty, but contains no classifiers: "
                            + osClassifiers);
        }
        // at most ID, ID_LIKE classifiers
        if (classifiers.length > 2) {
            throw new IllegalArgumentException(
                    osClassifiersPropertyName + " property contains more than 2 classifiers: " + osClassifiers);
        }
        for (String classifier : classifiers) {
            addClassifier(availableClassifiers, classifier);
        }
        return true;
    }

    public static long byteArrayBaseOffset() {
        return BYTE_ARRAY_BASE_OFFSET;
    }

    public static boolean hasDirectBufferNoCleanerConstructor() {
        return PlatformDependent0.hasDirectBufferNoCleanerConstructor();
    }

    public static byte[] allocateUninitializedArray(int size) {
        return UNINITIALIZED_ARRAY_ALLOCATION_THRESHOLD < 0 || UNINITIALIZED_ARRAY_ALLOCATION_THRESHOLD > size ?
                new byte[size] : PlatformDependent0.allocateUninitializedArray(size);
    }

    /**
     * Returns {@code true} if and only if the current platform is Android
     */
    public static boolean isAndroid() {
        return PlatformDependent0.isAndroid();
    }

    /**
     * Return {@code true} if the JVM is running on Windows
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Return {@code true} if the JVM is running on OSX / MacOS
     */
    public static boolean isOsx() {
        return IS_OSX;
    }

    /**
     * Return {@code true} if the current user may be a super-user. Be aware that this is just an hint and so it may
     * return false-positives.
     */
    public static boolean maybeSuperUser() {
        return MAYBE_SUPER_USER;
    }

    /**
     * Return the version of Java under which this library is used.
     */
    public static int javaVersion() {
        return PlatformDependent0.javaVersion();
    }

    /**
     * Returns {@code true} if and only if it is fine to enable TCP_NODELAY socket option by default.
     */
    public static boolean canEnableTcpNoDelayByDefault() {
        return CAN_ENABLE_TCP_NODELAY_BY_DEFAULT;
    }

    /**
     * Return {@code true} if {@code sun.misc.Unsafe} was found on the classpath and can be used for accelerated
     * direct memory access.
     */
    public static boolean hasUnsafe() {
        return UNSAFE_UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Return the reason (if any) why {@code sun.misc.Unsafe} was not available.
     */
    public static Throwable getUnsafeUnavailabilityCause() {
        return UNSAFE_UNAVAILABILITY_CAUSE;
    }

    /**
     * {@code true} if and only if the platform supports unaligned access.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Segmentation_fault#Bus_error">Wikipedia on segfault</a>
     */
    public static boolean isUnaligned() {
        return PlatformDependent0.isUnaligned();
    }

    /**
     * Returns {@code true} if the platform has reliable low-level direct buffer access API and a user has not specified
     * {@code -Dio.netty5.noPreferDirect} option.
     */
    public static boolean directBufferPreferred() {
        return DIRECT_BUFFER_PREFERRED;
    }

    /**
     * Returns {@code true} if user has specified
     * {@code -Dio.netty.noPreferDirect=true} option.
     */
    public static boolean isExplicitNoPreferDirect() {
        return EXPLICIT_NO_PREFER_DIRECT;
    }

    /**
     * Returns the maximum memory reserved for direct buffer allocation.
     */
    public static long maxDirectMemory() {
        return DIRECT_MEMORY_LIMIT;
    }

    /**
     * Returns the current memory reserved for direct buffer allocation.
     * This method returns -1 in case that a value is not available.
     *
     * @see #maxDirectMemory()
     */
    public static long usedDirectMemory() {
        return DIRECT_MEMORY_COUNTER != null ? DIRECT_MEMORY_COUNTER.get() : -1;
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
     * Return the address size of the OS.
     * 4 (for 32 bits systems ) and 8 (for 64 bits systems).
     */
    public static int addressSize() {
        return ADDRESS_SIZE;
    }

    public static long allocateMemory(long size) {
        return PlatformDependent0.allocateMemory(size);
    }

    public static void freeMemory(long address) {
        PlatformDependent0.freeMemory(address);
    }

    public static long reallocateMemory(long address, long newSize) {
        return PlatformDependent0.reallocateMemory(address, newSize);
    }

    /**
     * Raises an exception bypassing compiler checks for checked exceptions.
     */
    public static void throwException(Throwable t) {
        throwException0(t);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwException0(Throwable t) throws E {
        throw (E) t;
    }

    /**
     * Try to deallocate the specified direct {@link ByteBuffer}. Please note this method does nothing if
     * the current platform does not support this operation or the specified buffer is not a direct buffer.
     */
    public static void freeDirectBuffer(ByteBuffer buffer) {
        CLEANER.freeDirectBuffer(buffer);
    }

    public static long directBufferAddress(ByteBuffer buffer) {
        return PlatformDependent0.directBufferAddress(buffer);
    }

    public static ByteBuffer directBuffer(long memoryAddress, int size, Object attachment) {
        if (PlatformDependent0.hasDirectBufferNoCleanerConstructor()) {
            return PlatformDependent0.newDirectBuffer(memoryAddress, size, attachment);
        }
        throw new UnsupportedOperationException(
                "sun.misc.Unsafe or java.nio.DirectByteBuffer.<init>(long, int) not available");
    }

    public static Object getObject(Object object, long fieldOffset) {
        return PlatformDependent0.getObject(object, fieldOffset);
    }

    public static byte getByte(Object object, long fieldOffset) {
        return PlatformDependent0.getByte(object, fieldOffset);
    }

    public static short getShort(Object object, long fieldOffset) {
        return PlatformDependent0.getShort(object, fieldOffset);
    }

    public static char getChar(Object object, long fieldOffset) {
        return PlatformDependent0.getChar(object, fieldOffset);
    }

    public static int getInt(Object object, long fieldOffset) {
        return PlatformDependent0.getInt(object, fieldOffset);
    }

    public static float getFloat(Object object, long fieldOffset) {
        return PlatformDependent0.getFloat(object, fieldOffset);
    }

    public static long getLong(Object object, long fieldOffset) {
        return PlatformDependent0.getLong(object, fieldOffset);
    }

    public static double getDouble(Object object, long fieldOffset) {
        return PlatformDependent0.getDouble(object, fieldOffset);
    }

    public static int getIntVolatile(long address) {
        return PlatformDependent0.getIntVolatile(address);
    }

    public static void putIntOrdered(long adddress, int newValue) {
        PlatformDependent0.putIntOrdered(adddress, newValue);
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

    public static byte getByte(byte[] data, int index) {
        return PlatformDependent0.getByte(data, index);
    }

    public static byte getByte(byte[] data, long index) {
        return PlatformDependent0.getByte(data, index);
    }

    public static short getShort(byte[] data, int index) {
        return PlatformDependent0.getShort(data, index);
    }

    public static int getInt(byte[] data, int index) {
        return PlatformDependent0.getInt(data, index);
    }

    public static int getInt(int[] data, long index) {
        return PlatformDependent0.getInt(data, index);
    }

    public static long getLong(byte[] data, int index) {
        return PlatformDependent0.getLong(data, index);
    }

    public static long getLong(long[] data, long index) {
        return PlatformDependent0.getLong(data, index);
    }

    private static long getLongSafe(byte[] bytes, int offset) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            return (long) bytes[offset] << 56 |
                    ((long) bytes[offset + 1] & 0xff) << 48 |
                    ((long) bytes[offset + 2] & 0xff) << 40 |
                    ((long) bytes[offset + 3] & 0xff) << 32 |
                    ((long) bytes[offset + 4] & 0xff) << 24 |
                    ((long) bytes[offset + 5] & 0xff) << 16 |
                    ((long) bytes[offset + 6] & 0xff) <<  8 |
                    (long) bytes[offset + 7] & 0xff;
        }
        return (long) bytes[offset] & 0xff |
                ((long) bytes[offset + 1] & 0xff) << 8 |
                ((long) bytes[offset + 2] & 0xff) << 16 |
                ((long) bytes[offset + 3] & 0xff) << 24 |
                ((long) bytes[offset + 4] & 0xff) << 32 |
                ((long) bytes[offset + 5] & 0xff) << 40 |
                ((long) bytes[offset + 6] & 0xff) << 48 |
                (long) bytes[offset + 7] << 56;
    }

    private static int getIntSafe(byte[] bytes, int offset) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            return bytes[offset] << 24 |
                    (bytes[offset + 1] & 0xff) << 16 |
                    (bytes[offset + 2] & 0xff) << 8 |
                    bytes[offset + 3] & 0xff;
        }
        return bytes[offset] & 0xff |
                (bytes[offset + 1] & 0xff) << 8 |
                (bytes[offset + 2] & 0xff) << 16 |
                bytes[offset + 3] << 24;
    }

    private static short getShortSafe(byte[] bytes, int offset) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            return (short) (bytes[offset] << 8 | (bytes[offset + 1] & 0xff));
        }
        return (short) (bytes[offset] & 0xff | (bytes[offset + 1] << 8));
    }

    /**
     * Identical to {@link PlatformDependent0#hashCodeAsciiCompute(long, int)} but for {@link CharSequence}.
     */
    private static int hashCodeAsciiCompute(CharSequence value, int offset, int hash) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            return hash * HASH_CODE_C1 +
                    // Low order int
                    hashCodeAsciiSanitizeInt(value, offset + 4) * HASH_CODE_C2 +
                    // High order int
                    hashCodeAsciiSanitizeInt(value, offset);
        }
        return hash * HASH_CODE_C1 +
                // Low order int
                hashCodeAsciiSanitizeInt(value, offset) * HASH_CODE_C2 +
                // High order int
                hashCodeAsciiSanitizeInt(value, offset + 4);
    }

    /**
     * Identical to {@link PlatformDependent0#hashCodeAsciiSanitize(int)} but for {@link CharSequence}.
     */
    private static int hashCodeAsciiSanitizeInt(CharSequence value, int offset) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            // mimic a unsafe.getInt call on a big endian machine
            return (value.charAt(offset + 3) & 0x1f) |
                   (value.charAt(offset + 2) & 0x1f) << 8 |
                   (value.charAt(offset + 1) & 0x1f) << 16 |
                   (value.charAt(offset) & 0x1f) << 24;
        }
        return (value.charAt(offset + 3) & 0x1f) << 24 |
               (value.charAt(offset + 2) & 0x1f) << 16 |
               (value.charAt(offset + 1) & 0x1f) << 8 |
               (value.charAt(offset) & 0x1f);
    }

    /**
     * Identical to {@link PlatformDependent0#hashCodeAsciiSanitize(short)} but for {@link CharSequence}.
     */
    private static int hashCodeAsciiSanitizeShort(CharSequence value, int offset) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            // mimic a unsafe.getShort call on a big endian machine
            return (value.charAt(offset + 1) & 0x1f) |
                    (value.charAt(offset) & 0x1f) << 8;
        }
        return (value.charAt(offset + 1) & 0x1f) << 8 |
                (value.charAt(offset) & 0x1f);
    }

    /**
     * Identical to {@link PlatformDependent0#hashCodeAsciiSanitize(byte)} but for {@link CharSequence}.
     */
    private static int hashCodeAsciiSanitizeByte(char value) {
        return value & 0x1f;
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

    public static void putByte(byte[] data, int index, byte value) {
        PlatformDependent0.putByte(data, index, value);
    }

    public static void putByte(Object data, long offset, byte value) {
        PlatformDependent0.putByte(data, offset, value);
    }

    public static void putShort(Object data, long offset, short value) {
        PlatformDependent0.putShort(data, offset, value);
    }

    public static void putChar(Object data, long offset, char value) {
        PlatformDependent0.putChar(data, offset, value);
    }

    public static void putInt(Object data, long offset, int value) {
        PlatformDependent0.putInt(data, offset, value);
    }

    public static void putFloat(Object data, long offset, float value) {
        PlatformDependent0.putFloat(data, offset, value);
    }

    public static void putLong(Object data, long offset, long value) {
        PlatformDependent0.putLong(data, offset, value);
    }

    public static void putDouble(Object data, long offset, double value) {
        PlatformDependent0.putDouble(data, offset, value);
    }

    public static void putShort(byte[] data, int index, short value) {
        PlatformDependent0.putShort(data, index, value);
    }

    public static void putInt(byte[] data, int index, int value) {
        PlatformDependent0.putInt(data, index, value);
    }

    public static void putLong(byte[] data, int index, long value) {
        PlatformDependent0.putLong(data, index, value);
    }

    public static void putObject(Object o, long offset, Object x) {
        PlatformDependent0.putObject(o, offset, x);
    }

    public static long objectFieldOffset(Field field) {
        return PlatformDependent0.objectFieldOffset(field);
    }

    public static void copyMemory(long srcAddr, long dstAddr, long length) {
        PlatformDependent0.copyMemory(srcAddr, dstAddr, length);
    }

    public static void copyMemory(byte[] src, int srcIndex, long dstAddr, long length) {
        PlatformDependent0.copyMemory(src, BYTE_ARRAY_BASE_OFFSET + srcIndex, null, dstAddr, length);
    }

    public static void copyMemory(byte[] src, int srcIndex, byte[] dst, int dstIndex, long length) {
        PlatformDependent0.copyMemory(src, BYTE_ARRAY_BASE_OFFSET + srcIndex,
                                      dst, BYTE_ARRAY_BASE_OFFSET + dstIndex, length);
    }

    public static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        PlatformDependent0.copyMemory(src, srcOffset, dst, dstOffset, length);
    }

    public static void copyMemory(long srcAddr, byte[] dst, int dstIndex, long length) {
        PlatformDependent0.copyMemory(null, srcAddr, dst, BYTE_ARRAY_BASE_OFFSET + dstIndex, length);
    }

    public static void setMemory(byte[] dst, int dstIndex, long bytes, byte value) {
        PlatformDependent0.setMemory(dst, BYTE_ARRAY_BASE_OFFSET + dstIndex, bytes, value);
    }

    public static void setMemory(Object base, long offset, long length, byte value) {
        PlatformDependent0.setMemory(base, offset, length, value);
    }

    public static void setMemory(long address, long bytes, byte value) {
        PlatformDependent0.setMemory(address, bytes, value);
    }

    /**
     * Allocate a new {@link ByteBuffer} with the given {@code capacity}. {@link ByteBuffer}s allocated with
     * this method <strong>MUST</strong> be deallocated via {@link #freeDirectNoCleaner(ByteBuffer)}.
     */
    public static ByteBuffer allocateDirectNoCleaner(int capacity) {
        assert USE_DIRECT_BUFFER_NO_CLEANER;

        incrementMemoryCounter(capacity);
        try {
            return PlatformDependent0.allocateDirectNoCleaner(capacity);
        } catch (Throwable e) {
            decrementMemoryCounter(capacity);
            throw e;
        }
    }

    /**
     * Reallocate a new {@link ByteBuffer} with the given {@code capacity}. {@link ByteBuffer}s reallocated with
     * this method <strong>MUST</strong> be deallocated via {@link #freeDirectNoCleaner(ByteBuffer)}.
     */
    public static ByteBuffer reallocateDirectNoCleaner(ByteBuffer buffer, int capacity) {
        assert USE_DIRECT_BUFFER_NO_CLEANER;

        int len = capacity - buffer.capacity();
        incrementMemoryCounter(len);
        try {
            return PlatformDependent0.reallocateDirectNoCleaner(buffer, capacity);
        } catch (Throwable e) {
            decrementMemoryCounter(len);
            throw e;
        }
    }

    /**
     * This method <strong>MUST</strong> only be called for {@link ByteBuffer}s that were allocated via
     * {@link #allocateDirectNoCleaner(int)}.
     */
    public static void freeDirectNoCleaner(ByteBuffer buffer) {
        assert USE_DIRECT_BUFFER_NO_CLEANER;

        int capacity = buffer.capacity();
        PlatformDependent0.freeMemory(PlatformDependent0.directBufferAddress(buffer));
        decrementMemoryCounter(capacity);
    }

    public static ByteBuffer alignDirectBuffer(ByteBuffer buffer, int alignment) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Cannot get aligned slice of non-direct byte buffer.");
        }
        return buffer.alignedSlice(alignment);
    }

    public static long align(long value, int alignment) {
        return Pow2.align(value, alignment);
    }

    public static int roundToPowerOfTwo(final int value) {
        return Pow2.roundToPowerOfTwo(value);
    }

    private static void incrementMemoryCounter(int capacity) {
        if (DIRECT_MEMORY_COUNTER != null) {
            long newUsedMemory = DIRECT_MEMORY_COUNTER.addAndGet(capacity);
            if (newUsedMemory > DIRECT_MEMORY_LIMIT) {
                DIRECT_MEMORY_COUNTER.addAndGet(-capacity);
                throw new OutOfDirectMemoryError("failed to allocate " + capacity
                        + " byte(s) of direct memory (used: " + (newUsedMemory - capacity)
                        + ", max: " + DIRECT_MEMORY_LIMIT + ')');
            }
        }
    }

    private static void decrementMemoryCounter(int capacity) {
        if (DIRECT_MEMORY_COUNTER != null) {
            long usedMemory = DIRECT_MEMORY_COUNTER.addAndGet(-capacity);
            assert usedMemory >= 0;
        }
    }

    public static boolean useDirectBufferNoCleaner() {
        return USE_DIRECT_BUFFER_NO_CLEANER;
    }

    /**
     * Compare two {@code byte} arrays for equality. For performance reasons no bounds checking on the
     * parameters is performed.
     *
     * @param bytes1 the first byte array.
     * @param startPos1 the position (inclusive) to start comparing in {@code bytes1}.
     * @param bytes2 the second byte array.
     * @param startPos2 the position (inclusive) to start comparing in {@code bytes2}.
     * @param length the amount of bytes to compare. This is assumed to be validated as not going out of bounds
     * by the caller.
     */
    public static boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        return !hasUnsafe() || !unalignedAccess() ?
                  equalsSafe(bytes1, startPos1, bytes2, startPos2, length) :
                  PlatformDependent0.equals(bytes1, startPos1, bytes2, startPos2, length);
    }

    /**
     * Determine if a subsection of an array is zero.
     * @param bytes The byte array.
     * @param startPos The starting index (inclusive) in {@code bytes}.
     * @param length The amount of bytes to check for zero.
     * @return {@code false} if {@code bytes[startPos:startsPos+length)} contains a value other than zero.
     */
    public static boolean isZero(byte[] bytes, int startPos, int length) {
        return !hasUnsafe() || !unalignedAccess() ?
                isZeroSafe(bytes, startPos, length) :
                PlatformDependent0.isZero(bytes, startPos, length);
    }

    /**
     * Compare two {@code byte} arrays for equality without leaking timing information.
     * For performance reasons no bounds checking on the parameters is performed.
     * <p>
     * The {@code int} return type is intentional and is designed to allow cascading of constant time operations:
     * <pre>
     *     byte[] s1 = new {1, 2, 3};
     *     byte[] s2 = new {1, 2, 3};
     *     byte[] s3 = new {1, 2, 3};
     *     byte[] s4 = new {4, 5, 6};
     *     boolean equals = (equalsConstantTime(s1, 0, s2, 0, s1.length) &
     *                       equalsConstantTime(s3, 0, s4, 0, s3.length)) != 0;
     * </pre>
     * @param bytes1 the first byte array.
     * @param startPos1 the position (inclusive) to start comparing in {@code bytes1}.
     * @param bytes2 the second byte array.
     * @param startPos2 the position (inclusive) to start comparing in {@code bytes2}.
     * @param length the amount of bytes to compare. This is assumed to be validated as not going out of bounds
     * by the caller.
     * @return {@code 0} if not equal. {@code 1} if equal.
     */
    public static int equalsConstantTime(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        return !hasUnsafe() || !unalignedAccess() ?
                  ConstantTimeUtils.equalsConstantTime(bytes1, startPos1, bytes2, startPos2, length) :
                  PlatformDependent0.equalsConstantTime(bytes1, startPos1, bytes2, startPos2, length);
    }

    /**
     * Calculate a hash code of a byte array assuming ASCII character encoding.
     * The resulting hash code will be case insensitive.
     * @param bytes The array which contains the data to hash.
     * @param startPos What index to start generating a hash code in {@code bytes}
     * @param length The amount of bytes that should be accounted for in the computation.
     * @return The hash code of {@code bytes} assuming ASCII character encoding.
     * The resulting hash code will be case insensitive.
     */
    public static int hashCodeAscii(byte[] bytes, int startPos, int length) {
        return !hasUnsafe() || !unalignedAccess() ?
                hashCodeAsciiSafe(bytes, startPos, length) :
                PlatformDependent0.hashCodeAscii(bytes, startPos, length);
    }

    /**
     * Calculate a hash code of a byte array assuming ASCII character encoding.
     * The resulting hash code will be case insensitive.
     * <p>
     * This method assumes that {@code bytes} is equivalent to a {@code byte[]} but just using {@link CharSequence}
     * for storage. The upper most byte of each {@code char} from {@code bytes} is ignored.
     * @param bytes The array which contains the data to hash (assumed to be equivalent to a {@code byte[]}).
     * @return The hash code of {@code bytes} assuming ASCII character encoding.
     * The resulting hash code will be case insensitive.
     */
    public static int hashCodeAscii(CharSequence bytes) {
        final int length = bytes.length();
        final int remainingBytes = length & 7;
        int hash = HASH_CODE_ASCII_SEED;
        // Benchmarking shows that by just naively looping for inputs 8~31 bytes long we incur a relatively large
        // performance penalty (only achieve about 60% performance of loop which iterates over each char). So because
        // of this we take special provisions to unroll the looping for these conditions.
        if (length >= 32) {
            for (int i = length - 8; i >= remainingBytes; i -= 8) {
                hash = hashCodeAsciiCompute(bytes, i, hash);
            }
        } else if (length >= 8) {
            hash = hashCodeAsciiCompute(bytes, length - 8, hash);
            if (length >= 16) {
                hash = hashCodeAsciiCompute(bytes, length - 16, hash);
                if (length >= 24) {
                    hash = hashCodeAsciiCompute(bytes, length - 24, hash);
                }
            }
        }
        if (remainingBytes == 0) {
            return hash;
        }
        int offset = 0;
        if (remainingBytes != 2 & remainingBytes != 4 & remainingBytes != 6) { // 1, 3, 5, 7
            hash = hash * HASH_CODE_C1 + hashCodeAsciiSanitizeByte(bytes.charAt(0));
            offset = 1;
        }
        if (remainingBytes != 1 & remainingBytes != 4 & remainingBytes != 5) { // 2, 3, 6, 7
            hash = hash * (offset == 0 ? HASH_CODE_C1 : HASH_CODE_C2)
                    + hashCodeAsciiSanitize(hashCodeAsciiSanitizeShort(bytes, offset));
            offset += 2;
        }
        if (remainingBytes >= 4) { // 4, 5, 6, 7
            return hash * ((offset == 0 | offset == 3) ? HASH_CODE_C1 : HASH_CODE_C2)
                    + hashCodeAsciiSanitizeInt(bytes, offset);
        }
        return hash;
    }

    private static final class QueueChoice {
        private static final boolean USE_CHUNKED_ARRAY_QUEUES;

        static {
            Object unsafe = null;
            if (hasUnsafe()) {
                // jctools goes through its own process of initializing unsafe
                unsafe = org.jctools.util.UnsafeAccess.UNSAFE;
            }

            if (unsafe == null) {
                logger.debug("org.jctools-core.MpscChunkedArrayQueue: unavailable");
                USE_CHUNKED_ARRAY_QUEUES = false;
            } else {
                logger.debug("org.jctools-core.MpscChunkedArrayQueue: available");
                USE_CHUNKED_ARRAY_QUEUES = true;
            }
        }

        static <T> Queue<T> newMpscQueue(final int maxCapacity) {
            // Calculate the max capacity which can not be bigger than MAX_ALLOWED_MPSC_CAPACITY.
            // This is forced by the MpscChunkedArrayQueue implementation as will try to round it
            // up to the next power of two and so will overflow otherwise.
            final int capacity = max(min(maxCapacity, MAX_ALLOWED_MPSC_CAPACITY), MIN_MAX_MPSC_CAPACITY);
            return newChunkedMpscQueue(MPSC_CHUNK_SIZE, capacity);
        }

        static <T> Queue<T> newChunkedMpscQueue(final int chunkSize, final int capacity) {
            return USE_CHUNKED_ARRAY_QUEUES ?
                    new MpscChunkedArrayQueue<>(chunkSize, capacity) :
                    new MpscChunkedAtomicArrayQueue<>(chunkSize, capacity);
        }

        static <T> Queue<T> newMpmcQueue(final int capacity) {
            return hasUnsafe() ? new MpmcArrayQueue<>(capacity) : new MpmcAtomicArrayQueue<>(capacity);
        }

        static <T> Queue<T> newMpscQueue() {
            return USE_CHUNKED_ARRAY_QUEUES ?
                    new MpscUnboundedArrayQueue<>(MPSC_CHUNK_SIZE) :
                    new MpscUnboundedAtomicArrayQueue<>(MPSC_CHUNK_SIZE);
        }

        static <T> Queue<T> newSpscQueue() {
            return hasUnsafe() ? new SpscLinkedUnpaddedQueue<>() : new SpscLinkedAtomicQueue<>();
        }

        static <T> Queue<T> newFixedMpscQueue(int capacity) {
            return hasUnsafe() ? new MpscArrayQueue<>(capacity) : new MpscAtomicArrayQueue<>(capacity);
        }
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     * @return A MPSC queue which may be unbounded.
     */
    public static <T> Queue<T> newMpscQueue() {
        return QueueChoice.newMpscQueue();
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     */
    public static <T> Queue<T> newMpscQueue(final int maxCapacity) {
        return QueueChoice.newMpscQueue(maxCapacity);
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     * The queue will grow and shrink its capacity in units of the given chunk size.
     */
    public static <T> Queue<T> newMpscQueue(final int chunkSize, final int maxCapacity) {
        return QueueChoice.newChunkedMpscQueue(chunkSize, maxCapacity);
    }

    /**
     * Create a multi-producer, multi-consumer queue with the given capacity.
     */
    public static <T> Queue<T> newMpmcQueue(final int capacity) {
        return QueueChoice.newMpmcQueue(capacity);
    }

    /**
     * Create a new {@link Queue} which is safe to use for single producer (one thread!) and a single
     * consumer (one thread!).
     */
    public static <T> Queue<T> newSpscQueue() {
        return QueueChoice.newSpscQueue();
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!) with the given fixes {@code capacity}.
     */
    public static <T> Queue<T> newFixedMpscQueue(int capacity) {
        return QueueChoice.newFixedMpscQueue(capacity);
    }

    /**
     * Returns a new concurrent {@link Deque}.
     */
    public static <C> Deque<C> newConcurrentDeque() {
        return new ConcurrentLinkedDeque<>();
    }

    public static Object unwrapUnsafeOrNull() {
        if (!hasUnsafe()) {
            return null;
        }
        Class<?> callerClass = StackWalker.getInstance(Set.of(Option.RETAIN_CLASS_REFERENCE), 1).getCallerClass();
        if (!callerClass.isAnnotationPresent(io.netty5.util.internal.UnsafeAccess.class)) {
            return null;
        }
        return PlatformDependent0.UNSAFE;
    }

    private static boolean isWindows0() {
        boolean windows = "windows".equals(NORMALIZED_OS);
        if (windows) {
            logger.debug("Platform: Windows");
        }
        return windows;
    }

    private static boolean isOsx0() {
        boolean osx = "osx".equals(NORMALIZED_OS);
        if (osx) {
            logger.debug("Platform: MacOS");
        }
        return osx;
    }

    private static boolean maybeSuperUser0() {
        String username = SystemPropertyUtil.get("user.name");
        if (isWindows()) {
            return "Administrator".equals(username);
        }
        // Check for root and toor as some BSDs have a toor user that is basically the same as root.
        return "root".equals(username) || "toor".equals(username);
    }

    private static Throwable unsafeUnavailabilityCause0() {
        if (isAndroid()) {
            logger.debug("sun.misc.Unsafe: unavailable (Android)");
            return new UnsupportedOperationException("sun.misc.Unsafe: unavailable (Android)");
        }

        if (isIkvmDotNet()) {
            logger.debug("sun.misc.Unsafe: unavailable (IKVM.NET)");
            return new UnsupportedOperationException("sun.misc.Unsafe: unavailable (IKVM.NET)");
        }

        Throwable cause = PlatformDependent0.getUnsafeUnavailabilityCause();
        if (cause != null) {
            return cause;
        }

        try {
            boolean hasUnsafe = PlatformDependent0.hasUnsafe();
            logger.debug("sun.misc.Unsafe: {}", hasUnsafe ? "available" : "unavailable");
            return null;
        } catch (Throwable t) {
            logger.trace("Could not determine if Unsafe is available", t);
            // Probably failed to initialize PlatformDependent0.
            return new UnsupportedOperationException("Could not determine if Unsafe is available", t);
        }
    }

    /**
     * Returns {@code true} if the running JVM is either <a href="https://developer.ibm.com/javasdk/">IBM J9</a> or
     * <a href="https://www.eclipse.org/openj9/">Eclipse OpenJ9</a>, {@code false} otherwise.
     */
    public static boolean isJ9Jvm() {
        return IS_J9_JVM;
    }

    private static boolean isJ9Jvm0() {
        String vmName = SystemPropertyUtil.get("java.vm.name", "").toLowerCase();
        return vmName.startsWith("ibm j9") || vmName.startsWith("eclipse openj9");
    }

    /**
     * Returns {@code true} if the running JVM is <a href="https://www.ikvm.net">IKVM.NET</a>, {@code false} otherwise.
     */
    public static boolean isIkvmDotNet() {
        return IS_IVKVM_DOT_NET;
    }

    private static boolean isIkvmDotNet0() {
        String vmName = SystemPropertyUtil.get("java.vm.name", "").toUpperCase(Locale.US);
        return vmName.equals("IKVM.NET");
    }

    private static Pattern getMaxDirectMemorySizeArgPattern() {
        // Pattern's is immutable so it's always safe published
        Pattern pattern = MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN;
        if (pattern == null) {
            pattern = Pattern.compile("\\s*-XX:MaxDirectMemorySize\\s*=\\s*([0-9]+)\\s*([kKmMgG]?)\\s*$");
            MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN =  pattern;
        }
        return pattern;
    }

    /**
     * Compute an estimate of the maximum amount of direct memory available to this JVM.
     * <p>
     * The computation is not cached, so you probably want to use {@link #maxDirectMemory()} instead.
     * <p>
     * This will produce debug log output when called.
     *
     * @return The estimated max direct memory, in bytes.
     */
    public static long estimateMaxDirectMemory() {
        long maxDirectMemory = PlatformDependent0.bitsMaxDirectMemory();
        if (maxDirectMemory > 0) {
            return maxDirectMemory;
        }

        ClassLoader systemClassLoader = null;
        try {
            systemClassLoader = ClassLoader.getSystemClassLoader();

            // When using IBM J9 / Eclipse OpenJ9 we should not use VM.maxDirectMemory() as it not reflects the
            // correct value.
            // See:
            //  - https://github.com/netty/netty/issues/7654
            String vmName = SystemPropertyUtil.get("java.vm.name", "").toLowerCase();
            if (!vmName.startsWith("ibm j9") &&
                    // https://github.com/eclipse/openj9/blob/openj9-0.8.0/runtime/include/vendor_version.h#L53
                    !vmName.startsWith("eclipse openj9")) {
                // Try to get from sun.misc.VM.maxDirectMemory() which should be most accurate.
                Class<?> vmClass = Class.forName("sun.misc.VM", true, systemClassLoader);
                Method m = vmClass.getDeclaredMethod("maxDirectMemory");
                maxDirectMemory = ((Number) m.invoke(null)).longValue();
            }
        } catch (Throwable ignored) {
            // Ignore
        }

        if (maxDirectMemory > 0) {
            return maxDirectMemory;
        }

        try {
            // Now try to get the JVM option (-XX:MaxDirectMemorySize) and parse it.
            // Note that we are using reflection because Android doesn't have these classes.
            Class<?> mgmtFactoryClass = Class.forName(
                    "java.lang.management.ManagementFactory", true, systemClassLoader);
            Class<?> runtimeClass = Class.forName(
                    "java.lang.management.RuntimeMXBean", true, systemClassLoader);

            Object runtime = mgmtFactoryClass.getDeclaredMethod("getRuntimeMXBean").invoke(null);

            @SuppressWarnings("unchecked")
            List<String> vmArgs = (List<String>) runtimeClass.getDeclaredMethod("getInputArguments").invoke(runtime);

            Pattern maxDirectMemorySizeArgPattern = getMaxDirectMemorySizeArgPattern();

            for (int i = vmArgs.size() - 1; i >= 0; i --) {
                Matcher m = maxDirectMemorySizeArgPattern.matcher(vmArgs.get(i));
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
                    default:
                        break;
                }
                break;
            }
        } catch (Throwable ignored) {
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

    private static File tmpdir0() {
        File f;
        try {
            f = toDirectory(SystemPropertyUtil.get("io.netty5.tmpdir"));
            if (f != null) {
                logger.debug("-Dio.netty5.tmpdir: {}", f);
                return f;
            }

            f = toDirectory(SystemPropertyUtil.get("java.io.tmpdir"));
            if (f != null) {
                logger.debug("-Dio.netty5.tmpdir: {} (java.io.tmpdir)", f);
                return f;
            }

            // This shouldn't happen, but just in case ..
            if (isWindows()) {
                f = toDirectory(System.getenv("TEMP"));
                if (f != null) {
                    logger.debug("-Dio.netty5.tmpdir: {} (%TEMP%)", f);
                    return f;
                }

                String userprofile = System.getenv("USERPROFILE");
                if (userprofile != null) {
                    f = toDirectory(userprofile + "\\AppData\\Local\\Temp");
                    if (f != null) {
                        logger.debug("-Dio.netty5.tmpdir: {} (%USERPROFILE%\\AppData\\Local\\Temp)", f);
                        return f;
                    }

                    f = toDirectory(userprofile + "\\Local Settings\\Temp");
                    if (f != null) {
                        logger.debug("-Dio.netty5.tmpdir: {} (%USERPROFILE%\\Local Settings\\Temp)", f);
                        return f;
                    }
                }
            } else {
                f = toDirectory(System.getenv("TMPDIR"));
                if (f != null) {
                    logger.debug("-Dio.netty5.tmpdir: {} ($TMPDIR)", f);
                    return f;
                }
            }
        } catch (Throwable ignored) {
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
        f.mkdirs();

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
        int bitMode = SystemPropertyUtil.getInt("io.netty5.bitMode", 0);
        if (bitMode > 0) {
            logger.debug("-Dio.netty5.bitMode: {}", bitMode);
            return bitMode;
        }

        // And then the vendor specific ones which is probably most reliable.
        bitMode = SystemPropertyUtil.getInt("sun.arch.data.model", 0);
        if (bitMode > 0) {
            logger.debug("-Dio.netty5.bitMode: {} (sun.arch.data.model)", bitMode);
            return bitMode;
        }
        bitMode = SystemPropertyUtil.getInt("com.ibm.vm.bitmode", 0);
        if (bitMode > 0) {
            logger.debug("-Dio.netty5.bitMode: {} (com.ibm.vm.bitmode)", bitMode);
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
            logger.debug("-Dio.netty5.bitMode: {} (os.arch: {})", bitMode, arch);
        }

        // Last resort: guess from VM name and then fall back to most common 64-bit mode.
        String vm = SystemPropertyUtil.get("java.vm.name", "").toLowerCase(Locale.US);
        Pattern bitPattern = Pattern.compile("([1-9][0-9]+)-?bit");
        Matcher m = bitPattern.matcher(vm);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        } else {
            return 64;
        }
    }

    private static int addressSize0() {
        if (!hasUnsafe()) {
            return -1;
        }
        return PlatformDependent0.addressSize();
    }

    private static long byteArrayBaseOffset0() {
        if (!hasUnsafe()) {
            return -1;
        }
        return PlatformDependent0.byteArrayBaseOffset();
    }

    private static boolean equalsSafe(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        final int end = startPos1 + length;
        for (; startPos1 < end; ++startPos1, ++startPos2) {
            if (bytes1[startPos1] != bytes2[startPos2]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isZeroSafe(byte[] bytes, int startPos, int length) {
        final int end = startPos + length;
        for (; startPos < end; ++startPos) {
            if (bytes[startPos] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Package private for testing purposes only!
     */
    @VisibleForTesting
    static int hashCodeAsciiSafe(byte[] bytes, int startPos, int length) {
        int hash = HASH_CODE_ASCII_SEED;
        final int remainingBytes = length & 7;
        final int end = startPos + remainingBytes;
        for (int i = startPos - 8 + length; i >= end; i -= 8) {
            hash = PlatformDependent0.hashCodeAsciiCompute(getLongSafe(bytes, i), hash);
        }
        switch(remainingBytes) {
        case 7:
            return ((hash * HASH_CODE_C1 + hashCodeAsciiSanitize(bytes[startPos]))
                          * HASH_CODE_C2 + hashCodeAsciiSanitize(getShortSafe(bytes, startPos + 1)))
                          * HASH_CODE_C1 + hashCodeAsciiSanitize(getIntSafe(bytes, startPos + 3));
        case 6:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(getShortSafe(bytes, startPos)))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(getIntSafe(bytes, startPos + 2));
        case 5:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(bytes[startPos]))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(getIntSafe(bytes, startPos + 1));
        case 4:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(getIntSafe(bytes, startPos));
        case 3:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(bytes[startPos]))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(getShortSafe(bytes, startPos + 1));
        case 2:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(getShortSafe(bytes, startPos));
        case 1:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(bytes[startPos]);
        default:
            return hash;
        }
    }

    public static String normalizedArch() {
        return NORMALIZED_ARCH;
    }

    public static String normalizedOs() {
        return NORMALIZED_OS;
    }

    public static Set<String> normalizedLinuxClassifiers() {
        return LINUX_OS_CLASSIFIERS;
    }

    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        if (directory == null) {
            return Files.createTempFile(prefix, suffix).toFile();
        }
        return Files.createTempFile(directory.toPath(), prefix, suffix).toFile();
    }

    public static File createTempDirectory(String prefix, File directory) throws IOException {
        if (directory == null) {
            return Files.createTempDirectory(prefix).toFile();
        }
        return Files.createTempDirectory(directory.toPath(), prefix).toFile();
    }

    /**
     * Adds only those classifier strings to <tt>dest</tt> which are present in <tt>allowed</tt>.
     *
     * @param dest             destination set
     * @param maybeClassifiers potential classifiers to add
     */
    private static void addClassifier(Set<String> dest, String... maybeClassifiers) {
        for (String id : maybeClassifiers) {
            if (isAllowedClassifier(id)) {
                dest.add(id);
            }
        }
    }
    // keep in sync with maven's pom.xml via os.detection.classifierWithLikes!
    private static boolean isAllowedClassifier(String classifier) {
        switch (classifier) {
            case "fedora":
            case "suse":
            case "arch":
                return true;
            default:
                return false;
        }
    }

    //replaces value.trim().replaceAll("[\"']", "") to avoid regexp overhead
    private static String normalizeOsReleaseVariableValue(String value) {
        String trimmed = value.trim();
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '"' && c != '\'') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    //replaces value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "") to avoid regexp overhead
    private static String normalize(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normalizeArch(String value) {
        value = normalize(value);
        switch (value) {
            case "x8664":
            case "amd64":
            case "ia32e":
            case "em64t":
            case "x64":
                return "x86_64";

            case "x8632":
            case "x86":
            case "i386":
            case "i486":
            case "i586":
            case "i686":
            case "ia32":
            case "x32":
                return "x86_32";

            case "ia64":
            case "itanium64":
                return "itanium_64";

            case "sparc":
            case "sparc32":
                return "sparc_32";

            case "sparcv9":
            case "sparc64":
                return "sparc_64";

            case "arm":
            case "arm32":
                return "arm_32";

            case "aarch64":
                return "aarch_64";

            case "riscv64":
                return "riscv64";

            case "ppc":
            case "ppc32":
                return "ppc_32";

            case "ppc64":
                return "ppc_64";

            case "ppc64le":
                return "ppcle_64";

            case "s390":
                return "s390_32";

            case "s390x":
                return "s390_64";

            case "loongarch64":
                return "loongarch_64";

            default:
                return "unknown";
        }
    }

    private static String normalizeOs(String value) {
        value = normalize(value);
        if (value.startsWith("aix")) {
            return "aix";
        }
        if (value.startsWith("hpux")) {
            return "hpux";
        }
        if (value.startsWith("os400")) {
            // Avoid the names such as os4000
            if (value.length() <= 5 || !Character.isDigit(value.charAt(5))) {
                return "os400";
            }
        }
        if (value.startsWith("linux")) {
            return "linux";
        }
        if (value.startsWith("macosx") || value.startsWith("osx") || value.startsWith("darwin")) {
            return "osx";
        }
        if (value.startsWith("freebsd")) {
            return "freebsd";
        }
        if (value.startsWith("openbsd")) {
            return "openbsd";
        }
        if (value.startsWith("netbsd")) {
            return "netbsd";
        }
        if (value.startsWith("solaris") || value.startsWith("sunos")) {
            return "sunos";
        }
        if (value.startsWith("windows")) {
            return "windows";
        }

        return "unknown";
    }

    private PlatformDependent() {
        // only static method supported
    }
}
