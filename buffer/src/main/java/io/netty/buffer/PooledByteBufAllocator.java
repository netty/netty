/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import io.netty.buffer.ByteBuf.Unsafe;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class PooledByteBufAllocator extends AbstractByteBufAllocator {

    private static final int DEFAULT_NUM_HEAP_ARENA = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_NUM_DIRECT_ARENA = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_PAGE_SIZE = 4096;
    private static final int DEFAULT_MAX_ORDER = 11;

    private static final int MIN_PAGE_SIZE = 4096;
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    private final Arena[] heapArenas;
    private final ThreadLocal<Arena> threadLocalHeapArena = new ThreadLocal<Arena>() {
        private final AtomicInteger index = new AtomicInteger();
        @Override
        @SuppressWarnings("ClassEscapesDefinedScope")
        protected Arena initialValue() {
            return heapArenas[Math.abs(index.getAndIncrement() % heapArenas.length)];
        }
    };

    protected PooledByteBufAllocator() {
        this(DEFAULT_NUM_HEAP_ARENA, DEFAULT_NUM_DIRECT_ARENA, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER);
    }

    protected PooledByteBufAllocator(int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        this(false, nHeapArena, nDirectArena, pageSize, maxOrder);
    }

    protected PooledByteBufAllocator(
            boolean directByDefault, int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        super(validateAndCalculateChunkSize(pageSize, maxOrder), directByDefault);
        if (nHeapArena <= 0) {
            throw new IllegalArgumentException("nHeapArena: " + nHeapArena + " (expected: 1+)");
        }
        if (nDirectArena <= 0) {
            throw new IllegalArgumentException("nDirectArea: " + nDirectArena + " (expected: 1+)");
        }

        int pageShifts = validateAndCalculatePageShifts(pageSize);
        int chunkSize = bufferMaxCapacity();

        heapArenas = new Arena[nHeapArena];
        for (int i = 0; i < heapArenas.length; i ++) {
            heapArenas[i] = new HeapArena(pageSize, maxOrder, pageShifts, chunkSize);
        }
    }

    private static int validateAndCalculatePageShifts(int pageSize) {
        if (pageSize < MIN_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: 4096+)");
        }

        // Ensure pageSize is power of 2.
        boolean found1 = false;
        int pageShifts = 0;
        for (int i = pageSize; i != 0 ; i >>= 1) {
            if ((i & 1) != 0) {
                if (!found1) {
                    found1 = true;
                } else {
                    throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: power of 2");
                }
            } else {
                if (!found1) {
                    pageShifts ++;
                }
            }
        }
        return pageShifts;
    }

    private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        int chunkSize = pageSize;
        for (int i = maxOrder; i > 0; i --) {
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format(
                        "pageSize (%d) << maxOrder (%d) must not exceed %d", pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
        }
        return chunkSize;
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        Arena arena = threadLocalHeapArena.get();
        // TODO: Create a composite buffer if initialCapacity is larger thank chunkSize.
        return arena.allocate(initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        return directBuffer();
    }

    @Override
    public void shutdown() {
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(heapArenas.length);
        buf.append(" arena(s):");
        buf.append(StringUtil.NEWLINE);
        for (Arena a: heapArenas) {
            buf.append(a);
        }
        return buf.toString();
    }

    private static abstract class Arena {

        private final ReentrantLock lock = new ReentrantLock();

        protected final int pageSize;
        protected final int maxOrder;
        protected final int pageShifts;
        protected final int chunkSize;

        private final Deque<Subpage>[] tinySubpagePools;
        private final Deque<Subpage>[] smallSubpagePools;
        // TODO: Split this into Q0 Q25 Q50 Q75
        private final List<Chunk> chunks = new ArrayList<Chunk>();

        Arena(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            this.pageSize = pageSize;
            this.maxOrder = maxOrder;
            this.pageShifts = pageShifts;
            this.chunkSize = chunkSize;

            //noinspection unchecked
            tinySubpagePools = new Deque[512 >>> 4];
            //noinspection unchecked
            smallSubpagePools = new Deque[pageShifts - 9];
            for (int i = 1; i < tinySubpagePools.length; i ++) {
                tinySubpagePools[i] = new ArrayDeque<Subpage>();
            }
            for (int i = 0; i < smallSubpagePools.length; i ++) {
                smallSubpagePools[i] = new ArrayDeque<Subpage>();
            }
        }

        PooledByteBuf allocate(int minCapacity, int maxCapacity) {
            lock.lock();
            try {
                final int capacity = normalizeCapacity(minCapacity);

                if (capacity < pageSize) {
                    int tableIdx;
                    Deque<Subpage>[] table;
                    if (capacity < 512) {
                        tableIdx = capacity >>> 4;
                        table = tinySubpagePools;
                    } else {
                        tableIdx = 0;
                        int i = capacity >>> 10;
                        while (i != 0) {
                            i >>>= 1;
                            tableIdx ++;
                        }
                        table = smallSubpagePools;
                    }

                    long handle;
                    Deque<Subpage> subpages = table[tableIdx];
                    while (!subpages.isEmpty()) {
                        Subpage s = subpages.getFirst();
                        handle = s.allocate();
                        if (handle < 0) {
                            subpages.removeFirst();
                        } else {
                            PooledByteBuf allocated = s.chunk.newByteBuf(handle, maxCapacity);
                            if (s.numAvail == 0) {
                                subpages.removeFirst();
                            }
                            return allocated;
                        }
                    }
                }

                final int nChunks = chunks.size();
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < nChunks; i ++) {
                    Chunk c = chunks.get(i);
                    long handle = c.allocate(capacity);
                    if (handle < 0) {
                        continue;
                    }

                    return c.newByteBuf(handle, maxCapacity);
                }

                // Add a new chunk.
                Chunk c = newChunk(pageSize, maxOrder, pageShifts, chunkSize);
                chunks.add(c);
                long handle = c.allocate(capacity);
                assert handle >= 0;
                return c.newByteBuf(handle, maxCapacity);
            } finally {
                lock.unlock();
            }
        }

        void free(PooledByteBuf buf) {
            lock.lock();
            try {
                Chunk c = buf.chunk;
                if (!c.free(buf.handle)) {
                    // Chunk got empty.
                    // TODO: Free the chunk or leave it for later use.
                    //       Probably better free it only when it is not touched for a while.
                }
            } finally {
                lock.unlock();
            }
        }

        void addSubpage(Subpage subpage) {
            int tableIdx;
            int elemSize = subpage.elemSize;
            Deque<Subpage>[] table;
            if (elemSize < 512) {
                tableIdx = elemSize >>> 4;
                table = tinySubpagePools;
            } else {
                tableIdx = 0;
                elemSize >>>= 10;
                while (elemSize != 0) {
                    elemSize >>>= 1;
                    tableIdx ++;
                }
                table = smallSubpagePools;
            }
            table[tableIdx].addFirst(subpage);
        }

        private int normalizeCapacity(int capacity) {
            if (capacity <= 0 || capacity > chunkSize) {
                throw new IllegalArgumentException("capacity: " + capacity + " (expected: 1-" + chunkSize + ')');
            }

            if (capacity >= 512) {
                // Doubled
                int normalizedCapacity = 512;
                while (normalizedCapacity < capacity) {
                    normalizedCapacity <<= 1;
                }
                return normalizedCapacity;
            }

            // Quantum-spaced
            if ((capacity & 15) == 0) {
                return capacity;
            }

            return (capacity & ~15) + 16;
        }

        protected abstract Chunk newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize);

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append(chunks.size());
            buf.append(" chunk(s)");
            buf.append(StringUtil.NEWLINE);
            if (!chunks.isEmpty()) {
                for (Chunk c: chunks) {
                    buf.append(c);
                }
                buf.append(StringUtil.NEWLINE);
            }
            buf.append("tiny subpages:");
            buf.append(StringUtil.NEWLINE);
            for (int i = 1; i < tinySubpagePools.length; i ++) {
                Deque<Subpage> subpages = tinySubpagePools[i];
                if (subpages.isEmpty()) {
                    continue;
                }

                buf.append(i);
                buf.append(": ");
                buf.append(subpages);
                buf.append(StringUtil.NEWLINE);
            }
            buf.append("small subpages:");
            buf.append(StringUtil.NEWLINE);
            for (int i = 1; i < smallSubpagePools.length; i ++) {
                Deque<Subpage> subpages = smallSubpagePools[i];
                if (subpages.isEmpty()) {
                    continue;
                }

                buf.append(i);
                buf.append(": ");
                buf.append(subpages);
                buf.append(StringUtil.NEWLINE);
            }

            return buf.toString();
        }
    }

    private static final class HeapArena extends Arena {

        HeapArena(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            super(pageSize, maxOrder, pageShifts, chunkSize);
        }

        @Override
        protected Chunk newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            return new HeapChunk(this, pageSize, maxOrder, pageShifts, chunkSize);
        }
    }

    private static abstract class Chunk {
        private static final int ST_UNUSED = 0;
        private static final int ST_BRANCH = 1;
        private static final int ST_ALLOCATED = 2;
        private static final int ST_ALLOCATED_SUBPAGE = 3;

        private final Arena arena;
        private final int[] memoryMap;
        private final Subpage[] subpages;
        private final int pageSize;
        private final int pageShifts;
        private final int maxOrder;
        private final int chunkSize;
        private final int chunkSizeInPages;
        private final int maxSubpageAllocs;

        int freeBytes;

        Chunk(Arena arena, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            this.arena = arena;
            this.pageSize = pageSize;
            this.pageShifts = pageShifts;
            this.maxOrder = maxOrder;
            this.chunkSize = chunkSize;
            freeBytes = chunkSize;

            chunkSizeInPages = chunkSize >>> pageShifts;
            maxSubpageAllocs = 1 << maxOrder;

            // Generate the memory map.
            memoryMap = new int[(maxSubpageAllocs << 1) - 1];
            int memoryMapIndex = 0;
            for (int i = 0; i <= maxOrder; i ++) {
                int runSizeInPages = chunkSizeInPages >>> i;
                for (int j = 0; j < chunkSizeInPages; j += runSizeInPages) {
                    //noinspection PointlessBitwiseExpression
                    memoryMap[memoryMapIndex ++] = j << 17 | runSizeInPages << 2 | ST_UNUSED;
                }
            }

            subpages = new Subpage[maxSubpageAllocs];
        }

        long allocate(int capacity) {
            return allocate(capacity, 0);
        }

        private long allocate(int capacity, int curIdx) {
            int val = memoryMap[curIdx];
            switch (val & 3) {
            case ST_UNUSED: {
                int runLength = runLength(val);
                if (capacity > runLength >>> 1) {
                    // minCapacity is greater than the half of the current run.
                    memoryMap[curIdx] = val & ~3 | ST_ALLOCATED;
                    freeBytes -= runLength;
                    return curIdx;
                }

                if (runLength == pageSize) {
                    memoryMap[curIdx] = val & ~3 | ST_ALLOCATED_SUBPAGE;
                    freeBytes -= runLength;

                    int subpageIdx = subpageIdx(curIdx);
                    Subpage subpage = subpages[subpageIdx];
                    if (subpage == null) {
                        subpage = new Subpage(this, curIdx, runOffset(curIdx), runLength, pageSize, capacity);
                        subpages[subpageIdx] = subpage;
                    } else {
                        subpage.init(capacity);
                    }
                    arena.addSubpage(subpage);
                    return subpage.allocate();
                }

                // minCapacity is equal to or less than the half of the current run.
                int leftLeafIdx = (curIdx << 1) + 1;
                int rightLeafIdx = leftLeafIdx + 1;

                // Split into a small or large run depending on minCapacity.
                memoryMap[curIdx] = val & ~3 | ST_BRANCH;
                //noinspection PointlessBitwiseExpression
                memoryMap[leftLeafIdx] = memoryMap[leftLeafIdx] & ~3 | ST_UNUSED;
                //noinspection PointlessBitwiseExpression
                memoryMap[rightLeafIdx] = memoryMap[rightLeafIdx] & ~3 | ST_UNUSED;

                long res = allocate(capacity, leftLeafIdx);
                if (res > 0) {
                    return res;
                }

                res = allocate(capacity, rightLeafIdx);
                if (res > 0) {
                    return res;
                }

                return -1;
            }
            case ST_BRANCH: {
                long res;
                int nextIdx = (curIdx << 1) + 1;

                res = allocate(capacity, nextIdx);
                if (res > 0) {
                    return res;
                }

                nextIdx ++;
                res = allocate(capacity, nextIdx);
                if (res > 0) {
                    return res;
                }

                return -1;
            }
            case ST_ALLOCATED:
                return -1;
            case ST_ALLOCATED_SUBPAGE: {
                Subpage subpage = subpages[subpageIdx(curIdx)];
                int elemSize = subpage.elemSize;
                if (capacity != elemSize) {
                    return -1;
                }

                long handle = subpage.allocate();
                if (handle < 0) {
                    return -1;
                }

                return handle;
            }
            default:
                throw new Error();
            }
        }

        /**
         * @return {@code true} if this chunk is in use.
         *         {@code false} if this chunk is not used by its arena and thus it's OK to be deallocated.
         */
        boolean free(long handle) {
            int memoryMapIdx = (int) handle;
            int bitmapIdx = (int) (handle >>> 32);

            int val = memoryMap[memoryMapIdx];
            switch (val & 3) {
            case ST_ALLOCATED:
                assert bitmapIdx == 0;
                break;
            case ST_ALLOCATED_SUBPAGE:
                Subpage subpage = subpages[subpageIdx(memoryMapIdx)];
                assert subpage != null && subpage.inUse;
                if (subpage.free(bitmapIdx & 0x3FFFFFFF)) {
                    if (subpage.numAvail == 1) {
                        arena.addSubpage(subpage);
                    }
                    return true;
                }
                break;
            default:
                assert false;
            }

            //noinspection PointlessBitwiseExpression
            memoryMap[memoryMapIdx] = val & ~3 | ST_UNUSED;
            freeBytes += runLength(val);

            if ((memoryMap[siblingIdx(memoryMapIdx)] & 3) == ST_UNUSED) {
                // Parent's state: ST_BRANCH -> ST_UNUSED
                int parentIdx = parentIdx(memoryMapIdx);
                //noinspection PointlessBitwiseExpression
                memoryMap[parentIdx] = memoryMap[parentIdx] & ~3 | ST_UNUSED;
                // Climb up the tree.
                if (parentIdx != 0) {
                    for (;;) {
                        if ((memoryMap[siblingIdx(parentIdx)] & 3) != ST_UNUSED) {
                            break;
                        }

                        int grandParentIdx = parentIdx(parentIdx);
                        //noinspection PointlessBitwiseExpression
                        memoryMap[grandParentIdx] = memoryMap[grandParentIdx] & ~3 | ST_UNUSED;
                        parentIdx = grandParentIdx;
                        if (parentIdx == 0) {
                            break;
                        }
                    }
                } else {
                    return false;
                }
            }

            return true;
        }

        PooledByteBuf newByteBuf(long handle, int maxCapacity) {
            int memoryMapIdx = (int) handle;
            int bitmapIdx = (int) (handle >>> 32);

            int val = memoryMap[memoryMapIdx];
            switch (val & 3) {
            case ST_ALLOCATED:
                assert bitmapIdx == 0;
                return newByteBuf(handle, runOffset(val), runLength(val), maxCapacity);
            case ST_ALLOCATED_SUBPAGE:
                Subpage subpage = subpages[subpageIdx(memoryMapIdx)];
                assert subpage != null && subpage.inUse;
                return newByteBuf(handle, runOffset(val) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize, subpage.elemSize, maxCapacity);
            }

            throw new Error(String.valueOf(val & 3));
        }

        protected abstract PooledByteBuf newByteBuf(long handle, int memoryOffset, int memoryLength, int maxCapacity);

        private static int parentIdx(int memoryMapIdx) {
            assert memoryMapIdx > 0;
            return memoryMapIdx - 1 >>> 1;
        }

        private static int siblingIdx(int memoryMapIdx) {
            assert memoryMapIdx > 0;

            if ((memoryMapIdx & 1) == 0) {
                return memoryMapIdx - 1;
            }

            return memoryMapIdx + 1;
        }

        private int runLength(int val) {
            return (val >>> 2 & 0x7FFF) << pageShifts;
        }

        private int runOffset(int val) {
            return val >>> 17 << pageShifts;
        }

        private int subpageIdx(int memoryMapIdx) {
            return memoryMapIdx - maxSubpageAllocs + 1;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("Chunk(");
            buf.append(chunkSize - freeBytes);
            buf.append('/');
            buf.append(chunkSize);
            buf.append(", pageSize: ");
            buf.append(pageSize);
            buf.append(", maxOrder: ");
            buf.append(maxOrder);
            buf.append(", memoryMap: ");
            buf.append(StringUtil.NEWLINE);

            int memoryMapIndex = 0;
            for (int i = 0; i <= maxOrder; i ++) {
                buf.append("Order ");
                buf.append(i);
                buf.append(':');

                int runSize = chunkSize >>> i;
                for (int j = 0; j < chunkSize; j += runSize) {
                    int val = memoryMap[memoryMapIndex ++];
                    buf.append(" (");
                    switch (val & 3) {
                    case ST_UNUSED:
                        buf.append("U: ");
                        break;
                    case ST_BRANCH:
                        buf.append("B: ");
                        break;
                    case ST_ALLOCATED:
                        buf.append("A: ");
                        break;
                    case ST_ALLOCATED_SUBPAGE:
                        buf.append("S: ");
                        break;
                    }
                    buf.append(val >>> 17);
                    buf.append(", ");
                    buf.append(val >>> 2 & 0x7FFF);
                    buf.append(')');
                }

                buf.append(StringUtil.NEWLINE);
            }
            buf.append("subpages:");
            buf.append(StringUtil.NEWLINE);
            for (Subpage subpage: subpages) {
                if (subpage == null || !subpage.inUse) {
                    continue;
                }
                buf.append(subpage);
                buf.append(' ');
            }

            return buf.toString();
        }
    }

    private static final class Subpage {

        final Chunk chunk;
        final int memoryMapIdx;
        final int runOffset;
        final int runLength;
        final long[] bitmap;

        boolean inUse;
        int elemSize;
        int maxNumElems;
        int nextAvail;
        int bitmapLength;
        int numAvail;

        Subpage(Chunk chunk, int memoryMapIdx, int runOffset, int runLength, int pageSize, int elemSize) {
            this.chunk = chunk;
            this.memoryMapIdx = memoryMapIdx;
            this.runOffset = runOffset;
            this.runLength = runLength;
            bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64
            init(elemSize);
        }

        void init(int elemSize) {
            inUse = true;
            this.elemSize = elemSize;
            maxNumElems = numAvail = runLength / elemSize;

            bitmapLength = maxNumElems >>> 6;
            if ((maxNumElems & 63) != 0) {
                bitmapLength ++;
            }

            for (int i = 0; i < bitmapLength; i ++) {
                bitmap[i] = 0;
            }
        }

        /**
         * Returns the bitmap index of the subpage allocation.
         */
        long allocate() {
            if (numAvail == 0 || !inUse) {
                return -1;
            }

            final int bitmapIdx = nextAvail;
            bitmap[bitmapIdx >>> 6] ^= 1L << (bitmapIdx & 63);
            numAvail --;

            if (numAvail == 0) {
                nextAvail = -1;
                return toHandle(bitmapIdx);
            }

            int i = bitmapIdx + 1;
            for (; i < maxNumElems; i ++) {
                if ((bitmap[i >>> 6] & 1L << (i & 63)) == 0) {
                    nextAvail = i;
                    return toHandle(bitmapIdx);
                }
            }

            for (i = 0; i < bitmapIdx; i ++) {
                if ((bitmap[i >>> 6] & 1L << (i & 63)) == 0) {
                    nextAvail = i;
                    return toHandle(bitmapIdx);
                }
            }

            return toHandle(bitmapIdx);
        }

        private long toHandle(int bitmapIdx) {
            return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
        }

        /**
         * @return {@code true} if this subpage is in use.
         *         {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
         */
        boolean free(int bitmapIdx) {
            int q = bitmapIdx >>> 6;
            int r = bitmapIdx & 63;
            assert (bitmap[q] & 1L << r) != 0;
            bitmap[q] ^= 1L << r;

            if (numAvail == 0) {
                nextAvail = bitmapIdx;
            }

            numAvail ++;

            if (numAvail < maxNumElems) {
                return true;
            } else {
                inUse = false;
                return false;
            }
        }

        public String toString() {
            if (!inUse) {
                return "(" + memoryMapIdx + ": not in use)";
            }

            return String.valueOf('(') + memoryMapIdx + ": " + (maxNumElems - numAvail) + '/' + maxNumElems + ", offset: " + runOffset + ", length: " + runLength + ", elemSize: " + elemSize + ')';
        }
    }

    private static final class HeapChunk extends Chunk {

        private final byte[] memory;

        HeapChunk(Arena arena, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            super(arena, pageSize, maxOrder, pageShifts, chunkSize);
            memory = new byte[chunkSize];
        }

        @Override
        protected PooledByteBuf newByteBuf(long handle, int memoryOffset, int memoryLength, int maxCapacity) {
            return new PooledByteBuf(this, handle, memory, memoryOffset, maxCapacity);
        }
    }

    // Dummy buffer for proof of concept.
    private static final class PooledByteBuf extends AbstractByteBuf implements Unsafe {

        final Chunk chunk;
        final long handle;
        final byte[] memory;
        final int memoryOffset;
        final int memoryLength;

        PooledByteBuf(Chunk chunk, long handle, byte[] memory, int memoryOffset, int memoryLength) {
            super(Integer.MAX_VALUE);
            this.chunk = chunk;
            this.handle = handle;
            this.memory = memory;
            this.memoryOffset = memoryOffset;
            this.memoryLength = memoryLength;
        }

        @Override
        public int capacity() {
            return 0;
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            return null;
        }

        @Override
        public ByteBufAllocator alloc() {
            return null;
        }

        @Override
        public ByteOrder order() {
            return null;
        }

        @Override
        public ByteBuf unwrap() {
            return null;
        }

        @Override
        public boolean isDirect() {
            return false;
        }

        @Override
        public byte getByte(int index) {
            return 0;
        }

        @Override
        public short getShort(int index) {
            return 0;
        }

        @Override
        public int getUnsignedMedium(int index) {
            return 0;
        }

        @Override
        public int getInt(int index) {
            return 0;
        }

        @Override
        public long getLong(int index) {
            return 0;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            return null;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            return null;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            return null;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
            return null;
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
            return 0;
        }

        @Override
        public ByteBuf setByte(int index, int value) {
            return null;
        }

        @Override
        public ByteBuf setShort(int index, int value) {
            return null;
        }

        @Override
        public ByteBuf setMedium(int index, int value) {
            return null;
        }

        @Override
        public ByteBuf setInt(int index, int value) {
            return null;
        }

        @Override
        public ByteBuf setLong(int index, long value) {
            return null;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            return null;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            return null;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            return null;
        }

        @Override
        public int setBytes(int index, InputStream in, int length) throws IOException {
            return 0;
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
            return 0;
        }

        @Override
        public ByteBuf copy(int index, int length) {
            return null;
        }

        @Override
        public boolean hasNioBuffer() {
            return false;
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            return null;
        }

        @Override
        public boolean hasNioBuffers() {
            return false;
        }

        @Override
        public ByteBuffer[] nioBuffers(int offset, int length) {
            return new ByteBuffer[0];
        }

        @Override
        public boolean hasArray() {
            return false;
        }

        @Override
        public byte[] array() {
            return new byte[0];
        }

        @Override
        public int arrayOffset() {
            return 0;
        }

        @Override
        public Unsafe unsafe() {
            return this;
        }

        @Override
        public ByteBuffer internalNioBuffer() {
            return null;
        }

        @Override
        public ByteBuffer[] internalNioBuffers() {
            return new ByteBuffer[0];
        }

        @Override
        public void discardSomeReadBytes() {
        }

        @Override
        public void suspendIntermediaryDeallocations() {
        }

        @Override
        public void resumeIntermediaryDeallocations() {
        }

        @Override
        public boolean isFreed() {
            return false;
        }

        @Override
        public void free() {
            chunk.free(handle);
        }
    }

    public static void main(String[] args) throws Exception {
        ByteBufAllocator alloc = new PooledByteBufAllocator(1, 1, 4096, 9);
        ByteBufAllocator unpooled = UnpooledByteBufAllocator.HEAP_BY_DEFAULT;

        for (;;) {
            System.err.print("POOLED: ");
            test(alloc);
            System.gc();
            Thread.sleep(1000);
            System.err.print("UNPOOLED: ");
            test(unpooled);
            System.gc();
            Thread.sleep(1000);
        }

//        System.err.println(alloc);
//
//        ByteBuf a, b, c, d;
//
//        System.err.println("ALLOC A(4096)");
//        a = alloc.heapBuffer(4096);
//        System.err.println(alloc);
//
//        System.err.println("ALLOC B(4096)");
//        b = alloc.heapBuffer(4096);
//        System.err.println(alloc);
//
//        System.err.println("FREE  A(4096)");
//        a.unsafe().free();
//        System.err.println(alloc);
//
//        System.err.println("FREE  B(4096)");
//        b.unsafe().free();
//        System.err.println(alloc);
//
//        System.err.println("ALLOC A(256)");
//        a = alloc.heapBuffer(256);
//        System.err.println(alloc);
//
//        System.err.println("ALLOC B(256)");
//        b = alloc.heapBuffer(256);
//        System.err.println(alloc);
//
//        System.err.println("ALLOC C(2048)");
//        c = alloc.heapBuffer(2048);
//        System.err.println(alloc);
//
//        System.err.println("ALLOC D(2048)");
//        d = alloc.heapBuffer(2048);
//        System.err.println(alloc);
//
//        System.err.println("FREE  A(256)");
//        a.unsafe().free();
//        System.err.println(alloc);
//
//        System.err.println("FREE  B(256)");
//        b.unsafe().free();
//        System.err.println(alloc);
//
//        System.err.println("FREE  C(2048)");
//        c.unsafe().free();
//        System.err.println(alloc);
//
//        System.err.println("FREE  D(2048)");
//        d.unsafe().free();
//        System.err.println(alloc);

    }

    private static void test(ByteBufAllocator alloc) {
        final int size = 1024;
        Deque<ByteBuf> queue = new ArrayDeque<ByteBuf>();
        for (int i = 0; i < 512; i ++) {
            queue.add(alloc.heapBuffer(size));
        }

        long startTime = System.nanoTime();
        test0(alloc, queue, size);
        long endTime = System.nanoTime();
        System.err.println(TimeUnit.NANOSECONDS.toMillis(endTime - startTime) + " ms");

        for (ByteBuf b: queue) {
            b.unsafe().free();
        }
        queue.clear();
    }

    private static void test0(ByteBufAllocator alloc, Deque<ByteBuf> queue, int size) {
        for (int i = 0; i < 10000000; i ++) {
            queue.add(alloc.heapBuffer(size));
            queue.removeFirst().unsafe().free();
        }
    }
}
