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
import io.netty.util.NettyRuntime;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.SystemPropertyUtil;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.StampedLock;

import static io.netty.util.internal.PlatformDependent.javaVersion;

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
final class AdaptivePoolingAllocator {
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

    private final ChunkAllocator chunkAllocator;
    private final Queue<ChunkByteBuf> centralQueue;
    private final StampedLock magazineExpandLock;
    private volatile Magazine[] magazines;

    AdaptivePoolingAllocator(ChunkAllocator chunkAllocator) {
        this.chunkAllocator = chunkAllocator;
        if (javaVersion() < 8) {
            // The implementation uses StampedLock, which was introduced in Java 8.
            throw new IllegalStateException("This allocator require Java 8 or newer.");
        }
        centralQueue = ObjectUtil.checkNotNull(createSharedChunkQueue(), "centralQueue");
        magazineExpandLock = new StampedLock();
        Magazine[] mags = new Magazine[4];
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
    private static Queue<ChunkByteBuf> createSharedChunkQueue() {
        return PlatformDependent.newFixedMpmcQueue(CENTRAL_QUEUE_CAPACITY);
    }

    ByteBuf allocate(int size, int maxCapacity) {
        if (size <= MAX_CHUNK_SIZE) {
            Thread currentThread = Thread.currentThread();
            boolean willCleanupFastThreadLocals = FastThreadLocalThread.willCleanupFastThreadLocals(currentThread);
            AdaptiveByteBuf buf = AdaptiveByteBuf.newInstance(willCleanupFastThreadLocals);
            AdaptiveByteBuf result = allocate(size, maxCapacity, currentThread, buf);
            if (result != null) {
                return result;
            }
            // Return the buffer we pulled from the recycler but didn't use.
            buf.release();
        }
        // The magazines failed us, or the buffer is too big to be pooled.
        return chunkAllocator.allocate(size, maxCapacity);
    }

    private AdaptiveByteBuf allocate(int size, int maxCapacity, Thread currentThread, AdaptiveByteBuf buf) {
        long threadId = currentThread.getId();
        int sizeBucket = AllocationStatistics.sizeBucket(size); // Compute outside of Magazine lock for better ILP.
        Magazine[] mags;
        int expansions = 0;
        do {
            mags = magazines;
            int mask = mags.length - 1;
            int index = (int) (threadId & mask);
            for (int i = 0, m = Integer.numberOfTrailingZeros(~mask); i < m; i++) {
                Magazine mag = mags[index + i & mask];
                long writeLock = mag.tryWriteLock();
                if (writeLock != 0) {
                    try {
                        return mag.allocate(size, sizeBucket, maxCapacity, buf);
                    } finally {
                        mag.unlockWrite(writeLock);
                    }
                }
            }
            expansions++;
        } while (expansions <= 3 && tryExpandMagazines(mags.length));
        return null;
    }

    /**
     * Allocate into the given buffer. Used by {@link AdaptiveByteBuf#capacity(int)}.
     */
    void allocate(int size, int maxCapacity, AdaptiveByteBuf into) {
        Magazine magazine = into.chunk.magazine;
        AdaptiveByteBuf result = allocate(size, maxCapacity, Thread.currentThread(), into);
        if (result == null) {
            // Create a one-off chunk for this allocation.
            AbstractByteBuf innerChunk = (AbstractByteBuf) chunkAllocator.allocate(size, maxCapacity);
            ChunkByteBuf chunk = new ChunkByteBuf(innerChunk, magazine, false);
            chunk.readInitInto(into, size, maxCapacity);
        }
    }

    long usedMemory() {
        long sum = 0;
        for (ByteBuf byteBuf : centralQueue) {
            sum += byteBuf.capacity();
        }
        for (Magazine magazine : magazines) {
            sum += magazine.usedMemory.get();
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

    private boolean offerToQueue(ChunkByteBuf buffer) {
        return centralQueue.offer(buffer);
    }

    @SuppressJava6Requirement(reason = "Guarded by version check")
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

        private AllocationStatistics(AdaptivePoolingAllocator parent) {
            this.parent = parent;
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
            for (Magazine mag : parent.magazines) {
                prefChunkSize = Math.max(prefChunkSize, mag.localPrefChunkSize);
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
        private static final AtomicReferenceFieldUpdater<Magazine, ChunkByteBuf> NEXT_IN_LINE;

        static {
            NEXT_IN_LINE = AtomicReferenceFieldUpdater.newUpdater(Magazine.class, ChunkByteBuf.class, "nextInLine");
        }
        private ChunkByteBuf current;
        @SuppressWarnings("unused") // updated via NEXT_IN_LINE
        private volatile ChunkByteBuf nextInLine;
        private final AtomicLong usedMemory;

        Magazine(AdaptivePoolingAllocator parent) {
            super(parent);
            usedMemory = new AtomicLong();
        }

        public AdaptiveByteBuf allocate(int size, int sizeBucket, int maxCapacity, AdaptiveByteBuf buf) {
            recordAllocationSize(sizeBucket);
            ChunkByteBuf curr = current;
            if (curr != null && curr.readableBytes() >= size) {
                if (curr.readableBytes() == size) {
                    current = null;
                    try {
                        return curr.readInitInto(buf, size, maxCapacity);
                    } finally {
                        curr.release();
                    }
                }
                return curr.readInitInto(buf, size, maxCapacity);
            }
            if (curr != null) {
                curr.release();
            }
            if (nextInLine != null) {
                curr = NEXT_IN_LINE.getAndSet(this, null);
            } else {
                curr = parent.centralQueue.poll();
                if (curr == null) {
                    curr = newChunkAllocation(size);
                }
            }
            current = curr;
            final AdaptiveByteBuf result;
            if (curr.readableBytes() > size) {
                result = curr.readInitInto(buf, size, maxCapacity);
            } else if (curr.readableBytes() == size) {
                result = curr.readInitInto(buf, size, maxCapacity);
                curr.release();
                current = null;
            } else {
                ChunkByteBuf newChunk = newChunkAllocation(size);
                result = newChunk.readInitInto(buf, size, maxCapacity);
                if (curr.readableBytes() < RETIRE_CAPACITY) {
                    curr.release();
                    current = newChunk;
                } else if (!(boolean) NEXT_IN_LINE.compareAndSet(this, null, newChunk)) {
                    if (!parent.offerToQueue(newChunk)) {
                        // Next-in-line is occupied AND the central queue is full.
                        // Rare that we should get here, but we'll only do one allocation out of this chunk, then.
                        newChunk.release();
                    }
                }
            }
            return result;
        }

        private ChunkByteBuf newChunkAllocation(int promptingSize) {
            int size = Math.max(promptingSize * BUFS_PER_CHUNK, preferredChunkSize());
            ChunkAllocator chunkAllocator = parent.chunkAllocator;
            ChunkByteBuf chunk = new ChunkByteBuf((AbstractByteBuf) chunkAllocator.allocate(size, size), this, true);
            chunk.writerIndex(size);
            return chunk;
        }

        boolean trySetNextInLine(ChunkByteBuf buffer) {
            return NEXT_IN_LINE.compareAndSet(this, null, buffer);
        }
    }

    private static final class ChunkByteBuf extends AbstractReferenceCountedByteBuf {
        private final AbstractByteBuf delegate;
        private final Magazine magazine;
        private final boolean pooled;

        ChunkByteBuf(AbstractByteBuf delegate, Magazine magazine, boolean pooled) {
            super(delegate.maxCapacity());
            this.delegate = delegate;
            this.magazine = magazine;
            this.pooled = pooled;
            magazine.usedMemory.getAndAdd(capacity());
        }

        @Override
        protected void deallocate() {
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
                resetRefCnt();
                delegate.setIndex(0, 0);
                setIndex(0, capacity());
                if (!mag.trySetNextInLine(this)) {
                    if (!parent.offerToQueue(this)) {
                        // The central queue is full. Drop the memory with the original Drop instance.
                        delegate.release();
                    }
                }
            }
        }

        public AdaptiveByteBuf readInitInto(AdaptiveByteBuf buf, int size, int maxCapacity) {
            int startIndex = readerIndex();
            skipBytes(size);
            buf.init(delegate, this, 0, 0, startIndex, size, maxCapacity);
            return buf;
        }

        @Override
        protected byte _getByte(int index) {
            return delegate._getByte(index);
        }

        @Override
        protected short _getShort(int index) {
            return delegate._getShort(index);
        }

        @Override
        protected short _getShortLE(int index) {
            return delegate._getShortLE(index);
        }

        @Override
        protected int _getUnsignedMedium(int index) {
            return delegate._getUnsignedMedium(index);
        }

        @Override
        protected int _getUnsignedMediumLE(int index) {
            return delegate._getUnsignedMediumLE(index);
        }

        @Override
        protected int _getInt(int index) {
            return delegate._getInt(index);
        }

        @Override
        protected int _getIntLE(int index) {
            return delegate._getIntLE(index);
        }

        @Override
        protected long _getLong(int index) {
            return delegate._getLong(index);
        }

        @Override
        protected long _getLongLE(int index) {
            return delegate._getLongLE(index);
        }

        @Override
        protected void _setByte(int index, int value) {
            delegate._setByte(index, value);
        }

        @Override
        protected void _setShort(int index, int value) {
            delegate._setShort(index, value);
        }

        @Override
        protected void _setShortLE(int index, int value) {
            delegate._setShortLE(index, value);
        }

        @Override
        protected void _setMedium(int index, int value) {
            delegate._setMedium(index, value);
        }

        @Override
        protected void _setMediumLE(int index, int value) {
            delegate._setMediumLE(index, value);
        }

        @Override
        protected void _setInt(int index, int value) {
            delegate._setInt(index, value);
        }

        @Override
        protected void _setIntLE(int index, int value) {
            delegate._setIntLE(index, value);
        }

        @Override
        protected void _setLong(int index, long value) {
            delegate._setLong(index, value);
        }

        @Override
        protected void _setLongLE(int index, long value) {
            delegate._setLongLE(index, value);
        }

        @Override
        public int capacity() {
            return delegate.capacity();
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            delegate.capacity(newCapacity);
            return this;
        }

        @Override
        public ByteBufAllocator alloc() {
            return delegate.alloc();
        }

        @Override
        public ByteOrder order() {
            return delegate.order();
        }

        @Override
        public ByteBuf unwrap() {
            return delegate;
        }

        @Override
        public boolean isDirect() {
            return delegate.isDirect();
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            delegate.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            delegate.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            delegate.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
            delegate.getBytes(index, out, length);
            return this;
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
            return delegate.getBytes(index, out, length);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
            return delegate.getBytes(index, out, position, length);
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            delegate.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            delegate.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            delegate.setBytes(index, src);
            return this;
        }

        @Override
        public int setBytes(int index, InputStream in, int length) throws IOException {
            return delegate.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
            return delegate.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
            return delegate.setBytes(index, in, position, length);
        }

        @Override
        public ByteBuf copy(int index, int length) {
            return delegate.copy(index, length);
        }

        @Override
        public int nioBufferCount() {
            return delegate.nioBufferCount();
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            return delegate.nioBuffer(index, length);
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            return delegate.internalNioBuffer(index, length);
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            return delegate.nioBuffers(index, length);
        }

        @Override
        public boolean hasArray() {
            return delegate.hasArray();
        }

        @Override
        public byte[] array() {
            return delegate.array();
        }

        @Override
        public int arrayOffset() {
            return delegate.arrayOffset();
        }

        @Override
        public boolean hasMemoryAddress() {
            return delegate.hasMemoryAddress();
        }

        @Override
        public long memoryAddress() {
            return delegate.memoryAddress();
        }
    }

    static final class AdaptiveByteBuf extends AbstractReferenceCountedByteBuf {
        static final ObjectPool<AdaptiveByteBuf> RECYCLER = ObjectPool.newPool(
                new ObjectPool.ObjectCreator<AdaptiveByteBuf>() {
                    @Override
                    public AdaptiveByteBuf newObject(ObjectPool.Handle<AdaptiveByteBuf> handle) {
                        return new AdaptiveByteBuf(handle);
                    }
                });

        static AdaptiveByteBuf newInstance(boolean useThreadLocal) {
            if (useThreadLocal) {
                AdaptiveByteBuf buf = RECYCLER.get();
                buf.resetRefCnt();
                buf.discardMarks();
                return buf;
            }
            return new AdaptiveByteBuf(null);
        }

        private final ObjectPool.Handle<AdaptiveByteBuf> handle;

        int adjustment;
        private AbstractByteBuf rootParent;
        private ChunkByteBuf chunk;
        private int length;
        private ByteBuffer tmpNioBuf;

        AdaptiveByteBuf(ObjectPool.Handle<AdaptiveByteBuf> recyclerHandle) {
            super(0);
            handle = recyclerHandle;
        }

        void init(AbstractByteBuf unwrapped, ChunkByteBuf wrapped, int readerIndex, int writerIndex,
                         int adjustment, int capacity, int maxCapacity) {
            wrapped.retain();
            this.adjustment = adjustment;
            chunk = wrapped;
            length = capacity;
            maxCapacity(maxCapacity);
            setIndex0(readerIndex, writerIndex);
            rootParent = unwrapped;
            tmpNioBuf = rootParent.internalNioBuffer(adjustment, capacity).slice();
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
            ChunkByteBuf chunk = this.chunk;
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
            return rootParent.alloc();
        }

        @Override
        public ByteOrder order() {
            return rootParent.order();
        }

        @Override
        public ByteBuf unwrap() {
            return null;
        }

        @Override
        public boolean isDirect() {
            return rootParent.isDirect();
        }

        @Override
        public int arrayOffset() {
            return idx(rootParent.arrayOffset());
        }

        @Override
        public boolean hasMemoryAddress() {
            return rootParent.hasMemoryAddress();
        }

        @Override
        public long memoryAddress() {
            ensureAccessible();
            return rootParent.memoryAddress() + adjustment;
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            checkIndex(index, length);
            return rootParent.nioBuffer(idx(index), length);
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
            return rootParent.nioBuffers(idx(index), length);
        }

        @Override
        public boolean hasArray() {
            return rootParent.hasArray();
        }

        @Override
        public byte[] array() {
            return rootParent.array();
        }

        @Override
        public ByteBuf copy(int index, int length) {
            checkIndex(index, length);
            return rootParent.copy(idx(index), length);
        }

        @Override
        public ByteBuf slice(int index, int length) {
            checkIndex(index, length);
            return new PooledNonRetainedSlicedByteBuf(this, rootParent, idx(index), length);
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return slice(index, length).retain();
        }

        @Override
        public ByteBuf duplicate() {
            ensureAccessible();
            return new PooledNonRetainedDuplicateByteBuf(this, this).setIndex(readerIndex(), writerIndex());
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return duplicate().retain();
        }

        @Override
        public int nioBufferCount() {
            return rootParent.nioBufferCount();
        }

        @Override
        public byte getByte(int index) {
            checkIndex(index, 1);
            return rootParent.getByte(idx(index));
        }

        @Override
        protected byte _getByte(int index) {
            return rootParent._getByte(idx(index));
        }

        @Override
        public short getShort(int index) {
            checkIndex(index, 2);
            return rootParent.getShort(idx(index));
        }

        @Override
        protected short _getShort(int index) {
            return rootParent._getShort(idx(index));
        }

        @Override
        public short getShortLE(int index) {
            checkIndex(index, 2);
            return rootParent.getShortLE(idx(index));
        }

        @Override
        protected short _getShortLE(int index) {
            return rootParent._getShortLE(idx(index));
        }

        @Override
        public int getUnsignedMedium(int index) {
            checkIndex(index, 3);
            return rootParent.getUnsignedMedium(idx(index));
        }

        @Override
        protected int _getUnsignedMedium(int index) {
            return rootParent._getUnsignedMedium(idx(index));
        }

        @Override
        public int getUnsignedMediumLE(int index) {
            checkIndex(index, 3);
            return rootParent.getUnsignedMediumLE(idx(index));
        }

        @Override
        protected int _getUnsignedMediumLE(int index) {
            return rootParent._getUnsignedMediumLE(idx(index));
        }

        @Override
        public int getInt(int index) {
            checkIndex(index, 4);
            return rootParent.getInt(idx(index));
        }

        @Override
        protected int _getInt(int index) {
            return rootParent._getInt(idx(index));
        }

        @Override
        public int getIntLE(int index) {
            checkIndex(index, 4);
            return rootParent.getIntLE(idx(index));
        }

        @Override
        protected int _getIntLE(int index) {
            return rootParent._getIntLE(idx(index));
        }

        @Override
        public long getLong(int index) {
            checkIndex(index, 8);
            return rootParent.getLong(idx(index));
        }

        @Override
        protected long _getLong(int index) {
            return rootParent._getLong(idx(index));
        }

        @Override
        public long getLongLE(int index) {
            checkIndex(index, 8);
            return rootParent.getLongLE(idx(index));
        }

        @Override
        protected long _getLongLE(int index) {
            return rootParent._getLongLE(idx(index));
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            checkIndex(index, length);
            rootParent.getBytes(idx(index), dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            checkIndex(index, length);
            rootParent.getBytes(idx(index), dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            checkIndex(index, dst.remaining());
            rootParent.getBytes(idx(index), dst);
            return this;
        }

        @Override
        public ByteBuf setByte(int index, int value) {
            checkIndex(index, 1);
            rootParent.setByte(idx(index), value);
            return this;
        }

        @Override
        protected void _setByte(int index, int value) {
            rootParent._setByte(idx(index), value);
        }

        @Override
        public ByteBuf setShort(int index, int value) {
            checkIndex(index, 2);
            rootParent.setShort(idx(index), value);
            return this;
        }

        @Override
        protected void _setShort(int index, int value) {
            rootParent._setShort(idx(index), value);
        }

        @Override
        public ByteBuf setShortLE(int index, int value) {
            checkIndex(index, 2);
            rootParent.setShortLE(idx(index), value);
            return this;
        }

        @Override
        protected void _setShortLE(int index, int value) {
            rootParent._setShortLE(idx(index), value);
        }

        @Override
        public ByteBuf setMedium(int index, int value) {
            checkIndex(index, 3);
            rootParent.setMedium(idx(index), value);
            return this;
        }

        @Override
        protected void _setMedium(int index, int value) {
            rootParent._setMedium(idx(index), value);
        }

        @Override
        public ByteBuf setMediumLE(int index, int value) {
            checkIndex(index, 3);
            rootParent.setMediumLE(idx(index), value);
            return this;
        }

        @Override
        protected void _setMediumLE(int index, int value) {
            rootParent._setMediumLE(idx(index), value);
        }

        @Override
        public ByteBuf setInt(int index, int value) {
            checkIndex(index, 4);
            rootParent.setInt(idx(index), value);
            return this;
        }

        @Override
        protected void _setInt(int index, int value) {
            rootParent._setInt(idx(index), value);
        }

        @Override
        public ByteBuf setIntLE(int index, int value) {
            checkIndex(index, 4);
            rootParent.setIntLE(idx(index), value);
            return this;
        }

        @Override
        protected void _setIntLE(int index, int value) {
            rootParent._setIntLE(idx(index), value);
        }

        @Override
        public ByteBuf setLong(int index, long value) {
            checkIndex(index, 8);
            rootParent.setLong(idx(index), value);
            return this;
        }

        @Override
        protected void _setLong(int index, long value) {
            rootParent._setLong(idx(index), value);
        }

        @Override
        public ByteBuf setLongLE(int index, long value) {
            checkIndex(index, 8);
            rootParent.setLongLE(idx(index), value);
            return this;
        }

        @Override
        protected void _setLongLE(int index, long value) {
            rootParent.setLongLE(idx(index), value);
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            checkIndex(index, length);
            rootParent.setBytes(idx(index), src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            checkIndex(index, length);
            rootParent.setBytes(idx(index), src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            checkIndex(index, src.remaining());
            rootParent.setBytes(idx(index), src);
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
            int ret = rootParent.forEachByte(idx(index), length, processor);
            if (ret < adjustment) {
                return -1;
            }
            return ret - adjustment;
        }

        @Override
        public int forEachByteDesc(int index, int length, ByteProcessor processor) {
            checkIndex(index, length);
            int ret = rootParent.forEachByteDesc(idx(index), length, processor);
            if (ret < adjustment) {
                return -1;
            }
            return ret - adjustment;
        }

        @Override
        public boolean isContiguous() {
            return rootParent.isContiguous();
        }

        private int idx(int index) {
            return index + adjustment;
        }

        @Override
        protected void deallocate() {
            tmpNioBuf = null;
            if (chunk != null) {
                chunk.release();
            }
            if (handle != null) {
                handle.recycle(this);
            }
        }
    }

    private static final class PooledNonRetainedDuplicateByteBuf extends UnpooledDuplicatedByteBuf {
        private final ReferenceCounted referenceCountDelegate;

        PooledNonRetainedDuplicateByteBuf(ReferenceCounted referenceCountDelegate, AbstractByteBuf buffer) {
            super(buffer);
            this.referenceCountDelegate = referenceCountDelegate;
        }

        @Override
        boolean isAccessible0() {
            return referenceCountDelegate.refCnt() != 0;
        }

        @Override
        int refCnt0() {
            return referenceCountDelegate.refCnt();
        }

        @Override
        ByteBuf retain0() {
            referenceCountDelegate.retain();
            return this;
        }

        @Override
        ByteBuf retain0(int increment) {
            referenceCountDelegate.retain(increment);
            return this;
        }

        @Override
        ByteBuf touch0() {
            referenceCountDelegate.touch();
            return this;
        }

        @Override
        ByteBuf touch0(Object hint) {
            referenceCountDelegate.touch(hint);
            return this;
        }

        @Override
        boolean release0() {
            return referenceCountDelegate.release();
        }

        @Override
        boolean release0(int decrement) {
            return referenceCountDelegate.release(decrement);
        }

        @Override
        public ByteBuf duplicate() {
            ensureAccessible();
            return new PooledNonRetainedDuplicateByteBuf(referenceCountDelegate, unwrap());
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return duplicate().retain();
        }

        @Override
        public ByteBuf slice(int index, int length) {
            checkIndex(index, length);
            return new PooledNonRetainedSlicedByteBuf(referenceCountDelegate, unwrap(), index, length);
        }

        @Override
        public ByteBuf retainedSlice() {
            // Capacity is not allowed to change for a sliced ByteBuf, so length == capacity()
            return retainedSlice(readerIndex(), capacity());
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return slice(index, length).retain();
        }
    }

    private static final class PooledNonRetainedSlicedByteBuf extends UnpooledSlicedByteBuf {
        private final ReferenceCounted referenceCountDelegate;

        PooledNonRetainedSlicedByteBuf(ReferenceCounted referenceCountDelegate,
                                       AbstractByteBuf buffer, int index, int length) {
            super(buffer, index, length);
            this.referenceCountDelegate = referenceCountDelegate;
        }

        @Override
        boolean isAccessible0() {
            return referenceCountDelegate.refCnt() != 0;
        }

        @Override
        int refCnt0() {
            return referenceCountDelegate.refCnt();
        }

        @Override
        ByteBuf retain0() {
            referenceCountDelegate.retain();
            return this;
        }

        @Override
        ByteBuf retain0(int increment) {
            referenceCountDelegate.retain(increment);
            return this;
        }

        @Override
        ByteBuf touch0() {
            referenceCountDelegate.touch();
            return this;
        }

        @Override
        ByteBuf touch0(Object hint) {
            referenceCountDelegate.touch(hint);
            return this;
        }

        @Override
        boolean release0() {
            return referenceCountDelegate.release();
        }

        @Override
        boolean release0(int decrement) {
            return referenceCountDelegate.release(decrement);
        }

        @Override
        public ByteBuf duplicate() {
            ensureAccessible();
            return new PooledNonRetainedSlicedByteBuf(referenceCountDelegate, unwrap(), idx(0), capacity())
                    .setIndex(readerIndex(), writerIndex());
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return duplicate().retain();
        }

        @Override
        public ByteBuf slice(int index, int length) {
            checkIndex(index, length);
            return new PooledNonRetainedSlicedByteBuf(referenceCountDelegate, unwrap(), idx(index), length);
        }

        @Override
        public ByteBuf retainedSlice() {
            // Capacity is not allowed to change for a sliced ByteBuf, so length == capacity()
            return retainedSlice(0, capacity());
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return slice(index, length).retain();
        }
    }

    /**
     * The strategy for how {@link AdaptivePoolingAllocator} should allocate chunk buffers.
     */
    public interface ChunkAllocator {
        /**
         * Allocate a buffer for a chunk. This can be any kind of {@link ByteBuf} implementation.
         * @param initialCapacity The initial capacity of the returned {@link ByteBuf}.
         * @param maxCapacity The maximum capacity of the returned {@link ByteBuf}.
         * @return The buffer that represents the chunk memory.
         */
        ByteBuf allocate(int initialCapacity, int maxCapacity);
    }
}
