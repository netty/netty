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
import io.netty.util.CharsetUtil;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.IntConsumer;
import io.netty.util.NettyRuntime;
import io.netty.util.Recycler;
import io.netty.util.Recycler.EnhancedHandle;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.ConcurrentSkipListIntObjMultimap;
import io.netty.util.concurrent.ConcurrentSkipListIntObjMultimap.IntEntry;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.MpscAtomicIntegerArrayQueue;
import io.netty.util.concurrent.MpscIntQueue;
import io.netty.util.internal.MathUtil;
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
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.LongAdder;
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
    private static final int LOW_MEM_THRESHOLD = 512 * 1024 * 1024;
    private static final boolean IS_LOW_MEM = Runtime.getRuntime().maxMemory() <= LOW_MEM_THRESHOLD;

    /**
     * Whether the IS_LOW_MEM setting should disable thread-local magazines.
     * This can have fairly high performance overhead.
     */
    private static final boolean DISABLE_THREAD_LOCAL_MAGAZINES_ON_LOW_MEM = SystemPropertyUtil.getBoolean(
            "io.netty.allocator.disableThreadLocalMagazinesOnLowMemory", true);

    /**
     * The 128 KiB minimum chunk size is chosen to encourage the system allocator to delegate to mmap for chunk
     * allocations. For instance, glibc will do this.
     * This pushes any fragmentation from chunk size deviations off physical memory, onto virtual memory,
     * which is a much, much larger space. Chunks are also allocated in whole multiples of the minimum
     * chunk size, which itself is a whole multiple of popular page sizes like 4 KiB, 16 KiB, and 64 KiB.
     */
    static final int MIN_CHUNK_SIZE = 128 * 1024;
    private static final int EXPANSION_ATTEMPTS = 3;
    private static final int INITIAL_MAGAZINES = 1;
    private static final int RETIRE_CAPACITY = 256;
    private static final int MAX_STRIPES = IS_LOW_MEM ? 1 : NettyRuntime.availableProcessors() * 2;
    private static final int BUFS_PER_CHUNK = 8; // For large buffers, aim to have about this many buffers per chunk.

    /**
     * The maximum size of a pooled chunk, in bytes. Allocations bigger than this will never be pooled.
     * <p>
     * This number is 8 MiB, and is derived from the limitations of internal histograms.
     */
    private static final int MAX_CHUNK_SIZE = IS_LOW_MEM ?
            2 * 1024 * 1024 : // 2 MiB for systems with small heaps.
            8 * 1024 * 1024; // 8 MiB.
    private static final int MAX_POOLED_BUF_SIZE = MAX_CHUNK_SIZE / BUFS_PER_CHUNK;

    /**
     * The capacity if the chunk reuse queues, that allow chunks to be shared across magazines in a group.
     * The default size is twice {@link NettyRuntime#availableProcessors()},
     * same as the maximum number of magazines per magazine group.
     */
    private static final int CHUNK_REUSE_QUEUE = Math.max(2, SystemPropertyUtil.getInt(
            "io.netty.allocator.chunkReuseQueueCapacity", NettyRuntime.availableProcessors() * 2));

    /**
     * The capacity if the magazine local buffer queue. This queue just pools the outer ByteBuf instance and not
     * the actual memory and so helps to reduce GC pressure.
     */
    private static final int MAGAZINE_BUFFER_QUEUE_CAPACITY = SystemPropertyUtil.getInt(
            "io.netty.allocator.magazineBufferQueueCapacity", 1024);

    /**
     * The size classes are chosen based on the following observation:
     * <p>
     * Most allocations, particularly ones above 256 bytes, aim to be a power-of-2. However, many use cases, such
     * as framing protocols, are themselves operating or moving power-of-2 sized payloads, to which they add a
     * small amount of overhead, such as headers or checksums.
     * This means we seem to get a lot of mileage out of having both power-of-2 sizes, and power-of-2-plus-a-bit.
     * <p>
     * On the conflicting requirements of both having as few chunks as possible, and having as little wasted
     * memory within each chunk as possible, this seems to strike a surprisingly good balance for the use cases
     * tested so far.
     */
    private static final int[] SIZE_CLASSES = {
            32,
            64,
            128,
            256,
            512,
            640, // 512 + 128
            1024,
            1152, // 1024 + 128
            2048,
            2304, // 2048 + 256
            4096,
            4352, // 4096 + 256
            8192,
            8704, // 8192 + 512
            16384,
            16896, // 16384 + 512
    };

    private static final int SIZE_CLASSES_COUNT = SIZE_CLASSES.length;
    private static final byte[] SIZE_INDEXES = new byte[SIZE_CLASSES[SIZE_CLASSES_COUNT - 1] / 32 + 1];

    static {
        if (MAGAZINE_BUFFER_QUEUE_CAPACITY < 2) {
            throw new IllegalArgumentException("MAGAZINE_BUFFER_QUEUE_CAPACITY: " + MAGAZINE_BUFFER_QUEUE_CAPACITY
                    + " (expected: >= " + 2 + ')');
        }
        int lastIndex = 0;
        for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
            int sizeClass = SIZE_CLASSES[i];
            //noinspection ConstantValue
            assert (sizeClass & 5) == 0 : "Size class must be a multiple of 32";
            int sizeIndex = sizeIndexOf(sizeClass);
            Arrays.fill(SIZE_INDEXES, lastIndex + 1, sizeIndex + 1, (byte) i);
            lastIndex = sizeIndex;
        }
    }

    private final ChunkAllocator chunkAllocator;
    private final ChunkRegistry chunkRegistry;
    private final MagazineGroup[] sizeClassedMagazineGroups;
    private final MagazineGroup largeBufferMagazineGroup;
    private final FastThreadLocal<MagazineGroup[]> threadLocalGroup;

    AdaptivePoolingAllocator(ChunkAllocator chunkAllocator, final boolean useCacheForNonEventLoopThreads) {
        this.chunkAllocator = ObjectUtil.checkNotNull(chunkAllocator, "chunkAllocator");
        chunkRegistry = new ChunkRegistry();
        sizeClassedMagazineGroups = createMagazineGroupSizeClasses(this, false);
        largeBufferMagazineGroup = new MagazineGroup(
                this, chunkAllocator, new BuddyChunkManagementStrategy(), false);

        boolean disableThreadLocalGroups = IS_LOW_MEM && DISABLE_THREAD_LOCAL_MAGAZINES_ON_LOW_MEM;
        threadLocalGroup = disableThreadLocalGroups ? null : new FastThreadLocal<MagazineGroup[]>() {
            @Override
            protected MagazineGroup[] initialValue() {
                if (useCacheForNonEventLoopThreads || ThreadExecutorMap.currentExecutor() != null) {
                    return createMagazineGroupSizeClasses(AdaptivePoolingAllocator.this, true);
                }
                return null;
            }

            @Override
            protected void onRemoval(final MagazineGroup[] groups) throws Exception {
                if (groups != null) {
                    for (MagazineGroup group : groups) {
                        group.free();
                    }
                }
            }
        };
    }

    private static MagazineGroup[] createMagazineGroupSizeClasses(
            AdaptivePoolingAllocator allocator, boolean isThreadLocal) {
        MagazineGroup[] groups = new MagazineGroup[SIZE_CLASSES.length];
        for (int i = 0; i < SIZE_CLASSES.length; i++) {
            int segmentSize = SIZE_CLASSES[i];
            groups[i] = new MagazineGroup(allocator, allocator.chunkAllocator,
                    new SizeClassChunkManagementStrategy(segmentSize), isThreadLocal);
        }
        return groups;
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
     * The default implementation will create a bounded queue with a capacity of {@link #CHUNK_REUSE_QUEUE}.
     *
     * @return A new multi-producer, multi-consumer queue.
     */
    private static Queue<Chunk> createSharedChunkQueue() {
        return PlatformDependent.newFixedMpmcQueue(CHUNK_REUSE_QUEUE);
    }

    @Override
    public ByteBuf allocate(int size, int maxCapacity) {
        return allocate(size, maxCapacity, Thread.currentThread(), null);
    }

    private AdaptiveByteBuf allocate(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
        AdaptiveByteBuf allocated = null;
        if (size <= MAX_POOLED_BUF_SIZE) {
            final int index = sizeClassIndexOf(size);
            MagazineGroup[] magazineGroups;
            if (!FastThreadLocalThread.willCleanupFastThreadLocals(Thread.currentThread()) ||
                    IS_LOW_MEM ||
                    (magazineGroups = threadLocalGroup.get()) == null) {
                magazineGroups =  sizeClassedMagazineGroups;
            }
            if (index < magazineGroups.length) {
                allocated = magazineGroups[index].allocate(size, maxCapacity, currentThread, buf);
            } else if (!IS_LOW_MEM) {
                allocated = largeBufferMagazineGroup.allocate(size, maxCapacity, currentThread, buf);
            }
        }
        if (allocated == null) {
            allocated = allocateFallback(size, maxCapacity, currentThread, buf);
        }
        return allocated;
    }

    private static int sizeIndexOf(final int size) {
        // this is aligning the size to the next multiple of 32 and dividing by 32 to get the size index.
        return size + 31 >> 5;
    }

    static int sizeClassIndexOf(int size) {
        int sizeIndex = sizeIndexOf(size);
        if (sizeIndex < SIZE_INDEXES.length) {
            return SIZE_INDEXES[sizeIndex];
        }
        return SIZE_CLASSES_COUNT;
    }

    static int[] getSizeClasses() {
        return SIZE_CLASSES.clone();
    }

    private AdaptiveByteBuf allocateFallback(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
        // If we don't already have a buffer, obtain one from the most conveniently available magazine.
        Magazine magazine;
        if (buf != null) {
            Chunk chunk = buf.chunk;
            if (chunk == null || chunk == Magazine.MAGAZINE_FREED || (magazine = chunk.currentMagazine()) == null) {
                magazine = getFallbackMagazine(currentThread);
            }
        } else {
            magazine = getFallbackMagazine(currentThread);
            buf = magazine.newBuffer();
        }
        // Create a one-off chunk for this allocation.
        AbstractByteBuf innerChunk = chunkAllocator.allocate(size, maxCapacity);
        Chunk chunk = new Chunk(innerChunk, magazine);
        chunkRegistry.add(chunk);
        try {
            boolean success = chunk.readInitInto(buf, size, size, maxCapacity);
            assert success: "Failed to initialize ByteBuf with dedicated chunk";
        } finally {
            // As the chunk is an one-off we need to always call release explicitly as readInitInto(...)
            // will take care of retain once when successful. Once The AdaptiveByteBuf is released it will
            // completely release the Chunk and so the contained innerChunk.
            chunk.release();
        }
        return buf;
    }

    private Magazine getFallbackMagazine(Thread currentThread) {
        Magazine[] mags = largeBufferMagazineGroup.magazines;
        return mags[(int) currentThread.getId() & mags.length - 1];
    }

    /**
     * Allocate into the given buffer. Used by {@link AdaptiveByteBuf#capacity(int)}.
     */
    void reallocate(int size, int maxCapacity, AdaptiveByteBuf into) {
        AdaptiveByteBuf result = allocate(size, maxCapacity, Thread.currentThread(), into);
        assert result == into: "Re-allocation created separate buffer instance";
    }

    @Override
    public long usedMemory() {
        return chunkRegistry.totalCapacity();
    }

    // Ensure that we release all previous pooled resources when this object is finalized. This is needed as otherwise
    // we might end up with leaks. While these leaks are usually harmless in reality it would still at least be
    // very confusing for users.
    @SuppressWarnings({"FinalizeDeclaration", "deprecation"})
    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            free();
        }
    }

    private void free() {
        largeBufferMagazineGroup.free();
    }

    @SuppressJava6Requirement(reason = "Guarded by version check")
    private static final class MagazineGroup {
        private final AdaptivePoolingAllocator allocator;
        private final ChunkAllocator chunkAllocator;
        private final ChunkManagementStrategy chunkManagementStrategy;
        private final ChunkCache chunkCache;
        private final StampedLock magazineExpandLock;
        private final Magazine threadLocalMagazine;
        private Thread ownerThread;
        private volatile Magazine[] magazines;
        private volatile boolean freed;

        MagazineGroup(AdaptivePoolingAllocator allocator,
                      ChunkAllocator chunkAllocator,
                      ChunkManagementStrategy chunkManagementStrategy,
                      boolean isThreadLocal) {
            this.allocator = allocator;
            this.chunkAllocator = chunkAllocator;
            this.chunkManagementStrategy = chunkManagementStrategy;
            chunkCache = chunkManagementStrategy.createChunkCache(isThreadLocal);
            if (isThreadLocal) {
                ownerThread = Thread.currentThread();
                magazineExpandLock = null;
                threadLocalMagazine = new Magazine(this, false, chunkManagementStrategy.createController(this));
            } else {
                ownerThread = null;
                magazineExpandLock = new StampedLock();
                threadLocalMagazine = null;
                Magazine[] mags = new Magazine[INITIAL_MAGAZINES];
                for (int i = 0; i < mags.length; i++) {
                    mags[i] = new Magazine(this, true, chunkManagementStrategy.createController(this));
                }
                magazines = mags;
            }
        }

        public AdaptiveByteBuf allocate(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
            boolean reallocate = buf != null;

            // Path for thread-local allocation.
            Magazine tlMag = threadLocalMagazine;
            if (tlMag != null) {
                if (buf == null) {
                    buf = tlMag.newBuffer();
                }
                boolean allocated = tlMag.tryAllocate(size, maxCapacity, buf, reallocate);
                assert allocated : "Allocation of threadLocalMagazine must always succeed";
                return buf;
            }

            // Path for concurrent allocation.
            long threadId = currentThread.getId();
            Magazine[] mags;
            int expansions = 0;
            do {
                mags = magazines;
                int mask = mags.length - 1;
                int index = (int) (threadId & mask);
                for (int i = 0, m = mags.length << 1; i < m; i++) {
                    Magazine mag = mags[index + i & mask];
                    if (buf == null) {
                        buf = mag.newBuffer();
                    }
                    if (mag.tryAllocate(size, maxCapacity, buf, reallocate)) {
                        // Was able to allocate.
                        return buf;
                    }
                }
                expansions++;
            } while (expansions <= EXPANSION_ATTEMPTS && tryExpandMagazines(mags.length));

            // The magazines failed us; contention too high and we don't want to spend more effort expanding the array.
            if (!reallocate && buf != null) {
                buf.release(); // Release the previously claimed buffer before we return.
            }
            return null;
        }

        private boolean tryExpandMagazines(int currentLength) {
            if (currentLength >= MAX_STRIPES) {
                return true;
            }
            final Magazine[] mags;
            long writeLock = magazineExpandLock.tryWriteLock();
            if (writeLock != 0) {
                try {
                    mags = magazines;
                    if (mags.length >= MAX_STRIPES || mags.length > currentLength || freed) {
                        return true;
                    }
                    Magazine[] expanded = new Magazine[mags.length * 2];
                    for (int i = 0, l = expanded.length; i < l; i++) {
                        expanded[i] = new Magazine(this, true, chunkManagementStrategy.createController(this));
                    }
                    magazines = expanded;
                } finally {
                    magazineExpandLock.unlockWrite(writeLock);
                }
                for (Magazine magazine : mags) {
                    magazine.free();
                }
            }
            return true;
        }

        Chunk pollChunk(int size) {
            return chunkCache.pollChunk(size);
        }

        boolean offerChunk(Chunk chunk) {
            if (freed) {
                return false;
            }

            if (chunk.hasUnprocessedFreelistEntries()) {
                chunk.processFreelistEntries();
            }
            boolean isAdded = chunkCache.offerChunk(chunk);

            if (freed && isAdded) {
                // Help to free the reuse queue.
                freeChunkReuseQueue(ownerThread);
            }
            return isAdded;
        }

        private void free() {
            freed = true;
            Thread ownerThread = this.ownerThread;
            if (threadLocalMagazine != null) {
                this.ownerThread = null;
                threadLocalMagazine.free();
            } else {
                long stamp = magazineExpandLock.writeLock();
                try {
                    Magazine[] mags = magazines;
                    for (Magazine magazine : mags) {
                        magazine.free();
                    }
                } finally {
                    magazineExpandLock.unlockWrite(stamp);
                }
            }
            freeChunkReuseQueue(ownerThread);
        }

        private void freeChunkReuseQueue(Thread ownerThread) {
            Chunk chunk;
            while ((chunk = chunkCache.pollChunk(0)) != null) {
                if (ownerThread != null && chunk instanceof SizeClassedChunk) {
                    SizeClassedChunk threadLocalChunk = (SizeClassedChunk) chunk;
                    assert ownerThread == threadLocalChunk.ownerThread;
                    // no release segment can ever happen from the owner Thread since it's not running anymore
                    // This is required to let the ownerThread to be GC'ed despite there are AdaptiveByteBuf
                    // that reference some thread local chunk
                    threadLocalChunk.ownerThread = null;
                }
                chunk.release();
            }
        }
    }

    private interface ChunkCache {
        Chunk pollChunk(int size);
        boolean offerChunk(Chunk chunk);
    }

    private static final class ConcurrentQueueChunkCache implements ChunkCache {
        private final Queue<Chunk> queue;

        private ConcurrentQueueChunkCache() {
            queue = createSharedChunkQueue();
        }

        @Override
        public Chunk pollChunk(int size) {
            int attemps = queue.size();
            for (int i = 0; i < attemps; i++) {
                Chunk chunk = queue.poll();
                if (chunk == null) {
                    return null;
                }
                if (chunk.hasUnprocessedFreelistEntries()) {
                    chunk.processFreelistEntries();
                }
                if (chunk.remainingCapacity() >= size) {
                    return chunk;
                }
                queue.offer(chunk);
            }
            return null;
        }

        @Override
        public boolean offerChunk(Chunk chunk) {
            return queue.offer(chunk);
        }
    }

    private static final class ConcurrentSkipListChunkCache implements ChunkCache {
        private final ConcurrentSkipListIntObjMultimap<Chunk> chunks;

        private ConcurrentSkipListChunkCache() {
            chunks = new ConcurrentSkipListIntObjMultimap<Chunk>(-1);
        }

        @Override
        public Chunk pollChunk(int size) {
            if (chunks.isEmpty()) {
                return null;
            }
            IntEntry<Chunk> entry = chunks.pollCeilingEntry(size);
            if (entry != null) {
                Chunk chunk = entry.getValue();
                if (chunk.hasUnprocessedFreelistEntries()) {
                    chunk.processFreelistEntries();
                }
                return chunk;
            }

            Chunk bestChunk = null;
            int bestRemainingCapacity = 0;
            Iterator<IntEntry<Chunk>> itr = chunks.iterator();
            while (itr.hasNext()) {
                entry = itr.next();
                final Chunk chunk;
                if (entry != null && (chunk = entry.getValue()).hasUnprocessedFreelistEntries()) {
                    if (!chunks.remove(entry.getKey(), entry.getValue())) {
                        continue;
                    }
                    chunk.processFreelistEntries();
                    int remainingCapacity = chunk.remainingCapacity();
                    if (remainingCapacity >= size &&
                            (bestChunk == null || remainingCapacity > bestRemainingCapacity)) {
                        if (bestChunk != null) {
                            chunks.put(bestRemainingCapacity, bestChunk);
                        }
                        bestChunk = chunk;
                        bestRemainingCapacity = remainingCapacity;
                    } else {
                        chunks.put(remainingCapacity, chunk);
                    }
                }
            }

            return bestChunk;
        }

        @Override
        public boolean offerChunk(Chunk chunk) {
            chunks.put(chunk.remainingCapacity(), chunk);

            int size = chunks.size();
            while (size > CHUNK_REUSE_QUEUE) {
                // Deallocate the chunk with the fewest incoming references.
                int key = -1;
                Chunk toDeallocate = null;
                for (IntEntry<Chunk> entry : chunks) {
                    Chunk candidate = entry.getValue();
                    if (candidate != null) {
                        if (toDeallocate == null) {
                            toDeallocate = candidate;
                            key = entry.getKey();
                        } else {
                            int candidateRefCnt = candidate.refCnt();
                            int toDeallocateRefCnt = toDeallocate.refCnt();
                            if (candidateRefCnt < toDeallocateRefCnt ||
                                    candidateRefCnt == toDeallocateRefCnt &&
                                            candidate.capacity() < toDeallocate.capacity()) {
                                toDeallocate = candidate;
                                key = entry.getKey();
                            }
                        }
                    }
                }
                if (toDeallocate == null) {
                    break;
                }
                if (chunks.remove(key, toDeallocate)) {
                    toDeallocate.release();
                }
                size = chunks.size();
            }
            return true;
        }
    }

    private interface ChunkManagementStrategy {
        ChunkController createController(MagazineGroup group);

        ChunkCache createChunkCache(boolean isThreadLocal);
    }

    private interface ChunkController {
        /**
         * Compute the "fast max capacity" value for the buffer.
         */
        int computeBufferCapacity(int requestedSize, int maxCapacity, boolean isReallocation);

        /**
         * Allocate a new {@link Chunk} for the given {@link Magazine}.
         */
        Chunk newChunkAllocation(int promptingSize, Magazine magazine);
    }

    private static final class SizeClassChunkManagementStrategy implements ChunkManagementStrategy {
        // To amortize activation/deactivation of chunks, we should have a minimum number of segments per chunk.
        // We choose 32 because it seems neither too small nor too big.
        // For segments of 16 KiB, the chunks will be half a megabyte.
        private static final int MIN_SEGMENTS_PER_CHUNK = 32;
        private final int segmentSize;
        private final int chunkSize;

        private SizeClassChunkManagementStrategy(int segmentSize) {
            this.segmentSize = ObjectUtil.checkPositive(segmentSize, "segmentSize");
            chunkSize = Math.max(MIN_CHUNK_SIZE, segmentSize * MIN_SEGMENTS_PER_CHUNK);
        }

        @Override
        public ChunkController createController(MagazineGroup group) {
            return new SizeClassChunkController(group, segmentSize, chunkSize);
        }

        @Override
        public ChunkCache createChunkCache(boolean isThreadLocal) {
            return new ConcurrentQueueChunkCache();
        }
    }

    private static final class SizeClassChunkController implements ChunkController {

        private final ChunkAllocator chunkAllocator;
        private final int segmentSize;
        private final int chunkSize;
        private final ChunkRegistry chunkRegistry;

        private SizeClassChunkController(MagazineGroup group, int segmentSize, int chunkSize) {
            chunkAllocator = group.chunkAllocator;
            this.segmentSize = segmentSize;
            this.chunkSize = chunkSize;
            chunkRegistry = group.allocator.chunkRegistry;
        }

        private MpscIntQueue createEmptyFreeList() {
            return new MpscAtomicIntegerArrayQueue(chunkSize / segmentSize, SizeClassedChunk.FREE_LIST_EMPTY);
        }

        private MpscIntQueue createFreeList() {
            final int segmentsCount = chunkSize / segmentSize;
            final MpscIntQueue freeList = new MpscAtomicIntegerArrayQueue(
                    segmentsCount, SizeClassedChunk.FREE_LIST_EMPTY);
            int segmentOffset = 0;
            for (int i = 0; i < segmentsCount; i++) {
                freeList.offer(segmentOffset);
                segmentOffset += segmentSize;
            }
            return freeList;
        }

        private IntStack createLocalFreeList() {
            final int segmentsCount = chunkSize / segmentSize;
            int segmentOffset = chunkSize;
            int[] offsets = new int[segmentsCount];
            for (int i = 0; i < segmentsCount; i++) {
                segmentOffset -= segmentSize;
                offsets[i] = segmentOffset;
            }
            return new IntStack(offsets);
        }

        @Override
        public int computeBufferCapacity(
                int requestedSize, int maxCapacity, boolean isReallocation) {
            return Math.min(segmentSize, maxCapacity);
        }

        @Override
        public Chunk newChunkAllocation(int promptingSize, Magazine magazine) {
            AbstractByteBuf chunkBuffer = chunkAllocator.allocate(chunkSize, chunkSize);
            assert chunkBuffer.capacity() == chunkSize;
            SizeClassedChunk chunk = new SizeClassedChunk(chunkBuffer, magazine, this);
            chunkRegistry.add(chunk);
            return chunk;
        }
    }

    private static final class BuddyChunkManagementStrategy implements ChunkManagementStrategy {
        private final AtomicInteger maxChunkSize = new AtomicInteger();

        @Override
        public ChunkController createController(MagazineGroup group) {
            return new BuddyChunkController(group, maxChunkSize);
        }

        @Override
        public ChunkCache createChunkCache(boolean isThreadLocal) {
            return new ConcurrentSkipListChunkCache();
        }
    }

    private static final class BuddyChunkController implements ChunkController {
        private final ChunkAllocator chunkAllocator;
        private final ChunkRegistry chunkRegistry;
        private final AtomicInteger maxChunkSize;

        BuddyChunkController(MagazineGroup group, AtomicInteger maxChunkSize) {
            chunkAllocator = group.chunkAllocator;
            chunkRegistry = group.allocator.chunkRegistry;
            this.maxChunkSize = maxChunkSize;
        }

        @Override
        public int computeBufferCapacity(int requestedSize, int maxCapacity, boolean isReallocation) {
            return MathUtil.safeFindNextPositivePowerOfTwo(requestedSize);
        }

        @Override
        public Chunk newChunkAllocation(int promptingSize, Magazine magazine) {
            int maxChunkSize = this.maxChunkSize.get();
            int proposedChunkSize = MathUtil.safeFindNextPositivePowerOfTwo(BUFS_PER_CHUNK * promptingSize);
            int chunkSize = Math.min(MAX_CHUNK_SIZE, Math.max(maxChunkSize, proposedChunkSize));
            if (chunkSize > maxChunkSize) {
                // Update our stored max chunk size. It's fine that this is racy.
                this.maxChunkSize.set(chunkSize);
            }
            BuddyChunk chunk = new BuddyChunk(chunkAllocator.allocate(chunkSize, chunkSize), magazine);
            chunkRegistry.add(chunk);
            return chunk;
        }
    }

    @SuppressJava6Requirement(reason = "Guarded by version check")
    private static final class Magazine {
        private static final AtomicReferenceFieldUpdater<Magazine, Chunk> NEXT_IN_LINE;
        static {
            NEXT_IN_LINE = AtomicReferenceFieldUpdater.newUpdater(Magazine.class, Chunk.class, "nextInLine");
        }
        private static final Chunk MAGAZINE_FREED = new Chunk();

        private static final class AdaptiveRecycler extends Recycler<AdaptiveByteBuf> {

            private AdaptiveRecycler() {
            }

            private AdaptiveRecycler(int maxCapacity) {
                // doesn't use fast thread local, shared
                super(maxCapacity);
            }

            @Override
            protected AdaptiveByteBuf newObject(final Handle<AdaptiveByteBuf> handle) {
                return new AdaptiveByteBuf((EnhancedHandle<AdaptiveByteBuf>) handle);
            }

            public static AdaptiveRecycler threadLocal() {
                return new AdaptiveRecycler();
            }

            public static AdaptiveRecycler sharedWith(int maxCapacity) {
                return new AdaptiveRecycler(maxCapacity);
            }
        }

        private static final AdaptiveRecycler EVENT_LOOP_LOCAL_BUFFER_POOL = AdaptiveRecycler.threadLocal();

        private Chunk current;
        @SuppressWarnings("unused") // updated via NEXT_IN_LINE
        private volatile Chunk nextInLine;
        private final MagazineGroup group;
        private final ChunkController chunkController;
        private final StampedLock allocationLock;
        private final AdaptiveRecycler recycler;

        Magazine(MagazineGroup group, boolean shareable, ChunkController chunkController) {
            this.group = group;
            this.chunkController = chunkController;

            if (shareable) {
                // We only need the StampedLock if this Magazine will be shared across threads.
                allocationLock = new StampedLock();
                recycler = AdaptiveRecycler.sharedWith(MAGAZINE_BUFFER_QUEUE_CAPACITY);
            } else {
                allocationLock = null;
                recycler = null;
            }
        }

        public boolean tryAllocate(int size, int maxCapacity, AdaptiveByteBuf buf, boolean reallocate) {
            if (allocationLock == null) {
                // This magazine is not shared across threads, just allocate directly.
                return allocate(size, maxCapacity, buf, reallocate);
            }

            // Try to retrieve the lock and if successful allocate.
            long writeLock = allocationLock.tryWriteLock();
            if (writeLock != 0) {
                try {
                    return allocate(size, maxCapacity, buf, reallocate);
                } finally {
                    allocationLock.unlockWrite(writeLock);
                }
            }
            return allocateWithoutLock(size, maxCapacity, buf);
        }

        private boolean allocateWithoutLock(int size, int maxCapacity, AdaptiveByteBuf buf) {
            Chunk curr = NEXT_IN_LINE.getAndSet(this, null);
            if (curr == MAGAZINE_FREED) {
                // Allocation raced with a stripe-resize that freed this magazine.
                restoreMagazineFreed();
                return false;
            }
            if (curr == null) {
                curr = group.pollChunk(size);
                if (curr == null) {
                    return false;
                }
                curr.attachToMagazine(this);
            }
            boolean allocated = false;
            int remainingCapacity = curr.remainingCapacity();
            int startingCapacity = chunkController.computeBufferCapacity(
                    size, maxCapacity, true /* never update stats as we don't hold the magazine lock */);
            if (remainingCapacity >= size &&
                    curr.readInitInto(buf, size, Math.min(remainingCapacity, startingCapacity), maxCapacity)) {
                allocated = true;
                remainingCapacity = curr.remainingCapacity();
            }
            try {
                if (remainingCapacity >= RETIRE_CAPACITY) {
                    transferToNextInLineOrRelease(curr);
                    curr = null;
                }
            } finally {
                if (curr != null) {
                    curr.releaseFromMagazine();
                }
            }
            return allocated;
        }

        private boolean allocate(int size, int maxCapacity, AdaptiveByteBuf buf, boolean reallocate) {
            int startingCapacity = chunkController.computeBufferCapacity(size, maxCapacity, reallocate);
            Chunk curr = current;
            if (curr != null) {
                boolean success = curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                int remainingCapacity = curr.remainingCapacity();
                if (!success && remainingCapacity > 0) {
                    current = null;
                    transferToNextInLineOrRelease(curr);
                } else if (remainingCapacity == 0) {
                    current = null;
                    curr.releaseFromMagazine();
                }
                if (success) {
                    return true;
                }
            }

            assert current == null;
            // The fast-path for allocations did not work.
            //
            // Try to fetch the next "Magazine local" Chunk first, if this fails because we don't have a
            // next-in-line chunk available, we will poll our centralQueue.
            // If this fails as well we will just allocate a new Chunk.
            //
            // In any case we will store the Chunk as the current so it will be used again for the next allocation and
            // thus be "reserved" by this Magazine for exclusive usage.
            curr = NEXT_IN_LINE.getAndSet(this, null);
            if (curr != null) {
                if (curr == MAGAZINE_FREED) {
                    // Allocation raced with a stripe-resize that freed this magazine.
                    restoreMagazineFreed();
                    return false;
                }

                int remainingCapacity = curr.remainingCapacity();
                if (remainingCapacity > startingCapacity &&
                        curr.readInitInto(buf, size, startingCapacity, maxCapacity)) {
                    // We have a Chunk that has some space left.
                    current = curr;
                    return true;
                }

                try {
                    if (remainingCapacity >= size) {
                        // At this point we know that this will be the last time curr will be used, so directly set it
                        // to null and release it once we are done.
                        return curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                    }
                } finally {
                    // Release in a finally block so even if readInitInto(...) would throw we would still correctly
                    // release the current chunk before null it out.
                    curr.releaseFromMagazine();
                }
            }

            // Now try to poll from the central queue first
            curr = group.pollChunk(size);
            if (curr == null) {
                curr = chunkController.newChunkAllocation(size, this);
            } else {
                curr.attachToMagazine(this);

                int remainingCapacity = curr.remainingCapacity();
                if (remainingCapacity == 0 || remainingCapacity < size) {
                    // Check if we either retain the chunk in the nextInLine cache or releasing it.
                    if (remainingCapacity < RETIRE_CAPACITY) {
                        curr.releaseFromMagazine();
                    } else {
                        // See if it makes sense to transfer the Chunk to the nextInLine cache for later usage.
                        // This method will release curr if this is not the case
                        transferToNextInLineOrRelease(curr);
                    }
                    curr = chunkController.newChunkAllocation(size, this);
                }
            }

            current = curr;
            boolean success;
            try {
                int remainingCapacity = curr.remainingCapacity();
                assert remainingCapacity >= size;
                if (remainingCapacity > startingCapacity) {
                    success = curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                    curr = null;
                } else {
                    success = curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                }
            } finally {
                if (curr != null) {
                    // Release in a finally block so even if readInitInto(...) would throw we would still correctly
                    // release the current chunk before null it out.
                    curr.releaseFromMagazine();
                    current = null;
                }
            }
            return success;
        }

        private void restoreMagazineFreed() {
            Chunk next = NEXT_IN_LINE.getAndSet(this, MAGAZINE_FREED);
            if (next != null && next != MAGAZINE_FREED) {
                // A chunk snuck in through a race. Release it after restoring MAGAZINE_FREED state.
                next.releaseFromMagazine();
            }
        }

        private void transferToNextInLineOrRelease(Chunk chunk) {
            if (NEXT_IN_LINE.compareAndSet(this, null, chunk)) {
                return;
            }

            Chunk nextChunk = NEXT_IN_LINE.get(this);
            if (nextChunk != null && nextChunk != MAGAZINE_FREED
                    && chunk.remainingCapacity() > nextChunk.remainingCapacity()) {
                if (NEXT_IN_LINE.compareAndSet(this, nextChunk, chunk)) {
                    nextChunk.releaseFromMagazine();
                    return;
                }
            }
            // Next-in-line is occupied. We don't try to add it to the central queue yet as it might still be used
            // by some buffers and so is attached to a Magazine.
            // Once a Chunk is completely released by Chunk.release() it will try to move itself to the queue
            // as last resort.
            chunk.releaseFromMagazine();
        }

        void free() {
            // Release the current Chunk and the next that was stored for later usage.
            restoreMagazineFreed();
            long stamp = allocationLock != null ? allocationLock.writeLock() : 0;
            try {
                if (current != null) {
                    current.releaseFromMagazine();
                    current = null;
                }
            } finally {
                if (allocationLock != null) {
                    allocationLock.unlockWrite(stamp);
                }
            }
        }

        public AdaptiveByteBuf newBuffer() {
            AdaptiveRecycler recycler = this.recycler;
            AdaptiveByteBuf buf = recycler == null? EVENT_LOOP_LOCAL_BUFFER_POOL.get() : recycler.get();
            buf.resetRefCnt();
            buf.discardMarks();
            return buf;
        }

        boolean offerToQueue(Chunk chunk) {
            return group.offerChunk(chunk);
        }
    }

    @SuppressJava6Requirement(reason = "Guarded by version check")
    private static final class ChunkRegistry {
        private final LongAdder totalCapacity = new LongAdder();

        public long totalCapacity() {
            return totalCapacity.sum();
        }

        public void add(Chunk chunk) {
            totalCapacity.add(chunk.capacity());
        }

        public void remove(Chunk chunk) {
            totalCapacity.add(-chunk.capacity());
        }
    }

    private static class Chunk implements ReferenceCounted {
        private static final long REFCNT_FIELD_OFFSET =
                ReferenceCountUpdater.getUnsafeOffset(Chunk.class, "refCnt");
        private static final AtomicIntegerFieldUpdater<Chunk> AIF_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(Chunk.class, "refCnt");

        protected final AbstractByteBuf delegate;
        protected Magazine magazine;
        private final AdaptivePoolingAllocator allocator;
        private final int capacity;
        protected int allocatedBytes;

        private static final ReferenceCountUpdater<Chunk> updater =
                new ReferenceCountUpdater<Chunk>() {
                    @Override
                    protected AtomicIntegerFieldUpdater<Chunk> updater() {
                        return AIF_UPDATER;
                    }
                    @Override
                    protected long unsafeOffset() {
                        // on native image, REFCNT_FIELD_OFFSET can be recomputed even with Unsafe unavailable, so we
                        // need to guard here
                        return PlatformDependent.hasUnsafe() ? REFCNT_FIELD_OFFSET : -1;
                    }
                };

        // Value might not equal "real" reference count, all access should be via the updater
        @SuppressWarnings({"unused", "FieldMayBeFinal"})
        private volatile int refCnt;

        Chunk() {
            // Constructor only used by the MAGAZINE_FREED sentinel.
            delegate = null;
            magazine = null;
            allocator = null;
            capacity = 0;
        }

        Chunk(AbstractByteBuf delegate, Magazine magazine) {
            this.delegate = delegate;
            capacity = delegate.capacity();
            updater.setInitialValue(this);
            attachToMagazine(magazine);

            // We need the top-level allocator so ByteBuf.capacity(int) can call reallocate()
            allocator = magazine.group.allocator;
        }

        Magazine currentMagazine()  {
            return magazine;
        }

        void detachFromMagazine() {
            if (magazine != null) {
                magazine = null;
            }
        }

        void attachToMagazine(Magazine magazine) {
            assert this.magazine == null;
            this.magazine = magazine;
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

        /**
         * Called when a magazine is done using this chunk, probably because it was emptied.
         */
        boolean releaseFromMagazine() {
            // Chunks can be reused before they become empty.
            // We can therefor put them in the shared queue as soon as the magazine is done with this chunk.
            Magazine mag = magazine;
            detachFromMagazine();
            if (!mag.offerToQueue(this)) {
                return release();
            }
            return false;
        }

        /**
         * Called when a ByteBuf is done using its allocation in this chunk.
         */
        void releaseSegment(int ignoredSegmentId, int size) {
            release();
        }

        protected void deallocate() {
            allocator.chunkRegistry.remove(this);
            delegate.release();
        }

        public boolean readInitInto(AdaptiveByteBuf buf, int size, int startingCapacity, int maxCapacity) {
            int startIndex = allocatedBytes;
            allocatedBytes = startIndex + startingCapacity;
            Chunk chunk = this;
            chunk.retain();
            try {
                buf.init(delegate, chunk, 0, 0, startIndex, size, startingCapacity, maxCapacity);
                chunk = null;
            } finally {
                if (chunk != null) {
                    // If chunk is not null we know that buf.init(...) failed and so we need to manually release
                    // the chunk again as we retained it before calling buf.init(...). Beside this we also need to
                    // restore the old allocatedBytes value.
                    allocatedBytes = startIndex;
                    chunk.release();
                }
            }
            return true;
        }

        public int remainingCapacity() {
            return capacity - allocatedBytes;
        }

        public boolean hasUnprocessedFreelistEntries() {
            return false;
        }

        public void processFreelistEntries() {
        }

        public int capacity() {
            return capacity;
        }
    }

    private static final class IntStack {

        private final int[] stack;
        private int top;

        IntStack(int[] initialValues) {
            stack = initialValues;
            top = initialValues.length - 1;
        }

        public boolean isEmpty() {
            return top == -1;
        }

        public int pop() {
            final int last = stack[top];
            top--;
            return last;
        }

        public void push(int value) {
            stack[top + 1] = value;
            top++;
        }

        public int size() {
            return top + 1;
        }
    }

    private static final class SizeClassedChunk extends Chunk {
        private static final int FREE_LIST_EMPTY = -1;
        private final int segmentSize;
        private final MpscIntQueue externalFreeList;
        private final IntStack localFreeList;
        private Thread ownerThread;

        SizeClassedChunk(AbstractByteBuf delegate, Magazine magazine,
                         SizeClassChunkController controller) {
            super(delegate, magazine);
            segmentSize = controller.segmentSize;
            ownerThread = magazine.group.ownerThread;
            if (ownerThread == null) {
                externalFreeList = controller.createFreeList();
                localFreeList = null;
            } else {
                externalFreeList = controller.createEmptyFreeList();
                localFreeList = controller.createLocalFreeList();
            }
        }

        @Override
        public boolean readInitInto(AdaptiveByteBuf buf, int size, int startingCapacity, int maxCapacity) {
            final int startIndex = nextAvailableSegmentOffset();
            if (startIndex == FREE_LIST_EMPTY) {
                return false;
            }
            allocatedBytes += segmentSize;
            Chunk chunk = this;
            chunk.retain();
            try {
                buf.init(delegate, chunk, 0, 0, startIndex, size, startingCapacity, maxCapacity);
                chunk = null;
            } finally {
                if (chunk != null) {
                    // If chunk is not null we know that buf.init(...) failed and so we need to manually release
                    // the chunk again as we retained it before calling buf.init(...). Beside this we also need to
                    // restore the old allocatedBytes value.
                    allocatedBytes -= segmentSize;
                    chunk.releaseSegment(startIndex, startingCapacity);
                }
            }
            return true;
        }

        private int nextAvailableSegmentOffset() {
            final int startIndex;
            IntStack localFreeList = this.localFreeList;
            if (localFreeList != null) {
                assert Thread.currentThread() == ownerThread;
                if (localFreeList.isEmpty()) {
                    startIndex = externalFreeList.poll();
                } else {
                    startIndex = localFreeList.pop();
                }
            } else {
                startIndex = externalFreeList.poll();
            }
            return startIndex;
        }

        private int remainingCapacityOnFreeList() {
            final int segmentSize = this.segmentSize;
            int remainingCapacity = externalFreeList.size() * segmentSize;
            IntStack localFreeList = this.localFreeList;
            if (localFreeList != null) {
                assert Thread.currentThread() == ownerThread;
                remainingCapacity += localFreeList.size() * segmentSize;
            }
            return remainingCapacity;
        }

        @Override
        public int remainingCapacity() {
            int remainingCapacity = super.remainingCapacity();
            if (remainingCapacity > segmentSize) {
                return remainingCapacity;
            }
            int updatedRemainingCapacity = remainingCapacityOnFreeList();
            if (updatedRemainingCapacity == remainingCapacity) {
                return remainingCapacity;
            }
            // update allocatedBytes based on what's available in the free list
            allocatedBytes = capacity() - updatedRemainingCapacity;
            return updatedRemainingCapacity;
        }

        private void releaseSegmentOffsetIntoFreeList(int startIndex) {
            IntStack localFreeList = this.localFreeList;
            if (localFreeList != null && Thread.currentThread() == ownerThread) {
                localFreeList.push(startIndex);
            } else {
                boolean segmentReturned = externalFreeList.offer(startIndex);
                assert segmentReturned : "Unable to return segment " + startIndex + " to free list";
            }
        }

        @Override
        void releaseSegment(int startIndex, int size) {
            release();
            releaseSegmentOffsetIntoFreeList(startIndex);
        }
    }

    private static final class BuddyChunk extends Chunk implements IntConsumer {
        private static final int MIN_BUDDY_SIZE = 32768;
        private static final byte IS_CLAIMED = (byte) (1 << 7);
        private static final byte HAS_CLAIMED_CHILDREN = 1 << 6;
        private static final byte SHIFT_MASK = ~(IS_CLAIMED | HAS_CLAIMED_CHILDREN);
        private static final int PACK_OFFSET_MASK = 0xFFFF;
        private static final int PACK_SIZE_SHIFT = Integer.SIZE - Integer.numberOfLeadingZeros(PACK_OFFSET_MASK);

        private final MpscIntQueue freeList;
        // The bits of each buddy: [1: is claimed][1: has claimed children][30: MIN_BUDDY_SIZE shift to get size]
        private final byte[] buddies;
        private final int freeListCapacity;

        BuddyChunk(AbstractByteBuf delegate, Magazine magazine) {
            super(delegate, magazine);
            int capacity = delegate.capacity();
            int capFactor = capacity / MIN_BUDDY_SIZE;
            int tree = (capFactor << 1) - 1;
            int maxShift = Integer.numberOfTrailingZeros(capFactor);
            assert maxShift <= 30; // The top 2 bits are used for marking.
            freeListCapacity = tree >> 1; // At most half of tree (all leaf nodes) can be freed.
            freeList = new MpscAtomicIntegerArrayQueue(freeListCapacity, -1);
            buddies = new byte[1 + tree];

            // Generate the buddies entries.
            int index = 1;
            int runLength = 1;
            int currentRun = 0;
            while (maxShift > 0) {
                buddies[index++] = (byte) maxShift;
                if (++currentRun == runLength) {
                    currentRun = 0;
                    runLength <<= 1;
                    maxShift--;
                }
            }
        }

        @Override
        public boolean readInitInto(AdaptiveByteBuf buf, int size, int startingCapacity, int maxCapacity) {
            if (!freeList.isEmpty()) {
                freeList.drain(freeListCapacity, this);
            }
            int startIndex = chooseFirstFreeBuddy(1, startingCapacity, 0);
            if (startIndex == -1) {
                return false;
            }
            Chunk chunk = this;
            chunk.retain();
            try {
                buf.init(delegate, this, 0, 0, startIndex, size, startingCapacity, maxCapacity);
                allocatedBytes += startingCapacity;
                chunk = null;
            } finally {
                if (chunk != null) {
                    // If chunk is not null we know that buf.init(...) failed and so we need to manually release
                    // the chunk again as we retained it before calling buf.init(...).
                    chunk.release();
                }
            }
            return true;
        }

        @Override
        public void accept(int packed) {
            // Called by allocating thread when draining freeList.
            int size = MIN_BUDDY_SIZE << (packed >> PACK_SIZE_SHIFT);
            int offset = (packed & PACK_OFFSET_MASK) * MIN_BUDDY_SIZE;
            unreserveMatchingBuddy(1, size, offset, 0);
            allocatedBytes -= size;
        }

        @Override
        void releaseSegment(int startingIndex, int size) {
            int packedOffset = startingIndex / MIN_BUDDY_SIZE;
            int packedSize = Integer.numberOfTrailingZeros(size / MIN_BUDDY_SIZE) << PACK_SIZE_SHIFT;
            int packed = packedOffset | packedSize;
            freeList.offer(packed);
            release();
        }

        @Override
        public int remainingCapacity() {
            if (!freeList.isEmpty()) {
                freeList.drain(freeListCapacity, this);
            }
            return super.remainingCapacity();
        }

        @Override
        public boolean hasUnprocessedFreelistEntries() {
            return !freeList.isEmpty();
        }

        @Override
        public void processFreelistEntries() {
            freeList.drain(freeListCapacity, this);
        }

        /**
         * Claim a suitable buddy and return its start offset into the delegate chunk, or return -1 if nothing claimed.
         */
        private int chooseFirstFreeBuddy(int index, int size, int currOffset) {
            byte[] buddies = this.buddies;
            while (index < buddies.length) {
                byte buddy = buddies[index];
                int currValue = MIN_BUDDY_SIZE << (buddy & SHIFT_MASK);
                if (currValue < size || (buddy & IS_CLAIMED) == IS_CLAIMED) {
                    return -1;
                }
                if (currValue == size && (buddy & HAS_CLAIMED_CHILDREN) == 0) {
                    buddies[index] |= IS_CLAIMED;
                    return currOffset;
                }
                int found = chooseFirstFreeBuddy(index << 1, size, currOffset);
                if (found != -1) {
                    buddies[index] |= HAS_CLAIMED_CHILDREN;
                    return found;
                }
                index = (index << 1) + 1;
                currOffset += currValue >> 1; // Bump offset to skip first half of this layer.
            }
            return -1;
        }

        /**
         * Un-reserve the matching buddy and return whether there are any other child or sibling reservations.
         */
        private boolean unreserveMatchingBuddy(int index, int size, int offset, int currOffset) {
            byte[] buddies = this.buddies;
            if (buddies.length <= index) {
                return false;
            }
            byte buddy = buddies[index];
            int currSize = MIN_BUDDY_SIZE << (buddy & SHIFT_MASK);

            if (currSize == size) {
                // We're at the right size level.
                if (currOffset == offset) {
                    buddies[index] &= SHIFT_MASK;
                    return false;
                }
                throw new IllegalStateException("The intended segment was not found at index " +
                        index + ", for size " + size + " and offset " + offset);
            }

            // We're at a parent size level. Use the target offset to guide our drill-down path.
            boolean claims;
            int siblingIndex;
            if (offset < currOffset + (currSize >> 1)) {
                // Must be down the left path.
                claims = unreserveMatchingBuddy(index << 1, size, offset, currOffset);
                siblingIndex = (index << 1) + 1;
            } else {
                // Must be down the rigth path.
                claims = unreserveMatchingBuddy((index << 1) + 1, size, offset, currOffset + (currSize >> 1));
                siblingIndex = index << 1;
            }
            if (!claims) {
                // No other claims down the path we took. Check if the sibling has claims.
                byte sibling = buddies[siblingIndex];
                if ((sibling & SHIFT_MASK) == sibling) {
                    // No claims in the sibling. We can clear this level as well.
                    buddies[index] &= SHIFT_MASK;
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            int capacity = delegate.capacity();
            int remaining = capacity - allocatedBytes;
            return "BuddyChunk[capacity: " + capacity +
                    ", remaining: " + remaining +
                    ", free list: " + freeList.size() + ']';
        }
    }

    static final class AdaptiveByteBuf extends AbstractReferenceCountedByteBuf {

        private final EnhancedHandle<AdaptiveByteBuf> handle;

        // this both act as adjustment and the start index for a free list segment allocation
        private int startIndex;
        private AbstractByteBuf rootParent;
        Chunk chunk;
        private int length;
        private int maxFastCapacity;
        private ByteBuffer tmpNioBuf;
        private boolean hasArray;
        private boolean hasMemoryAddress;

        AdaptiveByteBuf(EnhancedHandle<AdaptiveByteBuf> recyclerHandle) {
            super(0);
            handle = ObjectUtil.checkNotNull(recyclerHandle, "recyclerHandle");
        }

        void init(AbstractByteBuf unwrapped, Chunk wrapped, int readerIndex, int writerIndex,
                  int startIndex, int size, int capacity, int maxCapacity) {
            this.startIndex = startIndex;
            chunk = wrapped;
            length = size;
            maxFastCapacity = capacity;
            maxCapacity(maxCapacity);
            setIndex0(readerIndex, writerIndex);
            hasArray = unwrapped.hasArray();
            hasMemoryAddress = unwrapped.hasMemoryAddress();
            rootParent = unwrapped;
            tmpNioBuf = null;
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
        public int maxFastWritableBytes() {
            return Math.min(maxFastCapacity, maxCapacity()) - writerIndex;
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            if (length <= newCapacity && newCapacity <= maxFastCapacity) {
                ensureAccessible();
                length = newCapacity;
                return this;
            }
            checkNewCapacity(newCapacity);
            if (newCapacity < capacity()) {
                length = newCapacity;
                trimIndicesToCapacity(newCapacity);
                return this;
            }

            // Reallocation required.
            Chunk chunk = this.chunk;
            AdaptivePoolingAllocator allocator = chunk.allocator;
            int readerIndex = this.readerIndex;
            int writerIndex = this.writerIndex;
            int baseOldRootIndex = startIndex;
            int oldLength = length;
            int oldCapacity = maxFastCapacity;
            AbstractByteBuf oldRoot = rootParent();
            allocator.reallocate(newCapacity, maxCapacity(), this);
            oldRoot.getBytes(baseOldRootIndex, this, 0, oldLength);
            chunk.releaseSegment(baseOldRootIndex, oldCapacity);
            assert oldCapacity < maxFastCapacity && newCapacity <= maxFastCapacity:
                    "Capacity increase failed";
            this.readerIndex = readerIndex;
            this.writerIndex = writerIndex;
            return this;
        }

        @Override
        public ByteBufAllocator alloc() {
            return rootParent().alloc();
        }

        @SuppressWarnings("deprecation")
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
            return rootParent().memoryAddress() + startIndex;
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
            if (tmpNioBuf == null) {
                tmpNioBuf = rootParent().nioBuffer(startIndex, maxFastCapacity);
            }
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
            ByteBuffer tmp = (ByteBuffer) internalNioBuffer().clear().position(index);
            tmp.put(src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            checkIndex(index, length);
            ByteBuffer tmp = (ByteBuffer) internalNioBuffer().clear().position(index);
            tmp.put(src.nioBuffer(srcIndex, length));
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            checkIndex(index, src.remaining());
            ByteBuffer tmp = (ByteBuffer) internalNioBuffer().clear().position(index);
            tmp.put(src);
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
            ByteBuffer buf = internalNioBuffer().duplicate();
            buf.clear().position(index).limit(index + length);
            return out.write(buf);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length)
                throws IOException {
            ByteBuffer buf = internalNioBuffer().duplicate();
            buf.clear().position(index).limit(index + length);
            return out.write(buf, position);
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
                return in.read(internalNioBuffer(index, length));
            } catch (ClosedChannelException ignored) {
                return -1;
            }
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length)
                throws IOException {
            try {
                return in.read(internalNioBuffer(index, length), position);
            } catch (ClosedChannelException ignored) {
                return -1;
            }
        }

        @Override
        public int setCharSequence(int index, CharSequence sequence, Charset charset) {
            return setCharSequence0(index, sequence, charset, false);
        }

        private int setCharSequence0(int index, CharSequence sequence, Charset charset, boolean expand) {
            if (charset.equals(CharsetUtil.UTF_8)) {
                int length = ByteBufUtil.utf8MaxBytes(sequence);
                if (expand) {
                    ensureWritable0(length);
                    checkIndex0(index, length);
                } else {
                    checkIndex(index, length);
                }
                return ByteBufUtil.writeUtf8(this, index, length, sequence, sequence.length());
            }
            if (charset.equals(CharsetUtil.US_ASCII) || charset.equals(CharsetUtil.ISO_8859_1)) {
                int length = sequence.length();
                if (expand) {
                    ensureWritable0(length);
                    checkIndex0(index, length);
                } else {
                    checkIndex(index, length);
                }
                return ByteBufUtil.writeAscii(this, index, sequence, length);
            }
            byte[] bytes = sequence.toString().getBytes(charset);
            if (expand) {
                ensureWritable0(bytes.length);
                // setBytes(...) will take care of checking the indices.
            }
            setBytes(index, bytes);
            return bytes.length;
        }

        @Override
        public int writeCharSequence(CharSequence sequence, Charset charset) {
            int written = setCharSequence0(writerIndex, sequence, charset, true);
            writerIndex += written;
            return written;
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

        @Override
        public ByteBuf setZero(int index, int length) {
            checkIndex(index, length);
            rootParent().setZero(idx(index), length);
            return this;
        }

        @Override
        public ByteBuf writeZero(int length) {
            ensureWritable(length);
            rootParent().setZero(idx(writerIndex), length);
            writerIndex += length;
            return this;
        }

        private int forEachResult(int ret) {
            if (ret < startIndex) {
                return -1;
            }
            return ret - startIndex;
        }

        @Override
        public boolean isContiguous() {
            return rootParent().isContiguous();
        }

        private int idx(int index) {
            return index + startIndex;
        }

        @Override
        protected void deallocate() {
            if (chunk != null) {
                chunk.releaseSegment(startIndex, maxFastCapacity);
            }
            tmpNioBuf = null;
            chunk = null;
            rootParent = null;
            handle.unguardedRecycle(this);
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
