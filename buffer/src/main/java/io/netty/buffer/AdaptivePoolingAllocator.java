/*
 * Copyright 2022 The Netty Project
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

import io.netty.util.ByteProcessor;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.NettyRuntime;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ReferenceCountUpdater;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThreadExecutorMap;
import io.netty.util.internal.UnstableApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.Arrays;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.StampedLock;

/**
 * An auto-tuning pooling allocator, that follows an anti-generational hypothesis.
 * <p>
 * The allocator is organized into a list of Magazines, and each magazine has a chunk-buffer that they allocate buffers
 * from.
 * <p>
 * The magazines hold the mutexes that ensure the thread-safety of the allocator, and each thread picks a magazine
 * based on the id of the thread. This spreads the contention of multi-threaded access across the magazines.
 * If contention is detected above a certain threshold, the number of magazines are increased in response to the
 * contention.
 * <p>
 * The magazines maintain histograms of the sizes of the allocations they do. The histograms are used to compute the
 * preferred chunk size. The preferred chunk size is one that is big enough to service 10 allocations of the
 * 99-percentile size. This way, the chunk size is adapted to the allocation patterns.
 * <p>
 * Computing the preferred chunk size is a somewhat expensive operation. Therefore, the frequency with which this is
 * done, is also adapted to the allocation pattern. If a newly computed preferred chunk is the same as the previous
 * preferred chunk size, then the frequency is reduced. Otherwise, the frequency is increased.
 * <p>
 * This allows the allocator to quickly respond to changes in the application workload,
 * without suffering undue overhead from maintaining its statistics.
 * <p>
 * Since magazines are "relatively thread-local", the allocator has a central queue that allow excess chunks from any
 * magazine, to be shared with other magazines.
 * The {@link #createSharedChunkQueue()} method can be overridden to customize this queue.
 */
@SuppressJava6Requirement(reason = "Guarded by version check")
@UnstableApi
final class AdaptivePoolingAllocator implements AdaptiveByteBufAllocator.AdaptiveAllocatorApi {

    enum MagazineCaching {
        EventLoopThreads,
        FastThreadLocalThreads,
        None
    }

    private static final int EXPANSION_ATTEMPTS = 3;
    private static final int INITIAL_MAGAZINES = 4;
    private static final int RETIRE_CAPACITY = 4 * 1024;
    private static final int MIN_CHUNK_SIZE = 128 * 1024;
    private static final int MAX_STRIPES = NettyRuntime.availableProcessors() * 2;
    private static final int BUFS_PER_CHUNK = 10; // For large buffers, aim to have about this many buffers per chunk.

    /**
     * The maximum size of a pooled chunk, in bytes. Allocations bigger than this will never be pooled.
     * <p>
     * This number is 10 MiB, and is derived from the limitations of internal histograms.
     */
    private static final int MAX_CHUNK_SIZE =
            BUFS_PER_CHUNK * (1 << AllocationStatistics.HISTO_MAX_BUCKET_SHIFT); // 10 MiB.

    /**
     * The capacity if the central queue that allow chunks to be shared across magazines.
     * The default size is {@link NettyRuntime#availableProcessors()},
     * and the maximum number of magazines is twice this.
     * <p>
     * This means the maximum amount of memory that we can have allocated-but-not-in-use is
     * 5 * {@link NettyRuntime#availableProcessors()} * {@link #MAX_CHUNK_SIZE} bytes.
     */
    private static final int CENTRAL_QUEUE_CAPACITY = SystemPropertyUtil.getInt(
            "io.netty.allocator.centralQueueCapacity", NettyRuntime.availableProcessors());

    /**
     * The capacity if the magazine local buffer queue. This queue just pools the outer ByteBuf instance and not
     * the actual memory and so helps to reduce GC pressure.
     */
    private static final int MAGAZINE_BUFFER_QUEUE_CAPACITY = SystemPropertyUtil.getInt(
            "io.netty.allocator.magazineBufferQueueCapacity", 1024);

    private static final Object NO_MAGAZINE = Boolean.TRUE;

    private final ChunkAllocator chunkAllocator;
    private final Queue<Chunk> centralQueue;
    private final StampedLock magazineExpandLock;
    private volatile Magazine[] magazines;
    private final FastThreadLocal<Object> threadLocalMagazine;
    private final Set<Magazine> liveCachedMagazines;
    private volatile boolean freed;

