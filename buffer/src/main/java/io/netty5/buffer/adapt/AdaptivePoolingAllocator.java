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
package io.netty5.buffer.adapt;

import io.netty5.buffer.AllocationType;
import io.netty5.buffer.AllocatorControl;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.Drop;
import io.netty5.buffer.MemoryManager;
import io.netty5.buffer.StandardAllocationTypes;
import io.netty5.buffer.internal.ArcDrop;
import io.netty5.buffer.internal.CleanerDrop;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.util.NettyRuntime;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.concurrent.FastThreadLocalThread;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.ThreadExecutorMap;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

import static io.netty5.buffer.internal.InternalBufferUtils.allocatorClosedException;
import static io.netty5.buffer.internal.InternalBufferUtils.standardDrop;
import static io.netty5.util.internal.PlatformDependent.threadId;
import static java.util.Objects.requireNonNull;

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
public class AdaptivePoolingAllocator implements BufferAllocator {

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
    protected static final int MAX_CHUNK_SIZE =
            BUFS_PER_CHUNK * (1 << AllocationStatistics.HISTO_MAX_BUCKET_SHIFT); // 10 MiB.

    /**
     * The capacity if the central queue that allow chunks to be shared across magazines.
     * The default size is {@link NettyRuntime#availableProcessors()},
     * and the maximum number of magazines is twice this.
     * <p>
     * This means the maximum amount of memory that we can have allocated-but-not-in-use is
     * 5 * {@link NettyRuntime#availableProcessors()} * {@link #MAX_CHUNK_SIZE} bytes.
     */
    protected static final int CENTRAL_QUEUE_CAPACITY = SystemPropertyUtil.getInt(
            "io.netty5.allocator.centralQueueCapacity", NettyRuntime.availableProcessors());

    private static final Object NO_MAGAZINE = Boolean.TRUE;

    private final AllocationType allocationType;
    private final MemoryManager manager;
    private final Queue<Buffer> centralQueue;
    private final AllocatorControl allocatorControl;
    private final StampedLock magazineExpandLock;
    private volatile Magazine[] magazines;
    private volatile boolean closed;
    private final FastThreadLocal<Object> threadLocalMagazine;
    private final Set<Magazine> liveCachedMagazines;

    public AdaptivePoolingAllocator() {
        this(PlatformDependent.directBufferPreferred());
    }

    public AdaptivePoolingAllocator(boolean direct) {
        this(MemoryManager.instance(), direct, true);
    }

