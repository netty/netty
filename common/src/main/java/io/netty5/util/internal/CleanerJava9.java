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
package io.netty5.util.internal;

import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Provide a way to clean a ByteBuffer on Java9+.
 */
final class CleanerJava9 implements Cleaner {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CleanerJava9.class);

    private static final MethodHandle INVOKE_CLEANER_HANDLE;

    static {
        final MethodHandle invokeCleanerHandle;
        final Throwable error;
        if (PlatformDependent0.hasUnsafe()) {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(1);
            Object maybeInvokeMethodHandle = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.lookup();
                    // See https://bugs.openjdk.java.net/browse/JDK-8171377
                    MethodHandle m = lookup.findVirtual(
                            PlatformDependent0.UNSAFE.getClass(),
                            "invokeCleaner",
                            MethodType.methodType(void.class, ByteBuffer.class)).bindTo(PlatformDependent0.UNSAFE);
                    m.invokeExact(buffer);
                    return m;
                } catch (Throwable cause) {
                    return cause;
                }
            });

            if (maybeInvokeMethodHandle instanceof Throwable) {
                invokeCleanerHandle = null;
                error = (Throwable) maybeInvokeMethodHandle;
            } else {
                invokeCleanerHandle = (MethodHandle) maybeInvokeMethodHandle;
                error = null;
            }
        } else {
            invokeCleanerHandle = null;
            error = new UnsupportedOperationException("sun.misc.Unsafe unavailable");
        }
        if (error == null) {
            logger.debug("java.nio.ByteBuffer.cleaner(): available");
        } else {
            logger.debug("java.nio.ByteBuffer.cleaner(): unavailable", error);
        }
        INVOKE_CLEANER_HANDLE = invokeCleanerHandle;
    }

    static boolean isSupported() {
        return INVOKE_CLEANER_HANDLE != null;
    }

    @Override
    public void freeDirectBuffer(ByteBuffer buffer) {
        // Try to minimize overhead when there is no SecurityManager present.
        // See https://bugs.openjdk.java.net/browse/JDK-8191053.
        if (System.getSecurityManager() == null) {
            try {
                INVOKE_CLEANER_HANDLE.invokeExact(buffer);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Throwable throwable) {
                PlatformDependent.throwException(throwable);
            }
        } else {
            freeDirectBufferPrivileged(buffer);
        }
    }

    private static void freeDirectBufferPrivileged(final ByteBuffer buffer) {
        Exception error = AccessController.doPrivileged((PrivilegedAction<Exception>) () -> {
            try {
                INVOKE_CLEANER_HANDLE.invokeExact(PlatformDependent0.UNSAFE, buffer);
            } catch (RuntimeException exception) {
                return exception;
            } catch (Throwable throwable) {
                PlatformDependent.throwException(throwable);
            }
            return null;
        });
        if (error != null) {
            PlatformDependent.throwException(error);
        }
    }
}
