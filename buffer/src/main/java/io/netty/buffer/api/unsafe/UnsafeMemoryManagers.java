/*
 * Copyright 2021 The Netty Project
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
package io.netty.buffer.api.unsafe;

import io.netty.buffer.api.MemoryManager;
import io.netty.buffer.api.MemoryManagers;
import io.netty.util.internal.PlatformDependent;

public class UnsafeMemoryManagers implements MemoryManagers {
    public UnsafeMemoryManagers() {
        if (!PlatformDependent.hasUnsafe()) {
            throw new UnsupportedOperationException("Unsafe is not available.");
        }
        if (!PlatformDependent.hasDirectBufferNoCleanerConstructor()) {
            throw new UnsupportedOperationException("DirectByteBuffer internal constructor is not available.");
        }
    }

    @Override
    public MemoryManager getHeapMemoryManager() {
        return new UnsafeMemoryManager(false);
    }

    @Override
    public MemoryManager getNativeMemoryManager() {
        return new UnsafeMemoryManager(true);
    }

    @Override
    public String getImplementationName() {
        return "Unsafe";
    }

    @Override
    public String toString() {
        return "US";
    }
}
