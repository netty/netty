/*
 * Copyright 2024 The Netty Project
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
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

import static java.lang.invoke.MethodType.methodType;

/**
 * Helper class which delegates to {@link sun.misc.Unsafe} via {@link MethodHandle}s.
 */
final class SunMiscUnsafeAccess {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SunMiscUnsafeAccess.class);
    private static final String SUN_MISC_UNSAFE_CLASS_NAME = "sun.misc.Unsafe";
    private static final Throwable EXPLICIT_NO_UNSAFE_CAUSE = explicitNoUnsafeCause0();
    private static final Throwable UNSAFE_UNAVAILABILITY_CAUSE;
    private static final MethodHandle UNSAFE_ALLOCATE_MEMORY_HANDLE;
    private static final MethodHandle UNSAFE_COPY_MEMORY_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_COPY_MEMORY_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_OBJECT_FIELD_OFFSET_HANDLE;
    private static final MethodHandle UNSAFE_REALLOCATE_MEMORY_HANDLE;
    private static final MethodHandle UNSAFE_FREE_MEMORY_HANDLE;
    private static final MethodHandle UNSAFE_SET_MEMORY_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_SET_MEMORY_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_GET_BYTE_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_GET_BYTE_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_GET_CHAR_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_GET_SHORT_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_GET_SHORT_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_GET_INT_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_GET_INT_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_GET_INT_VOLATILE_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_GET_LONG_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_GET_LONG_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_GET_OBJECT_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_PUT_BYTE_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_PUT_BYTE_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_PUT_SHORT_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_PUT_SHORT_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_PUT_INT_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_PUT_INT_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_PUT_ORDERED_INT_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_PUT_LONG_WITH_LONG_HANDLE;
    private static final MethodHandle UNSAFE_PUT_LONG_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_PUT_OBJECT_WITH_OBJECT_HANDLE;
    private static final MethodHandle UNSAFE_ADDRESS_SIZE_HANDLE;
    private static final MethodHandle UNSAFE_STORE_FENCE_HANDLE;
    private static final MethodHandle UNSAFE_ARRAY_BASE_OFFSET_HANDLE;
    private static final MethodHandle UNSAFE_ARRAY_INDEX_SCALE_HANDLE;
    private static final MethodHandle UNSAFE_STATIC_FIELD_OFFSET_HANDLE;
    private static final MethodHandle UNSAFE_STATIC_FIELD_BASE_HANDLE;
    private static final MethodHandle UNSAFE_GET_BOOLEAN_WITH_OBJECT_HANDLE;

    // Store as Object so we can compile with --release
    static final Object UNSAFE;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Throwable unsafeUnavailabilityCause = null;
        Object unsafe;
        MethodHandle allocateMemoryHandle = null;
        MethodHandle copyMemoryWithLongHandle = null;
        MethodHandle copyMemoryWithObjectHandle = null;
        MethodHandle objectFieldOffsetHandle = null;
        MethodHandle staticFieldOffsetHandle = null;
        MethodHandle staticFieldBaseHandle = null;
        MethodHandle arrayBaseOffsetHandle = null;
        MethodHandle arrayIndexScaleHandle = null;
        MethodHandle reallocateMemoryHandle = null;
        MethodHandle freeMemoryHandle = null;
        MethodHandle setMemoryWithLongHandle = null;
        MethodHandle setMemoryWithObjectHandle = null;
        MethodHandle getBooleanWithObjectHandle = null;
        MethodHandle getByteWithLongHandle = null;
        MethodHandle getByteWithObjectHandle = null;
        MethodHandle getCharWithObjectHandle = null;
        MethodHandle getShortWithLongHandle = null;
        MethodHandle getShortWithObjectHandle = null;
        MethodHandle getIntWithLongHandle = null;
        MethodHandle getIntWithObjectHandle = null;
        MethodHandle getIntVolatileWithObjectHandle = null;
        MethodHandle getLongWithLongHandle = null;
        MethodHandle getLongWithObjectHandle = null;
        MethodHandle getObjectWithObjectHandle = null;
        MethodHandle putByteWithLongHandle = null;
        MethodHandle putByteWithObjectHandle = null;
        MethodHandle putShortWithLongHandle = null;
        MethodHandle putShortWithObjectHandle = null;
        MethodHandle putIntWithLongHandle = null;
        MethodHandle putIntWithObjectHandle = null;
        MethodHandle putOrderedIntWithObjectHandle = null;
        MethodHandle putLongWithLongHandle = null;
        MethodHandle putLongWithObjectHandle = null;
        MethodHandle putObjectWithObjectHandle = null;
        MethodHandle addressSizeHandle = null;
        MethodHandle storeFenceHandle = null;

        if ((unsafeUnavailabilityCause = EXPLICIT_NO_UNSAFE_CAUSE) != null) {
            unsafe = null;
        } else {
            // attempt to access field Unsafe#theUnsafe
            final Object maybeUnsafe = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        Class<?> unsafeClass = Class.forName(SUN_MISC_UNSAFE_CLASS_NAME);
                        final Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                        // We always want to try using Unsafe as the access still works on java9 as well and
                        // we need it for out native-transports and many optimizations.
                        Throwable cause = ReflectionUtil.trySetAccessible(unsafeField, false);
                        if (cause != null) {
                            return cause;
                        }
                        // the unsafe instance
                        return unsafeField.get(null);
                    } catch (NoSuchFieldException | SecurityException |
                             IllegalAccessException | ClassNotFoundException e) {
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
                    logger.debug("{}: unavailable", SUN_MISC_UNSAFE_CLASS_NAME, maybeUnsafe);
                } else {
                    logger.debug("{}.theUnsafe: unavailable: {}", SUN_MISC_UNSAFE_CLASS_NAME,
                            unsafeUnavailabilityCause.getMessage());
                }
            } else {
                unsafe = maybeUnsafe;
                logger.debug("sun.misc.Unsafe.theUnsafe: available");
            }

            // ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK,
            // or that they haven't been removed by JEP 471.
            // https://github.com/netty/netty/issues/1061
            // https://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
            // https://openjdk.org/jeps/471
            if (unsafe != null) {
                final Object finalUnsafe = unsafe;
                final Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            // Other methods like invokeCleaner() are tested for elsewhere.
                            Class<?> cls = finalUnsafe.getClass();
                            List<MethodHandle> methodHandles = new ArrayList<>();
                            MethodHandle allocateMemoryHandle = lookup.findVirtual(cls, "allocateMemory",
                                    methodType(long.class, long.class)).bindTo(finalUnsafe);
                            methodHandles.add(allocateMemoryHandle);
                            methodHandles.add(
                                    lookup.findVirtual(cls, "copyMemory",
                                                    methodType(void.class, long.class, long.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "copyMemory",
                                                    methodType(void.class, Object.class, long.class, Object.class,
                                                            long.class, long.class))
                                            .bindTo(finalUnsafe)
                                    );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "objectFieldOffset",
                                                    methodType(long.class, Field.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "staticFieldOffset",
                                                    methodType(long.class, Field.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "staticFieldBase",
                                                    methodType(Object.class, Field.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "arrayBaseOffset",
                                                    methodType(int.class, Class.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "arrayIndexScale",
                                                    methodType(int.class, Class.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "reallocateMemory",
                                                    methodType(long.class, long.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            MethodHandle freeMemoryHandle = lookup.findVirtual(cls, "freeMemory",
                                    methodType(void.class, long.class)).bindTo(finalUnsafe);
                            methodHandles.add(freeMemoryHandle);
                            methodHandles.add(
                                    lookup.findVirtual(cls, "setMemory",
                                                    methodType(void.class, long.class, long.class, byte.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "setMemory",
                                                    methodType(void.class, Object.class, long.class,
                                                            long.class, byte.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getBoolean",
                                                    methodType(boolean.class, Object.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getByte",
                                                    methodType(byte.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getByte",
                                                    methodType(byte.class, Object.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getChar",
                                                    methodType(char.class, Object.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getShort",
                                                    methodType(short.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getShort",
                                                    methodType(short.class, Object.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getInt",
                                                    methodType(int.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getInt",
                                                    methodType(int.class, Object.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getIntVolatile",
                                                    methodType(int.class, Object.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getLong",
                                                    methodType(long.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getLong",
                                                    methodType(long.class, Object.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "getObject",
                                                    methodType(Object.class, Object.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putByte",
                                                    methodType(void.class, long.class, byte.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putByte",
                                                    methodType(void.class, Object.class, long.class, byte.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putShort",
                                                    methodType(void.class, long.class, short.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putShort",
                                                    methodType(void.class, Object.class, long.class, short.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putInt",
                                            methodType(void.class, long.class, int.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putInt",
                                                    methodType(void.class, Object.class, long.class, int.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putOrderedInt",
                                                    methodType(void.class, Object.class, long.class, int.class))
                                            .bindTo(finalUnsafe)
                            );
                            MethodHandle putLongHandle = lookup.findVirtual(cls, "putLong",
                                    methodType(void.class, long.class, long.class)).bindTo(finalUnsafe);
                            methodHandles.add(putLongHandle);
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putLong",
                                                    methodType(void.class, Object.class, long.class, long.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "putObject",
                                                    methodType(void.class, Object.class, long.class, Object.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "addressSize",
                                                    methodType(int.class))
                                            .bindTo(finalUnsafe)
                            );
                            methodHandles.add(
                                    lookup.findVirtual(cls, "storeFence",
                                                    methodType(void.class))
                                            .bindTo(finalUnsafe)
                            );

                            // The following tests the methods are usable.
                            // Will throw UnsupportedOperationException if unsafe memory access is denied:
                            long address = (long) allocateMemoryHandle.invokeExact((long) 8);
                            putLongHandle.invokeExact(address, (long) 42);
                            freeMemoryHandle.invokeExact(address);
                            return methodHandles;
                        } catch (Throwable cause) {
                            return cause;
                        }
                    }
                });

                if (maybeException instanceof Throwable) {
                    // Unsafe.copyMemory(Object, long, Object, long, long) unavailable.
                    unsafe = null;
                    unsafeUnavailabilityCause = (Throwable) maybeException;
                    if (logger.isTraceEnabled()) {
                        logger.debug("{} method unavailable:", SUN_MISC_UNSAFE_CLASS_NAME, unsafeUnavailabilityCause);
                    } else {
                        logger.debug("{} method unavailable: {}", SUN_MISC_UNSAFE_CLASS_NAME,
                                ((Throwable) maybeException).getMessage());
                    }
                } else {
                    logger.debug("{} base methods: all available", SUN_MISC_UNSAFE_CLASS_NAME);
                    @SuppressWarnings("unchecked") List<MethodHandle> handles = (List<MethodHandle>) maybeException;

                    int idx = 0;
                    allocateMemoryHandle = handles.get(idx++);
                    copyMemoryWithLongHandle = handles.get(idx++);
                    copyMemoryWithObjectHandle = handles.get(idx++);
                    objectFieldOffsetHandle = handles.get(idx++);
                    staticFieldOffsetHandle = handles.get(idx++);
                    staticFieldBaseHandle = handles.get(idx++);
                    arrayBaseOffsetHandle = handles.get(idx++);
                    arrayIndexScaleHandle = handles.get(idx++);
                    reallocateMemoryHandle = handles.get(idx++);
                    freeMemoryHandle = handles.get(idx++);
                    setMemoryWithLongHandle = handles.get(idx++);
                    setMemoryWithObjectHandle = handles.get(idx++);
                    getBooleanWithObjectHandle = handles.get(idx++);
                    getByteWithLongHandle = handles.get(idx++);
                    getByteWithObjectHandle = handles.get(idx++);
                    getCharWithObjectHandle = handles.get(idx++);
                    getShortWithLongHandle = handles.get(idx++);
                    getShortWithObjectHandle = handles.get(idx++);
                    getIntWithLongHandle = handles.get(idx++);
                    getIntWithObjectHandle = handles.get(idx++);
                    getIntVolatileWithObjectHandle = handles.get(idx++);
                    getLongWithLongHandle = handles.get(idx++);
                    getLongWithObjectHandle = handles.get(idx++);
                    getObjectWithObjectHandle = handles.get(idx++);
                    putByteWithLongHandle = handles.get(idx++);
                    putByteWithObjectHandle = handles.get(idx++);
                    putShortWithLongHandle = handles.get(idx++);
                    putShortWithObjectHandle = handles.get(idx++);
                    putIntWithLongHandle = handles.get(idx++);
                    putIntWithObjectHandle = handles.get(idx++);
                    putOrderedIntWithObjectHandle = handles.get(idx++);
                    putLongWithLongHandle = handles.get(idx++);
                    putLongWithObjectHandle = handles.get(idx++);
                    putObjectWithObjectHandle = handles.get(idx++);
                    addressSizeHandle = handles.get(idx++);
                    storeFenceHandle = handles.get(idx);
                }
            }
        }

        UNSAFE_ALLOCATE_MEMORY_HANDLE = allocateMemoryHandle;
        UNSAFE_COPY_MEMORY_WITH_LONG_HANDLE = copyMemoryWithLongHandle;
        UNSAFE_COPY_MEMORY_WITH_OBJECT_HANDLE = copyMemoryWithObjectHandle;
        UNSAFE_OBJECT_FIELD_OFFSET_HANDLE = objectFieldOffsetHandle;
        UNSAFE_REALLOCATE_MEMORY_HANDLE = reallocateMemoryHandle;
        UNSAFE_FREE_MEMORY_HANDLE = freeMemoryHandle;
        UNSAFE_SET_MEMORY_WITH_LONG_HANDLE = setMemoryWithLongHandle;
        UNSAFE_SET_MEMORY_WITH_OBJECT_HANDLE = setMemoryWithObjectHandle;
        UNSAFE_GET_BYTE_WITH_LONG_HANDLE = getByteWithLongHandle;
        UNSAFE_GET_BYTE_WITH_OBJECT_HANDLE = getByteWithObjectHandle;
        UNSAFE_GET_CHAR_WITH_OBJECT_HANDLE = getCharWithObjectHandle;
        UNSAFE_GET_SHORT_WITH_LONG_HANDLE = getShortWithLongHandle;
        UNSAFE_GET_SHORT_WITH_OBJECT_HANDLE = getShortWithObjectHandle;
        UNSAFE_GET_INT_WITH_LONG_HANDLE = getIntWithLongHandle;
        UNSAFE_GET_INT_WITH_OBJECT_HANDLE = getIntWithObjectHandle;
        UNSAFE_GET_INT_VOLATILE_WITH_OBJECT_HANDLE = getIntVolatileWithObjectHandle;
        UNSAFE_GET_LONG_WITH_LONG_HANDLE = getLongWithLongHandle;
        UNSAFE_GET_LONG_WITH_OBJECT_HANDLE = getLongWithObjectHandle;
        UNSAFE_GET_OBJECT_WITH_OBJECT_HANDLE = getObjectWithObjectHandle;
        UNSAFE_PUT_BYTE_WITH_LONG_HANDLE = putByteWithLongHandle;
        UNSAFE_PUT_BYTE_WITH_OBJECT_HANDLE = putByteWithObjectHandle;
        UNSAFE_PUT_SHORT_WITH_LONG_HANDLE = putShortWithLongHandle;
        UNSAFE_PUT_SHORT_WITH_OBJECT_HANDLE = putShortWithObjectHandle;
        UNSAFE_PUT_INT_WITH_LONG_HANDLE = putIntWithLongHandle;
        UNSAFE_PUT_INT_WITH_OBJECT_HANDLE = putIntWithObjectHandle;
        UNSAFE_PUT_ORDERED_INT_WITH_OBJECT_HANDLE = putOrderedIntWithObjectHandle;
        UNSAFE_PUT_LONG_WITH_LONG_HANDLE = putLongWithLongHandle;
        UNSAFE_PUT_LONG_WITH_OBJECT_HANDLE = putLongWithObjectHandle;
        UNSAFE_PUT_OBJECT_WITH_OBJECT_HANDLE = putObjectWithObjectHandle;
        UNSAFE_ADDRESS_SIZE_HANDLE = addressSizeHandle;
        UNSAFE_STORE_FENCE_HANDLE = storeFenceHandle;
        UNSAFE_ARRAY_BASE_OFFSET_HANDLE = arrayBaseOffsetHandle;
        UNSAFE_ARRAY_INDEX_SCALE_HANDLE = arrayIndexScaleHandle;
        UNSAFE_STATIC_FIELD_OFFSET_HANDLE = staticFieldOffsetHandle;
        UNSAFE_STATIC_FIELD_BASE_HANDLE = staticFieldBaseHandle;
        UNSAFE_GET_BOOLEAN_WITH_OBJECT_HANDLE = getBooleanWithObjectHandle;
        UNSAFE_UNAVAILABILITY_CAUSE = unsafeUnavailabilityCause;
        UNSAFE = unsafe;
    }

    static boolean isExplicitNoUnsafe() {
        return EXPLICIT_NO_UNSAFE_CAUSE != null;
    }

    private static Throwable explicitNoUnsafeCause0() {
        boolean explicitProperty = SystemPropertyUtil.contains("io.netty.noUnsafe");
        boolean noUnsafe = SystemPropertyUtil.getBoolean("io.netty.noUnsafe", false);
        logger.debug("-Dio.netty.noUnsafe: {}", noUnsafe);

        // See JDK 23 JEP 471 https://openjdk.org/jeps/471 and sun.misc.Unsafe.beforeMemoryAccess() on JDK 23+.
        // And JDK 24 JEP 498 https://openjdk.org/jeps/498, that enable warnings by default.
        String reason = "io.netty.noUnsafe";
        String unspecified = "<unspecified>";
        String unsafeMemoryAccess = SystemPropertyUtil.get("sun.misc.unsafe.memory.access", unspecified);
        if (!explicitProperty && unspecified.equals(unsafeMemoryAccess) && JavaVersion.javaVersion() >= 24) {
            reason = "io.netty.noUnsafe=true by default on Java 24+";
            noUnsafe = true;
        } else if (!("allow".equals(unsafeMemoryAccess) || unspecified.equals(unsafeMemoryAccess))) {
            reason = "--sun-misc-unsafe-memory-access=" + unsafeMemoryAccess;
            noUnsafe = true;
        }

        if (noUnsafe) {
            String msg = "sun.misc.Unsafe: unavailable (" + reason + ')';
            logger.debug(msg);
            return new UnsupportedOperationException(msg);
        }

        // Legacy properties
        String unsafePropName;
        if (SystemPropertyUtil.contains("io.netty.tryUnsafe")) {
            unsafePropName = "io.netty.tryUnsafe";
        } else {
            unsafePropName = "org.jboss.netty.tryUnsafe";
        }

        if (!SystemPropertyUtil.getBoolean(unsafePropName, true)) {
            String msg = "sun.misc.Unsafe: unavailable (" + unsafePropName + ')';
            logger.debug(msg);
            return new UnsupportedOperationException(msg);
        }

        return null;
    }

    static int arrayIndexScale(Class<?> arrayClass) {
        try {
            return (int) UNSAFE_ARRAY_INDEX_SCALE_HANDLE.invokeExact(arrayClass);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static int arrayBaseOffset(Class<?> arrayClass) {
        try {
            return (int) UNSAFE_ARRAY_BASE_OFFSET_HANDLE.invokeExact(arrayClass);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void storeFence() {
        try {
            UNSAFE_STORE_FENCE_HANDLE.invokeExact();
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static long objectFieldOffset(Field field) {
        try {
            return (long) UNSAFE_OBJECT_FIELD_OFFSET_HANDLE.invokeExact(field);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static long staticFieldOffset(Field f) {
        try {
            return (long) UNSAFE_STATIC_FIELD_OFFSET_HANDLE.invokeExact(f);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static Object staticFieldBase(Field f) {
        try {
            return UNSAFE_STATIC_FIELD_BASE_HANDLE.invokeExact(f);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static boolean getBoolean(Object o, long offset) {
        try {
            return (boolean) UNSAFE_GET_BOOLEAN_WITH_OBJECT_HANDLE.invokeExact(o, offset);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static Object getObject(Object object, long fieldOffset) {
        try {
            return (Object) UNSAFE_GET_OBJECT_WITH_OBJECT_HANDLE.invokeExact(object, fieldOffset);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static long getLong(Object object, long fieldOffset) {
        try {
            return (long) UNSAFE_GET_LONG_WITH_OBJECT_HANDLE.invokeExact(object, fieldOffset);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static byte getByte(long address) {
        try {
            return (byte) UNSAFE_GET_BYTE_WITH_LONG_HANDLE.invokeExact(address);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static short getShort(long address) {
        try {
            return (short) UNSAFE_GET_SHORT_WITH_LONG_HANDLE.invokeExact(address);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static int getInt(long address) {
        try {
            return (int) UNSAFE_GET_INT_WITH_LONG_HANDLE.invokeExact(address);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static long getLong(long address) {
        try {
            return (long) UNSAFE_GET_LONG_WITH_LONG_HANDLE.invokeExact(address);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static byte getByte(Object o, long offset) {
        try {
            return (byte) UNSAFE_GET_BYTE_WITH_OBJECT_HANDLE.invokeExact(o, offset);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static short getShort(Object o, long offset) {
        try {
            return (short) UNSAFE_GET_SHORT_WITH_OBJECT_HANDLE.invokeExact(o, offset);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static char getChar(Object o, long offset) {
        try {
            return (char) UNSAFE_GET_CHAR_WITH_OBJECT_HANDLE.invokeExact(o, offset);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static int getInt(Object o, long offset) {
        try {
            return (int) UNSAFE_GET_INT_WITH_OBJECT_HANDLE.invokeExact(o, offset);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static int getIntVolatile(Object o, long address) {
        try {
            return (int) UNSAFE_GET_INT_VOLATILE_WITH_OBJECT_HANDLE.invokeExact(o, address);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putIntOrdered(Object o, long address, int newValue) {
        try {
            UNSAFE_PUT_ORDERED_INT_WITH_OBJECT_HANDLE.invokeExact(o, address, newValue);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putByte(long address, byte value) {
        try {
            UNSAFE_PUT_BYTE_WITH_LONG_HANDLE.invokeExact(address, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putShort(long address, short value) {
        try {
            UNSAFE_PUT_SHORT_WITH_LONG_HANDLE.invokeExact(address, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putInt(long address, int value) {
        try {
            UNSAFE_PUT_INT_WITH_LONG_HANDLE.invokeExact(address, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putLong(long address, long value) {
        try {
            UNSAFE_PUT_LONG_WITH_LONG_HANDLE.invokeExact(address, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putByte(Object o, long offset, byte value) {
        try {
            UNSAFE_PUT_BYTE_WITH_OBJECT_HANDLE.invokeExact(o, offset, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putShort(Object o, long offset, short value) {
        try {
            UNSAFE_PUT_SHORT_WITH_OBJECT_HANDLE.invokeExact(o, offset, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putInt(Object o, long offset, int value) {
        try {
            UNSAFE_PUT_INT_WITH_OBJECT_HANDLE.invokeExact(o, offset, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putLong(Object o, long offset, long value) {
        try {
            UNSAFE_PUT_LONG_WITH_OBJECT_HANDLE.invokeExact(o, offset, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void putObject(Object o, long offset, Object value) {
        try {
            UNSAFE_PUT_OBJECT_WITH_OBJECT_HANDLE.invokeExact(o, offset, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void copyMemory(Object srcBase, long srcOffset,
                           Object destBase, long destOffset,
                           long bytes) {
        try {
            UNSAFE_COPY_MEMORY_WITH_OBJECT_HANDLE.invokeExact(srcBase, srcOffset, destBase, destOffset, bytes);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void copyMemory(long srcAddress, long destAddress, long bytes) {
        try {
            UNSAFE_COPY_MEMORY_WITH_LONG_HANDLE.invokeExact(srcAddress, destAddress, bytes);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void setMemory(long address, long bytes, byte value) {
        try {
            UNSAFE_SET_MEMORY_WITH_LONG_HANDLE.invokeExact(address, bytes, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void setMemory(Object o, long offset, long bytes, byte value) {
        try {
            UNSAFE_SET_MEMORY_WITH_OBJECT_HANDLE.invokeExact(o, offset, bytes, value);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static int addressSize() {
        try {
            return (int) UNSAFE_ADDRESS_SIZE_HANDLE.invokeExact();
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static long allocateMemory(long size) {
        try {
            return (long) UNSAFE_ALLOCATE_MEMORY_HANDLE.invokeExact(size);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static void freeMemory(long address) {
        try {
            UNSAFE_FREE_MEMORY_HANDLE.invokeExact(address);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
        }
    }

    static long reallocateMemory(long address, long newSize) {
        try {
            return (long) UNSAFE_REALLOCATE_MEMORY_HANDLE.invokeExact(address, newSize);
        } catch (Throwable cause) {
            rethrowIfPossible(cause);
            throw new IllegalStateException(cause);
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

    static boolean isAvailabile() {
        return UNSAFE_UNAVAILABILITY_CAUSE == null && UNSAFE != null;
    }

    static Throwable unavailabilityCause() {
        return UNSAFE_UNAVAILABILITY_CAUSE;
    }

    private SunMiscUnsafeAccess() { }
}
