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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base class for {@link IoUringBufferRingAllocator} implementations which support large allocations.
 */
public abstract class AbstractIoUringBufferRingAllocator implements IoUringBufferRingAllocator {
    private final ByteBufAllocator allocator;
    private final boolean largeAllocation;

    /**
     * Creates new instance.
     *
     * @param allocator         the {@link ByteBufAllocator} to use for the allocations
     * @param largeAllocation   {@code true} if we should do a large allocation for the whole buffer ring
     *                          and then slice out the buffers or {@code false} if we should do one allocation
     *                          per buffer.
     */
    protected AbstractIoUringBufferRingAllocator(ByteBufAllocator allocator, boolean largeAllocation) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.largeAllocation = largeAllocation;
    }

    @Override
    public final void allocateBatch(Consumer<ByteBuf> consumer, int number) {
        if (largeAllocation) {
            int bufferSize = nextBufferSize();
            ByteBuf buffer = allocator.directBuffer(nextBufferSize() * number);
            try {
                for (int i = 0; i < number; i++) {
                    consumer.accept(buffer
                            .retainedSlice(i * bufferSize, bufferSize)
                            .setIndex(0, 0)
                    );
                }
            } finally {
                buffer.release();
            }
        } else {
            IoUringBufferRingAllocator.super.allocateBatch(consumer, number);
        }
    }

    @Override
    public final ByteBuf allocate() {
        return allocator.directBuffer(nextBufferSize());
    }

    /**
     * Does nothing by default, sub-classes might override this.
     *
     * @param attempted  the attempted bytes to read.
     * @param actual     the number of bytes that could be read.
     */
    @Override
    public void lastBytesRead(int attempted, int actual) {
        // NOOP.
    }

    /**
     * Return the next buffer size of each {@link ByteBuf} that is put into the buffer ring.
     *
     * @return  the next size.
     */
    protected abstract int nextBufferSize();
}
