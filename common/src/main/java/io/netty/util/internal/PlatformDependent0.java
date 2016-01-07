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

import java.lang.reflect.Field;
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

    /**
     * Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to allow safepoint polling
     * during a large copy.
     */
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    private static final boolean UNALIGNED;

    static {
        ByteBuffer direct = ByteBuffer.allocateDirect(1);
        Field addressField;
        try {
            addressField = Buffer.class.getDeclaredField("address");
            addressField.setAccessible(true);
            if (addressField.getLong(ByteBuffer.allocate(1)) != 0) {
                // A heap buffer must have 0 address.
                addressField = null;
            } else {
                if (addressField.getLong(direct) == 0) {
                    // A direct buffer must have non-zero address.
                    addressField = null;
                }
            }
        } catch (Throwable t) {
            // Failed to access the address field.
            addressField = null;
        }
        logger.debug("java.nio.Buffer.address: {}", addressField != null? "available" : "unavailable");

        Unsafe unsafe;
        if (addressField != null) {
            try {
                Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                unsafe = (Unsafe) unsafeField.get(null);
                logger.debug("sun.misc.Unsafe.theUnsafe: {}", unsafe != null ? "available" : "unavailable");

                // Ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK.
                // https://github.com/netty/netty/issues/1061
                // http://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
                try {
                    if (unsafe != null) {
                        unsafe.getClass().getDeclaredMethod(
                                "copyMemory", Object.class, long.class, Object.class, long.class, long.class);
                        logger.debug("sun.misc.Unsafe.copyMemory: available");
                    }
                } catch (NoSuchMethodError t) {
                    logger.debug("sun.misc.Unsafe.copyMemory: unavailable");
                    throw t;
                } catch (NoSuchMethodException e) {
                    logger.debug("sun.misc.Unsafe.copyMemory: unavailable");
                    throw e;
                }
            } catch (Throwable cause) {
                // Unsafe.copyMemory(Object, long, Object, long, long) unavailable.
                unsafe = null;
            }
        } else {
            // If we cannot access the address of a direct buffer, there's no point of using unsafe.
            // Let's just pretend unsafe is unavailable for overall simplicity.
            unsafe = null;
        }

        UNSAFE = unsafe;

        if (unsafe == null) {
            BYTE_ARRAY_BASE_OFFSET = -1;
            ADDRESS_FIELD_OFFSET = -1;
            UNALIGNED = false;
        } else {
            ADDRESS_FIELD_OFFSET = objectFieldOffset(addressField);
            boolean unaligned;
            try {
                Class<?> bitsClass = Class.forName("java.nio.Bits", false, ClassLoader.getSystemClassLoader());
                Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                unalignedMethod.setAccessible(true);
                unaligned = Boolean.TRUE.equals(unalignedMethod.invoke(null));
            } catch (Throwable t) {
                // We at least know x86 and x64 support unaligned access.
                String arch = SystemPropertyUtil.get("os.arch", "");
                //noinspection DynamicRegexReplaceableByCompiledPattern
                unaligned = arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64)$");
            }

            UNALIGNED = unaligned;
            logger.debug("java.nio.Bits.unaligned: {}", UNALIGNED);
            BYTE_ARRAY_BASE_OFFSET = arrayBaseOffset();
        }
    }

    static boolean isUnaligned() {
        return UNALIGNED;
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static void throwException(Throwable cause) {
        // JVM has been observed to crash when passing a null argument. See https://github.com/netty/netty/issues/4131.
        UNSAFE.throwException(checkNotNull(cause, "cause"));
    }

    static void freeDirectBuffer(ByteBuffer buffer) {
        // Delegate to other class to not break on android
        // See https://github.com/netty/netty/issues/2604
        Cleaner0.freeDirectBuffer(buffer);
    }

    static long directBufferAddress(ByteBuffer buffer) {
        return getLong(buffer, ADDRESS_FIELD_OFFSET);
    }

    static long arrayBaseOffset() {
        return UNSAFE.arrayBaseOffset(byte[].class);
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
