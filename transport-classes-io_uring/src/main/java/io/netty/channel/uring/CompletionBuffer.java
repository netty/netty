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

import io.netty.util.internal.MathUtil;

/**
 * A buffer for completion events.
 */
final class CompletionBuffer {
    private final CompletionCallback callback = this::add;
    // long[(tail + 1) % capacity] holds res and flags (packed as long) and long[(tail + 2) % capacity] the udata.
    private final long[] array;
    private final int capacity;
    private final int mask;
    private final long tombstone;
    private int size;
    private int head;
    private int tail = -1;

    CompletionBuffer(int numCompletions, long tombstone) {
        capacity = MathUtil.findNextPositivePowerOfTwo(numCompletions * 2);
        array = new long[capacity];
        mask = capacity - 1;
        for (int i = 1; i < capacity; i++) {
            if (i % 2 == 0) {
                array[i] = tombstone;
            }
        }
        this.tombstone = tombstone;
    }

    private boolean add(int res, int flags, long udata) {
        if (udata == tombstone) {
            throw new IllegalStateException("udata can't be the same as the tombstone");
        }
        // Pack res and flag into the first slot.
        array[combinedIdx(tail + 1)] = (((long) res) << 32) | (flags & 0xffffffffL);
        array[udataIdx(tail + 1)] = udata;

        tail += 2;
        size += 2;
        return size < capacity;
    }

    /**
     * Drain completions from the given {@link CompletionQueue}.
     *
     * @param queue the queue to drain from.
     * @return      {@code true} if the whole queue was drained, {@code false} otherwise.
     */
    boolean drain(CompletionQueue queue) {
        queue.process(callback);
        return !queue.hasCompletions();
    }

    /**
     * Process buffered completions via the given {@link CompletionCallback}.
     *
     * @param callback  the callback to use.
     * @return          the number of processed completions.
     */
    int processNow(CompletionCallback callback) {
        int i = 0;

        boolean processing = true;
        do {
            if (size == 0) {
                break;
            }
            long combined = array[combinedIdx(head)];
            long udata = array[udataIdx(head)];

            head += 2;
            size -= 2;
            // Skipping over tombstones
            if (udata != tombstone) {
                processing = handle(callback, combined, udata);
                i++;
            }
        } while (processing);
        return i;
    }

    private int combinedIdx(int idx) {
        return idx & mask;
    }

    private int udataIdx(int idx) {
        return (idx + 1) & mask;
    }

    private static boolean handle(CompletionCallback callback, long combined, long udata) {
        int res = (int) (combined >> 32);
        int flags = (int) combined;
        return callback.handle(res, flags, udata);
    }
}
