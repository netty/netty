/*
 * Copyright 2018 The Netty Project
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static java.lang.invoke.MethodType.methodType;

// Encapsulate access to jdk.internal.misc.Unsafe
public final class UnsafeInternal {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(UnsafeInternal.class);
    private static final MethodHandle ALLOCATE_UNINITIALIZED_ARRAY_HANDLE;
    private static final MethodHandle GET_LONG_UNALIGNED_HANDLE;
    private static final MethodHandle GET_INT_UNALIGNED_HANDLE;
    private static final MethodHandle GET_SHORT_UNALIGNED_HANDLE;
    private static final MethodHandle PUT_LONG_UNALIGNED_HANDLE;
    private static final MethodHandle PUT_INT_UNALIGNED_HANDLE;
    private static final MethodHandle PUT_SHORT_UNALIGNED_HANDLE;
    private static final Throwable INTERNAL_UNSAFE_UNAVAILABILITY_CAUSE;

    static {
        final Object maybeHandles;
        Throwable unsafeCause = PlatformDependent0.getUnsafeUnavailabilityCause();
        if (unsafeCause != null) {
            maybeHandles = unsafeCause;
        } else {
            if (PlatformDependent0.javaVersion() >= 9) {
                maybeHandles = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            // Java9 has jdk.internal.misc.Unsafe and not all methods are propagated to
                            // sun.misc.Unsafe
                            Class<?> internalUnsafeClass = Class.forName("jdk.internal.misc.Unsafe");
                            Method method = internalUnsafeClass.getDeclaredMethod("getUnsafe");
                            Object internalUnsafe = method.invoke(null);

                            Lookup lookup = MethodHandles.lookup();
                            MethodHandle allocateUninitializedArrayHandle =
                                    lookup.findVirtual(internalUnsafeClass, "allocateUninitializedArray",
                                                       methodType(Object.class, Class.class, int.class))
                                          .bindTo(internalUnsafe);

                            MethodHandle getLongUnalignedHandle =
                                    lookup.findVirtual(internalUnsafeClass, "getLongUnaligned",
                                                       methodType(long.class, Object.class, long.class, boolean.class))
                                          .bindTo(internalUnsafe);

                            MethodHandle getIntUnalignedHandle =
                                    lookup.findVirtual(internalUnsafeClass, "getIntUnaligned",
                                                       methodType(int.class, Object.class, long.class, boolean.class))
                                          .bindTo(internalUnsafe);

                            MethodHandle getShortUnalignedHandle =
                                    lookup.findVirtual(internalUnsafeClass, "getShortUnaligned",
                                                       methodType(short.class, Object.class, long.class, boolean.class))
                                          .bindTo(internalUnsafe);

                            MethodHandle putLongUnalignedHandle =
                                    lookup.findVirtual(internalUnsafeClass, "putLongUnaligned",
                                                       methodType(void.class, Object.class, long.class,
                                                                  long.class, boolean.class))
                                          .bindTo(internalUnsafe);

                            MethodHandle putIntUnalignedHandle =
                                    lookup.findVirtual(internalUnsafeClass, "putIntUnaligned",
                                                       methodType(void.class, Object.class, long.class,
                                                                  int.class, boolean.class))
                                          .bindTo(internalUnsafe);
                            MethodHandle putShortUnalignedHandle =
                                    lookup.findVirtual(internalUnsafeClass, "putShortUnaligned",
                                                       methodType(void.class, Object.class, long.class,
                                                                  short.class, boolean.class))
                                          .bindTo(internalUnsafe);
                            return new MethodHandle[] { allocateUninitializedArrayHandle,
                                    getLongUnalignedHandle, getIntUnalignedHandle, getShortUnalignedHandle,
                                    putLongUnalignedHandle, putIntUnalignedHandle, putShortUnalignedHandle };
                        } catch (Throwable e) {
                            return e;
                        }
                    }
                });
            } else {
                maybeHandles = new UnsupportedOperationException("Not supported before java9");
            }
        }

        if (maybeHandles instanceof Throwable) {
            INTERNAL_UNSAFE_UNAVAILABILITY_CAUSE = (Throwable) maybeHandles;
            ALLOCATE_UNINITIALIZED_ARRAY_HANDLE = null;
            GET_LONG_UNALIGNED_HANDLE = null;
            GET_INT_UNALIGNED_HANDLE = null;
            GET_SHORT_UNALIGNED_HANDLE = null;
            PUT_LONG_UNALIGNED_HANDLE = null;
            PUT_INT_UNALIGNED_HANDLE = null;
            PUT_SHORT_UNALIGNED_HANDLE = null;
        } else {
            INTERNAL_UNSAFE_UNAVAILABILITY_CAUSE = null;
            MethodHandle[] handles = (MethodHandle[]) maybeHandles;
            ALLOCATE_UNINITIALIZED_ARRAY_HANDLE = handles[0];
            GET_LONG_UNALIGNED_HANDLE = handles[1];
            GET_INT_UNALIGNED_HANDLE = handles[2];
            GET_SHORT_UNALIGNED_HANDLE = handles[3];
            PUT_LONG_UNALIGNED_HANDLE = handles[4];
            PUT_INT_UNALIGNED_HANDLE = handles[5];
            PUT_SHORT_UNALIGNED_HANDLE = handles[6];
        }
        if (INTERNAL_UNSAFE_UNAVAILABILITY_CAUSE != null) {
            LOGGER.debug("jdk.internal.misc.Unsafe: unavailable", INTERNAL_UNSAFE_UNAVAILABILITY_CAUSE);
        } else {
            LOGGER.debug("jdk.internal.misc.Unsafe: available");
        }
    }

    public static boolean isAvailable() {
        return INTERNAL_UNSAFE_UNAVAILABILITY_CAUSE == null;
    }

    public static byte[] allocateUninitializedArray(int size) {
        try {
            Object returnValue = ALLOCATE_UNINITIALIZED_ARRAY_HANDLE.invokeExact(byte.class, size);
            return (byte[]) returnValue;
        } catch (Throwable cause) {
            PlatformDependent0.throwException(cause);
            return null;
        }
    }

    public static long getLongUnaligned(Object o, long offset, boolean bigEndian) {
        try {
            return (long) GET_LONG_UNALIGNED_HANDLE.invokeExact(o, offset, bigEndian);
        } catch (Throwable cause) {
            PlatformDependent0.throwException(cause);
            return -1;
        }
    }

    public static int getIntUnaligned(Object o, long offset, boolean bigEndian) {
        try {
            return (int) GET_INT_UNALIGNED_HANDLE.invokeExact(o, offset, bigEndian);
        } catch (Throwable cause) {
            PlatformDependent0.throwException(cause);
            return -1;
        }
    }

    public static short getShortUnaligned(Object o, long offset, boolean bigEndian) {
        try {
            return (short) GET_SHORT_UNALIGNED_HANDLE.invokeExact(o, offset, bigEndian);
        } catch (Throwable cause) {
            PlatformDependent0.throwException(cause);
            return -1;
        }
    }

    public static void putLongUnaligned(Object o, long offset, long value, boolean bigEndian) {
        try {
            PUT_LONG_UNALIGNED_HANDLE.invokeExact(o, offset, value, bigEndian);
        } catch (Throwable cause) {
            PlatformDependent0.throwException(cause);
        }
    }

    public static void putIntUnaligned(Object o, long offset, int value, boolean bigEndian) {
        try {
            PUT_INT_UNALIGNED_HANDLE.invokeExact(o, offset, value, bigEndian);
        } catch (Throwable cause) {
            PlatformDependent0.throwException(cause);
        }
    }

    public static void putShortUnaligned(Object o, long offset, short value, boolean bigEndian) {
        try {
            PUT_SHORT_UNALIGNED_HANDLE.invokeExact(o, offset, value, bigEndian);
        } catch (Throwable cause) {
            PlatformDependent0.throwException(cause);
        }
    }

    private UnsafeInternal() { }
}
