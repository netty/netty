/*
* Copyright 2017 The Netty Project
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static java.lang.invoke.MethodType.methodType;

/**
 * Provide a way to clean a ByteBuffer on Java9+.
 */
final class CleanerJava9 implements Cleaner {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CleanerJava9.class);

    private static final MethodHandle INVOKE_CLEANER;

    static {
        final MethodHandle method;
        final Throwable error;
        if (PlatformDependent0.hasUnsafe()) {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(1);
            Object maybeInvokeMethod = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        // See https://bugs.openjdk.java.net/browse/JDK-8171377
                        Class<? extends Unsafe> unsafeClass = PlatformDependent0.UNSAFE.getClass();
                        MethodHandles.Lookup lookup = MethodHandles.lookup();
                        MethodHandle invokeCleaner = lookup.findVirtual(
                                unsafeClass, "invokeCleaner", methodType(void.class, ByteBuffer.class));
                        invokeCleaner = invokeCleaner.bindTo(PlatformDependent0.UNSAFE);
                        invokeCleaner.invokeExact(buffer);
                        return invokeCleaner;
                    } catch (Throwable e) {
                        return e;
                    }
                }
            });

            if (maybeInvokeMethod instanceof Throwable) {
                method = null;
                error = (Throwable) maybeInvokeMethod;
            } else {
                method = (MethodHandle) maybeInvokeMethod;
                error = null;
            }
        } else {
            method = null;
            error = new UnsupportedOperationException("sun.misc.Unsafe unavailable");
        }
        if (error == null) {
            logger.debug("java.nio.ByteBuffer.cleaner(): available");
        } else {
            logger.debug("java.nio.ByteBuffer.cleaner(): unavailable", error);
        }
        INVOKE_CLEANER = method;
    }

    static boolean isSupported() {
        return INVOKE_CLEANER != null;
    }

    @Override
    public void freeDirectBuffer(ByteBuffer buffer) {
        // Try to minimize overhead when there is no SecurityManager present.
        // See https://bugs.openjdk.java.net/browse/JDK-8191053.
        if (System.getSecurityManager() == null) {
            try {
                INVOKE_CLEANER.invokeExact(buffer);
            } catch (Throwable cause) {
                PlatformDependent0.throwException(cause);
            }
        } else {
            freeDirectBufferPrivileged(buffer);
        }
    }

    private static void freeDirectBufferPrivileged(final ByteBuffer buffer) {
        Throwable error = AccessController.doPrivileged(new PrivilegedAction<Throwable>() {
            @Override
            public Throwable run() {
                try {
                    INVOKE_CLEANER.invokeExact(buffer);
                } catch (Throwable e) {
                    return e;
                }
                return null;
            }
        });
        if (error != null) {
            PlatformDependent0.throwException(error);
        }
    }
}
