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

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.ObjectUtil;


/**
 * {@link IoUringBufferRingAllocator} implementation which uses a fixed size for the buffers that are returned by
 * {@link #allocate()}.
 */
public final class IoUringFixedBufferRingAllocator extends AbstractIoUringBufferRingAllocator {
    private final int bufferSize;

    /**
     * Create a new instance
     *
     * @param allocator         the {@link ByteBufAllocator} to use.
     * @param largeAllocation   {@code true} if we should do a large allocation for the whole buffer ring
     *                          and then slice out the buffers or {@code false} if we should do one allocation
     *                          per buffer.
     * @param bufferSize        the size of the buffers that are allocated.
     */
    public IoUringFixedBufferRingAllocator(ByteBufAllocator allocator, boolean largeAllocation, int bufferSize) {
        super(allocator, largeAllocation);
        this.bufferSize = ObjectUtil.checkPositive(bufferSize, "bufferSize");
    }

    /**
     * Create a new instance
     *
     * @param allocator     the {@link ByteBufAllocator} to use.
     * @param bufferSize    the size of the buffers that are allocated.
     */
    public IoUringFixedBufferRingAllocator(ByteBufAllocator allocator, int bufferSize) {
        this(allocator, false, bufferSize);
    }

    /**
     * Create a new instance
     *
     * @param bufferSize    the size of the buffers that are allocated.
     */
    public IoUringFixedBufferRingAllocator(int bufferSize) {
        this(ByteBufAllocator.DEFAULT, bufferSize);
    }

    @Override
    protected int nextBufferSize() {
        return bufferSize;
    }
}
