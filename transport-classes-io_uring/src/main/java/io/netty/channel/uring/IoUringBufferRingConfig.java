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

import io.netty.util.internal.ObjectUtil;

import java.util.Objects;

/**
 * Configuration class for an {@link IoUringBufferRing}.
 * It will configure the buffer ring size, buffer group id and the chunk size.
 */
public final class IoUringBufferRingConfig {
    private final short bgId;
    private final short bufferRingSize;
    private final int batchSize;
    private final int maxUnreleasedBuffers;
    private final boolean incremental;
    private final IoUringBufferRingAllocator allocator;

    /**
     * Create a new configuration.
     *
     * @param bgId                  the buffer group id to use (must be non-negative).
     * @param bufferRingSize        the size of the ring
     * @param maxUnreleasedBuffers  the maximum buffers that were allocated out of this buffer ring and are
     *                              unreleased yet. Once this threshold is hit the usage of the buffer ring will
     *                              be temporary disabled.
     * @param allocator             the {@link IoUringBufferRingAllocator} to use to allocate
     *                              {@link io.netty.buffer.ByteBuf}s.
     */
    public IoUringBufferRingConfig(short bgId, short bufferRingSize, int maxUnreleasedBuffers,
                                   IoUringBufferRingAllocator allocator) {
        this(bgId, bufferRingSize, bufferRingSize / 2, maxUnreleasedBuffers,
                IoUring.isRegisterBufferRingIncSupported(), allocator);
    }

    /**
     * Create a new configuration.
     *
     * @param bgId                  the buffer group id to use (must be non-negative).
     * @param bufferRingSize        the size of the ring
     * @param batchSize             the size of the batch on how many buffers are added everytime we need to expand the
     *                              buffer ring.
     * @param maxUnreleasedBuffers  the maximum buffers that can be allocated out of this buffer ring and are
     *                              unreleased yet. Once this threshold is hit the usage of the buffer ring will
     *                              be temporarily disabled.
     * @param incremental           {@code true} if the buffer ring is using incremental buffer consumption.
     * @param allocator             the {@link IoUringBufferRingAllocator} to use to allocate
     *                              {@link io.netty.buffer.ByteBuf}s.
     */
    public IoUringBufferRingConfig(short bgId, short bufferRingSize, int batchSize, int maxUnreleasedBuffers,
                                   boolean incremental, IoUringBufferRingAllocator allocator) {
        this.bgId = (short) ObjectUtil.checkPositiveOrZero(bgId, "bgId");
        this.bufferRingSize = checkBufferRingSize(bufferRingSize);
        this.batchSize = ObjectUtil.checkInRange(batchSize, 1, bufferRingSize, "batchSize");
        this.maxUnreleasedBuffers = ObjectUtil.checkInRange(
                maxUnreleasedBuffers, bufferRingSize, Integer.MAX_VALUE, "maxUnreleasedBuffers");
        if (incremental && !IoUring.isRegisterBufferRingIncSupported()) {
            throw new IllegalArgumentException("Incremental buffer ring is not supported");
        }
        this.incremental = incremental;
        this.allocator = ObjectUtil.checkNotNull(allocator, "allocator");
    }

    /**
     * Returns the buffer group id to use.
     *
     * @return the buffer group id to use.
     */
    public short bufferGroupId() {
        return bgId;
    }

    /**
     * Returns the size of the ring.
     *
     * @return the size of the ring.
     */
    public short bufferRingSize() {
        return bufferRingSize;
    }

    /**
     * Returns the size of the batch on how many buffers are added everytime we need to expand the buffer ring.
     *
     * @return batch size.
     */
    public int batchSize() {
        return batchSize;
    }

    /**
     * Returns the maximum buffers that can be allocated out of this buffer ring and are
     * unreleased yet
     *
     * @return the max unreleased buffers.
     */
    public int maxUnreleasedBuffers() {
        return maxUnreleasedBuffers;
    }

    /**
     * Returns the {@link IoUringBufferRingAllocator} to use to allocate {@link io.netty.buffer.ByteBuf}s.
     *
     * @return  the allocator.
     */
    public IoUringBufferRingAllocator allocator() {
        return allocator;
    }

    /**
     * Returns true if <a href="https://github.com/axboe/liburing/wiki/
     * What's-new-with-io_uring-in-6.11-and-6.12#incremental-provided-buffer-consumption">incremental mode</a>
     * should be used for the buffer ring.
     *
     * @return {@code true} if incremental mode is used, {@code false} otherwise.
     */
    public boolean isIncremental() {
        return incremental;
    }

    private static short checkBufferRingSize(short bufferRingSize) {
        if (bufferRingSize < 1) {
            throw new IllegalArgumentException("bufferRingSize: " + bufferRingSize + " (expected: > 0)");
        }

        boolean isPowerOfTwo = (bufferRingSize & (bufferRingSize - 1)) == 0;
        if (!isPowerOfTwo) {
            throw new IllegalArgumentException("bufferRingSize: " + bufferRingSize + " (expected: power of 2)");
        }
        return bufferRingSize;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IoUringBufferRingConfig that = (IoUringBufferRingConfig) o;
        return bgId == that.bgId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(bgId);
    }
}
