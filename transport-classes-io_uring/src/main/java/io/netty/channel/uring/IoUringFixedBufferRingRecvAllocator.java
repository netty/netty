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
import io.netty.util.internal.ObjectUtil;

import java.util.Objects;

/**
 * {@link IoUringBufferRingAllocator} implementation which uses a fixed size for the buffers that are returned by
 * {@link #allocate()}.
 */
public final class IoUringFixedBufferRingRecvAllocator implements IoUringBufferRingAllocator {
    private final ByteBufAllocator allocator;
    private final int bufferSize;

    /**
     * Create a new instance
     *
     * @param allocator     the {@link ByteBufAllocator} to use.
     * @param bufferSize    the size of the buffers that are allocated.
     */
    public IoUringFixedBufferRingRecvAllocator(ByteBufAllocator allocator, int bufferSize) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.bufferSize = ObjectUtil.checkPositive(bufferSize, "bufferSize");
    }

    /**
     * Create a new instance
     *
     * @param bufferSize    the size of the buffers that are allocated.
     */
    public IoUringFixedBufferRingRecvAllocator(int bufferSize) {
        this(ByteBufAllocator.DEFAULT, bufferSize);
    }

    @Override
    public ByteBuf allocate() {
        return allocator.directBuffer(bufferSize);
    }

    @Override
    public void lastBytesRead(int bytes) {
        // NOOP.
    }
}
