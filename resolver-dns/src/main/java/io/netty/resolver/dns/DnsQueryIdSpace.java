/*
 * Copyright 2024 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.util.internal.MathUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.Random;

/**
 * Special data-structure that will allow to retrieve the next query id to use, while still guarantee some sort
 * of randomness.
 * The query id will be between 0 (inclusive) and 65535 (inclusive) as defined by the RFC.
 */
final class DnsQueryIdSpace {
    private static final int MAX_ID = 65535;

    // Holds all possible ids which are stored as unsigned shorts
    private final short[] ids = new short[MAX_ID + 1];
    private final int mask = ids.length - 1;
    private int head;
    private int tail;
    private int count;

    DnsQueryIdSpace() {
        assert ids.length == MathUtil.findNextPositivePowerOfTwo(MAX_ID);
        for (int v = 0; v <= MAX_ID; v++) {
            pushId(v);
        }
    }

    /**
     * Returns the next ID to use for a query or {@code -1} if there is none left to use.
     *
     * @return  next id to use.
     */
    int nextId() {
        assert count >= 0;
        if (count == 0) {
            return -1;
        }
        short id = ids[head];
        head = (head + 1) & mask;
        count--;

        return id & 0xFFFF;
    }

    /**
     * Push back the id, so it can be used again for the next query.
     *
     * @param id    the id.
     */
    void pushId(int id) {
        if (count == ids.length) {
            throw new IllegalStateException("overflow");
        }

        assert id <= MAX_ID && id >= 0;
        if (tail == head) {
            ids[tail] = (short) id;
        } else {
            // Let's ensure our ids will be returned in a random fashion by not always add the id to the tail.
            final int idx;
            Random random = PlatformDependent.threadLocalRandom();
            if (tail < head) {
                if (tail != 0 && random.nextBoolean()) {
                    idx = random.nextInt(tail);
                } else {
                    idx = random.nextInt(ids.length - head) + head;
                }
            } else {
                idx = random.nextInt(tail - head) + head;
            }
            short otherId = ids[idx];
            ids[idx] = (short) id;
            ids[tail] = otherId;
            assert otherId != (short) id;
        }
        tail = (tail + 1) & mask;
        count++;
    }

    /**
     * Return how much more usable ids are left.
     *
     * @return  the number of ids that are left for usage.
     */
    int usableIds() {
        return count;
    }

    /**
     * Return the maximum number of ids that are supported.
     *
     * @return  the maximum number of ids.
     */
    int maxUsableIds() {
        return ids.length;
    }
}
