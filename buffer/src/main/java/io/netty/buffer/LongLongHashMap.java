/*
 * Copyright 2020 The Netty Project
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
package io.netty.buffer;

/**
 * Internal primitive map implementation that is specifically optimised for the runs availability map use case in {@link
 * PoolChunk}.
 */
final class LongLongHashMap {
    private static final int MASK_TEMPLATE = ~1;
    private int mask;
    private long[] array;
    private int maxProbe;
    private long zeroVal;
    private final long emptyVal;

    LongLongHashMap(long emptyVal) {
        this.emptyVal = emptyVal;
        zeroVal = emptyVal;
        int initialSize = 32;
        array = new long[initialSize];
        mask = initialSize - 1;
        computeMaskAndProbe();
    }

    public long put(long key, long value) {
        if (key == 0) {
            long prev = zeroVal;
            zeroVal = value;
            return prev;
        }

        for (;;) {
            int index = index(key);
            for (int i = 0; i < maxProbe; i++) {
                long existing = array[index];
                if (existing == key || existing == 0) {
                    long prev = existing == 0? emptyVal : array[index + 1];
                    array[index] = key;
                    array[index + 1] = value;
                    for (; i < maxProbe; i++) { // Nerf any existing misplaced entries.
                        index = index + 2 & mask;
                        if (array[index] == key) {
                            array[index] = 0;
                            prev = array[index + 1];
                            break;
                        }
                    }
                    return prev;
                }
                index = index + 2 & mask;
            }
            expand(); // Grow array and re-hash.
        }
    }

    public void remove(long key) {
        if (key == 0) {
            zeroVal = emptyVal;
            return;
        }
        int index = index(key);
        for (int i = 0; i < maxProbe; i++) {
            long existing = array[index];
            if (existing == key) {
                array[index] = 0;
                break;
            }
            index = index + 2 & mask;
        }
    }

    public long get(long key) {
        if (key == 0) {
            return zeroVal;
        }
        int index = index(key);
        for (int i = 0; i < maxProbe; i++) {
            long existing = array[index];
            if (existing == key) {
                return array[index + 1];
            }
            index = index + 2 & mask;
        }
        return emptyVal;
    }

    private int index(long key) {
        // Hash with murmur64, and mask.
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= key >>> 33;
        return (int) key & mask;
    }

    private void expand() {
        long[] prev = array;
        array = new long[prev.length * 2];
        computeMaskAndProbe();
        for (int i = 0; i < prev.length; i += 2) {
            long key = prev[i];
            if (key != 0) {
                long val = prev[i + 1];
                put(key, val);
            }
        }
    }

    private void computeMaskAndProbe() {
        int length = array.length;
        mask = length - 1 & MASK_TEMPLATE;
        maxProbe = (int) Math.log(length);
    }
}
