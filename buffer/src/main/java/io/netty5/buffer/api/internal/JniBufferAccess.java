/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.api.internal;

import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

final class JniBufferAccess {
    static final boolean IS_AVAILABLE;
    static final MethodHandle MEMORY_ADDRESS;

    static {
        boolean isAvailable = false;
        MethodHandle memoryAddress = null;
        try {
            Class<?> bufferAccess = Class.forName("io.netty5.channel.unix.Buffer");
            Lookup lookup = MethodHandles.lookup();
            bufferAccess = lookup.accessClass(bufferAccess);
            MethodType type = MethodType.methodType(long.class, ByteBuffer.class);
            memoryAddress = lookup.findStatic(bufferAccess, "memoryAddress", type);
            isAvailable = true;
        } catch (Throwable e) {
            InternalLogger logger = InternalLoggerFactory.getInstance(JniBufferAccess.class);
            logger.debug("JNI bypass for accessing native address of DirectByteBuffer is unavailable.", e);
        }
        IS_AVAILABLE = isAvailable;
        MEMORY_ADDRESS = memoryAddress;
    }

    private JniBufferAccess() {
    }
}
