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
package io.netty.channel.uring;

import java.util.concurrent.atomic.AtomicLong;

final class PendingOpSlots {
    private static final long INVALID_ID = 0;
    private static final long FIRST_NORMAL_SEQUENCE = 3;
    private static final long EVENT_LOOP_SEQUENCE_STEP = 2;
    private static final long OFF_LOOP_SEQUENCE_START = FIRST_NORMAL_SEQUENCE + 1;

    private int[] registrationIds;
    private byte[] ops;
    private long[] userDatas;
    private long[] activeSequences;
    private int mask;
    private long nextEventLoopSequence = FIRST_NORMAL_SEQUENCE;
    private final AtomicLong nextOffLoopSequence = new AtomicLong(OFF_LOOP_SEQUENCE_START);

    PendingOpSlots(int initialCapacity) {
        int capacity = normalizeCapacity(initialCapacity);
        registrationIds = new int[capacity];
        ops = new byte[capacity];
        userDatas = new long[capacity];
        activeSequences = new long[capacity];
        mask = capacity - 1;
    }

    long nextToken(boolean inEventLoop) {
        long sequence;
        if (inEventLoop) {
            sequence = nextEventLoopSequence;
            nextEventLoopSequence += EVENT_LOOP_SEQUENCE_STEP;
        } else {
            sequence = nextOffLoopSequence.getAndAdd(EVENT_LOOP_SEQUENCE_STEP);
        }
        if (sequence <= 0) {
            throw new IllegalStateException("slow path sequence overflow");
        }
        return token(sequence);
    }

    void registerNormal(long token, int registrationId, byte op, long userData) {
        long sequence = tokenSequence(token);
        int slot = ensureWritableSlot(sequence);
        registrationIds[slot] = registrationId;
        ops[slot] = op;
        userDatas[slot] = userData;
        activeSequences[slot] = sequence;
    }

    int findSlot(long token) {
        long sequence = tokenSequence(token);
        if (sequence == INVALID_ID) {
            return -1;
        }
        int slot = slot(sequence, mask);
        return activeSequences[slot] == sequence ? slot : -1;
    }

    int registrationId(int slot) {
        return registrationIds[slot];
    }

    byte op(int slot) {
        return ops[slot];
    }

    long userData(int slot) {
        return userDatas[slot];
    }

    void release(long token) {
        int slot = findSlot(token);
        if (slot != -1) {
            release(slot);
        }
    }

    void release(int slot) {
        registrationIds[slot] = 0;
        ops[slot] = 0;
        userDatas[slot] = 0;
        activeSequences[slot] = INVALID_ID;
    }

    private int ensureWritableSlot(long sequence) {
        int mask = this.mask;
        int slot = slot(sequence, mask);
        while (activeSequences[slot] != INVALID_ID) {
            resize();
            mask = this.mask;
            slot = slot(sequence, mask);
        }
        return slot;
    }

    private void resize() {
        int oldCapacity = activeSequences.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity <= 0) {
            throw new IllegalStateException("slow path table overflow");
        }

        int[] oldRegistrationIds = registrationIds;
        byte[] oldOps = ops;
        long[] oldUserDatas = userDatas;
        long[] oldActiveSequences = activeSequences;

        int[] newRegistrationIds = new int[newCapacity];
        byte[] newOps = new byte[newCapacity];
        long[] newUserDatas = new long[newCapacity];
        long[] newActiveSequences = new long[newCapacity];

        for (int i = 0; i < oldCapacity; i++) {
            long sequence = oldActiveSequences[i];
            if (sequence == INVALID_ID) {
                continue;
            }
            int newSlot = (sequence & oldCapacity) == 0 ? i : i + oldCapacity;
            newRegistrationIds[newSlot] = oldRegistrationIds[i];
            newOps[newSlot] = oldOps[i];
            newUserDatas[newSlot] = oldUserDatas[i];
            newActiveSequences[newSlot] = sequence;
        }

        registrationIds = newRegistrationIds;
        ops = newOps;
        userDatas = newUserDatas;
        activeSequences = newActiveSequences;
        mask = newCapacity - 1;
    }

    /**
     * sequence is always > 0, so its top bit (bit63) is always 0.
     * sequence layout:
     *   [ 0 | bit62 ... bit32 | bit31 | bit30 ... bit0 ]
     * upperBits = sequence >>> 31:
     *   [ 0 | bit62 ... bit32 | bit31 ]
     * lowerBits = ((int) sequence & Integer.MAX_VALUE) | Integer.MIN_VALUE:
     *   [ 1 | bit30 ... bit0 ]   // negative int, high bit is used as a token marker
     * Final token layout:
     *   [ 0 | bit62 ... bit32 | bit31 ][ 1 | bit30 ... bit0 ]
     * @param sequence original sequence
     * @return real userData
     */
    static long token(long sequence) {
        // We intentionally do not handle sequence wrap-around here.
        // `nextSequence` would need to reach Long.MAX_VALUE and overflow,
        // which is considered practically impossible in this context.
        long upperBits = sequence >>> 31;
        int lowerBits = ((int) sequence & Integer.MAX_VALUE) | Integer.MIN_VALUE;
        return (upperBits << Integer.SIZE) | (lowerBits & 0xFFFFFFFFL);
    }

    private static long tokenSequence(long token) {
        int lowerBits = (int) token;
        if (lowerBits >= 0) {
            return INVALID_ID;
        }
        return (token >>> Integer.SIZE) << 31 | (lowerBits & Integer.MAX_VALUE);
    }

    private static int slot(long sequence, int mask) {
        return (int) sequence & mask;
    }

    private static int normalizeCapacity(int requestedCapacity) {
        int capacity = 1;
        while (capacity < requestedCapacity) {
            capacity <<= 1;
            if (capacity <= 0) {
                throw new IllegalArgumentException("requestedCapacity overflow");
            }
        }
        return capacity;
    }
}