    AdaptivePoolingAllocator(ChunkAllocator chunkAllocator, MagazineCaching magazineCaching) {
        ObjectUtil.checkNotNull(chunkAllocator, "chunkAllocator");
        ObjectUtil.checkNotNull(magazineCaching, "magazineCaching");
        this.chunkAllocator = chunkAllocator;
        centralQueue = ObjectUtil.checkNotNull(createSharedChunkQueue(), "centralQueue");
        magazineExpandLock = new StampedLock();
        if (magazineCaching != MagazineCaching.None) {
            assert magazineCaching == MagazineCaching.EventLoopThreads ||
                   magazineCaching == MagazineCaching.FastThreadLocalThreads;
            final boolean cachedMagazinesNonEventLoopThreads =
                    magazineCaching == MagazineCaching.FastThreadLocalThreads;
            final Set<Magazine> liveMagazines = new CopyOnWriteArraySet<Magazine>();
            threadLocalMagazine = new FastThreadLocal<Object>() {
                @Override
                protected Object initialValue() {
                    if (cachedMagazinesNonEventLoopThreads || ThreadExecutorMap.currentExecutor() != null) {
                        Magazine mag = new Magazine(AdaptivePoolingAllocator.this, false);

                        if (FastThreadLocalThread.willCleanupFastThreadLocals(Thread.currentThread())) {
                            // Only add it to the liveMagazines if we can guarantee that onRemoval(...) is called,
                            // as otherwise we might end up holding the reference forever.
                            liveMagazines.add(mag);
                        }
                        return mag;
                    }
                    return NO_MAGAZINE;
                }

                @Override
                protected void onRemoval(final Object value) throws Exception {
                    if (value != NO_MAGAZINE) {
                        liveMagazines.remove(value);
                    }
                }
            };
            liveCachedMagazines = liveMagazines;
        } else {
            threadLocalMagazine = null;
            liveCachedMagazines = null;
        }
        Magazine[] mags = new Magazine[INITIAL_MAGAZINES];
        for (int i = 0; i < mags.length; i++) {
            mags[i] = new Magazine(this);
        }
        magazines = mags;
    }

    /**
     * Create a thread-safe multi-producer, multi-consumer queue to hold chunks that spill over from the
     * internal Magazines.
     * <p>
     * Each Magazine can only hold two chunks at any one time: the chunk it currently allocates from,
     * and the next-in-line chunk which will be used for allocation once the current one has been used up.
     * This queue will be used by magazines to share any excess chunks they allocate, so that they don't need to
     * allocate new chunks when their current and next-in-line chunks have both been used up.
     * <p>
     * The simplest implementation of this method is to return a new {@link ConcurrentLinkedQueue}.
     * However, the {@code CLQ} is unbounded, and this means there's no limit to how many chunks can be cached in this
     * queue.
     * <p>
     * Each chunk in this queue can be up to {@link #MAX_CHUNK_SIZE} in size, so it is recommended to use a bounded
     * queue to limit the maximum memory usage.
     * <p>
     * The default implementation will create a bounded queue with a capacity of {@link #CENTRAL_QUEUE_CAPACITY}.
     *
     * @return A new multi-producer, multi-consumer queue.
     */
    private static Queue<Chunk> createSharedChunkQueue() {
        return PlatformDependent.newFixedMpmcQueue(CENTRAL_QUEUE_CAPACITY);
    }

    @Override
    public ByteBuf allocate(int size, int maxCapacity) {
        return allocate(size, maxCapacity, Thread.currentThread(), null);
    }

    private AdaptiveByteBuf allocate(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
        if (size <= MAX_CHUNK_SIZE) {
            int sizeBucket = AllocationStatistics.sizeBucket(size); // Compute outside of Magazine lock for better ILP.
            FastThreadLocal<Object> threadLocalMagazine = this.threadLocalMagazine;
            if (threadLocalMagazine != null && currentThread instanceof FastThreadLocalThread) {
                Object mag = threadLocalMagazine.get();
                if (mag != NO_MAGAZINE) {
                    Magazine magazine = (Magazine) mag;
                    if (buf == null) {
                        buf = magazine.newBuffer();
                    }
                    boolean allocated = magazine.tryAllocate(size, sizeBucket, maxCapacity, buf);
                    assert allocated : "Allocation of threadLocalMagazine must always succeed";
                    return buf;
                }
            }
            long threadId = currentThread.getId();
            Magazine[] mags;
            int expansions = 0;
            do {
                mags = magazines;
                int mask = mags.length - 1;
                int index = (int) (threadId & mask);
                for (int i = 0, m = Integer.numberOfTrailingZeros(~mask); i < m; i++) {
                    Magazine mag = mags[index + i & mask];
                    if (buf == null) {
                        buf = mag.newBuffer();
                    }
                    if (mag.tryAllocate(size, sizeBucket, maxCapacity, buf)) {
                        // Was able to allocate.
                        return buf;
                    }
                }
                expansions++;
            } while (expansions <= EXPANSION_ATTEMPTS && tryExpandMagazines(mags.length));
        }

        // The magazines failed us, or the buffer is too big to be pooled.
        return allocateFallback(size, maxCapacity, currentThread, buf);
    }

