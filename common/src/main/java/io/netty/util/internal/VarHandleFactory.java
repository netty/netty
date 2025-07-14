/*
 * Copyright 2025 The Netty Project
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

final class VarHandleFactory {

    private VarHandleFactory() {
    }

    private static final MethodHandle FIND_VAR_HANDLE;
    private static final MethodHandle PRIVATE_LOOKUP_IN;
    private static final VarHandle LONG_LE_ARRAY_VIEW;
    private static final VarHandle LONG_BE_ARRAY_VIEW;
    private static final VarHandle INT_LE_ARRAY_VIEW;
    private static final VarHandle INT_BE_ARRAY_VIEW;
    private static final VarHandle SHORT_LE_ARRAY_VIEW;
    private static final VarHandle SHORT_BE_ARRAY_VIEW;
    private static final Throwable UNAVAILABILITY_CAUSE;

    static {
        MethodHandle findVarHandle = null;
        MethodHandle privateLookupIn = null;
        VarHandle longLeArrayViewHandle = null;
        VarHandle longBeArrayViewHandle = null;
        VarHandle intLeArrayViewHandle = null;
        VarHandle intBeArrayViewHandle = null;
        VarHandle shortLeArrayViewHandle = null;
        VarHandle shortBeArrayViewHandle = null;
        Throwable error = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            findVarHandle = lookup.findVirtual(MethodHandles.Lookup.class, "findVarHandle",
                    MethodType.methodType(VarHandle.class, Class.class, String.class, Class.class));
            privateLookupIn = lookup.findStatic(MethodHandles.class, "privateLookupIn",
                    MethodType.methodType(MethodHandles.Lookup.class, Class.class, MethodHandles.Lookup.class));
            MethodHandle byteArrayViewHandle = lookup.findStatic(MethodHandles.class, "byteArrayViewVarHandle",
                    MethodType.methodType(VarHandle.class, Class.class, ByteOrder.class));
            longLeArrayViewHandle = (VarHandle) byteArrayViewHandle.invokeExact(long[].class, ByteOrder.LITTLE_ENDIAN);
            longBeArrayViewHandle = (VarHandle) byteArrayViewHandle.invokeExact(long[].class, ByteOrder.BIG_ENDIAN);
            intLeArrayViewHandle = (VarHandle) byteArrayViewHandle.invokeExact(int[].class, ByteOrder.LITTLE_ENDIAN);
            intBeArrayViewHandle = (VarHandle) byteArrayViewHandle.invokeExact(int[].class, ByteOrder.BIG_ENDIAN);
            shortLeArrayViewHandle = (VarHandle) byteArrayViewHandle.invokeExact(
                    short[].class, ByteOrder.LITTLE_ENDIAN);
            shortBeArrayViewHandle = (VarHandle) byteArrayViewHandle.invokeExact(short[].class, ByteOrder.BIG_ENDIAN);
            error = null;
        } catch (Throwable e) {
            error = e;
            findVarHandle = null;
            privateLookupIn = null;
            longLeArrayViewHandle = null;
            longBeArrayViewHandle = null;
            intLeArrayViewHandle = null;
            intBeArrayViewHandle = null;
            shortLeArrayViewHandle = null;
        } finally {
            FIND_VAR_HANDLE = findVarHandle;
            PRIVATE_LOOKUP_IN = privateLookupIn;
            LONG_LE_ARRAY_VIEW = longLeArrayViewHandle;
            LONG_BE_ARRAY_VIEW = longBeArrayViewHandle;
            INT_LE_ARRAY_VIEW = intLeArrayViewHandle;
            INT_BE_ARRAY_VIEW = intBeArrayViewHandle;
            SHORT_LE_ARRAY_VIEW = shortLeArrayViewHandle;
            SHORT_BE_ARRAY_VIEW = shortBeArrayViewHandle;
            UNAVAILABILITY_CAUSE = error;
        }
    }

    public static boolean isSupported() {
        return UNAVAILABILITY_CAUSE == null;
    }

    public static Throwable unavailableCause() {
        return UNAVAILABILITY_CAUSE;
    }

    private static MethodHandles.Lookup privateLookup(MethodHandles.Lookup lookup, Class<?> targetClass) {
        try {
            return (MethodHandles.Lookup) PRIVATE_LOOKUP_IN.invokeExact(targetClass, lookup);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static VarHandle privateFindVarHandle(MethodHandles.Lookup lookup, Class<?> declaringClass,
                                                 String name, Class<?> type) {
        try {
            return (VarHandle) FIND_VAR_HANDLE.invokeExact(privateLookup(lookup, declaringClass),
                    declaringClass, name, type);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static VarHandle longLeArrayView() {
        return LONG_LE_ARRAY_VIEW;
    }

    public static VarHandle longBeArrayView() {
        return LONG_BE_ARRAY_VIEW;
    }

    public static VarHandle intLeArrayView() {
        return INT_LE_ARRAY_VIEW;
    }

    public static VarHandle intBeArrayView() {
        return INT_BE_ARRAY_VIEW;
    }

    public static VarHandle shortLeArrayView() {
        return SHORT_LE_ARRAY_VIEW;
    }

    public static VarHandle shortBeArrayView() {
        return SHORT_BE_ARRAY_VIEW;
    }
}
