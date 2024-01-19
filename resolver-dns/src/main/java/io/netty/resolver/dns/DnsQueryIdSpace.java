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
        short id = ids[count - 1];
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
        // pick a slot for our index, and whatever was in that slot before will get moved to the tail.
        Random random = PlatformDependent.threadLocalRandom();
        int insertionPosition = random.nextInt(count + 1);
        ids[count] = ids[insertionPosition];
        ids[insertionPosition] = (short) id;
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
