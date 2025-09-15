/*
 * Copyright 2013 The Netty Project
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

import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.invoke.MethodHandles.lookup;

/**
 * The {@link PlatformDependent} operations which requires access to {@code sun.misc.*}.
 */
final class PlatformDependent0 {

    private static final Logger logger = LoggerFactory.getLogger(PlatformDependent0.class);
    private static final long ADDRESS_FIELD_OFFSET;
    private static final long BYTE_ARRAY_BASE_OFFSET;
    private static final long INT_ARRAY_BASE_OFFSET;
    private static final long INT_ARRAY_INDEX_SCALE;
    private static final long LONG_ARRAY_BASE_OFFSET;
    private static final long LONG_ARRAY_INDEX_SCALE;
    private static final MethodHandle DIRECT_BUFFER_CONSTRUCTOR_HANDLE;
    private static final Throwable EXPLICIT_NO_UNSAFE_CAUSE = explicitNoUnsafeCause0();
    private static final MethodHandle ALLOCATE_ARRAY_HANDLE;
    private static final int JAVA_VERSION = javaVersion0();
    private static final boolean IS_ANDROID = isAndroid0();

    private static final Throwable UNSAFE_UNAVAILABILITY_CAUSE;

    // See https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/
    // ImageInfo.java
    private static final boolean RUNNING_IN_NATIVE_IMAGE = SystemPropertyUtil.contains(
            "org.graalvm.nativeimage.imagecode");

    private static final boolean IS_EXPLICIT_TRY_REFLECTION_SET_ACCESSIBLE = explicitTryReflectionSetAccessible0();

    static final Unsafe UNSAFE;

    // constants borrowed from murmur3
    static final int HASH_CODE_ASCII_SEED = 0xc2b2ae35;
    static final int HASH_CODE_C1 = 0xcc9e2d51;
    static final int HASH_CODE_C2 = 0x1b873593;

    private static final boolean UNALIGNED;
    private static final long BITS_MAX_DIRECT_MEMORY;

