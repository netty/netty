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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
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
     * The capacity of the buddy chunk cache (large buffer reuse).
     */
    static final int CHUNK_REUSE_QUEUE = Math.max(2, SystemPropertyUtil.getInt(
            "io.netty.allocator.chunkReuseQueueCapacity", NettyRuntime.availableProcessors() * 2));

    static final long CHUNK_PURGE_POLLS_THREAD_LOCAL = Math.max(1, SystemPropertyUtil.getLong(
            "io.netty.allocator.chunkPurgePollsThreadLocal", 4L));

    /**
     * Derivation basis for the per-size-class retention floor. No longer enforced as a cap.
     */
    static final int THREAD_LOCAL_CACHE_MAX_BYTES = Math.max(1, SystemPropertyUtil.getInt(
            "io.netty.allocator.threadLocalChunkCacheMaxBytes", 8 * 1024 * 1024));

    /**
     * Per-size-class retention floor (in bytes) on the chunk cache.
     * Chunks below this floor are kept cached to avoid hysteresis.
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

        void forEach(Consumer<T> action) {
            for (int i = 0; i < size; i++) {
                @SuppressWarnings("unchecked")
                T element = (T) elements[i];
                action.accept(element);
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
                pool.forEach(AbstractByteBuf::release);
            }
            Arrays.fill(freelistSlots, null);
            Arrays.fill(localFreelistSlots, null);
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
            Magazine mag = new Magazine(allocator, strategy, chunkRecycler, sizeClassIndex, null, recycler, lock,
                    magazines);
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
                if (mag.allocate(size, maxCapacity, buf)) {
                    if (mag.purgeFired && sizeClassIndex < SIZE_CLASSES_COUNT) {
                        mag.purgeFired = false;
                        stripeWidePurge(sizeClassIndex);
                    }
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

        private void stripeWidePurge(int triggeringSizeClassIndex) {
            if (magazines != null) {
                purgeSiblingCaches(magazines, triggeringSizeClassIndex);
            }
        }
    }

    private static void purgeSiblingCaches(Magazine[] magazines, int triggeringSizeClassIndex) {
        for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
            if (i != triggeringSizeClassIndex) {
                Magazine sibling = magazines[i];
                if (sibling != null) {
                    sibling.tickCachePurge();
                }
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
            boolean success = mag.allocate(size, maxCapacity, buf);
            assert success : "Thread-local allocation must always succeed";
            if (mag.purgeFired) {
                mag.purgeFired = false;
                stripeWidePurge(sizeClassIndex);
            }
            return buf;
        }

        private void stripeWidePurge(int triggeringSizeClassIndex) {
            purgeSiblingCaches(magazines, triggeringSizeClassIndex);
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
            Magazine mag = new Magazine(allocator, strategy, chunkRecycler, sizeClassIndex,
                                       Thread.currentThread(), null, null, magazines);
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

    abstract static class SizeClassedChunkCache implements ChunkCache {
        @Override
        public abstract SizeClassedChunk pollChunk(int size);

        // Visible for testing: triggers a purge scan bypassing the budget counter.
        abstract SizeClassedChunk forcePurge();
    }

    /**
     * Two-list chunk cache: answers "give me a chunk to carve from" and "release what is idle".
     *
     * <p><b>Access.</b> The lists, the counters and {@code cacheListState} are touched only by the
     * owner thread (thread-local magazines) or under the stripe write lock (shared magazines) —
     * one magazine's caches all share that one lock. The only exception is {@link #pendingHead},
     * which any releasing thread may push to; it is the sole concurrent structure here.
     *
     * <p><b>The two lists.</b>
     * <ul>
     *   <li><b>Reusable</b> — chunks known to have free segments. {@link #pollChunk} takes the
     *       head, O(1). Fully-free chunks at or below the retention floor stay here rather than
     *       being evicted, so a burst does not have to re-allocate immediately after draining.</li>
     *   <li><b>Exhausted</b> — chunks with no free segments when they were filed. Primarily an
     *       ownership registry: it keeps chunks reachable for {@link #free()} and gives the
     *       notification drain somewhere to move a chunk out of. It is <em>not</em> the discovery
     *       mechanism, and is never walked.</li>
     * </ul>
     *
     * <p><b>Why the reusable list is trustworthy.</b> A cached chunk can only <em>gain</em>
     * capacity: segments are handed out only by {@code readInitInto} on a magazine's chunk, and
     * {@link #pollChunk} removes a chunk from the cache before it is attached to a magazine. So a
     * chunk filed with capacity still has it, and the head of the reusable list is always usable.
     *
     * <p><b>Why the exhausted list is not.</b> {@code offerChunk} files a chunk by reading its
     * capacity, and a cross-thread return landing just after that read leaves it filed as exhausted
     * while it actually has capacity. Monotonicity does not help here — it says the reusable list
     * is pure, not that the exhausted list is.
     *
     * <p><b>Three routes move a chunk back to reusable</b>, all driven by the one event that can
     * change a chunk's occupancy, a segment return:
     * <ol>
     *   <li><b>Inline</b>, when the returning thread can synchronise — it is the owner thread, or it
     *       won the stripe lock. Plain field reads and pointer writes, no atomics. Signal A
     *       (exhausted → reusable, mimalloc's {@code pageUnfull}) and Signal B (fully free →
     *       evicted above the floor, mimalloc's {@code pageRetire}).</li>
     *   <li><b>Deferred</b>, when it cannot: {@link #notifyHasCapacity} leaves a note and
     *       {@link #drainPending} applies the transition under the lock. See Invariant N.</li>
     *   <li><b>Probed</b>, as a last resort: {@link #probeExhausted()} looks at a bounded number of
     *       exhausted chunks when the reusable list is empty, because a note pushed concurrently
     *       with the drain has not been applied yet. Without it the caller would allocate a fresh
     *       chunk while a usable one sat in the exhausted list.</li>
     * </ol>
     * Routes 2 and 3 overlap deliberately, as they do in mimalloc: notifications reach chunks a
     * bounded scan would not, and a bounded scan covers what notifications are late for.
     *
     * <p>There is deliberately no periodic sweep of the exhausted list. A note is never dropped -
     * {@link #drainPending} re-arms a chunk's link before processing it, so a return that lands
     * mid-processing queues the chunk again rather than being swallowed - so a sweep could only ever
     * find a chunk whose notification was lost, which is a bug in this protocol and not something a
     * periodic rescue should paper over. mimalloc reasons the same way: its collect walks the page
     * queues but deliberately stops one bin short of {@code pages_full}, because the free that would
     * un-full a page cannot be lost either.
     *
     * <p><b>Eviction only ever operates on the reusable list</b> — {@link #evictIfAboveFloor} calls
     * {@code removeFromReusable} unconditionally, and every caller either walks {@code reusableHead}
     * or moves the chunk there first. So an exhausted-list chunk never has its free lists stripped,
     * and {@link #probeExhausted()} cannot encounter one that does.
     *
     * <p>Note that route 3 can hand out a chunk that route 2 would have evicted. That is intended:
     * reusing a fully-free chunk beats evicting it and allocating a fresh one. mimalloc makes the
     * same trade, cancelling a page's retirement when a scan selects it.
     *
     * <p><b>No cap.</b> {@code offerChunk} always returns true; cache size follows the working set,
     * and idle chunks leave via Signal B rather than a byte threshold. Evicted buffers go to the
     * {@link SizeClassChunkRecycler}, which every size class on the heap draws from.
     */
    static final class ThreadLocalSizeClassedChunkCache extends SizeClassedChunkCache {
        private static final AtomicReferenceFieldUpdater<ThreadLocalSizeClassedChunkCache, SizeClassedChunk>
                PENDING_HEAD = AtomicReferenceFieldUpdater.newUpdater(
                        ThreadLocalSizeClassedChunkCache.class, SizeClassedChunk.class, "pendingHead");

        /** Bound on the last-resort probe of the exhausted list; see {@link #probeExhausted()}. */
        private static final int MAX_EXHAUSTED_PROBE = 8;

        SizeClassedChunk exhaustedHead;
        SizeClassedChunk reusableHead;
        /** Treiber stack of chunks that a releasing thread asked us to look at. */
        private volatile SizeClassedChunk pendingHead;
        int exhaustedCount;
        int reusableCount;

        SizeClassChunkRecycler chunkRecycler;
        int sizeClassIndex;
        final int purgeRetentionFloor;
        final StampedLock stripeLock; // null for thread-local, non-null for shared stripes

        ThreadLocalSizeClassedChunkCache(int chunkSize, SizeClassChunkRecycler chunkRecycler, int sizeClassIndex) {
            this(chunkSize, chunkRecycler, sizeClassIndex, null);
        }

        ThreadLocalSizeClassedChunkCache(int chunkSize, SizeClassChunkRecycler chunkRecycler,
                                         int sizeClassIndex, StampedLock stripeLock) {
            this.chunkRecycler = chunkRecycler;
            this.sizeClassIndex = sizeClassIndex;
            this.stripeLock = stripeLock;
            purgeRetentionFloor = Math.max(1, THREAD_LOCAL_CACHE_MIN_BYTES / chunkSize);
        }

        private int totalCount() {
            return exhaustedCount + reusableCount;
        }

        // --- Intrusive doubly-linked list operations ---

        private void addToExhausted(SizeClassedChunk chunk) {
            chunk.cacheListState = SizeClassedChunk.CACHE_EXHAUSTED;
            chunk.prevInCache = null;
            chunk.nextInCache = exhaustedHead;
            if (exhaustedHead != null) {
                exhaustedHead.prevInCache = chunk;
            }
            exhaustedHead = chunk;
            exhaustedCount++;
        }

        private void addToReusable(SizeClassedChunk chunk) {
            chunk.cacheListState = SizeClassedChunk.CACHE_REUSABLE;
            chunk.prevInCache = null;
            chunk.nextInCache = reusableHead;
            if (reusableHead != null) {
                reusableHead.prevInCache = chunk;
            }
            reusableHead = chunk;
            reusableCount++;
        }

        private void removeFromExhausted(SizeClassedChunk chunk) {
            if (chunk.prevInCache != null) {
                chunk.prevInCache.nextInCache = chunk.nextInCache;
            } else {
                exhaustedHead = chunk.nextInCache;
            }
            if (chunk.nextInCache != null) {
                chunk.nextInCache.prevInCache = chunk.prevInCache;
            }
            chunk.prevInCache = null;
            chunk.nextInCache = null;
            exhaustedCount--;
        }

        private void removeFromReusable(SizeClassedChunk chunk) {
            if (chunk.prevInCache != null) {
                chunk.prevInCache.nextInCache = chunk.nextInCache;
            } else {
                reusableHead = chunk.nextInCache;
            }
            if (chunk.nextInCache != null) {
                chunk.nextInCache.prevInCache = chunk.prevInCache;
            }
            chunk.prevInCache = null;
            chunk.nextInCache = null;
            reusableCount--;
        }

        private void detachFromCache(SizeClassedChunk chunk) {
            chunk.cacheListState = SizeClassedChunk.CACHE_NONE;
        }

        // Called from releaseSegment (Signal A): exhausted → reusable
        void moveToReusable(SizeClassedChunk chunk) {
            removeFromExhausted(chunk);
            addToReusable(chunk);
        }

        void evictIfAboveFloor(SizeClassedChunk chunk) {
            if (chunk.hasFullCapacity() && totalCount() > purgeRetentionFloor) {
                removeFromReusable(chunk);
                detachFromCache(chunk);
                chunk.recycleOrDeallocate(chunkRecycler, sizeClassIndex);
            }
        }

        // --- Notification queue: cross-thread segment returns that could not take the lock ---
        //
        // Invariant N (notification completeness): every segment return is either observed by a later
        // cache decision about that chunk, or leaves an outstanding note that is processed after that
        // decision. Nothing scans, so a lost signal means a chunk with capacity sits on the exhausted
        // list forever -- never reusable, and never fully free either, so the purge sweep will not
        // evict it. Four properties carry the invariant, and all four must hold:
        //
        //  1. Offer before notify. releaseSegment puts the segment in the MPSC free list first, so a
        //     drainer that pops the note is guaranteed to see the segment.
        //  2. Notes are state-independent: "look at this chunk", never "this specific thing changed".
        //     One note therefore covers any number of later returns, and a note left while the chunk
        //     was still CACHE_NONE stays correct once the chunk is classified. Do not optimise the
        //     note to carry state. This is also why a releaser that finds the claim already taken can
        //     simply walk away: the in-flight note covers its return too.
        //  3. Re-arm before processing (see drainPending).
        //  4. Classification and drain cannot interleave. offerChunk's (read capacity, insert) pair
        //     and the drain both run under the same stripe lock, or on the same owner thread. This is
        //     what covers a return landing right after offerChunk read the capacity but before the
        //     insert: the chunk is filed as exhausted while holding capacity, and the note -- which
        //     cannot be consumed in between -- is what fixes it.
        //
        // A drain that finds CACHE_NONE and no-ops is benign, not a lost signal: the chunk is in a
        // magazine, which consumes its own returned segments through nextAvailableSegmentOffset.

        /**
         * Queue {@code chunk} for the next drain. Called by a releasing thread that holds no lock,
         * <em>after</em> the segment has been offered to the chunk's external free list, so a drainer
         * that pops the note is guaranteed to also see the segment.
         *
         * <p>This path must never read {@code cacheListState} or any list link: those belong to the
         * owner thread / stripe lock holder. The note only says "look at this chunk". The chunk finds
         * this cache through its {@code final owningCache} field, so no racy reference read is involved.
         *
         * <p>{@link SizeClassedChunk#pendingNext} doubles as the dedup claim, so a return on a chunk
         * that is already queued costs a single volatile read.
         */
        void notifyHasCapacity(SizeClassedChunk chunk) {
            if (chunk.pendingNext != null) {
                return;
            }
            final SizeClassedChunk sentinel = SizeClassedChunk.PENDING_SENTINEL;
            // Claim: only the thread that moves the link off null owns the push.
            if (!SizeClassedChunk.PENDING_NEXT.compareAndSet(chunk, null, sentinel)) {
                return;
            }
            SizeClassedChunk head;
            do {
                head = pendingHead;
                SizeClassedChunk.PENDING_NEXT.lazySet(chunk, head == null ? sentinel : head);
            } while (!PENDING_HEAD.compareAndSet(this, head, chunk));
        }

        /**
         * Apply every queued notification. Caller must hold the stripe lock, or be the owner thread of
         * a thread-local cache.
         */
        void drainPending() {
            if (pendingHead == null) {
                // Cheap when there is nothing to do: one volatile read, no atomic RMW. The heap-wide
                // drain pays this per size class, so it has to stay a plain read.
                return;
            }
            SizeClassedChunk cur = PENDING_HEAD.getAndSet(this, null);
            final SizeClassedChunk sentinel = SizeClassedChunk.PENDING_SENTINEL;
            while (cur != null && cur != sentinel) {
                SizeClassedChunk next = cur.pendingNext;
                // Re-arm BEFORE processing. A return that lands while we are inside processPending must
                // be able to queue the chunk again; re-arming afterwards would lose it and strand the
                // chunk until some later, unrelated notification.
                //
                // This is a full volatile store on purpose, not a lazySet: it is the store half of a
                // Dekker pair with the releaser, which offers the segment (MPSC offer ends in a CAS on
                // the producer index, so a StoreLoad) and only then reads pendingNext. processPending
                // reads the free lists right after this store; without the StoreLoad here both sides
                // could miss each other and the chunk would be stranded.
                SizeClassedChunk.PENDING_NEXT.set(cur, null);
                processPending(cur);
                cur = next == sentinel ? null : next;
            }
        }

        // Visible for testing: how many chunks are queued for the next drain.
        int pendingCount() {
            int count = 0;
            SizeClassedChunk cur = pendingHead;
            while (cur != null && cur != SizeClassedChunk.PENDING_SENTINEL) {
                count++;
                cur = cur.pendingNext;
            }
            return count;
        }

        private void processPending(SizeClassedChunk chunk) {
            int cls = chunk.cacheListState;
            if (cls == SizeClassedChunk.CACHE_NONE) {
                // Attached to a magazine, already polled, or gone: not ours to move. Checked first,
                // because such a chunk may have had its free lists stripped by recycleOrDeallocate.
                return;
            }
            if (cls == SizeClassedChunk.CACHE_EXHAUSTED && chunk.hasRemainingCapacity()) {
                moveToReusable(chunk);
            }
            if (chunk.cacheListState == SizeClassedChunk.CACHE_REUSABLE) {
                evictIfAboveFloor(chunk);
            }
        }

        /**
         * Try to take exclusive access to this cache so a releasing thread can place a segment
         * and apply any resulting list transition. Returns 0 when unavailable (thread-local
         * caches have no lock, and a contended stripe lock is not waited on).
         */
        long tryLockForRelease() {
            return stripeLock == null ? 0 : stripeLock.tryWriteLock();
        }

        void unlockAfterRelease(long stamp) {
            stripeLock.unlockWrite(stamp);
        }

        /**
         * Apply the list transition implied by a segment return. Caller must hold the stamp
         * from {@link #tryLockForRelease()} and must have already placed the segment.
         */
        void transitionAfterRelease(SizeClassedChunk chunk, int cls) {
            if (cls == SizeClassedChunk.CACHE_EXHAUSTED) {
                moveToReusable(chunk);
            }
            evictIfAboveFloor(chunk);
        }

        @Override
        SizeClassedChunk forcePurge() {
            tickPurge();
            return pollChunkInternal();
        }

        @Override
        public SizeClassedChunk pollChunk(int size) {
            // Slow-path only (once per chunk-worth of allocations), which is exactly where a chunk is
            // wanted. Draining per allocation is what made the old notification cache expensive.
            drainPending();
            return pollChunkInternal();
        }

        /**
         * O(1) and unconditional: every chunk on the reusable list has capacity, and keeps it for as
         * long as it stays cached (nothing allocates out of a cached chunk, so its capacity can only
         * grow). The exhausted list is never searched — a chunk leaves it only when a notification
         * says it gained capacity.
         */
        private SizeClassedChunk pollChunkInternal() {
            if (reusableHead != null) {
                SizeClassedChunk chunk = reusableHead;
                removeFromReusable(chunk);
                detachFromCache(chunk);
                return chunk;
            }
            return probeExhausted();
        }

        /**
         * Last resort before the caller allocates a fresh chunk: look at a bounded number of
         * exhausted chunks in case one regained capacity from a return whose notification has not
         * been drained yet.
         *
         * <p>An empty reusable list means "no usable chunk is <em>known</em>", not "none exists".
         * {@code drainPending} runs immediately before the poll, so it catches every note pushed
         * before its {@code getAndSet} - but a note pushed concurrently with the drain, or by a
         * releaser that has claimed its link and not yet published it, is not seen. Without this
         * probe the caller would allocate a new chunk while a usable one sat in the exhausted list,
         * which is the chunk-count growth this cache exists to avoid.
         *
         * <p>mimalloc does the same and for the same reason: {@code findFreePage} calls
         * {@code pageFreeCollect} on the queue head before its fast path, and
         * {@code pageQueueFindFreeEx} calls it on every page it visits, bounded by
         * {@code MAX_PAGE_CANDIDATE_SEARCH}. Notifications cover what a scan cannot reach; a
         * bounded scan covers what notifications are late for.
         *
         * <p>Bounded by chunks <em>visited</em>, not by anything found - a bound on work done is
         * the only kind that holds when nothing matches.
         */
        private SizeClassedChunk probeExhausted() {
            SizeClassedChunk cur = exhaustedHead;
            int visited = 0;
            while (cur != null && visited < MAX_EXHAUSTED_PROBE) {
                SizeClassedChunk next = cur.nextInCache;
                visited++;
                if (cur.hasRemainingCapacity()) {
                    removeFromExhausted(cur);
                    detachFromCache(cur);
                    return cur;
                }
                cur = next;
            }
            return null;
        }

        @Override
        public void tickPurge() {
            drainPending();
            // Exhausted→reusable is applied by the drain above. All that is left is evicting
            // fully-free reusable chunks above the retention floor.
            int total = totalCount();
            SizeClassedChunk cur = reusableHead;
            while (cur != null && total > purgeRetentionFloor) {
                SizeClassedChunk next = cur.nextInCache;
                if (cur.hasFullCapacity()) {
                    removeFromReusable(cur);
                    detachFromCache(cur);
                    cur.recycleOrDeallocate(chunkRecycler, sizeClassIndex);
                    total--;
                }
                cur = next;
            }
        }

        @Override
        public boolean offerChunk(Chunk chunk) {
            SizeClassedChunk sc = (SizeClassedChunk) chunk;
            if (sc.hasRemainingCapacity()) {
                addToReusable(sc);
            } else {
                addToExhausted(sc);
            }
            return true;
        }

        @Override
        public void free() {
            // Drop any outstanding notes: every chunk they point at is about to be marked for
            // deallocation, and this cache is dead afterwards.
            PENDING_HEAD.lazySet(this, null);
            freeList(exhaustedHead);
            exhaustedHead = null;
            exhaustedCount = 0;
            freeList(reusableHead);
            reusableHead = null;
            reusableCount = 0;
        }

        private static void freeList(SizeClassedChunk head) {
            SizeClassedChunk cur = head;
            while (cur != null) {
                SizeClassedChunk next = cur.nextInCache;
                cur.cacheListState = SizeClassedChunk.CACHE_NONE;
                cur.prevInCache = null;
                cur.nextInCache = null;
                cur.markToDeallocate();
                cur = next;
            }
        }

        @Override
        public boolean isEmpty() {
            return totalCount() == 0;
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
        int computeBufferCapacity(int requestedSize, int maxCapacity);

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

        ChunkCache createChunkCache(SizeClassChunkRecycler chunkRecycler, int sizeClassIndex,
                                    StampedLock stripeLock) {
            return new ThreadLocalSizeClassedChunkCache(chunkSize, chunkRecycler, sizeClassIndex, stripeLock);
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

        private IntStack createEmptyLocalFreeList() {
            final int segmentsCount = chunkSize / segmentSize;
            int[] offsets = new int[segmentsCount];
            return new IntStack(offsets, -1);
        }

        @Override
        public int computeBufferCapacity(int requestedSize, int maxCapacity) {
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
        public int computeBufferCapacity(int requestedSize, int maxCapacity) {
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
        /**
         * Every size-classed magazine of the heap this magazine belongs to, including this one, or
         * {@code null} for the buddy magazine. The whole array is covered by the one lock (shared
         * stripe) or the one owner thread (thread-local heap) that guards this magazine, which is
         * what makes the heap-wide drain legal from here.
         */
        private final Magazine[] heapMagazines;
        final int sizeClassIndex;
        final SizeClassChunkRecycler chunkRecycler;
        final AdaptiveRecycler bufRecycler; // for ByteBuf wrapper pooling; null → EVENT_LOOP_LOCAL_BUFFER_POOL
        private final int purgeTickThreshold;
        private int allocCount;
        boolean purgeFired;

        // Size-classed magazine constructor (both thread-local and shared-stripe)
        Magazine(AdaptivePoolingAllocator allocator, SizeClassChunkManagementStrategy strategy,
                 SizeClassChunkRecycler chunkRecycler, int sizeClassIndex,
                 Thread ownerThread, AdaptiveRecycler bufRecycler, StampedLock stripeLock,
                 Magazine[] heapMagazines) {
            this.heapMagazines = heapMagazines;
            this.allocator = allocator;
            this.ownerThread = ownerThread;
            this.sizeClassIndex = sizeClassIndex;
            this.chunkRecycler = chunkRecycler;
            this.bufRecycler = bufRecycler;
            this.chunkController = strategy.createController(allocator);
            this.chunkCache = strategy.createChunkCache(chunkRecycler, sizeClassIndex, stripeLock);
            this.purgeTickThreshold = (int) Math.min(Integer.MAX_VALUE,
                    CHUNK_PURGE_POLLS_THREAD_LOCAL * (strategy.chunkSize / strategy.segmentSize));
        }

        // Buddy (large buffer) magazine constructor
        Magazine(AdaptivePoolingAllocator allocator,
                 BuddyChunkManagementStrategy strategy, AdaptiveRecycler bufRecycler) {
            this.heapMagazines = null;
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
                purgeFired = true;
            }
        }

        void tickCachePurge() {
            chunkCache.tickPurge();
        }

        /**
         * Apply the notifications left by releasers on every size class of this heap, not just this
         * magazine's. A size class that has gone idle stops allocating, so it would never drain its
         * own notes — and those are exactly the chunks worth reclaiming, because their backing
         * buffers go to the {@link SizeClassChunkRecycler} that every size class draws from.
         *
         * <p>Called on the allocation slow path only, right before {@link ChunkCache#pollChunk},
         * which is once per chunk-worth of allocations.
         */
        private void drainHeapPending() {
            Magazine[] mags = heapMagazines;
            if (mags == null) {
                return;
            }
            for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
                Magazine mag = mags[i];
                if (mag != null) {
                    ((ThreadLocalSizeClassedChunkCache) mag.chunkCache).drainPending();
                }
            }
        }

        boolean allocate(int size, int maxCapacity, AdaptiveByteBuf buf) {
            int startingCapacity = chunkController.computeBufferCapacity(size, maxCapacity);
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
            return allocateSlow(size, maxCapacity, buf, startingCapacity);
        }

        /**
         * The current chunk (if any) had no room. Try the next-in-line chunk, then the cache, then
         * fall back to allocating a fresh chunk. Whichever chunk ends up serving the allocation is
         * stashed in {@link #current}, "reserving" it for this magazine's exclusive use.
         */
        private boolean allocateSlow(int size, int maxCapacity, AdaptiveByteBuf buf, int startingCapacity) {
            assert current == null;
            Chunk curr = nextInLine;
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
            drainHeapPending();
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
            // Constructor only used by sentinel instances (MAGAZINE_FREED, PENDING_SENTINEL).
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
            if (delegate != null) {
                // Only when the buffer is actually being freed. recycleOrDeallocate hands the
                // buffer to SizeClassChunkRecycler and nulls the field, and a FreeChunk event for
                // that chunk would be wrong twice over: the memory has not been freed, it has been
                // pooled for another size class to pick up, and AbstractChunkEvent.fill reads
                // isDirect()/memoryAddress(), which dereference the delegate.
                onRelease();
                allocator.chunkRegistry.remove(this);
                delegate.release();
            } else {
                allocator.chunkRegistry.remove(this);
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

        IntStack(int[] backingArray, int initialTop) {
            stack = backingArray;
            top = initialTop;
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

        // Intrusive doubly-linked list pointers for cache membership
        static final int CACHE_NONE = 0;
        static final int CACHE_EXHAUSTED = 1;
        static final int CACHE_REUSABLE = 2;
        SizeClassedChunk prevInCache;
        SizeClassedChunk nextInCache;
        int cacheListState;
        final ThreadLocalSizeClassedChunkCache owningCache;

        // --- Pending-notification link (see ThreadLocalSizeClassedChunkCache#notifyHasCapacity) ---

        /**
         * Marks the end of the pending-notification list, so that {@code null} can keep its meaning of
         * "not queued". Never a usable chunk.
         */
        static final SizeClassedChunk PENDING_SENTINEL = new SizeClassedChunk();
        static final AtomicReferenceFieldUpdater<SizeClassedChunk, SizeClassedChunk> PENDING_NEXT =
                AtomicReferenceFieldUpdater.newUpdater(
                        SizeClassedChunk.class, SizeClassedChunk.class, "pendingNext");
        /**
         * {@code null} = not queued for attention, non-null = queued (or in the middle of being queued).
         * This field <em>is</em> the dedup claim: whoever moves it off {@code null} owns the push, so no
         * separate flag is needed.
         */
        volatile SizeClassedChunk pendingNext;

        /**
         * Constructor only used by {@link #PENDING_SENTINEL}.
         */
        private SizeClassedChunk() {
            segmentSize = 0;
            segments = 0;
            ownerThread = null;
            owningCache = null;
        }

        SizeClassedChunk(AbstractByteBuf delegate, Magazine magazine,
                         SizeClassChunkController controller) {
            super(delegate, magazine, true);
            segmentSize = controller.segmentSize;
            segments = controller.chunkSize / segmentSize;
            STATE.lazySet(this, AVAILABLE);
            ownerThread = magazine.ownerThread;
            owningCache = (ThreadLocalSizeClassedChunkCache) magazine.chunkCache;
            if (ownerThread == null) {
                externalFreeList = controller.createFreeList();
                localFreeList = controller.createEmptyLocalFreeList();
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
            owningCache = (ThreadLocalSizeClassedChunkCache) magazine.chunkCache;
            if (ownerThread != null) {
                if (recycledLocalFreeList != null && recycledLocalFreeList.capacity() >= segments) {
                    localFreeList = recycledLocalFreeList;
                    localFreeList.refill(segments, segmentSize);
                } else {
                    localFreeList = controller.createLocalFreeList();
                }
                recycledFreeList.resetAndFill(0, segmentSize);
            } else {
                if (recycledLocalFreeList != null && recycledLocalFreeList.capacity() >= segments) {
                    localFreeList = recycledLocalFreeList;
                    localFreeList.refill(0, segmentSize);
                } else {
                    localFreeList = controller.createEmptyLocalFreeList();
                }
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
            IntStack localFreeList = this.localFreeList;
            if (!localFreeList.isEmpty()) {
                return localFreeList.pop();
            }
            return externalFreeList.poll();
        }

        // this can be used by the ConcurrentQueueChunkCache to find the first buffer to use:
        // it doesn't update the remaining capacity and it's not consider a single segmentSize
        // case as not suitable to be reused
        public boolean hasRemainingCapacity() {
            int remaining = super.remainingCapacity();
            if (remaining > 0) {
                return true;
            }
            return !localFreeList.isEmpty() || !externalFreeList.isEmpty();
        }

        boolean hasFullCapacity() {
            int localSize = localFreeList.size();
            return localSize == segments || localSize + externalFreeList.size() == segments;
        }

        @Override
        public int remainingCapacity() {
            int remaining = super.remainingCapacity();
            return remaining > segmentSize ? remaining : updateRemainingCapacity(remaining);
        }

        private int updateRemainingCapacity(int snapshotted) {
            int freeSegments = externalFreeList.size() + localFreeList.size();
            int updated = freeSegments * segmentSize;
            if (updated != snapshotted) {
                allocatedBytes = capacity() - updated;
            }
            return updated;
        }

        private void releaseSegmentOffsetIntoFreeList(int startIndex) {
            if (ownerThread != null && Thread.currentThread() == ownerThread) {
                localFreeList.push(startIndex);
            } else {
                boolean segmentReturned = externalFreeList.offer(startIndex);
                assert segmentReturned : "Unable to return segment " + startIndex + " to free list";
            }
        }

        @Override
        void releaseSegment(int startIndex, int size) {
            if (ownerThread != null && Thread.currentThread() == ownerThread) {
                localFreeList.push(startIndex);
                afterLocalRelease();
            } else {
                final ThreadLocalSizeClassedChunkCache cache = owningCache;
                final long stamp = cache.tryLockForRelease();
                if (stamp != 0) {
                    try {
                        localFreeList.push(startIndex);
                        afterLockedRelease(cache);
                    } finally {
                        cache.unlockAfterRelease(stamp);
                    }
                } else {
                    boolean segmentReturned = externalFreeList.offer(startIndex);
                    assert segmentReturned;
                    // implicit StoreLoad barrier from MPSC offer()
                    int state = this.state;
                    if (state != AVAILABLE) {
                        deallocateIfNeeded(state);
                    } else {
                        // The chunk just gained capacity but we could not take the lock to apply the
                        // resulting list transition. Leave a note instead; the next drain applies it.
                        // A chunk whose state is not AVAILABLE is never on a cache list, so there is
                        // nothing to notify about on that branch.
                        cache.notifyHasCapacity(this);
                    }
                }
            }
        }

        /**
         * Cold: apply the deallocation bookkeeping or cache-list transition implied by a segment
         * returned by the owner thread. Split out of {@link #releaseSegment} so the common case —
         * push the segment, find nothing else to do — stays a few lines.
         */
        private void afterLocalRelease() {
            int state = this.state;
            if (state != AVAILABLE) {
                updateStateOnLocalReleaseSegment(state);
                return;
            }
            int cls = cacheListState;
            if (cls != CACHE_NONE) {
                detectCacheTransition(cls);
            }
        }

        /** Locked counterpart of {@link #afterLocalRelease()}; caller holds the stripe lock. */
        private void afterLockedRelease(ThreadLocalSizeClassedChunkCache cache) {
            int state = this.state;
            if (state != AVAILABLE) {
                updateStateOnLockedReleaseSegment(state);
                return;
            }
            int cls = cacheListState;
            if (cls != CACHE_NONE) {
                cache.transitionAfterRelease(this, cls);
            }
        }

        private void detectCacheTransition(int cls) {
            if (cls == CACHE_EXHAUSTED) {
                owningCache.moveToReusable(this);
                if (hasFullCapacity()) {
                    owningCache.evictIfAboveFloor(this);
                }
            } else if (cls == CACHE_REUSABLE && hasFullCapacity()) {
                owningCache.evictIfAboveFloor(this);
            }
        }

        /**
         * Deallocation accounting for a segment placed into {@link #localFreeList} while holding
         * the stripe lock. Unlike the owner-thread variant, {@code state} may be concurrently
         * advanced to {@link #DEALLOCATED} by a releaser on the lock-free MPSC path, so the
         * update is a CAS loop rather than an unconditional CAS.
         */
        private void updateStateOnLockedReleaseSegment(int observedState) {
            int st = observedState;
            while (st != DEALLOCATED) {
                // Safe under the stripe lock: only lock holders mutate localFreeList.
                int newLocalSize = localFreeList.size();
                if (STATE.compareAndSet(this, st, newLocalSize)) {
                    deallocateIfNeeded(newLocalSize);
                    return;
                }
                st = state;
            }
        }

        private void updateStateOnLocalReleaseSegment(int previousLocalSize) {
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
