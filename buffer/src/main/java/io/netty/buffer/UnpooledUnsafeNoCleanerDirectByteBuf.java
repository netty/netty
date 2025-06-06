/*
 * Copyright 2016 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.CleanableDirectBuffer;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;

class UnpooledUnsafeNoCleanerDirectByteBuf extends UnpooledUnsafeDirectByteBuf {
    UnpooledUnsafeNoCleanerDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(alloc, initialCapacity, maxCapacity);
    }

    @Override
    protected CleanableDirectBuffer allocateDirectBuffer(int capacity) {
        return PlatformDependent.allocateDirectBufferNoCleaner(capacity);
    }

    @Override
    protected ByteBuffer allocateDirect(int initialCapacity) {
        throw new UnsupportedOperationException();
    }

    CleanableDirectBuffer reallocateDirect(CleanableDirectBuffer oldBuffer, int initialCapacity) {
        return PlatformDependent.reallocateDirectBufferNoCleaner(oldBuffer, initialCapacity);
    }

    @Override
    protected void freeDirect(ByteBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        checkNewCapacity(newCapacity);

        int oldCapacity = capacity();
        if (newCapacity == oldCapacity) {
            return this;
        }

        trimIndicesToCapacity(newCapacity);
        setByteBuffer(reallocateDirect(cleanable, newCapacity), false);
        return this;
    }
}
