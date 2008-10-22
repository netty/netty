/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import java.util.ArrayList;
import java.util.List;

/**
 * The default {@link ReceiveBufferSizePredictor} implementation.
 * <p>
 * It gradually increases the expected number of readable bytes if the
 * previous read filled the allocated buffer.  It gradually decreases the
 * expected number of readable bytes if the read operation was not able to
 * fill a certain amount of the allocated buffer two times consecutively.
 * Otherwise, it keeps returning the previous prediction.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class DefaultReceiveBufferSizePredictor implements
        ReceiveBufferSizePredictor {

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

    private static final int DEFAULT_MINIMUM = 64;
    private static final int DEFAULT_INITIAL = 1024;
    private static final int DEFAULT_MAXIMUM = 65536;

    private final int minIndex;
    private final int maxIndex;
    private int index;
    private int nextReceiveBufferSize;
    private boolean decreaseNow;

    /**
     * Creates a new predictor with the default parameters.  With the default
     * parameters, the expected buffer size starts from {@code 1024}, doesn't
     * go down below {@code 64}, and doesn't go up above {@code 65536}.
     */
    public DefaultReceiveBufferSizePredictor() {
        this(DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM);
    }

    /**
     * Creates a new predictor with the specified parameters.
     *
     * @param minimum  the inclusive lower bound of the expected buffer size
     * @param initial  the initial buffer size when no feed back was received
     * @param maximum  the inclusive upper bound of the expected buffer size
     */
    public DefaultReceiveBufferSizePredictor(int minimum, int initial, int maximum) {
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
