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
import io.netty.util.internal.MathUtil;
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
    private final boolean batchAllocation;

    /**
     * Create a new configuration.
     *
     * @param bgId                  the buffer group id to use (must be non-negative).
     * @param bufferRingSize        the size of the ring
     * @param maxUnreleasedBuffers  this parameter is ignored by the buffer ring.
     * @param allocator             the {@link IoUringBufferRingAllocator} to use to allocate
     *                              {@link io.netty.buffer.ByteBuf}s.
     * @deprecated                  use {@link Builder}.
     */
    @Deprecated
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
     * @param maxUnreleasedBuffers  this parameter is ignored by the buffer ring.
     * @param incremental           {@code true} if the buffer ring is using incremental buffer consumption.
     * @param allocator             the {@link IoUringBufferRingAllocator} to use to allocate
     *                              {@link io.netty.buffer.ByteBuf}s.
     * @deprecated                  use {@link Builder}.
     */
    @Deprecated
    public IoUringBufferRingConfig(short bgId, short bufferRingSize, int batchSize, int maxUnreleasedBuffers,
                                   boolean incremental, IoUringBufferRingAllocator allocator) {
        this(bgId, bufferRingSize, batchSize, maxUnreleasedBuffers, incremental, allocator, false);
    }

    private IoUringBufferRingConfig(short bgId, short bufferRingSize, int batchSize, int maxUnreleasedBuffers,
                                   boolean incremental, IoUringBufferRingAllocator allocator, boolean batchAllocation) {
        this.bgId = (short) ObjectUtil.checkPositiveOrZero(bgId, "bgId");
        this.bufferRingSize = checkBufferRingSize(bufferRingSize);
        this.batchSize = MathUtil.findNextPositivePowerOfTwo(
                ObjectUtil.checkInRange(batchSize, 1, bufferRingSize, "batchSize"));
        this.maxUnreleasedBuffers = ObjectUtil.checkInRange(
                maxUnreleasedBuffers, bufferRingSize, Integer.MAX_VALUE, "maxUnreleasedBuffers");
        if (incremental && !IoUring.isRegisterBufferRingIncSupported()) {
            throw new IllegalArgumentException("Incremental buffer ring is not supported");
        }
        this.incremental = incremental;
        this.allocator = ObjectUtil.checkNotNull(allocator, "allocator");
        this.batchAllocation = batchAllocation;
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
     * @deprecated will be removed as it as no effect.
     */
    @Deprecated
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
     * Returns {@code true} if the ring should always be filled via a batch allocation or
     * {@code false} if we will try to allocate a new {@link ByteBuf} as we used a buffer from the ring.
     * @return {@code true} if the ring should always be filled via a batch allocation.
     */
    public boolean isBatchAllocation() {
        return batchAllocation;
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private short bgId = -1;
        private short bufferRingSize = -1;
        private int batchSize = -1;
        private boolean incremental = IoUring.isRegisterBufferRingIncSupported();
        private IoUringBufferRingAllocator allocator;
        private boolean batchAllocation;

        /**
         * Set the buffer group id to use.
         *
         * @param bgId  The buffer group id to use.
         * @return      This builder.
         */
        public Builder bufferGroupId(short bgId) {
            this.bgId = bgId;
            return this;
        }

        /**
         * Set the size of the ring.
         *
         * @param bufferRingSize    The size of the ring.
         * @return                  This builder.
         */
        public Builder bufferRingSize(short bufferRingSize) {
            this.bufferRingSize = bufferRingSize;
            return this;
        }

        /**
         * Set the size of the batch on how many buffers are added everytime we need to expand the buffer ring.
         *
         * @param batchSize The batch size.
         * @return          This builder.
         */
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Set the {@link IoUringBufferRingAllocator} to use to allocate {@link ByteBuf}s.
         *
         * @param allocator         The allocator.
         */
        public Builder allocator(IoUringBufferRingAllocator allocator) {
            this.allocator = allocator;
            return this;
        }

        /**
         * Set allocation strategy that is used to allocate {@link ByteBuf}s.
         *
         * @param batchAllocation   {@code true} if the ring should always be filled via a batch allocation or
         *                          {@code false} if we will try to allocate a new {@link ByteBuf} as soon
         *                          as we used a buffer from the ring.
         * @return                  This builder.
         */
        public Builder batchAllocation(boolean batchAllocation) {
            this.batchAllocation = batchAllocation;
            return this;
        }

        /**
         * Set if <a href="https://github.com/axboe/liburing/wiki/
         * What's-new-with-io_uring-in-6.11-and-6.12#incremental-provided-buffer-consumption">incremental mode</a>
         * should be used for the buffer ring.
         *
         * @param incremental   {@code true} if incremental mode is used, {@code false} otherwise.
         * @return              This builder.
         */
        public Builder incremental(boolean incremental) {
            this.incremental = incremental;
            return this;
        }

        /**
         * Create a new {@link IoUringBufferRingConfig}.
         *
         * @return a new config.
         */
        public IoUringBufferRingConfig build() {
            return new IoUringBufferRingConfig(
                    bgId, bufferRingSize, batchSize, Integer.MAX_VALUE, incremental, allocator, batchAllocation);
        }
    }
}