    private AdaptiveByteBuf allocateFallback(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
        // If we don't already have a buffer, obtain one from the most conveniently available magazine.
        Magazine magazine;
        if (buf != null) {
            Chunk chunk = buf.chunk;
            magazine = chunk != null && chunk != Magazine.MAGAZINE_FREED?
                    chunk.magazine : getFallbackMagazine(currentThread);
        } else {
            magazine = getFallbackMagazine(currentThread);
            buf = magazine.newBuffer();
        }
        // Create a one-off chunk for this allocation.
        AbstractByteBuf innerChunk = chunkAllocator.allocate(size, maxCapacity);
        Chunk chunk = new Chunk(innerChunk, magazine, false);
        try {
            chunk.readInitInto(buf, size, maxCapacity);
        } finally {
            // As the chunk is an one-off we need to always call release explicitly as readInitInto(...)
            // will take care of retain once when successful. Once The AdaptiveByteBuf is released it will
            // completely release the Chunk and so the contained innerChunk.
            chunk.release();
        }
        return buf;
    }

    private Magazine getFallbackMagazine(Thread currentThread) {
        Object tlMag;
        FastThreadLocal<Object> threadLocalMagazine = this.threadLocalMagazine;
        if (threadLocalMagazine != null &&
                currentThread instanceof FastThreadLocalThread &&
                (tlMag = threadLocalMagazine.get()) != NO_MAGAZINE) {
            return (Magazine) tlMag;
        }
        Magazine[] mags = magazines;
        return mags[(int) currentThread.getId() & mags.length - 1];
    }

    /**
     * Allocate into the given buffer. Used by {@link AdaptiveByteBuf#capacity(int)}.
     */
    void allocate(int size, int maxCapacity, AdaptiveByteBuf into) {
        AdaptiveByteBuf result = allocate(size, maxCapacity, Thread.currentThread(), into);
        assert result == into: "Re-allocation created separate buffer instance";
    }

    @Override
    public long usedMemory() {
        long sum = 0;
        for (Chunk chunk : centralQueue) {
            sum += chunk.capacity();
        }
        for (Magazine magazine : magazines) {
            sum += magazine.usedMemory.get();
        }
        if (liveCachedMagazines != null) {
            for (Magazine magazine : liveCachedMagazines) {
                sum += magazine.usedMemory.get();
            }
        }
        return sum;
    }

    private boolean tryExpandMagazines(int currentLength) {
        if (currentLength >= MAX_STRIPES) {
            return true;
        }
        long writeLock = magazineExpandLock.tryWriteLock();
        if (writeLock != 0) {
            try {
                Magazine[] mags = magazines;
                if (mags.length >= MAX_STRIPES || mags.length > currentLength || freed) {
                    return true;
                }
                int preferredChunkSize = mags[0].sharedPrefChunkSize;
                Magazine[] expanded = new Magazine[mags.length * 2];
                for (int i = 0, l = expanded.length; i < l; i++) {
                    Magazine m = new Magazine(this);
                    m.localPrefChunkSize = preferredChunkSize;
                    m.sharedPrefChunkSize = preferredChunkSize;
                    expanded[i] = m;
                }
                magazines = expanded;
                for (Magazine magazine : mags) {
                    magazine.free();
                }
            } finally {
                magazineExpandLock.unlockWrite(writeLock);
            }
        }
        return true;
    }

    private boolean offerToQueue(Chunk buffer) {
        if (freed) {
            return false;
        }
        return centralQueue.offer(buffer);
    }

