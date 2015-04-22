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
        } else {
            ADDRESS_FIELD_OFFSET = objectFieldOffset(addressField);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
            CHAR_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(char[].class);
            CHAR_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(char[].class);
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

    static boolean equals(byte[] bytes1, int startPos1, int endPos1, byte[] bytes2, int startPos2, int endPos2) {
        final int len1 = endPos1 - startPos1;
        final int len2 = endPos2 - startPos2;
        if (len1 != len2) {
            return false;
        }
        if (len1 == 0) {
            return true;
        }
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long baseOffset2 = BYTE_ARRAY_BASE_OFFSET + startPos2;
        int remainingBytes = len1 & 7;
        for (int i = len1 - 8; i >= remainingBytes; i -= 8) {
            if (UNSAFE.getLong(bytes1, baseOffset1 + i) != UNSAFE.getLong(bytes2, baseOffset2 + i)) {
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

    static HashCodeGenerator hashCodeGenerator() {
        return NettyHashAscii.INSTANCE;
    }

    static HashCodeGenerator hashCodeGeneratorAsciiCaseInsensitive() {
        return NettyHashAsciiCaseInsensitive.INSTANCE;
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

    private abstract static class AbstractNettyHashAscii extends AbstractHashCodeGenerator {
        private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractNettyHashAscii.class);
        protected static final long HASH_PRIME = 0x100000001b3L;
        protected static final long HASH_OFFSET = 0xcbf29ce484222325L;
        protected static final int EMPTY_HASH = (int) (HASH_OFFSET ^ (HASH_OFFSET >>> 32));
        protected static final long STRING_VALUE_FIELD_OFFSET;
        protected static final long STRING_BUILDER_VALUE_FIELD_OFFSET;
        static {
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
                    -1 : PlatformDependent0.objectFieldOffset(stringValueField);
            STRING_BUILDER_VALUE_FIELD_OFFSET = stringBuilderValueField == null ?
                    -1 : PlatformDependent0.objectFieldOffset(stringBuilderValueField);
        }

        @Override
        public final int emptyHashValue() {
            return EMPTY_HASH;
        }

        @Override
        public final int hashCodeAsBytes(CharSequence data, int startPos, int endPos) {
            // CharSequence by itself does not provide access to any underlying data structure, and using
            // charAt can be costly. For the common types in the CharSequence we can provide an optimized
            // hashCode by direclty using the underlying char[].
            if (data.getClass() == String.class && STRING_VALUE_FIELD_OFFSET != -1) {
                return hashCodeAsBytes(
                        (char[]) UNSAFE.getObject(data, STRING_VALUE_FIELD_OFFSET), startPos, endPos);
            }
            if (data.getClass() == StringBuilder.class && STRING_BUILDER_VALUE_FIELD_OFFSET != -1) {
                return hashCodeAsBytes(
                        (char[]) UNSAFE.getObject(data, STRING_BUILDER_VALUE_FIELD_OFFSET), startPos, endPos);
            }
            return hashCodeAsBytesDirect(data, startPos, endPos);
        }

        /**
         * If optimizations fail which directly get the underlying {@code char[]} from {@code data} and delegate to
         * {@link #hashCodeAsBytes(char[], int, int)} then this method will be called to manually use
         * {@link CharSequence#charAt(int)}.
         */
        protected abstract int hashCodeAsBytesDirect(CharSequence data, int startPos, int endPos);
    }

    /**
     * A modified version of <a href="http://www.isthe.com/chongo/tech/comp/fnv/">FNV1-a</a> for a byte stream.
     */
    private static final class NettyHashAscii extends AbstractNettyHashAscii {
        public static final NettyHashAscii INSTANCE = new NettyHashAscii();

        private NettyHashAscii() { }

        @Override
        public int hashCode(byte[] bytes, int startPos, int endPos) {
            long hash = HASH_OFFSET;
            final int len = endPos - startPos;
            final int remainingBytes = len & 7;
            final long baseOffset = startPos + BYTE_ARRAY_BASE_OFFSET;
            if (len > 7) { // This is to reduce overhead for small input sizes.
                final long rend = remainingBytes + baseOffset;
                long i = len - 8 + baseOffset;
                do {
                    hash ^= UNSAFE.getLong(bytes, i);
                    hash *= HASH_PRIME;
                    i -= 8;
                } while (i >= rend);
            }
            // Account for each case of remaining bytes independently. Conditional statements and decrementing a
            // variable to share code proved to generate measurable amount of overhead for small inputs which are
            // expected to be common (i.e. Header Names).
            switch (remainingBytes) {
            case 7: {
                hash ^= UNSAFE.getInt(bytes, baseOffset + 3);
                hash *= HASH_PRIME;
                hash ^= UNSAFE.getChar(bytes, baseOffset + 1);
                hash *= HASH_PRIME;
                hash ^= UNSAFE.getByte(bytes, baseOffset);
                hash *= HASH_PRIME;
                break;
            }
            case 6: {
                hash ^= UNSAFE.getInt(bytes, baseOffset + 2);
                hash *= HASH_PRIME;
                hash ^= UNSAFE.getChar(bytes, baseOffset);
                hash *= HASH_PRIME;
                break;
            }
            case 5: {
                hash ^= UNSAFE.getInt(bytes, baseOffset + 1);
                hash *= HASH_PRIME;
                hash ^= UNSAFE.getByte(bytes, baseOffset);
                hash *= HASH_PRIME;
                break;
            }
            case 4: {
                hash ^= UNSAFE.getInt(bytes, baseOffset);
                hash *= HASH_PRIME;
                break;
            }
            case 3:
                hash ^= UNSAFE.getChar(bytes, baseOffset + 1);
                hash *= HASH_PRIME;
                hash ^= UNSAFE.getByte(bytes, baseOffset);
                hash *= HASH_PRIME;
                break;
            case 2:
                hash ^= UNSAFE.getChar(bytes, baseOffset);
                hash *= HASH_PRIME;
                break;
            case 1:
                hash ^= UNSAFE.getByte(bytes, baseOffset);
                hash *= HASH_PRIME;
                break;
            default:
                break;
            }
            return (int) (hash ^ (hash >>> 32));
        }

        @Override
        public int hashCodeAsBytes(char[] bytes, int startPos, int endPos) {
            long hash = HASH_OFFSET;
            final int len = endPos - startPos;
            final int remainingBytes = len & 7;
            if (len > 7) { // This is to reduce overhead for small input sizes.
                final long rend = remainingBytes + startPos;
                long i = len - 8 + startPos;
                do {
                    long v = UNSAFE.getLong(bytes, CHAR_ARRAY_BASE_OFFSET + i * CHAR_ARRAY_INDEX_SCALE);
                    long v2 = UNSAFE.getLong(bytes, CHAR_ARRAY_BASE_OFFSET + (i + 4) * CHAR_ARRAY_INDEX_SCALE);
                    hash ^= (v & 0xFF) | (v & 0xFF0000) >>> 8 | (v & 0xFF00000000L) >>> 16 |
                            (v & 0xFF000000000000L) >>> 24 |
                            (v2 & 0xFF) << 32 | (v2 & 0xFF0000) << 24 | (v2 & 0xFF00000000L) << 16 |
                            (v2 & 0xFF000000000000L) << 8;
                    hash *= HASH_PRIME;
                    i -= 8;
                } while (i >= rend);
            }
            // Account for each case of remaining bytes independently. Conditional statements and decrementing a
            // variable to share code proved to generate measurable amount of overhead for small inputs which are
            // expected to be common (i.e. Header Names).
            switch (remainingBytes) {
            case 7: {
                long v = UNSAFE.getLong(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 3) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= (int) ((v & 0xFF) | (v & 0xFF0000) >>> 8 |
                               (v & 0xFF00000000L) >>> 16 | (v & 0xFF000000000000L) >>> 24);
                hash *= HASH_PRIME;
                int v2 = UNSAFE.getInt(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 1) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= (v2 & 0xFF) | (v2 & 0xFF0000) >>> 8;
                hash *= HASH_PRIME;
                hash ^= UNSAFE.getByte(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash *= HASH_PRIME;
                break;
            }
            case 6: {
                long v = UNSAFE.getLong(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 2) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= (int) ((v & 0xFF) | (v & 0xFF0000) >>> 8 |
                               (v & 0xFF00000000L) >>> 16 | (v & 0xFF000000000000L) >>> 24);
                hash *= HASH_PRIME;
                int v2 = UNSAFE.getInt(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash ^= (v2 & 0xFF) | (v2 & 0xFF0000) >>> 8;
                hash *= HASH_PRIME;
                break;
            }
            case 5: {
                long v = UNSAFE.getLong(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 1) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= (int) ((v & 0xFF) | (v & 0xFF0000) >>> 8 |
                               (v & 0xFF00000000L) >>> 16 | (v & 0xFF000000000000L) >>> 24);
                hash *= HASH_PRIME;
                hash ^= UNSAFE.getByte(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash *= HASH_PRIME;
                break;
            }
            case 4: {
                long v = UNSAFE.getLong(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash ^= (int) ((v & 0xFF) | (v & 0xFF0000) >>> 8 |
                               (v & 0xFF00000000L) >>> 16 | (v & 0xFF000000000000L) >>> 24);
                hash *= HASH_PRIME;
                break;
            }
            case 3: {
                int v = UNSAFE.getInt(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 1) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= (v & 0xFF) | (v & 0xFF0000) >>> 8;
                hash *= HASH_PRIME;
                hash ^= UNSAFE.getByte(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash *= HASH_PRIME;
                break;
            }
            case 2: {
                int v = UNSAFE.getInt(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash ^= (v & 0xFF) | (v & 0xFF0000) >>> 8;
                hash *= HASH_PRIME;
                break;
            }
            case 1:
                hash ^= UNSAFE.getByte(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash *= HASH_PRIME;
                break;
            default:
                break;
            }
            return (int) (hash ^ (hash >>> 32));
        }

        @Override
        protected int hashCodeAsBytesDirect(CharSequence data, int startPos, int endPos) {
            long hash = HASH_OFFSET;
            final int len = endPos - startPos;
            int remainingBytes = len & 7;
            if (len > 7) { // This is to reduce overhead for small input sizes.
                final int rend = remainingBytes + startPos;
                int i = len - 8 + startPos;
                do {
                    hash ^= (byte) data.charAt(i) |
                            (((byte) data.charAt(i + 1)) << 8) |
                            (((byte) data.charAt(i + 2)) << 16) |
                            (((byte) data.charAt(i + 3)) << 24) |
                            ((long) ((byte) data.charAt(i + 4)) << 32) |
                            ((long) ((byte) data.charAt(i + 5)) << 40) |
                            ((long) ((byte) data.charAt(i + 6)) << 48) |
                            ((long) ((byte) data.charAt(i + 7)) << 56);
                    hash *= HASH_PRIME;
                    i -= 8;
                } while (i >= rend);
            }
            if (remainingBytes >= 4) {
                remainingBytes -= 4;
                final int i = startPos + remainingBytes;
                hash ^= (byte) data.charAt(i) |
                        (asciiToLowerCase((byte) data.charAt(i + 1)) << 8) |
                        (asciiToLowerCase((byte) data.charAt(i + 2)) << 16) |
                        (asciiToLowerCase((byte) data.charAt(i + 3)) << 24);
                hash *= HASH_PRIME;
            }
            switch (remainingBytes) {
            case 3:
                hash ^= asciiToLowerCase((byte) data.charAt(startPos + 1)) |
                (asciiToLowerCase((byte) data.charAt(startPos + 2)) << 8);
                hash *= HASH_PRIME;
                hash ^= asciiToLowerCase(data.charAt(startPos));
                hash *= HASH_PRIME;
                break;
            case 2:
                hash ^= asciiToLowerCase((byte) data.charAt(startPos)) |
                (asciiToLowerCase((byte) data.charAt(startPos + 1)) << 8);
                hash *= HASH_PRIME;
                break;
            case 1:
                hash ^= asciiToLowerCase(data.charAt(startPos));
                hash *= HASH_PRIME;
                break;
            default:
                break;
            }
            return (int) (hash ^ (hash >>> 32));
        }
    }

    /**
     * A modified version of <a href="http://www.isthe.com/chongo/tech/comp/fnv/">FNV1-a</a> for a byte stream which
     * is agnostic to ASCII character case.
     */
    private static final class NettyHashAsciiCaseInsensitive extends AbstractNettyHashAscii {
        public static final NettyHashAsciiCaseInsensitive INSTANCE = new NettyHashAsciiCaseInsensitive();
        private NettyHashAsciiCaseInsensitive() { }

        @Override
        public int hashCode(byte[] bytes, int startPos, int endPos) {
            long hash = HASH_OFFSET;
            final int len = endPos - startPos;
            final int remainingBytes = len & 7;
            final long baseOffset = startPos + BYTE_ARRAY_BASE_OFFSET;
            if (len > 7) { // This is to reduce overhead for small input sizes.
                final long rend = remainingBytes + baseOffset;
                long i = len - 8 + baseOffset;
                do {
                    long v = UNSAFE.getLong(bytes, i);
                    hash ^= asciiToLowerCase((byte) v) |
                            byteOffset8(v) |
                            byteOffset16(v) |
                            byteOffset24(v) |
                            byteOffset32(v) |
                            byteOffset40(v) |
                            byteOffset48(v) |
                            byteOffset56(v);
                    hash *= HASH_PRIME;
                    i -= 8;
                } while (i >= rend);
            }
            // Account for each case of remaining bytes independently. Conditional statements and decrementing a
            // variable to share code proved to generate measurable amount of overhead for small inputs which are
            // expected to be common (i.e. Header Names).
            switch (remainingBytes) {
            case 7: {
                int v = UNSAFE.getInt(bytes, baseOffset + 3);
                hash ^= asciiToLowerCase((byte) v) |
                        byteOffset8(v) |
                        byteOffset16(v) |
                        byteOffset24(v);
                hash *= HASH_PRIME;
                char v2 = UNSAFE.getChar(bytes, baseOffset + 1);
                hash ^= asciiToLowerCase((byte) v2) | byteOffset8(v2);
                hash *= HASH_PRIME;
                hash ^= asciiToLowerCase(UNSAFE.getByte(bytes, baseOffset));
                hash *= HASH_PRIME;
                break;
            }
            case 6: {
                int v = UNSAFE.getInt(bytes, baseOffset + 2);
                hash ^= asciiToLowerCase((byte) v) |
                        byteOffset8(v) |
                        byteOffset16(v) |
                        byteOffset24(v);
                hash *= HASH_PRIME;
                char v2 = UNSAFE.getChar(bytes, baseOffset);
                hash ^= asciiToLowerCase((byte) v2) | byteOffset8(v2);
                hash *= HASH_PRIME;
                break;
            }
            case 5: {
                int v = UNSAFE.getInt(bytes, baseOffset + 1);
                hash ^= asciiToLowerCase((byte) v) |
                        byteOffset8(v) |
                        byteOffset16(v) |
                        byteOffset24(v);
                hash *= HASH_PRIME;
                hash ^= asciiToLowerCase(UNSAFE.getByte(bytes, baseOffset));
                hash *= HASH_PRIME;
                break;
            }
            case 4: {
                int v = UNSAFE.getInt(bytes, baseOffset);
                hash ^= asciiToLowerCase((byte) v) |
                        byteOffset8(v) |
                        byteOffset16(v) |
                        byteOffset24(v);
                hash *= HASH_PRIME;
                break;
            }
            case 3: {
                char v = UNSAFE.getChar(bytes, baseOffset + 1);
                hash ^= asciiToLowerCase((byte) v) | byteOffset8(v);
                hash *= HASH_PRIME;
                hash ^= asciiToLowerCase(UNSAFE.getByte(bytes, baseOffset));
                hash *= HASH_PRIME;
                break;
            }
            case 2: {
                char v = UNSAFE.getChar(bytes, baseOffset);
                hash ^= asciiToLowerCase((byte) v) | byteOffset8(v);
                hash *= HASH_PRIME;
                break;
            }
            case 1:
                hash ^= asciiToLowerCase(UNSAFE.getByte(bytes, baseOffset));
                hash *= HASH_PRIME;
                break;
            default:
                break;
            }
            return (int) (hash ^ (hash >>> 32));
        }

        @Override
        public int hashCodeAsBytes(char[] bytes, int startPos, int endPos) {
            long hash = HASH_OFFSET;
            final int len = endPos - startPos;
            final int remainingBytes = len & 7;
            if (len > 7) { // This is to reduce overhead for small input sizes.
                final long rend = remainingBytes + startPos;
                long i = len - 8 + startPos;
                do {
                    long v = UNSAFE.getLong(bytes, CHAR_ARRAY_BASE_OFFSET + i * CHAR_ARRAY_INDEX_SCALE);
                    long v2 = UNSAFE.getLong(bytes, CHAR_ARRAY_BASE_OFFSET + (i + 4) * CHAR_ARRAY_INDEX_SCALE);
                    hash ^= asciiToLowerCase((byte) v) | charOffset8(v) | charOffset16(v) | charOffset24(v) |
                            (((long) asciiToLowerCase((byte) v2) | charOffset8(v2) |
                                    charOffset16(v2) | charOffset24(v2)) << 32);
                    hash *= HASH_PRIME;
                    i -= 8;
                } while (i >= rend);
            }
            // Account for each case of remaining bytes independently. Conditional statements and decrementing a
            // variable to share code proved to generate measurable amount of overhead for small inputs which are
            // expected to be common (i.e. Header Names).
            switch (remainingBytes) {
            case 7: {
                long v = UNSAFE.getLong(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 3) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= asciiToLowerCase((byte) v) | charOffset8(v) | charOffset16(v) | charOffset24(v);
                hash *= HASH_PRIME;
                int v2 = UNSAFE.getInt(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 1) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= asciiToLowerCase((byte) v2) | charOffset8(v2);
                hash *= HASH_PRIME;
                hash ^= asciiToLowerCase(UNSAFE.getByte(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE));
                hash *= HASH_PRIME;
                break;
            }
            case 6: {
                long v = UNSAFE.getLong(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 2) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= asciiToLowerCase((byte) v) | charOffset8(v) | charOffset16(v) | charOffset24(v);
                hash *= HASH_PRIME;
                int v2 = UNSAFE.getInt(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash ^= asciiToLowerCase((byte) v2) | charOffset8(v2);
                hash *= HASH_PRIME;
                break;
            }
            case 5: {
                long v = UNSAFE.getLong(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 1) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= asciiToLowerCase((byte) v) | charOffset8(v) | charOffset16(v) | charOffset24(v);
                hash *= HASH_PRIME;
                hash ^= asciiToLowerCase(UNSAFE.getByte(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE));
                hash *= HASH_PRIME;
                break;
            }
            case 4: {
                long v = UNSAFE.getLong(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash ^= asciiToLowerCase((byte) v) | charOffset8(v) | charOffset16(v) | charOffset24(v);
                hash *= HASH_PRIME;
                break;
            }
            case 3: {
                int v = UNSAFE.getInt(bytes,
                        CHAR_ARRAY_BASE_OFFSET + (startPos + 1) * CHAR_ARRAY_INDEX_SCALE);
                hash ^= asciiToLowerCase((byte) v) | charOffset8(v);
                hash *= HASH_PRIME;
                hash ^= asciiToLowerCase(UNSAFE.getByte(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE));
                hash *= HASH_PRIME;
                break;
            }
            case 2: {
                int v = UNSAFE.getInt(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE);
                hash ^= asciiToLowerCase((byte) v) | charOffset8(v);
                hash *= HASH_PRIME;
                break;
            }
            case 1:
                hash ^= asciiToLowerCase(UNSAFE.getByte(bytes,
                        CHAR_ARRAY_BASE_OFFSET + startPos * CHAR_ARRAY_INDEX_SCALE));
                hash *= HASH_PRIME;
                break;
            default:
                break;
            }
            return (int) (hash ^ (hash >>> 32));
        }

        @Override
        protected int hashCodeAsBytesDirect(CharSequence data, int startPos, int endPos) {
            long hash = HASH_OFFSET;
            final int len = endPos - startPos;
            int remainingBytes = len & 7;
            if (len > 7) { // This is to reduce overhead for small input sizes.
                final int rend = remainingBytes + startPos;
                int i = len - 8 + startPos;
                do {
                    hash ^= asciiToLowerCase((byte) data.charAt(i)) |
                            (asciiToLowerCase((byte) data.charAt(i + 1)) << 8) |
                            (asciiToLowerCase((byte) data.charAt(i + 2)) << 16) |
                            (asciiToLowerCase((byte) data.charAt(i + 3)) << 24) |
                            ((long) asciiToLowerCase((byte) data.charAt(i + 4)) << 32) |
                            ((long) asciiToLowerCase((byte) data.charAt(i + 5)) << 40) |
                            ((long) asciiToLowerCase((byte) data.charAt(i + 6)) << 48) |
                            ((long) asciiToLowerCase((byte) data.charAt(i + 7)) << 56);
                    hash *= HASH_PRIME;
                    i -= 8;
                } while (i >= rend);
            }
            if (remainingBytes >= 4) {
                remainingBytes -= 4;
                final int i = startPos + remainingBytes;
                hash ^= asciiToLowerCase((byte) data.charAt(i)) |
                        (asciiToLowerCase((byte) data.charAt(i + 1)) << 8) |
                        (asciiToLowerCase((byte) data.charAt(i + 2)) << 16) |
                        (asciiToLowerCase((byte) data.charAt(i + 3)) << 24);
                hash *= HASH_PRIME;
            }
            switch (remainingBytes) {
            case 3:
                hash ^= asciiToLowerCase((byte) data.charAt(startPos + 1)) |
                (asciiToLowerCase((byte) data.charAt(startPos + 2)) << 8);
                hash *= HASH_PRIME;
                hash ^= asciiToLowerCase(data.charAt(startPos));
                hash *= HASH_PRIME;
                break;
            case 2:
                hash ^= asciiToLowerCase((byte) data.charAt(startPos)) |
                (asciiToLowerCase((byte) data.charAt(startPos + 1)) << 8);
                hash *= HASH_PRIME;
                break;
            case 1:
                hash ^= asciiToLowerCase(data.charAt(startPos));
                hash *= HASH_PRIME;
                break;
            default:
                break;
            }
            return (int) (hash ^ (hash >>> 32));
        }

        // The logic below is pretty low level but has been shown to be beneficial in benchmarking.
        // It essentially avoids an extra shift per byte and casting to/from long/byte/int.
        private static final long BYTE_8_MASK = 0XFFL << 8;
        private static final long BYTE_8_A = ((long) 'A') << 8;
        private static final long BYTE_8_Z = ((long) 'Z') << 8;
        private static final long BYTE_8_LOWER_CASE = (((long) 'a') << 8) - BYTE_8_A;
        private static final long BYTE_16_MASK = 0XFFL << 16;
        private static final long BYTE_16_A = ((long) 'A') << 16;
        private static final long BYTE_16_Z = ((long) 'Z') << 16;
        private static final long BYTE_16_LOWER_CASE = (((long) 'a') << 16) - BYTE_16_A;
        private static final long BYTE_24_MASK = 0XFFL << 24;
        private static final long BYTE_24_A = ((long) 'A') << 24;
        private static final long BYTE_24_Z = ((long) 'Z') << 24;
        private static final long BYTE_24_LOWER_CASE = (((long) 'a') << 24) - BYTE_24_A;
        private static final long BYTE_32_MASK = 0XFFL << 32;
        private static final long BYTE_32_A = ((long) 'A') << 32;
        private static final long BYTE_32_Z = ((long) 'Z') << 32;
        private static final long BYTE_32_LOWER_CASE = (((long) 'a') << 32) - BYTE_32_A;
        private static final long BYTE_40_MASK = 0XFFL << 40;
        private static final long BYTE_40_A = ((long) 'A') << 40;
        private static final long BYTE_40_Z = ((long) 'Z') << 40;
        private static final long BYTE_40_LOWER_CASE = (((long) 'a') << 40) - BYTE_40_A;
        private static final long BYTE_48_MASK = 0XFFL << 48;
        private static final long BYTE_48_A = ((long) 'A') << 48;
        private static final long BYTE_48_Z = ((long) 'Z') << 48;
        private static final long BYTE_48_LOWER_CASE = (((long) 'a') << 48) - BYTE_48_A;
        private static final long BYTE_56_MASK = 0XFFL << 56;
        private static final long BYTE_56_A = ((long) 'A') << 56;
        private static final long BYTE_56_Z = ((long) 'Z') << 56;
        private static final long BYTE_56_LOWER_CASE = (((long) 'a') << 56) - BYTE_56_A;
        private static final long CHAR_8_MASK = 0xFFL << 16;
        private static final long CHAR_8_A = ((long) 'A') << 16;
        private static final long CHAR_8_Z = ((long) 'Z') << 16;
        private static final long CHAR_8_LOWER_CASE = (((long) 'a') << 16) - CHAR_8_A;
        private static final long CHAR_16_MASK = 0xFFL << 32;
        private static final long CHAR_16_A = ((long) 'A') << 32;
        private static final long CHAR_16_Z = ((long) 'Z') << 32;
        private static final long CHAR_16_LOWER_CASE = (((long) 'a') << 32) - CHAR_16_A;
        private static final long CHAR_24_MASK = 0xFFL << 48;
        private static final long CHAR_24_A = ((long) 'A') << 48;
        private static final long CHAR_24_Z = ((long) 'Z') << 48;
        private static final long CHAR_24_LOWER_CASE = (((long) 'a') << 48) - CHAR_24_A;

        private long charOffset8(long raw) {
            long masked = raw & CHAR_8_MASK;
            return ((CHAR_8_A <= masked && masked <= CHAR_8_Z) ? masked + CHAR_8_LOWER_CASE : masked) >>> 8;
        }

        private long charOffset16(long raw) {
            long masked = raw & CHAR_16_MASK;
            return ((CHAR_16_A <= masked && masked <= CHAR_16_Z) ? masked + CHAR_16_LOWER_CASE : masked) >>> 16;
        }

        private long charOffset24(long raw) {
            long masked = raw & CHAR_24_MASK;
            return ((CHAR_24_A <= masked && masked <= CHAR_24_Z) ? masked + CHAR_24_LOWER_CASE : masked) >>> 24;
        }

        private long byteOffset8(long raw) {
            long masked = raw & BYTE_8_MASK;
            return (BYTE_8_A <= masked && masked <= BYTE_8_Z) ? masked + BYTE_8_LOWER_CASE : masked;
        }

        private long byteOffset16(long raw) {
            long masked = raw & BYTE_16_MASK;
            return (BYTE_16_A <= masked && masked <= BYTE_16_Z) ? masked + BYTE_16_LOWER_CASE : masked;
        }

        private long byteOffset24(long raw) {
            long masked = raw & BYTE_24_MASK;
            return (BYTE_24_A <= masked && masked <= BYTE_24_Z) ? masked + BYTE_24_LOWER_CASE : masked;
        }

        private long byteOffset32(long raw) {
            long masked = raw & BYTE_32_MASK;
            return (BYTE_32_A <= masked && masked <= BYTE_32_Z) ? masked + BYTE_32_LOWER_CASE : masked;
        }

        private long byteOffset40(long raw) {
            long masked = raw & BYTE_40_MASK;
            return (BYTE_40_A <= masked && masked <= BYTE_40_Z) ? masked + BYTE_40_LOWER_CASE : masked;
        }

        private long byteOffset48(long raw) {
            long masked = raw & BYTE_48_MASK;
            return (BYTE_48_A <= masked && masked <= BYTE_48_Z) ? masked + BYTE_48_LOWER_CASE : masked;
        }

        private long byteOffset56(long raw) {
            long masked = raw & BYTE_56_MASK;
            return (BYTE_56_A <= masked && masked <= BYTE_56_Z) ? masked + BYTE_56_LOWER_CASE : masked;
        }
    }

    private PlatformDependent0() {
    }
}
