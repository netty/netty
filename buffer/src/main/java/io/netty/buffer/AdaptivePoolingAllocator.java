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
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.MpscIntQueue;
import io.netty.util.internal.AtomicReferenceCountUpdater;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ReferenceCountUpdater;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThreadExecutorMap;
import io.netty.util.internal.UnsafeReferenceCountUpdater;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.VarHandleReferenceCountUpdater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntSupplier;

import static io.netty.util.internal.ReferenceCountUpdater.getUnsafeOffset;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

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
 * The {@link #createSharedChunkQueue(boolean)} method can be overridden to customize this queue.
 */
@UnstableApi
final class AdaptivePoolingAllocator {
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
    private static final int MAX_STRIPES = NettyRuntime.availableProcessors() * 2;
    private static final int BUFS_PER_CHUNK = 8; // For large buffers, aim to have about this many buffers per chunk.

    /**
     * The maximum size of a pooled chunk, in bytes. Allocations bigger than this will never be pooled.
     * <p>
     * This number is 8 MiB, and is derived from the limitations of internal histograms.
     */
    private static final int MAX_CHUNK_SIZE = 8 * 1024 * 1024; // 8 MiB.
    private static final int MAX_POOLED_BUF_SIZE = MAX_CHUNK_SIZE / BUFS_PER_CHUNK;

    /**
     * The capacity if the chunk reuse queues, that allow chunks to be shared across magazines in a group.
     * The default size is twice {@link NettyRuntime#availableProcessors()},
     * same as the maximum number of magazines per magazine group.
     */
    private static final int CHUNK_REUSE_QUEUE = Math.max(2, SystemPropertyUtil.getInt(
            "io.netty.allocator.chunkReuseQueueCapacity", NettyRuntime.availableProcessors() * 2));

    private static final int LOCAL_CHUNK_REUSE_QUEUE_CAPACITY = SystemPropertyUtil.getInt(
            "io.netty.allocator.localChunkReuseQueueCapacity", 8);

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
    private static final byte[] SIZE_INDEXES = new byte[(SIZE_CLASSES[SIZE_CLASSES_COUNT - 1] / 32) + 1];

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
    private final FastThreadLocal<ThreadLocalCache> threadLocalCache;

    AdaptivePoolingAllocator(ChunkAllocator chunkAllocator, boolean useCacheForNonEventLoopThreads) {
        this.chunkAllocator = ObjectUtil.checkNotNull(chunkAllocator, "chunkAllocator");
        chunkRegistry = new ChunkRegistry();
        sizeClassedMagazineGroups = createMagazineGroupSizeClasses(this, false);
        largeBufferMagazineGroup = new MagazineGroup(
                this, chunkAllocator, new HistogramChunkControllerFactory(true), false, null);

        threadLocalCache = new FastThreadLocal<ThreadLocalCache>() {
            @Override
            protected ThreadLocalCache initialValue() {
                if (useCacheForNonEventLoopThreads || ThreadExecutorMap.currentExecutor() != null) {
                    return new ThreadLocalCache(AdaptivePoolingAllocator.this);
                }
                return null;
            }

            @Override
            protected void onRemoval(final ThreadLocalCache cache) throws Exception {
                if (cache != null) {
                    cache.free();
                }
            }
        };
    }

    private static MagazineGroup[] createMagazineGroupSizeClasses(
            AdaptivePoolingAllocator allocator, boolean isThreadLocal) {
        return createMagazineGroupSizeClasses(allocator, isThreadLocal, null);
    }

    private static MagazineGroup[] createMagazineGroupSizeClasses(
            AdaptivePoolingAllocator allocator, boolean isThreadLocal, ThreadLocalCache cache) {
        MagazineGroup[] groups = new MagazineGroup[SIZE_CLASSES.length];
        for (int i = 0; i < SIZE_CLASSES.length; i++) {
            int segmentSize = SIZE_CLASSES[i];
            groups[i] = new MagazineGroup(allocator, allocator.chunkAllocator,
                                          new SizeClassChunkControllerFactory(segmentSize), isThreadLocal, cache);
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
    private static Queue<Chunk> createSharedChunkQueue(boolean shared) {
        return shared ? PlatformDependent.newFixedMpmcQueue(CHUNK_REUSE_QUEUE) :
                PlatformDependent.newFixedMpscQueue(CHUNK_REUSE_QUEUE);
    }

    ByteBuf allocate(int size, int maxCapacity) {
        return allocate(size, maxCapacity, Thread.currentThread(), null);
    }

    private AdaptiveByteBuf allocate(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
        AdaptiveByteBuf allocated = null;
        if (size <= MAX_POOLED_BUF_SIZE) {
            final int index = sizeClassIndexOf(size);
            MagazineGroup[] magazineGroups;
            ThreadLocalCache cache;
            if (!FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals() ||
                (cache = threadLocalCache.get()) == null) {
                magazineGroups =  sizeClassedMagazineGroups;
            } else {
                magazineGroups = cache.magazineGroups;
            }
            if (index < magazineGroups.length) {
                allocated = magazineGroups[index].allocate(size, maxCapacity, currentThread, buf);
            } else {
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

    private AdaptiveByteBuf allocateFallback(int size, int maxCapacity, Thread currentThread,
                                             AdaptiveByteBuf buf) {
        // If we don't already have a buffer, obtain one from the most conveniently available magazine.
        AbstractMagazine magazine;
        if (buf != null) {
            Chunk chunk = buf.chunk;
            if (chunk == null || chunk == SharedMagazine.MAGAZINE_FREED ||
                (magazine = chunk.currentMagazine()) == null) {
                magazine = getFallbackMagazine(currentThread);
            }
        } else {
            magazine = getFallbackMagazine(currentThread);
            buf = magazine.newBuffer();
        }
        // Create a one-off chunk for this allocation.
        BumpChunk.allocateOneOffWith(buf, chunkAllocator, size, maxCapacity, magazine, chunkRegistry);
        return buf;
    }

    private AbstractMagazine getFallbackMagazine(Thread currentThread) {
        AbstractMagazine[] mags = largeBufferMagazineGroup.magazines;
        return mags[(int) currentThread.getId() & mags.length - 1];
    }

    /**
     * Allocate into the given buffer. Used by {@link AdaptiveByteBuf#capacity(int)}.
     */
    void reallocate(int size, int maxCapacity, AdaptiveByteBuf into) {
        AdaptiveByteBuf result = allocate(size, maxCapacity, Thread.currentThread(), into);
        assert result == into: "Re-allocation created separate buffer instance";
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
            super.finalize();
        } finally {
            free();
        }
    }

    private void free() {
        largeBufferMagazineGroup.free();
    }

    static int sizeToBucket(int size) {
        return HistogramChunkController.sizeToBucket(size);
    }

    private static final class ThreadLocalCache {
        final MagazineGroup[] magazineGroups;
        final Queue<AdaptiveByteBuf> externalRecycledBuffers;
        final ArrayDeque<AdaptiveByteBuf> localRecycledBuffers;

        ThreadLocalCache(AdaptivePoolingAllocator allocator) {
            this.externalRecycledBuffers = PlatformDependent.newFixedMpscQueue(MAGAZINE_BUFFER_QUEUE_CAPACITY);
            this.localRecycledBuffers = new ArrayDeque<>();
            this.magazineGroups = createMagazineGroupSizeClasses(allocator, true, this);
        }

        void free() {
            for (MagazineGroup group : magazineGroups) {
                group.free();
            }
        }
    }

    private static final class MagazineGroup {
        private final AdaptivePoolingAllocator allocator;
        private final ChunkAllocator chunkAllocator;
        private final ChunkControllerFactory chunkControllerFactory;
        private final Queue<Chunk> externalChunkQueue;
        private final StampedLock magazineExpandLock;
        private final AbstractMagazine magazine;
        private volatile AbstractMagazine[] magazines;
        private final Thread ownerThread;
        private volatile boolean freed;

        MagazineGroup(AdaptivePoolingAllocator allocator,
                      ChunkAllocator chunkAllocator,
                      ChunkControllerFactory chunkControllerFactory,
                      boolean isThreadLocal,
                      ThreadLocalCache cache) {
            this.allocator = allocator;
            this.chunkAllocator = chunkAllocator;
            this.chunkControllerFactory = chunkControllerFactory;
            this.externalChunkQueue = createSharedChunkQueue(!isThreadLocal);

            if (isThreadLocal) {
                ownerThread = Thread.currentThread();
                magazineExpandLock = null;
                magazine = new ThreadLocalMagazine(this, chunkControllerFactory.create(this),
                                                   cache.externalRecycledBuffers, cache.localRecycledBuffers);
            } else {
                ownerThread = null;
                magazineExpandLock = new StampedLock();
                magazine = null;
                AbstractMagazine[] mags = new AbstractMagazine[INITIAL_MAGAZINES];
                for (int i = 0; i < mags.length; i++) {
                    mags[i] = new SharedMagazine(this, chunkControllerFactory.create(this));
                }
                magazines = mags;
            }
        }

        public AdaptiveByteBuf allocate(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
            boolean reallocate = buf != null;

            // Path for thread-local allocation.
            AbstractMagazine tlMag = magazine;
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
            AbstractMagazine[] mags;
            int expansions = 0;
            do {
                mags = magazines;
                int mask = mags.length - 1;
                int index = (int) (threadId & mask);
                for (int i = 0, m = mags.length << 1; i < m; i++) {
                    AbstractMagazine mag = mags[index + i & mask];
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
            final AbstractMagazine[] mags;
            long writeLock = magazineExpandLock.tryWriteLock();
            if (writeLock != 0) {
                try {
                    mags = magazines;
                    if (mags.length >= MAX_STRIPES || mags.length > currentLength || freed) {
                        return true;
                    }
                    AbstractMagazine firstMagazine = mags[0];
                    AbstractMagazine[] expanded = new AbstractMagazine[mags.length * 2];
                    for (int i = 0, l = expanded.length; i < l; i++) {
                        AbstractMagazine m = new SharedMagazine(this, chunkControllerFactory.create(this));
                        firstMagazine.initializeSharedStateIn(m);
                        expanded[i] = m;
                    }
                    magazines = expanded;
                } finally {
                    magazineExpandLock.unlockWrite(writeLock);
                }
                for (AbstractMagazine magazine : mags) {
                    magazine.free();
                }
            }
            return true;
        }

        boolean offerToQueue(Chunk buffer) {
            if (freed) {
                return false;
            }

            boolean offered = externalChunkQueue.offer(buffer);

            if (freed && offered) {
                freeChunkReuseQueue();
            }
            return offered;
        }

        private Chunk pollFromExternalQueue() {
            return externalChunkQueue.poll();
        }

        private void free() {
            freed = true;
            if (magazine != null) {
                magazine.free();
            } else {
                long stamp = magazineExpandLock.writeLock();
                try {
                    AbstractMagazine[] mags = magazines;
                    for (AbstractMagazine magazine : mags) {
                        magazine.free();
                    }
                } finally {
                    magazineExpandLock.unlockWrite(stamp);
                }
            }
            freeChunkReuseQueue();
        }

        private void freeChunkReuseQueue() {
            for (;;) {
                Chunk chunk = externalChunkQueue.poll();
                if (chunk == null) {
                    break;
                }
                chunk.markToDeallocate();
            }
        }
    }

    private interface ChunkControllerFactory {
        ChunkController create(MagazineGroup group);
    }

    private interface ChunkController {
        /**
         * Compute the "fast max capacity" value for the buffer.
         */
        int computeBufferCapacity(int requestedSize, int maxCapacity, boolean isReallocation);

        /**
         * Initialize the given chunk factory with shared statistics state (if any) from this factory.
         */
        void initializeSharedStateIn(ChunkController chunkController);

        /**
         * Allocate a new {@link Chunk} for the given {@link AbstractMagazine}.
         */
        Chunk newChunkAllocation(int promptingSize, AbstractMagazine magazine);
    }

    private interface ChunkReleasePredicate {
        boolean shouldReleaseChunk(int chunkSize);
    }

    private static final class SizeClassChunkControllerFactory implements ChunkControllerFactory {
        // To amortize activation/deactivation of chunks, we should have a minimum number of segments per chunk.
        // We choose 32 because it seems neither too small nor too big.
        // For segments of 16 KiB, the chunks will be half a megabyte.
        private static final int MIN_SEGMENTS_PER_CHUNK = 32;
        private final int segmentSize;
        private final int chunkSize;
        private final int[] segmentOffsets;

        private SizeClassChunkControllerFactory(int segmentSize) {
            this.segmentSize = ObjectUtil.checkPositive(segmentSize, "segmentSize");
            this.chunkSize = Math.max(MIN_CHUNK_SIZE, segmentSize * MIN_SEGMENTS_PER_CHUNK);
            int segmentsCount = chunkSize / segmentSize;
            this.segmentOffsets = new int[segmentsCount];
            for (int i = 0; i < segmentsCount; i++) {
                segmentOffsets[i] = i * segmentSize;
            }
        }

        @Override
        public ChunkController create(MagazineGroup group) {
            return new SizeClassChunkController(group, segmentSize, chunkSize, segmentOffsets);
        }
    }

    private static final class SizeClassChunkController implements ChunkController {

        private final ChunkAllocator chunkAllocator;
        private final int segmentSize;
        private final int chunkSize;
        private final ChunkRegistry chunkRegistry;
        private final int[] segmentOffsets;

        private SizeClassChunkController(MagazineGroup group, int segmentSize, int chunkSize, int[] segmentOffsets) {
            chunkAllocator = group.chunkAllocator;
            this.segmentSize = segmentSize;
            this.chunkSize = chunkSize;
            chunkRegistry = group.allocator.chunkRegistry;
            this.segmentOffsets = segmentOffsets;
        }

        @Override
        public int computeBufferCapacity(
                int requestedSize, int maxCapacity, boolean isReallocation) {
            return Math.min(segmentSize, maxCapacity);
        }

        @Override
        public void initializeSharedStateIn(ChunkController chunkController) {
            // NOOP
        }

        @Override
        public Chunk newChunkAllocation(int promptingSize, AbstractMagazine magazine) {
            AbstractByteBuf chunkBuffer = chunkAllocator.allocate(chunkSize, chunkSize);
            assert chunkBuffer.capacity() == chunkSize;
            SizeClassedChunk chunk = new SizeClassedChunk(chunkBuffer, magazine, segmentSize, segmentOffsets);
            chunkRegistry.add(chunk);
            return chunk;
        }
    }

    private static final class HistogramChunkControllerFactory implements ChunkControllerFactory {
        private final boolean shareable;

        private HistogramChunkControllerFactory(boolean shareable) {
            this.shareable = shareable;
        }

        @Override
        public ChunkController create(MagazineGroup group) {
            return new HistogramChunkController(group, shareable);
        }
    }

    private static final class HistogramChunkController implements ChunkController, ChunkReleasePredicate {
        private static final int MIN_DATUM_TARGET = 1024;
        private static final int MAX_DATUM_TARGET = 65534;
        private static final int INIT_DATUM_TARGET = 9;
        private static final int HISTO_BUCKET_COUNT = 16;
        private static final int[] HISTO_BUCKETS = {
                16 * 1024,
                24 * 1024,
                32 * 1024,
                48 * 1024,
                64 * 1024,
                96 * 1024,
                128 * 1024,
                192 * 1024,
                256 * 1024,
                384 * 1024,
                512 * 1024,
                768 * 1024,
                1024 * 1024,
                1792 * 1024,
                2048 * 1024,
                3072 * 1024
        };

        private final MagazineGroup group;
        private final boolean shareable;
        private final short[][] histos = {
                new short[HISTO_BUCKET_COUNT], new short[HISTO_BUCKET_COUNT],
                new short[HISTO_BUCKET_COUNT], new short[HISTO_BUCKET_COUNT],
        };
        private final ChunkRegistry chunkRegistry;
        private short[] histo = histos[0];
        private final int[] sums = new int[HISTO_BUCKET_COUNT];

        private int histoIndex;
        private int datumCount;
        private int datumTarget = INIT_DATUM_TARGET;
        private boolean hasHadRotation;
        private volatile int sharedPrefChunkSize = MIN_CHUNK_SIZE;
        private volatile int localPrefChunkSize = MIN_CHUNK_SIZE;
        private volatile int localUpperBufSize;

        private HistogramChunkController(MagazineGroup group, boolean shareable) {
            this.group = group;
            this.shareable = shareable;
            chunkRegistry = group.allocator.chunkRegistry;
        }

        @Override
        public int computeBufferCapacity(
                int requestedSize, int maxCapacity, boolean isReallocation) {
            if (!isReallocation) {
                // Only record allocation size if it's not caused by a reallocation that was triggered by capacity
                // change of the buffer.
                recordAllocationSize(requestedSize);
            }

            // Predict starting capacity from localUpperBufSize, but place limits on the max starting capacity
            // based on the requested size, because localUpperBufSize can potentially be quite large.
            int startCapLimits;
            if (requestedSize <= 32768) { // Less than or equal to 32 KiB.
                startCapLimits = 65536; // Use at most 64 KiB, which is also the AdaptiveRecvByteBufAllocator max.
            } else {
                startCapLimits = requestedSize * 2; // Otherwise use at most twice the requested memory.
            }
            int startingCapacity = Math.min(startCapLimits, localUpperBufSize);
            startingCapacity = Math.max(requestedSize, Math.min(maxCapacity, startingCapacity));
            return startingCapacity;
        }

        private void recordAllocationSize(int bufferSizeToRecord) {
            // Use the preserved size from the reused AdaptiveByteBuf, if available.
            // Otherwise, use the requested buffer size.
            // This way, we better take into account
            if (bufferSizeToRecord == 0) {
                return;
            }
            int bucket = sizeToBucket(bufferSizeToRecord);
            histo[bucket]++;
            if (datumCount++ == datumTarget) {
                rotateHistograms();
            }
        }

        static int sizeToBucket(int size) {
            int index = binarySearchInsertionPoint(Arrays.binarySearch(HISTO_BUCKETS, size));
            return index >= HISTO_BUCKETS.length ? HISTO_BUCKETS.length - 1 : index;
        }

        private static int binarySearchInsertionPoint(int index) {
            if (index < 0) {
                index = -(index + 1);
            }
            return index;
        }

        static int bucketToSize(int sizeBucket) {
            return HISTO_BUCKETS[sizeBucket];
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
            hasHadRotation = true;
            int percentileSize = bucketToSize(sizeBucket);
            int prefChunkSize = Math.max(percentileSize * BUFS_PER_CHUNK, MIN_CHUNK_SIZE);
            localUpperBufSize = percentileSize;
            localPrefChunkSize = prefChunkSize;
            if (shareable) {
                for (AbstractMagazine mag : group.magazines) {
                    HistogramChunkController statistics = (HistogramChunkController) mag.chunkController;
                    prefChunkSize = Math.max(prefChunkSize, statistics.localPrefChunkSize);
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
        int preferredChunkSize() {
            return sharedPrefChunkSize;
        }

        @Override
        public void initializeSharedStateIn(ChunkController chunkController) {
            HistogramChunkController statistics = (HistogramChunkController) chunkController;
            int sharedPrefChunkSize = this.sharedPrefChunkSize;
            statistics.localPrefChunkSize = sharedPrefChunkSize;
            statistics.sharedPrefChunkSize = sharedPrefChunkSize;
        }

        @Override
        public BumpChunk newChunkAllocation(int promptingSize, AbstractMagazine magazine) {
            int size = Math.max(promptingSize * BUFS_PER_CHUNK, preferredChunkSize());
            int minChunks = size / MIN_CHUNK_SIZE;
            if (MIN_CHUNK_SIZE * minChunks < size) {
                // Round up to nearest whole MIN_CHUNK_SIZE unit. The MIN_CHUNK_SIZE is an even multiple of many
                // popular small page sizes, like 4k, 16k, and 64k, which makes it easier for the system allocator
                // to manage the memory in terms of whole pages. This reduces memory fragmentation,
                // but without the potentially high overhead that power-of-2 chunk sizes would bring.
                size = MIN_CHUNK_SIZE * (1 + minChunks);
            }

            // Limit chunks to the max size, even if the histogram suggests to go above it.
            size = Math.min(size, MAX_CHUNK_SIZE);

            // If we haven't rotated the histogram yet, optimisticly record this chunk size as our preferred.
            if (!hasHadRotation && sharedPrefChunkSize == MIN_CHUNK_SIZE) {
                sharedPrefChunkSize = size;
            }

            ChunkAllocator chunkAllocator = group.chunkAllocator;
            BumpChunk chunk = new BumpChunk(chunkAllocator.allocate(size, size), magazine, true, this);
            chunkRegistry.add(chunk);
            return chunk;
        }

        @Override
        public boolean shouldReleaseChunk(int chunkSize) {
            int preferredSize = preferredChunkSize();
            int givenChunks = chunkSize / MIN_CHUNK_SIZE;
            int preferredChunks = preferredSize / MIN_CHUNK_SIZE;
            int deviation = Math.abs(givenChunks - preferredChunks);

            // Retire chunks with a 5% probability per unit of MIN_CHUNK_SIZE deviation from preference.
            return deviation != 0 &&
                   ThreadLocalRandom.current().nextDouble() * 20.0 < deviation;
        }
    }

    private abstract static class AbstractMagazine implements Recycler.Handle<AdaptiveByteBuf> {
        protected static final AtomicReferenceFieldUpdater<AbstractMagazine, Chunk> NEXT_IN_LINE =
                AtomicReferenceFieldUpdater.newUpdater(AbstractMagazine.class, Chunk.class, "nextInLine");

        @SuppressWarnings("unused") // updated via NEXT_IN_LINE
        protected volatile Chunk nextInLine;
        protected Chunk current;
        protected final MagazineGroup group;
        protected final ChunkController chunkController;
        protected final Queue<AdaptiveByteBuf> externalBuffers;
        protected final Thread ownerThread;
        protected final List<Chunk> localChunkCache;
        private final boolean isShared;

        AbstractMagazine(MagazineGroup group, ChunkController chunkController, Queue<AdaptiveByteBuf> externalBuffers,
                         Thread ownerThread) {
            this.group = group;
            this.chunkController = chunkController;
            this.externalBuffers = externalBuffers;
            this.ownerThread = ownerThread;
            isShared = ownerThread == null;
            localChunkCache = new ArrayList<>(LOCAL_CHUNK_REUSE_QUEUE_CAPACITY);
        }

        /**
         * The main allocation entry point.
         */
        abstract boolean tryAllocate(int size, int maxCapacity, AdaptiveByteBuf buf, boolean reallocate);

        /**
         * Cleans up resources held by the magazine.
         */
        abstract void free();

        /**
         * Tries to get a buffer from the fast, thread-local cache.
         * @return An {@link AdaptiveByteBuf} if successful, {@code null} otherwise.
         */
        abstract AdaptiveByteBuf pollFromLocalCache();

        /**
         * Adds the given value (can be negative) to the magazine's tracked used memory.
         */
        abstract void addUsedMemory(long value);

        /**
         * Returns the total memory currently in use by this magazine.
         */
        public abstract long usedMemory();

        @Override
        public abstract void recycle(AdaptiveByteBuf self);

        public AdaptiveByteBuf newBuffer() {
            AdaptiveByteBuf buf = null;
            if (isOwnerThread()) {
                buf = pollFromLocalCache();
            }
            if (buf == null) {
                buf = externalBuffers.poll();
            }
            if (buf == null) {
                buf = new AdaptiveByteBuf(this);
            }
            buf.resetRefCnt();
            buf.discardMarks();
            return buf;
        }

        boolean offerToQueue(Chunk chunk) {
            return group.offerToQueue(chunk);
        }

        public void initializeSharedStateIn(AbstractMagazine other) {
            chunkController.initializeSharedStateIn(other.chunkController);
        }

        protected final boolean isOwnerThread() {
            return ownerThread != null && Thread.currentThread() == ownerThread;
        }

        protected final Chunk pollFromLocalCache(int size) {
            for (int i = 0; i < localChunkCache.size(); i++) {
                Chunk candidate = localChunkCache.get(i);

                if (candidate.hasRemainingCapacity(size)) {
                    int lastIndex = localChunkCache.size() - 1;

                    if (i < lastIndex) {
                        localChunkCache.set(i, localChunkCache.remove(lastIndex));
                    } else {
                        localChunkCache.remove(lastIndex);
                    }
                    return candidate;
                }
            }
            return null;
        }

        /**
         * Tries to set the given chunk as the next-in-line chunk for this magazine.
         */
        protected boolean trySetNextInLine(Chunk chunk) {
            return NEXT_IN_LINE.compareAndSet(this, null, chunk);
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

    private static class SharedMagazine extends AbstractMagazine {
        private static final AtomicLongFieldUpdater<SharedMagazine> USED_MEMORY_UPDATER;
        static {
            USED_MEMORY_UPDATER = AtomicLongFieldUpdater.newUpdater(SharedMagazine.class, "usedMemory");
        }
        private static final Chunk MAGAZINE_FREED = new BumpChunk();
        private volatile long usedMemory;
        private final StampedLock allocationLock = new StampedLock();

        SharedMagazine(MagazineGroup group, ChunkController chunkController) {
            super(group, chunkController, PlatformDependent.newFixedMpmcQueue(MAGAZINE_BUFFER_QUEUE_CAPACITY), null);
        }

        @Override
        AdaptiveByteBuf pollFromLocalCache() {
            return null;
        }

        @Override
        void addUsedMemory(long value) {
            USED_MEMORY_UPDATER.getAndAdd(this, value);
        }

        @Override
        public long usedMemory() {
            return usedMemory;
        }

        @Override
        public void recycle(AdaptiveByteBuf self) {
            externalBuffers.offer(self);
        }

        @Override
        boolean tryAllocate(int size, int maxCapacity, AdaptiveByteBuf buf, boolean reallocate) {
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
            Chunk curr = nextInLine;
            if (curr == null || !NEXT_IN_LINE.compareAndSet(this, curr, null)) {
                curr = null;
            }
            if (curr == MAGAZINE_FREED) {
                restoreMagazineFreed();
                return false;
            }
            if (curr == null) {
                curr = group.pollFromExternalQueue();
                if (curr == null) {
                    return false;
                }
                curr.attachToMagazine(this);
            }
            boolean allocated = false;
            int remainingCapacity = curr.remainingCapacity();
            int startingCapacity = chunkController.computeBufferCapacity(
                    size, maxCapacity, true /* never update stats as we don't hold the magazine lock */);
            if (remainingCapacity >= size) {
                curr.readInitInto(buf, size, Math.min(remainingCapacity, startingCapacity), maxCapacity);
                allocated = true;
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
                // We have a Chunk that has some space left.
                int remainingCapacity = curr.remainingCapacity();
                if (remainingCapacity > startingCapacity) {
                    curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                    // We still have some bytes left that we can use for the next allocation, just early return.
                    return true;
                }

                // At this point we know that this will be the last time current will be used, so directly set it to
                // null and release it once we are done.
                current = null;
                if (remainingCapacity >= size) {
                    try {
                        curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                        return true;
                    } finally {
                        curr.releaseFromMagazine();
                    }
                }

                // Check if we either retain the chunk in the nextInLine cache or releasing it.
                if (remainingCapacity < RETIRE_CAPACITY) {
                    curr.releaseFromMagazine();
                } else {
                    // See if it makes sense to transfer the Chunk to the nextInLine cache for later usage.
                    // This method will release curr if this is not the case
                    transferToNextInLineOrRelease(curr);
                }
            }

            return allocateAndRefillCurrentChunk(size, maxCapacity, buf, startingCapacity);
        }

        private boolean allocateAndRefillCurrentChunk(int size, int maxCapacity, AdaptiveByteBuf buf,
                                                      int startingCapacity) {
            Chunk curr;
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
                if (remainingCapacity > startingCapacity) {
                    // We have a Chunk that has some space left.
                    curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                    current = curr;
                    return true;
                }

                if (remainingCapacity >= size) {
                    // At this point we know that this will be the last time curr will be used, so directly set it to
                    // null and release it once we are done.
                    try {
                        curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                        return true;
                    } finally {
                        // Release in a finally block so even if readInitInto(...) would throw we would still correctly
                        // release the current chunk before null it out.
                        curr.releaseFromMagazine();
                    }
                } else {
                    // Release it as it's too small.
                    curr.releaseFromMagazine();
                }
            }

            return allocateAndRefillCurrentFromGroup(size, maxCapacity, buf, startingCapacity);
        }

        private boolean allocateAndRefillCurrentFromGroup(int size, int maxCapacity,
                                                          AdaptiveByteBuf buf, int startingCapacity) {
            Chunk curr = pollFromLocalCache(size);
            if (curr == null) {
                // The Local cache was empty or had no suitable chunks.
                // Refill it from the shared queue and search one more time.
                drainToLocalCache();
                curr = pollFromLocalCache(size);
            }

            if (curr == null) {
                curr = chunkController.newChunkAllocation(size, this);
            } else {
                curr.attachToMagazine(this);
            }

            current = curr;
            try {
                int remainingCapacity = curr.remainingCapacity();
                assert remainingCapacity >= size; // Should be guaranteed by pollFromLocalCache or newChunkAllocation
                if (remainingCapacity > startingCapacity) {
                    curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                    curr = null;
                } else {
                    curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                }
            } finally {
                if (curr != null) {
                    // Release in a finally block so even if readInitInto(...) would throw we would still correctly
                    // release the current chunk before null it out.
                    curr.releaseFromMagazine();
                    current = null;
                }
            }
            return true;
        }

        private void drainToLocalCache() {
            while (localChunkCache.size() < LOCAL_CHUNK_REUSE_QUEUE_CAPACITY) {
                Chunk chunk = group.externalChunkQueue.poll();
                if (chunk == null) {
                    break;
                }
                localChunkCache.add(chunk);
            }
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

        @Override
        void free() {
            // Release the current Chunk and the next that was stored for later usage.
            restoreMagazineFreed();
            long stamp = allocationLock.writeLock();
            try {
                if (current != null) {
                    current.releaseFromMagazine();
                    current = null;
                }
            } finally {
                allocationLock.unlockWrite(stamp);
            }
        }
    }

    private static final class ThreadLocalMagazine extends AbstractMagazine {
        private static final AtomicLongFieldUpdater<ThreadLocalMagazine> EXTERNAL_USED_MEMORY_UPDATER =
                AtomicLongFieldUpdater.newUpdater(ThreadLocalMagazine.class, "externalUsedMemory");
        private final ArrayDeque<AdaptiveByteBuf> localBuffers;
        private volatile long externalUsedMemory; // For non-owneer threads
        private long localUsedMemory; // For owner threads

        ThreadLocalMagazine(MagazineGroup group, ChunkController chunkController,
                            Queue<AdaptiveByteBuf> externalBuffers, ArrayDeque<AdaptiveByteBuf> localBuffers) {
            super(group, chunkController, externalBuffers, group.ownerThread);
            this.localBuffers = localBuffers;
        }

        @Override
        AdaptiveByteBuf pollFromLocalCache() {
            return localBuffers.pollLast();
        }

        @Override
        void addUsedMemory(long value) {
            if (isOwnerThread()) {
                localUsedMemory += value;
            } else {
                EXTERNAL_USED_MEMORY_UPDATER.getAndAdd(this, value);
            }
        }

        @Override
        public long usedMemory() {
            final long external = externalUsedMemory;
            final long local = localUsedMemory;
            return external + local;
        }

        @Override
        public void recycle(AdaptiveByteBuf self) {
            if (isOwnerThread()) {
                localBuffers.addLast(self);
            } else {
                externalBuffers.offer(self);
            }
        }

        @Override
        boolean tryAllocate(int size, int maxCapacity, AdaptiveByteBuf buf, boolean reallocate) {
            return allocate(size, maxCapacity, buf, reallocate);
        }

        private boolean allocate(int size, int maxCapacity, AdaptiveByteBuf buf, boolean reallocate) {
            int startingCapacity = chunkController.computeBufferCapacity(size, maxCapacity, reallocate);
            Chunk curr = current;
            if (curr != null) {
                int remainingCapacity = curr.remainingCapacity();
                if (remainingCapacity > startingCapacity) {
                    curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                    return true;
                }
                current = null;
                if (remainingCapacity >= size) {
                    try {
                        curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                        return true;
                    } finally {
                        curr.releaseFromMagazine();
                    }
                }
                if (remainingCapacity < RETIRE_CAPACITY) {
                    curr.releaseFromMagazine();
                } else {
                    transferToNextInLineOrRelease(curr);
                }
            }
            return allocateAndRefillCurrentChunk(size, maxCapacity, buf, startingCapacity);
        }

        private boolean allocateAndRefillCurrentChunk(int size, int maxCapacity, AdaptiveByteBuf buf,
                                                      int startingCapacity) {
            Chunk curr = NEXT_IN_LINE.getAndSet(this, null);

            if (curr != null) {
                int remainingCapacity = curr.remainingCapacity();
                if (remainingCapacity > startingCapacity) {
                    curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                    current = curr;
                    return true;
                }
                if (remainingCapacity >= size) {
                    try {
                        curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                        return true;
                    } finally {
                        curr.releaseFromMagazine();
                    }
                } else {
                    curr.releaseFromMagazine();
                }
            }
            return allocateAndRefillCurrentFromGroup(size, maxCapacity, buf, startingCapacity);
        }

        private boolean allocateAndRefillCurrentFromGroup(int size, int maxCapacity,
                                                          AdaptiveByteBuf buf, int startingCapacity) {
            Chunk curr = null;
            if (isOwnerThread()) {
                curr = pollFromLocalCache(size);
            }

            if (curr == null) {
                // If not the owner or if the local cache is empty/unsuitable, use the group's external queue.
                curr = group.pollFromExternalQueue();
            }
            if (curr == null) {
                curr = chunkController.newChunkAllocation(size, this);
            } else {
                curr.attachToMagazine(this);
                int remainingCapacity = curr.remainingCapacity();
                if (remainingCapacity == 0 || remainingCapacity < size) {
                    if (remainingCapacity < RETIRE_CAPACITY) {
                        curr.releaseFromMagazine();
                    } else {
                        transferToNextInLineOrRelease(curr);
                    }
                    curr = chunkController.newChunkAllocation(size, this);
                }
            }

            current = curr;
            try {
                int remainingCapacity = curr.remainingCapacity();
                assert remainingCapacity >= size;
                if (remainingCapacity > startingCapacity) {
                    curr.readInitInto(buf, size, startingCapacity, maxCapacity);
                    curr = null;
                } else {
                    curr.readInitInto(buf, size, remainingCapacity, maxCapacity);
                }
            } finally {
                if (curr != null) {
                    curr.releaseFromMagazine();
                    current = null;
                }
            }
            return true;
        }

        private void transferToNextInLineOrRelease(Chunk chunk) {
            if (trySetNextInLine(chunk)) {
                return;
            }

            Chunk lessUsefulChunk;
            for (;;) {
                Chunk existingNext = nextInLine;
                if (existingNext == null) {
                    if (trySetNextInLine(chunk)) {
                        return;
                    }
                    continue;
                }

                if (chunk.remainingCapacity() > existingNext.remainingCapacity()) {
                    if (NEXT_IN_LINE.compareAndSet(this, existingNext, chunk)) {
                        lessUsefulChunk = existingNext;
                        break;
                    }
                    // Swap failed, another thread intervened. Retry loop.
                    continue;
                } else {
                    lessUsefulChunk = chunk;
                    break;
                }
            }

            if (isOwnerThread() && localChunkCache.size() < LOCAL_CHUNK_REUSE_QUEUE_CAPACITY) {
                localChunkCache.add(lessUsefulChunk);
            } else {
                lessUsefulChunk.releaseFromMagazine();
            }
        }

        @Override
        void free() {
            if (current != null) {
                current.releaseFromMagazine();
                current = null;
            }
            Chunk chunk = NEXT_IN_LINE.getAndSet(this, null);
            if (chunk != null) {
                chunk.releaseFromMagazine();
            }
        }
    }

    private static class BumpChunk extends Chunk implements ReferenceCounted {
        private static final long REFCNT_FIELD_OFFSET;
        private static final AtomicIntegerFieldUpdater<BumpChunk> AIF_UPDATER;
        private static final Object REFCNT_FIELD_VH;
        private static final ReferenceCountUpdater<BumpChunk> updater;
        // Value might not equal "real" reference count, all access should be via the updater
        @SuppressWarnings({"unused", "FieldMayBeFinal"})
        private volatile int refCnt;

        static {
            switch (ReferenceCountUpdater.updaterTypeOf(BumpChunk.class, "refCnt")) {
            case Atomic:
                AIF_UPDATER = newUpdater(BumpChunk.class, "refCnt");
                REFCNT_FIELD_OFFSET = -1;
                REFCNT_FIELD_VH = null;
                updater = new AtomicReferenceCountUpdater<BumpChunk>() {
                    @Override
                    protected AtomicIntegerFieldUpdater<BumpChunk> updater() {
                        return AIF_UPDATER;
                    }
                };
                break;
            case Unsafe:
                AIF_UPDATER = null;
                REFCNT_FIELD_OFFSET = getUnsafeOffset(BumpChunk.class, "refCnt");
                REFCNT_FIELD_VH = null;
                updater = new UnsafeReferenceCountUpdater<BumpChunk>() {
                    @Override
                    protected long refCntFieldOffset() {
                        return REFCNT_FIELD_OFFSET;
                    }
                };
                break;
            case VarHandle:
                AIF_UPDATER = null;
                REFCNT_FIELD_OFFSET = -1;
                REFCNT_FIELD_VH = PlatformDependent.findVarHandleOfIntField(MethodHandles.lookup(),
                                                                            BumpChunk.class, "refCnt");
                updater = new VarHandleReferenceCountUpdater<BumpChunk>() {
                    @Override
                    protected VarHandle varHandle() {
                        return (VarHandle) REFCNT_FIELD_VH;
                    }
                };
                break;
            default:
                throw new Error("Unknown updater type for Chunk");
            }
        }

        BumpChunk() {
        }

        BumpChunk(AbstractByteBuf delegate, AbstractMagazine magazine, boolean pooled,
                  ChunkReleasePredicate chunkReleasePredicate) {
            super(delegate, magazine, pooled, chunkReleasePredicate);
            updater.setInitialValue(this);
        }

        public static void allocateOneOffWith(AdaptiveByteBuf wrapper, ChunkAllocator chunkAllocator,
                                              int size, int maxCapacity, AbstractMagazine magazine,
                                              ChunkRegistry chunkRegistry) {
            AbstractByteBuf delegate = chunkAllocator.allocate(size, maxCapacity);
            BumpChunk chunk = new BumpChunk(delegate, magazine, false, chunkSize -> true);
            chunkRegistry.add(chunk);
            try {
                chunk.readInitInto(wrapper, size, size, maxCapacity);
            } finally {
                chunk.markToDeallocate();
            }
        }

        @Override
        public void readInitInto(AdaptiveByteBuf buf, int size, int startingCapacity, int maxCapacity) {
            int startIndex = allocatedBytes;
            allocatedBytes = startIndex + startingCapacity;
            BumpChunk chunk = this;
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
        }

        @Override
        boolean hasRemainingCapacity(int size) {
            return remainingCapacity() >= size;
        }

        @Override
        void releaseFromMagazine() {
            release();
        }

        @Override
        void releaseSegment(int ignoredSegmentId) {
            release();
        }

        @Override
        void resetToBeReused() {
            updater.resetRefCnt(this);
        }

        @Override
        void beforeRemovedFromChunkRegistry() {
            updater.release(this);
        }

        @Override
        void markToDeallocate() {
            release();
        }

        @Override
        public BumpChunk touch(Object hint) {
            return this;
        }

        @Override
        public int refCnt() {
            return updater.refCnt(this);
        }

        @Override
        public BumpChunk retain() {
            return updater.retain(this);
        }

        @Override
        public BumpChunk retain(int increment) {
            return updater.retain(this, increment);
        }

        @Override
        public BumpChunk touch() {
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
    }

    private abstract static class Chunk implements ChunkInfo {
        protected final AbstractByteBuf delegate;
        protected AbstractMagazine magazine;
        private final AdaptivePoolingAllocator allocator;
        private final ChunkReleasePredicate chunkReleasePredicate;
        private final int capacity;
        private final boolean pooled;
        protected int allocatedBytes;

        Chunk() {
            // Constructor only used by the MAGAZINE_FREED sentinel.
            delegate = null;
            magazine = null;
            allocator = null;
            chunkReleasePredicate = null;
            capacity = 0;
            pooled = false;
        }

        Chunk(AbstractByteBuf delegate, AbstractMagazine magazine, boolean pooled,
              ChunkReleasePredicate chunkReleasePredicate) {
            this.delegate = delegate;
            this.pooled = pooled;
            capacity = delegate.capacity();
            attachToMagazine(magazine);

            // We need the top-level allocator so ByteBuf.capacity(int) can call reallocate()
            allocator = magazine.group.allocator;

            this.chunkReleasePredicate = chunkReleasePredicate;

            if (PlatformDependent.isJfrEnabled() && AllocateChunkEvent.isEventEnabled()) {
                AllocateChunkEvent event = new AllocateChunkEvent();
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator.class);
                    event.pooled = pooled;
                    event.threadLocal = !magazine.isShared;
                    event.commit();
                }
            }
        }

        AbstractMagazine currentMagazine()  {
            return magazine;
        }

        void detachFromMagazine() {
            if (magazine != null) {
                magazine.addUsedMemory(-capacity);
                magazine = null;
            }
        }

        void attachToMagazine(AbstractMagazine magazine) {
            assert this.magazine == null;
            this.magazine = magazine;
            magazine.addUsedMemory(capacity);
        }

        /**
         * Called when a magazine is done using this chunk, probably because it was emptied.
         */
        abstract void releaseFromMagazine();

        /**
         * Called when a ByteBuf is done using its allocation in this chunk.
         */
        abstract void releaseSegment(int ignoredSegmentId);

        /**
         * Called before attempting to reuse this chunk into next or in the shared chunk queue.<br>
         * This is called from {@link #deallocate()}.
         */
        abstract void resetToBeReused();

        /**
         * Called before this chunk is removed from the chunk registry.
         * This is called from {@link #deallocate()}.
         */
        abstract void beforeRemovedFromChunkRegistry();

        /**
         * Called when this chunk is no longer needed and will be deallocated.
         */
        abstract void markToDeallocate();

        protected final void deallocate() {
            AbstractMagazine mag = magazine;
            int chunkSize = delegate.capacity();
            if (!pooled || chunkReleasePredicate.shouldReleaseChunk(chunkSize) || mag == null) {
                // Drop the chunk if the parent allocator is closed,
                // or if the chunk deviates too much from the preferred chunk size.
                detachFromMagazine();
                onRelease();
                allocator.chunkRegistry.remove(this);
                delegate.release();
            } else {
                resetToBeReused();
                delegate.setIndex(0, 0);
                allocatedBytes = 0;
                if (!mag.trySetNextInLine(this)) {
                    // As this Chunk does not belong to the mag anymore we need to decrease the used memory .
                    detachFromMagazine();
                    if (!mag.offerToQueue(this)) {
                        // The central queue is full.
                        beforeRemovedFromChunkRegistry();
                        onRelease();
                        allocator.chunkRegistry.remove(this);
                        delegate.release();
                    } else {
                        onReturn(false);
                    }
                } else {
                    onReturn(true);
                }
            }
        }

        private void onReturn(boolean returnedToMagazine) {
            if (PlatformDependent.isJfrEnabled() && ReturnChunkEvent.isEventEnabled()) {
                ReturnChunkEvent event = new ReturnChunkEvent();
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator.class);
                    event.returnedToMagazine = returnedToMagazine;
                    event.commit();
                }
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

        public abstract void readInitInto(AdaptiveByteBuf buf, int size, int startingCapacity, int maxCapacity);

        /**
         * Checks if the chunk has enough remaining capacity to satisfy an allocation of the given size.
         * @param size The size of the allocation.
         * @return {@code true} if there is enough capacity, {@code false} otherwise.
         */
        abstract boolean hasRemainingCapacity(int size);

        public int remainingCapacity() {
            return capacity - allocatedBytes;
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
            stack = new int[initialValues.length];
            // copy reversed
            for (int i = 0; i < initialValues.length; i++) {
                stack[i] = initialValues[initialValues.length - 1 - i];
            }
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
    }

    private static final class SizeClassedChunk extends Chunk {
        private static final int AVAILABLE = -1;
        private static final int DEALLOCATED = Integer.MIN_VALUE;

        private static final AtomicIntegerFieldUpdater<SizeClassedChunk> STATE = newUpdater(
                SizeClassedChunk.class, "state"
        );

        private static final int FREE_LIST_EMPTY = -1;
        private final int segmentSize;
        private final MpscIntQueue externalFreeList;
        private final IntStack localFreeList;
        private final Thread ownerThread;
        private volatile int state;
        private final int segments;
        private boolean markedForDeallocation;

        SizeClassedChunk(AbstractByteBuf delegate, AbstractMagazine magazine, int segmentSize, int[] segmentOffsets) {
            super(delegate, magazine, true, chunkSize -> false);
            ownerThread = magazine.ownerThread;
            this.segmentSize = segmentSize;
            int segmentCount = segmentOffsets.length;
            assert delegate.capacity() / segmentSize == segmentCount;
            assert segmentCount > 0: "Chunk must have a positive number of segments";
            externalFreeList = MpscIntQueue.create(segmentCount, FREE_LIST_EMPTY);
            if (ownerThread == null) {
                externalFreeList.fill(segmentCount, new IntSupplier() {
                    int counter;

                    @Override
                    public int getAsInt() {
                        return segmentOffsets[counter++];
                    }
                });
                localFreeList = null;
            } else {
                localFreeList = new IntStack(segmentOffsets);
            }
            segments = segmentCount;
            STATE.lazySet(this, AVAILABLE);
        }

        @Override
        public void readInitInto(AdaptiveByteBuf buf, int size, int startingCapacity, int maxCapacity) {
            assert state == AVAILABLE;
            IntStack localFreeList = this.localFreeList;
            final int startIndex;
            if (localFreeList != null) {
                assert Thread.currentThread() == ownerThread;
                if (localFreeList.isEmpty() && copyIntoLocalFreeList(localFreeList) == 0) {
                    throw new IllegalStateException("Free list is empty");
                }
                startIndex = localFreeList.pop();
            } else {
                startIndex = externalFreeList.poll();
                if (startIndex == FREE_LIST_EMPTY) {
                    throw new IllegalStateException("Free list is empty");
                }
            }

            allocatedBytes += segmentSize;
            SizeClassedChunk chunk = this;
            try {
                buf.init(delegate, chunk, 0, 0, startIndex, size, startingCapacity, maxCapacity);
                chunk = null;
            } finally {
                if (chunk != null) {
                    // If chunk is not null we know that buf.init(...) failed and so we need to manually release
                    // the chunk again as we retained it before calling buf.init(...). Beside this we also need to
                    // restore the old allocatedBytes value.
                    allocatedBytes -= segmentSize;
                    chunk.releaseSegment(startIndex);
                }
            }
        }

        /**
         * Drains segments from the thread-safe externalFreeList into the non-thread-safe localFreeList.
         * <p>
         * A capacity check is needed here. Because segments can be released by any thread and are
         * always added to the externalFreeList, a situation can arise where this method is called
         * to drain more segments than the localFreeList has available slots.
         * Without checking localFreeList.size() < capacity, this would cause an
         * ArrayIndexOutOfBoundsException by pushing onto a full stack. The loop therefore terminates
         * when either the local list is full or the external list is empty.
         */
        private int copyIntoLocalFreeList(IntStack localFreeList) {
            final MpscIntQueue externalFreeList = this.externalFreeList;
            final int capacity = localFreeList.capacity();
            int count = 0;
            while (localFreeList.size() < capacity) {
                int index = externalFreeList.poll();
                if (index == FREE_LIST_EMPTY) {
                    break;
                }
                localFreeList.push(index);
                count++;
            }
            return count;
        }

        @Override
        boolean hasRemainingCapacity(int size) {
            if (size > segmentSize) {
                return false;
            }

            if (ownerThread != null && Thread.currentThread() == ownerThread && !localFreeList.isEmpty()) {
                return true;
            }

            return !externalFreeList.isEmpty();
        }

        @Override
        public int remainingCapacity() {
            int remainingCapacity = super.remainingCapacity();
            if (remainingCapacity > segmentSize) {
                return remainingCapacity;
            }
            // TODO we could optimize this to save reading the externalFreeList.size() if we know that
            //      localFreeList is not null and has enough capacity
            int updatedRemainingCapacity = externalFreeList.size() * segmentSize;
            if (localFreeList != null) {
                assert Thread.currentThread() == ownerThread;
                updatedRemainingCapacity += localFreeList.size() * segmentSize;
            }
            if (updatedRemainingCapacity == remainingCapacity) {
                return remainingCapacity;
            }
            // update allocatedBytes based on what's available in the free list
            allocatedBytes = capacity() - updatedRemainingCapacity;
            return updatedRemainingCapacity;
        }

        @Override
        void releaseFromMagazine() {
            // Size-classed chunks can be reused before they become empty.
            // We can therefore put them in the shared queue as soon as the magazine is done with this chunk.
            AbstractMagazine mag = magazine;
            detachFromMagazine();
            if (!mag.offerToQueue(this)) {
                markToDeallocate();
            }
        }

        @Override
        void releaseSegment(int startIndex) {
            IntStack localFreeList = this.localFreeList;
            if (localFreeList != null && Thread.currentThread() == ownerThread) {
                localFreeList.push(startIndex);
                // Only check the volatile state if the chunk has been marked for deallocation.
                // This avoids a StoreLoad barrier on the hot path.
                if (markedForDeallocation) {
                    int state = this.state;
                    updateStateOnLocalReleaseSegment(state, localFreeList);
                }
            } else {
                boolean segmentReturned = externalFreeList.offer(startIndex);
                assert segmentReturned : "Unable to return segment " + startIndex + " to free list";
                // this has implicitly a StoreLoad barrier due to the multi-producer nature of the queue
                int state = this.state;
                if (state != AVAILABLE) {
                    handleStateOnExternalReleaseSegment(state);
                }
            }
        }

        private void handleStateOnExternalReleaseSegment(int localFreeListSize) {
            final int totalSize = localFreeListSize + externalFreeList.size();
            if (totalSize == segments && STATE.compareAndSet(this, state, DEALLOCATED)) {
                // we are done with this chunk and we can just deallocate it
                deallocate();
            }
        }

        private void updateStateOnLocalReleaseSegment(int previousLocalSize, IntStack localFreeList) {
            final int newLocalSize = previousLocalSize + 1;
            assert newLocalSize == localFreeList.size();
            if (newLocalSize == segments) {
                // no need to update the state w the local size as we are done with this chunk
                STATE.lazySet(this, DEALLOCATED);
                deallocate();
            } else {
                STATE.set(this, newLocalSize);
                // StoreLoad here as well
                final int totalSize = externalFreeList.size() + newLocalSize;
                if (totalSize == segments && STATE.compareAndSet(this, previousLocalSize, DEALLOCATED)) {
                    // we are done with this chunk and just deallocate it
                    deallocate();
                }
            }
        }

        @Override
        void resetToBeReused() {
            markedForDeallocation = false;
            STATE.lazySet(this, AVAILABLE);
        }

        @Override
        void beforeRemovedFromChunkRegistry() {
            // we shouldn't care about this that much sine it has been called on deallocate
            // which means no others can change it
            STATE.lazySet(this, DEALLOCATED);
        }

        @Override
        void markToDeallocate() {
            // IMPORTANT: This has to be called while holding the magazine lock or on the owner thread!
            // this is tricky and a bit racy on purpose, based on
            // https://www.scylladb.com/2018/02/15/memory-barriers-seastar-linux/

            if (ownerThread != null) {
                assert Thread.currentThread() == ownerThread;
                markedForDeallocation = true;
            }
            int state = localFreeList != null ? localFreeList.size() : 0;
            STATE.set(this, state);
            // StoreLoad
            int totalSize = state + externalFreeList.size();
            if (totalSize == segments && STATE.compareAndSet(this, state, DEALLOCATED)) {
                // we are done with this chunk and just deallocate it
                deallocate();
            }
        }
    }

    static final class AdaptiveByteBuf extends AbstractReferenceCountedByteBuf {

        private final Recycler.Handle<AdaptiveByteBuf> handle;
        // this both act as adjustment and the start index for a free list segment allocation
        private int startIndex;
        private AbstractByteBuf rootParent;
        Chunk chunk;
        private int length;
        private int maxFastCapacity;
        private ByteBuffer tmpNioBuf;
        private boolean hasArray;
        private boolean hasMemoryAddress;

        AdaptiveByteBuf(Recycler.Handle<AdaptiveByteBuf> recyclerHandle) {
            super(0);
            handle = recyclerHandle;
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
                    AbstractMagazine m = wrapped.magazine;
                    event.chunkThreadLocal = m != null && !m.isShared;
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
            int oldCapacity = length;
            AbstractByteBuf oldRoot = rootParent();
            allocator.reallocate(newCapacity, maxCapacity(), this);
            oldRoot.getBytes(baseOldRootIndex, this, 0, oldCapacity);
            chunk.releaseSegment(baseOldRootIndex);
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
            return _memoryAddress();
        }

        @Override
        long _memoryAddress() {
            AbstractByteBuf root = rootParent;
            return root != null ? root._memoryAddress() + startIndex : 0L;
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
                // Directly pass in the rootParent() with the adjusted index
                return ByteBufUtil.writeUtf8(rootParent(), idx(index), length, sequence, sequence.length());
            }
            if (charset.equals(CharsetUtil.US_ASCII) || charset.equals(CharsetUtil.ISO_8859_1)) {
                int length = sequence.length();
                if (expand) {
                    ensureWritable0(length);
                    checkIndex0(index, length);
                } else {
                    checkIndex(index, length);
                }
                // Directly pass in the rootParent() with the adjusted index
                return ByteBufUtil.writeAscii(rootParent(), idx(index), sequence, length);
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
                chunk.releaseSegment(startIndex);
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
