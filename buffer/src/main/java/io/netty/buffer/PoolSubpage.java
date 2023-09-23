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

package io.netty.buffer;

import java.util.concurrent.locks.ReentrantLock;

import static io.netty.buffer.PoolChunk.RUN_OFFSET_SHIFT;
import static io.netty.buffer.PoolChunk.SIZE_SHIFT;
import static io.netty.buffer.PoolChunk.IS_USED_SHIFT;
import static io.netty.buffer.PoolChunk.IS_SUBPAGE_SHIFT;

final class PoolSubpage<T> implements PoolSubpageMetric, PoolChunkSubPageWrapper<T> {

    final PoolChunk<T> chunk;
    final int elemSize;
    private final int pageShifts;
    private final int runOffset;
    private final int runSize;
    private final long[] bitmap;
    private final int bitmapLength;
    private final int maxNumElems;

    PoolSubpage<T> prev;
    PoolSubpage<T> next;

    boolean doNotDestroy;
    private int nextAvail;
    private int numAvail;

    private final ReentrantLock lock;

    final int headIndex;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /** Special constructor that creates a linked list head */
    PoolSubpage(int headIndex) {
        chunk = null;
        lock = new ReentrantLock();
        pageShifts = -1;
        runOffset = -1;
        elemSize = -1;
        runSize = -1;
        bitmap = null;
        bitmapLength = -1;
        maxNumElems = 0;
        this.headIndex = headIndex;
    }

    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int pageShifts, int runOffset, int runSize, int elemSize) {
        this.chunk = chunk;
        this.pageShifts = pageShifts;
        this.runOffset = runOffset;
        this.runSize = runSize;
        this.elemSize = elemSize;
        this.headIndex = head.headIndex;

        doNotDestroy = true;

        maxNumElems = numAvail = runSize / elemSize;
        int bitmapLength = maxNumElems >>> 6;
        if ((maxNumElems & 63) != 0) {
            bitmapLength ++;
        }
        this.bitmapLength = bitmapLength;
        bitmap = new long[bitmapLength];
        nextAvail = 0;

        lock = null;
        addToPool(head);
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     */
    long allocate() {
        // If this subpage is full, or it has been marked as invalid('doNotDestroy' is false).
        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }
        final int bitmapIdx = getNextAvail();
        // Assert this subpage is the first one of the link(head.next).
        assert this.prev.chunk == null;
        if (bitmapIdx < 0) {
            // Subpage appear to be in an invalid state.
            // Mark the 'doNotDestroy' as false to prohibit subsequent allocation from this subpage.
            doNotDestroy = false;
            // Move this subpage to tail.
            // We do not remove this subpage from pool, because it may still have other elements in use.
            moveToTail(this.prev);
            throw new AssertionError("No next available bitmap index found (bitmapIdx = " + bitmapIdx + "), " +
                    "even though there are supposed to be (numAvail = " + numAvail + ") " +
                    "out of (maxNumElems = " + maxNumElems + ") available indexes.");
        }
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) == 0;
        bitmap[q] |= 1L << r;
        // If this subpage becomes full.
        if (--numAvail == 0) {
            moveToTail(this.prev);
        }
        return toHandle(bitmapIdx);
    }

    /**
     * @return {@code true} if this subpage is in use.
     *         {@code false} if this subpage is not used by its chunk, thus it is removed from pool,
     *                       and it will be GCed.
     */
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) != 0;
        bitmap[q] ^= 1L << r;

        setNextAvail(bitmapIdx);

        numAvail ++;
        // If this subpage's state is valid('doNotDestroy' is true).
        if (doNotDestroy) {
            // If this subpage changed from full to non-full('numAvail' is 1),
            // and it is not empty: maxNumElems > numAvail.
            if (1 == numAvail && maxNumElems > numAvail) {
                moveToFirst(head);
                return true;
            }
        }
        // If this subpage becomes empty.
        if (numAvail == maxNumElems) {
            // If this subpage's state is valid('doNotDestroy' is true),
            // and it's the only one left in the pool,
            if (doNotDestroy && prev == next) {
                // Do not remove this subpage from the pool.
                return true;
            }
            // Remove this subpage from the pool.
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
        return true;
    }

    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    private void moveToTail(PoolSubpage<T> head) {
        // If already on tail.
        if (next == head) {
            return;
        }
        // Assert current is on head.next.
        assert head.next == this && next != null;
        prev.next = next;
        next.prev = prev;
        // 'head.pre' is the tail.
        prev = head.prev;
        next = head;
        head.prev.next = this;
        head.prev = this;
    }

    private void moveToFirst(PoolSubpage<T> head) {
        // If already on first.
        if (prev == head) {
            return;
        }
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        prev = head;
        next = head.next;
        head.next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        for (int i = 0; i < bitmapLength; i ++) {
            long bits = bitmap[i];
            if (~bits != 0) {
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        final int baseVal = i << 6;
        for (int j = 0; j < 64; j ++) {
            if ((bits & 1) == 0) {
                int val = baseVal | j;
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;
        }
        return -1;
    }

    private long toHandle(int bitmapIdx) {
        int pages = runSize >> pageShifts;
        return (long) runOffset << RUN_OFFSET_SHIFT
               | (long) pages << SIZE_SHIFT
               | 1L << IS_USED_SHIFT
               | 1L << IS_SUBPAGE_SHIFT
               | bitmapIdx;
    }

    @Override
    public String toString() {
        final int numAvail;
        if (chunk == null) {
            // This is the head so there is no need to synchronize at all as these never change.
            numAvail = 0;
        } else {
            final boolean doNotDestroy;
            PoolSubpage<T> head = chunk.arena.smallSubpagePools[headIndex];
            head.lock();
            try {
                doNotDestroy = this.doNotDestroy;
                numAvail = this.numAvail;
            } finally {
                head.unlock();
            }
            if (!doNotDestroy) {
                // Not used for creating the String.
                return "(" + runOffset + ": not in use)";
            }
        }

        return "(" + this.runOffset + ": " + (this.maxNumElems - numAvail) + '/' + this.maxNumElems +
                ", offset: " + this.runOffset + ", length: " + this.runSize + ", elemSize: " + this.elemSize + ')';
    }

    @Override
    public int maxNumElements() {
        return maxNumElems;
    }

    @Override
    public int numAvailable() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }
        PoolSubpage<T> head = chunk.arena.smallSubpagePools[headIndex];
        head.lock();
        try {
            return numAvail;
        } finally {
            head.unlock();
        }
    }

    @Override
    public int elementSize() {
        return elemSize;
    }

    @Override
    public int pageSize() {
        return 1 << pageShifts;
    }

    boolean isDoNotDestroy() {
        if (chunk == null) {
            // It's the head.
            return true;
        }
        PoolSubpage<T> head = chunk.arena.smallSubpagePools[headIndex];
        head.lock();
        try {
            return doNotDestroy;
        } finally {
            head.unlock();
        }
    }

    boolean isPoolAllocatable() {
        final PoolSubpage<T> head;
        if (chunk == null) {
            // It's the head.
            head = this;
        } else {
            head = chunk.arena.smallSubpagePools[headIndex];
        }
        head.lock();
        try {
            return head.next != null && head.next.numAvail > 0 && head.next.doNotDestroy;
        } finally {
            head.unlock();
        }
    }

    void destroy() {
        if (chunk != null) {
            chunk.destroy();
        }
    }

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }

    @Override
    public PoolSubpage<T> getPoolSubpage() {
        return this;
    }

    @Override
    public PoolChunk<T> getPoolChunk() {
        return this.chunk;
    }
}
