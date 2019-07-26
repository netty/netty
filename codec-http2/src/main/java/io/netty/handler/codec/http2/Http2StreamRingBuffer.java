/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.util.internal.MathUtil;

import java.util.Arrays;

/**
 * Fixed size ring-buffer for {@code HTTP2} streams.
 */
final class Http2StreamRingBuffer {

    private final int[] elements;
    private int oldestIdx;

    Http2StreamRingBuffer() {
        this(32);
    }

    Http2StreamRingBuffer(int capacity) {
        elements = new int[MathUtil.findNextPositivePowerOfTwo(capacity)];
        Arrays.fill(elements, -1);
    }

    int capacity() {
        return elements.length;
    }

    /**
     * Add the given {@code streamId}
     * @param streamId the ID
     * @return {@code true} if could be added, false if it was not added as this ring-buffer already contained it.
     */
    boolean add(int streamId) {
        assert streamId >= 0;
        // We expect elements to be very small and also to have the latest streamId at the beginning, so just scan the
        // array.
        for (int i = 0; i < elements.length; i++) {
            int id = elements[i];
            if (id == streamId) {
                return false;
            }
            if (id == -1) {
                // Still some space left
                elements[i] = streamId;
                return true;
            }
        }

        // Just override the oldest entry.
        elements[oldestIdx++] = streamId;
        if (oldestIdx == elements.length) {
            // We need to start from 0 again.
            oldestIdx = 0;
        }

        return true;
    }
}
