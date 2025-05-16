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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

/**
 * Provide a way to clean a ByteBuffer on Java9+.
 */
final class CleanerJava9 implements Cleaner {
    private static final Logger logger = LoggerFactory.getLogger(CleanerJava9.class);

    private static final MethodHandle INVOKE_CLEANER_HANDLE;

    static {
        MethodHandle invokeCleanerHandle = null;
        Throwable error = null;
        if (PlatformDependent0.hasUnsafe()) {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(1);
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                // See https://bugs.openjdk.java.net/browse/JDK-8171377
                invokeCleanerHandle = lookup.findVirtual(
                        PlatformDependent0.UNSAFE.getClass(),
                        "invokeCleaner",
                        MethodType.methodType(void.class, ByteBuffer.class)).bindTo(PlatformDependent0.UNSAFE);
                invokeCleanerHandle.invokeExact(buffer);
            } catch (Throwable cause) {
                error = cause;
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
        try {
            INVOKE_CLEANER_HANDLE.invokeExact(buffer);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Throwable throwable) {
            PlatformDependent.throwException(throwable);
        }
    }
}
