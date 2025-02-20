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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * {@link IoUringBufferRingAllocator} implementation which uses an adaptive strategy to allocate buffers, which
 * will decrease / increase the buffer size depending on if the allocated buffers were completely used or not before.
 */
public final class IoUringAdaptiveBufferRingAllocator implements IoUringBufferRingAllocator {

    public static final int DEFAULT_MINIMUM = 1024;
    public static final int DEFAULT_INITIAL = 4096;
    public static final int DEFAULT_MAXIMUM = 65536;

    private static final int INDEX_INCREMENT = 4;
    private static final int INDEX_DECREMENT = 1;

    private static final int[] SIZE_TABLE;

    static {
        List<Integer> sizeTable = new ArrayList<Integer>();
        for (int i = 16; i < 512; i += 16) {
            sizeTable.add(i);
        }

        // Suppress a warning since i becomes negative when an integer overflow happens
        for (int i = 512; i > 0; i <<= 1) {
            sizeTable.add(i);
        }

        SIZE_TABLE = new int[sizeTable.size()];
        for (int i = 0; i < SIZE_TABLE.length; i ++) {
            SIZE_TABLE[i] = sizeTable.get(i);
        }
    }

    private static int getSizeTableIndex(final int size) {
        for (int low = 0, high = SIZE_TABLE.length - 1;;) {
            if (high < low) {
                return low;
            }
            if (high == low) {
                return high;
            }

            int mid = low + high >>> 1;
            int a = SIZE_TABLE[mid];
            int b = SIZE_TABLE[mid + 1];
            if (size > b) {
                low = mid + 1;
            } else if (size < a) {
                high = mid - 1;
            } else if (size == a) {
                return mid;
            } else {
                return mid + 1;
            }
        }
    }

    private final ByteBufAllocator allocator;
    private final int minIndex;
    private final int maxIndex;
    private final int minCapacity;
    private final int maxCapacity;
    private int index;
    private int nextReceiveBufferSize;
    private boolean decreaseNow;

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
        checkPositive(minimum, "minimum");
        if (initial < minimum) {
            throw new IllegalArgumentException("initial: " + initial);
        }
        if (maximum < initial) {
            throw new IllegalArgumentException("maximum: " + maximum);
        }

        int minIndex = getSizeTableIndex(minimum);
        if (SIZE_TABLE[minIndex] < minimum) {
            this.minIndex = minIndex + 1;
        } else {
            this.minIndex = minIndex;
        }

        int maxIndex = getSizeTableIndex(maximum);
        if (SIZE_TABLE[maxIndex] > maximum) {
            this.maxIndex = maxIndex - 1;
        } else {
            this.maxIndex = maxIndex;
        }

        int initialIndex = getSizeTableIndex(initial);
        if (SIZE_TABLE[initialIndex] > initial) {
            this.index = initialIndex - 1;
        } else {
            this.index = initialIndex;
        }
        this.minCapacity = minimum;
        this.maxCapacity = maximum;

        nextReceiveBufferSize = max(SIZE_TABLE[index], minCapacity);
    }

    @Override
    public ByteBuf allocate() {
        return allocator.directBuffer(nextReceiveBufferSize);
    }

    @Override
    public void lastBytesRead(int attempted, int actual) {
        // If we read as much as we asked for we should check if we need to ramp up the size of our next guess.
        // This helps adjust more quickly when large amounts of data is pending and can avoid going back to
        // the selector to check for more data. Going back to the selector can add significant latency for large
        // data transfers.
        if (attempted == actual) {
            record(actual);
        }
    }

    private void record(int actualReadBytes) {
        if (actualReadBytes <= SIZE_TABLE[max(0, index - INDEX_DECREMENT)]) {
            if (decreaseNow) {
                index = max(index - INDEX_DECREMENT, minIndex);
                nextReceiveBufferSize = max(SIZE_TABLE[index], minCapacity);
                decreaseNow = false;
            } else {
                decreaseNow = true;
            }
        } else if (actualReadBytes >= nextReceiveBufferSize) {
            index = min(index + INDEX_INCREMENT, maxIndex);
            nextReceiveBufferSize = min(SIZE_TABLE[index], maxCapacity);
            decreaseNow = false;
        }
    }
}
