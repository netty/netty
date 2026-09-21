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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntConsumer;

/**
 * A pooling allocator that follows an anti-generational hypothesis: buffers are expected to die young, so the memory
 * behind them is kept close to the thread that allocated it and handed out again as soon as it comes back.
 * <p>
 * Memory is held in chunks, and a buffer is a range of one chunk. Which chunk serves a request depends on its size:
 * <ul>
 *   <li><b>Up to the largest size class</b> ({@link #SIZE_CLASSES}): a {@link SizeClassedChunk}, cut into equal
 *       segments of one size class. Its {@link SizeClassMagazine} allocates from one chunk at a time and keeps the
 *       others in a {@link SizeClassedChunkCache}, which files them by whether they have a free segment.</li>
 *   <li><b>Above it, up to {@link #MAX_POOLED_BUF_SIZE}</b>: a power-of-two block of a {@link BuddyChunk}, carved by a
 *       {@link BuddyTree}. Its {@link BuddyMagazine} files the chunks it is not allocating from by the size of their
 *       largest free block, so a request takes a block from the chunk with the most room.</li>
 *   <li><b>Larger still</b>: a one-shot {@link BuddyChunk} with no tree, holding that buffer alone and freed with
 *       it.</li>
 * </ul>
 * <p>
 * The magazines are grouped into {@link StripedHeap}s, each guarded by one lock, and a thread picks a stripe by its
 * id; more stripes are used when threads collide on the lock. A {@link FastThreadLocalThread} instead gets a
 * {@link ThreadLocalSizeClassHeap} of its own for the size classes, which needs no lock at all; buffers above the
 * size classes always come from a stripe.
 * <p>
 * A buffer released by the thread that owns its chunk is returned to it directly. A buffer released by any other
 * thread puts its segment or block on the chunk's lock-free free list and leaves a note for the owner, which applies
 * it on its next slow path: the chunk's own structures are only ever touched by one thread at a time.
 * <p>
 * Chunks that are given up are kept for reuse, bounded per magazine, and freed beyond that. Their buffers go to a
 * {@link SizeClassChunkRecycler} so a chunk of another size class can be built from the same memory.
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
    /**
     * To amortize activation/deactivation of chunks, a size-classed chunk holds at least this many segments.
     * We choose 32 because it seems neither too small nor too big.
     */
    private static final int MIN_SEGMENTS_PER_CHUNK = 32;
    /**
     * From this segment size up, every size class uses the chunk size of the first one of its family: 512 KiB for
     * 16, 32, 64 and 128 KiB, and 528 KiB for the four that add a header. This is mimalloc's medium page, which holds
     * 32 blocks of 16 KiB down to 4 of 128 KiB: a heap pays the same for its first buffer of any of these classes,
     * and a chunk given up by one of them is reused by the other three.
     */
    private static final int MEDIUM_SEGMENT_SIZE = 16 * 1024;
    private static final AtomicIntegerFieldUpdater<AdaptivePoolingAllocator> STRIPE_SCAN_LENGTH =
            AtomicIntegerFieldUpdater.newUpdater(AdaptivePoolingAllocator.class, "stripeScanLength");
    private static final int EXPANSION_ATTEMPTS = 3;
    private static final int MAX_STRIPES = IS_LOW_MEM ? 1 :
            MathUtil.safeFindNextPositivePowerOfTwo(NettyRuntime.availableProcessors() * 2);
    private static final int INITIAL_MAGAZINES = 1;
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
     * The capacity of each stripe's buddy chunk cache (large buffer reuse).
     */
    static final int CHUNK_REUSE_QUEUE = Math.max(2, SystemPropertyUtil.getInt(
            "io.netty.allocator.chunkReuseQueueCapacity", NettyRuntime.availableProcessors() * 2));

    /**
     * The bytes of wholly free chunks a buddy magazine keeps for reuse.
     */
    static final int BUDDY_IDLE_BYTES = Math.max(MAX_CHUNK_SIZE, SystemPropertyUtil.getInt(
            "io.netty.allocator.buddyIdleBytes", IS_LOW_MEM ? 4 * 1024 * 1024 : 32 * 1024 * 1024));

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
            32768,
            33792, // 32768 + 1024
            65536,
            67584, // 65536 + 2048
            131072,
            135168, // 131072 + 4096
    };

    private static final int SIZE_CLASSES_COUNT = SIZE_CLASSES.length;
    private static final byte[] SIZE_INDEXES = new byte[SIZE_CLASSES[SIZE_CLASSES_COUNT - 1] / 32 + 1];

    /**
     * The distinct chunk sizes of the size classes. A {@link SizeClassChunkRecycler} has one pool per entry, so a
     * chunk buffer given up by a size class is reused by every other size class with the same chunk size.
     */
    private static final int[] CHUNK_SIZES = distinctChunkSizes(SIZE_CLASSES);
    private static final int CHUNK_POOL_COUNT = CHUNK_SIZES.length;
    /**
     * Size class index to the index of its chunk size in {@link #CHUNK_SIZES}: precomputed, so that routing a chunk
     * buffer to its pool is a table lookup.
     */
    private static final byte[] SIZE_CLASS_TO_CHUNK_POOL = chunkPools(SIZE_CLASSES, CHUNK_SIZES);

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
    }

    /**
     * Largest size served by a size class in low-memory mode. Low-memory mode never pooled sizes above it (the buddy
     * path is disabled there too), so the size classes above it stay unpooled.
     */
    private static final int LOW_MEM_MAX_SIZE_CLASS = 16896;

    /** Number of size classes that are pooled: all of them, except in low-memory mode. */
    private static final int POOLED_SIZE_CLASSES_COUNT =
            IS_LOW_MEM ? sizeClassIndexOf(LOW_MEM_MAX_SIZE_CLASS) + 1 : SIZE_CLASSES_COUNT;

    private final ChunkAllocator chunkAllocator;
    private final ChunkRegistry chunkRegistry;
    private final SizeClassChunkManagementStrategy[] sizeClassStrategies;
    private final StripedHeap[] stripedHeaps;
    private volatile int stripeScanLength;
    private final BuddyChunkManagementStrategy buddyStrategy;
    private final AdaptiveRecycler fallbackRecycler;
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
        fallbackRecycler = AdaptiveRecycler.sharedWith(MAGAZINE_BUFFER_QUEUE_CAPACITY);

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
            if (index < POOLED_SIZE_CLASSES_COUNT) {
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

    /**
     * The size of the chunks of a size class: {@link #MIN_CHUNK_SIZE} up to 4 KiB segments,
     * {@link #MIN_SEGMENTS_PER_CHUNK} segments up to 16.9 KiB, and that same chunk size for the rest of the family
     * from {@link #MEDIUM_SEGMENT_SIZE} up, whose chunks hold 16, 8 and 4 segments.
     */
    static int chunkSizeOf(int segmentSize) {
        while (segmentSize >= MEDIUM_SEGMENT_SIZE << 1) {
            segmentSize >>= 1;
        }
        return Math.max(MIN_CHUNK_SIZE, segmentSize * MIN_SEGMENTS_PER_CHUNK);
    }

    /**
     * The distinct chunk sizes of {@code sizeClasses}, in order of first appearance. Nothing is assumed about the
     * order of the chunk sizes. Size classes with the same chunk size need not be adjacent.
     */
    // Visible for testing.
    static int[] distinctChunkSizes(int[] sizeClasses) {
        int[] distinct = new int[sizeClasses.length];
        int count = 0;
        for (int sizeClass : sizeClasses) {
            int chunkSize = chunkSizeOf(sizeClass);
            if (indexOf(distinct, count, chunkSize) == -1) {
                distinct[count++] = chunkSize;
            }
        }
        return Arrays.copyOf(distinct, count);
    }

    /**
     * For each of {@code sizeClasses}, the index of its chunk size in {@code chunkSizes}.
     */
    // Visible for testing.
    static byte[] chunkPools(int[] sizeClasses, int[] chunkSizes) {
        assert chunkSizes.length <= Byte.MAX_VALUE;
        byte[] pools = new byte[sizeClasses.length];
        for (int i = 0; i < pools.length; i++) {
            int pool = indexOf(chunkSizes, chunkSizes.length, chunkSizeOf(sizeClasses[i]));
            assert pool >= 0;
            pools[i] = (byte) pool;
        }
        return pools;
    }

    private static int indexOf(int[] values, int count, int value) {
        for (int i = 0; i < count; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    // Visible for testing.
    static int chunkPoolOf(int sizeClassIndex) {
        return SIZE_CLASS_TO_CHUNK_POOL[sizeClassIndex];
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
        // Create a one-shot chunk for this allocation.
        AbstractByteBuf innerChunk = chunkAllocator.allocate(size, maxCapacity);
        BuddyChunk chunk = new BuddyChunk(innerChunk, this);
        chunkRegistry.add(chunk);
        try {
            chunk.readInitOneShot(buf, size, maxCapacity);
        } finally {
            // Drop the reference the chunk got at construction: readInitOneShot(...) took one for the buffer
            // when successful, so the chunk and its innerChunk are freed when the AdaptiveByteBuf is released.
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
    }

    /**
     * Per-heap pool of chunk buffers that were fully free when their chunk was evicted, kept for the next
     * chunk of the same chunk size on this heap. A buffer travels with the two free lists of the chunk
     * that owned it, so re-creating a chunk from the pool only allocates the chunk object: the lists have
     * the lifetime and count of the buffers, and the pool is sized by a single number.
     * <p>
     * Pools are keyed by chunk size, not size class: the size classes up to 4 KiB share one chunk size, and a
     * buffer freed by one of them serves the next. The free lists are sized for the class that freed them; the
     * recycling {@link SizeClassedChunk} constructor replaces a list that is too small and keeps one that is
     * larger than needed, so within a shared pool the lists drift towards the largest need (up to 4096 entries,
     * for the 32-byte class).
     * <p>
     * <p>
     * The pool is bounded by one number of bytes per heap, {@link #RECYCLED_BYTES_BUDGET}, across every chunk size:
     * whichever chunk sizes are churning get the room. A bound per chunk size left one or two buffers to the large
     * size classes, whose chunks are given up at the rate of the small ones (measured on E_COMMERCE heap 16384:
     * 12 GiB of chunk buffers allocated in 20 s, and four times the garbage collections, for the same used memory).
     * <p>
     * Accessed only under the owning stripe lock, or by the owner thread of a thread-local heap.
     */
    static final class SizeClassChunkRecycler {
        /**
         * The bytes of chunk buffers a heap keeps for reuse.
         */
        static final int RECYCLED_BYTES_BUDGET = Math.max(MIN_CHUNK_SIZE, SystemPropertyUtil.getInt(
                "io.netty.allocator.recycledChunkBytes", IS_LOW_MEM ? 4 * 1024 * 1024 : 32 * 1024 * 1024));

        private final AbstractByteBuf[][] buffers = new AbstractByteBuf[CHUNK_POOL_COUNT][];
        private final MpscIntQueue[][] freeLists = new MpscIntQueue[CHUNK_POOL_COUNT][];
        private final IntStack[][] localFreeLists = new IntStack[CHUNK_POOL_COUNT][];
        private final int[] sizes = new int[CHUNK_POOL_COUNT];
        /** Bytes of the buffers held by all the pools; never above {@link #RECYCLED_BYTES_BUDGET}. */
        private int retainedBytes;

        // What the last successful poll() returned, read once by the caller.
        private AbstractByteBuf polledBuffer;
        private MpscIntQueue polledFreeList;
        private IntStack polledLocalFreeList;

        SizeClassChunkRecycler() {
            for (int i = 0; i < CHUNK_POOL_COUNT; i++) {
                int capacity = capacityOf(i);
                buffers[i] = new AbstractByteBuf[capacity];
                freeLists[i] = new MpscIntQueue[capacity];
                localFreeLists[i] = new IntStack[capacity];
            }
        }

        private static int capacityOf(int pool) {
            return Math.max(1, RECYCLED_BYTES_BUDGET / CHUNK_SIZES[pool]);
        }

        // Visible for testing.
        static int poolCapacity(int sizeClassIndex) {
            return capacityOf(SIZE_CLASS_TO_CHUNK_POOL[sizeClassIndex]);
        }

        /**
         * Take a buffer, and the free lists that came with it, for a chunk of {@code sizeClassIndex}.
         * On {@code true}, read them through {@link #takeBuffer()}, {@link #takeFreeList()} and
         * {@link #takeLocalFreeList()} before the next poll.
         */
        boolean poll(int sizeClassIndex) {
            assert polledBuffer == null && polledFreeList == null && polledLocalFreeList == null :
                    "the previous poll was not fully taken";
            int pool = SIZE_CLASS_TO_CHUNK_POOL[sizeClassIndex];
            int size = sizes[pool];
            if (size == 0) {
                return false;
            }
            int idx = --size;
            sizes[pool] = size;
            retainedBytes -= CHUNK_SIZES[pool];
            polledBuffer = buffers[pool][idx];
            polledFreeList = freeLists[pool][idx];
            polledLocalFreeList = localFreeLists[pool][idx];
            buffers[pool][idx] = null;
            freeLists[pool][idx] = null;
            localFreeLists[pool][idx] = null;
            return true;
        }

        AbstractByteBuf takeBuffer() {
            AbstractByteBuf buf = polledBuffer;
            polledBuffer = null;
            return buf;
        }

        MpscIntQueue takeFreeList() {
            MpscIntQueue fl = polledFreeList;
            polledFreeList = null;
            return fl;
        }

        IntStack takeLocalFreeList() {
            IntStack fl = polledLocalFreeList;
            polledLocalFreeList = null;
            return fl;
        }

        /**
         * Keep {@code delegate} and its free lists for the next chunk of this chunk size, or refuse when that would
         * take the heap's pools above {@link #RECYCLED_BYTES_BUDGET}.
         */
        boolean offer(AbstractByteBuf delegate, MpscIntQueue freeList, IntStack localFreeList, int sizeClassIndex) {
            assert delegate != null && freeList != null && localFreeList != null;
            int pool = SIZE_CLASS_TO_CHUNK_POOL[sizeClassIndex];
            int size = sizes[pool];
            int chunkSize = CHUNK_SIZES[pool];
            if (size >= buffers[pool].length || retainedBytes + chunkSize > RECYCLED_BYTES_BUDGET) {
                return false;
            }
            retainedBytes += chunkSize;
            buffers[pool][size] = delegate;
            freeLists[pool][size] = freeList;
            localFreeLists[pool][size] = localFreeList;
            sizes[pool] = size + 1;
            return true;
        }

        // Visible for testing.
        int size(int sizeClassIndex) {
            return sizes[SIZE_CLASS_TO_CHUNK_POOL[sizeClassIndex]];
        }

        void freeAll() {
            for (int pool = 0; pool < CHUNK_POOL_COUNT; pool++) {
                for (int i = 0; i < sizes[pool]; i++) {
                    buffers[pool][i].release();
                    buffers[pool][i] = null;
                    freeLists[pool][i] = null;
                    localFreeLists[pool][i] = null;
                }
                sizes[pool] = 0;
            }
            retainedBytes = 0;
        }
    }

    // Striped heap holding all size-class magazines under one lock.
    // One StampedLock per stripe covers ALL size classes.
    private static final class StripedHeap {
        final StampedLock lock = new StampedLock();
        SizeClassMagazine[] magazines;
        BuddyMagazine buddyMagazine;
        AdaptiveRecycler recycler;
        SizeClassChunkRecycler chunkRecycler;

        SizeClassMagazine getOrCreateMagazine(int sizeClassIndex, AdaptivePoolingAllocator allocator) {
            SizeClassMagazine[] mags = magazines;
            if (mags == null) {
                return createFirstMagazine(sizeClassIndex, allocator);
            }
            SizeClassMagazine mag = mags[sizeClassIndex];
            if (mag == null) {
                mag = createMagazine(sizeClassIndex, allocator);
            }
            return mag;
        }

        private SizeClassMagazine createFirstMagazine(int sizeClassIndex, AdaptivePoolingAllocator allocator) {
            magazines = new SizeClassMagazine[SIZE_CLASSES_COUNT];
            chunkRecycler = new SizeClassChunkRecycler();
            return createMagazine(sizeClassIndex, allocator);
        }

        private SizeClassMagazine createMagazine(int sizeClassIndex, AdaptivePoolingAllocator allocator) {
            if (recycler == null) {
                recycler = AdaptiveRecycler.sharedExclusiveGet(MAGAZINE_BUFFER_QUEUE_CAPACITY);
            }
            SizeClassChunkManagementStrategy strategy = allocator.sizeClassStrategies[sizeClassIndex];
            SizeClassMagazine mag = new SizeClassMagazine(allocator, strategy, chunkRecycler, sizeClassIndex,
                    null, recycler, lock, magazines);
            magazines[sizeClassIndex] = mag;
            return mag;
        }

        BuddyMagazine getOrCreateBuddyMagazine(AdaptivePoolingAllocator allocator) {
            BuddyMagazine mag = buddyMagazine;
            if (mag == null) {
                mag = createBuddyMagazine(allocator);
            }
            return mag;
        }

        private BuddyMagazine createBuddyMagazine(AdaptivePoolingAllocator allocator) {
            if (recycler == null) {
                recycler = AdaptiveRecycler.sharedExclusiveGet(MAGAZINE_BUFFER_QUEUE_CAPACITY);
            }
            BuddyMagazine mag = new BuddyMagazine(allocator, allocator.buddyStrategy, recycler, lock);
            buddyMagazine = mag;
            return mag;
        }

        void freeStripe() {
            final StampedLock l = lock;
            long stamp = l.writeLock();
            try {
                if (magazines != null) {
                    for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
                        SizeClassMagazine mag = magazines[i];
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
                if (sizeClassIndex < SIZE_CLASSES_COUNT) {
                    SizeClassMagazine mag = getOrCreateMagazine(sizeClassIndex, allocator);
                    if (buf == null) {
                        buf = mag.newBuffer();
                    }
                    if (mag.allocate(size, maxCapacity, buf)) {
                        mag.tickAllocPurge();
                        return buf;
                    }
                } else {
                    // Cache purging is size-class management: the buddy magazine has no size
                    // class, no sibling magazines and no chunk recycler to feed, so it never ticks.
                    BuddyMagazine mag = getOrCreateBuddyMagazine(allocator);
                    if (buf == null) {
                        buf = mag.newBuffer();
                    }
                    if (mag.allocate(size, maxCapacity, buf)) {
                        return buf;
                    }
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
        private final SizeClassMagazine[] magazines = new SizeClassMagazine[SIZE_CLASSES_COUNT];
        private final SizeClassChunkRecycler chunkRecycler = new SizeClassChunkRecycler();
        private final AdaptivePoolingAllocator allocator;

        ThreadLocalSizeClassHeap(AdaptivePoolingAllocator allocator) {
            this.allocator = allocator;
        }

        AdaptiveByteBuf allocate(int sizeClassIndex, int size, int maxCapacity, AdaptiveByteBuf buf) {
            SizeClassMagazine mag = getOrCreateMagazine(sizeClassIndex);
            boolean reallocate = buf != null;
            if (!reallocate) {
                buf = mag.newBuffer();
            }
            boolean success = mag.allocate(size, maxCapacity, buf);
            assert success : "Thread-local allocation must always succeed";
            mag.tickAllocPurge();
            return buf;
        }

        SizeClassMagazine getOrCreateMagazine(int sizeClassIndex) {
            SizeClassMagazine mag = magazines[sizeClassIndex];
            if (mag == null) {
                mag = createMagazine(sizeClassIndex);
            }
            return mag;
        }

        private SizeClassMagazine createMagazine(int sizeClassIndex) {
            SizeClassChunkManagementStrategy strategy = allocator.sizeClassStrategies[sizeClassIndex];
            SizeClassMagazine mag = new SizeClassMagazine(allocator, strategy, chunkRecycler, sizeClassIndex,
                                       Thread.currentThread(), null, null, magazines);
            magazines[sizeClassIndex] = mag;
            return mag;
        }

        void free() {
            for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
                SizeClassMagazine mag = magazines[i];
                if (mag != null) {
                    mag.free();
                    magazines[i] = null;
                }
            }
            chunkRecycler.freeAll();
        }
    }

    /**
     * An intrusive doubly linked list of chunks, newest first, like mimalloc's page queue. The links live on the
     * chunk, so removing any chunk is O(1), and so does the chunk's membership: {@code chunk.queue} is the queue it
     * is on, or {@code null}. Not concurrent: the magazine that owns it holds the stripe lock, or is the only thread
     * that touches it. Used by both magazines: by capacity on the size-class path, by largest free block on the
     * buddy path.
     */
    static final class ChunkQueue {
        Chunk head;
        int size;

        void pushFront(Chunk chunk) {
            Chunk head = this.head;
            chunk.prevInQueue = null;
            chunk.nextInQueue = head;
            if (head != null) {
                head.prevInQueue = chunk;
            }
            this.head = chunk;
            chunk.queue = this;
            size++;
        }

        void remove(Chunk chunk) {
            Chunk prev = chunk.prevInQueue;
            Chunk next = chunk.nextInQueue;
            if (prev != null) {
                prev.nextInQueue = next;
            } else {
                head = next;
            }
            if (next != null) {
                next.prevInQueue = prev;
            }
            chunk.prevInQueue = null;
            chunk.nextInQueue = null;
            chunk.queue = null;
            size--;
        }
    }

    /**
     * Chunks that a releasing thread asked their owner to look at, because it freed memory in a chunk whose queues
     * it may not touch (see Invariant N in {@link SizeClassedChunkCache}, which both magazines follow). A lock-free
     * (Treiber) stack that any thread pushes to and the owner takes whole. A chunk's {@code pendingNext} is both its
     * link and the claim that it is queued: a chunk is queued at most once, and a push on a queued chunk costs one
     * volatile read.
     */
    static final class PendingChunks {
        private static final AtomicReferenceFieldUpdater<PendingChunks, Chunk> HEAD =
                AtomicReferenceFieldUpdater.newUpdater(PendingChunks.class, Chunk.class, "head");
        private static final AtomicReferenceFieldUpdater<Chunk, Chunk> NEXT =
                AtomicReferenceFieldUpdater.newUpdater(Chunk.class, Chunk.class, "pendingNext");
        /**
         * Ends the stack, so that a {@code null} link keeps its meaning of "not queued". Never a usable chunk.
         */
        private static final Chunk END = new SizeClassedChunk();

        private volatile Chunk head;

        /**
         * Queue {@code chunk}, unless it is queued already. Any thread, no lock.
         */
        void push(Chunk chunk) {
            if (chunk.pendingNext != null) {
                return;
            }
            // Claim: only the thread that moves the link off null owns the push.
            if (!NEXT.compareAndSet(chunk, null, END)) {
                return;
            }
            Chunk head;
            do {
                head = this.head;
                NEXT.lazySet(chunk, head == null ? END : head);
            } while (!HEAD.compareAndSet(this, head, chunk));
        }

        /**
         * Take every queued chunk: the first one, whose successors {@link #rearm} returns, or {@code null}. Owner
         * only. Cheap when nothing is queued: one volatile read, no atomic read-modify-write; the heap-wide drain
         * pays this per size class.
         */
        Chunk takeAll() {
            if (head == null) {
                return null;
            }
            return HEAD.getAndSet(this, null);
        }

        /**
         * Unlink {@code chunk}, taken by {@link #takeAll}, and make it queueable again; return the next taken chunk,
         * or {@code null}. Call it BEFORE processing the chunk: a return that lands while the chunk is processed must
         * be able to queue it again, and re-arming afterwards would lose that note and strand the chunk until some
         * later, unrelated one.
         * <p>
         * The store is a full volatile store on purpose, not a lazySet: it is the store half of a Dekker pair with
         * the releaser, which offers the segment (the MPSC offer ends in a CAS on the producer index, a StoreLoad)
         * and only then reads {@code pendingNext}. The processing reads the free lists right after this store;
         * without the StoreLoad here both sides could miss each other and the chunk would be stranded.
         */
        static Chunk rearm(Chunk chunk) {
            Chunk next = chunk.pendingNext;
            NEXT.set(chunk, null);
            return next == END ? null : next;
        }

        /**
         * Drop every queued chunk; for a cache being freed, whose chunks are all about to be deallocated.
         */
        void clear() {
            HEAD.lazySet(this, null);
        }

        // Visible for testing: how many chunks are queued.
        int size() {
            int count = 0;
            Chunk cur = head;
            while (cur != null && cur != END) {
                count++;
                cur = cur.pendingNext;
            }
            return count;
        }
    }

    /**
     * Two-list chunk cache: answers "give me a chunk to carve from" and "release what is idle".
     *
     * <p><b>Access.</b> The queues and each chunk's {@code queue} are touched only by the
     * owner thread (thread-local magazines) or under the stripe write lock (shared magazines) —
     * one magazine's caches all share that one lock. The only exception is {@link #pending},
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
     * <p><b>The active chunk.</b> The chunk the magazine allocates from is the cache's {@link #active}
     * chunk, on neither queue; the magazine's {@code current} field is
     * only the fast path's alias of it. {@link #activate} makes a polled or freshly allocated chunk active,
     * and {@link #deactivate} files it by capacity, like {@link #offerChunk}, when the magazine runs it out
     * of segments or is freed. The active chunk is the magazine's, not a retention candidate: no cache
     * decision touches it (the release paths and the drain act only on chunks filed on a queue,
     * and {@link #tickPurge} walks the lists only), and it is not counted against
     * the retention floor ({@link #atOrBelowFloor}).
     *
     * <p><b>Why the reusable list is trustworthy.</b> A cached chunk other than the active one can
     * only <em>gain</em> capacity: segments are handed out only by {@code readInitInto} on the active
     * chunk, and a chunk becomes active only through {@link #activate}, after the magazine gave up the
     * previous one. So a non-active chunk filed with capacity still has it, and the head of the
     * reusable list is always usable when there is no active chunk, which is the only time
     * {@link #pollChunk} runs.
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
     * {@code reusable.remove} unconditionally, and every caller either walks the reusable list
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
    static final class SizeClassedChunkCache {
        /** Bound on the last-resort probe of the exhausted list; see {@link #probeExhausted()}. */
        private static final int MAX_EXHAUSTED_PROBE = 8;

        final ChunkQueue exhausted = new ChunkQueue();
        final ChunkQueue reusable = new ChunkQueue();
        /**
         * The chunk the magazine allocates from, or {@code null}. It is on neither queue, and neither the purge nor
         * the drain acts on it: only the magazine allocates from it, until {@link #deactivate} files it by capacity
         * like any other chunk.
         */
        SizeClassedChunk active;
        /** Treiber stack of chunks that a releasing thread asked us to look at. */
        final PendingChunks pending = new PendingChunks();

        final SizeClassChunkRecycler chunkRecycler;
        final int sizeClassIndex;
        /**
         * The lock guarding this cache's lists, or {@code null} when there is nothing to guard.
         *
         * <p>A magazine owned by one thread reaches its cache only from that thread, so it has no
         * lock; a magazine on a stripe shares the stripe's lock with the other size classes there.
         * Either way the lists are only ever touched by a thread with exclusive access - see
         * {@link #tryLockForRelease()}, which reports "not available" for both cases alike.
         */
        final StampedLock stripeLock;

        SizeClassedChunkCache(int chunkSize, SizeClassChunkRecycler chunkRecycler, int sizeClassIndex) {
            this(chunkSize, chunkRecycler, sizeClassIndex, null);
        }

        SizeClassedChunkCache(int chunkSize, SizeClassChunkRecycler chunkRecycler,
                                         int sizeClassIndex, StampedLock stripeLock) {
            this.chunkRecycler = chunkRecycler;
            this.sizeClassIndex = sizeClassIndex;
            this.stripeLock = stripeLock;
        }

        /**
         * {@code true} when the two queues hold at most one chunk between them. This is the retention floor:
         * eviction must never take the last chunk of a size class besides the active one, which is what mimalloc's
         * {@code pageRetire} does by refusing to free the only page left in a bin. Every other chunk that empties
         * goes to the heap's {@link SizeClassChunkRecycler}, whose byte budget is what bounds idle memory.
         */
        private boolean atOrBelowFloor() {
            return exhausted.size + reusable.size <= 1;
        }

        // Signal A (see refile): exhausted → reusable
        void moveToReusable(SizeClassedChunk chunk) {
            exhausted.remove(chunk);
            reusable.pushFront(chunk);
        }

        void evictIfAboveFloor(SizeClassedChunk chunk) {
            // Every caller filters on the reusable queue, which the active chunk is never on.
            assert chunk != active : "the active chunk must never be evicted";
            if (chunk.hasFullCapacity() && !atOrBelowFloor()) {
                reusable.remove(chunk);
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
        //     was still active (or on no queue) stays correct once the chunk is filed. Do not optimise the
        //     note to carry state. This is also why a releaser that finds the claim already taken can
        //     simply walk away: the in-flight note covers its return too.
        //  3. Re-arm before processing (see drainPending).
        //  4. Classification and drain cannot interleave. offerChunk's (read capacity, insert) pair
        //     and the drain both run under the same stripe lock, or on the same owner thread. This is
        //     what covers a return landing right after offerChunk read the capacity but before the
        //     insert: the chunk is filed as exhausted while holding capacity, and the note -- which
        //     cannot be consumed in between -- is what fixes it.
        //
        // A drain that finds the active chunk and no-ops is benign, not a lost signal: the chunk is the
        // magazine's, which consumes its own returned segments through nextAvailableSegmentOffset, and
        // when the magazine gives it up, deactivate files it by capacity -- an offerChunk, so property 4
        // covers it: a note consumed before deactivate made its segment visible to deactivate's capacity
        // read, and a note still outstanding is processed after it, against the list it was filed on.
        // That includes a return that lands from another thread while the chunk is being deactivated.
        // A drain that finds a chunk on no queue is benign too: the chunk is gone (evicted, recycled, or its cache
        // freed). A polled chunk is never seen in that state, because pollChunk and activate run back to
        // back under the same lock or on the same owner thread, with no drain in between.

        /**
         * Queue {@code chunk} for the next drain. Called by a releasing thread that holds no lock,
         * <em>after</em> the segment has been offered to the chunk's external free list, so a drainer
         * that pops the note is guaranteed to also see the segment.
         *
         * <p>This path must never read {@code queue} or any list link: those belong to the
         * owner thread / stripe lock holder. The note only says "look at this chunk". The chunk finds
         * this cache through its {@code final owningCache} field, so no racy reference read is involved.
         *
         * <p>{@link SizeClassedChunk#pendingNext} doubles as the dedup claim, so a return on a chunk
         * that is already queued costs a single volatile read.
         */
        void notifyHasCapacity(SizeClassedChunk chunk) {
            pending.push(chunk);
        }

        /**
         * Apply every queued notification. Caller must hold the stripe lock, or be the owner thread of
         * a thread-local cache.
         */
        void drainPending() {
            Chunk cur = pending.takeAll();
            while (cur != null) {
                // Re-arm BEFORE processing (property 3): see PendingChunks#rearm.
                Chunk next = PendingChunks.rearm(cur);
                refile((SizeClassedChunk) cur);
                cur = next;
            }
        }

        // Visible for testing: how many chunks are queued for the next drain.
        int pendingCount() {
            return pending.size();
        }

        /**
         * Move {@code chunk} to the queue its capacity calls for, after it may have gained capacity: a segment
         * return, by the owner thread or under the lock, or a note of one. An exhausted chunk with a free segment
         * becomes reusable; a fully free reusable chunk is evicted above the retention floor. Caller holds the
         * stripe lock or is the owner thread.
         */
        void refile(SizeClassedChunk chunk) {
            ChunkQueue queue = chunk.queue;
            if (queue == exhausted) {
                // A note may be stale; a return by the owner or under the lock has just pushed the segment.
                if (!chunk.hasRemainingCapacity()) {
                    return;
                }
                moveToReusable(chunk);
            } else if (queue != reusable) {
                // On no queue: either gone (evicted, recycled, or its cache freed), not ours to move - its capacity
                // is never read, because such a chunk may have had its free lists stripped by recycleOrDeallocate - or
                // the active chunk, which consumes its own returned segments and is filed by capacity when the
                // magazine gives it up (see deactivate). A polled chunk is activated before any drain can run.
                return;
            }
            evictIfAboveFloor(chunk);
        }

        /**
         * Try to take exclusive access to this cache so a releasing thread can place a segment and
         * apply any resulting list transition. Returns 0 when unavailable: a cache with no lock has
         * no exclusive mode to take, and a contended stripe lock is not waited on.
         *
         * <p>A non-zero result must be passed to {@link #unlockAfterRelease(long)}; a zero result
         * must not be.
         */
        long tryLockForRelease() {
            return stripeLock == null ? 0 : stripeLock.tryWriteLock();
        }

        /**
         * Release the exclusive access taken by {@link #tryLockForRelease()}.
         *
         * @param stamp a non-zero stamp from {@code tryLockForRelease}. Zero is not a stamp - it is
         *              how that method reports failure, and a cache without a lock reports nothing
         *              else - so passing it here is a caller bug, not a no-op.
         */
        void unlockAfterRelease(long stamp) {
            assert stamp != 0 : "unlockAfterRelease(0): tryLockForRelease did not grant the lock";
            stripeLock.unlockWrite(stamp);
        }

        /** Visible for testing: runs a purge tick bypassing the budget counter, then polls. */
        SizeClassedChunk forcePurge() {
            tickPurge();
            return pollChunkInternal();
        }

        SizeClassedChunk pollChunk(int size) {
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
            // The magazine gives up its active chunk before it asks for another one.
            assert active == null : "poll with an active chunk";
            SizeClassedChunk chunk = (SizeClassedChunk) reusable.head;
            if (chunk != null) {
                reusable.remove(chunk);
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
            SizeClassedChunk cur = (SizeClassedChunk) exhausted.head;
            int visited = 0;
            while (cur != null && visited < MAX_EXHAUSTED_PROBE) {
                SizeClassedChunk next = (SizeClassedChunk) cur.nextInQueue;
                visited++;
                if (cur.hasRemainingCapacity()) {
                    exhausted.remove(cur);
                    return cur;
                }
                cur = next;
            }
            return null;
        }

        void tickPurge() {
            drainPending();
            // Exhausted→reusable is applied by the drain above. All that is left is evicting
            // fully-free reusable chunks above the retention floor.
            SizeClassedChunk cur = (SizeClassedChunk) reusable.head;
            while (cur != null && !atOrBelowFloor()) {
                SizeClassedChunk next = (SizeClassedChunk) cur.nextInQueue;
                if (cur.hasFullCapacity()) {
                    reusable.remove(cur);
                    cur.recycleOrDeallocate(chunkRecycler, sizeClassIndex);
                }
                cur = next;
            }
        }

        boolean offerChunk(SizeClassedChunk chunk) {
            if (chunk.hasRemainingCapacity()) {
                reusable.pushFront(chunk);
            } else {
                exhausted.pushFront(chunk);
            }
            return true;
        }

        /**
         * Make {@code chunk}, which is not in this cache, the active chunk: the one the magazine allocates from
         * until {@link #deactivate} files it by capacity like any other chunk. Caller holds the stripe lock or is
         * the owner thread, like every list operation.
         */
        void activate(SizeClassedChunk chunk) {
            assert active == null : "the magazine already has an active chunk";
            assert chunk.queue == null : "the chunk is filed on a queue";
            active = chunk;
        }

        /**
         * The magazine is done allocating from its active chunk (it ran out of segments, or the magazine is
         * being freed): unlink it and file it by capacity, exactly as {@link #offerChunk} files any chunk.
         *
         * <p>Invariant N holds across this step as it does for any {@code offerChunk}. While the chunk was
         * active, a return that could not synchronise left a note, and the drain ignored it
         * ({@code refile} skips chunks on no queue). Such a note was either
         * <ul>
         *   <li>consumed before this call: then the segment was offered before the note was pushed (property
         *       1), the push happens-before the drain that popped it, and that drain ran on this thread or
         *       under this lock before this call. So the segment is visible here: either the magazine already
         *       allocated it again, or the capacity read below sees it and files the chunk reusable; or</li>
         *   <li>still outstanding (or pushed after this call started): the drain that pops it runs after
         *       this call, under the same lock or on the same owner thread (property 4), and finds the
         *       chunk on the list this call filed it on, so an exhausted-but-not-really chunk is moved; or</li>
         *   <li>never pushed: the releaser found {@code pendingNext} already non-null and walked away. If that
         *       link is a note still outstanding, the previous case covers this segment too. If it is a note a
         *       drain already popped, the releaser read the link before that drain's re-arm store: the releaser
         *       offered first (a CAS on the MPSC queue) and read {@code pendingNext} second, and the drain's
         *       full volatile re-arm store precedes every later volatile read of the queue indices on the
         *       draining side, this call's capacity read included. So the segment is visible here (see
         *       {@link PendingChunks#rearm}).</li>
         * </ul>
         * A return that took the lock or came from the owner thread cannot interleave with this call at all.
         */
        void deactivate(SizeClassedChunk chunk) {
            assert chunk == active : "not the active chunk";
            active = null;
            offerChunk(chunk);
        }

        void free() {
            // Drop any outstanding notes: every chunk they point at is about to be marked for
            // deallocation, and this cache is dead afterwards.
            pending.clear();
            // The magazine gives up its active chunk before it frees its cache.
            assert active == null : "free with an active chunk";
            freeAll(exhausted);
            freeAll(reusable);
        }

        private static void freeAll(ChunkQueue queue) {
            Chunk cur;
            while ((cur = queue.head) != null) {
                queue.remove(cur);
                ((SizeClassedChunk) cur).markToDeallocate();
            }
        }

        // Visible for testing: no chunk linked on either list.
        boolean isEmpty() {
            return exhausted.size + reusable.size == 0;
        }
    }

    private static final class SizeClassChunkManagementStrategy {
        private final int segmentSize;
        private final int chunkSize;

        private SizeClassChunkManagementStrategy(int segmentSize) {
            this.segmentSize = ObjectUtil.checkPositive(segmentSize, "segmentSize");
            chunkSize = chunkSizeOf(segmentSize);
        }

        SizeClassChunkController createController(AdaptivePoolingAllocator allocator) {
            return new SizeClassChunkController(
                    allocator.chunkAllocator, allocator.chunkRegistry, segmentSize, chunkSize);
        }

        SizeClassedChunkCache createChunkCache(SizeClassChunkRecycler chunkRecycler, int sizeClassIndex,
                                               StampedLock stripeLock) {
            return new SizeClassedChunkCache(chunkSize, chunkRecycler, sizeClassIndex, stripeLock);
        }
    }

    private static final class SizeClassChunkController {

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

        /**
         * Compute the "fast max capacity" value for the buffer: one segment, or less if the buffer may not grow
         * that far.
         */
        int computeBufferCapacity(int maxCapacity) {
            return Math.min(segmentSize, maxCapacity);
        }

        /**
         * Allocate a new {@link SizeClassedChunk} for the given {@link SizeClassMagazine}: re-create one from a
         * buffer of its heap's {@link SizeClassChunkRecycler}, or allocate a new buffer.
         */
        SizeClassedChunk newChunkAllocation(SizeClassMagazine magazine) {
            SizeClassChunkRecycler recycler = magazine.chunkRecycler;
            if (recycler.poll(magazine.sizeClassIndex)) {
                AbstractByteBuf recycledBuf = recycler.takeBuffer();
                MpscIntQueue recycledFL = recycler.takeFreeList();
                IntStack recycledLocal = recycler.takeLocalFreeList();
                SizeClassedChunk chunk = new SizeClassedChunk(
                        recycledBuf, recycledFL, recycledLocal, magazine, this);
                chunkRegistry.add(chunk);
                return chunk;
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

        BuddyChunkController createController(AdaptivePoolingAllocator allocator) {
            return new BuddyChunkController(
                    allocator.chunkAllocator, allocator.chunkRegistry, maxChunkSize);
        }
    }

    private static final class BuddyChunkController {
        private final ChunkAllocator chunkAllocator;
        private final ChunkRegistry chunkRegistry;
        private final AtomicInteger maxChunkSize;

        BuddyChunkController(ChunkAllocator chunkAllocator, ChunkRegistry chunkRegistry,
                             AtomicInteger maxChunkSize) {
            this.chunkAllocator = chunkAllocator;
            this.chunkRegistry = chunkRegistry;
            this.maxChunkSize = maxChunkSize;
        }

        /**
         * Compute the "fast max capacity" value for the buffer.
         */
        int computeBufferCapacity(int requestedSize, int maxCapacity) {
            return MathUtil.safeFindNextPositivePowerOfTwo(requestedSize);
        }

        /**
         * Allocate a new {@link BuddyChunk} for the given {@link BuddyMagazine}.
         */
        BuddyChunk newChunkAllocation(int promptingSize, BuddyMagazine magazine) {
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

    static final class AdaptiveRecycler extends Recycler<AdaptiveByteBuf> {

        private AdaptiveRecycler(boolean unguarded, int interval) {
            // uses fast thread local
            super(unguarded, interval);
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
            // Interval 0: pool every recycled buffer, matching what the shared-stripe recycler
            // gets from sharedExclusiveGet. The global default interval of 8 admits one buffer
            // in eight and pays a stateful counter plus a data-dependent branch per allocation;
            // retention is already bounded by the recycler's capacity, so the interval buys
            // nothing here. Measured on SOCKET_PROXY, t=1: -15.0 ns/op at MLB=65536,
            // -3.3 ns/op at MLB=1024, neutral on API_GATEWAY.
            return new AdaptiveRecycler(true, 0);
        }

        public static AdaptiveRecycler sharedWith(int maxCapacity) {
            return new AdaptiveRecycler(maxCapacity, true);
        }

        public static AdaptiveRecycler sharedExclusiveGet(int maxCapacity) {
            return new AdaptiveRecycler(maxCapacity, true, true);
        }
    }

    /**
     * The magazine of one size class, on a shared stripe (guarded by the stripe lock) or on a thread-local heap
     * (used by its owner thread only). It carves fixed-size segments out of {@link SizeClassedChunk}s, keeps its
     * chunks in its own {@link SizeClassedChunkCache} (the one it allocates from as the cache's active chunk, which
     * {@link #current} aliases), and feeds evicted chunk buffers to the {@link SizeClassChunkRecycler} of its heap.
     */
    private static final class SizeClassMagazine {
        private static final AdaptiveRecycler EVENT_LOOP_LOCAL_BUFFER_POOL = AdaptiveRecycler.threadLocal();

        private SizeClassedChunk current;
        final AdaptivePoolingAllocator allocator;
        final Thread ownerThread;
        private final SizeClassChunkController chunkController;
        private final SizeClassedChunkCache chunkCache;
        /**
         * Every size-classed magazine of the heap this magazine belongs to, including this one. The whole array is
         * covered by the one lock (shared stripe) or the one owner thread (thread-local heap) that guards this
         * magazine, which is what makes the heap-wide drain legal from here.
         */
        private final SizeClassMagazine[] heapMagazines;
        final int sizeClassIndex;
        final SizeClassChunkRecycler chunkRecycler;
        final AdaptiveRecycler bufRecycler; // for ByteBuf wrapper pooling; null → EVENT_LOOP_LOCAL_BUFFER_POOL
        private final int purgeTickThreshold;
        private int allocCount;

        SizeClassMagazine(AdaptivePoolingAllocator allocator, SizeClassChunkManagementStrategy strategy,
                          SizeClassChunkRecycler chunkRecycler, int sizeClassIndex,
                          Thread ownerThread, AdaptiveRecycler bufRecycler, StampedLock stripeLock,
                          SizeClassMagazine[] heapMagazines) {
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

        /**
         * Count one successful allocation and, when the budget is spent, purge this magazine's cache
         * and those of every other size class on this heap.
         *
         * <p>Call exactly once per successful {@link #allocate}.
         */
        void tickAllocPurge() {
            if (++allocCount >= purgeTickThreshold) {
                allocCount = 0;
                chunkCache.tickPurge();
                purgeHeapSiblings();
            }
        }

        /**
         * Purge the caches of the other size classes on this heap. A size class that has gone idle
         * stops allocating, so it would never fire its own tick — and those are exactly the caches
         * worth purging, because the chunks they release go to the {@link SizeClassChunkRecycler}
         * that every size class on this heap draws from.
         */
        private void purgeHeapSiblings() {
            SizeClassMagazine[] mags = heapMagazines;
            for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
                SizeClassMagazine sibling = mags[i];
                if (sibling != null && sibling != this) {
                    sibling.chunkCache.tickPurge();
                }
            }
        }

        /**
         * Apply the notifications left by releasers on every size class of this heap, not just this
         * magazine's. A size class that has gone idle stops allocating, so it would never drain its
         * own notes — and those are exactly the chunks worth reclaiming, because their backing
         * buffers go to the {@link SizeClassChunkRecycler} that every size class draws from.
         *
         * <p>Called on the allocation slow path only, right before {@link SizeClassedChunkCache#pollChunk},
         * which is once per chunk-worth of allocations.
         *
         * <p>This magazine's own cache is skipped: {@code pollChunk} drains it on the very next
         * line, which is both the last moment before the poll and therefore the freshest - it also
         * catches notes that landed while the other size classes were being drained.
         */
        private void drainHeapPending() {
            SizeClassMagazine[] mags = heapMagazines;
            for (int i = 0; i < SIZE_CLASSES_COUNT; i++) {
                SizeClassMagazine mag = mags[i];
                if (mag != null && mag != this) {
                    mag.chunkCache.drainPending();
                }
            }
        }

        boolean allocate(int size, int maxCapacity, AdaptiveByteBuf buf) {
            int startingCapacity = chunkController.computeBufferCapacity(maxCapacity);
            SizeClassedChunk curr = current;
            if (curr != null) {
                boolean success = curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                if (!success || curr.remainingCapacity() == 0) {
                    // Out of segments: give the chunk up. If a segment comes back from another thread after the
                    // count above, deactivate files the chunk as reusable by its capacity, so a later poll can hand
                    // it back. The !success case is defensive: the previous call left remainingCapacity() > 0,
                    // which counts only free segments, and this magazine is the only consumer of its chunk's free
                    // lists, so the read above always finds a segment.
                    current = null;
                    curr.releaseFromMagazine();
                }
                if (success) {
                    return true;
                }
            }
            return allocateSlow(size, maxCapacity, buf, startingCapacity);
        }

        /**
         * The current chunk (if any) had no room. Poll the cache, then fall back to allocating a fresh chunk.
         * Whichever chunk ends up serving the allocation becomes the cache's active chunk, which no cache decision
         * touches, and is aliased by {@link #current} for the fast path.
         */
        private boolean allocateSlow(int size, int maxCapacity, AdaptiveByteBuf buf, int startingCapacity) {
            assert current == null;
            SizeClassedChunk curr;
            boolean polledChunkWithoutSegment = false;

            // Now try to poll from the cache first
            drainHeapPending();
            curr = chunkCache.pollChunk(size);
            if (curr != null) {
                chunkCache.activate(curr);
                // The size-class cache only hands out chunks with a free segment, and a segment always fits the size,
                // so this never happens; if that invariant ever broke, fall back to a fresh chunk rather than fail.
                if (curr.remainingCapacity() < size) {
                    polledChunkWithoutSegment = true;
                    curr.releaseFromMagazine();
                    curr = null;
                }
            }
            if (curr == null) {
                curr = chunkController.newChunkAllocation(this);
                chunkCache.activate(curr);
            }

            // The active chunk stays the cache's (see SizeClassedChunkCache#active); current is only the fast
            // path's alias of it.
            current = curr;
            // Checked only now, with the fallback chunk active and aliased, so that with assertions enabled the
            // failure leaves the magazine and its cache consistent.
            assert !polledChunkWithoutSegment : "the cache handed out a chunk without a free segment";
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

        void free() {
            if (current != null) {
                current.releaseFromMagazine();
                current = null;
            }
            chunkCache.free();
        }

        AdaptiveByteBuf newBuffer() {
            AdaptiveByteBuf buf = bufRecycler != null ? bufRecycler.get() : EVENT_LOOP_LOCAL_BUFFER_POOL.get();
            buf.resetRefCnt();
            buf.discardMarks();
            return buf;
        }
    }

    /**
     * The magazine for buffers above the largest size class, one per stripe, guarded by the stripe lock. It carves
     * power-of-two blocks out of {@link BuddyChunk}s: it allocates from its {@link #active} chunk and files every other
     * chunk it owns by the order of its largest free block, so the next chunk to allocate from is the one with the
     * largest free block, found in O(1).
     * <p>
     * Only the stripe lock holder touches the queues and the chunks' trees. A buffer released by any thread puts
     * its block on the chunk's MPSC free list and leaves a note in {@link #pending} (see
     * {@link BuddyChunk#releaseSegment}); the next slow path drains the notes and refiles each chunk by its tree,
     * after applying its free list. The same protocol as {@link SizeClassedChunkCache}'s Invariant N: the block is
     * offered before the note is pushed, notes say only "look at this chunk", a note is re-armed before it is
     * processed, and filing and draining both run under the stripe lock. A bounded probe of the full chunks covers
     * notes that are still in flight.
     */
    private static final class BuddyMagazine {
        /** One queue per order of largest free block: {@link BuddyTree#MIN_BLOCK_SIZE} up to the largest chunk. */
        private static final int ORDERS = Integer.numberOfTrailingZeros(MAX_CHUNK_SIZE / BuddyTree.MIN_BLOCK_SIZE) + 1;
        /** Bound on the last-resort look at the full chunks; see {@link #probeFull}. */
        private static final int MAX_FULL_PROBE = 8;

        final AdaptivePoolingAllocator allocator;
        private final BuddyChunkController chunkController;
        private final AdaptiveRecycler bufRecycler; // for ByteBuf wrapper pooling
        /** Chunks that other threads' releases asked this magazine to look at. */
        final PendingChunks pending = new PendingChunks();
        /** Chunks with a free block, by the order of the largest one; wholly free chunks are in {@link #whollyFree}. */
        private final ChunkQueue[] byLargestFreeOrder = new ChunkQueue[ORDERS];
        /** Bit {@code k} set when {@code byLargestFreeOrder[k]} may be non-empty: set on filing, cleared by a poll. */
        private int ordersInUse;
        /** Chunks without a free block. */
        private final ChunkQueue full = new ChunkQueue();
        /** Chunks with no block claimed; the only ones the magazine can free while it holds more than it may keep. */
        private final ChunkQueue whollyFree = new ChunkQueue();
        /** Bytes of the chunks on {@link #whollyFree}; never above {@link #BUDDY_IDLE_BYTES}. */
        private long idleBytes;
        /** The lock of the stripe this magazine lives on, which guards everything here but {@link #pending}. */
        private final StampedLock stripeLock;
        /** The chunk the magazine allocates from, on no queue; {@code null} before the first allocation. */
        private BuddyChunk active;

        BuddyMagazine(AdaptivePoolingAllocator allocator,
                      BuddyChunkManagementStrategy strategy, AdaptiveRecycler bufRecycler, StampedLock stripeLock) {
            this.allocator = allocator;
            this.bufRecycler = bufRecycler;
            this.stripeLock = stripeLock;
            this.chunkController = strategy.createController(allocator);
            for (int order = 0; order < ORDERS; order++) {
                byLargestFreeOrder[order] = new ChunkQueue();
            }
        }

        boolean allocate(int size, int maxCapacity, AdaptiveByteBuf buf) {
            int blockSize = chunkController.computeBufferCapacity(size, maxCapacity);
            BuddyChunk chunk = active;
            if (chunk != null && chunk.readInitInto(buf, size, blockSize, maxCapacity)) {
                return true;
            }
            return allocateSlow(size, maxCapacity, buf, blockSize);
        }

        /**
         * The active chunk (if any) had no free block of {@code blockSize}: file it, apply the notes, and make the
         * chunk with the smallest fitting free block active, or a new chunk when none has one.
         */
        private boolean allocateSlow(int size, int maxCapacity, AdaptiveByteBuf buf, int blockSize) {
            BuddyChunk chunk = active;
            if (chunk != null) {
                active = null;
                file(chunk);
            }
            drainPending();
            chunk = poll(blockSize);
            if (chunk == null) {
                chunk = chunkController.newChunkAllocation(size, this);
            }
            active = chunk;
            boolean success = chunk.readInitInto(buf, size, blockSize, maxCapacity);
            // A polled chunk's largest free block was exact when it was filed, and can only have grown since.
            assert success : "no free block of " + blockSize + " in " + chunk;
            return success;
        }

        /**
         * The chunk with the largest free block, taken off its queue, when that block is at least {@code blockSize};
         * else a wholly free chunk that large; else a full chunk that regained such a block from releases not yet
         * noted; else null.
         * <p>
         * The largest rather than the smallest that fits: the chunk polled here becomes the one the magazine allocates
         * from, and one with a big free block serves many more requests before it runs out. Taking the smallest fit
         * picks the chunk that is nearly full, which serves about one request and sends the next allocation down here
         * again (measured on E_COMMERCE heap 65536: 47% of buddy allocations took this path, against 9%).
         */
        private BuddyChunk poll(int blockSize) {
            int order = Integer.numberOfTrailingZeros(blockSize / BuddyTree.MIN_BLOCK_SIZE);
            int candidates = ordersInUse & -(1 << order);
            while (candidates != 0) {
                int candidate = 31 - Integer.numberOfLeadingZeros(candidates);
                Chunk head = byLargestFreeOrder[candidate].head;
                if (head != null) {
                    unfile(head);
                    return (BuddyChunk) head;
                }
                ordersInUse &= ~(1 << candidate);
                candidates &= ~(1 << candidate);
            }
            for (Chunk cur = whollyFree.head; cur != null; cur = cur.nextInQueue) {
                if (cur.capacity >= blockSize) {
                    unfile(cur);
                    return (BuddyChunk) cur;
                }
            }
            return probeFull(order);
        }

        /**
         * Last resort before a new chunk: look at a bounded number of full chunks for releases whose notes are not
         * drained yet (pushed during or after the drain), as {@link SizeClassedChunkCache} probes its exhausted list.
         * Bounded by chunks visited, not by anything found.
         */
        private BuddyChunk probeFull(int order) {
            Chunk cur = full.head;
            for (int visited = 0; cur != null && visited < MAX_FULL_PROBE; visited++) {
                Chunk next = cur.nextInQueue;
                BuddyChunk chunk = (BuddyChunk) cur;
                if (chunk.hasUnprocessedFreelistEntries()) {
                    unfile(chunk);
                    chunk.processFreelistEntries();
                    if (chunk.largestFreeOrder() >= order) {
                        return chunk;
                    }
                    file(chunk);
                }
                cur = next;
            }
            return null;
        }

        /**
         * File {@code chunk}, on no queue, by its tree once its free list is applied. A wholly free chunk is freed
         * instead when keeping it would take the idle chunks above {@link #BUDDY_IDLE_BYTES}, or above
         * {@link #CHUNK_REUSE_QUEUE} chunks: the limits are on idle memory, whatever the number of chunks in use.
         * A count alone is no bound: chunks are 2 to 8 MiB, and a burst of large buffers was kept whole (measured on
         * one heap: 192 MiB of 192 held after every buffer was released).
         */
        private void file(BuddyChunk chunk) {
            chunk.processFreelistEntries();
            if (chunk.isWhollyFree()) {
                if (whollyFree.size >= CHUNK_REUSE_QUEUE || idleBytes + chunk.capacity > BUDDY_IDLE_BYTES) {
                    chunk.markToDeallocate();
                    return;
                }
                idleBytes += chunk.capacity;
                whollyFree.pushFront(chunk);
            } else {
                int order = chunk.largestFreeOrder();
                if (order < 0) {
                    full.pushFront(chunk);
                } else {
                    byLargestFreeOrder[order].pushFront(chunk);
                    ordersInUse |= 1 << order;
                }
            }
        }

        /**
         * Try to take the stripe lock so a releasing thread can put its block back and refile the chunk itself.
         * Returns 0 when the lock is busy: it is never waited on, and the release leaves a note instead.
         */
        long tryLockForRelease() {
            return stripeLock.tryWriteLock();
        }

        void unlockAfterRelease(long stamp) {
            stripeLock.unlockWrite(stamp);
        }

        /**
         * Put a block back in its chunk and move the chunk to the queue its tree now calls for; a chunk that became
         * wholly free is kept or given up here, by {@link #file}. Caller holds the stripe lock. This is what makes
         * the idle bound hold for a magazine that stops allocating: a release that only left a note would wait for
         * the magazine's next slow path.
         */
        void releaseInPlace(BuddyChunk chunk, int offset, int size) {
            chunk.releaseToTree(offset, size);
            if (chunk.queue != null) {
                // Not the active chunk, which is filed when the magazine gives it up, nor one that left the magazine.
                unfile(chunk);
                file(chunk);
            }
        }

        private void unfile(Chunk chunk) {
            ChunkQueue queue = chunk.queue;
            if (queue == whollyFree) {
                idleBytes -= chunk.capacity;
            }
            queue.remove(chunk);
        }

        /**
         * Refile every chunk other threads' releases asked about. A chunk on no queue is skipped: the active chunk
         * applies its own free list, and a chunk that left the magazine is not its to move.
         */
        private void drainPending() {
            Chunk cur = pending.takeAll();
            while (cur != null) {
                // Re-arm BEFORE processing: see PendingChunks#rearm.
                Chunk next = PendingChunks.rearm(cur);
                if (cur.queue != null) {
                    unfile(cur);
                    file((BuddyChunk) cur);
                }
                cur = next;
            }
        }

        void free() {
            BuddyChunk chunk = active;
            active = null;
            if (chunk != null) {
                chunk.markToDeallocate();
            }
            freeAll(full);
            freeAll(whollyFree);
            for (ChunkQueue queue : byLargestFreeOrder) {
                freeAll(queue);
            }
            ordersInUse = 0;
            // Every chunk the notes point at is freed above, or is gone.
            pending.clear();
        }

        private void freeAll(ChunkQueue queue) {
            Chunk cur;
            while ((cur = queue.head) != null) {
                unfile(cur);
                ((BuddyChunk) cur).markToDeallocate();
            }
        }

        AdaptiveByteBuf newBuffer() {
            AdaptiveByteBuf buf = bufRecycler.get();
            buf.resetRefCnt();
            buf.discardMarks();
            return buf;
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

    /**
     * What every chunk has in common, pooled or not: the buffer it carves allocations out of, the allocator that
     * owns it, and its accounting in the {@link ChunkRegistry} and the JFR events.
     */
    abstract static class Chunk implements ChunkInfo {
        /**
         * The {@link ChunkQueue} of its magazine's cache this chunk is filed on, or {@code null}: the magazine's
         * active chunk, a chunk just polled, or one that left the cache. The release paths take a cache decision only
         * when it is on a queue, with one null check.
         */
        ChunkQueue queue;
        // Links of the ChunkQueue this chunk is on, if any.
        Chunk prevInQueue;
        Chunk nextInQueue;
        /**
         * Link in its cache's {@link PendingChunks}: {@code null} = not queued for attention, non-null = queued (or in
         * the middle of being queued). This field <em>is</em> the dedup claim: whoever moves it off {@code null} owns
         * the push, so no separate flag is needed.
         */
        volatile Chunk pendingNext;
        protected AbstractByteBuf delegate;
        // We need the top-level allocator so ByteBuf.capacity(int) can call reallocate()
        final AdaptivePoolingAllocator allocator;
        final int capacity;
        private final boolean pooled;

        Chunk() {
            // Constructor only used by the PendingChunks end marker.
            delegate = null;
            allocator = null;
            capacity = 0;
            pooled = false;
        }

        /**
         * Constructor for an unpooled chunk: a one-shot {@link BuddyChunk}.
         */
        Chunk(AbstractByteBuf delegate, AdaptivePoolingAllocator allocator) {
            this.delegate = delegate;
            this.pooled = false;
            capacity = delegate.capacity();
            this.allocator = allocator;
        }

        /**
         * Constructor for a pooled chunk, created by a magazine.
         *
         * @param threadLocal whether the creating magazine belongs to a thread-local heap, for the JFR event.
         */
        Chunk(AbstractByteBuf delegate, AdaptivePoolingAllocator allocator, boolean threadLocal) {
            this.delegate = delegate;
            this.pooled = true;
            capacity = delegate.capacity();
            this.allocator = allocator;

            if (PlatformDependent.isJfrEnabled() && AllocateChunkEvent.isEventEnabled()) {
                AllocateChunkEvent event = new AllocateChunkEvent();
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator.class);
                    event.pooled = true;
                    event.threadLocal = threadLocal;
                    event.commit();
                }
            }
        }

        /**
         * Called when a ByteBuf is done using its allocation in this chunk.
         */
        abstract void releaseSegment(int startIndex, int size);

        /**
         * Whether this chunk is attached to a magazine of a thread-local heap right now, for the JFR events.
         */
        boolean inThreadLocalMagazine() {
            return false;
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

    static final class IntStack {

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
        /**
         * Snapshot behind {@link #remainingCapacity()}: bytes handed out since the last refresh from the free lists.
         * Segments returned since then are not subtracted, so {@code capacity - allocatedBytes} never counts a
         * segment that is not free.
         */
        private int allocatedBytes;

        final SizeClassedChunkCache owningCache;

        /**
         * Constructor only used by {@link PendingChunks}' end marker.
         */
        SizeClassedChunk() {
            segmentSize = 0;
            segments = 0;
            ownerThread = null;
            owningCache = null;
        }

        SizeClassedChunk(AbstractByteBuf delegate, SizeClassMagazine magazine,
                         SizeClassChunkController controller) {
            super(delegate, magazine.allocator, magazine.ownerThread != null);
            segmentSize = controller.segmentSize;
            segments = controller.chunkSize / segmentSize;
            STATE.lazySet(this, AVAILABLE);
            ownerThread = magazine.ownerThread;
            owningCache = magazine.chunkCache;
            if (ownerThread == null) {
                externalFreeList = controller.createFreeList();
                localFreeList = controller.createEmptyLocalFreeList();
            } else {
                externalFreeList = controller.createEmptyFreeList();
                localFreeList = controller.createLocalFreeList();
            }
        }

        /**
         * Constructor for recycled parts: reuses a recycled delegate buffer and the two free lists that came with it.
         * The lists were sized for the size class that freed the buffer; one that holds fewer than this chunk's
         * segments is replaced, one that holds more is kept.
         */
        SizeClassedChunk(AbstractByteBuf recycledDelegate, MpscIntQueue recycledFreeList,
                         IntStack recycledLocalFreeList,
                         SizeClassMagazine magazine, SizeClassChunkController controller) {
            super(recycledDelegate, magazine.allocator, magazine.ownerThread != null);
            segmentSize = controller.segmentSize;
            segments = controller.chunkSize / segmentSize;
            MpscIntQueue externalFreeList = recycledFreeList.capacity() >= segments ?
                    recycledFreeList : controller.createEmptyFreeList();
            boolean reuseLocal = recycledLocalFreeList.capacity() >= segments;
            this.externalFreeList = externalFreeList;
            STATE.lazySet(this, AVAILABLE);
            ownerThread = magazine.ownerThread;
            owningCache = magazine.chunkCache;
            if (ownerThread != null) {
                if (reuseLocal) {
                    localFreeList = recycledLocalFreeList;
                    localFreeList.refill(segments, segmentSize);
                } else {
                    localFreeList = controller.createLocalFreeList();
                }
                externalFreeList.resetAndFill(0, segmentSize);
            } else {
                if (reuseLocal) {
                    localFreeList = recycledLocalFreeList;
                    localFreeList.refill(0, segmentSize);
                } else {
                    localFreeList = controller.createEmptyLocalFreeList();
                }
                externalFreeList.resetAndFill(segments, segmentSize);
            }
        }

        /**
         * Called when a magazine is done using this chunk, probably because it was emptied: it stops being the
         * cache's active chunk and is filed by capacity. {@link #owningCache} is the cache of the one magazine that
         * ever allocates from this chunk.
         */
        void releaseFromMagazine() {
            owningCache.deactivate(this);
        }

        /**
         * Only read from {@link AdaptiveByteBuf#init}, reached from {@link #readInitInto} on the magazine's active
         * chunk, so this chunk is always attached to its magazine here, and that magazine is a thread-local one exactly
         * when this chunk has an owner thread.
         */
        @Override
        boolean inThreadLocalMagazine() {
            return ownerThread != null;
        }

        boolean readInitInto(AdaptiveByteBuf buf, int size, int startingCapacity, int maxCapacity) {
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

        /**
         * Whether this chunk has a free segment, as the cache files it (reusable or exhausted) and probes it.
         * Unlike {@link #remainingCapacity()} it never refreshes the snapshot.
         */
        public boolean hasRemainingCapacity() {
            int remaining = capacity - allocatedBytes;
            if (remaining > 0) {
                return true;
            }
            return !localFreeList.isEmpty() || !externalFreeList.isEmpty();
        }

        boolean hasFullCapacity() {
            int localSize = localFreeList.size();
            return localSize == segments || localSize + externalFreeList.size() == segments;
        }

        /**
         * The free bytes of this chunk as the magazine sees it after each allocation. While the snapshot is above
         * one segment it is returned as is, without touching the free lists; at or below one segment the free lists
         * are counted and the snapshot refreshed. Before the first refresh the snapshot also counts the tail of the
         * chunk that is too small for a segment, when the chunk size is not a multiple of the segment size.
         */
        public int remainingCapacity() {
            int remaining = capacity - allocatedBytes;
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
                final SizeClassedChunkCache cache = owningCache;
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
            // Neither a chunk out of the cache nor the magazine's active chunk is ever moved or evicted
            // by a segment return: the active chunk consumes its own returned segments.
            if (queue != null) {
                owningCache.refile(this);
            }
        }

        /** Locked counterpart of {@link #afterLocalRelease()}; caller holds the stripe lock. */
        private void afterLockedRelease(SizeClassedChunkCache cache) {
            int state = this.state;
            if (state != AVAILABLE) {
                updateStateOnLockedReleaseSegment(state);
                return;
            }
            if (queue != null) {
                cache.refile(this);
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
            if (recycler != null && recycler.offer(delegate, externalFreeList, localFreeList, sizeClassIndex)) {
                delegate = null;
            }
            externalFreeList = null;
            localFreeList = null;
            markToDeallocate();
        }

        void markToDeallocate() {
            MpscIntQueue fl = externalFreeList;
            if (fl == null) {
                // The free lists went to the recycler with the buffer, or were dropped with it when its pool was
                // full. No outstanding segments are possible since the chunk had full capacity when it was stripped.
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

    /**
     * A ref-counted chunk handing out power-of-two blocks from a buddy tree, for sizes above the size classes.
     * <p>
     * A chunk without a tree is <em>one-shot</em>: it holds exactly one buffer spanning the whole chunk, for
     * allocations that are not pooled. It belongs to no magazine, is never cached, and is freed when its buffer
     * is released.
     */
    private static final class BuddyChunk extends Chunk implements IntConsumer {
        private static final int MIN_BUDDY_SIZE = BuddyTree.MIN_BLOCK_SIZE;
        private static final int PACK_OFFSET_MASK = 0xFFFF;
        private static final int PACK_SIZE_SHIFT = Integer.SIZE - Integer.numberOfLeadingZeros(PACK_OFFSET_MASK);

        // Always populate the refCnt field, so HotSpot doesn't emit `null` checks.
        // This is safe to do even on native-image.
        final RefCnt refCnt = new RefCnt();
        // null for a one-shot chunk.
        private final MpscIntQueue freeList;
        // null for a one-shot chunk.
        private final BuddyTree tree;
        private final int freeListCapacity;
        /** The magazine this chunk belongs to for its whole life, or {@code null} for a one-shot chunk. */
        private final BuddyMagazine owner;

        /**
         * Constructor for a one-shot chunk: no tree, no magazine. The caller owns the reference it gets here and
         * must {@link #release()} it once {@link #readInitOneShot} returned.
         */
        BuddyChunk(AbstractByteBuf delegate, AdaptivePoolingAllocator allocator) {
            super(delegate, allocator);
            freeList = null;
            tree = null;
            freeListCapacity = 0;
            owner = null;
        }

        BuddyChunk(AbstractByteBuf delegate, BuddyMagazine owner) {
            // Buddy magazines live on the shared stripes only, so a buddy chunk is never thread-local.
            super(delegate, owner.allocator, false);
            this.owner = owner;
            freeListCapacity = delegate.capacity() / MIN_BUDDY_SIZE;
            freeList = MpscIntQueue.create(freeListCapacity, -1); // At most half of tree (all leaf nodes) can be freed.
            tree = new BuddyTree(delegate.capacity());
        }

        /**
         * Claim a free block of {@code blockSize} for {@code buf}, after applying the blocks released since the last
         * look. Owner (stripe lock holder) only.
         */
        boolean readInitInto(AdaptiveByteBuf buf, int size, int blockSize, int maxCapacity) {
            processFreelistEntries();
            int startIndex = tree.claim(blockSize);
            if (startIndex == -1) {
                return false;
            }
            BuddyChunk chunk = this;
            chunk.retain();
            try {
                buf.init(delegate, this, 0, 0, startIndex, size, blockSize, maxCapacity);
                chunk = null;
            } finally {
                if (chunk != null) {
                    tree.release(startIndex, blockSize);
                    // If chunk is not null we know that buf.init(...) failed and so we need to manually release
                    // the chunk again as we retained it before calling buf.init(...).
                    chunk.release();
                }
            }
            return true;
        }

        /**
         * Initialize {@code buf} over the whole of this one-shot chunk. On success the buffer holds a reference to
         * this chunk.
         */
        void readInitOneShot(AdaptiveByteBuf buf, int size, int maxCapacity) {
            assert tree == null : "not a one-shot chunk";
            retain();
            boolean initialized = false;
            try {
                buf.init(delegate, this, 0, 0, 0, size, size, maxCapacity);
                initialized = true;
            } finally {
                if (!initialized) {
                    // buf.init(...) failed: drop the reference taken for the buffer.
                    release();
                }
            }
        }

        @Override
        public void accept(int packed) {
            // Called by allocating thread when draining freeList.
            int size = unpackSize(packed);
            int offset = unpackOffset(packed);
            tree.release(offset, size);
        }

        private static int unpackSize(int packed) {
            return MIN_BUDDY_SIZE << (packed >> PACK_SIZE_SHIFT);
        }

        private static int unpackOffset(int packed) {
            return (packed & PACK_OFFSET_MASK) * MIN_BUDDY_SIZE;
        }

        /**
         * Any thread. If the stripe lock is free, take it and put the block straight back in the tree, refiling the
         * chunk. Otherwise put the block on the free list, then leave a note for the owner: offer before note, so a
         * drain that pops the note sees the block. Either way the buffer's reference is dropped last, so the chunk
         * is alive throughout. A one-shot chunk only drops the reference.
         */
        @Override
        void releaseSegment(int startingIndex, int size) {
            MpscIntQueue freeList = this.freeList;
            if (freeList != null) {
                final BuddyMagazine owner = this.owner;
                final long stamp = owner.tryLockForRelease();
                if (stamp != 0) {
                    try {
                        owner.releaseInPlace(this, startingIndex, size);
                    } finally {
                        owner.unlockAfterRelease(stamp);
                    }
                } else {
                    int packedOffset = startingIndex / MIN_BUDDY_SIZE;
                    int packedSize = Integer.numberOfTrailingZeros(size / MIN_BUDDY_SIZE) << PACK_SIZE_SHIFT;
                    freeList.offer(packedOffset | packedSize);
                    owner.pending.push(this);
                }
            }
            release();
        }

        void markToDeallocate() {
            release();
        }

        private void retain() {
            RefCnt.retain(refCnt);
        }

        void release() {
            if (RefCnt.release(refCnt)) {
                deallocate();
            }
        }

        /** Owner or stripe lock holder only. */
        void releaseToTree(int offset, int size) {
            tree.release(offset, size);
        }

        boolean hasUnprocessedFreelistEntries() {
            return !freeList.isEmpty();
        }

        /**
         * Apply the blocks released by any thread to the tree. Owner only.
         */
        void processFreelistEntries() {
            if (!freeList.isEmpty()) {
                freeList.drain(freeListCapacity, this);
            }
        }

        /**
         * The order of the largest free block in the tree, or -1; exact once {@link #processFreelistEntries} ran.
         */
        int largestFreeOrder() {
            return tree.largestFreeOrder();
        }

        boolean isWhollyFree() {
            return tree.isWhollyFree();
        }

        @Override
        public String toString() {
            int capacity = delegate.capacity();
            if (tree == null) {
                return "BuddyChunk[one-shot, capacity: " + capacity + ']';
            }
            return "BuddyChunk[capacity: " + capacity +
                    ", largest free order: " + tree.largestFreeOrder() +
                    ", free list: " + freeList.size() + ']';
        }
    }

    /**
     * The buddy tree of a {@link BuddyChunk}: hands out power-of-two blocks of at least {@link #MIN_BLOCK_SIZE} from
     * a capacity that is a power-of-two multiple of it, and merges freed buddies back. Not thread-safe: used by the
     * chunk's magazine only (frees from other threads reach it through the chunk's free list).
     * <p>
     * An implicit binary tree over the blocks: node 1 is the whole chunk, the children of node {@code i} are its two
     * halves {@code 2i} and {@code 2i + 1}, and the leaves are the {@link #MIN_BLOCK_SIZE} blocks. Each node stores
     * the order of the largest free block in its subtree plus one, 0 when nothing in it is free (order {@code k} is a
     * block of {@code MIN_BLOCK_SIZE << k}). A claim follows the leftmost child that fits down to the order asked
     * for, and a release walks up merging buddies: both are one pass along a root-to-leaf path.
     */
    static final class BuddyTree {
        static final int MIN_BLOCK_SIZE = 32768;

        private final byte[] nodes;
        private final int maxOrder;

        BuddyTree(int capacity) {
            int leaves = capacity / MIN_BLOCK_SIZE;
            assert leaves > 0 && (leaves & leaves - 1) == 0 : "capacity " + capacity;
            maxOrder = Integer.numberOfTrailingZeros(leaves);
            byte[] nodes = new byte[leaves << 1];
            // All free: the nodes at depth d, [2^d, 2^(d+1)), are whole blocks of order maxOrder - d.
            // One constant per level. A loop computing each node's order (numberOfLeadingZeros of its index) into
            // the byte array was miscompiled by JDK 21's C2 (SuperWord), which built corrupt trees.
            for (int depth = 0; depth <= maxOrder; depth++) {
                Arrays.fill(nodes, 1 << depth, 2 << depth, (byte) (maxOrder - depth + 1));
            }
            this.nodes = nodes;
        }

        /**
         * The order of the largest free block, or -1 when no block is free: exact, it is the root's value.
         */
        int largestFreeOrder() {
            return nodes[1] - 1;
        }

        /**
         * Whether no block is claimed.
         */
        boolean isWhollyFree() {
            return nodes[1] == maxOrder + 1;
        }

        /**
         * Claim the leftmost free block of {@code size} and return its offset, or -1 if there is none. Blocks are
         * powers of two of at least {@link #MIN_BLOCK_SIZE}: any other size is never claimed, and returns -1.
         */
        int claim(int size) {
            if (size < MIN_BLOCK_SIZE || (size & size - 1) != 0) {
                // BuddyMagazine asks for a chunk's remaining capacity, which is the sum of its free blocks.
                return -1;
            }
            int order = Integer.numberOfTrailingZeros(size / MIN_BLOCK_SIZE);
            byte[] nodes = this.nodes;
            int wanted = order + 1;
            if (order > maxOrder || nodes[1] < wanted) {
                return -1;
            }
            int index = 1;
            for (int depth = maxOrder - order; depth > 0; depth--) {
                index <<= 1;
                if (nodes[index] < wanted) {
                    index++;
                }
            }
            nodes[index] = 0;
            updateAncestors(index, order);
            return (index - (1 << maxOrder - order)) * size;
        }

        /**
         * Give back the block of {@code size} at {@code offset}, claimed earlier.
         */
        void release(int offset, int size) {
            int order = Integer.numberOfTrailingZeros(size / MIN_BLOCK_SIZE);
            if ((offset & size - 1) != 0) {
                throw new IllegalStateException("No block of size " + size + " at offset " + offset);
            }
            int index = (1 << maxOrder - order) + offset / size;
            assert nodes[index] == 0 : "no block of size " + size + " claimed at offset " + offset;
            nodes[index] = (byte) (order + 1);
            updateAncestors(index, order);
        }

        /**
         * Recompute the ancestors of {@code index}, a node of {@code order}, after its value changed; stops at the
         * first ancestor whose value stays the same.
         */
        private void updateAncestors(int index, int order) {
            byte[] nodes = this.nodes;
            while (index > 1) {
                index >>= 1;
                order++;
                int left = nodes[index << 1];
                int right = nodes[(index << 1) + 1];
                // Two whole free halves (each of this node's order minus one, stored plus one) merge into one block.
                int value = left == order && right == order ? order + 1 : Math.max(left, right);
                if (nodes[index] == value) {
                    return;
                }
                nodes[index] = (byte) value;
            }
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
                    event.chunkThreadLocal = wrapped.inThreadLocalMagazine();
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
            if (src == tmpNioBuf) {
                src = src.duplicate();
            }
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
            checkIndex(index, length);
            ByteBuffer buf = internalNioBuffer().duplicate();
            buf.clear().position(index).limit(index + length);
            return out.write(buf);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length)
                throws IOException {
            checkIndex(index, length);
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