    AdaptivePoolingAllocator(MemoryManager manager, boolean direct, boolean eventExecutorMagazines) {
        allocationType = direct ? StandardAllocationTypes.OFF_HEAP : StandardAllocationTypes.ON_HEAP;
        this.manager = manager;
        centralQueue = requireNonNull(createSharedChunkQueue());
        allocatorControl = new SimpleAllocatorControl(this);
        magazineExpandLock = new StampedLock();
        if (eventExecutorMagazines) {
            final Set<Magazine> liveMagazines = new CopyOnWriteArraySet<>();
            threadLocalMagazine = new FastThreadLocal<>() {
                @Override
                protected Object initialValue() {
                    EventExecutor executor = ThreadExecutorMap.currentExecutor();
                    if (executor == null) {
                        return NO_MAGAZINE;
                    }
                    Magazine mag = new Magazine(AdaptivePoolingAllocator.this, executor);
                    liveMagazines.add(mag);
                    return mag;
                }

                @Override
                protected void onRemoval(final Object value) {
                    if (value != NO_MAGAZINE) {
                        if (liveMagazines.remove(value)) {
                            ((Magazine) value).close();
                        }
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
     * The simplest implementation of this method is to return a new {@link java.util.concurrent.ConcurrentLinkedQueue}.
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
    @NotNull
    protected Queue<Buffer> createSharedChunkQueue() {
        return PlatformDependent.newMpmcQueue(CENTRAL_QUEUE_CAPACITY);
    }

    @Override
    public final boolean isPooling() {
        return true;
    }

    @Override
    public final AllocationType getAllocationType() {
        return allocationType;
    }

    @Override
    public Buffer allocate(int size) {
        if (closed) {
            throw allocatorClosedException();
        }
        InternalBufferUtils.assertValidBufferSize(size);
        if (size <= MAX_CHUNK_SIZE) {
            int sizeBucket = AllocationStatistics.sizeBucket(size); // Compute outside of Magazine lock for better ILP.
            FastThreadLocal<Object> threadLocalMagazine = this.threadLocalMagazine;
            Thread currentThread = Thread.currentThread();
            if (threadLocalMagazine != null && currentThread instanceof FastThreadLocalThread) {
                Object mag = threadLocalMagazine.get();
                if (mag != NO_MAGAZINE) {
                    return ((Magazine) mag).allocate(size, sizeBucket);
                }
            }
            long threadId = threadId(currentThread);
            int expansions = 0;
            Magazine[] mags;
            do {
                mags = magazines;
                int mask = mags.length - 1;
                int index = (int) (threadId & mask);
                for (int i = 0, m = Integer.numberOfTrailingZeros(~mask); i < m; i++) {
                    Magazine mag = mags[index + i & mask];
                    long writeLock = mag.tryWriteLock();
                    if (writeLock != 0) {
                        try {
                            return mag.allocate(size, sizeBucket);
                        } finally {
                            mag.unlockWrite(writeLock);
                        }
                    }
                }
                expansions++;
            } while (expansions <= EXPANSION_ATTEMPTS && tryExpandMagazines(mags.length));
        }
        // The magazines failed us, or the buffer is too big to be pooled. Allocate unpooled buffer.
        return manager.allocateShared(allocatorControl, size, standardDrop(manager), allocationType);
    }

    private boolean tryExpandMagazines(int currentLength) {
        if (currentLength >= MAX_STRIPES) {
            return true;
        }
        long writeLock = magazineExpandLock.tryWriteLock();
        if (writeLock != 0) {
            try {
                Magazine[] mags = magazines;
                if (mags.length >= MAX_STRIPES || mags.length > currentLength) {
                    return true;
                }
                Magazine[] expanded = Arrays.copyOf(mags, mags.length * 2);
                for (int i = mags.length, m = expanded.length; i < m; i++) {
                    expanded[i] = new Magazine(this);
                }
                magazines = expanded;
            } finally {
                magazineExpandLock.unlockWrite(writeLock);
            }
        }
        return true;
    }

    @Override
    public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
        if (closed) {
            throw allocatorClosedException();
        }
        Buffer constantBuffer = manager.allocateShared(
                allocatorControl, bytes.length, drop -> CleanerDrop.wrapWithoutLeakDetection(drop, manager),
                allocationType);
        constantBuffer.writeBytes(bytes).makeReadOnly();
        return () -> manager.allocateConstChild(constantBuffer);
    }

    @Override
    public void close() {
        closed = true;
        long magsExpandWriteLock = magazineExpandLock.writeLock();
        try {
            for (Magazine mag : magazines) {
                long writeLock = mag.writeLock();
                try {
                    mag.close();
                } finally {
                    mag.unlockWrite(writeLock);
                }
            }
        } finally {
            magazineExpandLock.unlockWrite(magsExpandWriteLock);
        }
        if (liveCachedMagazines != null) {
            liveCachedMagazines.forEach(mag -> {
                try {
                    mag.ownerEventExecutor.execute(() -> {
                        threadLocalMagazine.remove();
                        drainCloseCentralQueue();
                    });
                } catch (Throwable ignore) {
                    // if we've been rejected here, it's likely the event executor will take care of remove it
                    // but if the event executor was running on an FastThreadLocalThread which doesn't remove it,
                    // we're in trouble :"(
                }
            });
        }
        drainCloseCentralQueue();
    }

    private void drainCloseCentralQueue() {
        Buffer curr;
        while ((curr = centralQueue.poll()) != null) {
            curr.close();
        }
    }

    private boolean offerToQueue(Buffer buffer) {
        if (!centralQueue.offer(buffer)) {
            return false;
        }
        if (closed) {
            drainCloseCentralQueue();
        }
        return true;
    }

    @SuppressWarnings("checkstyle:finalclass") // Checkstyle mistakenly believes this class should be final.
    private static class AllocationStatistics extends StampedLock {
        private static final long serialVersionUID = -8319929980932269688L;
        private static final int MIN_DATUM_TARGET = 1024;
        private static final int MAX_DATUM_TARGET = 65534;
        private static final int INIT_DATUM_TARGET = 8192;
        private static final int HISTO_MIN_BUCKET_SHIFT = 13; // Smallest bucket is 1 << 13 = 8192 bytes in size.
        private static final int HISTO_MAX_BUCKET_SHIFT = 20; // Biggest bucket is 1 << 20 = 1 MiB bytes in size.
        private static final int HISTO_BUCKET_COUNT = 1 + HISTO_MAX_BUCKET_SHIFT - HISTO_MIN_BUCKET_SHIFT; // 8 buckets.
        private static final int HISTO_MAX_BUCKET_MASK = HISTO_BUCKET_COUNT - 1;

        protected final AdaptivePoolingAllocator parent;
        protected final EventExecutor ownerEventExecutor;
        private final short[][] histos = {
                new short[HISTO_BUCKET_COUNT], new short[HISTO_BUCKET_COUNT],
                new short[HISTO_BUCKET_COUNT], new short[HISTO_BUCKET_COUNT],
        };
        private short[] histo = histos[0];
        private final int[] sums = new int[HISTO_BUCKET_COUNT];

        private int histoIndex;
        private int datumCount;
        private int datumTarget = INIT_DATUM_TARGET;
        private volatile int sharedPrefChunkSize = MIN_CHUNK_SIZE;
        protected volatile int localPrefChunkSize = MIN_CHUNK_SIZE;

        private AllocationStatistics(AdaptivePoolingAllocator parent, EventExecutor ownerEventExecutor) {
            this.parent = parent;
            this.ownerEventExecutor = ownerEventExecutor;
        }

        protected void recordAllocationSize(int bucket) {
            histo[bucket]++;
            if (datumCount++ == datumTarget) {
                rotateHistograms();
            }
        }

        static int sizeBucket(int size) {
            // Minimum chunk size is 128 KiB. We'll only make bigger chunks if the 99-percentile is 16 KiB or greater,
            // so we truncate and roll up the bottom part of the histogram to 8 KiB.
            // The upper size band is 1 MiB, and that gives us exactly 8 size buckets,
            // which is a magical number for JIT optimisations.
            int normalizedSize = size - 1 >> HISTO_MIN_BUCKET_SHIFT & HISTO_MAX_BUCKET_MASK;
            return Integer.SIZE - Integer.numberOfLeadingZeros(normalizedSize);
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
            if (ownerEventExecutor == null) {
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

    private static final class Magazine extends AllocationStatistics {
        private static final long serialVersionUID = -4068223712022528165L;
        private static final VarHandle NEXT_IN_LINE;

        static {
            try {
                NEXT_IN_LINE = MethodHandles.lookup().findVarHandle(Magazine.class, "nextInLine", Buffer.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        private Buffer current;
        @SuppressWarnings("unused") // updated via VarHandle
        private volatile Buffer nextInLine;

        Magazine(AdaptivePoolingAllocator parent) {
            super(parent, null);
        }

        Magazine(AdaptivePoolingAllocator parent, EventExecutor ownerEventExecutor) {
            super(parent, ownerEventExecutor);
        }

        public Buffer allocate(int size, int sizeBucket) {
            recordAllocationSize(sizeBucket);
            Buffer curr = current;
            if (curr != null && curr.capacity() >= size) {
                if (curr.capacity() == size) {
                    current = null;
                    return curr;
                }
                return curr.split(size);
            }
            if (curr != null) {
                curr.close();
            }
            if (nextInLine != null) {
                curr = (Buffer) NEXT_IN_LINE.getAndSet(this, (Buffer) null);
            } else {
                curr = parent.centralQueue.poll();
                if (curr == null) {
                    curr = newChunkAllocation(size);
                }
            }
            current = curr;
            final Buffer result;
            if (curr.capacity() > size) {
                result = curr.split(size);
            } else if (curr.capacity() == size) {
                result = curr;
                current = null;
            } else {
                Buffer buffer = newChunkAllocation(size);
                result = buffer.split(size);
                if (curr.capacity() < RETIRE_CAPACITY) {
                    curr.close();
                    current = buffer;
                } else if (!(boolean) NEXT_IN_LINE.compareAndSet(this, null, buffer)) {
                    if (!parent.offerToQueue(buffer)) {
                        // Next-in-line is occupied AND the central queue is full.
                        // Rare that we should get here, but we'll only do one allocation out of this chunk, then.
                        buffer.close();
                    }
                }
            }
            return result;
        }

        private Buffer newChunkAllocation(int promptingSize) {
            int size = Math.max(promptingSize * BUFS_PER_CHUNK, preferredChunkSize());
            return parent.manager.allocateShared(parent.allocatorControl, size, this::decorate, parent.allocationType);
        }

        private Drop<Buffer> decorate(Drop<Buffer> drop) {
            if (drop instanceof ArcDrop) {
                drop = ((ArcDrop<Buffer>) drop).unwrap();
            }
            drop = CleanerDrop.wrap(ArcDrop.wrap(new PoolDrop(drop, this)), parent.manager);
            if (drop instanceof CleanerDrop) {
                // Only avoid recording splits if we have a CleanerDrop here,
                // because they're the only ones recording splits anyway.
                drop = new NoSplitTracingDrop((CleanerDrop<Buffer>) drop);
            }
            return drop;
        }

        boolean trySetNextInLine(Buffer buffer) {
            return (boolean) NEXT_IN_LINE.compareAndSet(this, null, buffer);
        }

        void close() {
            Buffer curr = current;
            if (curr != null) {
                current = null;
                curr.close();
            }
            curr = (Buffer) NEXT_IN_LINE.getAndSet(this, null);
            if (curr != null) {
                curr.close();
            }
        }
    }

    private static final class PoolDrop implements Drop<Buffer> {
        private static final Object DEALLOCATE = new Object();
        private final Drop<Buffer> drop;
        private final Magazine magazine;
        private Object memory;

        PoolDrop(Drop<Buffer> drop, Magazine magazine) {
            this.drop = drop;
            this.magazine = magazine;
        }

        @Override
        public void drop(Buffer obj) {
            if (memory == DEALLOCATE) {
                drop.drop(obj);
                return;
            }
            Magazine mag = magazine;
            AdaptivePoolingAllocator parent = mag.parent;
            MemoryManager manager = parent.manager;
            int chunkSize = mag.preferredChunkSize();
            int memSize = manager.sizeOf(memory);
            if (parent.closed || memSize < chunkSize || memSize > chunkSize + (chunkSize >> 1)) {
                // Drop the chunk if the parent allocator is closed, or if the chunk is smaller than the
                // preferred chunk size, or over 50% larger than the preferred chunk size.
                drop.drop(obj);
            } else {
                Drop<Buffer> recoveredMemoryDrop = CleanerDrop.wrap(ArcDrop.wrap(this), manager);
                Buffer buffer = manager.recoverMemory(parent.allocatorControl, memory, recoveredMemoryDrop);
                if (!mag.trySetNextInLine(buffer)) {
                    if (!parent.offerToQueue(buffer)) {
                        // The central queue is full. Drop the memory through the Buffer we created.
                        // Mark this PoolDrop for deallocation to avoid infinite recursion.
                        memory = DEALLOCATE;
                        buffer.close();
                    }
                }
            }
        }

        @Override
        public Drop<Buffer> fork() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void attach(Buffer obj) {
            if (memory == null) {
                memory = magazine.parent.manager.unwrapRecoverableMemory(obj);
            }
            drop.attach(obj);
        }
    }

    private static final class NoSplitTracingDrop implements Drop<Buffer> {
        private final CleanerDrop<Buffer> drop;

        NoSplitTracingDrop(CleanerDrop<Buffer> drop) {
            this.drop = drop;
        }

        @Override
        public void drop(Buffer obj) {
            drop.drop(obj);
        }

        @Override
        public Drop<Buffer> fork() {
            // Intentionally don't wrap the returned drop in a NoSplitTracingDrop.
            // We do this because we don't want to record the splitting off of chunk buffers,
            // in the lifecycle of the allocated buffers.
            // Once the initial split has been made, we want to record all lifecycle events,
            // including splits, in the allocated buffer.
            return drop.forkWithoutTracingSplit();
        }

        @Override
        public void attach(Buffer obj) {
            drop.attach(obj);
        }
    }

    private static final class SimpleAllocatorControl implements AllocatorControl {
        private final BufferAllocator allocator;

        private SimpleAllocatorControl(BufferAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public BufferAllocator getAllocator() {
            return allocator;
        }
    }
}
