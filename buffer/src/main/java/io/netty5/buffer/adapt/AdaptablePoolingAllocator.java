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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

import static io.netty5.buffer.internal.InternalBufferUtils.allocatorClosedException;
import static io.netty5.buffer.internal.InternalBufferUtils.standardDrop;
import static io.netty5.util.internal.PlatformDependent.threadId;

public class AdaptablePoolingAllocator implements BufferAllocator {
    private static final int RETIRE_CAPACITY = 4 * 1024;
    private static final int MIN_CHUNK_SIZE = 128 * 1024;
    private static final int MAX_STRIPES = NettyRuntime.availableProcessors() * 2;

    private final AllocationType allocationType;
    private final MemoryManager manager;
    private final ConcurrentLinkedQueue<Buffer> centralQueue;
    private final AllocatorControl allocatorControl;
    private final StampedLock magazineExpandLock;
    private volatile Magazine[] magazines;
    private volatile boolean closed;

    public AdaptablePoolingAllocator(boolean direct) {
        this(MemoryManager.instance(), direct);
    }

    public AdaptablePoolingAllocator(MemoryManager manager, boolean direct) {
        allocationType = direct ? StandardAllocationTypes.OFF_HEAP : StandardAllocationTypes.ON_HEAP;
        this.manager = manager;
        centralQueue = new ConcurrentLinkedQueue<>();
        allocatorControl = new SimpleAllocatorControl(this);
        magazineExpandLock = new StampedLock();
        Magazine[] mags = new Magazine[4];
        for (int i = 0; i < mags.length; i++) {
            mags[i] = new Magazine(this);
        }
        magazines = mags;
    }

    @Override
    public boolean isPooling() {
        return true;
    }

    @Override
    public AllocationType getAllocationType() {
        return allocationType;
    }

    @Override
    public Buffer allocate(int size) {
        if (closed) {
            throw allocatorClosedException();
        }
        InternalBufferUtils.assertValidBufferSize(size);
        int sizeBucket = Magazine.sizeBucket(size); // Compute outside of Magazine lock for better ILP.
        int expansions = 0;
        do {
            Magazine[] mags = magazines;
            int mask = mags.length - 1;
            int index = (int) (threadId(Thread.currentThread()) & mask);
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
            tryExpandMagazines();
            expansions++;
        } while (expansions < 3);
        // The magazines failed us. Allocate unpooled buffer.
        return manager.allocateShared(allocatorControl, size, standardDrop(manager), allocationType);
    }