    static {
        final ByteBuffer direct;
        Field addressField = null;
        MethodHandle allocateArrayHandle = null;
        Throwable unsafeUnavailabilityCause;
        Unsafe unsafe;

        if ((unsafeUnavailabilityCause = EXPLICIT_NO_UNSAFE_CAUSE) != null) {
            direct = null;
            addressField = null;
            unsafe = null;
        } else {
            direct = ByteBuffer.allocateDirect(1);

            // attempt to access field Unsafe#theUnsafe
            Object maybeUnsafe;
            try {
                final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                // We always want to try using Unsafe as the access still works on java9 as well and
                // we need it for out native-transports and many optimizations.
                Throwable cause = ReflectionUtil.trySetAccessible(unsafeField, false);
                if (cause != null) {
                    maybeUnsafe = cause;
                } else {
                    // the unsafe instance
                    maybeUnsafe = unsafeField.get(null);
                }
            } catch (NoSuchFieldException | IllegalAccessException | NoClassDefFoundError e) {
                maybeUnsafe = e;
            } // Also catch NoClassDefFoundError in case someone uses for example OSGI and it made
            // Unsafe unloadable.

            // the conditional check here can not be replaced with checking that maybeUnsafe
            // is an instanceof Unsafe and reversing the if and else blocks; this is because an
            // instanceof check against Unsafe will trigger a class load and we might not have
            // the runtime permission accessClassInPackage.sun.misc
            if (maybeUnsafe instanceof Throwable) {
                unsafe = null;
                unsafeUnavailabilityCause = (Throwable) maybeUnsafe;
                if (logger.isTraceEnabled()) {
                    logger.debug("sun.misc.Unsafe.theUnsafe: unavailable", unsafeUnavailabilityCause);
                } else {
                    logger.debug("sun.misc.Unsafe.theUnsafe: unavailable: {}", ((Throwable) maybeUnsafe).getMessage());
                }
            } else {
                unsafe = (Unsafe) maybeUnsafe;
                logger.debug("sun.misc.Unsafe.theUnsafe: available");
            }

            if (unsafe != null) {
                final Unsafe finalUnsafe = unsafe;

                // attempt to access field Buffer#address
                Object maybeAddressField = null;
                try {
                    final Field field = Buffer.class.getDeclaredField("address");
                    // Use Unsafe to read value of the address field. This way it will not fail on JDK9+ which
                    // will forbid changing the access level via reflection.
                    final long offset = finalUnsafe.objectFieldOffset(field);
                    final long address = finalUnsafe.getLong(direct, offset);

                    // if direct really is a direct buffer, address will be non-zero
                    if (address != 0) {
                        maybeAddressField = field;
                    }
                } catch (NoSuchFieldException e) {
                    maybeAddressField = e;
                }

                if (maybeAddressField instanceof Field) {
                    addressField = (Field) maybeAddressField;
                    logger.debug("java.nio.Buffer.address: available");
                } else {
                    unsafeUnavailabilityCause = (Throwable) maybeAddressField;
                    if (logger.isTraceEnabled()) {
                        logger.debug("java.nio.Buffer.address: unavailable", unsafeUnavailabilityCause);
                    } else {
                        logger.debug("java.nio.Buffer.address: unavailable: {}",
                                unsafeUnavailabilityCause.getMessage());
                    }

                    // If we cannot access the address of a direct buffer, there's no point of using unsafe.
                    // Let's just pretend unsafe is unavailable for overall simplicity.
                    unsafe = null;
                }
            }

            if (unsafe != null) {
                // There are assumptions made where ever BYTE_ARRAY_BASE_OFFSET is used (equals, hashCodeAscii, and
                // primitive accessors) that arrayIndexScale == 1, and results are undefined if this is not the case.
                long byteArrayIndexScale = unsafe.arrayIndexScale(byte[].class);
                if (byteArrayIndexScale != 1) {
                    logger.debug("unsafe.arrayIndexScale is {} (expected: 1). Not using unsafe.", byteArrayIndexScale);
                    unsafeUnavailabilityCause = new UnsupportedOperationException("Unexpected unsafe.arrayIndexScale");
                    unsafe = null;
                }
            }
        }
        UNSAFE_UNAVAILABILITY_CAUSE = unsafeUnavailabilityCause;
        UNSAFE = unsafe;

        if (unsafe == null) {
            ADDRESS_FIELD_OFFSET = -1;
            BYTE_ARRAY_BASE_OFFSET = -1;
            LONG_ARRAY_BASE_OFFSET = -1;
            LONG_ARRAY_INDEX_SCALE = -1;
            INT_ARRAY_BASE_OFFSET = -1;
            INT_ARRAY_INDEX_SCALE = -1;
            UNALIGNED = false;
            BITS_MAX_DIRECT_MEMORY = -1;
            DIRECT_BUFFER_CONSTRUCTOR_HANDLE = null;
            ALLOCATE_ARRAY_HANDLE = null;
        } else {
            MethodHandle directBufferConstructorHandle;
            long address = -1;
            try {
                Object maybeDirectBufferConstructorHandle = Arrays
                        .stream(direct.getClass().getDeclaredConstructors())
                        .filter(declaredConstructor -> {
                            int parameterCount = declaredConstructor.getParameterCount();
                            if (parameterCount != 3 && parameterCount != 4) {
                                return false;
                            }
                            Parameter[] parameters = declaredConstructor.getParameters();
                            return parameters[0].getType() == long.class
                                   && (parameters[1].getType() == int.class
                                       || parameters[1].getType() == long.class)
                                   && parameters[2].getType() == Object.class;
                        })
                        .findFirst()
                        .map(constructor -> {
                            try {
                                Throwable cause = ReflectionUtil
                                        .trySetAccessible(constructor, true);
                                if (cause != null) {
                                    return cause;
                                }
                                MethodHandle constructorHandle = lookup()
                                        .unreflectConstructor(constructor);
                                if (constructor.getParameterCount() == 4) {
                                    Object[] nullArgs = { null };
                                    constructorHandle = MethodHandles
                                            .insertArguments(constructorHandle, 3, nullArgs);
                                }
                                return constructorHandle;
                            } catch (IllegalAccessException e) {
                                return e;
                            }
                        })
                        .orElseGet(() -> new NoSuchElementException("No matching constructor found"));

                if (maybeDirectBufferConstructorHandle instanceof MethodHandle) {
                    address = UNSAFE.allocateMemory(1);
                    // try to use the constructor now
                    try {
                        directBufferConstructorHandle = (MethodHandle) maybeDirectBufferConstructorHandle;
                        directBufferConstructorHandle.invoke(address, 1, (Object) null);
                        logger.debug("direct buffer constructor: available");
                    } catch (Throwable ignore) {
                        directBufferConstructorHandle = null;
                    }
                } else {
                    if (logger.isTraceEnabled()) {
                        logger.debug("direct buffer constructor: unavailable",
                                (Throwable) maybeDirectBufferConstructorHandle);
                    } else {
                        logger.debug("direct buffer constructor: unavailable: {}",
                                ((Throwable) maybeDirectBufferConstructorHandle).getMessage());
                    }
                    directBufferConstructorHandle = null;
                }
            } finally {
                if (address != -1) {
                    UNSAFE.freeMemory(address);
                }
            }
            DIRECT_BUFFER_CONSTRUCTOR_HANDLE = directBufferConstructorHandle;
            ADDRESS_FIELD_OFFSET = objectFieldOffset(addressField);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
            INT_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
            INT_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(int[].class);
            LONG_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(long[].class);
            LONG_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(long[].class);
            final boolean unaligned;
            // using a known type to avoid loading new classes
            final AtomicLong maybeMaxMemory = new AtomicLong(-1);

            Object maybeUnaligned = null;
            try {
                Class<?> bitsClass =
                        Class.forName("java.nio.Bits", false, ClassLoader.getSystemClassLoader());
                if (unsafeStaticFieldOffsetSupported()) {
                    try {
                        Field maxMemoryField = bitsClass.getDeclaredField("MAX_MEMORY");
                        if (maxMemoryField.getType() == long.class) {
                            long offset = UNSAFE.staticFieldOffset(maxMemoryField);
                            Object object = UNSAFE.staticFieldBase(maxMemoryField);
                            maybeMaxMemory.lazySet(UNSAFE.getLong(object, offset));
                        }
                    } catch (Throwable ignore) {
                        // ignore if can't access
                    }
                    try {
                        Field unalignedField = bitsClass.getDeclaredField("UNALIGNED");
                        if (unalignedField.getType() == boolean.class) {
                            long offset = UNSAFE.staticFieldOffset(unalignedField);
                            Object object = UNSAFE.staticFieldBase(unalignedField);
                            maybeUnaligned = UNSAFE.getBoolean(object, offset);
                        }
                        // There is something unexpected stored in the field,
                        // let us fall-back and try to use a reflective method call as last resort.
                    } catch (NoSuchFieldException ignore) {
                        // We did not find the field we expected, move on.
                    }
                }

                if (maybeUnaligned == null) {
                    Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                    Throwable cause = ReflectionUtil.trySetAccessible(unalignedMethod, true);
                    if (cause != null) {
                        maybeUnaligned = cause;
                    } else {
                        maybeUnaligned = unalignedMethod.invoke(null);
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException
                     | ClassNotFoundException | InvocationTargetException e) {
                maybeUnaligned = e;
            }

            if (maybeUnaligned instanceof Boolean) {
                unaligned = (Boolean) maybeUnaligned;
                logger.debug("java.nio.Bits.unaligned: available, {}", unaligned);
            } else {
                String arch = SystemPropertyUtil.get("os.arch", "");
                //noinspection DynamicRegexReplaceableByCompiledPattern
                unaligned = arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64)$");
                Throwable t = (Throwable) maybeUnaligned;
                if (logger.isTraceEnabled()) {
                    logger.debug("java.nio.Bits.unaligned: unavailable, {}", unaligned, t);
                } else {
                    logger.debug("java.nio.Bits.unaligned: unavailable, {}, {}", unaligned, t.getMessage());
                }
            }

            UNALIGNED = unaligned;
            BITS_MAX_DIRECT_MEMORY = maybeMaxMemory.get() >= 0? maybeMaxMemory.get() : -1;

            try {
                // Java9 has jdk.internal.misc.Unsafe and not all methods are propagated to
                // sun.misc.Unsafe
                Class<?> internalUnsafeClass = PlatformDependent0.class.getClassLoader()
                        .loadClass("jdk.internal.misc.Unsafe");
                Method method = internalUnsafeClass.getDeclaredMethod("getUnsafe");
                Object theInternalUnsafe = method.invoke(null);

                MethodHandle allocateUninitializedArray = lookup()
                        .findVirtual(
                                theInternalUnsafe.getClass(),
                                "allocateUninitializedArray",
                                MethodType.methodType(Object.class, Class.class, int.class))
                        .bindTo(theInternalUnsafe);
                byte[] bytes = (byte[]) (Object) allocateUninitializedArray.invoke(byte.class, 8);
                assert bytes.length == 8;
                allocateArrayHandle = allocateUninitializedArray;
                logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): available");
            } catch (Throwable e) {
                if (logger.isTraceEnabled()) {
                    logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable", e);
                } else {
                    logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable: {}",
                                 e.getMessage());
                }
            }

            ALLOCATE_ARRAY_HANDLE = allocateArrayHandle;
        }

