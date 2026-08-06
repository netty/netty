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
import io.netty.util.NettyRuntime;
import io.netty.util.Recycler;
import io.netty.util.Recycler.EnhancedHandle;
import io.netty.util.concurrent.ConcurrentSkipListIntObjMultimap;
import io.netty.util.concurrent.ConcurrentSkipListIntObjMultimap.IntEntry;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.MpscIntQueue;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.RefCnt;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntConsumer;

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
 * Since magazines are "relatively thread-local", the allocator has a chunk cache that allows excess chunks from any
 * magazine to be shared with other magazines.
 */
@UnstableApi
final class AdaptivePoolingAllocator {
    private static final int LOW_MEM_THRESHOLD = 512 * 1024 * 1024;
    private static final boolean IS_LOW_MEM = SystemPropertyUtil.getBoolean(
            "io.netty.allocator.lowMemory",
            Runtime.getRuntime().maxMemory() <= LOW_MEM_THRESHOLD);

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
    private static final AtomicIntegerFieldUpdater<AdaptivePoolingAllocator> STRIPE_SCAN_LENGTH =
            AtomicIntegerFieldUpdater.newUpdater(AdaptivePoolingAllocator.class, "stripeScanLength");
    private static final int EXPANSION_ATTEMPTS = 3;
    private static final int MAX_STRIPES = IS_LOW_MEM ? 1 :
            MathUtil.safeFindNextPositivePowerOfTwo(NettyRuntime.availableProcessors() * 2);
    private static final int INITIAL_MAGAZINES = 1;
    private static final int RETIRE_CAPACITY = 256;
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
     * The capacity of the chunk reuse queues, that allow chunks to be shared across magazines in a stripe.
     * The default size is twice {@link NettyRuntime#availableProcessors()}.
     */
    static final int CHUNK_REUSE_QUEUE = Math.max(2, SystemPropertyUtil.getInt(
            "io.netty.allocator.chunkReuseQueueCapacity", NettyRuntime.availableProcessors() * 2));

    static final long CHUNK_PURGE_POLLS_THREAD_LOCAL = Math.max(1, SystemPropertyUtil.getLong(
            "io.netty.allocator.chunkPurgePollsThreadLocal", 16L));

    static final int CHUNK_PURGE_THRESHOLD = Math.max(1, SystemPropertyUtil.getInt(
            "io.netty.allocator.chunkPurgeThreshold", 3));

    /**
     * Per-size-class upper bound (in bytes) on the thread-local chunk cache.
     */
    static final int THREAD_LOCAL_CACHE_MAX_BYTES = Math.max(1, SystemPropertyUtil.getInt(
            "io.netty.allocator.threadLocalChunkCacheMaxBytes", 8 * 1024 * 1024));

    /**
     * Per-size-class lower bound (in bytes) on the thread-local chunk cache.
     * Clamped to {@link #THREAD_LOCAL_CACHE_MAX_BYTES} if the configured value exceeds it.
     */
    static final int THREAD_LOCAL_CACHE_MIN_BYTES = Math.min(THREAD_LOCAL_CACHE_MAX_BYTES,
            Math.max(1, SystemPropertyUtil.getInt(
                    "io.netty.allocator.threadLocalChunkCacheMinBytes",
                    THREAD_LOCAL_CACHE_MAX_BYTES / 2)));

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

    private static final byte[] SIZE_CLASS_TO_CHUNK_POOL; // sizeClassIndex -> poolIndex
    private static final int CHUNK_POOL_COUNT;           // number of distinct pools
    private static final int[] CHUNK_SIZES;              // chunkSize per pool index

    static {
        if (MAGAZINE_BUFFER_QUEUE_CAPACITY < 2) {
            throw new IllegalArgumentException("MAGAZINE_BUFFER_QUEUE_CAPACITY: " + MAGAZINE_BUFFER_QUEUE_CAPACITY
                    + " (expected: >= " + 2 + ')');
        }
        int lastIndex = 0;
        for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
            int sizeClass = SIZE_CLASSES[i];
            //noinspection ConstantValue
            assert (sizeClass & 31) == 0 : "Size class must be a multiple of 32";
            int sizeIndex = sizeIndexOf(sizeClass);
            Arrays.fill(SIZE_INDEXES, lastIndex + 1, sizeIndex + 1, (byte) i);
            lastIndex = sizeIndex;
        }

        // Precompute per-chunkSize pool mapping for O(1) recycled chunk routing.
        // Each size class maps to a chunkSize = max(MIN_CHUNK_SIZE, segmentSize * 32).
        // Multiple small size classes share the same chunkSize (MIN_CHUNK_SIZE),
        // while larger ones get their own pool.
        int[] chunkSizesTemp = new int[SIZE_CLASSES_COUNT];
        byte[] mappingTemp = new byte[SIZE_CLASSES_COUNT];
        int poolCount = 0;
        for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
            int chunkSize = Math.max(MIN_CHUNK_SIZE, SIZE_CLASSES[i] * 32);
            if (poolCount == 0 || chunkSizesTemp[poolCount - 1] != chunkSize) {
                chunkSizesTemp[poolCount] = chunkSize;
                poolCount++;
            }
            mappingTemp[i] = (byte) (poolCount - 1);
        }
        CHUNK_POOL_COUNT = poolCount;
        CHUNK_SIZES = Arrays.copyOf(chunkSizesTemp, poolCount);
        SIZE_CLASS_TO_CHUNK_POOL = mappingTemp;
    }

    private final ChunkAllocator chunkAllocator;
    private final ChunkRegistry chunkRegistry;
    private final SizeClassChunkManagementStrategy[] sizeClassStrategies;
    private final StripedHeap[] stripedHeaps;
    private volatile int stripeScanLength;
    private final BuddyChunkManagementStrategy buddyStrategy;
    private final ChunkCache sharedBuddyCache;
    private final Magazine.AdaptiveRecycler fallbackRecycler;
    private final FastThreadLocal<ThreadLocalSizeClassHeap> threadLocalSizeClassHeap;

    AdaptivePoolingAllocator(ChunkAllocator chunkAllocator, boolean useCacheForNonEventLoopThreads) {
        this.chunkAllocator = ObjectUtil.checkNotNull(chunkAllocator, "chunkAllocator");
        chunkRegistry = new ChunkRegistry();
        sizeClassStrategies = new SizeClassChunkManagementStrategy[SIZE_CLASSES.length];
        for (int i = 0; i < SIZE_CLASSES.length; i++) {
            sizeClassStrategies[i] = new SizeClassChunkManagementStrategy(SIZE_CLASSES[i]);
        }
        stripedHeaps = new StripedHeap[MAX_STRIPES];
        for (int i = 0; i < MAX_STRIPES; i++) {
            stripedHeaps[i] = new StripedHeap();
        }
        stripeScanLength = INITIAL_MAGAZINES;
        buddyStrategy = new BuddyChunkManagementStrategy();
        sharedBuddyCache = buddyStrategy.createChunkCache();
        fallbackRecycler = Magazine.AdaptiveRecycler.sharedWith(MAGAZINE_BUFFER_QUEUE_CAPACITY);

        boolean disableThreadLocalGroups = IS_LOW_MEM && DISABLE_THREAD_LOCAL_MAGAZINES_ON_LOW_MEM;
        threadLocalSizeClassHeap = disableThreadLocalGroups ? null : new FastThreadLocal<ThreadLocalSizeClassHeap>() {
            @Override
            protected ThreadLocalSizeClassHeap initialValue() {
                if (useCacheForNonEventLoopThreads || ThreadExecutorMap.currentExecutor() != null) {
                    return new ThreadLocalSizeClassHeap(AdaptivePoolingAllocator.this);
                }
                return null;
            }

            @Override
            protected void onRemoval(final ThreadLocalSizeClassHeap heap) throws Exception {
                if (heap != null) {
                    heap.free();
                }
            }
        };
    }

    ByteBuf allocate(int size, int maxCapacity) {
        return allocate(size, maxCapacity, Thread.currentThread(), null);
    }

    private AdaptiveByteBuf allocate(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
        AdaptiveByteBuf allocated = null;
        if (size <= MAX_POOLED_BUF_SIZE) {
            final int index = sizeClassIndexOf(size);
            if (index < SIZE_CLASSES_COUNT) {
                ThreadLocalSizeClassHeap heap = null;
                if (!IS_LOW_MEM && FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals()) {
                    heap = threadLocalSizeClassHeap.get();
                }
                if (heap != null) {
                    allocated = heap.allocate(index, size, maxCapacity, buf);
                } else {
                    allocated = allocateShared(index, size, maxCapacity, currentThread, buf);
                }
            } else if (!IS_LOW_MEM) {
                allocated = allocateShared(index, size, maxCapacity, currentThread, buf);
            }
        }
        if (allocated == null) {
            allocated = allocateFallback(size, maxCapacity, buf);
        }
        return allocated;
    }

    private AdaptiveByteBuf allocateShared(int sizeClassIndex, int size, int maxCapacity,
                                             Thread currentThread, AdaptiveByteBuf buf) {
        boolean reallocate = buf != null;
        int threadIdx = threadIndex(currentThread);
        int expansions = 0;
        int currentScanLen;
        do {
            currentScanLen = stripeScanLength;
            int mask = currentScanLen - 1;
            int start = threadIdx & mask;
            for (int i = 0, m = currentScanLen << 1; i < m; i++) {
                StripedHeap stripe = stripedHeaps[(start + i) & mask];
                AdaptiveByteBuf result = stripe.tryAllocate(
                        sizeClassIndex, size, maxCapacity, buf, reallocate, this);
                if (result != null) {
                    return result;
                }
            }
            expansions++;
        } while (expansions <= EXPANSION_ATTEMPTS && tryExpandStripeScanLength(currentScanLen));

        return null;
    }

    private boolean tryExpandStripeScanLength(int observed) {
        int current = stripeScanLength;
        if (current > observed) {
            return true;
        }
        if (current >= MAX_STRIPES) {
            return false;
        }
        STRIPE_SCAN_LENGTH.compareAndSet(this, current, current << 1);
        return true;
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

    private AdaptiveByteBuf allocateFallback(int size, int maxCapacity, AdaptiveByteBuf buf) {
        if (buf == null) {
            buf = newFallbackBuffer();
        }
        // Create a one-off chunk for this allocation.
        AbstractByteBuf innerChunk = chunkAllocator.allocate(size, maxCapacity);
        Chunk chunk = new Chunk(innerChunk, this);
        chunkRegistry.add(chunk);
        try {
            boolean success = chunk.readInitInto(buf, size, size, maxCapacity);
            assert success : "Failed to initialize ByteBuf with dedicated chunk";
        } finally {
            // As the chunk is an one-off we need to always call release explicitly as readInitInto(...)
            // will take care of retain once when successful. Once The AdaptiveByteBuf is released it will
            // completely release the Chunk and so the contained innerChunk.
            chunk.release();
        }
        return buf;
    }

    private AdaptiveByteBuf newFallbackBuffer() {
        AdaptiveByteBuf buf = fallbackRecycler.get();
        buf.resetRefCnt();
        buf.discardMarks();
        return buf;
    }

    /**
     * Allocate into the given buffer. Used by {@link AdaptiveByteBuf#capacity(int)}.
     */
    void reallocate(int size, int maxCapacity, AdaptiveByteBuf into) {
        AdaptiveByteBuf result = allocate(size, maxCapacity, Thread.currentThread(), into);
        assert result == into : "Re-allocation created separate buffer instance";
    }

    long usedMemory() {
        return chunkRegistry.totalCapacity();
    }

    // Ensure that we release all previous pooled resources when this object is finalized. This is needed as otherwise
    // we might end up with leaks. While these leaks are usually harmless in reality it would still at least be
    // very confusing for users.
    @SuppressWarnings({"FinalizeDeclaration", "deprecation"})
    @Override
    protected void finalize() throws Throwable {
        try {
            free();
        } finally {
            super.finalize();
        }
    }

    private void free() {
        for (StripedHeap stripe : stripedHeaps) {
            stripe.freeStripe();
        }
        sharedBuddyCache.free();
    }

    private static final int FREELIST_POOL_COUNT; // number of distinct freelist capacity buckets
    static {
        // Compute the number of distinct power-of-2 freelist capacities across all size classes.
        // Capacities are chunkSize/segmentSize, and chunkSize = max(MIN_CHUNK_SIZE, segmentSize * 32).
        // Min capacity is 32 (2^5), max varies. We index by numberOfTrailingZeros(capacity) - 5.
        int maxCapBits = 0;
        for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
            int segmentSize = SIZE_CLASSES[i];
            int chunkSize = Math.max(MIN_CHUNK_SIZE, segmentSize * 32);
            int cap = chunkSize / segmentSize;
            int bits = Integer.numberOfTrailingZeros(Integer.highestOneBit(cap));
            if (bits > maxCapBits) {
                maxCapBits = bits;
            }
        }
        FREELIST_POOL_COUNT = maxCapBits - 5 + 1; // indices 0..(maxCapBits-5)
    }

    private static final class RecycleStack<T> {
        private final Object[] elements;
        private int size;

        RecycleStack(int capacity) {
            elements = new Object[capacity];
        }

        @SuppressWarnings("unchecked")
        T poll() {
            if (size == 0) {
                return null;
            }
            int idx = --size;
            T element = (T) elements[idx];
            elements[idx] = null; // help GC
            return element;
        }

        boolean offer(T element) {
            if (size >= elements.length) {
                return false;
            }
            elements[size++] = element;
            return true;
        }

        void forEach(java.util.function.Consumer<T> action) {
            for (int i = 0; i < size; i++) {
                @SuppressWarnings("unchecked")
                T element = (T) elements[i];
                action.accept(element);
                elements[i] = null;
            }
            size = 0;
        }

        void clear() {
            for (int i = 0; i < size; i++) {
                elements[i] = null;
            }
            size = 0;
        }
    }

    private static final class SizeClassChunkRecycler {
        @SuppressWarnings("unchecked")
        private final RecycleStack<AbstractByteBuf>[] bufferPools = new RecycleStack[CHUNK_POOL_COUNT];
        private final MpscIntQueue[] freelistSlots = new MpscIntQueue[FREELIST_POOL_COUNT];
        private final IntStack[] localFreelistSlots = new IntStack[FREELIST_POOL_COUNT];

        private static final int TARGET_RECYCLED_BYTES = 4 * 1024 * 1024;

        SizeClassChunkRecycler() {
            for (int i = 0; i < CHUNK_POOL_COUNT; i++) {
                bufferPools[i] = new RecycleStack<>(Math.max(1, TARGET_RECYCLED_BYTES / CHUNK_SIZES[i]));
            }
        }

        AbstractByteBuf pollBuffer(int sizeClassIndex) {
            int poolIdx = SIZE_CLASS_TO_CHUNK_POOL[sizeClassIndex];
            return bufferPools[poolIdx].poll();
        }

        boolean offerBuffer(AbstractByteBuf delegate, int sizeClassIndex) {
            int poolIdx = SIZE_CLASS_TO_CHUNK_POOL[sizeClassIndex];
            return bufferPools[poolIdx].offer(delegate);
        }

        private static int freelistPoolIndex(int capacity) {
            return Integer.numberOfTrailingZeros(Integer.highestOneBit(Math.max(32, capacity))) - 5;
        }

        MpscIntQueue pollFreelist(int capacity) {
            int idx = freelistPoolIndex(MathUtil.safeFindNextPositivePowerOfTwo(capacity));
            if (idx < 0 || idx >= freelistSlots.length) {
                return null;
            }
            MpscIntQueue fl = freelistSlots[idx];
            freelistSlots[idx] = null;
            return fl;
        }

        boolean offerFreelist(MpscIntQueue freelist) {
            int idx = freelistPoolIndex(freelist.capacity());
            if (idx < 0 || idx >= freelistSlots.length) {
                return false;
            }
            if (freelistSlots[idx] != null) {
                return false;
            }
            freelistSlots[idx] = freelist;
            return true;
        }

        IntStack pollLocalFreelist(int capacity) {
            int idx = freelistPoolIndex(capacity);
            if (idx < 0 || idx >= localFreelistSlots.length) {
                return null;
            }
            IntStack fl = localFreelistSlots[idx];
            localFreelistSlots[idx] = null;
            return fl;
        }

        boolean offerLocalFreelist(IntStack freelist) {
            int idx = freelistPoolIndex(freelist.capacity());
            if (idx < 0 || idx >= localFreelistSlots.length) {
                return false;
            }
            if (localFreelistSlots[idx] != null) {
                return false;
            }
            localFreelistSlots[idx] = freelist;
            return true;
        }

        void freeAll() {
            for (RecycleStack<AbstractByteBuf> pool : bufferPools) {
                pool.forEach(new java.util.function.Consumer<AbstractByteBuf>() {
                    @Override
                    public void accept(AbstractByteBuf buf) {
                        buf.release();
                    }
                });
            }
            java.util.Arrays.fill(freelistSlots, null);
            java.util.Arrays.fill(localFreelistSlots, null);
        }
    }

    // Striped heap holding all size-class magazines under one lock.
    // One StampedLock per stripe covers ALL size classes.
    private static final class StripedHeap {
        final StampedLock lock = new StampedLock();
        Magazine[] magazines;
        Magazine buddyMagazine;
        Magazine.AdaptiveRecycler recycler;
        SizeClassChunkRecycler chunkRecycler;

        Magazine getOrCreateMagazine(int sizeClassIndex, AdaptivePoolingAllocator allocator) {
            Magazine[] mags = magazines;
            if (mags == null) {
                return createFirstMagazine(sizeClassIndex, allocator);
            }
            Magazine mag = mags[sizeClassIndex];
            if (mag == null) {
                mag = createMagazine(sizeClassIndex, allocator);
            }
            return mag;
        }

        private Magazine createFirstMagazine(int sizeClassIndex, AdaptivePoolingAllocator allocator) {
            magazines = new Magazine[SIZE_CLASSES_COUNT];
            chunkRecycler = new SizeClassChunkRecycler();
            return createMagazine(sizeClassIndex, allocator);
        }

        private Magazine createMagazine(int sizeClassIndex, AdaptivePoolingAllocator allocator) {
            if (recycler == null) {
                recycler = Magazine.AdaptiveRecycler.sharedExclusiveGet(MAGAZINE_BUFFER_QUEUE_CAPACITY);
            }
            SizeClassChunkManagementStrategy strategy = allocator.sizeClassStrategies[sizeClassIndex];
            Magazine mag = new Magazine(allocator, strategy, chunkRecycler, sizeClassIndex, null, recycler);
            magazines[sizeClassIndex] = mag;
            return mag;
        }

        Magazine getOrCreateBuddyMagazine(AdaptivePoolingAllocator allocator) {
            Magazine mag = buddyMagazine;
            if (mag == null) {
                mag = createBuddyMagazine(allocator);
            }
            return mag;
        }

        private Magazine createBuddyMagazine(AdaptivePoolingAllocator allocator) {
            if (recycler == null) {
                recycler = Magazine.AdaptiveRecycler.sharedExclusiveGet(MAGAZINE_BUFFER_QUEUE_CAPACITY);
            }
            Magazine mag = new Magazine(allocator, allocator.buddyStrategy, recycler);
            buddyMagazine = mag;
            return mag;
        }

        void freeStripe() {
            final StampedLock l = lock;
            long stamp = l.writeLock();
            try {
                if (magazines != null) {
                    for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
                        Magazine mag = magazines[i];
                        if (mag != null) {
                            mag.free();
                            magazines[i] = null;
                        }
                    }
                }
                if (buddyMagazine != null) {
                    buddyMagazine.free();
                    buddyMagazine = null;
                }
                if (chunkRecycler != null) {
                    chunkRecycler.freeAll();
                }
            } finally {
                l.unlockWrite(stamp);
            }
        }

        AdaptiveByteBuf tryAllocate(int sizeClassIndex, int size, int maxCapacity,
                                     AdaptiveByteBuf buf, boolean reallocate,
                                     AdaptivePoolingAllocator allocator) {
            final StampedLock l = lock;
            long stamp = l.tryWriteLock();
            if (stamp == 0) {
                return null;
            }
            try {
                Magazine mag = sizeClassIndex < SIZE_CLASSES_COUNT
                        ? getOrCreateMagazine(sizeClassIndex, allocator)
                        : getOrCreateBuddyMagazine(allocator);
                if (buf == null) {
                    buf = mag.newBuffer();
                }
                if (mag.allocate(size, maxCapacity, buf, reallocate)) {
                    return buf;
                }
                if (!reallocate) {
                    buf.release();
                }
                return null;
            } finally {
                l.unlockWrite(stamp);
            }
        }
    }

    private static final class ThreadLocalSizeClassHeap {
        private final Magazine[] magazines = new Magazine[SIZE_CLASSES_COUNT];
        private final SizeClassChunkRecycler chunkRecycler = new SizeClassChunkRecycler();
        private final AdaptivePoolingAllocator allocator;

        ThreadLocalSizeClassHeap(AdaptivePoolingAllocator allocator) {
            this.allocator = allocator;
        }

        AdaptiveByteBuf allocate(int sizeClassIndex, int size, int maxCapacity, AdaptiveByteBuf buf) {
            Magazine mag = getOrCreateMagazine(sizeClassIndex);
            boolean reallocate = buf != null;
            if (!reallocate) {
                buf = mag.newBuffer();
            }
            boolean success = mag.allocate(size, maxCapacity, buf, reallocate);
            assert success : "Thread-local allocation must always succeed";
            return buf;
        }

        Magazine getOrCreateMagazine(int sizeClassIndex) {
            Magazine mag = magazines[sizeClassIndex];
            if (mag == null) {
                mag = createMagazine(sizeClassIndex);
            }
            return mag;
        }

        private Magazine createMagazine(int sizeClassIndex) {
            SizeClassChunkManagementStrategy strategy = allocator.sizeClassStrategies[sizeClassIndex];
            Magazine mag = new Magazine(allocator, strategy,
                                       chunkRecycler, sizeClassIndex, Thread.currentThread(), null);
            magazines[sizeClassIndex] = mag;
            return mag;
        }

        void free() {
            for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
                Magazine mag = magazines[i];
                if (mag != null) {
                    mag.free();
                    magazines[i] = null;
                }
            }
            chunkRecycler.freeAll();
        }
    }

    interface ChunkCache {
        Chunk pollChunk(int size);

        boolean offerChunk(Chunk chunk);

        void free();

        boolean isEmpty();

        default void tickPurge() {
        }
    }

    // Cached chunks are detached from magazines: no readInitInto can happen, so segment count
    // can only grow (external releaseSegment returns) and never shrink. Once a chunk reaches
    // full capacity (hasFullCapacity), it stays idle while in the cache.
    //
    // Epoch-based aging invariants (both caches):
    //
    // 1. CLASSIFICATION: purge scans all chunks. Idle (hasFullCapacity) → epoch++.
    //    Non-idle → epoch = 0. Only idle chunks can accumulate epoch.
    //
    // 2. EVICTION: idle chunks with epoch > CHUNK_PURGE_THRESHOLD are evicted (markToDeallocate).
    //    Eviction is immediate — all segments are in, no outstanding references.
    //    Non-idle chunks are never evicted (deallocation would be deferred, not immediate).
    //    Evicted chunks are offered to the stripe's recycled pool for cross-size-class reuse.
    //
    // 3. SCAN RESET: scanForCapacity resets purgeEpoch = 0 on the chunk it picks. The scan
    //    knows the chunk is being used. The chunk gets allocated from, becomes non-idle, and
    //    the next purge resets its epoch anyway (non-idle → 0). The scan reset covers the case
    //    where all segments return before the next purge (short-lived buffers).
    //
    // 4. CONVERGENCE: idle chunks that are never picked by scan age undisturbed across
    //    purge cycles. After CHUNK_PURGE_THRESHOLD + 1 consecutive cycles of being idle and
    //    unpolled, they are evicted. Chunks picked by scan get epoch reset — aging interrupted.
    //    Partition orders [epoch=0 | 0<epoch<T | epoch>=T | noCap]. Scan takes
    //    from head (epoch=0 first). Chunks with epoch>=threshold are placed at the back of
    //    the hasCap zone so scan doesn't reach them — they age to threshold+1 and get evicted.
    abstract static class SizeClassedChunkCache implements ChunkCache {
        @Override
        public abstract SizeClassedChunk pollChunk(int size);

        // Visible for testing: triggers a purge scan bypassing the budget counter.
        abstract SizeClassedChunk forcePurge();
    }

    /**
     * Ring buffer cache for thread-local chunk reuse (SPSC — only the owner thread accesses it).
     *
     * <p>Logical layout after purge:
     * <pre>
     *   head                          tail
     *   v                             v
     *   [..., notEmpty, notEmpty, ..., empty, empty, ..., null, ...]
     *        |--- notEmptyCount ---|--- emptyCount --|
     *        |------------ count ------------------|
     * </pre>
     *
     * <p>Physical layout when the ring wraps:
     * <pre>
     *   0         tail          head          length
     *   v         v             v             v
     *   [...tail] [  unused  ]  [head................]
     *             ^             |--- content wraps ---|
     *             wrap point
     * </pre>
     *
     * <p><b>scanForCapacity</b> — O(1) fast path takes from head while {@code notEmptyCount > 0}:
     * <pre>
     *   before: notEmptyCount=2, count=5
     *   [NE, NE, E, E, E, _, _, _]
     *    ^head            ^tail
     *
     *   after: returns NE, notEmptyCount=1, count=4
     *   [_,  NE, E, E, E, _, _, _]
     *        ^head        ^tail
     * </pre>
     * Fallback when {@code notEmptyCount == 0}: linear scan of the empty zone for chunks
     * that gained capacity from external segment returns.
     *
     * <p><b>offerChunk</b> — write at tail, grow (double + linearize) if full:
     * <pre>
     *   before: count=4
     *   [_,  NE, E, E, E, _, _, _]
     *        ^head        ^tail
     *
     *   after: count=5
     *   [_,  NE, E, E, E, X, _, _]
     *        ^head           ^tail
     * </pre>
     *
     * <p><b>runPurgeScan</b> (triggered by the per-magazine allocation counter) —
     * two passes. Pass 1: age idle chunks (full → epoch++, non-full → epoch=0), evict
     * past threshold, compact survivors (nulls stale slots inline). Pass 2: partition
     * hasCap to front / noCap to back, then three-way Dutch-flag within hasCap into
     * [epoch=0 | 0&lt;epoch&lt;threshold | epoch&gt;=threshold]. Chunks with epoch&gt;=threshold
     * are placed at the back of hasCap so scan doesn't reach them — they age to
     * threshold+1 and get evicted. Never selects — selection is always
     * {@code scanForCapacity}.
     *
     * <p>Case 1 — no eviction, an empty chunk gained capacity externally (common):
     * <pre>
     *   before (E* gained capacity since last purge):
     *   [NE, NE, E*, E, _, _, _, _]
     *    ^head            ^tail
     *    notEmptyCount=2
     *
     *   pass 1: age idle chunks. None past threshold. No compaction needed.
     *   pass 2 (partition): E* now has capacity → placed in notEmpty zone.
     *
     *   after:
     *   [NE, NE, E*, E, _, _, _, _]
     *    ^head            ^tail
     *    notEmptyCount=3
     * </pre>
     *
     * <p>Case 2 — eviction (uncommon, burst wind-down):
     * <pre>
     *   before (ring wraps, IDLE* = idle past threshold):
     *   [E, NE, _,  IDLE*, NE, E, E, NE]
     *          ^tail ^head
     *
     *   pass 1: IDLE* evicted (markToDeallocate), survivors compacted, stale slots nulled.
     *   [_, _, _,  NE, E, E, NE, E]
     *     ^tail    ^head
     *              |--- kept=6 ---|
     *
     *   pass 2 (partition): [epoch=0 hasCap | 0&lt;epoch&lt;T hasCap | epoch&gt;=T hasCap | noCap].
     *   [_, _, _,  NE, NE, E, E, E]
     *     ^tail    ^head
     *              notEmptyCount=2, count=6
     * </pre>
     * Idle chunks ({@code remainingCapacity == capacity}) age via purgeEpoch and are evicted
     * past threshold. Evicted chunks are offered to the stripe's recycled pool.
     */
    static final class ThreadLocalSizeClassedChunkCache extends SizeClassedChunkCache {
        SizeClassedChunk[] chunks; // package-private for testing
        int head;
        int tail;
        int count;
        int notEmptyCount;

        SizeClassChunkRecycler chunkRecycler;
        int sizeClassIndex;
        final int maxCachedChunks;
        final int purgeRetentionFloor;

        ThreadLocalSizeClassedChunkCache(int chunkSize, SizeClassChunkRecycler chunkRecycler, int sizeClassIndex) {
            chunks = new SizeClassedChunk[8];
            this.chunkRecycler = chunkRecycler;
            this.sizeClassIndex = sizeClassIndex;
            maxCachedChunks = Math.max(1, THREAD_LOCAL_CACHE_MAX_BYTES / chunkSize);
            purgeRetentionFloor = Math.min(maxCachedChunks, Math.max(1, THREAD_LOCAL_CACHE_MIN_BYTES / chunkSize));
        }

        @Override
        SizeClassedChunk forcePurge() {
            runPurgeScan();
            return scanForCapacity();
        }

        @Override
        public SizeClassedChunk pollChunk(int size) {
            return scanForCapacity();
        }

        @Override
        public void tickPurge() {
            runPurgeScan();
        }

        private SizeClassedChunk scanForCapacity() {
            if (notEmptyCount > 0) {
                SizeClassedChunk chunk = chunks[head];
                assert chunk.hasRemainingCapacity();
                chunk.purgeEpoch = 0;
                chunks[head] = null;
                head = (head + 1) & (chunks.length - 1);
                count--;
                notEmptyCount--;
                return chunk;
            }
            return scanForCapacityFallback();
        }

        private SizeClassedChunk scanForCapacityFallback() {
            int mask = chunks.length - 1;
            int emptyCount = count - notEmptyCount;
            int pos = (head + notEmptyCount) & mask;
            for (int i = 0; i < emptyCount; i++) {
                SizeClassedChunk chunk = chunks[pos];
                if (chunk.hasRemainingCapacity()) {
                    chunk.purgeEpoch = 0;
                    int lastIdx = (tail - 1) & mask;
                    chunks[pos] = chunks[lastIdx];
                    chunks[lastIdx] = null;
                    tail = lastIdx;
                    count--;
                    return chunk;
                }
                pos = (pos + 1) & mask;
            }
            return null;
        }

        private void runPurgeScan() {
            int mask = chunks.length - 1;
            int kept = 0;
            int survivors = count;
            for (int i = 0; i < count; i++) {
                int readIdx = (head + i) & mask;
                SizeClassedChunk chunk = chunks[readIdx];
                if (chunk.purgeEpoch > 0) {
                    assert chunk.hasFullCapacity();
                    chunk.purgeEpoch++;
                    if (chunk.purgeEpoch > CHUNK_PURGE_THRESHOLD && survivors > purgeRetentionFloor) {
                        chunk.recycleOrDeallocate(chunkRecycler, sizeClassIndex);
                        chunks[readIdx] = null;
                        survivors--;
                        continue;
                    }
                } else if (chunk.hasFullCapacity()) {
                    chunk.purgeEpoch = 1;
                }
                int writeIdx = (head + kept) & mask;
                if (writeIdx != readIdx) {
                    chunks[writeIdx] = chunk;
                    chunks[readIdx] = null;
                }
                kept++;
            }
            tail = (head + kept) & mask;
            count = kept;
            partition(kept);
        }

        private void partition(int size) {
            int mask = chunks.length - 1;
            // Pass 1: hasCapacity to front, noCapacity to back.
            int lo = 0;
            int hi = size - 1;
            while (lo <= hi) {
                int loIdx = (head + lo) & mask;
                if (chunks[loIdx].hasRemainingCapacity()) {
                    lo++;
                } else {
                    int hiIdx = (head + hi) & mask;
                    SizeClassedChunk tmp = chunks[loIdx];
                    chunks[loIdx] = chunks[hiIdx];
                    chunks[hiIdx] = tmp;
                    hi--;
                }
            }
            notEmptyCount = lo;
            // Pass 2: three-way Dutch-flag within notEmpty:
            //   [epoch=0 | 0<epoch<threshold | epoch>=threshold]
            //
            // Epoch=0 (recently used) at head — scan picks these first.
            // Epoch>=threshold (about to be evicted) at back — scan doesn't reach them,
            // so they age one more cycle to threshold+1 and get evicted.
            //
            // This ordering guarantees convergence regardless of count/polls ratio.
            // Without it (e.g., a simple epoch=0/epoch>0 split with mid++), when
            // count/polls == threshold the groups rotate perfectly and max epoch never
            // exceeds threshold — eviction stalls at threshold * polls chunks.
            int elo = 0;
            int emid = 0;
            int ehi = lo - 1;
            while (emid <= ehi) {
                int emidIdx = (head + emid) & mask;
                SizeClassedChunk c = chunks[emidIdx];
                if (c.purgeEpoch == 0) {
                    if (elo != emid) {
                        int eloIdx = (head + elo) & mask;
                        chunks[emidIdx] = chunks[eloIdx];
                        chunks[eloIdx] = c;
                    }
                    elo++;
                    emid++;
                } else if (c.purgeEpoch < CHUNK_PURGE_THRESHOLD) {
                    emid++;
                } else {
                    int ehiIdx = (head + ehi) & mask;
                    chunks[emidIdx] = chunks[ehiIdx];
                    chunks[ehiIdx] = c;
                    ehi--;
                }
            }
        }

        @Override
        public boolean offerChunk(Chunk chunk) {
            if (count >= maxCachedChunks) {
                return false;
            }
            if (count == chunks.length) {
                SizeClassedChunk[] newChunks = new SizeClassedChunk[chunks.length * 2];
                for (int i = 0; i < count; i++) {
                    newChunks[i] = chunks[(head + i) & (chunks.length - 1)];
                }
                chunks = newChunks;
                head = 0;
                tail = count;
            }
            chunks[tail] = (SizeClassedChunk) chunk;
            tail = (tail + 1) & (chunks.length - 1);
            count++;
            return true;
        }

        @Override
        public String toString() {
            int mask = chunks.length - 1;
            StringBuilder sb = new StringBuilder();
            sb.append("ThreadLocalCache[head=").append(head)
                    .append(", tail=").append(tail)
                    .append(", count=").append(count)
                    .append(", notEmpty=").append(notEmptyCount)
                    .append(", length=").append(chunks.length)
                    .append("]\n  ");
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                if (i == notEmptyCount) {
                    sb.append("| ");
                }
                SizeClassedChunk c = chunks[(head + i) & mask];
                String region = i < notEmptyCount ? "notEmpty" : "empty";
                String actual = c == null ? "null" :
                        c.hasRemainingCapacity() ? "hasCap" : "noCap";
                sb.append('[').append(region).append(':').append(actual)
                        .append(",ep=").append(c == null ? -1 : c.purgeEpoch).append(']');
            }
            return sb.toString();
        }

        @Override
        public void free() {
            int mask = chunks.length - 1;
            for (int i = 0; i < count; i++) {
                int idx = (head + i) & mask;
                chunks[idx].markToDeallocate();
                chunks[idx] = null;
            }
            head = 0;
            tail = 0;
            count = 0;
            notEmptyCount = 0;
        }

        @Override
        public boolean isEmpty() {
            return count == 0;
        }
    }

    private static final class ConcurrentSkipListChunkCache implements ChunkCache {
        private final ConcurrentSkipListIntObjMultimap<Chunk> chunks;

        private ConcurrentSkipListChunkCache() {
            chunks = new ConcurrentSkipListIntObjMultimap<>(-1);
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
                int key = -1;
                Chunk toDeallocate = null;
                for (IntEntry<Chunk> entry : chunks) {
                    Chunk candidate = entry.getValue();
                    if (candidate != null && RefCnt.refCnt(candidate.refCnt) == 1) {
                        toDeallocate = candidate;
                        key = entry.getKey();
                        break;
                    }
                }
                if (toDeallocate == null) {
                    break;
                }
                if (chunks.remove(key, toDeallocate)) {
                    toDeallocate.markToDeallocate();
                }
                size = chunks.size();
            }
            return true;
        }

        @Override
        public void free() {
            for (IntEntry<Chunk> entry : chunks) {
                Chunk chunk = entry.getValue();
                if (chunk != null && chunks.remove(entry.getKey(), chunk)) {
                    chunk.markToDeallocate();
                }
            }
        }

        @Override
        public boolean isEmpty() {
            return chunks.isEmpty();
        }
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

    private static final class SizeClassChunkManagementStrategy {
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

        ChunkController createController(AdaptivePoolingAllocator allocator) {
            return new SizeClassChunkController(
                    allocator.chunkAllocator, allocator.chunkRegistry, segmentSize, chunkSize);
        }

        ChunkCache createChunkCache(SizeClassChunkRecycler chunkRecycler, int sizeClassIndex) {
            return new ThreadLocalSizeClassedChunkCache(chunkSize, chunkRecycler, sizeClassIndex);
        }
    }

    private static final class SizeClassChunkController implements ChunkController {

        private final ChunkAllocator chunkAllocator;
        private final int segmentSize;
        private final int chunkSize;
        private final ChunkRegistry chunkRegistry;

        private SizeClassChunkController(ChunkAllocator chunkAllocator, ChunkRegistry chunkRegistry,
                                          int segmentSize, int chunkSize) {
            this.chunkAllocator = chunkAllocator;
            this.segmentSize = segmentSize;
            this.chunkSize = chunkSize;
            this.chunkRegistry = chunkRegistry;
        }

        private MpscIntQueue createEmptyFreeList() {
            return MpscIntQueue.create(chunkSize / segmentSize, SizeClassedChunk.FREE_LIST_EMPTY);
        }

        private MpscIntQueue createFreeList() {
            final int segmentsCount = chunkSize / segmentSize;
            final MpscIntQueue freeList = MpscIntQueue.create(segmentsCount, SizeClassedChunk.FREE_LIST_EMPTY);
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
            if (magazine.chunkRecycler != null) {
                // Try recycled buffer
                AbstractByteBuf recycledBuf = magazine.chunkRecycler.pollBuffer(magazine.sizeClassIndex);
                if (recycledBuf != null) {
                    int neededSegments = chunkSize / segmentSize;
                    // Try recycled freelist of matching capacity
                    MpscIntQueue recycledFL = magazine.chunkRecycler.pollFreelist(neededSegments);
                    if (recycledFL == null) {
                        recycledFL = MpscIntQueue.create(neededSegments, SizeClassedChunk.FREE_LIST_EMPTY);
                    }
                    IntStack recycledLocal = (magazine.ownerThread != null) ?
                            magazine.chunkRecycler.pollLocalFreelist(neededSegments) : null;
                    SizeClassedChunk chunk = new SizeClassedChunk(
                            recycledBuf, recycledFL, recycledLocal, magazine, this);
                    chunkRegistry.add(chunk);
                    return chunk;
                }
            }
            AbstractByteBuf chunkBuffer = chunkAllocator.allocate(chunkSize, chunkSize);
            assert chunkBuffer.capacity() == chunkSize;
            SizeClassedChunk chunk = new SizeClassedChunk(chunkBuffer, magazine, this);
            chunkRegistry.add(chunk);
            return chunk;
        }
    }

    private static final class BuddyChunkManagementStrategy {
        private final AtomicInteger maxChunkSize = new AtomicInteger();

        ChunkController createController(AdaptivePoolingAllocator allocator) {
            return new BuddyChunkController(
                    allocator.chunkAllocator, allocator.chunkRegistry, maxChunkSize);
        }

        ChunkCache createChunkCache() {
            return new ConcurrentSkipListChunkCache();
        }
    }

    private static final class BuddyChunkController implements ChunkController {
        private final ChunkAllocator chunkAllocator;
        private final ChunkRegistry chunkRegistry;
        private final AtomicInteger maxChunkSize;

        BuddyChunkController(ChunkAllocator chunkAllocator, ChunkRegistry chunkRegistry,
                             AtomicInteger maxChunkSize) {
            this.chunkAllocator = chunkAllocator;
            this.chunkRegistry = chunkRegistry;
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

    private static int threadIndex(Thread t) {
        int id = (int) t.getId();
        return id ^ (id >>> 16);
    }

    private static final class Magazine {
        private static final Chunk MAGAZINE_FREED = new Chunk();

        static final class AdaptiveRecycler extends Recycler<AdaptiveByteBuf> {

            private AdaptiveRecycler(boolean unguarded) {
                // uses fast thread local
                super(unguarded);
            }

            private AdaptiveRecycler(int maxCapacity, boolean unguarded) {
                // doesn't use fast thread local, shared MPMC
                super(maxCapacity, unguarded);
            }

            private AdaptiveRecycler(int maxCapacity, boolean unguarded, boolean exclusiveGet) {
                // doesn't use fast thread local, exclusive-get mode
                super(maxCapacity, unguarded, exclusiveGet);
            }

            @Override
            protected AdaptiveByteBuf newObject(final Handle<AdaptiveByteBuf> handle) {
                return new AdaptiveByteBuf((EnhancedHandle<AdaptiveByteBuf>) handle);
            }

            public static AdaptiveRecycler threadLocal() {
                return new AdaptiveRecycler(true);
            }

            public static AdaptiveRecycler sharedWith(int maxCapacity) {
                return new AdaptiveRecycler(maxCapacity, true);
            }

            public static AdaptiveRecycler sharedExclusiveGet(int maxCapacity) {
                return new AdaptiveRecycler(maxCapacity, true, true);
            }
        }

        private static final AdaptiveRecycler EVENT_LOOP_LOCAL_BUFFER_POOL = AdaptiveRecycler.threadLocal();

        private Chunk current;
        private Chunk nextInLine;
        final AdaptivePoolingAllocator allocator;
        final Thread ownerThread;
        private final ChunkController chunkController;
        private final ChunkCache chunkCache;
        final int sizeClassIndex;
        final SizeClassChunkRecycler chunkRecycler;
        final AdaptiveRecycler bufRecycler; // for ByteBuf wrapper pooling; null → EVENT_LOOP_LOCAL_BUFFER_POOL
        private final int purgeTickThreshold;
        private int allocCount;

        // Size-classed magazine constructor (both thread-local and shared-stripe)
        Magazine(AdaptivePoolingAllocator allocator, SizeClassChunkManagementStrategy strategy,
                 SizeClassChunkRecycler chunkRecycler, int sizeClassIndex,
                 Thread ownerThread, AdaptiveRecycler bufRecycler) {
            this.allocator = allocator;
            this.ownerThread = ownerThread;
            this.sizeClassIndex = sizeClassIndex;
            this.chunkRecycler = chunkRecycler;
            this.bufRecycler = bufRecycler;
            this.chunkController = strategy.createController(allocator);
            this.chunkCache = strategy.createChunkCache(chunkRecycler, sizeClassIndex);
            this.purgeTickThreshold = (int) Math.min(Integer.MAX_VALUE,
                    CHUNK_PURGE_POLLS_THREAD_LOCAL * (strategy.chunkSize / strategy.segmentSize));
        }

        // Buddy (large buffer) magazine constructor
        Magazine(AdaptivePoolingAllocator allocator,
                 BuddyChunkManagementStrategy strategy, AdaptiveRecycler bufRecycler) {
            this.allocator = allocator;
            this.ownerThread = null;
            this.sizeClassIndex = -1;
            this.chunkRecycler = null;
            this.bufRecycler = bufRecycler;
            this.chunkController = strategy.createController(allocator);
            this.chunkCache = allocator.sharedBuddyCache;
            this.purgeTickThreshold = 0;
        }

        private void tickAllocPurge() {
            if (purgeTickThreshold > 0 && ++allocCount >= purgeTickThreshold) {
                allocCount = 0;
                chunkCache.tickPurge();
            }
        }

        boolean allocate(int size, int maxCapacity, AdaptiveByteBuf buf, boolean reallocate) {
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
                    tickAllocPurge();
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
            curr = nextInLine;
            nextInLine = null;
            if (curr != null) {
                if (curr == MAGAZINE_FREED) {
                    restoreMagazineFreed();
                    return false;
                }

                int remainingCapacity = curr.remainingCapacity();
                if (remainingCapacity > startingCapacity &&
                        curr.readInitInto(buf, size, startingCapacity, maxCapacity)) {
                    // We have a Chunk that has some space left.
                    current = curr;
                    tickAllocPurge();
                    return true;
                }

                try {
                    if (remainingCapacity >= size) {
                        // At this point we know that this will be the last time curr will be used, so directly set it
                        // to null and release it once we are done.
                        boolean allocated = curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                        if (allocated) {
                            tickAllocPurge();
                        }
                        return allocated;
                    }
                } finally {
                    // Release in a finally block so even if readInitInto(...) would throw we would still correctly
                    // release the current chunk before null it out.
                    curr.releaseFromMagazine();
                }
            }

            // Now try to poll from the cache first
            curr = chunkCache.pollChunk(size);
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
            if (success) {
                tickAllocPurge();
            }
            return success;
        }

        private void restoreMagazineFreed() {
            Chunk next = nextInLine;
            nextInLine = MAGAZINE_FREED;
            if (next != null && next != MAGAZINE_FREED) {
                next.releaseFromMagazine();
            }
        }

        private void transferToNextInLineOrRelease(Chunk chunk) {
            Chunk next = nextInLine;
            if (next == null) {
                nextInLine = chunk;
                return;
            }
            if (next != MAGAZINE_FREED && chunk.remainingCapacity() > next.remainingCapacity()) {
                nextInLine = chunk;
                next.releaseFromMagazine();
                return;
            }
            chunk.releaseFromMagazine();
        }

        void free() {
            restoreMagazineFreed();
            if (current != null) {
                current.releaseFromMagazine();
                current = null;
            }
            if (chunkCache != allocator.sharedBuddyCache) {
                chunkCache.free();
            }
        }

        public AdaptiveByteBuf newBuffer() {
            AdaptiveByteBuf buf = bufRecycler != null ? bufRecycler.get() : EVENT_LOOP_LOCAL_BUFFER_POOL.get();
            buf.resetRefCnt();
            buf.discardMarks();
            return buf;
        }

        boolean offerToCache(Chunk chunk) {
            if (chunk.hasUnprocessedFreelistEntries()) {
                chunk.processFreelistEntries();
            }
            return chunkCache.offerChunk(chunk);
        }
    }

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

    static class Chunk implements ChunkInfo {
        protected AbstractByteBuf delegate;
        protected Magazine magazine;
        final AdaptivePoolingAllocator allocator;
        // Always populate the refCnt field, so HotSpot doesn't emit `null` checks.
        // This is safe to do even on native-image.
        final RefCnt refCnt = new RefCnt();
        private final int capacity;
        private final boolean pooled;
        protected int allocatedBytes;

        Chunk() {
            // Constructor only used by the MAGAZINE_FREED sentinel.
            delegate = null;
            magazine = null;
            allocator = null;
            capacity = 0;
            pooled = false;
        }

        Chunk(AbstractByteBuf delegate, AdaptivePoolingAllocator allocator) {
            this.delegate = delegate;
            this.pooled = false;
            capacity = delegate.capacity();
            this.allocator = allocator;
        }

        Chunk(AbstractByteBuf delegate, Magazine magazine, boolean pooled) {
            this.delegate = delegate;
            this.pooled = pooled;
            capacity = delegate.capacity();
            attachToMagazine(magazine);

            // We need the top-level allocator so ByteBuf.capacity(int) can call reallocate()
            allocator = magazine.allocator;

            if (PlatformDependent.isJfrEnabled() && AllocateChunkEvent.isEventEnabled()) {
                AllocateChunkEvent event = new AllocateChunkEvent();
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator.class);
                    event.pooled = pooled;
                    event.threadLocal = magazine.ownerThread != null;
                    event.commit();
                }
            }
        }

        void attachToMagazine(Magazine magazine) {
            assert this.magazine == null;
            this.magazine = magazine;
        }

        /**
         * Called when a magazine is done using this chunk, probably because it was emptied.
         */
        void releaseFromMagazine() {
            Magazine mag = magazine;
            magazine = null;
            if (!mag.offerToCache(this)) {
                markToDeallocate();
            }
        }

        /**
         * Called when a ByteBuf is done using its allocation in this chunk.
         */
        void releaseSegment(int ignoredSegmentId, int size) {
            release();
        }

        void markToDeallocate() {
            release();
        }

        private void retain() {
            RefCnt.retain(refCnt);
        }

        protected boolean release() {
            boolean deallocate = RefCnt.release(refCnt);
            if (deallocate) {
                deallocate();
            }
            return deallocate;
        }

        protected void deallocate() {
            onRelease();
            allocator.chunkRegistry.remove(this);
            if (delegate != null) {
                delegate.release();
            }
        }

        private void onRelease() {
            if (PlatformDependent.isJfrEnabled() && FreeChunkEvent.isEventEnabled()) {
                FreeChunkEvent event = new FreeChunkEvent();
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator.class);
                    event.pooled = pooled;
                    event.commit();
                }
            }
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

        @Override
        public int capacity() {
            return capacity;
        }

        @Override
        public boolean isDirect() {
            return delegate.isDirect();
        }

        @Override
        public long memoryAddress() {
            return delegate._memoryAddress();
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

        public int capacity() {
            return stack.length;
        }

        void refill(int count, int segmentSize) {
            int offset = count * segmentSize;
            for (int i = 0; i < count; i++) {
                offset -= segmentSize;
                stack[i] = offset;
            }
            top = count - 1;
        }
    }

    /**
     * Removes per-allocation retain()/release() atomic ops from the hot path by replacing ref counting
     * with a segment-count state machine. Atomics are only needed on the cold deallocation path
     * ({@link #markToDeallocate()}), which is rare for long-lived chunks that cycle segments many times.
     * The tradeoff is a {@link MpscIntQueue#size()} call (volatile reads, no RMW) per remaining segment
     * return after mark — acceptable since it avoids atomic RMWs entirely.
     * <p>
     * State transitions:
     * <ul>
     *   <li>{@link #AVAILABLE} (-1): chunk is in use, no deallocation tracking needed</li>
     *   <li>0..N: local free list size at the time {@link #markToDeallocate()} was called;
     *       used to track when all segments have been returned</li>
     *   <li>{@link #DEALLOCATED} (Integer.MIN_VALUE): all segments returned, chunk deallocated</li>
     * </ul>
     * <p>
     * Ordering: external {@link #releaseSegment} pushes to the MPSC queue (which has an implicit
     * StoreLoad barrier via its {@code offer()}), then reads {@code state} — this guarantees
     * visibility of any preceding {@link #markToDeallocate()} write.
     */
    static class SizeClassedChunk extends Chunk {
        private static final int FREE_LIST_EMPTY = -1;
        private static final int AVAILABLE = -1;
        // Integer.MIN_VALUE so that `DEALLOCATED + externalFreeList.size()` can never equal `segments`,
        // making late-arriving releaseSegment calls on external threads arithmetically harmless.
        private static final int DEALLOCATED = Integer.MIN_VALUE;
        private static final AtomicIntegerFieldUpdater<SizeClassedChunk> STATE =
                AtomicIntegerFieldUpdater.newUpdater(SizeClassedChunk.class, "state");
        private volatile int state;
        private final int segments;
        private final int segmentSize;
        MpscIntQueue externalFreeList;
        private IntStack localFreeList;
        private final Thread ownerThread;
        int purgeEpoch;

        SizeClassedChunk(AbstractByteBuf delegate, Magazine magazine,
                         SizeClassChunkController controller) {
            super(delegate, magazine, true);
            segmentSize = controller.segmentSize;
            segments = controller.chunkSize / segmentSize;
            STATE.lazySet(this, AVAILABLE);
            ownerThread = magazine.ownerThread;
            if (ownerThread == null) {
                externalFreeList = controller.createFreeList();
                localFreeList = null;
            } else {
                externalFreeList = controller.createEmptyFreeList();
                localFreeList = controller.createLocalFreeList();
            }
        }

        /**
         * Constructor for recycled parts: reuses a recycled delegate buffer and a recycled freelist.
         */
        SizeClassedChunk(AbstractByteBuf recycledDelegate, MpscIntQueue recycledFreeList,
                         IntStack recycledLocalFreeList,
                         Magazine magazine, SizeClassChunkController controller) {
            super(recycledDelegate, magazine, true);
            this.externalFreeList = recycledFreeList;
            segmentSize = controller.segmentSize;
            segments = controller.chunkSize / segmentSize;
            STATE.lazySet(this, AVAILABLE);
            ownerThread = magazine.ownerThread;
            if (ownerThread != null) {
                if (recycledLocalFreeList != null && recycledLocalFreeList.capacity() >= segments) {
                    localFreeList = recycledLocalFreeList;
                    localFreeList.refill(segments, segmentSize);
                } else {
                    localFreeList = controller.createLocalFreeList();
                }
                recycledFreeList.resetAndFill(0, segmentSize);
            } else {
                localFreeList = null;
                recycledFreeList.resetAndFill(segments, segmentSize);
            }
        }

        @Override
        public boolean readInitInto(AdaptiveByteBuf buf, int size, int startingCapacity, int maxCapacity) {
            assert state == AVAILABLE;
            final int startIndex = nextAvailableSegmentOffset();
            if (startIndex == FREE_LIST_EMPTY) {
                return false;
            }
            allocatedBytes += segmentSize;
            try {
                buf.init(delegate, this, 0, 0, startIndex, size, startingCapacity, maxCapacity);
            } catch (Throwable t) {
                allocatedBytes -= segmentSize;
                releaseSegmentOffsetIntoFreeList(startIndex);
                throw t;
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

        // this can be used by the ConcurrentQueueChunkCache to find the first buffer to use:
        // it doesn't update the remaining capacity and it's not consider a single segmentSize
        // case as not suitable to be reused
        public boolean hasRemainingCapacity() {
            int remaining = super.remainingCapacity();
            if (remaining > 0) {
                return true;
            }
            if (localFreeList != null) {
                return !localFreeList.isEmpty();
            }
            return !externalFreeList.isEmpty();
        }

        boolean hasFullCapacity() {
            int free = externalFreeList.size();
            IntStack local = localFreeList;
            if (local != null) {
                free += local.size();
            }
            return free == segments;
        }

        @Override
        public int remainingCapacity() {
            int remaining = super.remainingCapacity();
            return remaining > segmentSize ? remaining : updateRemainingCapacity(remaining);
        }

        private int updateRemainingCapacity(int snapshotted) {
            int freeSegments = externalFreeList.size();
            IntStack localFreeList = this.localFreeList;
            if (localFreeList != null) {
                freeSegments += localFreeList.size();
            }
            int updated = freeSegments * segmentSize;
            if (updated != snapshotted) {
                allocatedBytes = capacity() - updated;
            }
            return updated;
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
            IntStack localFreeList = this.localFreeList;
            if (localFreeList != null && Thread.currentThread() == ownerThread) {
                localFreeList.push(startIndex);
                int state = this.state;
                if (state != AVAILABLE) {
                    updateStateOnLocalReleaseSegment(state, localFreeList);
                }
            } else {
                boolean segmentReturned = externalFreeList.offer(startIndex);
                assert segmentReturned;
                // implicit StoreLoad barrier from MPSC offer()
                int state = this.state;
                if (state != AVAILABLE) {
                    deallocateIfNeeded(state);
                }
            }
        }

        private void updateStateOnLocalReleaseSegment(int previousLocalSize, IntStack localFreeList) {
            int newLocalSize = localFreeList.size();
            boolean alwaysTrue = STATE.compareAndSet(this, previousLocalSize, newLocalSize);
            assert alwaysTrue : "this shouldn't happen unless double release in the local free list";
            deallocateIfNeeded(newLocalSize);
        }

        private void deallocateIfNeeded(int localSize) {
            // Check if all segments have been returned.
            MpscIntQueue fl = externalFreeList;
            int externalSize = fl != null ? fl.size() : 0;
            int totalFreeSegments = localSize + externalSize;
            if (totalFreeSegments == segments && STATE.compareAndSet(this, localSize, DEALLOCATED)) {
                deallocate();
            }
        }

        void recycleOrDeallocate(SizeClassChunkRecycler recycler, int sizeClassIndex) {
            if (recycler != null) {
                if (recycler.offerBuffer(delegate, sizeClassIndex)) {
                    delegate = null;
                }
                if (externalFreeList != null) {
                    recycler.offerFreelist(externalFreeList);
                }
                if (localFreeList != null) {
                    recycler.offerLocalFreelist(localFreeList);
                }
            }
            externalFreeList = null;
            localFreeList = null;
            markToDeallocate();
        }

        @Override
        void markToDeallocate() {
            MpscIntQueue fl = externalFreeList;
            if (fl == null) {
                // Freelist was stripped (pooled separately). No outstanding segments possible
                // since the chunk had full capacity when it was stripped.
                STATE.set(this, DEALLOCATED);
                deallocate();
                return;
            }
            IntStack localFreeList = this.localFreeList;
            int localSize = localFreeList != null ? localFreeList.size() : 0;
            STATE.set(this, localSize);
            deallocateIfNeeded(localSize);
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
            super(delegate, magazine, true);
            freeListCapacity = delegate.capacity() / MIN_BUDDY_SIZE;
            int maxShift = Integer.numberOfTrailingZeros(freeListCapacity);
            assert maxShift <= 30; // The top 2 bits are used for marking.
            freeList = MpscIntQueue.create(freeListCapacity, -1); // At most half of tree (all leaf nodes) can be freed.
            buddies = new byte[freeListCapacity << 1];

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
                    unreserveMatchingBuddy(1, startingCapacity, startIndex, 0);
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
            int size = unpackSize(packed);
            int offset = unpackOffset(packed);
            unreserveMatchingBuddy(1, size, offset, 0);
            allocatedBytes -= size;
        }

        private static int unpackSize(int packed) {
            return MIN_BUDDY_SIZE << (packed >> PACK_SIZE_SHIFT);
        }

        private static int unpackOffset(int packed) {
            return (packed & PACK_OFFSET_MASK) * MIN_BUDDY_SIZE;
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
            int capacityInFreeList = 0;
            if (!freeList.isEmpty()) {
                capacityInFreeList = freeList.weakPeekReduce(freeListCapacity, 0,
                        (sum, entry) -> sum + unpackSize(entry));
            }
            return super.remainingCapacity() + capacityInFreeList;
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

            if (PlatformDependent.isJfrEnabled() && AllocateBufferEvent.isEventEnabled()) {
                AllocateBufferEvent event = new AllocateBufferEvent();
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator.class);
                    event.chunkPooled = wrapped.pooled;
                    Magazine m = wrapped.magazine;
                    event.chunkThreadLocal = m != null && m.ownerThread != null;
                    event.commit();
                }
            }
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
            checkNewCapacity(newCapacity);
            if (length <= newCapacity && newCapacity <= maxFastCapacity) {
                length = newCapacity;
                return this;
            }
            if (newCapacity < capacity()) {
                length = newCapacity;
                trimIndicesToCapacity(newCapacity);
                return this;
            }

            if (PlatformDependent.isJfrEnabled() && ReallocateBufferEvent.isEventEnabled()) {
                ReallocateBufferEvent event = new ReallocateBufferEvent();
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator.class);
                    event.newCapacity = newCapacity;
                    event.commit();
                }
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
            assert oldCapacity < maxFastCapacity && newCapacity <= maxFastCapacity :
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
            return _memoryAddress();
        }

        @Override
        long _memoryAddress() {
            AbstractByteBuf root = rootParent;
            return root != null ? root._memoryAddress() + startIndex : 0L;
        }

        @Override
        boolean _isDirect() {
            AbstractByteBuf root = rootParent;
            return root != null && root.isDirect();
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
            rootParent()._setLongLE(idx(index), value);
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            checkIndex(index, length);
            if (tmpNioBuf == null && PlatformDependent.javaVersion() >= 13) {
                ByteBuffer dstBuffer = rootParent()._internalNioBuffer();
                PlatformDependent.absolutePut(dstBuffer, idx(index), src, srcIndex, length);
            } else {
                ByteBuffer tmp = (ByteBuffer) internalNioBuffer().clear().position(index);
                tmp.put(src, srcIndex, length);
            }
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            checkIndex(index, length);
            if (src instanceof AdaptiveByteBuf && PlatformDependent.javaVersion() >= 16) {
                AdaptiveByteBuf srcBuf = (AdaptiveByteBuf) src;
                srcBuf.checkIndex(srcIndex, length);
                ByteBuffer dstBuffer = rootParent()._internalNioBuffer();
                ByteBuffer srcBuffer = srcBuf.rootParent()._internalNioBuffer();
                PlatformDependent.absolutePut(dstBuffer, idx(index), srcBuffer, srcBuf.idx(srcIndex), length);
            } else {
                ByteBuffer tmp = internalNioBuffer();
                tmp.position(index);
                tmp.put(src.nioBuffer(srcIndex, length));
            }
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            int length = src.remaining();
            checkIndex(index, length);
            ByteBuffer tmp = internalNioBuffer();
            if (PlatformDependent.javaVersion() >= 16) {
                int offset = src.position();
                PlatformDependent.absolutePut(tmp, index, src, offset, length);
                src.position(offset + length);
            } else {
                tmp.position(index);
                tmp.put(src);
            }
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length)
                throws IOException {
            checkIndex(index, length);
            if (length != 0) {
                ByteBuffer tmp = internalNioBuffer();
                ByteBufUtil.readBytes(alloc(), tmp.hasArray() ? tmp : tmp.duplicate(), index, length, out);
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
            if (PlatformDependent.isJfrEnabled() && FreeBufferEvent.isEventEnabled()) {
                FreeBufferEvent event = new FreeBufferEvent();
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator.class);
                    event.commit();
                }
            }

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
         *
         * @param initialCapacity The initial capacity of the returned {@link AbstractByteBuf}.
         * @param maxCapacity     The maximum capacity of the returned {@link AbstractByteBuf}.
         * @return The buffer that represents the chunk memory.
         */
        AbstractByteBuf allocate(int initialCapacity, int maxCapacity);
    }
}
