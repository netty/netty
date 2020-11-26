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
    private static final int MAX_PROBE = 4;
    private int mask = 31;
    private long[] array = new long[mask + 1];
    private long zeroVal;

    public long put(long key, long value) {
        if (key == 0) {
            long prev = zeroVal;
            zeroVal = value;
            return prev;
        }

        for (;;) {
            int index = index(key);
            for (int i = 0; i < MAX_PROBE; i++) {
                long existing = array[index];
                if (existing == key || existing == 0) {
                    long prev = array[index + 1];
                    array[index] = key;
                    array[index + 1] = value;
                    return prev;
                }
                index = index + 2 & mask;
            }
            expand(); // Grow array and re-hash.
        }
    }

    public void remove(long key) {
        if (key == 0) {
            zeroVal = 0;
            return;
        }
        int index = index(key);
        for (int i = 0; i < MAX_PROBE; i++) {
            long existing = array[index];
            if (existing == key) {
                array[index] = 0;
                array[index + 1] = 0;
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
        for (int i = 0; i < MAX_PROBE; i++) {
            long existing = array[index];
            if (existing == key) {
                return array[index + 1];
            }
            index = index + 2 & mask;
        }
        return -1;
    }

    private int index(long key) {
        return (int) (key << 1 & mask);
    }

    private void expand() {
        long[] prev = array;
        int newSize = prev.length * 2;
        mask = newSize - 1;
        array = new long[newSize];
        for (int i = 0; i < prev.length >> 1; i += 2) {
            long key = prev[i];
            long val = prev[i + 1];
            put(key, val);
        }
    }
}
