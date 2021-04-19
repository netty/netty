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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The {@link PlatformDependent} operations which requires access to {@code sun.misc.*}.
 */
@SuppressJava6Requirement(reason = "Unsafe access is guarded")
final class PlatformDependent0 {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent0.class);
    private static final long ADDRESS_FIELD_OFFSET;
    private static final long BYTE_ARRAY_BASE_OFFSET;
    private static final long INT_ARRAY_BASE_OFFSET;
    private static final long INT_ARRAY_INDEX_SCALE;
    private static final long LONG_ARRAY_BASE_OFFSET;
    private static final long LONG_ARRAY_INDEX_SCALE;
    private static final Constructor<?> DIRECT_BUFFER_CONSTRUCTOR;
    private static final Throwable EXPLICIT_NO_UNSAFE_CAUSE = explicitNoUnsafeCause0();
    private static final Method ALLOCATE_ARRAY_METHOD;
    private static final Method ALIGN_SLICE;
    private static final int JAVA_VERSION = javaVersion0();
    private static final boolean IS_ANDROID = isAndroid0();

    private static final Throwable UNSAFE_UNAVAILABILITY_CAUSE;
    private static final Object INTERNAL_UNSAFE;
    private static final boolean IS_EXPLICIT_TRY_REFLECTION_SET_ACCESSIBLE = explicitTryReflectionSetAccessible0();

    // See https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/
    // ImageInfo.java
    private static final boolean RUNNING_IN_NATIVE_IMAGE = SystemPropertyUtil.contains(
            "org.graalvm.nativeimage.imagecode");

    static final Unsafe UNSAFE;

    // constants borrowed from murmur3
    static final int HASH_CODE_ASCII_SEED = 0xc2b2ae35;
    static final int HASH_CODE_C1 = 0xcc9e2d51;
    static final int HASH_CODE_C2 = 0x1b873593;

    /**
     * Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to allow safepoint polling
     * during a large copy.
     */
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    private static final boolean UNALIGNED;

    static {
        final ByteBuffer direct;
        Field addressField = null;
        Method allocateArrayMethod = null;
        Throwable unsafeUnavailabilityCause = null;
        Unsafe unsafe;
        Object internalUnsafe = null;

        if ((unsafeUnavailabilityCause = EXPLICIT_NO_UNSAFE_CAUSE) != null) {
            direct = null;
            addressField = null;
            unsafe = null;
            internalUnsafe = null;
        } else {
            direct = ByteBuffer.allocateDirect(1);

            // attempt to access field Unsafe#theUnsafe
            final Object maybeUnsafe = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                        // We always want to try using Unsafe as the access still works on java9 as well and
                        // we need it for out native-transports and many optimizations.
                        Throwable cause = ReflectionUtil.trySetAccessible(unsafeField, false);
                        if (cause != null) {
                            return cause;
                        }
                        // the unsafe instance
                        return unsafeField.get(null);
                    } catch (NoSuchFieldException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    } catch (IllegalAccessException e) {
                        return e;
                    } catch (NoClassDefFoundError e) {
                        // Also catch NoClassDefFoundError in case someone uses for example OSGI and it made
                        // Unsafe unloadable.
                        return e;
                    }
                }
            });

            // the conditional check here can not be replaced with checking that maybeUnsafe
            // is an instanceof Unsafe and reversing the if and else blocks; this is because an
            // instanceof check against Unsafe will trigger a class load and we might not have
            // the runtime permission accessClassInPackage.sun.misc
            if (maybeUnsafe instanceof Throwable) {
                unsafe = null;
                unsafeUnavailabilityCause = (Throwable) maybeUnsafe;
                if (logger.isTraceEnabled()) {
                    logger.debug("sun.misc.Unsafe.theUnsafe: unavailable", (Throwable) maybeUnsafe);
                } else {
                    logger.debug("sun.misc.Unsafe.theUnsafe: unavailable: {}", ((Throwable) maybeUnsafe).getMessage());
                }
            } else {
                unsafe = (Unsafe) maybeUnsafe;
                logger.debug("sun.misc.Unsafe.theUnsafe: available");
            }

            // ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK
            // https://github.com/netty/netty/issues/1061
            // https://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
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
                    unsafeUnavailabilityCause = (Throwable) maybeException;
                    if (logger.isTraceEnabled()) {
                        logger.debug("sun.misc.Unsafe.copyMemory: unavailable", (Throwable) maybeException);
                    } else {
                        logger.debug("sun.misc.Unsafe.copyMemory: unavailable: {}",
                                ((Throwable) maybeException).getMessage());
                    }
                }
            }

            if (unsafe != null) {
                final Unsafe finalUnsafe = unsafe;

                // attempt to access field Buffer#address
                final Object maybeAddressField = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            final Field field = Buffer.class.getDeclaredField("address");
                            // Use Unsafe to read value of the address field. This way it will not fail on JDK9+ which
                            // will forbid changing the access level via reflection.
                            final long offset = finalUnsafe.objectFieldOffset(field);
                            final long address = finalUnsafe.getLong(direct, offset);

                            // if direct really is a direct buffer, address will be non-zero
                            if (address == 0) {
                                return null;
                            }
                            return field;
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
                    unsafeUnavailabilityCause = (Throwable) maybeAddressField;
                    if (logger.isTraceEnabled()) {
                        logger.debug("java.nio.Buffer.address: unavailable", (Throwable) maybeAddressField);
                    } else {
                        logger.debug("java.nio.Buffer.address: unavailable: {}",
                                ((Throwable) maybeAddressField).getMessage());
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
            DIRECT_BUFFER_CONSTRUCTOR = null;
            ALLOCATE_ARRAY_METHOD = null;
        } else {
            Constructor<?> directBufferConstructor;
            long address = -1;
            try {
                final Object maybeDirectBufferConstructor =
                        AccessController.doPrivileged(new PrivilegedAction<Object>() {
                            @Override
                            public Object run() {
                                try {
                                    final Constructor<?> constructor =
                                            direct.getClass().getDeclaredConstructor(long.class, int.class);
                                    Throwable cause = ReflectionUtil.trySetAccessible(constructor, true);
                                    if (cause != null) {
                                        return cause;
                                    }
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
                        ((Constructor<?>) maybeDirectBufferConstructor).newInstance(address, 1);
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
                    if (logger.isTraceEnabled()) {
                        logger.debug("direct buffer constructor: unavailable",
                                (Throwable) maybeDirectBufferConstructor);
                    } else {
                        logger.debug("direct buffer constructor: unavailable: {}",
                                ((Throwable) maybeDirectBufferConstructor).getMessage());
                    }
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
            INT_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
            INT_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(int[].class);
            LONG_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(long[].class);
            LONG_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(long[].class);
            final boolean unaligned;
            Object maybeUnaligned = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        Class<?> bitsClass =
                                Class.forName("java.nio.Bits", false, getSystemClassLoader());
                        int version = javaVersion();
                        if (unsafeStaticFieldOffsetSupported() && version >= 9) {
                            // Java9/10 use all lowercase and later versions all uppercase.
                            String fieldName = version >= 11 ? "UNALIGNED" : "unaligned";
                            // On Java9 and later we try to directly access the field as we can do this without
                            // adjust the accessible levels.
                            try {
                                Field unalignedField = bitsClass.getDeclaredField(fieldName);
                                if (unalignedField.getType() == boolean.class) {
                                    long offset = UNSAFE.staticFieldOffset(unalignedField);
                                    Object object = UNSAFE.staticFieldBase(unalignedField);
                                    return UNSAFE.getBoolean(object, offset);
                                }
                                // There is something unexpected stored in the field,
                                // let us fall-back and try to use a reflective method call as last resort.
                            } catch (NoSuchFieldException ignore) {
                                // We did not find the field we expected, move on.
                            }
                        }
                        Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                        Throwable cause = ReflectionUtil.trySetAccessible(unalignedMethod, true);
                        if (cause != null) {
                            return cause;
                        }
                        return unalignedMethod.invoke(null);
                    } catch (NoSuchMethodException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    } catch (IllegalAccessException e) {
                        return e;
                    } catch (ClassNotFoundException e) {
                        return e;
                    } catch (InvocationTargetException e) {
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
                Throwable t = (Throwable) maybeUnaligned;
                if (logger.isTraceEnabled()) {
                    logger.debug("java.nio.Bits.unaligned: unavailable, {}", unaligned, t);
                } else {
                    logger.debug("java.nio.Bits.unaligned: unavailable, {}, {}", unaligned, t.getMessage());
                }
            }

            UNALIGNED = unaligned;

            if (javaVersion() >= 9) {
                Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            // Java9 has jdk.internal.misc.Unsafe and not all methods are propagated to
                            // sun.misc.Unsafe
                            Class<?> internalUnsafeClass = getClassLoader(PlatformDependent0.class)
                                    .loadClass("jdk.internal.misc.Unsafe");
                            Method method = internalUnsafeClass.getDeclaredMethod("getUnsafe");
                            return method.invoke(null);
                        } catch (Throwable e) {
                            return e;
                        }
                    }
                });
                if (!(maybeException instanceof Throwable)) {
                    internalUnsafe = maybeException;
                    final Object finalInternalUnsafe = internalUnsafe;
                    maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            try {
                                return finalInternalUnsafe.getClass().getDeclaredMethod(
                                        "allocateUninitializedArray", Class.class, int.class);
                            } catch (NoSuchMethodException e) {
                                return e;
                            } catch (SecurityException e) {
                                return e;
                            }
                        }
                    });

                    if (maybeException instanceof Method) {
                        try {
                            Method m = (Method) maybeException;
                            byte[] bytes = (byte[]) m.invoke(finalInternalUnsafe, byte.class, 8);
                            assert bytes.length == 8;
                            allocateArrayMethod = m;
                        } catch (IllegalAccessException e) {
                            maybeException = e;
                        } catch (InvocationTargetException e) {
                            maybeException = e;
                        }
                    }
                }

                if (maybeException instanceof Throwable) {
                    if (logger.isTraceEnabled()) {
                        logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable",
                                (Throwable) maybeException);
                    } else {
                        logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable: {}",
                                ((Throwable) maybeException).getMessage());
                    }
                } else {
                    logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): available");
                }
            } else {
                logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable prior to Java9");
            }
            ALLOCATE_ARRAY_METHOD = allocateArrayMethod;
        }

        if (javaVersion() > 9) {
            ALIGN_SLICE = (Method) AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        return ByteBuffer.class.getDeclaredMethod("alignedSlice", int.class);
                    } catch (Exception e) {
                        return null;
                    }
                }
            });
        } else {
            ALIGN_SLICE = null;
        }

        INTERNAL_UNSAFE = internalUnsafe;

        logger.debug("java.nio.DirectByteBuffer.<init>(long, int): {}",
                DIRECT_BUFFER_CONSTRUCTOR != null ? "available" : "unavailable");
    }

    private static boolean unsafeStaticFieldOffsetSupported() {
        return !RUNNING_IN_NATIVE_IMAGE;
    }

    static boolean isExplicitNoUnsafe() {
        return EXPLICIT_NO_UNSAFE_CAUSE != null;
    }

    private static Throwable explicitNoUnsafeCause0() {
        final boolean noUnsafe = SystemPropertyUtil.getBoolean("io.netty.noUnsafe", false);
        logger.debug("-Dio.netty.noUnsafe: {}", noUnsafe);

        if (noUnsafe) {
            logger.debug("sun.misc.Unsafe: unavailable (io.netty.noUnsafe)");
            return new UnsupportedOperationException("sun.misc.Unsafe: unavailable (io.netty.noUnsafe)");
        }

        // Legacy properties
        String unsafePropName;
        if (SystemPropertyUtil.contains("io.netty.tryUnsafe")) {
            unsafePropName = "io.netty.tryUnsafe";
        } else {
            unsafePropName = "org.jboss.netty.tryUnsafe";
        }

        if (!SystemPropertyUtil.getBoolean(unsafePropName, true)) {
            String msg = "sun.misc.Unsafe: unavailable (" + unsafePropName + ")";
            logger.debug(msg);
            return new UnsupportedOperationException(msg);
        }

        return null;
    }

    static boolean isUnaligned() {
        return UNALIGNED;
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
        // Calling malloc with capacity of 0 may return a null ptr or a memory address that can be used.
        // Just use 1 to make it safe to use in all cases:
        // See: https://pubs.opengroup.org/onlinepubs/009695399/functions/malloc.html
        return newDirectBuffer(UNSAFE.allocateMemory(Math.max(1, capacity)), capacity);
    }

    static boolean hasAlignSliceMethod() {
        return ALIGN_SLICE != null;
    }

    static ByteBuffer alignSlice(ByteBuffer buffer, int alignment) {
        try {
            return (ByteBuffer) ALIGN_SLICE.invoke(buffer, alignment);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw new Error(e);
        }
    }

    static boolean hasAllocateArrayMethod() {
        return ALLOCATE_ARRAY_METHOD != null;
    }

    static byte[] allocateUninitializedArray(int size) {
        try {
            return (byte[]) ALLOCATE_ARRAY_METHOD.invoke(INTERNAL_UNSAFE, byte.class, size);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw new Error(e);
        }
    }

    static ByteBuffer newDirectBuffer(long address, int capacity) {
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

    static long directBufferAddress(ByteBuffer buffer) {
        return getLong(buffer, ADDRESS_FIELD_OFFSET);
    }

    static long byteArrayBaseOffset() {
        return BYTE_ARRAY_BASE_OFFSET;
    }

    static Object getObject(Object object, long fieldOffset) {
        return UNSAFE.getObject(object, fieldOffset);
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

    static void putIntOrdered(long adddress, int newValue) {
        UNSAFE.putOrderedInt(null, adddress, newValue);
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

    static void putInt(byte[] data, int index, int value) {
        UNSAFE.putInt(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putLong(byte[] data, int index, long value) {
        UNSAFE.putLong(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putObject(Object o, long offset, Object x) {
        UNSAFE.putObject(o, offset, x);
    }

    static void copyMemory(long srcAddr, long dstAddr, long length) {
        // Manual safe-point polling is only needed prior Java9:
        // See https://bugs.openjdk.java.net/browse/JDK-8149596
        if (javaVersion() <= 8) {
            copyMemoryWithSafePointPolling(srcAddr, dstAddr, length);
        } else {
            UNSAFE.copyMemory(srcAddr, dstAddr, length);
        }
    }

    private static void copyMemoryWithSafePointPolling(long srcAddr, long dstAddr, long length) {
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            UNSAFE.copyMemory(srcAddr, dstAddr, size);
            length -= size;
            srcAddr += size;
            dstAddr += size;
        }
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        // Manual safe-point polling is only needed prior Java9:
        // See https://bugs.openjdk.java.net/browse/JDK-8149596
        if (javaVersion() <= 8) {
            copyMemoryWithSafePointPolling(src, srcOffset, dst, dstOffset, length);
        } else {
            UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, length);
        }
    }

    private static void copyMemoryWithSafePointPolling(
            Object src, long srcOffset, Object dst, long dstOffset, long length) {
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
        return SystemPropertyUtil.getBoolean("io.netty.tryReflectionSetAccessible", javaVersion() < 9);
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
            majorVersion = 6;
        } else {
            majorVersion = majorVersionFromJavaSpecificationVersion();
        }

        logger.debug("Java version: {}", majorVersion);

        return majorVersion;
    }

    // Package-private for testing only
    static int majorVersionFromJavaSpecificationVersion() {
        return majorVersion(SystemPropertyUtil.get("java.specification.version", "1.6"));
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

    private PlatformDependent0() {
    }
}
