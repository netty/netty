package io.netty.util.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

// Encapsulate access to jdk.internal.misc.Unsafe
final class UnsafeInternal {

    private final MethodHandle allocateUninitializedArrayHandle;

    static UnsafeInternal newInstance(sun.misc.Unsafe unsafe) throws Throwable {
        Object maybeUnsafe = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    Field internalUnsafeField = unsafe.getClass().getDeclaredField("theInternalUnsafe");
                    long offset = unsafe.staticFieldOffset(internalUnsafeField);
                    Object base = unsafe.staticFieldBase(internalUnsafeField);
                    if (internalUnsafeField.getType().getName().equals("jdk.internal.misc.Unsafe")) {
                        return unsafe.getObject(base, offset);
                    }
                    throw new IllegalStateException("Field does have the wrong type");
                } catch (Throwable e) {
                    return e;
                }
            }
        });
        if (maybeUnsafe instanceof Throwable) {
            throw (Throwable) maybeUnsafe;
        } else {
            MethodHandle allocateUninitializedArrayHandle;

            Lookup lookup = MethodHandles.lookup();
            allocateUninitializedArrayHandle = lookup.findVirtual(
                    maybeUnsafe.getClass(), "allocateUninitializedArray",
                    MethodType.methodType(Object.class, Class.class, int.class)).bindTo(maybeUnsafe);
            return new UnsafeInternal(allocateUninitializedArrayHandle);
        }
    }

    private UnsafeInternal(MethodHandle allocateUninitializedArrayHandle) {
        this.allocateUninitializedArrayHandle = allocateUninitializedArrayHandle;
    }


    byte[] allocateUninitializedArray(int size) {
        try {
            Object returnValue = allocateUninitializedArrayHandle.invokeExact(byte.class, size);
            return (byte[]) returnValue;
        } catch (Throwable cause) {
            PlatformDependent0.throwException(cause);
            return null;
        }
    }
}
