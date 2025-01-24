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
 * Configuration class for an {@link IoUringBufferRing}.
 * It will configure the buffer ring size, buffer group id and the chunk size.
 */
public final class IoUringBufferRingConfig {

    private final short bgId;
    private final short bufferRingSize;
    private final int chunkSize;
    private final ByteBufAllocator allocator;
    private final int initSize;

    /**
     * Create a new configuration.
     *
     * @param bgId              the buffer group id to use.
     * @param bufferRingSize    the size of the ring
     * @param chunkSize         the chunk size of each {@link io.netty.buffer.ByteBuf} that is allocated out of the
     *                          {@link ByteBufAllocator} to fill the ring.
     * @param allocator         the {@link ByteBufAllocator} to use to allocate {@link io.netty.buffer.ByteBuf}s.
     */
    public IoUringBufferRingConfig(short bgId, short bufferRingSize, int chunkSize, ByteBufAllocator allocator) {
        this(bgId, bufferRingSize, chunkSize, allocator, 0);
    }

    /**
     * Create a new configuration.
     *
     * @param bgId              the buffer group id to use.
     * @param bufferRingSize    the size of the ring
     * @param chunkSize         the chunk size of each {@link io.netty.buffer.ByteBuf} that is allocated out of the
     *                          {@link ByteBufAllocator} to fill the ring.
     * @param allocator         the {@link ByteBufAllocator} to use to allocate {@link io.netty.buffer.ByteBuf}s.
     * @param initSize          the number of buffers that are created during initialization.
     */
    public IoUringBufferRingConfig(short bgId, short bufferRingSize, int chunkSize,
                                   ByteBufAllocator allocator, int initSize) {
        this.bgId = ObjectUtil.checkPositive(bgId, "bgId");
        this.bufferRingSize = checkBufferRingSize(bufferRingSize);
        this.chunkSize = ObjectUtil.checkPositive(chunkSize, "chunkSize");
        this.allocator = ObjectUtil.checkNotNull(allocator, "allocator");
        this.initSize = checkInitSize(initSize, bufferRingSize);
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
     * Returns the chunk size of each {@link io.netty.buffer.ByteBuf} that is allocated out of the
     * {@link ByteBufAllocator} to fill the ring.
     *
     * @return  the chunksize.
     */
    public int chunkSize() {
        return chunkSize;
    }

    /**
     * Returns the {@link ByteBufAllocator} to use to allocate {@link io.netty.buffer.ByteBuf}s.
     *
     * @return  the allocator.
     */
    public ByteBufAllocator allocator() {
        return allocator;
    }

    /**
     * Returns the number of buffers that are created during initialization.
     *
     * @return  init size.
     */
    public int initSize() {
        return initSize;
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

    private static int checkInitSize(int initSize, short bufferRingSize) {
        ObjectUtil.checkPositiveOrZero(initSize, "initSize");
        if (initSize > bufferRingSize) {
            throw new IllegalArgumentException(
                    "initSize: " + initSize + " (expected: <= bufferRingSize: " + bufferRingSize + ')'
            );
        }
        return initSize;
    }
}
