/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The {@link PlatformDependent} operations which requires access to {@code sun.misc.*}.
 */
final class PlatformDependent0 {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent0.class);
    static final Unsafe UNSAFE;
    private static final long ADDRESS_FIELD_OFFSET;
    private static final long BYTE_ARRAY_BASE_OFFSET;
    private static final Constructor<?> DIRECT_BUFFER_CONSTRUCTOR;

    // constants borrowed from murmur3
    static final int HASH_CODE_ASCII_SEED = 0xc2b2ae35;
    static final int HASH_CODE_C1 = 0x1b873593;
    static final int HASH_CODE_C2 = 0x1b873593;

    /**
     * Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to allow safepoint polling
     * during a large copy.
     */
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    private static final boolean UNALIGNED;

    static {
        final ByteBuffer direct = ByteBuffer.allocateDirect(1);
        final Field addressField;
        // attempt to access field Buffer#address
        final Object maybeAddressField = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    final Field field = Buffer.class.getDeclaredField("address");
                    field.setAccessible(true);
                    // if direct really is a direct buffer, address will be non-zero
                    if (field.getLong(direct) == 0) {
                        return null;
                    }
                    return field;
                } catch (IllegalAccessException e) {
                    return e;
                } catch (NoSuchFieldException e) {
                    return e;
                } catch (SecurityException e) {
                    return e;
                }
            }
        });

        if (maybeAddressField instanceof Field) {
            addressField = (Field) maybeAddressField;
            logger.debug("java.nio.Buffer.address: available");
        } else {
            logger.debug("java.nio.Buffer.address: unavailable", (Exception) maybeAddressField);
            addressField = null;
        }

        Unsafe unsafe;
        if (addressField != null) {
            // attempt to access field Unsafe#theUnsafe
            final Object maybeUnsafe = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                        unsafeField.setAccessible(true);
                        // the unsafe instance
                        return unsafeField.get(null);
                    } catch (NoSuchFieldException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    } catch (IllegalAccessException e) {
                        return e;
                    }
                }
            });

            // the conditional check here can not be replaced with checking that maybeUnsafe
            // is an instanceof Unsafe and reversing the if and else blocks; this is because an
            // instanceof check against Unsafe will trigger a class load and we might not have
            // the runtime permission accessClassInPackage.sun.misc
            if (maybeUnsafe instanceof Exception) {
                unsafe = null;
                logger.debug("sun.misc.Unsafe.theUnsafe: unavailable", (Exception) maybeUnsafe);
            } else {
                unsafe = (Unsafe) maybeUnsafe;
                logger.debug("sun.misc.Unsafe.theUnsafe: available");
            }

            // ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK
            // https://github.com/netty/netty/issues/1061
            // http://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
            if (unsafe != null) {
                final Unsafe finalUnsafe = unsafe;
                final Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            finalUnsafe.getClass().getDeclaredMethod(
                                    "copyMemory", Object.class, long.class, Object.class, long.class, long.class);
                            return null;
                        } catch (NoSuchMethodException e) {
                            return e;
                        } catch (SecurityException e) {
                            return e;
                        }
                    }
                });

                if (maybeException == null) {
                    logger.debug("sun.misc.Unsafe.copyMemory: available");
                } else {
                    // Unsafe.copyMemory(Object, long, Object, long, long) unavailable.
                    unsafe = null;
                    logger.debug("sun.misc.Unsafe.copyMemory: unavailable", (Exception) maybeException);
                }
            }
        } else {
            // If we cannot access the address of a direct buffer, there's no point of using unsafe.
            // Let's just pretend unsafe is unavailable for overall simplicity.
            unsafe = null;
        }

        UNSAFE = unsafe;

        if (unsafe == null) {
            ADDRESS_FIELD_OFFSET = -1;
            BYTE_ARRAY_BASE_OFFSET = -1;
            UNALIGNED = false;
            DIRECT_BUFFER_CONSTRUCTOR = null;
        } else {
            Constructor<?> directBufferConstructor;
            long address = -1;
            try {
                final Object maybeDirectBufferConstructor =
                        AccessController.doPrivileged(new PrivilegedAction<Object>() {
                            @Override
                            public Object run() {
                                try {
                                    final Constructor constructor =
                                            direct.getClass().getDeclaredConstructor(long.class, int.class);
                                    constructor.setAccessible(true);
                                    return constructor;
                                } catch (NoSuchMethodException e) {
                                    return e;
                                } catch (SecurityException e) {
                                    return e;
                                }
                            }
                        });

                if (maybeDirectBufferConstructor instanceof Constructor<?>) {
                    address = UNSAFE.allocateMemory(1);
                    // try to use the constructor now
                    try {
                        ((Constructor) maybeDirectBufferConstructor).newInstance(address, 1);
                        directBufferConstructor = (Constructor<?>) maybeDirectBufferConstructor;
                        logger.debug("direct buffer constructor: available");
                    } catch (InstantiationException e) {
                        directBufferConstructor = null;
                    } catch (IllegalAccessException e) {
                        directBufferConstructor = null;
                    } catch (InvocationTargetException e) {
                        directBufferConstructor = null;
                    }
                } else {
                    logger.debug(
                            "direct buffer constructor: unavailable",
                            (Exception) maybeDirectBufferConstructor);
                    directBufferConstructor = null;
                }
            } finally {
                if (address != -1) {
                    UNSAFE.freeMemory(address);
                }
            }
            DIRECT_BUFFER_CONSTRUCTOR = directBufferConstructor;

            ADDRESS_FIELD_OFFSET = objectFieldOffset(addressField);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
            boolean unaligned;
            Object maybeUnaligned = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        Class<?> bitsClass =
                                Class.forName("java.nio.Bits", false, PlatformDependent.getSystemClassLoader());
                        Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                        unalignedMethod.setAccessible(true);
                        return unalignedMethod.invoke(null);
                    } catch (ClassNotFoundException e) {
                        return e;
                    } catch (NoSuchMethodException e) {
                        return e;
                    } catch (InvocationTargetException e) {
                        return e;
                    } catch (IllegalAccessException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    }
                }
            });

            if (maybeUnaligned instanceof Boolean) {
                unaligned = (Boolean) maybeUnaligned;
                logger.debug("java.nio.Bits.unaligned: available, {}", unaligned);
            } else {
                String arch = SystemPropertyUtil.get("os.arch", "");
                //noinspection DynamicRegexReplaceableByCompiledPattern
                unaligned = arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64)$");
                Exception e = (Exception) maybeUnaligned;
                logger.debug("java.nio.Bits.unaligned: unavailable, " + unaligned, e);
            }

            UNALIGNED = unaligned;
        }

        logger.debug("java.nio.DirectByteBuffer.<init>(long, int): {}",
                DIRECT_BUFFER_CONSTRUCTOR != null ? "available" : "unavailable");

        freeDirectBuffer(direct);
    }

    static boolean isUnaligned() {
        return UNALIGNED;
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static boolean unalignedAccess() {
        return UNALIGNED;
    }

    static void throwException(Throwable cause) {
        // JVM has been observed to crash when passing a null argument. See https://github.com/netty/netty/issues/4131.
        UNSAFE.throwException(checkNotNull(cause, "cause"));
    }

    static boolean hasDirectBufferNoCleanerConstructor() {
        return DIRECT_BUFFER_CONSTRUCTOR != null;
    }

    static ByteBuffer reallocateDirectNoCleaner(ByteBuffer buffer, int capacity) {
        return newDirectBuffer(UNSAFE.reallocateMemory(directBufferAddress(buffer), capacity), capacity);
    }

    static ByteBuffer allocateDirectNoCleaner(int capacity) {
        return newDirectBuffer(UNSAFE.allocateMemory(capacity), capacity);
    }

    static ByteBuffer newDirectBuffer(long address, int capacity) {
        ObjectUtil.checkPositiveOrZero(address, "address");
        ObjectUtil.checkPositiveOrZero(capacity, "capacity");

        try {
            return (ByteBuffer) DIRECT_BUFFER_CONSTRUCTOR.newInstance(address, capacity);
        } catch (Throwable cause) {
            // Not expected to ever throw!
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new Error(cause);
        }
    }

    static void freeDirectBuffer(ByteBuffer buffer) {
        // Delegate to other class to not break on android
        // See https://github.com/netty/netty/issues/2604
        Cleaner0.freeDirectBuffer(buffer);
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

    static Object getObjectVolatile(Object object, long fieldOffset) {
        return UNSAFE.getObjectVolatile(object, fieldOffset);
    }

    static int getInt(Object object, long fieldOffset) {
        return UNSAFE.getInt(object, fieldOffset);
    }

    private static long getLong(Object object, long fieldOffset) {
        return UNSAFE.getLong(object, fieldOffset);
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

    static short getShort(byte[] data, int index) {
        return UNSAFE.getShort(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static int getInt(byte[] data, int index) {
        return UNSAFE.getInt(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static long getLong(byte[] data, int index) {
        return UNSAFE.getLong(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static void putOrderedObject(Object object, long address, Object value) {
        UNSAFE.putOrderedObject(object, address, value);
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

    static void putShort(byte[] data, int index, short value) {
        UNSAFE.putShort(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putInt(byte[] data, int index, int value) {
        UNSAFE.putInt(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putLong(byte[] data, int index, long value) {
        UNSAFE.putLong(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void copyMemory(long srcAddr, long dstAddr, long length) {
        //UNSAFE.copyMemory(srcAddr, dstAddr, length);
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            UNSAFE.copyMemory(srcAddr, dstAddr, size);
            length -= size;
            srcAddr += size;
            dstAddr += size;
        }
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        //UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, length);
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, size);
            length -= size;
            srcOffset += size;
            dstOffset += size;
        }
    }

    static void setMemory(long address, long bytes, byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }

    static void setMemory(Object o, long offset, long bytes, byte value) {
        UNSAFE.setMemory(o, offset, bytes, value);
    }

    static boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        if (length == 0) {
            return true;
        }
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long baseOffset2 = BYTE_ARRAY_BASE_OFFSET + startPos2;
        int remainingBytes = length & 7;
        final long end = baseOffset1 + remainingBytes;
        for (long i = baseOffset1 - 8 + length, j = baseOffset2 - 8 + length; i >= end; i -= 8, j -= 8) {
            if (UNSAFE.getLong(bytes1, i) != UNSAFE.getLong(bytes2, j)) {
                return false;
            }
        }

        if (remainingBytes >= 4) {
            remainingBytes -= 4;
            if (UNSAFE.getInt(bytes1, baseOffset1 + remainingBytes) !=
                UNSAFE.getInt(bytes2, baseOffset2 + remainingBytes)) {
                return false;
            }
        }
        if (remainingBytes >= 2) {
            return UNSAFE.getChar(bytes1, baseOffset1) == UNSAFE.getChar(bytes2, baseOffset2) &&
                   (remainingBytes == 2 || bytes1[startPos1 + 2] == bytes2[startPos2 + 2]);
        }
        return bytes1[startPos1] == bytes2[startPos2];
    }

    static int equalsConstantTime(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        long result = 0;
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long baseOffset2 = BYTE_ARRAY_BASE_OFFSET + startPos2;
        final int remainingBytes = length & 7;
        final long end = baseOffset1 + remainingBytes;
        for (long i = baseOffset1 - 8 + length, j = baseOffset2 - 8 + length; i >= end; i -= 8, j -= 8) {
            result |= UNSAFE.getLong(bytes1, i) ^ UNSAFE.getLong(bytes2, j);
        }
        switch (remainingBytes) {
            case 7:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getInt(bytes1, baseOffset1 + 3) ^ UNSAFE.getInt(bytes2, baseOffset2 + 3)) |
                        (UNSAFE.getChar(bytes1, baseOffset1 + 1) ^ UNSAFE.getChar(bytes2, baseOffset2 + 1)) |
                        (UNSAFE.getByte(bytes1, baseOffset1) ^ UNSAFE.getByte(bytes2, baseOffset2)), 0);
            case 6:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getInt(bytes1, baseOffset1 + 2) ^ UNSAFE.getInt(bytes2, baseOffset2 + 2)) |
                        (UNSAFE.getChar(bytes1, baseOffset1) ^ UNSAFE.getChar(bytes2, baseOffset2)), 0);
            case 5:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getInt(bytes1, baseOffset1 + 1) ^ UNSAFE.getInt(bytes2, baseOffset2 + 1)) |
                        (UNSAFE.getByte(bytes1, baseOffset1) ^ UNSAFE.getByte(bytes2, baseOffset2)), 0);
            case 4:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getInt(bytes1, baseOffset1) ^ UNSAFE.getInt(bytes2, baseOffset2)), 0);
            case 3:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getChar(bytes1, baseOffset1 + 1) ^ UNSAFE.getChar(bytes2, baseOffset2 + 1)) |
                        (UNSAFE.getByte(bytes1, baseOffset1) ^ UNSAFE.getByte(bytes2, baseOffset2)), 0);
            case 2:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getChar(bytes1, baseOffset1) ^ UNSAFE.getChar(bytes2, baseOffset2)), 0);
            case 1:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getByte(bytes1, baseOffset1) ^ UNSAFE.getByte(bytes2, baseOffset2)), 0);
            default:
                return ConstantTimeUtils.equalsConstantTime(result, 0);
        }
    }

    static int hashCodeAscii(byte[] bytes, int startPos, int length) {
        int hash = HASH_CODE_ASCII_SEED;
        final long baseOffset = BYTE_ARRAY_BASE_OFFSET + startPos;
        final int remainingBytes = length & 7;
        final long end = baseOffset + remainingBytes;
        for (long i = baseOffset - 8 + length; i >= end; i -= 8) {
            hash = hashCodeAsciiCompute(UNSAFE.getLong(bytes, i), hash);
        }
        switch(remainingBytes) {
        case 7:
            return ((hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset)))
                          * HASH_CODE_C2 + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset + 1)))
                          * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset + 3));
        case 6:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset)))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset + 2));
        case 5:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset)))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset + 1));
        case 4:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset));
        case 3:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset)))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset + 1));
        case 2:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset));
        case 1:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset));
        default:
            return hash;
        }
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

    static <U, W> AtomicReferenceFieldUpdater<U, W> newAtomicReferenceFieldUpdater(
            Class<? super U> tclass, String fieldName) throws Exception {
        return new UnsafeAtomicReferenceFieldUpdater<U, W>(UNSAFE, tclass, fieldName);
    }

    static <T> AtomicIntegerFieldUpdater<T> newAtomicIntegerFieldUpdater(
            Class<? super T> tclass, String fieldName) throws Exception {
        return new UnsafeAtomicIntegerFieldUpdater<T>(UNSAFE, tclass, fieldName);
    }

    static <T> AtomicLongFieldUpdater<T> newAtomicLongFieldUpdater(
            Class<? super T> tclass, String fieldName) throws Exception {
        return new UnsafeAtomicLongFieldUpdater<T>(UNSAFE, tclass, fieldName);
    }

    static ClassLoader getClassLoader(final Class<?> clazz) {
        if (System.getSecurityManager() == null) {
            return clazz.getClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return clazz.getClassLoader();
                }
            });
        }
    }

    static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
        }
    }

    static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return ClassLoader.getSystemClassLoader();
                }
            });
        }
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

    private PlatformDependent0() {
    }
}
