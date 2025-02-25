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
import io.netty.util.internal.AdaptiveCalculator;

import java.util.Objects;

/**
 * {@link IoUringBufferRingAllocator} implementation which uses an adaptive strategy to allocate buffers, which
 * will decrease / increase the buffer size depending on if the allocated buffers were completely used or not before.
 */
public final class IoUringAdaptiveBufferRingAllocator implements IoUringBufferRingAllocator {

    public static final int DEFAULT_MINIMUM = 1024;
    public static final int DEFAULT_INITIAL = 4096;
    public static final int DEFAULT_MAXIMUM = 65536;

    private final ByteBufAllocator allocator;
    private final AdaptiveCalculator calculator;

    public IoUringAdaptiveBufferRingAllocator() {
        this(ByteBufAllocator.DEFAULT);
    }

    /**
     * Creates new instance.
     *
     * @param allocator the {@link ByteBufAllocator} to use.
     */
    public IoUringAdaptiveBufferRingAllocator(ByteBufAllocator allocator) {
        this(allocator, DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM);
    }

    /**
     * Creates new instance.
     *
     * @param allocator the {@link ByteBufAllocator} to use for the allocations
     * @param minimum   the inclusive lower bound of the expected buffer size
     * @param initial   the initial buffer size when no feed back was received
     * @param maximum   the inclusive upper bound of the expected buffer size
     */
    public IoUringAdaptiveBufferRingAllocator(ByteBufAllocator allocator, int minimum, int initial, int maximum) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.calculator = new AdaptiveCalculator(minimum, initial, maximum);
    }

    @Override
    public ByteBuf allocate() {
        return allocator.directBuffer(calculator.nextSize());
    }

    @Override
    public void lastBytesRead(int attempted, int actual) {
        // If we read as much as we asked for we should check if we need to ramp up the size of our next guess.
        // This helps adjust more quickly when large amounts of data is pending and can avoid going back to
        // the selector to check for more data. Going back to the selector can add significant latency for large
        // data transfers.
        if (attempted == actual) {
            calculator.record(actual);
        }
    }
}