    private void tryExpandMagazines() {
        long writeLock = magazineExpandLock.tryWriteLock();
        if (writeLock != 0) {
            try {
                Magazine[] mags = magazines;
                if (mags.length >= MAX_STRIPES) {
                    return;
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
        drainCloseCentralQueue();
    }

    private void drainCloseCentralQueue() {
        Buffer curr;
        while ((curr = centralQueue.poll()) != null) {
            curr.close();
        }
    }

    private void offerToQueue(Buffer buffer) {
        centralQueue.offer(buffer);
        if (closed) {
            drainCloseCentralQueue();
        }
    }

    private static final class Magazine extends StampedLock {
        private static final long serialVersionUID = -4068223712022528165L;
        private static final VarHandle NEXT_IN_LINE;

        static {
            try {
                NEXT_IN_LINE = MethodHandles.lookup().findVarHandle(Magazine.class, "nextInLine", Buffer.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        private final AdaptablePoolingAllocator parent;
        private Buffer current;
        @SuppressWarnings("unused") // updated via VarHandle
        private volatile Buffer nextInLine;

        Magazine(AdaptablePoolingAllocator parent) {
            this.parent = parent;
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
                    parent.offerToQueue(buffer);
                }
            }
            return result;
        }

        private final short[][] histos = {
           new short[8], new short[8], new short[8], new short[8],
        };
        private short[] histo = histos[0];
        private final int[] sums = new int[8];

        private int histoIndex;
        private int datumCount;
        private int datumTarget = 8192;
        private volatile int localPrefChunkSize = MIN_CHUNK_SIZE;
        private int sharedPrefChunkSize = MIN_CHUNK_SIZE;

        private void recordAllocationSize(int bucket) {
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
            int normalizedSize = size - 1 >> 13 & (1 << 7) - 1;
            return Integer.SIZE - Integer.numberOfLeadingZeros(normalizedSize);
        }

        private void rotateHistograms() {
            Arrays.fill(sums, 0);
            int sum = 0;
            for (short[] buckets : histos) {
                int len = buckets.length;
                for (int i = 0; i < len; i++) {
                    int count = buckets[i] & 0xFFFF; // Read as unsigned short.
                    sums[i] += count;
                    sum  += count;
                }
            }
            int targetPercentile = (int) (sum * 0.99);
            int sizeBucket = 0;
            for (; sizeBucket < sums.length; sizeBucket++) {
                if (sums[sizeBucket] > targetPercentile) {
                    break;
                }
                targetPercentile -= sums[sizeBucket];
            }
            int percentileSize = 1 << sizeBucket + 13;
            int prefChunkSize = Math.max(percentileSize * 10, MIN_CHUNK_SIZE);
            localPrefChunkSize = prefChunkSize;
            for (Magazine mag : parent.magazines) {
                prefChunkSize = Math.max(prefChunkSize, mag.localPrefChunkSize);
            }
            if (sharedPrefChunkSize != prefChunkSize) {
                // Preferred chunk size changed. Increase check frequency.
                datumTarget = Math.max(datumTarget >> 1, 1024);
            } else {
                // Preferred chunk size did not change. Check less often.
                datumTarget = Math.min(datumTarget << 1, 65534);
            }
            sharedPrefChunkSize = prefChunkSize;

            histoIndex = histoIndex + 1 & histos.length - 1;
            histo = histos[histoIndex];
            datumCount = 0;
            Arrays.fill(histos[histoIndex], (short) 0);
        }

        /**
         * Get the preferred chunk size, based on statistics from the {@linkplain #recordAllocationSize(int) recorded}
         * allocation sizes.
         * <p>
         * This method must be thread-safe.
         *
         * @return The currently preferred chunk allocation size.
         */
        private int preferredChunkSize() {
            return sharedPrefChunkSize;
        }

        private Buffer newChunkAllocation(int promptingSize) {
            int size = Math.max(promptingSize * 10, preferredChunkSize());
            return parent.manager.allocateShared(parent.allocatorControl, size, this::decorate, parent.allocationType);
        }

        private Drop<Buffer> decorate(Drop<Buffer> drop) {
            if (drop instanceof ArcDrop) {
                drop = ((ArcDrop<Buffer>) drop).unwrap();
            }
            return CleanerDrop.wrap(ArcDrop.wrap(new PoolDrop(drop, this)), parent.manager);
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
        private final Drop<Buffer> drop;
        private final Magazine magazine;
        private Object memory;

        PoolDrop(Drop<Buffer> drop, Magazine magazine) {
            this.drop = drop;
            this.magazine = magazine;
        }

        @Override
        public void drop(Buffer obj) {
            Magazine mag = magazine;
            AdaptablePoolingAllocator parent = mag.parent;
            MemoryManager manager = parent.manager;
            int chunkSize = mag.preferredChunkSize();
            int memSize = manager.sizeOf(memory);
            if (parent.closed || memSize < chunkSize || memSize > chunkSize + (chunkSize >> 1)) {
                // Drop the chunk if the parent allocator is closed, or if the chunk is smaller than the
                // preferred chunk size, or over 50% larger than the preferred chunk size.
                drop.drop(obj);
            } else {
                Buffer buffer = manager.recoverMemory(
                        parent.allocatorControl, memory, CleanerDrop.wrap(ArcDrop.wrap(this), manager));
                if (!mag.trySetNextInLine(buffer)) {
                    parent.offerToQueue(buffer);
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
