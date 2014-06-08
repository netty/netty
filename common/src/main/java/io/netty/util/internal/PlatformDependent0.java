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
import sun.misc.Cleaner;
import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * The {@link PlatformDependent} operations which requires access to {@code sun.misc.*}.
 */
final class PlatformDependent0 {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent0.class);
    private static final Unsafe UNSAFE;
    private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private static final long ADDRESS_FIELD_OFFSET;

    /**
     * Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to allow safepoint polling
     * during a large copy.
     */
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    /**
     * {@code true} if and only if the platform supports unaligned access.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Segmentation_fault#Bus_error">Wikipedia on segfault</a>
     */
    private static final boolean UNALIGNED;

    static {
        boolean directBufferFreeable = false;
        try {
            Class<?> cls = Class.forName("sun.nio.ch.DirectBuffer", false, getClassLoader(PlatformDependent0.class));
            Method method = cls.getMethod("cleaner");
            if ("sun.misc.Cleaner".equals(method.getReturnType().getName())) {
                directBufferFreeable = true;
            }
        } catch (Throwable t) {
            // We don't have sun.nio.ch.DirectBuffer.cleaner().
        }
        logger.debug("sun.nio.ch.DirectBuffer.cleaner(): {}", directBufferFreeable? "available" : "unavailable");

        Field addressField;
        try {
            addressField = Buffer.class.getDeclaredField("address");
            addressField.setAccessible(true);
            if (addressField.getLong(ByteBuffer.allocate(1)) != 0) {
                // A heap buffer must have 0 address.
                addressField = null;
            } else {
                ByteBuffer direct = ByteBuffer.allocateDirect(1);
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
        if (addressField != null && directBufferFreeable) {
            try {
                Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                unsafe = (Unsafe) unsafeField.get(null);
                logger.debug("sun.misc.Unsafe.theUnsafe: {}", unsafe != null? "available" : "unavailable");

                // Ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK.
                // https://github.com/netty/netty/issues/1061
                // http://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
                try {
                    unsafe.getClass().getDeclaredMethod(
                            "copyMemory",
                            new Class[] { Object.class, long.class, Object.class, long.class, long.class });

                    logger.debug("sun.misc.Unsafe.copyMemory: available");
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
        }
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static void throwException(Throwable t) {
        UNSAFE.throwException(t);
    }

    static void freeDirectBuffer(ByteBuffer buffer) {
        if (!(buffer instanceof DirectBuffer)) {
            return;
        }
        try {
            Cleaner cleaner = ((DirectBuffer) buffer).cleaner();
            if (cleaner != null) {
                cleaner.clean();
            }
        } catch (Throwable t) {
            // Nothing we can do here.
        }
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
        if (UNALIGNED) {
            return UNSAFE.getShort(address);
        } else if (BIG_ENDIAN) {
            return (short) (getByte(address) << 8 | getByte(address + 1) & 0xff);
        } else {
            return (short) (getByte(address + 1) << 8 | getByte(address) & 0xff);
        }
    }

    static int getInt(long address) {
        if (UNALIGNED) {
            return UNSAFE.getInt(address);
        } else if (BIG_ENDIAN) {
            return getByte(address) << 24 |
                  (getByte(address + 1) & 0xff) << 16 |
                  (getByte(address + 2) & 0xff) <<  8 |
                   getByte(address + 3) & 0xff;
        } else {
            return getByte(address + 3) << 24 |
                  (getByte(address + 2) & 0xff) << 16 |
                  (getByte(address + 1) & 0xff) <<  8 |
                   getByte(address) & 0xff;
        }
    }

    static long getLong(long address) {
        if (UNALIGNED) {
            return UNSAFE.getLong(address);
        } else if (BIG_ENDIAN) {
            return (long) getByte(address) << 56 |
                  ((long) getByte(address + 1) & 0xff) << 48 |
                  ((long) getByte(address + 2) & 0xff) << 40 |
                  ((long) getByte(address + 3) & 0xff) << 32 |
                  ((long) getByte(address + 4) & 0xff) << 24 |
                  ((long) getByte(address + 5) & 0xff) << 16 |
                  ((long) getByte(address + 6) & 0xff) <<  8 |
                   (long) getByte(address + 7) & 0xff;
        } else {
            return (long) getByte(address + 7) << 56 |
                  ((long) getByte(address + 6) & 0xff) << 48 |
                  ((long) getByte(address + 5) & 0xff) << 40 |
                  ((long) getByte(address + 4) & 0xff) << 32 |
                  ((long) getByte(address + 3) & 0xff) << 24 |
                  ((long) getByte(address + 2) & 0xff) << 16 |
                  ((long) getByte(address + 1) & 0xff) <<  8 |
                   (long) getByte(address) & 0xff;
        }
    }

    static void putOrderedObject(Object object, long address, Object value) {
        UNSAFE.putOrderedObject(object, address, value);
    }

    static void putByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    static void putShort(long address, short value) {
        if (UNALIGNED) {
            UNSAFE.putShort(address, value);
        } else if (BIG_ENDIAN) {
            putByte(address, (byte) (value >>> 8));
            putByte(address + 1, (byte) value);
        } else {
            putByte(address + 1, (byte) (value >>> 8));
            putByte(address, (byte) value);
        }
    }

    static void putInt(long address, int value) {
        if (UNALIGNED) {
            UNSAFE.putInt(address, value);
        } else if (BIG_ENDIAN) {
            putByte(address, (byte) (value >>> 24));
            putByte(address + 1, (byte) (value >>> 16));
            putByte(address + 2, (byte) (value >>> 8));
            putByte(address + 3, (byte) value);
        } else {
            putByte(address + 3, (byte) (value >>> 24));
            putByte(address + 2, (byte) (value >>> 16));
            putByte(address + 1, (byte) (value >>> 8));
            putByte(address, (byte) value);
        }
    }

    static void putLong(long address, long value) {
        if (UNALIGNED) {
            UNSAFE.putLong(address, value);
        } else if (BIG_ENDIAN) {
            putByte(address, (byte) (value >>> 56));
            putByte(address + 1, (byte) (value >>> 48));
            putByte(address + 2, (byte) (value >>> 40));
            putByte(address + 3, (byte) (value >>> 32));
            putByte(address + 4, (byte) (value >>> 24));
            putByte(address + 5, (byte) (value >>> 16));
            putByte(address + 6, (byte) (value >>> 8));
            putByte(address + 7, (byte) value);
        } else {
            putByte(address + 7, (byte) (value >>> 56));
            putByte(address + 6, (byte) (value >>> 48));
            putByte(address + 5, (byte) (value >>> 40));
            putByte(address + 4, (byte) (value >>> 32));
            putByte(address + 3, (byte) (value >>> 24));
            putByte(address + 2, (byte) (value >>> 16));
            putByte(address + 1, (byte) (value >>> 8));
            putByte(address, (byte) value);
        }
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
            Class<U> tclass, String fieldName) throws Exception {
        return new UnsafeAtomicReferenceFieldUpdater<U, W>(UNSAFE, tclass, fieldName);
    }

    static <T> AtomicIntegerFieldUpdater<T> newAtomicIntegerFieldUpdater(
            Class<?> tclass, String fieldName) throws Exception {
        return new UnsafeAtomicIntegerFieldUpdater<T>(UNSAFE, tclass, fieldName);
    }

    static <T> AtomicLongFieldUpdater<T> newAtomicLongFieldUpdater(
            Class<?> tclass, String fieldName) throws Exception {
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

    private PlatformDependent0() {
    }

}