    // Ensure that we release all previous pooled resources when this object is finalized. This is needed as otherwise
    // we might end up with leaks. While these leaks are usually harmless in reality it would still at least be
    // very confusing for users.
    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            free();
        }
    }

    private void free() {
        freed = true;
        long stamp = magazineExpandLock.writeLock();
        try {
            Magazine[] mags = magazines;
            for (Magazine magazine : mags) {
                magazine.free();
            }
        } finally {
            magazineExpandLock.unlockWrite(stamp);
        }
        for (;;) {
            Chunk chunk = centralQueue.poll();
            if (chunk == null) {
                break;
            }
            chunk.release();
        }
    }

    static int sizeBucket(int size) {
        return AllocationStatistics.sizeBucket(size);
    }

    @SuppressWarnings("checkstyle:finalclass") // Checkstyle mistakenly believes this class should be final.
    private static class AllocationStatistics {
        private static final int MIN_DATUM_TARGET = 1024;
        private static final int MAX_DATUM_TARGET = 65534;
        private static final int INIT_DATUM_TARGET = 9;
        private static final int HISTO_MIN_BUCKET_SHIFT = 13; // Smallest bucket is 1 << 13 = 8192 bytes in size.
        private static final int HISTO_MAX_BUCKET_SHIFT = 20; // Biggest bucket is 1 << 20 = 1 MiB bytes in size.
        private static final int HISTO_BUCKET_COUNT = 1 + HISTO_MAX_BUCKET_SHIFT - HISTO_MIN_BUCKET_SHIFT; // 8 buckets.
        private static final int HISTO_MAX_BUCKET_MASK = HISTO_BUCKET_COUNT - 1;
        private static final int SIZE_MAX_MASK = MAX_CHUNK_SIZE - 1;

        protected final AdaptivePoolingAllocator parent;
        private final boolean shareable;
        private final short[][] histos = {
                new short[HISTO_BUCKET_COUNT], new short[HISTO_BUCKET_COUNT],
                new short[HISTO_BUCKET_COUNT], new short[HISTO_BUCKET_COUNT],
        };
        private short[] histo = histos[0];
        private final int[] sums = new int[HISTO_BUCKET_COUNT];

        private int histoIndex;
        private int datumCount;
        private int datumTarget = INIT_DATUM_TARGET;
        protected volatile int sharedPrefChunkSize = MIN_CHUNK_SIZE;
        protected volatile int localPrefChunkSize = MIN_CHUNK_SIZE;

        private AllocationStatistics(AdaptivePoolingAllocator parent, boolean shareable) {
            this.parent = parent;
            this.shareable = shareable;
        }

        protected void recordAllocationSize(int bucket) {
            histo[bucket]++;
            if (datumCount++ == datumTarget) {
                rotateHistograms();
            }
        }

        static int sizeBucket(int size) {
            if (size == 0) {
                return 0;
            }
            // Minimum chunk size is 128 KiB. We'll only make bigger chunks if the 99-percentile is 16 KiB or greater,
            // so we truncate and roll up the bottom part of the histogram to 8 KiB.
            // The upper size band is 1 MiB, and that gives us exactly 8 size buckets,
            // which is a magical number for JIT optimisations.
            int normalizedSize = size - 1 >> HISTO_MIN_BUCKET_SHIFT & SIZE_MAX_MASK;
            return Math.min(Integer.SIZE - Integer.numberOfLeadingZeros(normalizedSize), HISTO_MAX_BUCKET_MASK);
        }

        private void rotateHistograms() {
            short[][] hs = histos;
            for (int i = 0; i < HISTO_BUCKET_COUNT; i++) {
                sums[i] = (hs[0][i] & 0xFFFF) + (hs[1][i] & 0xFFFF) + (hs[2][i] & 0xFFFF) + (hs[3][i] & 0xFFFF);
            }
            int sum = 0;
            for (int count : sums) {
                sum  += count;
            }
            int targetPercentile = (int) (sum * 0.99);
            int sizeBucket = 0;
            for (; sizeBucket < sums.length; sizeBucket++) {
                if (sums[sizeBucket] > targetPercentile) {
                    break;
                }
                targetPercentile -= sums[sizeBucket];
            }
            int percentileSize = 1 << sizeBucket + HISTO_MIN_BUCKET_SHIFT;
            int prefChunkSize = Math.max(percentileSize * BUFS_PER_CHUNK, MIN_CHUNK_SIZE);
            localPrefChunkSize = prefChunkSize;
            if (shareable) {
                for (Magazine mag : parent.magazines) {
                    prefChunkSize = Math.max(prefChunkSize, mag.localPrefChunkSize);
                }
            }
            if (sharedPrefChunkSize != prefChunkSize) {
                // Preferred chunk size changed. Increase check frequency.
                datumTarget = Math.max(datumTarget >> 1, MIN_DATUM_TARGET);
                sharedPrefChunkSize = prefChunkSize;
            } else {
                // Preferred chunk size did not change. Check less often.
                datumTarget = Math.min(datumTarget << 1, MAX_DATUM_TARGET);
            }

            histoIndex = histoIndex + 1 & 3;
            histo = histos[histoIndex];
            datumCount = 0;
            Arrays.fill(histo, (short) 0);
        }

        /**
         * Get the preferred chunk size, based on statistics from the {@linkplain #recordAllocationSize(int) recorded}
         * allocation sizes.
         * <p>
         * This method must be thread-safe.
         *
         * @return The currently preferred chunk allocation size.
         */
        protected int preferredChunkSize() {
            return sharedPrefChunkSize;
        }
    }

    @SuppressJava6Requirement(reason = "Guarded by version check")
    private static final class Magazine extends AllocationStatistics {
        private static final AtomicReferenceFieldUpdater<Magazine, Chunk> NEXT_IN_LINE;
        static {
            NEXT_IN_LINE = AtomicReferenceFieldUpdater.newUpdater(Magazine.class, Chunk.class, "nextInLine");
        }
        private static final Chunk MAGAZINE_FREED = new Chunk();

        private Chunk current;
        @SuppressWarnings("unused") // updated via NEXT_IN_LINE
        private volatile Chunk nextInLine;
        private final AtomicLong usedMemory;
        private final StampedLock allocationLock;
        private final Queue<AdaptiveByteBuf> bufferQueue;
        private final ObjectPool.Handle<AdaptiveByteBuf> handle;

        Magazine(AdaptivePoolingAllocator parent) {
            this(parent, true);
        }

        Magazine(AdaptivePoolingAllocator parent, boolean shareable) {
            super(parent, shareable);

            // We only need the StampedLock if this Magazine will be shared across threads.
            if (shareable) {
                allocationLock = new StampedLock();
            } else {
                allocationLock = null;
            }
            usedMemory = new AtomicLong();
            bufferQueue = PlatformDependent.newFixedMpmcQueue(MAGAZINE_BUFFER_QUEUE_CAPACITY);
            handle = new ObjectPool.Handle<AdaptiveByteBuf>() {
                @Override
                public void recycle(AdaptiveByteBuf self) {
                    bufferQueue.offer(self);
                }
            };
        }

        public boolean tryAllocate(int size, int sizeBucket, int maxCapacity, AdaptiveByteBuf buf) {
            if (allocationLock == null) {
                // This magazine is not shared across threads, just allocate directly.
                return allocate(size, sizeBucket, maxCapacity, buf);
            }

            // Try to retrieve the lock and if successful allocate.
            long writeLock = allocationLock.tryWriteLock();
            if (writeLock != 0) {
                try {
                    return allocate(size, sizeBucket, maxCapacity, buf);
                } finally {
                    allocationLock.unlockWrite(writeLock);
                }
            }
            return false;
        }

        private boolean allocate(int size, int sizeBucket, int maxCapacity, AdaptiveByteBuf buf) {
            recordAllocationSize(sizeBucket);
            Chunk curr = current;
            if (curr != null) {
                if (curr.remainingCapacity() > size) {
                    curr.readInitInto(buf, size, maxCapacity);
                    // We still have some bytes left that we can use for the next allocation, just early return.
                    return true;
                }

                // At this point we know that this will be the last time current will be used, so directly set it to
                // null and release it once we are done.
                current = null;
                if (curr.remainingCapacity() == size) {
                    try {
                        curr.readInitInto(buf, size, maxCapacity);
                        return true;
                    } finally {
                        curr.release();
                    }
                }
            }
            Chunk last = curr;
            assert current == null;
            // The fast-path for allocations did not work.
            //
            // Try to fetch the next "Magazine local" Chunk first, if this this fails as we don't have
            // one setup we will poll our centralQueue. If this fails as well we will just allocate a new Chunk.
            //
            // In any case we will store the Chunk as the current so it will be used again for the next allocation and
            // so be "reserved" by this Magazine for exclusive usage.
            if (nextInLine != null) {
                curr = NEXT_IN_LINE.getAndSet(this, null);
                if (curr == MAGAZINE_FREED) {
                    // Allocation raced with a stripe-resize that freed this magazine.
                    restoreMagazineFreed();
                    return false;
                }
            } else {
                curr = parent.centralQueue.poll();
                if (curr == null) {
                    curr = newChunkAllocation(size);
                }
            }
            current = curr;
            assert current != null;
            if (last != null) {
                if (last.remainingCapacity() < RETIRE_CAPACITY) {
                    last.release();
                } else {
                    transferChunk(last);
                }
            }
            if (curr.remainingCapacity() > size) {
                curr.readInitInto(buf, size, maxCapacity);
            } else if (curr.remainingCapacity() == size) {
                try {
                    curr.readInitInto(buf, size, maxCapacity);
                } finally {
                    // Release in a finally block so even if readInitInto(...) would throw we would still correctly
                    // release the current chunk before null it out.
                    curr.release();
                    current = null;
                }
            } else {
                Chunk newChunk = newChunkAllocation(size);
                try {
                    newChunk.readInitInto(buf, size, maxCapacity);
                    if (curr.remainingCapacity() < RETIRE_CAPACITY) {
                        curr.release();
                        current = newChunk;
                    } else {
                        transferChunk(newChunk);
                    }
                    newChunk = null;
                } finally {
                    if (newChunk != null) {
                        assert current == null;
                        // Something went wrong, let's ensure we not leak the newChunk.
                        newChunk.release();
                    }
                }
            }
            return true;
        }

        private void restoreMagazineFreed() {
            Chunk next = NEXT_IN_LINE.getAndSet(this, MAGAZINE_FREED);
            if (next != null && next != MAGAZINE_FREED) {
                next.release(); // A chunk snuck in through a race. Release it after restoring MAGAZINE_FREED state.
            }
        }

        private void transferChunk(Chunk current) {
            if (NEXT_IN_LINE.compareAndSet(this, null, current)
                    || parent.offerToQueue(current)) {
                return;
            }
            Chunk nextChunk = NEXT_IN_LINE.get(this);
            if (nextChunk != null && current.remainingCapacity() > nextChunk.remainingCapacity()) {
                if (NEXT_IN_LINE.compareAndSet(this, nextChunk, current)) {
                    if (nextChunk != MAGAZINE_FREED) {
                        nextChunk.release();
                    }
                    return;
                }
            }
            // Next-in-line is occupied AND the central queue is full.
            // Rare that we should get here, but we'll only do one allocation out of this chunk, then.
            current.release();
        }

        private Chunk newChunkAllocation(int promptingSize) {
            int size = Math.max(promptingSize * BUFS_PER_CHUNK, preferredChunkSize());
            ChunkAllocator chunkAllocator = parent.chunkAllocator;
            return new Chunk(chunkAllocator.allocate(size, size), this, true);
        }

        boolean trySetNextInLine(Chunk chunk) {
            return NEXT_IN_LINE.compareAndSet(this, null, chunk);
        }

        void free() {
            // Release the current Chunk and the next that was stored for later usage.
            restoreMagazineFreed();
            long stamp = allocationLock.writeLock();
            try {
                if (current != null) {
                    current.release();
                    current = null;
                }
            } finally {
                allocationLock.unlockWrite(stamp);
            }
        }

        public AdaptiveByteBuf newBuffer() {
            AdaptiveByteBuf buf = bufferQueue.poll();
            if (buf == null) {
                buf = new AdaptiveByteBuf(handle);
            } else {
                buf.resetRefCnt();
                buf.discardMarks();
            }
            return buf;
        }
    }

    private static final class Chunk implements ReferenceCounted {

        private final AbstractByteBuf delegate;
        final Magazine magazine;
        private final int capacity;
        private final boolean pooled;
        private int allocatedBytes;
        private static final long REFCNT_FIELD_OFFSET =
                ReferenceCountUpdater.getUnsafeOffset(Chunk.class, "refCnt");
        private static final AtomicIntegerFieldUpdater<Chunk> AIF_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(Chunk.class, "refCnt");

        private static final ReferenceCountUpdater<Chunk> updater =
                new ReferenceCountUpdater<Chunk>() {
                    @Override
                    protected AtomicIntegerFieldUpdater<Chunk> updater() {
                        return AIF_UPDATER;
                    }
                    @Override
                    protected long unsafeOffset() {
                        return REFCNT_FIELD_OFFSET;
                    }
                };

        // Value might not equal "real" reference count, all access should be via the updater
        @SuppressWarnings({"unused", "FieldMayBeFinal"})
        private volatile int refCnt;

        Chunk() {
            // Constructor only used by the MAGAZINE_FREED sentinel.
            delegate = null;
            magazine = null;
            capacity = 0;
            pooled = false;
        }

        Chunk(AbstractByteBuf delegate, Magazine magazine, boolean pooled) {
            this.delegate = delegate;
            this.magazine = magazine;
            this.pooled = pooled;
            capacity = delegate.capacity();
            magazine.usedMemory.getAndAdd(capacity);
            updater.setInitialValue(this);
        }

        @Override
        public Chunk touch(Object hint) {
            return this;
        }

        @Override
        public int refCnt() {
            return updater.refCnt(this);
        }

        @Override
        public Chunk retain() {
            return updater.retain(this);
        }

        @Override
        public Chunk retain(int increment) {
            return updater.retain(this, increment);
        }

        @Override
        public Chunk touch() {
            return this;
        }

        @Override
        public boolean release() {
            if (updater.release(this)) {
                deallocate();
                return true;
            }
            return false;
        }

        @Override
        public boolean release(int decrement) {
            if (updater.release(this, decrement)) {
                deallocate();
                return true;
            }
            return false;
        }

        private void deallocate() {
            Magazine mag = magazine;
            AdaptivePoolingAllocator parent = mag.parent;
            int chunkSize = mag.preferredChunkSize();
            int memSize = delegate.capacity();
            if (!pooled || memSize < chunkSize || memSize > chunkSize + (chunkSize >> 1)) {
                // Drop the chunk if the parent allocator is closed, or if the chunk is smaller than the
                // preferred chunk size, or over 50% larger than the preferred chunk size.
                mag.usedMemory.getAndAdd(-capacity());
                delegate.release();
            } else {
                updater.resetRefCnt(this);
                delegate.setIndex(0, 0);
                allocatedBytes = 0;
                if (!mag.trySetNextInLine(this)) {
                    if (!parent.offerToQueue(this)) {
                        // The central queue is full. Ensure we release again as we previously did use resetRefCnt()
                        // which did increase the reference count by 1.
                        boolean released = updater.release(this);
                        delegate.release();
                        assert released;
                    }
                }
            }
        }

        public void readInitInto(AdaptiveByteBuf buf, int size, int maxCapacity) {
            int startIndex = allocatedBytes;
            allocatedBytes = startIndex + size;
            Chunk chunk = this;
            chunk.retain();
            try {
                buf.init(delegate, chunk, 0, 0, startIndex, size, maxCapacity);
                chunk = null;
            } finally {
                if (chunk != null) {
                    // If chunk is not null we know that buf.init(...) failed and so we need to manually release
                    // the chunk again as we retained it before calling buf.init(...).
                    chunk.release();
                }
            }
        }

        public int remainingCapacity() {
            return capacity - allocatedBytes;
        }

        public int capacity() {
            return capacity;
        }
    }

    static final class AdaptiveByteBuf extends AbstractReferenceCountedByteBuf {

        private final ObjectPool.Handle<AdaptiveByteBuf> handle;

        private int adjustment;
        private AbstractByteBuf rootParent;
        Chunk chunk;
        private int length;
        private ByteBuffer tmpNioBuf;
        private boolean hasArray;
        private boolean hasMemoryAddress;

        AdaptiveByteBuf(ObjectPool.Handle<AdaptiveByteBuf> recyclerHandle) {
            super(0);
            handle = ObjectUtil.checkNotNull(recyclerHandle, "recyclerHandle");
        }

        void init(AbstractByteBuf unwrapped, Chunk wrapped, int readerIndex, int writerIndex,
                  int adjustment, int capacity, int maxCapacity) {
            this.adjustment = adjustment;
            chunk = wrapped;
            length = capacity;
            maxCapacity(maxCapacity);
            setIndex0(readerIndex, writerIndex);
            hasArray = unwrapped.hasArray();
            hasMemoryAddress = unwrapped.hasMemoryAddress();
            rootParent = unwrapped;
            tmpNioBuf = unwrapped.internalNioBuffer(adjustment, capacity).slice();
        }

        private AbstractByteBuf rootParent() {
            final AbstractByteBuf rootParent = this.rootParent;
            if (rootParent != null) {
                return rootParent;
            }
            throw new IllegalReferenceCountException();
        }

        @Override
        public int capacity() {
            return length;
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            if (newCapacity == capacity()) {
                ensureAccessible();
                return this;
            }
            checkNewCapacity(newCapacity);
            if (newCapacity < capacity()) {
                length = newCapacity;
                setIndex0(Math.min(readerIndex(), newCapacity), Math.min(writerIndex(), newCapacity));
                return this;
            }

            // Reallocation required.
            ByteBuffer data = tmpNioBuf;
            data.clear();
            tmpNioBuf = null;
            Chunk chunk = this.chunk;
            Magazine magazine = chunk.magazine;
            AdaptivePoolingAllocator allocator = magazine.parent;
            int readerIndex = this.readerIndex;
            int writerIndex = this.writerIndex;
            allocator.allocate(newCapacity, maxCapacity(), this);
            tmpNioBuf.put(data);
            tmpNioBuf.clear();
            chunk.release();
            this.readerIndex = readerIndex;
            this.writerIndex = writerIndex;
            return this;
        }

        @Override
        public ByteBufAllocator alloc() {
            return rootParent().alloc();
        }

        @Override
        public ByteOrder order() {
            return rootParent().order();
        }

        @Override
        public ByteBuf unwrap() {
            return null;
        }

        @Override
        public boolean isDirect() {
            return rootParent().isDirect();
        }

        @Override
        public int arrayOffset() {
            return idx(rootParent().arrayOffset());
        }

        @Override
        public boolean hasMemoryAddress() {
            return hasMemoryAddress;
        }

        @Override
        public long memoryAddress() {
            ensureAccessible();
            return rootParent().memoryAddress() + adjustment;
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            checkIndex(index, length);
            return rootParent().nioBuffer(idx(index), length);
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            checkIndex(index, length);
            return (ByteBuffer) internalNioBuffer().position(index).limit(index + length);
        }

        private ByteBuffer internalNioBuffer() {
            return (ByteBuffer) tmpNioBuf.clear();
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            checkIndex(index, length);
            return rootParent().nioBuffers(idx(index), length);
        }

        @Override
        public boolean hasArray() {
            return hasArray;
        }

        @Override
        public byte[] array() {
            ensureAccessible();
            return rootParent().array();
        }

        @Override
        public ByteBuf copy(int index, int length) {
            checkIndex(index, length);
            return rootParent().copy(idx(index), length);
        }

        @Override
        public int nioBufferCount() {
            return rootParent().nioBufferCount();
        }

        @Override
        protected byte _getByte(int index) {
            return rootParent()._getByte(idx(index));
        }

        @Override
        protected short _getShort(int index) {
            return rootParent()._getShort(idx(index));
        }

        @Override
        protected short _getShortLE(int index) {
            return rootParent()._getShortLE(idx(index));
        }

        @Override
        protected int _getUnsignedMedium(int index) {
            return rootParent()._getUnsignedMedium(idx(index));
        }

        @Override
        protected int _getUnsignedMediumLE(int index) {
            return rootParent()._getUnsignedMediumLE(idx(index));
        }

        @Override
        protected int _getInt(int index) {
            return rootParent()._getInt(idx(index));
        }

        @Override
        protected int _getIntLE(int index) {
            return rootParent()._getIntLE(idx(index));
        }

        @Override
        protected long _getLong(int index) {
            return rootParent()._getLong(idx(index));
        }

        @Override
        protected long _getLongLE(int index) {
            return rootParent()._getLongLE(idx(index));
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            checkIndex(index, length);
            rootParent().getBytes(idx(index), dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            checkIndex(index, length);
            rootParent().getBytes(idx(index), dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            checkIndex(index, dst.remaining());
            rootParent().getBytes(idx(index), dst);
            return this;
        }

        @Override
        protected void _setByte(int index, int value) {
            rootParent()._setByte(idx(index), value);
        }

        @Override
        protected void _setShort(int index, int value) {
            rootParent()._setShort(idx(index), value);
        }

        @Override
        protected void _setShortLE(int index, int value) {
            rootParent()._setShortLE(idx(index), value);
        }

        @Override
        protected void _setMedium(int index, int value) {
            rootParent()._setMedium(idx(index), value);
        }

        @Override
        protected void _setMediumLE(int index, int value) {
            rootParent()._setMediumLE(idx(index), value);
        }

        @Override
        protected void _setInt(int index, int value) {
            rootParent()._setInt(idx(index), value);
        }

        @Override
        protected void _setIntLE(int index, int value) {
            rootParent()._setIntLE(idx(index), value);
        }

        @Override
        protected void _setLong(int index, long value) {
            rootParent()._setLong(idx(index), value);
        }

        @Override
        protected void _setLongLE(int index, long value) {
            rootParent().setLongLE(idx(index), value);
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            checkIndex(index, length);
            rootParent().setBytes(idx(index), src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            checkIndex(index, length);
            rootParent().setBytes(idx(index), src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            checkIndex(index, src.remaining());
            rootParent().setBytes(idx(index), src);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length)
                throws IOException {
            checkIndex(index, length);
            if (length != 0) {
                ByteBufUtil.readBytes(alloc(), internalNioBuffer().duplicate(), index, length, out);
            }
            return this;
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length)
                throws IOException {
            return out.write(internalNioBuffer(index, length).duplicate());
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length)
                throws IOException {
            return out.write(internalNioBuffer(index, length).duplicate(), position);
        }

        @Override
        public int setBytes(int index, InputStream in, int length)
                throws IOException {
            checkIndex(index, length);
            final AbstractByteBuf rootParent = rootParent();
            if (rootParent.hasArray()) {
                return rootParent.setBytes(idx(index), in, length);
            }
            byte[] tmp = ByteBufUtil.threadLocalTempArray(length);
            int readBytes = in.read(tmp, 0, length);
            if (readBytes <= 0) {
                return readBytes;
            }
            setBytes(index, tmp, 0, readBytes);
            return readBytes;
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length)
                throws IOException {
            try {
                return in.read(internalNioBuffer(index, length).duplicate());
            } catch (ClosedChannelException ignored) {
                return -1;
            }
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length)
                throws IOException {
            try {
                return in.read(internalNioBuffer(index, length).duplicate(), position);
            } catch (ClosedChannelException ignored) {
                return -1;
            }
        }

        @Override
        public int forEachByte(int index, int length, ByteProcessor processor) {
            checkIndex(index, length);
            int ret = rootParent().forEachByte(idx(index), length, processor);
            return forEachResult(ret);
        }

        @Override
        public int forEachByteDesc(int index, int length, ByteProcessor processor) {
            checkIndex(index, length);
            int ret = rootParent().forEachByteDesc(idx(index), length, processor);
            return forEachResult(ret);
        }

        private int forEachResult(int ret) {
            if (ret < adjustment) {
                return -1;
            }
            return ret - adjustment;
        }

        @Override
        public boolean isContiguous() {
            return rootParent().isContiguous();
        }

        private int idx(int index) {
            return index + adjustment;
        }

        @Override
        protected void deallocate() {
            if (chunk != null) {
                chunk.release();
            }
            tmpNioBuf = null;
            chunk = null;
            rootParent = null;
            handle.recycle(this);
        }
    }

    /**
     * The strategy for how {@link AdaptivePoolingAllocator} should allocate chunk buffers.
     */
    interface ChunkAllocator {
        /**
         * Allocate a buffer for a chunk. This can be any kind of {@link AbstractByteBuf} implementation.
         * @param initialCapacity The initial capacity of the returned {@link AbstractByteBuf}.
         * @param maxCapacity The maximum capacity of the returned {@link AbstractByteBuf}.
         * @return The buffer that represents the chunk memory.
         */
        AbstractByteBuf allocate(int initialCapacity, int maxCapacity);
    }
}
