/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel;

import java.util.ArrayList;
import java.util.List;

import static io.netty5.util.internal.ObjectUtil.checkPositive;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * The {@link ReadHandleFactory} that automatically increases and
 * decreases the predicted buffer size on feed back.
 * <p>
 * It gradually increases the expected number of readable bytes if the previous
 * read fully filled the allocated buffer.  It gradually decreases the expected
 * number of readable bytes if the read operation was unable to fill a certain
 * amount of the allocated buffer two times consecutively.  Otherwise, it keeps
 * returning the same prediction.
 */
public class AdaptiveReadHandleFactory extends MaxMessagesReadHandleFactory {

    static final int DEFAULT_MINIMUM = 64;
    // Use an initial value that is bigger than the common MTU of 1500
    static final int DEFAULT_INITIAL = 2048;
    static final int DEFAULT_MAXIMUM = 65536;

    private static final int INDEX_INCREMENT = 4;
    private static final int INDEX_DECREMENT = 1;

    private static final int[] SIZE_TABLE;

    static {
        List<Integer> sizeTable = new ArrayList<>();
        for (int i = 16; i < 512; i += 16) {
            sizeTable.add(i);
        }

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

    private static final class ReadHandleImpl extends MaxMessageReadHandle {
        private final int minIndex;
        private final int maxIndex;
        private final int minCapacity;
        private final int maxCapacity;
        private int index;
        private int nextReceiveBufferSize;
        private boolean decreaseNow;

        private int totalBytesRead;

        ReadHandleImpl(int maxMessagesPerRead, int minIndex, int maxIndex, int initialIndex,
                       int minCapacity, int maxCapacity) {
            super(maxMessagesPerRead);
            this.minIndex = minIndex;
            this.maxIndex = maxIndex;

            index = initialIndex;
            nextReceiveBufferSize = max(SIZE_TABLE[index], minCapacity);
            this.minCapacity = minCapacity;
            this.maxCapacity = maxCapacity;
        }

        @Override
        public boolean lastRead(int attemptedBytesRead, int actualBytesRead, int numMessagesRead) {
            // If we read as much as we asked for we should check if we need to ramp up the size of our next guess.
            // This helps adjust more quickly when large amounts of data is pending and can avoid going back to
            // the selector to check for more data. Going back to the selector can add significant latency for large
            // data transfers.
            if (attemptedBytesRead == actualBytesRead) {
                record(actualBytesRead);
            }
            if (actualBytesRead > 0) {
                totalBytesRead += actualBytesRead;
            }
            return super.lastRead(attemptedBytesRead, actualBytesRead, numMessagesRead);
        }

        @Override
        public int prepareRead() {
            return nextReceiveBufferSize * super.prepareRead();
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

        @Override
        public void readComplete() {
            record(totalBytesRead());
            totalBytesRead = 0;
            super.readComplete();
        }

        private int totalBytesRead() {
            return totalBytesRead < 0 ? Integer.MAX_VALUE : totalBytesRead;
        }
    }

    private final int minIndex;
    private final int maxIndex;
    private final int initialIndex;
    private final int minCapacity;
    private final int maxCapacity;

    /**
     * Creates a new predictor with the default parameters.  With the default
     * parameters, the expected buffer size starts from {@code 1024}, does not
     * go down below {@code 64}, and does not go up above {@code 65536}.
     */
    public AdaptiveReadHandleFactory() {
        this(1);
    }

    /**
     * Creates a new predictor with the default parameters.  With the default
     * parameters, the expected buffer size starts from {@code 1024}, does not
     * go down below {@code 64}, and does not go up above {@code 65536}.
     *
     * @param maxMessagesPerRead    the maximum number of messages to read per read loop invocation.
     */
    public AdaptiveReadHandleFactory(int maxMessagesPerRead) {
        this(maxMessagesPerRead, DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM);
    }

    /**
     * Creates a new predictor with the specified parameters.
     *
     * @param maxMessagesPerRead    the maximum number of messages to read per read loop invocation.
     * @param minimum               the inclusive lower bound of the expected buffer size
     * @param initial               the initial buffer size when no feedback was received
     * @param maximum               the inclusive upper bound of the expected buffer size
     */
    public AdaptiveReadHandleFactory(int maxMessagesPerRead, int minimum, int initial, int maximum) {
        super(maxMessagesPerRead);
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
            this.initialIndex = initialIndex - 1;
        } else {
            this.initialIndex = initialIndex;
        }
        this.minCapacity = minimum;
        this.maxCapacity = maximum;
    }

    @Override
    public MaxMessageReadHandle newMaxMessageHandle(int maxMessagesPerRead) {
        return new ReadHandleImpl(maxMessagesPerRead, minIndex, maxIndex, initialIndex, minCapacity, maxCapacity);
    }
}
