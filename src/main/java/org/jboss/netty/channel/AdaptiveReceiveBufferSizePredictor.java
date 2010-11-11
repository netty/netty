/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link ReceiveBufferSizePredictor} that automatically increases and
 * decreases the predicted buffer size on feed back.
 * <p>
 * It gradually increases the expected number of readable bytes if the previous
 * read fully filled the allocated buffer.  It gradually decreases the expected
 * number of readable bytes if the read operation was not able to fill a certain
 * amount of the allocated buffer two times consecutively.  Otherwise, it keeps
 * returning the same prediction.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class AdaptiveReceiveBufferSizePredictor implements
        ReceiveBufferSizePredictor {

    static final int DEFAULT_MINIMUM = 64;
    static final int DEFAULT_INITIAL = 1024;
    static final int DEFAULT_MAXIMUM = 65536;

    private static final int INDEX_INCREMENT = 4;
    private static final int INDEX_DECREMENT = 1;

    private static final int[] SIZE_TABLE;

    static {
        List<Integer> sizeTable = new ArrayList<Integer>();
        for (int i = 1; i <= 8; i ++) {
            sizeTable.add(i);
        }

        for (int i = 4; i < 32; i ++) {
            long v = 1L << i;
            long inc = v >>> 4;
            v -= inc << 3;

            for (int j = 0; j < 8; j ++) {
                v += inc;
                if (v > Integer.MAX_VALUE) {
                    sizeTable.add(Integer.MAX_VALUE);
                } else {
                    sizeTable.add((int) v);
                }
            }
        }

        SIZE_TABLE = new int[sizeTable.size()];
        for (int i = 0; i < SIZE_TABLE.length; i ++) {
            SIZE_TABLE[i] = sizeTable.get(i);
        }
    }

    private static int getSizeTableIndex(final int size) {
        if (size <= 16) {
            return size - 1;
        }

        int bits = 0;
        int v = size;
        do {
            v >>>= 1;
            bits ++;
        } while (v != 0);

        final int baseIdx = bits << 3;
        final int startIdx = baseIdx - 18;
        final int endIdx = baseIdx - 25;

        for (int i = startIdx; i >= endIdx; i --) {
            if (size >= SIZE_TABLE[i]) {
                return i;
            }
        }

        throw new Error("shouldn't reach here; please file a bug report.");
    }

    private final int minIndex;
    private final int maxIndex;
    private int index;
    private int nextReceiveBufferSize;
    private boolean decreaseNow;

    /**
     * Creates a new predictor with the default parameters.  With the default
     * parameters, the expected buffer size starts from {@code 1024}, does not
     * go down below {@code 64}, and does not go up above {@code 65536}.
     */
    public AdaptiveReceiveBufferSizePredictor() {
        this(DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM);
    }

    /**
     * Creates a new predictor with the specified parameters.
     *
     * @param minimum  the inclusive lower bound of the expected buffer size
     * @param initial  the initial buffer size when no feed back was received
     * @param maximum  the inclusive upper bound of the expected buffer size
     */
    public AdaptiveReceiveBufferSizePredictor(int minimum, int initial, int maximum) {
        if (minimum <= 0) {
            throw new IllegalArgumentException("minimum: " + minimum);
        }
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

        index = getSizeTableIndex(initial);
        nextReceiveBufferSize = SIZE_TABLE[index];
    }

    public int nextReceiveBufferSize() {
        return nextReceiveBufferSize;
    }

    public void previousReceiveBufferSize(int previousReceiveBufferSize) {
        if (previousReceiveBufferSize <= SIZE_TABLE[Math.max(0, index - INDEX_DECREMENT - 1)]) {
            if (decreaseNow) {
                index = Math.max(index - INDEX_DECREMENT, minIndex);
                nextReceiveBufferSize = SIZE_TABLE[index];
                decreaseNow = false;
            } else {
                decreaseNow = true;
            }
        } else if (previousReceiveBufferSize >= nextReceiveBufferSize) {
            index = Math.min(index + INDEX_INCREMENT, maxIndex);
            nextReceiveBufferSize = SIZE_TABLE[index];
            decreaseNow = false;
        }
    }
}
