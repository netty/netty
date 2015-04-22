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

import static io.netty.util.internal.StringUtil.asciiToLowerCase;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

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

import sun.misc.Unsafe;

/**
 * The {@link PlatformDependent} operations which requires access to {@code sun.misc.*}.
 */
final class PlatformDependent0 {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent0.class);
    private static final Unsafe UNSAFE;
    private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private static final long ADDRESS_FIELD_OFFSET;
    private static final long BYTE_ARRAY_BASE_OFFSET;
    private static final long CHAR_ARRAY_BASE_OFFSET;
    private static final long CHAR_ARRAY_INDEX_SCALE;
    private static final HashCodeGenerator UNSAFE_HASER;
    private static final HashCodeGenerator UNSAFE_HASER_CASE_INSENSITIVE;
    private static final long STRING_VALUE_FIELD_OFFSET;
    private static final long STRING_BUILDER_VALUE_FIELD_OFFSET;

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
            ADDRESS_FIELD_OFFSET = -1;
            BYTE_ARRAY_BASE_OFFSET = CHAR_ARRAY_BASE_OFFSET = CHAR_ARRAY_INDEX_SCALE = -1;
            UNALIGNED = false;
            UNSAFE_HASER = UNSAFE_HASER_CASE_INSENSITIVE = null;
            STRING_VALUE_FIELD_OFFSET = STRING_BUILDER_VALUE_FIELD_OFFSET = -1;
        } else {
            ADDRESS_FIELD_OFFSET = objectFieldOffset(addressField);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
            CHAR_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(char[].class);
            CHAR_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(char[].class);
            UNSAFE_HASER = new UnsafeHasher();
            UNSAFE_HASER_CASE_INSENSITIVE = new UnsafeHasherCaseInsensitive();
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

            Field stringValueField = AccessController.doPrivileged(new PrivilegedAction<Field>() {
                @Override
                public Field run() {
                    try {
                        Field f = String.class.getDeclaredField("value");
                        f.setAccessible(true);
                        return f;
                    } catch (NoSuchFieldException e) {
                        logger.warn("Failed to find String value array." +
                                "String hash code optimizations are disabled.", e);
                    } catch (SecurityException e) {
                        logger.info("No permissions to get String value array." +
                                "String hash code optimizations are disabled.", e);
                    }
                    return null;
                }
            });
            Field stringBuilderValueField = AccessController.doPrivileged(new PrivilegedAction<Field>() {
                @Override
                public Field run() {
                    try {
                        Field f = StringBuilder.class.getSuperclass().getDeclaredField("value");
                        f.setAccessible(true);
                        return f;
                    } catch (NoSuchFieldException e) {
                        logger.warn("Failed to find StringBuilder value array." +
                                " Hash code optimizations for this type are disabled.", e);
                    } catch (SecurityException e) {
                        logger.info("No permissions to get StringBuilder value array." +
                                " Hash code optimizations for this type are disabled.", e);
                    }
                    return null;
                }
            });
            STRING_VALUE_FIELD_OFFSET = stringValueField == null ?
                    -1 : UNSAFE.objectFieldOffset(stringValueField);
            STRING_BUILDER_VALUE_FIELD_OFFSET = stringBuilderValueField == null ?
                    -1 : UNSAFE.objectFieldOffset(stringBuilderValueField);
        }
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static boolean unalignedAccess() {
        return UNALIGNED;
    }

    static void throwException(Throwable t) {
        UNSAFE.throwException(t);
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

    static long charArrayBaseOffset() {
        return CHAR_ARRAY_BASE_OFFSET;
    }

    static long charArrayIndexScale() {
        return CHAR_ARRAY_INDEX_SCALE;
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

    static long getLong(Object object, long fieldOffset) {
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

    static HashCodeGenerator hashCodeGenerator() {
        return UNSAFE_HASER;
    }

    static HashCodeGenerator hashCodeGeneratorAsciiCaseInsensitive() {
        return UNSAFE_HASER_CASE_INSENSITIVE;
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

    static int addressSize() {
        return UNSAFE.addressSize();
    }

    static long allocateMemory(long size) {
        return UNSAFE.allocateMemory(size);
    }

    static void freeMemory(long address) {
        UNSAFE.freeMemory(address);
    }

    private static char[] array(CharSequence data) {
        if (data.getClass() == String.class) {
            return STRING_VALUE_FIELD_OFFSET != -1 ? (char[]) UNSAFE.getObject(data, STRING_VALUE_FIELD_OFFSET) : null;
        } else if (data.getClass() == StringBuilder.class) {
            return STRING_BUILDER_VALUE_FIELD_OFFSET != -1 ?
                    (char[]) UNSAFE.getObject(data, STRING_BUILDER_VALUE_FIELD_OFFSET) : null;
        }
        return null;
    }

    private static final class UnsafeHasherCaseInsensitive extends DefaultHashCodeGeneratorCaseInsensitive {
        @Override
        public boolean equals(byte[] bytes1, int startPos1, CharSequence bytes2, int startPos2, int len) {
            char[] bytes2Array = array(bytes2);
            if (bytes2Array != null) {
                return equals(bytes1, startPos1, bytes2Array, startPos2, len);
            }
            return super.equals(bytes1, startPos1, bytes2, startPos2, len);
        }

        @Override
        public boolean equals(CharSequence bytes1, int startPos1, CharSequence bytes2, int startPos2, int len) {
            // CharSequence by itself does not provide access to any underlying data structure, and using
            // charAt can be costly. For the common types in the CharSequence we can provide an optimized
            // hashCode by directly using the underlying char[].
            char[] bytes1Array = array(bytes1);
            if (bytes1Array != null) {
                char[] bytes2Array = array(bytes2);
                if (bytes2Array != null) {
                    return equals(bytes1Array, startPos1, bytes2Array, startPos2, len);
                }
            }
            // TODO: consider optimization if 1 returns a char[] and the other doesn't?
            return super.equals(bytes1, startPos1, bytes2, startPos2, len);
        }

        private boolean equals(byte[] bytes1, int startPos1, char[] bytes2, int startPos2, int len) {
            final int end = startPos1 + len;
            for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
                char c1 = (char) (bytes1[i] & 0xFF);
                char c2 = bytes2[j];
                if (c1 != c2 && asciiToLowerCase(c1) != asciiToLowerCase(c2)) {
                    return false;
                }
            }
            return true;
        }

        private boolean equals(char[] bytes1, int startPos1, char[] bytes2, int startPos2, int len) {
            final int end = startPos1 + len;
            for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
                char c1 = bytes1[i];
                char c2 = bytes2[j];
                if (c1 != c2 && asciiToLowerCase(c1) != asciiToLowerCase(c2)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class UnsafeHasher extends DefaultHashCodeGenerator {
        @Override
        public boolean equals(byte[] bytes1, int startPos1, CharSequence bytes2, int startPos2, int len) {
            char[] bytes2Array = array(bytes2);
            if (bytes2Array != null) {
                return equals(bytes1, startPos1, bytes2Array, startPos2, len);
            }
            return super.equals(bytes1, startPos1, bytes2, startPos2, len);
        }

        private boolean equals(byte[] bytes1, int startPos1, char[] bytes2, int startPos2, int len) {
            final int end = startPos1 + len;
            for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
                if ((char) (bytes1[i] & 0xFF) != bytes2[j]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(CharSequence bytes1, int startPos1, CharSequence bytes2, int startPos2, int len) {
            // CharSequence by itself does not provide access to any underlying data structure, and using
            // charAt can be costly. For the common types in the CharSequence we can provide an optimized
            // hashCode by directly using the underlying char[].
            char[] bytes1Array = array(bytes1);
            if (bytes1Array != null) {
                char[] bytes2Array = array(bytes2);
                if (bytes2Array != null) {
                    return equals(bytes1Array, startPos1, bytes2Array, startPos2, len);
                }
            }
            // TODO: consider optimization if 1 returns a char[] and the other doesn't?
            return super.equals(bytes1, startPos1, bytes2, startPos2, len);
        }

        @Override
        public boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int len) {
            if (len <= 0) {
                return true;
            }
            int remainingBytes = len & 7;
            final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
            final long baseOffset2 = BYTE_ARRAY_BASE_OFFSET + startPos2;
            final long end1 = baseOffset1 + remainingBytes;
            for (long i = baseOffset1 - 8 + len,
                      j = baseOffset2 - 8 + len; i >= end1; i -= 8, j -= 8) {
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

        private boolean equals(char[] bytes1, int startPos1, char[] bytes2, int startPos2, int len) {
            final int remainingBytes = len & 7;
            if (len > 7) {
                final long rend = remainingBytes + startPos1;
                int i = len - 8 + startPos1, j = len - 8 + startPos2;
                do {
                    if (UNSAFE.getLong(bytes1, CHAR_ARRAY_BASE_OFFSET + i * CHAR_ARRAY_INDEX_SCALE) !=
                        UNSAFE.getLong(bytes2, CHAR_ARRAY_BASE_OFFSET + j * CHAR_ARRAY_INDEX_SCALE) ||
                        UNSAFE.getLong(bytes1, CHAR_ARRAY_BASE_OFFSET + (i + 4) * CHAR_ARRAY_INDEX_SCALE) !=
                        UNSAFE.getLong(bytes2, CHAR_ARRAY_BASE_OFFSET + (j + 4) * CHAR_ARRAY_INDEX_SCALE)) {
                        return false;
                    }
                    i -= 8;
                    j -= 8;
                } while (i >= rend);
            }
            switch (remainingBytes) {
            case 7:
                return UNSAFE.getLong(bytes1, CHAR_ARRAY_BASE_OFFSET + (startPos1 + 3) * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getLong(bytes2, CHAR_ARRAY_BASE_OFFSET + (startPos2 + 3) * CHAR_ARRAY_INDEX_SCALE) &&
                       UNSAFE.getInt(bytes1, CHAR_ARRAY_BASE_OFFSET + (startPos1 + 1) * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getInt(bytes2, CHAR_ARRAY_BASE_OFFSET + (startPos2 + 1) * CHAR_ARRAY_INDEX_SCALE) &&
                       UNSAFE.getByte(bytes1, CHAR_ARRAY_BASE_OFFSET + startPos1 * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getByte(bytes2, CHAR_ARRAY_BASE_OFFSET + startPos2 * CHAR_ARRAY_INDEX_SCALE);
            case 6:
                return UNSAFE.getLong(bytes1, CHAR_ARRAY_BASE_OFFSET + (startPos1 + 2) * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getLong(bytes2, CHAR_ARRAY_BASE_OFFSET + (startPos2 + 2) * CHAR_ARRAY_INDEX_SCALE) &&
                       UNSAFE.getInt(bytes1, CHAR_ARRAY_BASE_OFFSET + startPos1 * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getInt(bytes2, CHAR_ARRAY_BASE_OFFSET + startPos2 * CHAR_ARRAY_INDEX_SCALE);
            case 5:
                return UNSAFE.getLong(bytes1, CHAR_ARRAY_BASE_OFFSET + (startPos1 + 1) * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getLong(bytes2, CHAR_ARRAY_BASE_OFFSET + (startPos2 + 1) * CHAR_ARRAY_INDEX_SCALE) &&
                       UNSAFE.getByte(bytes1, CHAR_ARRAY_BASE_OFFSET + startPos1 * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getByte(bytes2, CHAR_ARRAY_BASE_OFFSET + startPos2 * CHAR_ARRAY_INDEX_SCALE);
            case 4:
                return UNSAFE.getLong(bytes1, CHAR_ARRAY_BASE_OFFSET + startPos1 * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getLong(bytes2, CHAR_ARRAY_BASE_OFFSET + startPos2 * CHAR_ARRAY_INDEX_SCALE);
            case 3:
                return UNSAFE.getInt(bytes1, CHAR_ARRAY_BASE_OFFSET + (startPos1 + 1) * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getInt(bytes2, CHAR_ARRAY_BASE_OFFSET + (startPos2 + 1) * CHAR_ARRAY_INDEX_SCALE) &&
                       UNSAFE.getByte(bytes1, CHAR_ARRAY_BASE_OFFSET + startPos1 * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getByte(bytes2, CHAR_ARRAY_BASE_OFFSET + startPos2 * CHAR_ARRAY_INDEX_SCALE);
            case 2:
                return UNSAFE.getInt(bytes1, CHAR_ARRAY_BASE_OFFSET + startPos1 * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getInt(bytes2, CHAR_ARRAY_BASE_OFFSET + startPos2 * CHAR_ARRAY_INDEX_SCALE);
            case 1:
                return UNSAFE.getByte(bytes1, CHAR_ARRAY_BASE_OFFSET + startPos1 * CHAR_ARRAY_INDEX_SCALE) ==
                       UNSAFE.getByte(bytes2, CHAR_ARRAY_BASE_OFFSET + startPos2 * CHAR_ARRAY_INDEX_SCALE);
            default:
                return true;
            }
        }
    }

    private PlatformDependent0() {
    }
}
