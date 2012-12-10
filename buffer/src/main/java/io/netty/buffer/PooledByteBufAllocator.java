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

import io.netty.util.internal.StringUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PooledByteBufAllocator extends AbstractByteBufAllocator {

    private static final int DEFAULT_NUM_HEAP_ARENA = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_NUM_DIRECT_ARENA = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_PAGE_SIZE = 8192;
    private static final int DEFAULT_MAX_ORDER = 11; // 8192 << 11 = 16 MiB per chunk

    private static final int MIN_PAGE_SIZE = 4096;
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    public static final PooledByteBufAllocator DEFAULT = new PooledByteBufAllocator();

    private final Arena<byte[]>[] heapArenas;
    private final Arena<ByteBuffer>[] directArenas;

    private final ThreadLocal<ThreadCache> threadCache = new ThreadLocal<ThreadCache>() {
        private final AtomicInteger index = new AtomicInteger();
        @Override
        protected ThreadCache initialValue() {
            int idx = Math.abs(index.getAndIncrement() % heapArenas.length);
            return new ThreadCache(heapArenas[idx], directArenas[idx]);
        }
    };

    public PooledByteBufAllocator() {
        this(false);
    }

    public PooledByteBufAllocator(boolean directByDefault) {
        this(directByDefault, DEFAULT_NUM_HEAP_ARENA, DEFAULT_NUM_DIRECT_ARENA, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER);
    }

    public PooledByteBufAllocator(int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        this(false, nHeapArena, nDirectArena, pageSize, maxOrder);
    }

    public PooledByteBufAllocator(
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

        //noinspection unchecked
        heapArenas = new Arena[nHeapArena];
        for (int i = 0; i < heapArenas.length; i ++) {
            heapArenas[i] = new HeapArena(this, pageSize, maxOrder, pageShifts, chunkSize);
        }

        //noinspection unchecked
        directArenas = new Arena[nDirectArena];
        for (int i = 0; i < directArenas.length; i ++) {
            directArenas[i] = new DirectArena(this, pageSize, maxOrder, pageShifts, chunkSize);
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
        return threadCache.get().heapArena.allocate(initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        return threadCache.get().directArena.allocate(initialCapacity, maxCapacity);
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
        for (Arena<byte[]> a: heapArenas) {
            buf.append(a);
        }
        return buf.toString();
    }

    static abstract class Arena<T> {

        final PooledByteBufAllocator parent;

        private final int pageSize;
        private final int maxOrder;
        private final int pageShifts;
        private final int chunkSize;
        private final int subpageOverflowMask;

        private final Deque<Subpage<T>>[] tinySubpagePools;
        private final Deque<Subpage<T>>[] smallSubpagePools;

        // TODO: Destroy the old chunks in chunks0 periodically.

        private final ChunkList<T> chunks50to100;
        private final ChunkList<T> chunks25to75;
        private final ChunkList<T> chunks1to50;
        private final ChunkList<T> chunks0;
        private final ChunkList<T> chunks75to100;
        private final ChunkList<T> chunks100;

        // TODO: Test if addingd padding helps under contention (ThreadCache, Arena, ChunkList, Chunk, and Subpage)
        //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

        protected Arena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            this.parent = parent;
            this.pageSize = pageSize;
            this.maxOrder = maxOrder;
            this.pageShifts = pageShifts;
            this.chunkSize = chunkSize;
            subpageOverflowMask = ~(pageSize - 1);

            //noinspection unchecked
            tinySubpagePools = new Deque[512 >>> 4];
            //noinspection unchecked
            smallSubpagePools = new Deque[pageShifts - 9];
            for (int i = 1; i < tinySubpagePools.length; i ++) {
                tinySubpagePools[i] = new ArrayDeque<Subpage<T>>();
            }
            for (int i = 0; i < smallSubpagePools.length; i ++) {
                smallSubpagePools[i] = new ArrayDeque<Subpage<T>>();
            }

            chunks100 = new ChunkList<T>(null, 100, Integer.MAX_VALUE);
            chunks75to100 = new ChunkList<T>(chunks100, 75, 100);
            chunks50to100 = new ChunkList<T>(chunks75to100, 50, 100);
            chunks25to75 = new ChunkList<T>(chunks50to100, 25, 75);
            chunks1to50 = new ChunkList<T>(chunks25to75, 1, 50);
            chunks0 = new ChunkList<T>(chunks1to50, 0, 1);
            chunks100.prevList = chunks75to100;
            chunks75to100.prevList = chunks50to100;
            chunks50to100.prevList = chunks25to75;
            chunks25to75.prevList = chunks1to50;
            chunks1to50.prevList = chunks0;
        }


        PooledByteBuf<T> allocate(int minCapacity, int maxCapacity) {
            PooledByteBuf<T> buf = newByteBuf(maxCapacity);
            allocate(buf, minCapacity);
            return buf;
        }

        void allocate(PooledByteBuf<T> buf, int minCapacity) {
            final int capacity = normalizeCapacity(minCapacity);
            if ((capacity & subpageOverflowMask) == 0) { // capacity < pageSize
                int tableIdx;
                Deque<Subpage<T>>[] table;
                if ((capacity & 0xFFFFFE00) == 0) { // < 512
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

                synchronized (this) {
                    Deque<Subpage<T>> subpages = table[tableIdx];
                    for (;;) {
                        Subpage<T> s = subpages.peekFirst();
                        if (s == null) {
                            break;
                        }

                        if (!s.doNotDestroy || s.elemSize != capacity) {
                            // The subpage has been destroyed or being used for different element size.
                            subpages.removeFirst();
                            continue;
                        }

                        long handle = s.allocate();
                        if (handle < 0) {
                            subpages.removeFirst();
                        } else {
                            s.chunk.initBufWithSubpage(buf, handle);
                            return;
                        }
                    }
                }
            }

            synchronized (this) {
                if (chunks50to100.allocate(buf, capacity) ||
                    chunks25to75.allocate(buf, capacity) ||
                    chunks1to50.allocate(buf, capacity) ||
                    chunks0.allocate(buf, capacity) ||
                    chunks75to100.allocate(buf, capacity)) {
                    return;
                }

                // Add a new chunk.
                Chunk<T> c = newChunk(pageSize, maxOrder, pageShifts, chunkSize);
                long handle = c.allocate(capacity);
                assert handle > 0;
                c.initBuf(buf, handle);
                chunks1to50.add(c);
            }
        }

        void free(PooledByteBuf<T> buf) {
            free(buf.chunk, buf.handle);
        }

        synchronized void free(Chunk<T> chunk, long handle) {
            chunk.parent.free(chunk, handle);
        }

        void addSubpage(Subpage<T> subpage) {
            int tableIdx;
            int elemSize = subpage.elemSize;
            Deque<Subpage<T>>[] table;
            if ((elemSize & 0xFFFFFE00) == 0) { // < 512
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

            if ((capacity & 0xFFFFFE00) != 0) { // >= 512
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

        synchronized void reallocate(PooledByteBuf<T> buf, int newCapacity, boolean freeOldMemory) {
            if (newCapacity < 0 || newCapacity > buf.maxCapacity()) {
                throw new IllegalArgumentException("newCapacity: " + newCapacity);
            }

            int oldCapacity = buf.length;
            if (oldCapacity == newCapacity) {
                return;
            }

            Chunk<T> oldChunk = buf.chunk;
            long oldHandle = buf.handle;
            T oldMemory = buf.memory;
            int oldOffset = buf.offset;

            int readerIndex = buf.readerIndex();
            int writerIndex = buf.writerIndex();

            allocate(buf, newCapacity);
            if (newCapacity > oldCapacity) {
                memoryCopy(
                        oldMemory, oldOffset + readerIndex,
                        buf.memory, buf.offset + readerIndex, writerIndex - readerIndex);
            } else if (newCapacity < oldCapacity) {
                if (readerIndex < newCapacity) {
                    if (writerIndex > newCapacity) {
                        writerIndex = newCapacity;
                    }
                    memoryCopy(
                            oldMemory, oldOffset + readerIndex,
                            buf.memory, buf.offset + readerIndex, writerIndex - readerIndex);
                } else {
                    readerIndex = writerIndex = newCapacity;
                }
            }

            buf.setIndex(readerIndex, writerIndex);

            if (freeOldMemory) {
                free(oldChunk, oldHandle);
            }
        }

        protected abstract Chunk<T> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize);
        protected abstract PooledByteBuf<T> newByteBuf(int maxCapacity);
        protected abstract void memoryCopy(T src, int srcOffset, T dst, int dstOffset, int length);
        protected abstract void destroyChunk(Chunk<T> chunk);

        public synchronized String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("Chunk(s) at 0%:");
            buf.append(StringUtil.NEWLINE);
            buf.append(chunks0);
            buf.append(StringUtil.NEWLINE);
            buf.append("Chunk(s) at 0~50%:");
            buf.append(StringUtil.NEWLINE);
            buf.append(chunks1to50);
            buf.append(StringUtil.NEWLINE);
            buf.append("Chunk(s) at 25~75%:");
            buf.append(StringUtil.NEWLINE);
            buf.append(chunks25to75);
            buf.append(StringUtil.NEWLINE);
            buf.append("Chunk(s) at 50~100%:");
            buf.append(StringUtil.NEWLINE);
            buf.append(chunks50to100);
            buf.append(StringUtil.NEWLINE);
            buf.append("Chunk(s) at 75~100%:");
            buf.append(StringUtil.NEWLINE);
            buf.append(chunks75to100);
            buf.append(StringUtil.NEWLINE);
            buf.append("Chunk(s) at 100%:");
            buf.append(StringUtil.NEWLINE);
            buf.append(chunks100);
            buf.append(StringUtil.NEWLINE);
            buf.append("tiny subpages:");
            for (int i = 1; i < tinySubpagePools.length; i ++) {
                Deque<Subpage<T>> subpages = tinySubpagePools[i];
                if (subpages.isEmpty()) {
                    continue;
                }

                buf.append(StringUtil.NEWLINE);
                buf.append(i);
                buf.append(": ");
                buf.append(subpages);
            }
            buf.append(StringUtil.NEWLINE);
            buf.append("small subpages:");
            for (int i = 1; i < smallSubpagePools.length; i ++) {
                Deque<Subpage<T>> subpages = smallSubpagePools[i];
                if (subpages.isEmpty()) {
                    continue;
                }

                buf.append(StringUtil.NEWLINE);
                buf.append(i);
                buf.append(": ");
                buf.append(subpages);
            }
            buf.append(StringUtil.NEWLINE);

            return buf.toString();
        }
    }

    private static final class ChunkList<T> {
        private final ChunkList<T> nextList;
        private ChunkList<T> prevList;

        private final int minUsage;
        private final int maxUsage;

        private Chunk<T> head;

        ChunkList(ChunkList<T> nextList, int minUsage, int maxUsage) {
            this.nextList = nextList;
            this.minUsage = minUsage;
            this.maxUsage = maxUsage;
        }

        boolean allocate(PooledByteBuf<T> buf, int capacity) {
            if (head == null) {
                return false;
            }

            for (Chunk<T> cur = head;;) {
                long handle = cur.allocate(capacity);
                if (handle < 0) {
                    cur = cur.next;
                    if (cur == null) {
                        return false;
                    }
                } else {
                    cur.initBuf(buf, handle);
                    if (cur.usage() >= maxUsage) {
                        remove(cur);
                        nextList.add(cur);
                    }
                    return true;
                }
            }
        }

        void free(Chunk<T> chunk, long handle) {
            if (!chunk.free(handle)) {
                // Chunk got empty.
                chunk.timestamp = System.nanoTime();
            }

            int usage = chunk.usage();
            if (usage < minUsage) {
                remove(chunk);
                prevList.add(chunk);
                if (usage == 0) {
                    chunk.timestamp = System.nanoTime();
                }
            }
        }

        void add(Chunk<T> chunk) {
            if (chunk.usage() >= maxUsage) {
                nextList.add(chunk);
                return;
            }

            chunk.parent = this;
            if (head == null) {
                head = chunk;
                chunk.prev = null;
                chunk.next = null;
            } else {
                chunk.prev = null;
                chunk.next = head;
                head.prev = chunk;
                head = chunk;
            }
        }

        private void remove(Chunk<T> cur) {
            if (cur == head) {
                head = cur.next;
                if (head != null) {
                    head.prev = null;
                }
            } else {
                Chunk<T> next = cur.next;
                cur.prev.next = next;
                if (next != null) {
                    next.prev = cur.prev;
                }
            }
        }

        @Override
        public String toString() {
            if (head == null) {
                return "none";
            }

            StringBuilder buf = new StringBuilder();
            for (Chunk<T> cur = head;;) {
                buf.append(cur);
                cur = cur.next;
                if (cur == null) {
                    break;
                }
                buf.append(StringUtil.NEWLINE);
            }

            return buf.toString();
        }
    }

    private static final class HeapArena extends Arena<byte[]> {

        HeapArena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize);
        }

        @Override
        protected Chunk<byte[]> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            return new Chunk<byte[]>(this, new byte[chunkSize], pageSize, maxOrder, pageShifts, chunkSize);
        }

        @Override
        protected void destroyChunk(Chunk<byte[]> chunk) {
            // Rely on GC.
        }

        @Override
        protected PooledByteBuf<byte[]> newByteBuf(int maxCapacity) {
            return new PooledHeapByteBuf(maxCapacity);
        }

        @Override
        protected void memoryCopy(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
            if (length == 0) {
                return;
            }

            System.arraycopy(src, srcOffset, dst, dstOffset, length);
        }
    }

    private static final class DirectArena extends Arena<ByteBuffer> {

        DirectArena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize);
        }

        @Override
        protected Chunk<ByteBuffer> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            return new Chunk<ByteBuffer>(
                    this, ByteBuffer.allocateDirect(chunkSize), pageSize, maxOrder, pageShifts, chunkSize);
        }

        @Override
        protected void destroyChunk(Chunk<ByteBuffer> chunk) {
            UnpooledDirectByteBuf.freeDirect(chunk.memory);
        }

        @Override
        protected PooledByteBuf<ByteBuffer> newByteBuf(int maxCapacity) {
            return new PooledDirectByteBuf(maxCapacity);
        }

        @Override
        protected void memoryCopy(ByteBuffer src, int srcOffset, ByteBuffer dst, int dstOffset, int length) {
            if (length == 0) {
                return;
            }

            // We must duplicate the NIO buffers because they may be accessed by other Netty buffers.
            src = src.duplicate();
            dst = dst.duplicate();
            src.position(srcOffset).limit(srcOffset + length);
            dst.position(dstOffset);
            dst.put(src);
        }
    }

    static final class Chunk<T> {
        private static final int ST_UNUSED = 0;
        private static final int ST_BRANCH = 1;
        private static final int ST_ALLOCATED = 2;
        private static final int ST_ALLOCATED_SUBPAGE = ST_ALLOCATED | 1;

        private static final long multiplier = 0x5DEECE66DL;
        private static final long addend = 0xBL;
        private static final long mask = (1L << 48) - 1;

        final Arena<T> arena;

        private final T memory;
        private final int[] memoryMap;
        private final Subpage<T>[] subpages;
        /** Used to determine if the requested capacity is equal to or greater than pageSize. */
        private final int subpageOverflowMask;
        private final int pageSize;
        private final int pageShifts;

        private final int chunkSize;
        private final int maxSubpageAllocs;

        private long random = (System.nanoTime() ^ multiplier) & mask;

        private int freeBytes;
        private ChunkList<T> parent;
        private Chunk<T> prev;
        private Chunk<T> next;

        private long timestamp; // Arena updates and checks this timestamp to determine when to destroy this chunk.

        Chunk(Arena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            this.arena = arena;
            this.memory = memory;
            this.pageSize = pageSize;
            this.pageShifts = pageShifts;
            this.chunkSize = chunkSize;
            subpageOverflowMask = ~(pageSize - 1);
            freeBytes = chunkSize;

            int chunkSizeInPages = chunkSize >>> pageShifts;
            maxSubpageAllocs = 1 << maxOrder;

            // Generate the memory map.
            memoryMap = new int[(maxSubpageAllocs << 1)];
            int memoryMapIndex = 1;
            for (int i = 0; i <= maxOrder; i ++) {
                int runSizeInPages = chunkSizeInPages >>> i;
                for (int j = 0; j < chunkSizeInPages; j += runSizeInPages) {
                    //noinspection PointlessBitwiseExpression
                    memoryMap[memoryMapIndex ++] = j << 17 | runSizeInPages << 2 | ST_UNUSED;
                }
            }

            //noinspection unchecked
            subpages = new Subpage[maxSubpageAllocs];
        }

        private int usage() {
            if (freeBytes == 0) {
                return 100;
            }

            int freePercentage = (int) (freeBytes * 100L / chunkSize);
            if (freePercentage == 0) {
                return 99;
            }
            return 100 - freePercentage;
        }

        private long allocate(int capacity) {
            int firstVal = memoryMap[1];
            if ((capacity & subpageOverflowMask) != 0) { // >= pageSize
                return allocateRun(capacity, 1, firstVal);
            } else {
                return allocateSubpage(capacity, 1, firstVal);
            }
        }

        private long allocateRun(int capacity, int curIdx, int val) {
            for (;;) {
                if ((val & ST_ALLOCATED) != 0) { // state == ST_ALLOCATED || state == ST_ALLOCATED_SUBPAGE
                    return -1;
                }

                if ((val & ST_BRANCH) != 0) { // state == ST_BRANCH
                    int nextIdx = curIdx << 1 ^ nextRandom();
                    long res = allocateRun(capacity, nextIdx, memoryMap[nextIdx]);
                    if (res > 0) {
                        return res;
                    }

                    curIdx = nextIdx ^ 1;
                    val = memoryMap[curIdx];
                    continue;
                }

                // state == ST_UNUSED
                return allocateRunSimple(capacity, curIdx, val);
            }
        }

        private long allocateRunSimple(int capacity, int curIdx, int val) {
            int runLength = runLength(val);
            if (capacity > runLength) {
                return -1;
            }

            for (;;) {
                if (capacity == runLength) {
                    // Found the run that fits.
                    // Note that capacity has been normalized already, so we don't need to deal with
                    // the values that are not power of 2.
                    memoryMap[curIdx] = val & ~3 | ST_ALLOCATED;
                    freeBytes -= runLength;
                    return curIdx;
                }

                int nextIdx = curIdx << 1 ^ nextRandom();
                int unusedIdx = nextIdx ^ 1;

                memoryMap[curIdx] = val & ~3 | ST_BRANCH;
                //noinspection PointlessBitwiseExpression
                memoryMap[unusedIdx] = memoryMap[unusedIdx] & ~3 | ST_UNUSED;

                runLength >>>= 1;
                curIdx = nextIdx;
                val = memoryMap[curIdx];
            }
        }

        private long allocateSubpage(int capacity, int curIdx, int val) {
            int state = val & 3;
            if (state == ST_BRANCH) {
                int nextIdx = curIdx << 1 ^ nextRandom();
                long res = branchSubpage(capacity, nextIdx);
                if (res > 0) {
                    return res;
                }

                return branchSubpage(capacity, nextIdx ^ 1);
            }

            if (state == ST_UNUSED) {
                return allocateSubpageSimple(capacity, curIdx, val);
            }

            if (state == ST_ALLOCATED_SUBPAGE) {
                Subpage<T> subpage = subpages[subpageIdx(curIdx)];
                int elemSize = subpage.elemSize;
                if (capacity != elemSize) {
                    return -1;
                }

                return subpage.allocate();
            }

            return -1;
        }

        private long allocateSubpageSimple(int capacity, int curIdx, int val) {
            int runLength = runLength(val);
            for (;;) {
                if (runLength == pageSize) {
                    memoryMap[curIdx] = val & ~3 | ST_ALLOCATED_SUBPAGE;
                    freeBytes -= runLength;

                    int subpageIdx = subpageIdx(curIdx);
                    Subpage<T> subpage = subpages[subpageIdx];
                    if (subpage == null) {
                        subpage = new Subpage<T>(this, curIdx, runOffset(val), pageSize, capacity);
                        subpages[subpageIdx] = subpage;
                    } else {
                        subpage.init(capacity);
                    }
                    arena.addSubpage(subpage);
                    return subpage.allocate();
                }

                int nextIdx = curIdx << 1 ^ nextRandom();
                int unusedIdx = nextIdx ^ 1;

                memoryMap[curIdx] = val & ~3 | ST_BRANCH;
                //noinspection PointlessBitwiseExpression
                memoryMap[unusedIdx] = memoryMap[unusedIdx] & ~3 | ST_UNUSED;

                runLength >>>= 1;
                curIdx = nextIdx;
                val = memoryMap[curIdx];
            }
        }

        private long branchSubpage(int capacity, int nextIdx) {
            int nextVal = memoryMap[nextIdx];
            if ((nextVal & 3) != ST_ALLOCATED) {
                return allocateSubpage(capacity, nextIdx, nextVal);
            }
            return -1;
        }

        /**
         * @return {@code true} if this chunk is in use.
         *         {@code false} if this chunk is not used by its arena and thus it's OK to be deallocated.
         */
        private boolean free(long handle) {
            int memoryMapIdx = (int) handle;
            int bitmapIdx = (int) (handle >>> 32);

            int val = memoryMap[memoryMapIdx];
            int state = val & 3;
            if (state == ST_ALLOCATED_SUBPAGE) {
                assert bitmapIdx != 0;
                Subpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
                assert subpage != null && subpage.doNotDestroy;
                if (subpage.free(bitmapIdx & 0x3FFFFFFF)) {
                    return true;
                }
            } else {
                assert state == ST_ALLOCATED : "state: " + state;
                assert bitmapIdx == 0;
            }

            freeBytes += runLength(val);

            for (;;) {
                //noinspection PointlessBitwiseExpression
                memoryMap[memoryMapIdx] = val & ~3 | ST_UNUSED;
                if (memoryMapIdx == 1) {
                    assert freeBytes == chunkSize;
                    return false;
                }

                if ((memoryMap[siblingIdx(memoryMapIdx)] & 3) != ST_UNUSED) {
                    break;
                }

                memoryMapIdx = parentIdx(memoryMapIdx);
                val = memoryMap[memoryMapIdx];
            }

            return true;
        }

        private void initBuf(PooledByteBuf<T> buf, long handle) {
            int memoryMapIdx = (int) handle;
            int bitmapIdx = (int) (handle >>> 32);
            if (bitmapIdx == 0) {
                int val = memoryMap[memoryMapIdx];
                assert (val & 3) == ST_ALLOCATED : String.valueOf(val & 3);
                buf.init(this, handle, memory, runOffset(val), runLength(val));
            } else {
                initBufWithSubpage(buf, handle, bitmapIdx);
            }
        }

        private void initBufWithSubpage(PooledByteBuf<T> buf, long handle) {
            initBufWithSubpage(buf, handle, (int) (handle >>> 32));
        }

        private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx) {
            assert bitmapIdx != 0;

            int memoryMapIdx = (int) handle;
            int val = memoryMap[memoryMapIdx];
            assert (val & 3) == ST_ALLOCATED_SUBPAGE;

            Subpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage.doNotDestroy;

            buf.init(
                    this, handle, memory,
                    runOffset(val) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize, subpage.elemSize);
        }

        private static int parentIdx(int memoryMapIdx) {
            return memoryMapIdx >>> 1;
        }

        private static int siblingIdx(int memoryMapIdx) {
            return memoryMapIdx ^ 1;
        }

        private int runLength(int val) {
            return (val >>> 2 & 0x7FFF) << pageShifts;
        }

        private int runOffset(int val) {
            return val >>> 17 << pageShifts;
        }

        private int subpageIdx(int memoryMapIdx) {
            return memoryMapIdx - maxSubpageAllocs;
        }

        private int nextRandom() {
            random = random * multiplier + addend & mask;
            return (int) (random >>> 47) & 1;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("Chunk(");
            buf.append(Integer.toHexString(System.identityHashCode(this)));
            buf.append(": ");
            buf.append(usage());
            buf.append("%, ");
            buf.append(chunkSize - freeBytes);
            buf.append('/');
            buf.append(chunkSize);
            buf.append(')');
            return buf.toString();
        }
    }

    private static final class Subpage<T> {

        final Chunk<T> chunk;
        final int memoryMapIdx;
        final int runOffset;
        final int pageSize;
        final long[] bitmap;

        boolean doNotDestroy;
        int elemSize;
        int maxNumElems;
        int nextAvail;
        int bitmapLength;
        int numAvail;

        Subpage(Chunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
            this.chunk = chunk;
            this.memoryMapIdx = memoryMapIdx;
            this.runOffset = runOffset;
            this.pageSize = pageSize;
            bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64
            init(elemSize);
        }

        void init(int elemSize) {
            doNotDestroy = true;
            this.elemSize = elemSize;
            maxNumElems = numAvail = pageSize / elemSize;
            nextAvail = 0;
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
            if (numAvail == 0 || !doNotDestroy) {
                return -1;
            }

            final int bitmapIdx = nextAvail;
            int q = bitmapIdx >>> 6;
            int r = bitmapIdx & 63;
            assert (bitmap[q] >>> r & 1) == 0;
            bitmap[q] |= 1L << r;
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
            assert (bitmap[q] >>> r & 1) != 0;
            bitmap[q] ^= 1L << r;

            if (numAvail ++ == 0) {
                nextAvail = bitmapIdx;
                chunk.arena.addSubpage(this);
                return true;
            }

            if (numAvail < maxNumElems) {
                return true;
            } else {
                doNotDestroy = false;
                return false;
            }
        }

        public String toString() {
            if (!doNotDestroy) {
                return "(" + memoryMapIdx + ": not in use)";
            }

            return String.valueOf('(') + memoryMapIdx + ": " + (maxNumElems - numAvail) + '/' + maxNumElems + ", offset: " + runOffset + ", length: " + pageSize + ", elemSize: " + elemSize + ')';
        }
    }

    static final class ThreadCache {
        private final Arena<byte[]> heapArena;
        private final Arena<ByteBuffer> directArena;

        ThreadCache(Arena<byte[]> heapArena, Arena<ByteBuffer> directArena) {
            this.heapArena = heapArena;
            this.directArena = directArena;
        }
    }

    public static void main(String[] args) throws Exception {
        new Thread() {
            @Override
            public void run() {
                for (;;) {
//                    System.err.println(DEFAULT.toString());
//                    System.err.println();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }.start();
        ByteBufAllocator alloc = DEFAULT;
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
    }

    private static void test(ByteBufAllocator alloc) {
        final int size = DEFAULT_PAGE_SIZE / 2 + 1;
        Deque<ByteBuf> queue = new ArrayDeque<ByteBuf>();
        for (int i = 0; i < 2048 + 256; i ++) {
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
