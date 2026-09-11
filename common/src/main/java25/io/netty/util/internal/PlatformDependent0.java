/*
 * Copyright 2026 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import java.util.Arrays;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.SplittableRandom;

/**
 * Java 25+ multi-release JAR variant of {@link PlatformDependent0}.
 * <p>
 * This variant does not use {@code sun.misc.Unsafe}. Instead, it uses:
 * <ul>
 *   <li>{@link java.lang.foreign.MemorySegment} for native memory access ({@code getByte/putByte/copyMemory} etc.)</li>
 *   <li>{@link java.lang.foreign.Arena} for native memory allocation and lifecycle</li>
 *   <li>{@link java.lang.invoke.VarHandle} for efficient multibyte array reads and writes</li>
 * </ul>
 * <p>
 * Methods that require {@code Unsafe} object field offsets (e.g. {@code getObject(Object, long)})
 * throw {@link UnsupportedOperationException}. {@link #hasUnsafe()} always returns {@code false}.
 * <p>
 * Native memory allocations are tracked by address via {@code ALLOCATED_MEMORY} to support
 * {@link #freeMemory(long)} and {@link #reallocateMemory(long, long)} without Unsafe.
 */
@SuppressJava8Requirement(reason = "Java 25+ multi-release JAR variant")
final class PlatformDependent0 {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent0.class);

    private static final int JAVA_VERSION = javaVersion0();

    private static final boolean UNALIGNED;

    private static final VarHandle BYTE_ARRAY_LONG =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle BYTE_ARRAY_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle BYTE_ARRAY_SHORT =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    private static final boolean RUNNING_IN_NATIVE_IMAGE =
            SystemPropertyUtil.contains("org.graalvm.nativeimage.imagecode");

    private static final ConcurrentHashMap<Long, Allocation> ALLOCATED_MEMORY = new ConcurrentHashMap<>();

    private record Allocation(MemorySegment segment, Arena arena) { }

    // constants borrowed from murmur3
    static final int HASH_CODE_ASCII_SEED = 0xc2b2ae35;
    static final int HASH_CODE_C1 = 0xcc9e2d51;
    static final int HASH_CODE_C2 = 0x1b873593;

    static {
        logger.debug("PlatformDependent0: Java 25+ version");

        String unalignedProperty = SystemPropertyUtil.get("io.netty.unalignedAccess", "").trim();
        if ("true".equalsIgnoreCase(unalignedProperty)) {
            UNALIGNED = true;
            logger.debug("io.netty.unalignedAccess: {} (set by system property)", true);
        } else if ("false".equalsIgnoreCase(unalignedProperty)) {
            UNALIGNED = false;
            logger.debug("io.netty.unalignedAccess: {} (set by system property)", false);
        } else {
            String arch = SystemPropertyUtil.get("os.arch", "");
            UNALIGNED = arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64)$");
            logger.debug("io.netty.unalignedAccess: {} (arch: {})", UNALIGNED, arch);
        }

        logger.debug("sun.misc.Unsafe: unavailable (FFM MemorySegment API used instead)");
    }

    /**
     * @param thread The thread to be checked.
     * @return {@code true} if this {@link Thread} is a virtual thread, {@code false} otherwise.
     */
    static boolean isVirtualThread(Thread thread) {
        return thread != null && thread.isVirtual();
    }

    static boolean isNativeImage() {
        return RUNNING_IN_NATIVE_IMAGE;
    }

    static boolean isExplicitNoUnsafe() {
        return true;
    }

    static boolean isUnaligned() {
        return UNALIGNED;
    }

    /**
     * Any value >= 0 should be considered as a valid max direct memory value.
     */
    static long bitsMaxDirectMemory() {
        return -1;
    }

    static boolean hasUnsafe() {
        return false;
    }

    static Throwable getUnsafeUnavailabilityCause() {
        return new UnsupportedOperationException(
                "sun.misc.Unsafe: unavailable (PlatformDependent0.java25 variant)");
    }

    static boolean hasMemorySegmentAddressOfBuffer() {
        return true;
    }

    static boolean unalignedAccess() {
        return UNALIGNED;
    }

    static void splittableRandomNextBytes(SplittableRandom rng, byte[] data) {
        rng.nextBytes(data);
    }

    static void throwException(Throwable cause) {
        throwException0(cause);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwException0(Throwable t) throws E {
        throw (E) t;
    }

    static boolean hasDirectBufferNoCleanerConstructor() {
        return true;
    }

    static ByteBuffer reallocateDirectNoCleaner(ByteBuffer buffer, int capacity) {
        return newDirectBuffer(reallocateMemory(directBufferAddress(buffer), capacity), capacity);
    }

    static ByteBuffer allocateDirectNoCleaner(int capacity) {
        return newDirectBuffer(allocateMemory(Math.max(1, capacity)), capacity);
    }

    static boolean hasAlignSliceMethod() {
        return true;
    }

    static ByteBuffer alignSlice(ByteBuffer buffer, int alignment) {
        return buffer.alignedSlice(alignment);
    }

    static boolean hasOffsetSliceMethod() {
        return true;
    }

    static ByteBuffer offsetSlice(ByteBuffer buffer, int index, int length) {
        return buffer.slice(index, length);
    }

    static boolean hasAbsolutePutBufferMethod() {
        return true;
    }

    static boolean hasAbsolutePutArrayMethod() {
        return true;
    }

    static ByteBuffer absolutePut(ByteBuffer dst, int dstOffset, ByteBuffer src, int srcOffset, int length) {
        return dst.put(dstOffset, src, srcOffset, length);
    }

    static ByteBuffer absolutePut(ByteBuffer dst, int dstOffset, byte[] src, int srcOffset, int length) {
        return dst.put(dstOffset, src, srcOffset, length);
    }

    static boolean hasAllocateArrayMethod() {
        return false;
    }

    static byte[] allocateUninitializedArray(int size) {
        return new byte[size];
    }

    static ByteBuffer newDirectBuffer(long address, int capacity) {
        ObjectUtil.checkPositiveOrZero(capacity, "capacity");
        return segmentAt(address, capacity).asByteBuffer();
    }

    static boolean hasDirectByteBufferAddress(ByteBuffer buffer) {
        return buffer.isDirect();
    }

    static long directBufferAddress(ByteBuffer buffer) {
        return MemorySegment.ofBuffer(buffer).address() - buffer.position();
    }

    static long byteArrayBaseOffset() {
        return -1;
    }

    static Object getObject(Object object, long fieldOffset) {
        throw neverCalledWithUnsafe();
    }

    static int getInt(Object object, long fieldOffset) {
        throw neverCalledWithUnsafe();
    }

    static int getIntVolatile(Object object, long fieldOffset) {
        throw neverCalledWithUnsafe();
    }

    static void putOrderedInt(Object object, long fieldOffset, int value) {
        throw neverCalledWithUnsafe();
    }

    static int getAndAddInt(Object object, long fieldOffset, int value) {
        throw neverCalledWithUnsafe();
    }

    static boolean compareAndSwapInt(Object object, long fieldOffset, int expected, int value) {
        throw neverCalledWithUnsafe();
    }

    static void safeConstructPutInt(Object object, long fieldOffset, int value) {
        throw neverCalledWithUnsafe();
    }

    static long getLong(Object object, long fieldOffset) {
        throw neverCalledWithUnsafe();
    }

    static long objectFieldOffset(Field field) {
        throw neverCalledWithUnsafe();
    }

    private static MemorySegment segmentAt(long address, long size) {
        return MemorySegment.ofAddress(address).reinterpret(size);
    }

    static byte getByte(long address) {
        return segmentAt(address, 1).get(ValueLayout.JAVA_BYTE, 0);
    }

    static short getShort(long address) {
        return segmentAt(address, 2).get(ValueLayout.JAVA_SHORT_UNALIGNED, 0);
    }

    static int getInt(long address) {
        return segmentAt(address, 4).get(ValueLayout.JAVA_INT_UNALIGNED, 0);
    }

    static long getLong(long address) {
        return segmentAt(address, 8).get(ValueLayout.JAVA_LONG_UNALIGNED, 0);
    }

    static byte getByte(byte[] data, int index) {
        return data[index];
    }

    static byte getByte(byte[] data, long index) {
        return data[(int) index];
    }

    static short getShort(byte[] data, int index) {
        return (short) BYTE_ARRAY_SHORT.get(data, index);
    }

    static int getInt(byte[] data, int index) {
        return (int) BYTE_ARRAY_INT.get(data, index);
    }

    static int getInt(int[] data, long index) {
        return data[(int) index];
    }

    static long getLong(byte[] data, int index) {
        return (long) BYTE_ARRAY_LONG.get(data, index);
    }

    static long getLong(long[] data, long index) {
        return data[(int) index];
    }

    static void putByte(long address, byte value) {
        segmentAt(address, 1).set(ValueLayout.JAVA_BYTE, 0, value);
    }

    static void putShort(long address, short value) {
        segmentAt(address, 2).set(ValueLayout.JAVA_SHORT_UNALIGNED, 0, value);
    }

    static void putShortOrdered(long address, short newValue) {
        VarHandle.storeStoreFence();
        putShort(address, newValue);
    }

    static void putInt(long address, int value) {
        segmentAt(address, 4).set(ValueLayout.JAVA_INT_UNALIGNED, 0, value);
    }

    static void putLong(long address, long value) {
        segmentAt(address, 8).set(ValueLayout.JAVA_LONG_UNALIGNED, 0, value);
    }

    static void putByte(byte[] data, int index, byte value) {
        data[index] = value;
    }

    static void putByte(Object data, long offset, byte value) {
        ((byte[]) data)[(int) offset + 1] = value;
    }

    static void putShort(byte[] data, int index, short value) {
        BYTE_ARRAY_SHORT.set(data, index, Short.reverseBytes(value));
    }

    static void putInt(byte[] data, int index, int value) {
        BYTE_ARRAY_INT.set(data, index, Integer.reverseBytes(value));
    }

    static void putLong(byte[] data, int index, long value) {
        BYTE_ARRAY_LONG.set(data, index, Long.reverseBytes(value));
    }

    static void putObject(Object o, long offset, Object x) {
        throw neverCalledWithUnsafe();
    }

    static void copyMemory(long srcAddr, long dstAddr, long length) {
        MemorySegment srcSegment = segmentAt(srcAddr, length);
        MemorySegment dstSegment = segmentAt(dstAddr, length);
        dstSegment.copyFrom(srcSegment);
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        if (src instanceof byte[] srcArray && dst == null) {
            int srcPos = (int) srcOffset + 1;
            MemorySegment.ofArray(srcArray).asSlice(srcPos, length).copyFrom(
                    MemorySegment.ofAddress(dstOffset).reinterpret(length));
        } else if (src == null && dst instanceof byte[] dstArray) {
            int dstPos = (int) dstOffset + 1;
            MemorySegment.ofAddress(srcOffset).reinterpret(length).copyFrom(
                    MemorySegment.ofArray(dstArray).asSlice(dstPos, length));
        } else if (src instanceof byte[] srcArray && dst instanceof byte[] dstArray) {
            System.arraycopy(srcArray, (int) srcOffset + 1, dstArray, (int) dstOffset + 1, (int) length);
        }
    }

    static void copyMemory(byte[] src, int srcIndex, byte[] dst, int dstIndex, int length) {
        System.arraycopy(src, srcIndex, dst, dstIndex, length);
    }

    static void copyMemory(byte[] src, int srcIndex, byte[] dst, int dstIndex, long length) {
        System.arraycopy(src, srcIndex, dst, dstIndex, (int) length);
    }

    static void setMemory(long address, long length, byte value) {
        segmentAt(address, length).fill(value);
    }

    static void setMemory(Object o, long offset, long bytes, byte value) {
        byte[] arr = (byte[]) o;
        int start = (int) offset + 1;
        Arrays.fill(arr, start, start + (int) bytes, value);
    }

    static boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        return Arrays.equals(bytes1, startPos1, startPos1 + length,
                                       bytes2, startPos2, startPos2 + length);
    }

    static int equalsConstantTime(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        long result = 0;
        int i = startPos1;
        int j = startPos2;
        final int longEnd = startPos1 + (length & ~7);
        for (; i < longEnd; i += 8, j += 8) {
            long v1 = (long) BYTE_ARRAY_LONG.get(bytes1, i);
            long v2 = (long) BYTE_ARRAY_LONG.get(bytes2, j);
            result |= v1 ^ v2;
        }
        int remainingBytes = length & 7;
        if (remainingBytes >= 4) {
            result |= (int) BYTE_ARRAY_INT.get(bytes1, i) ^ (int) BYTE_ARRAY_INT.get(bytes2, j);
            i += 4;
            j += 4;
            remainingBytes -= 4;
        }
        if (remainingBytes >= 2) {
            result |= (short) BYTE_ARRAY_SHORT.get(bytes1, i) ^ (short) BYTE_ARRAY_SHORT.get(bytes2, j);
            i += 2;
            j += 2;
            remainingBytes -= 2;
        }
        if (remainingBytes == 1) {
            result |= (long) bytes1[i] ^ (long) bytes2[j];
        }
        return ConstantTimeUtils.equalsConstantTime(result, 0);
    }

    static boolean isZero(byte[] bytes, int startPos, int length) {
        if (length <= 0) {
            return true;
        }
        int i = startPos;
        final int longEnd = startPos + (length & ~7);
        for (; i < longEnd; i += 8) {
            if ((long) BYTE_ARRAY_LONG.get(bytes, i) != 0) {
                return false;
            }
        }
        int remainingBytes = length & 7;
        if (remainingBytes >= 4) {
            if ((int) BYTE_ARRAY_INT.get(bytes, i) != 0) {
                return false;
            }
            i += 4;
            remainingBytes -= 4;
        }
        if (remainingBytes >= 2) {
            if ((short) BYTE_ARRAY_SHORT.get(bytes, i) != 0) {
                return false;
            }
            i += 2;
            remainingBytes -= 2;
        }
        if (remainingBytes == 1) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    static int hashCodeAscii(byte[] bytes, int startPos, int length) {
        int hash = HASH_CODE_ASCII_SEED;
        int i = startPos;
        final int end = startPos + length;
        // Process in 8-byte chunks using VarHandle for efficient long reads.
        int remainingBytes = length & 7;
        int longEnd = end - remainingBytes;
        for (; i < longEnd; i += 8) {
            long value = (long) BYTE_ARRAY_LONG.get(bytes, i);
            hash = hashCodeAsciiCompute(value, hash);
        }
        if (remainingBytes == 0) {
            return hash;
        }
        int hcConst = HASH_CODE_C1;
        if (remainingBytes != 2 & remainingBytes != 4 & remainingBytes != 6) { // 1, 3, 5, 7
            hash = hash * HASH_CODE_C1 + hashCodeAsciiSanitize(bytes[i]);
            hcConst = HASH_CODE_C2;
            i++;
        }
        if (remainingBytes != 1 & remainingBytes != 4 & remainingBytes != 5) { // 2, 3, 6, 7
            short shortVal = (short) BYTE_ARRAY_SHORT.get(bytes, i);
            hash = hash * hcConst + hashCodeAsciiSanitize(shortVal);
            hcConst = hcConst == HASH_CODE_C1 ? HASH_CODE_C2 : HASH_CODE_C1;
            i += 2;
        }
        if (remainingBytes >= 4) { // 4, 5, 6, 7
            int intVal = (int) BYTE_ARRAY_INT.get(bytes, i);
            return hash * hcConst + hashCodeAsciiSanitize(intVal);
        }
        return hash;
    }

    static int hashCodeAsciiCompute(long value, int hash) {
        // masking with 0x1f reduces the number of overall bits that impact the hash code but makes the hash
        // code the same regardless of character case (upper case or lower case hash is the same).
        return hash * HASH_CODE_C1 +
                // Low order int
                hashCodeAsciiSanitize((int) value) * HASH_CODE_C2 +
                // High order int
                (int) ((value & 0x1f1f1f1f00000000L) >>> 32);
    }

    static int hashCodeAsciiSanitize(int value) {
        return value & 0x1f1f1f1f;
    }

    static int hashCodeAsciiSanitize(short value) {
        return value & 0x1f1f;
    }

    static int hashCodeAsciiSanitize(byte value) {
        return value & 0x1f;
    }

    static ClassLoader getClassLoader(final Class<?> clazz) {
        return clazz.getClassLoader();
    }

    static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    static ClassLoader getSystemClassLoader() {
        return ClassLoader.getSystemClassLoader();
    }

    static int addressSize() {
        return (int) ValueLayout.ADDRESS.byteSize();
    }

    static long allocateMemory(long size) {
        Arena arena = Arena.ofShared();
        try {
            MemorySegment segment = arena.allocate(size);
            long address = segment.address();
            ALLOCATED_MEMORY.put(address, new Allocation(segment, arena));
            return address;
        } catch (Throwable t) {
            arena.close();
            throw t;
        }
    }

    static void freeMemory(long address) {
        if (address == 0) {
            return;
        }
        Allocation alloc = ALLOCATED_MEMORY.remove(address);
        if (alloc != null) {
            alloc.arena().close();
        }
    }

    static long reallocateMemory(long address, long newSize) {
        if (address == 0) {
            return allocateMemory(newSize);
        }
        // Allocate new memory first so old allocation stays valid on failure
        Arena newArena = Arena.ofShared();
        try {
            MemorySegment newSegment = newArena.allocate(newSize);
            // Now that new allocation succeeded, safely remove old
            Allocation old = ALLOCATED_MEMORY.remove(address);
            if (old == null) {
                throw new IllegalArgumentException("Unknown address: " + address);
            }
            long copySize = Math.min(old.segment().byteSize(), newSize);
            if (copySize > 0) {
                newSegment.copyFrom(segmentAt(address, copySize));
            }
            old.arena().close();
            long newAddress = newSegment.address();
            ALLOCATED_MEMORY.put(newAddress, new Allocation(newSegment, newArena));
            return newAddress;
        } catch (Throwable t) {
            newArena.close();
            throw t;
        }
    }

    static boolean isAndroid() {
        return false;
    }

    static boolean isExplicitTryReflectionSetAccessible() {
        return true;
    }

    static int javaVersion() {
        return JAVA_VERSION;
    }

    private static int javaVersion0() {
        try {
            int majorVersion = majorVersionFromJavaSpecificationVersion();
            logger.debug("Java version: {}", majorVersion);
            return majorVersion;
        } catch (Exception e) {
            logger.debug("Unable to determine major version from java.specification.version", e);
            return 25;
        }
    }

    // Package-private for testing only
    static int majorVersionFromJavaSpecificationVersion() {
        return majorVersion(SystemPropertyUtil.get("java.specification.version", "25"));
    }

    // Package-private for testing only
    static int majorVersion(final String javaSpecVersion) {
        final String[] components = javaSpecVersion.split("\\.");
        final int[] version = new int[components.length];
        for (int i = 0; i < components.length; i++) {
            version[i] = Integer.parseInt(components[i]);
        }

        if (version[0] == 1) {
            assert version[1] >= 6;
            return version[1];
        } else {
            return version[0];
        }
    }

    private static RuntimeException neverCalledWithUnsafe() {
        return new UnsupportedOperationException("Unsafe not available");
    }

    private PlatformDependent0() {
    }
}
