/*
 * Copyright 2016 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;

final class UnpooledUnsafeNoCleanerDirectByteBuf extends UnpooledUnsafeDirectByteBuf {

    UnpooledUnsafeNoCleanerDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(alloc, initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuffer allocateDirect(int initialCapacity) {
        return PlatformDependent.allocateDirectNoCleaner(initialCapacity);
    }

    @Override
    protected void freeDirect(ByteBuffer buffer) {
        PlatformDependent.freeDirectNoCleaner(buffer);
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        ensureAccessible();
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        int readerIndex = readerIndex();
        int writerIndex = writerIndex();
        int oldCapacity = capacity();

        if (newCapacity > oldCapacity) {
            ByteBuffer oldBuffer = buffer;
            ByteBuffer newBuffer = PlatformDependent.reallocateDirectNoCleaner(oldBuffer, newCapacity);
            setByteBuffer(newBuffer, false);
        } else if (newCapacity < oldCapacity) {
            ByteBuffer oldBuffer = buffer;
            ByteBuffer newBuffer = allocateDirect(newCapacity);
            if (readerIndex < newCapacity) {
                if (writerIndex > newCapacity) {
                    writerIndex = newCapacity;
                    writerIndex(writerIndex);
                }
                oldBuffer.position(readerIndex).limit(writerIndex);
                newBuffer.position(readerIndex).limit(writerIndex);
                newBuffer.put(oldBuffer);
                newBuffer.clear();
            } else {
                setIndex(newCapacity, newCapacity);
            }
            setByteBuffer(newBuffer, true);
        }
        return this;
    }
}
