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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.invoke.MethodType.methodType;

/**
 * The {@link PlatformDependent} operations which requires access to {@code sun.misc.*}.
 */
final class PlatformDependent0 {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent0.class);

    /**
     * Limits the number of bytes to copy per {@link sun.misc.Unsafe#copyMemory(long, long, long)} to allow
     * safepoint polling during a large copy.
     */
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    private static final long ADDRESS_FIELD_OFFSET;
    private static final long BYTE_ARRAY_BASE_OFFSET;
    private static final long INT_ARRAY_BASE_OFFSET;
    private static final long INT_ARRAY_INDEX_SCALE;
    private static final long LONG_ARRAY_BASE_OFFSET;
    private static final long LONG_ARRAY_INDEX_SCALE;

    private static final MethodHandle DIRECT_BUFFER_CONSTRUCTOR;
    private static final MethodHandle ALLOCATE_ARRAY_METHOD;
    private static final MethodHandle ALIGN_SLICE;

    private static final Throwable UNSAFE_UNAVAILABILITY_CAUSE;

    // See https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/
    // ImageInfo.java
    private static final boolean RUNNING_IN_NATIVE_IMAGE = SystemPropertyUtil.contains(
            "org.graalvm.nativeimage.imagecode");

    private static final boolean IS_EXPLICIT_TRY_REFLECTION_SET_ACCESSIBLE = explicitTryReflectionSetAccessible0();

    // Package-private for testing.
    static final MethodHandle IS_VIRTUAL_THREAD_METHOD_HANDLE = getIsVirtualThreadMethodHandle();

    // constants borrowed from murmur3
    static final int HASH_CODE_ASCII_SEED = 0xc2b2ae35;
    static final int HASH_CODE_C1 = 0xcc9e2d51;
    static final int HASH_CODE_C2 = 0x1b873593;

    private static final boolean UNALIGNED;
    private static final long BITS_MAX_DIRECT_MEMORY;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        final ByteBuffer direct;
        Field addressField = null;
        MethodHandle allocateArrayMethod = null;
        Throwable unsafeUnavailabilityCause = null;
        long byteArrayBaseOffset = -1;
        long intArrayBaseOffset = -1;
        long longArrayBaseOffset = -1;
        long intArrayIndexScale = -1;
        long longArrayIndexScale = -1;

        if (!SunMiscUnsafeAccess.isAvailabile()) {
            direct = null;
            unsafeUnavailabilityCause = SunMiscUnsafeAccess.unavailabilityCause();
            assert unsafeUnavailabilityCause != null;
        } else {
            direct = ByteBuffer.allocateDirect(1);
            // attempt to access field Buffer#address
            final Object maybeAddressField = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        final Field field = Buffer.class.getDeclaredField("address");
                        // Use Unsafe to read value of the address field. This way it will not fail on JDK9+ which
                        // will forbid changing the access level via reflection.
                        final long offset = SunMiscUnsafeAccess.objectFieldOffset(field);
                        final long address = SunMiscUnsafeAccess.getLong(direct, offset);

                        // if direct really is a direct buffer, address will be non-zero
                        if (address == 0) {
                            return null;
                        }
                        return field;
                    } catch (Throwable e) {
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
            }

            if (unsafeUnavailabilityCause == null) {
                try {
                    long byteArrayIndexScale = SunMiscUnsafeAccess.arrayIndexScale(byte[].class);
                    if (byteArrayIndexScale != 1) {
                        logger.debug(
                                "unsafe.arrayIndexScale is {} (expected: 1). Not using unsafe.", byteArrayIndexScale);
                        unsafeUnavailabilityCause = new UnsupportedOperationException(
                                "Unexpected unsafe.arrayIndexScale");
                    }
                } catch (Throwable cause) {
                    logger.debug("unsafe.arrayIndexScale did throw", cause);
                    unsafeUnavailabilityCause = new UnsupportedOperationException(
                            "Unexpected unsafe.arrayIndexScale", cause);
                }
            }

            if (unsafeUnavailabilityCause == null) {
                try {
                    byteArrayBaseOffset = SunMiscUnsafeAccess.arrayBaseOffset(byte[].class);
                    intArrayBaseOffset = SunMiscUnsafeAccess.arrayBaseOffset(int[].class);
                    longArrayBaseOffset = SunMiscUnsafeAccess.arrayBaseOffset(long[].class);
                    intArrayIndexScale = SunMiscUnsafeAccess.arrayIndexScale(int[].class);
                    longArrayIndexScale = SunMiscUnsafeAccess.arrayIndexScale(long[].class);
                } catch (Throwable cause) {
                    byteArrayBaseOffset = 1;
                    intArrayBaseOffset = -1;
                    intArrayIndexScale = -1;
                    longArrayBaseOffset = -1;
                    longArrayIndexScale = -1;
                    unsafeUnavailabilityCause = cause;
                }
            }
        }
        UNSAFE_UNAVAILABILITY_CAUSE = unsafeUnavailabilityCause;

        if (unsafeUnavailabilityCause != null) {
            ADDRESS_FIELD_OFFSET = -1;
            UNALIGNED = false;
            BITS_MAX_DIRECT_MEMORY = -1;
            DIRECT_BUFFER_CONSTRUCTOR = null;
            ALLOCATE_ARRAY_METHOD = null;
        } else {
            MethodHandle directBufferConstructor;
            long address = -1;
            try {
                final Object maybeDirectBufferConstructor =
                        AccessController.doPrivileged(new PrivilegedAction<Object>() {
                            @Override
                            public Object run() {
                                try {
                                    Class<? extends ByteBuffer> directClass = direct.getClass();
                                    final Constructor<?> constructor = JavaVersion.javaVersion() >= 21 ?
                                            directClass.getDeclaredConstructor(long.class, long.class) :
                                            directClass.getDeclaredConstructor(long.class, int.class);
                                    Throwable cause = ReflectionUtil.trySetAccessible(constructor, true);
                                    if (cause != null) {
                                        return cause;
                                    }
                                    return lookup.unreflectConstructor(constructor)
                                            .asType(methodType(ByteBuffer.class, long.class, int.class));
                                } catch (Throwable e) {
                                    return e;
                                }
                            }
                        });

                if (maybeDirectBufferConstructor instanceof MethodHandle) {
                    // try to use the constructor now
                    try {
                        address = SunMiscUnsafeAccess.allocateMemory(1);
                        MethodHandle constructor = (MethodHandle) maybeDirectBufferConstructor;
                        ByteBuffer ignore = (ByteBuffer) constructor.invokeExact(address, 1);
                        directBufferConstructor = constructor;
                        logger.debug("direct buffer constructor: available");
                    } catch (Throwable e) {
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
                    try {
                        SunMiscUnsafeAccess.freeMemory(address);
                    } catch (Throwable ignore) {
                        // ignore
                    }
                }
            }
            DIRECT_BUFFER_CONSTRUCTOR = directBufferConstructor;
            ADDRESS_FIELD_OFFSET = SunMiscUnsafeAccess.objectFieldOffset(addressField);

            final boolean unaligned;

            // using a known type to avoid loading new classes
            final AtomicLong maybeMaxMemory = new AtomicLong(-1);
            Object maybeUnaligned = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        Class<?> bitsClass =
                                Class.forName("java.nio.Bits", false, getSystemClassLoader());
                        int version = JavaVersion.javaVersion();
                        if (unsafeStaticFieldOffsetSupported() && version >= 9) {
                            // Java9/10 use all lowercase and later versions all uppercase.
                            String fieldName = version >= 11? "MAX_MEMORY" : "maxMemory";
                            // On Java9 and later we try to directly access the field as we can do this without
                            // adjust the accessible levels.
                            try {
                                Field maxMemoryField = bitsClass.getDeclaredField(fieldName);
                                if (maxMemoryField.getType() == long.class) {

                                    long offset = SunMiscUnsafeAccess.staticFieldOffset(maxMemoryField);
                                    Object object = SunMiscUnsafeAccess.staticFieldBase(maxMemoryField);
                                    maybeMaxMemory.lazySet(SunMiscUnsafeAccess.getLong(object, offset));
                                }
                            } catch (Throwable ignore) {
                                // ignore if can't access
                            }
                            fieldName = version >= 11? "UNALIGNED" : "unaligned";
                            try {
                                Field unalignedField = bitsClass.getDeclaredField(fieldName);
                                if (unalignedField.getType() == boolean.class) {
                                    long offset = SunMiscUnsafeAccess.staticFieldOffset(unalignedField);
                                    Object object = SunMiscUnsafeAccess.staticFieldBase(unalignedField);
                                    return SunMiscUnsafeAccess.getBoolean(object, offset);
                                }
                                // There is something unexpected stored in the field,
                                // let us fall-back and try to use a reflective method call as last resort.
                            } catch (Throwable ignore) {
                                // We did not find the field we expected, move on.
                            }
                        }
                        Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                        Throwable cause = ReflectionUtil.trySetAccessible(unalignedMethod, true);
                        if (cause != null) {
                            return cause;
                        }
                        return unalignedMethod.invoke(null);
                    } catch (NoSuchMethodException | SecurityException | IllegalAccessException |
                             ClassNotFoundException | InvocationTargetException e) {
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
            BITS_MAX_DIRECT_MEMORY = maybeMaxMemory.get() >= 0? maybeMaxMemory.get() : -1;

            if (JavaVersion.javaVersion() >= 9) {
                Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            // Java9 has jdk.internal.misc.Unsafe and not all methods are propagated to
                            // sun.misc.Unsafe
                            Class<?> cls = getClassLoader(PlatformDependent0.class)
                                    .loadClass("jdk.internal.misc.Unsafe");
                            return lookup.findStatic(cls, "getUnsafe", methodType(cls)).invoke();
                        } catch (Throwable e) {
                            return e;
                        }
                    }
                });
                if (!(maybeException instanceof Throwable)) {
                    final Object finalInternalUnsafe = maybeException;
                    maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            try {
                                Class<?> finalInternalUnsafeClass = finalInternalUnsafe.getClass();
                                return lookup.findVirtual(
                                        finalInternalUnsafeClass,
                                        "allocateUninitializedArray",
                                        methodType(Object.class, Class.class, int.class));
                            } catch (Throwable e) {
                                return e;
                            }
                        }
                    });

                    if (maybeException instanceof MethodHandle) {
                        try {
                            MethodHandle m = (MethodHandle) maybeException;
                            m = m.bindTo(finalInternalUnsafe);
                            byte[] bytes = (byte[]) (Object) m.invokeExact(byte.class, 8);
                            assert bytes.length == 8;
                            allocateArrayMethod = m;
                        } catch (Throwable e) {
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

        if (JavaVersion.javaVersion() > 9) {
            ALIGN_SLICE = (MethodHandle) AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        return MethodHandles.publicLookup().findVirtual(
                                ByteBuffer.class, "alignedSlice", methodType(ByteBuffer.class, int.class));
                    } catch (Throwable e) {
                        return null;
                    }
                }
            });
        } else {
            ALIGN_SLICE = null;
        }
        BYTE_ARRAY_BASE_OFFSET = byteArrayBaseOffset;
        INT_ARRAY_BASE_OFFSET = intArrayBaseOffset;
        INT_ARRAY_INDEX_SCALE = intArrayIndexScale;
        LONG_ARRAY_BASE_OFFSET = longArrayBaseOffset;
        LONG_ARRAY_INDEX_SCALE = longArrayIndexScale;

        logger.debug("java.nio.DirectByteBuffer.<init>(long, {int,long}): {}",
                DIRECT_BUFFER_CONSTRUCTOR != null ? "available" : "unavailable");
    }

    private static MethodHandle getIsVirtualThreadMethodHandle() {
        try {
            MethodHandle methodHandle = MethodHandles.publicLookup().findVirtual(Thread.class, "isVirtual",
                    methodType(boolean.class));
            // Call once to make sure the invocation works.
            boolean isVirtual = (boolean) methodHandle.invokeExact(Thread.currentThread());
            return methodHandle;
        } catch (Throwable e) {
            if (logger.isTraceEnabled()) {
                logger.debug("Thread.isVirtual() is not available: ", e);
            } else {
                logger.debug("Thread.isVirtual() is not available: ", e.getMessage());
            }
            return null;
        }
    }

    /**
     * @param thread The thread to be checked.
     * @return {@code true} if this {@link Thread} is a virtual thread, {@code false} otherwise.
     */
    static boolean isVirtualThread(Thread thread) {
        if (thread == null || IS_VIRTUAL_THREAD_METHOD_HANDLE == null) {
            return false;
        }
        try {
            return (boolean) IS_VIRTUAL_THREAD_METHOD_HANDLE.invokeExact(thread);
        } catch (Throwable t) {
            // Should not happen.
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new Error(t);
        }
    }

    private static boolean unsafeStaticFieldOffsetSupported() {
        return !RUNNING_IN_NATIVE_IMAGE;
    }

    static boolean isExplicitNoUnsafe() {
        return SunMiscUnsafeAccess.isExplicitNoUnsafe();
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
        return SunMiscUnsafeAccess.isAvailabile() && UNSAFE_UNAVAILABILITY_CAUSE == null;
    }

    static Throwable getUnsafeUnavailabilityCause() {
        return UNSAFE_UNAVAILABILITY_CAUSE;
    }

    static boolean unalignedAccess() {
        return UNALIGNED;
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> void throwException(Throwable t) throws E {
        ObjectUtil.checkNotNull(t, "t");
        throw (E) t;
    }

    static boolean hasDirectBufferNoCleanerConstructor() {
        return DIRECT_BUFFER_CONSTRUCTOR != null;
    }

    static ByteBuffer reallocateDirectNoCleaner(ByteBuffer buffer, int capacity) {
        try {
            return newDirectBuffer(reallocateMemory(directBufferAddress(buffer), capacity), capacity);
        } catch (Throwable e) {
            rethrowIfPossible(e);
            throw new LinkageError("Unsafe.allocateUninitializedArray not available", e);
        }
    }

    static ByteBuffer allocateDirectNoCleaner(int capacity) {
        // Calling malloc with capacity of 0 may return a null ptr or a memory address that can be used.
        // Just use 1 to make it safe to use in all cases:
        // See: https://pubs.opengroup.org/onlinepubs/009695399/functions/malloc.html
        return newDirectBuffer(allocateMemory(Math.max(1, capacity)), capacity);
    }

    static boolean hasAlignSliceMethod() {
        return ALIGN_SLICE != null;
    }

    static ByteBuffer alignSlice(ByteBuffer buffer, int alignment) {
        try {
            return (ByteBuffer) ALIGN_SLICE.invokeExact(buffer, alignment);
        } catch (Throwable e) {
            rethrowIfPossible(e);
            throw new LinkageError("ByteBuffer.alignedSlice not available", e);
        }
    }

    static boolean hasAllocateArrayMethod() {
        return ALLOCATE_ARRAY_METHOD != null;
    }

    static byte[] allocateUninitializedArray(int size) {
        try {
            return (byte[]) (Object) ALLOCATE_ARRAY_METHOD.invokeExact(byte.class, size);
        } catch (Throwable e) {
            rethrowIfPossible(e);
            throw new LinkageError("Unsafe.allocateUninitializedArray not available", e);
        }
    }

    static ByteBuffer newDirectBuffer(long address, int capacity) {
        ObjectUtil.checkPositiveOrZero(capacity, "capacity");

        try {
            return (ByteBuffer) DIRECT_BUFFER_CONSTRUCTOR.invokeExact(address, capacity);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new LinkageError("DirectByteBuffer constructor not available", cause);
        }
    }

    private static void rethrowIfPossible(Throwable cause) {
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
    }

    static long directBufferAddress(ByteBuffer buffer) {
        return getLong(buffer, ADDRESS_FIELD_OFFSET);
    }

    static long byteArrayBaseOffset() {
        return BYTE_ARRAY_BASE_OFFSET;
    }

    static Object getObject(Object object, long fieldOffset) {
        return SunMiscUnsafeAccess.getObject(object, fieldOffset);
    }

    static int getInt(Object object, long fieldOffset) {
        return SunMiscUnsafeAccess.getInt(object, fieldOffset);
    }

    static short getShort(Object object, long fieldOffset) {
        return SunMiscUnsafeAccess.getShort(object, fieldOffset);
    }

    static void safeConstructPutInt(Object object, long fieldOffset, int value) {
        SunMiscUnsafeAccess.putInt(object, fieldOffset, value);
        SunMiscUnsafeAccess.storeFence();
    }

    private static long getLong(Object object, long fieldOffset) {
        return SunMiscUnsafeAccess.getLong(object, fieldOffset);
    }

    static long objectFieldOffset(Field field) {
        return SunMiscUnsafeAccess.objectFieldOffset(field);
    }

    static byte getByte(long address) {
        return SunMiscUnsafeAccess.getByte(address);
    }

    static short getShort(long address) {
        return SunMiscUnsafeAccess.getShort(address);
    }

    static int getInt(long address) {
        return SunMiscUnsafeAccess.getInt(address);
    }

    static long getLong(long address) {
        return SunMiscUnsafeAccess.getLong(address);
    }

    static byte getByte(byte[] data, int index) {
        return SunMiscUnsafeAccess.getByte(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static byte getByte(byte[] data, long index) {
        return SunMiscUnsafeAccess.getByte(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static short getShort(byte[] data, int index) {
        return SunMiscUnsafeAccess.getShort(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static int getInt(byte[] data, int index) {
        return SunMiscUnsafeAccess.getInt(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static int getInt(int[] data, long index) {
        return SunMiscUnsafeAccess.getInt(data, INT_ARRAY_BASE_OFFSET + INT_ARRAY_INDEX_SCALE * index);
    }

    static int getIntVolatile(long address) {
        return SunMiscUnsafeAccess.getIntVolatile(null, address);
    }

    static void putIntOrdered(long address, int newValue) {
        SunMiscUnsafeAccess.putIntOrdered(null, address, newValue);
    }

    static long getLong(byte[] data, int index) {
        return SunMiscUnsafeAccess.getLong(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static long getLong(long[] data, long index) {
       return SunMiscUnsafeAccess.getLong(data, LONG_ARRAY_BASE_OFFSET + LONG_ARRAY_INDEX_SCALE * index);
    }

    static void putByte(long address, byte value) {
        SunMiscUnsafeAccess.putByte(address, value);
    }

    static void putShort(long address, short value) {
        SunMiscUnsafeAccess.putShort(address, value);
    }

    static void putShortOrdered(long adddress, short newValue) {
        SunMiscUnsafeAccess.storeFence();
        SunMiscUnsafeAccess.putShort(null, adddress, newValue);
    }

    static void putInt(long address, int value) {
        SunMiscUnsafeAccess.putInt(address, value);
    }

    static void putLong(long address, long value) {
        SunMiscUnsafeAccess.putLong(address, value);
    }

    static void putByte(byte[] data, int index, byte value) {
        SunMiscUnsafeAccess.putByte(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putByte(Object data, long offset, byte value) {
        SunMiscUnsafeAccess.putByte(data, offset, value);
    }

    static void putShort(byte[] data, int index, short value) {
        SunMiscUnsafeAccess.putShort(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putInt(byte[] data, int index, int value) {
        SunMiscUnsafeAccess.putInt(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putLong(byte[] data, int index, long value) {
        SunMiscUnsafeAccess.putLong(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putObject(Object o, long offset, Object x) {
        SunMiscUnsafeAccess.putObject(o, offset, x);
    }

    static void copyMemory(long srcAddr, long dstAddr, long length) {
        // Manual safe-point polling is only needed prior Java9:
        // See https://bugs.openjdk.java.net/browse/JDK-8149596
        if (JavaVersion.javaVersion() <= 8) {
            copyMemoryWithSafePointPolling(srcAddr, dstAddr, length);
        } else {
            SunMiscUnsafeAccess.copyMemory(srcAddr, dstAddr, length);
        }
    }

    private static void copyMemoryWithSafePointPolling(long srcAddr, long dstAddr, long length) {
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            SunMiscUnsafeAccess.copyMemory(srcAddr, dstAddr, size);
            length -= size;
            srcAddr += size;
            dstAddr += size;
        }
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        // Manual safe-point polling is only needed prior Java9:
        // See https://bugs.openjdk.java.net/browse/JDK-8149596
        if (JavaVersion.javaVersion() <= 8) {
            copyMemoryWithSafePointPolling(src, srcOffset, dst, dstOffset, length);
        } else {
            SunMiscUnsafeAccess.copyMemory(src, srcOffset, dst, dstOffset, length);
        }
    }

    private static void copyMemoryWithSafePointPolling(
            Object src, long srcOffset, Object dst, long dstOffset, long length) {
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            SunMiscUnsafeAccess.copyMemory(src, srcOffset, dst, dstOffset, size);
            length -= size;
            srcOffset += size;
            dstOffset += size;
        }
    }

    static void setMemory(long address, long bytes, byte value) {
        SunMiscUnsafeAccess.setMemory(address, bytes, value);
    }

    static void setMemory(Object o, long offset, long bytes, byte value) {
        SunMiscUnsafeAccess.setMemory(o, offset, bytes, value);
    }

    static boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        int remainingBytes = length & 7;
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long diff = startPos2 - startPos1;
        if (length >= 8) {
            final long end = baseOffset1 + remainingBytes;
            for (long i = baseOffset1 - 8 + length; i >= end; i -= 8) {
                if (SunMiscUnsafeAccess.getLong(bytes1, i) != SunMiscUnsafeAccess.getLong(bytes2, i + diff)) {
                    return false;
                }
            }
        }
        if (remainingBytes >= 4) {
            remainingBytes -= 4;
            long pos = baseOffset1 + remainingBytes;
            if (SunMiscUnsafeAccess.getInt(bytes1, pos) != SunMiscUnsafeAccess.getInt(bytes2, pos + diff)) {
                return false;
            }
        }
        final long baseOffset2 = baseOffset1 + diff;
        if (remainingBytes >= 2) {
            return SunMiscUnsafeAccess.getChar(bytes1, baseOffset1) ==
                    SunMiscUnsafeAccess.getChar(bytes2, baseOffset2) &&
                    (remainingBytes == 2 || SunMiscUnsafeAccess.getByte(bytes1, baseOffset1 + 2) ==
                            SunMiscUnsafeAccess.getByte(bytes2, baseOffset2 + 2));
        }
        return remainingBytes == 0 ||
                SunMiscUnsafeAccess.getByte(bytes1, baseOffset1) == SunMiscUnsafeAccess.getByte(bytes2, baseOffset2);
    }

    static int equalsConstantTime(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        long result = 0;
        long remainingBytes = length & 7;
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long end = baseOffset1 + remainingBytes;
        final long diff = startPos2 - startPos1;
        for (long i = baseOffset1 - 8 + length; i >= end; i -= 8) {
            result |= SunMiscUnsafeAccess.getLong(bytes1, i) ^ SunMiscUnsafeAccess.getLong(bytes2, i + diff);
        }
        if (remainingBytes >= 4) {
            result |= SunMiscUnsafeAccess.getInt(bytes1, baseOffset1) ^
                    SunMiscUnsafeAccess.getInt(bytes2, baseOffset1 + diff);
            remainingBytes -= 4;
        }
        if (remainingBytes >= 2) {
            long pos = end - remainingBytes;
            result |= SunMiscUnsafeAccess.getChar(bytes1, pos) ^ SunMiscUnsafeAccess.getChar(bytes2, pos + diff);
            remainingBytes -= 2;
        }
        if (remainingBytes == 1) {
            long pos = end - 1;
            result |= SunMiscUnsafeAccess.getByte(bytes1, pos) ^ SunMiscUnsafeAccess.getByte(bytes2, pos + diff);
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
            if (SunMiscUnsafeAccess.getLong(bytes, i) != 0) {
                return false;
            }
        }

        if (remainingBytes >= 4) {
            remainingBytes -= 4;

            if (SunMiscUnsafeAccess.getInt(bytes, baseOffset + remainingBytes) != 0) {
                return false;
            }
        }
        if (remainingBytes >= 2) {
            return SunMiscUnsafeAccess.getChar(bytes, baseOffset) == 0 &&
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
            hash = hashCodeAsciiCompute(SunMiscUnsafeAccess.getLong(bytes, i), hash);
        }
        if (remainingBytes == 0) {
            return hash;
        }
        int hcConst = HASH_CODE_C1;
        if (remainingBytes != 2 & remainingBytes != 4 & remainingBytes != 6) { // 1, 3, 5, 7
            hash = hash * HASH_CODE_C1 + hashCodeAsciiSanitize(SunMiscUnsafeAccess.getByte(bytes, baseOffset));
            hcConst = HASH_CODE_C2;
            baseOffset++;
        }
        if (remainingBytes != 1 & remainingBytes != 4 & remainingBytes != 5) { // 2, 3, 6, 7
            hash = hash * hcConst + hashCodeAsciiSanitize(SunMiscUnsafeAccess.getShort(bytes, baseOffset));
            hcConst = hcConst == HASH_CODE_C1 ? HASH_CODE_C2 : HASH_CODE_C1;
            baseOffset += 2;
        }
        if (remainingBytes >= 4) { // 4, 5, 6, 7
            return hash * hcConst + hashCodeAsciiSanitize(SunMiscUnsafeAccess.getInt(bytes, baseOffset));
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
        return SunMiscUnsafeAccess.addressSize();
    }

    static long allocateMemory(long size) {
        return SunMiscUnsafeAccess.allocateMemory(size);
    }

    static void freeMemory(long address) {
        SunMiscUnsafeAccess.freeMemory(address);
    }

    static long reallocateMemory(long address, long newSize) {
        return SunMiscUnsafeAccess.reallocateMemory(address, newSize);
    }

    private static boolean explicitTryReflectionSetAccessible0() {
        // we disable reflective access
        return SystemPropertyUtil.getBoolean("io.netty.tryReflectionSetAccessible",
                JavaVersion.javaVersion() < 9 || RUNNING_IN_NATIVE_IMAGE);
    }

    static boolean isExplicitTryReflectionSetAccessible() {
        return IS_EXPLICIT_TRY_REFLECTION_SET_ACCESSIBLE;
    }

    private PlatformDependent0() {
    }
}