        logger.debug("java.nio.DirectByteBuffer.<init>(long, {int,long}): {}",
                DIRECT_BUFFER_CONSTRUCTOR_HANDLE != null ? "available" : "unavailable");
    }

    private static boolean unsafeStaticFieldOffsetSupported() {
        return !RUNNING_IN_NATIVE_IMAGE;
    }

    static boolean isExplicitNoUnsafe() {
        return EXPLICIT_NO_UNSAFE_CAUSE != null;
    }

    private static Throwable explicitNoUnsafeCause0() {
        final boolean noUnsafe = SystemPropertyUtil.getBoolean("io.netty5.noUnsafe", false);
        logger.debug("-Dio.netty5.noUnsafe: {}", noUnsafe);

        if (noUnsafe) {
            logger.debug("sun.misc.Unsafe: unavailable (io.netty5.noUnsafe)");
            return new UnsupportedOperationException("sun.misc.Unsafe: unavailable (io.netty5.noUnsafe)");
        }

        boolean tryUnsafe = SystemPropertyUtil.getBoolean("io.netty5.tryUnsafe", true);

        if (!tryUnsafe) {
            String msg = "sun.misc.Unsafe: unavailable (io.netty5.tryUnsafe)";
            logger.debug(msg);
            return new UnsupportedOperationException(msg);
        }

        return null;
    }

    static boolean isUnaligned() {
        return UNALIGNED;
    }

    /**
     * Any value >= 0 should be considered as a valid max direct memory value.
     */
    static long bitsMaxDirectMemory() {
        return BITS_MAX_DIRECT_MEMORY;
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static Throwable getUnsafeUnavailabilityCause() {
        return UNSAFE_UNAVAILABILITY_CAUSE;
    }

    static boolean unalignedAccess() {
        return UNALIGNED;
    }

    static boolean hasDirectBufferNoCleanerConstructor() {
        return DIRECT_BUFFER_CONSTRUCTOR_HANDLE != null;
    }

    static ByteBuffer reallocateDirectNoCleaner(ByteBuffer buffer, int capacity) {
        return newDirectBuffer(UNSAFE.reallocateMemory(directBufferAddress(buffer), capacity), capacity, null);
    }

    static ByteBuffer allocateDirectNoCleaner(int capacity) {
        // Calling malloc with capacity of 0 may return a null ptr or a memory address that can be used.
        // Just use 1 to make it safe to use in all cases:
        // See: https://pubs.opengroup.org/onlinepubs/009695399/functions/malloc.html
        return newDirectBuffer(UNSAFE.allocateMemory(Math.max(1, capacity)), capacity, null);
    }

    static boolean hasAllocateArrayMethod() {
        return ALLOCATE_ARRAY_HANDLE != null;
    }

    static byte[] allocateUninitializedArray(int size) {
        try {
            return (byte[]) (Object) ALLOCATE_ARRAY_HANDLE.invoke(byte.class, size);
        } catch (Throwable e) {
            if (e instanceof Error) {
                throw (Error) e;
            }
            throw new Error(e);
        }
    }

    static ByteBuffer newDirectBuffer(long address, int capacity, Object attachment) {
        ObjectUtil.checkPositiveOrZero(capacity, "capacity");

        try {
            return (ByteBuffer) DIRECT_BUFFER_CONSTRUCTOR_HANDLE.invoke(address, capacity, attachment);
        } catch (Throwable cause) {
            // Not expected to ever throw!
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new Error(cause);
        }
    }

    static long directBufferAddress(ByteBuffer buffer) {
        return getLong(buffer, ADDRESS_FIELD_OFFSET);
    }

    static long byteArrayBaseOffset() {
        return BYTE_ARRAY_BASE_OFFSET;
    }

    static Object getObject(Object object, long fieldOffset) {
        return UNSAFE.getObject(object, fieldOffset);
    }

    static byte getByte(Object object, long fieldOffset) {
        return UNSAFE.getByte(object, fieldOffset);
    }

    static short getShort(Object object, long fieldOffset) {
        return UNSAFE.getShort(object, fieldOffset);
    }

    static char getChar(Object object, long fieldOffset) {
        return UNSAFE.getChar(object, fieldOffset);
    }

    static int getInt(Object object, long fieldOffset) {
        return UNSAFE.getInt(object, fieldOffset);
    }

    static float getFloat(Object object, long fieldOffset) {
        return UNSAFE.getFloat(object, fieldOffset);
    }

    static long getLong(Object object, long fieldOffset) {
        return UNSAFE.getLong(object, fieldOffset);
    }

    static double getDouble(Object object, long fieldOffset) {
        return UNSAFE.getDouble(object, fieldOffset);
    }

    static long objectFieldOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    static byte getByte(long address) {
        return UNSAFE.getByte(address);
    }

    static short getShort(long address) {
        return UNSAFE.getShort(address);
    }

    static int getInt(long address) {
        return UNSAFE.getInt(address);
    }

    static long getLong(long address) {
        return UNSAFE.getLong(address);
    }

    static byte getByte(byte[] data, int index) {
        return UNSAFE.getByte(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static byte getByte(byte[] data, long index) {
        return UNSAFE.getByte(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static short getShort(byte[] data, int index) {
        return UNSAFE.getShort(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static int getInt(byte[] data, int index) {
        return UNSAFE.getInt(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static int getInt(int[] data, long index) {
        return UNSAFE.getInt(data, INT_ARRAY_BASE_OFFSET + INT_ARRAY_INDEX_SCALE * index);
    }

    static int getIntVolatile(long address) {
        return UNSAFE.getIntVolatile(null, address);
    }

    static void putIntOrdered(long address, int newValue) {
        UNSAFE.putOrderedInt(null, address, newValue);
    }

    static long getLong(byte[] data, int index) {
        return UNSAFE.getLong(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static long getLong(long[] data, long index) {
        return UNSAFE.getLong(data, LONG_ARRAY_BASE_OFFSET + LONG_ARRAY_INDEX_SCALE * index);
    }

    static void putByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    static void putShort(long address, short value) {
        UNSAFE.putShort(address, value);
    }

    static void putInt(long address, int value) {
        UNSAFE.putInt(address, value);
    }

    static void putLong(long address, long value) {
        UNSAFE.putLong(address, value);
    }

    static void putByte(byte[] data, int index, byte value) {
        UNSAFE.putByte(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putByte(Object data, long offset, byte value) {
        UNSAFE.putByte(data, offset, value);
    }

    static void putShort(byte[] data, int index, short value) {
        UNSAFE.putShort(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putShort(Object data, long offset, short value) {
        UNSAFE.putShort(data, offset, value);
    }

    static void putChar(Object data, long offset, char value) {
        UNSAFE.putChar(data, offset, value);
    }

    static void putInt(byte[] data, int index, int value) {
        UNSAFE.putInt(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putInt(Object data, long offset, int value) {
        UNSAFE.putInt(data, offset, value);
    }

    static void putFloat(Object data, long offset, float value) {
        UNSAFE.putFloat(data, offset, value);
    }

    static void putLong(byte[] data, int index, long value) {
        UNSAFE.putLong(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putLong(Object data, long offset, long value) {
        UNSAFE.putLong(data, offset, value);
    }

    static void putDouble(Object data, long offset, double value) {
        UNSAFE.putDouble(data, offset, value);
    }

    static void putObject(Object o, long offset, Object x) {
        UNSAFE.putObject(o, offset, x);
    }

    static void copyMemory(long srcAddr, long dstAddr, long length) {
        UNSAFE.copyMemory(srcAddr, dstAddr, length);
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, length);
    }

    static void setMemory(long address, long bytes, byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }

    static void setMemory(Object o, long offset, long bytes, byte value) {
        UNSAFE.setMemory(o, offset, bytes, value);
    }

    static boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        int remainingBytes = length & 7;
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long diff = startPos2 - startPos1;
        if (length >= 8) {
            final long end = baseOffset1 + remainingBytes;
            for (long i = baseOffset1 - 8 + length; i >= end; i -= 8) {
                if (UNSAFE.getLong(bytes1, i) != UNSAFE.getLong(bytes2, i + diff)) {
                    return false;
                }
            }
        }
        if (remainingBytes >= 4) {
            remainingBytes -= 4;
            long pos = baseOffset1 + remainingBytes;
            if (UNSAFE.getInt(bytes1, pos) != UNSAFE.getInt(bytes2, pos + diff)) {
                return false;
            }
        }
        final long baseOffset2 = baseOffset1 + diff;
        if (remainingBytes >= 2) {
            return UNSAFE.getChar(bytes1, baseOffset1) == UNSAFE.getChar(bytes2, baseOffset2) &&
                    (remainingBytes == 2 ||
                    UNSAFE.getByte(bytes1, baseOffset1 + 2) == UNSAFE.getByte(bytes2, baseOffset2 + 2));
        }
        return remainingBytes == 0 ||
                UNSAFE.getByte(bytes1, baseOffset1) == UNSAFE.getByte(bytes2, baseOffset2);
    }

    static int equalsConstantTime(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        long result = 0;
        long remainingBytes = length & 7;
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long end = baseOffset1 + remainingBytes;
        final long diff = startPos2 - startPos1;
        for (long i = baseOffset1 - 8 + length; i >= end; i -= 8) {
            result |= UNSAFE.getLong(bytes1, i) ^ UNSAFE.getLong(bytes2, i + diff);
        }
        if (remainingBytes >= 4) {
            result |= UNSAFE.getInt(bytes1, baseOffset1) ^ UNSAFE.getInt(bytes2, baseOffset1 + diff);
            remainingBytes -= 4;
        }
        if (remainingBytes >= 2) {
            long pos = end - remainingBytes;
            result |= UNSAFE.getChar(bytes1, pos) ^ UNSAFE.getChar(bytes2, pos + diff);
            remainingBytes -= 2;
        }
        if (remainingBytes == 1) {
            long pos = end - 1;
            result |= UNSAFE.getByte(bytes1, pos) ^ UNSAFE.getByte(bytes2, pos + diff);
        }
        return ConstantTimeUtils.equalsConstantTime(result, 0);
    }

    static boolean isZero(byte[] bytes, int startPos, int length) {
        if (length <= 0) {
            return true;
        }
        final long baseOffset = BYTE_ARRAY_BASE_OFFSET + startPos;
        int remainingBytes = length & 7;
        final long end = baseOffset + remainingBytes;
        for (long i = baseOffset - 8 + length; i >= end; i -= 8) {
            if (UNSAFE.getLong(bytes, i) != 0) {
                return false;
            }
        }

        if (remainingBytes >= 4) {
            remainingBytes -= 4;
            if (UNSAFE.getInt(bytes, baseOffset + remainingBytes) != 0) {
                return false;
            }
        }
        if (remainingBytes >= 2) {
            return UNSAFE.getChar(bytes, baseOffset) == 0 &&
                    (remainingBytes == 2 || bytes[startPos + 2] == 0);
        }
        return bytes[startPos] == 0;
    }

    static int hashCodeAscii(byte[] bytes, int startPos, int length) {
        int hash = HASH_CODE_ASCII_SEED;
        long baseOffset = BYTE_ARRAY_BASE_OFFSET + startPos;
        final int remainingBytes = length & 7;
        final long end = baseOffset + remainingBytes;
        for (long i = baseOffset - 8 + length; i >= end; i -= 8) {
            hash = hashCodeAsciiCompute(UNSAFE.getLong(bytes, i), hash);
        }
        if (remainingBytes == 0) {
            return hash;
        }
        int hcConst = HASH_CODE_C1;
        if (remainingBytes != 2 & remainingBytes != 4 & remainingBytes != 6) { // 1, 3, 5, 7
            hash = hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset));
            hcConst = HASH_CODE_C2;
            baseOffset++;
        }
        if (remainingBytes != 1 & remainingBytes != 4 & remainingBytes != 5) { // 2, 3, 6, 7
            hash = hash * hcConst + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset));
            hcConst = hcConst == HASH_CODE_C1 ? HASH_CODE_C2 : HASH_CODE_C1;
            baseOffset += 2;
        }
        if (remainingBytes >= 4) { // 4, 5, 6, 7
            return hash * hcConst + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset));
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

    static int addressSize() {
        return UNSAFE.addressSize();
    }

    static long allocateMemory(long size) {
        return UNSAFE.allocateMemory(size);
    }

    static void freeMemory(long address) {
        UNSAFE.freeMemory(address);
    }

    static long reallocateMemory(long address, long newSize) {
        return UNSAFE.reallocateMemory(address, newSize);
    }

    static boolean isAndroid() {
        return IS_ANDROID;
    }

    private static boolean isAndroid0() {
        // Idea: Sometimes java binaries include Android classes on the classpath, even if it isn't actually Android.
        // Rather than check if certain classes are present, just check the VM, which is tied to the JDK.

        // Optional improvement: check if `android.os.Build.VERSION` is >= 24. On later versions of Android, the
        // OpenJDK is used, which means `Unsafe` will actually work as expected.

        // Android sets this property to Dalvik, regardless of whether it actually is.
        String vmName = SystemPropertyUtil.get("java.vm.name");
        boolean isAndroid = "Dalvik".equals(vmName);
        if (isAndroid) {
            logger.debug("Platform: Android");
        }
        return isAndroid;
    }

    private static boolean explicitTryReflectionSetAccessible0() {
        // we disable reflective access
        return SystemPropertyUtil.getBoolean("io.netty5.tryReflectionSetAccessible", RUNNING_IN_NATIVE_IMAGE);
    }

    static boolean isExplicitTryReflectionSetAccessible() {
        return IS_EXPLICIT_TRY_REFLECTION_SET_ACCESSIBLE;
    }

    static int javaVersion() {
        return JAVA_VERSION;
    }

    private static int javaVersion0() {
        final int majorVersion;

        if (isAndroid0()) {
            majorVersion = 11;
        } else {
            majorVersion = majorVersionFromJavaSpecificationVersion();
        }

        logger.debug("Java version: {}", majorVersion);

        return majorVersion;
    }

    // Package-private for testing only
    @VisibleForTesting
    static int majorVersionFromJavaSpecificationVersion() {
        return majorVersion(SystemPropertyUtil.get("java.specification.version", "11"));
    }

    // Package-private for testing only
    @VisibleForTesting
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

    private PlatformDependent0() {
    }
}
