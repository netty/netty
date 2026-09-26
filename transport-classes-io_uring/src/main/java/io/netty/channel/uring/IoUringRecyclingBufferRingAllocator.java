/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.buffer.UnpooledUnsafeDirectByteBuf;
import io.netty.channel.unix.Buffer;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link IoUringBufferRingAllocator} which reserves one contiguous direct region per event loop and re-uses the
 * buffers carved out of it, instead of allocating a new {@link ByteBuf} from a general purpose
 * {@link ByteBufAllocator} for every buffer the kernel consumed. A buffer is handed out again once the ring and
 * everything the pipeline derived from it released it, so nothing is allocated in the steady state.
 *
 * <p>The region is one buffer taken from the given {@link ByteBufAllocator}, so it is accounted for like every
 * other buffer of that allocator. It holds {@code bufferRingSize} buffers of {@code bufferSize} bytes, which is
 * what a buffer ring keeps alive anyway, and is extended <em>once</em> if the pipeline holds on to more buffers
 * than the ring keeps alive. Once even that is used up {@link #allocate()} takes a buffer from the same
 * {@link ByteBufAllocator} and counts it in {@link #fallbackAllocations()}, rather than failing a read. It starts
 * at a page boundary, so a {@code bufferSize} that is a multiple of the page size keeps every buffer aligned too.
 *
 * <p>Every thread that allocates gets its own region, which is the ring's event loop in every supported use, so
 * one instance can serve any number of buffer rings: that is what happens when one {@link IoUringIoHandlerConfig}
 * creates more than one {@link io.netty.channel.IoHandler}. A region is released when its thread terminates,
 * which is guaranteed for a {@link io.netty.util.concurrent.FastThreadLocalThread}, i.e. every event loop, or, if
 * a buffer of it is still in flight then, once that last buffer is released. Buffers may be released from any
 * thread, and one that allocates on a thread which does not clean up its {@link FastThreadLocal}s is served from
 * the {@link ByteBufAllocator} instead of being given a region of its own.
 *
 * <p>Each instance registers one {@link FastThreadLocal} index for the life of the process, like the
 * {@code Recycler} and the pooled allocator's thread cache: create one allocator per configuration and share it,
 * do not create one per channel.
 */
public final class IoUringRecyclingBufferRingAllocator implements IoUringBufferRingAllocator {

    private final ByteBufAllocator allocator;
    private final int bufferSize;
    private final int buffers;
    private final int maxBuffers;
    // Chosen once, exactly like UnpooledByteBufAllocator.newDirectBuffer(int, int) chooses per buffer.
    private final boolean unsafeSlots = PlatformDependent.hasUnsafe();
    private final AtomicLong fallbackAllocations = new AtomicLong();
    private final FastThreadLocal<Region> regions = new FastThreadLocal<Region>() {
        @Override
        protected Region initialValue() {
            return new Region();
        }

        @Override
        protected void onRemoval(Region region) {
            region.free();
        }
    };

    /**
     * Create a new instance which uses {@link ByteBufAllocator#DEFAULT}.
     */
    public IoUringRecyclingBufferRingAllocator(short bufferRingSize, int bufferSize) {
        this(ByteBufAllocator.DEFAULT, bufferRingSize, bufferSize);
    }

    /**
     * Create a new instance.
     *
     * @param allocator         the {@link ByteBufAllocator} the region and any fallback buffer is taken from.
     * @param bufferRingSize    the number of buffers to carve out of the region, which should be the value passed
     *                          to {@link IoUringBufferRingConfig.Builder#bufferRingSize(short)}. If this instance
     *                          serves more than one buffer ring, pass the sum of their sizes.
     * @param bufferSize        the size of each buffer.
     */
    public IoUringRecyclingBufferRingAllocator(ByteBufAllocator allocator, short bufferRingSize, int bufferSize) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.buffers = ObjectUtil.checkPositive(bufferRingSize, "bufferRingSize");
        this.bufferSize = ObjectUtil.checkPositive(bufferSize, "bufferSize");
        this.maxBuffers = buffers * 2;
        if ((long) maxBuffers * bufferSize + 2L * Native.PAGE_SIZE > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("bufferRingSize * bufferSize too large: " + bufferRingSize + " * "
                    + bufferSize + " (plus 2 * " + Native.PAGE_SIZE + " bytes for page alignment)");
        }
    }

    @Override
    public ByteBuf allocate() {
        if (!FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals()) {
            // The same guard AdaptivePoolingAllocator uses for its thread-local heap: a thread that never runs
            // FastThreadLocal.removeAll() would keep a region alive forever, so it gets a plain buffer. A buffer
            // ring only ever allocates on its own event loop, which does clean up.
            fallbackAllocations.incrementAndGet();
            return allocator.directBuffer(bufferSize, bufferSize);
        }
        return regions.get().take();
    }

    @Override
    public void lastBytesRead(int attempted, int actual) {
        // NOOP, the buffer size is fixed.
    }

    /**
     * Returns how often {@link #allocate()} could not be served out of a region and fell back to the
     * {@link ByteBufAllocator}. Expected to be {@code 0} once the number of retained buffers has settled.
     *
     * @return  the number of allocations not served out of a region.
     */
    public long fallbackAllocations() {
        return fallbackAllocations.get();
    }

    /**
     * One buffer of a region. Both implementations use the {@link ByteBuffer} constructor of their super class,
     * which never frees the memory: the region outlives every buffer carved out of it.
     */
    private interface Slot {
        int index();

        /** Refcount and indices back to their initial state, so the ring can write into the buffer again. */
        ByteBuf reuse();
    }

    /**
     * The buffers of one event loop. Only {@link #handback} is touched by any other thread.
     */
    private final class Region {
        private final Thread owner = Thread.currentThread();
        private final Slot[] slots = new Slot[maxBuffers];
        private final int[] unused = new int[maxBuffers];
        // A slot can be in here at most once, and the capacity is the slot count, so offering never fails.
        private final Queue<Slot> handback = PlatformDependent.newFixedMpscQueue(maxBuffers);
        // The buffers the slots were carved out of: the initial region and, after extending, one more.
        private final ByteBuf[] memory = new ByteBuf[2];
        private int numMemory;
        private int numUnused;
        private int numSlots;
        private boolean extended;
        // Set by the owner as its last act. Written before it drains, so a release that offered its buffer after
        // that drain is guaranteed to see it and finish the job, which also relies on an offer being visible to
        // any later poll: the MPSC queue spins on a claimed but not yet stored slot instead of returning null.
        private volatile boolean closing;

        Region() {
            add(buffers);
        }

        ByteBuf take() {
            if (numUnused == 0) {
                return refill();
            }
            return slots[unused[--numUnused]].reuse();
        }

        void put(Slot slot) {
            if (Thread.currentThread() == owner) {
                unused[numUnused++] = slot.index();
                return;
            }
            boolean offered = handback.offer(slot);
            assert offered;
            if (closing) {
                complete();
            }
        }

        void free() {
            closing = true;
            complete();
        }

        /**
         * Give the memory back once every buffer is home. Runs on the thread that terminated the owner and on any
         * thread that released a buffer afterwards, so the monitor is what makes the drain single-consumer - the
         * owner is gone by then - and the release happen exactly once.
         */
        private synchronized void complete() {
            if (numMemory == 0) {
                return;
            }
            drain();
            if (numUnused != numSlots) {
                // A buffer is still in flight. Giving the memory back now would let the allocator hand it out
                // again while the pipeline still reads from it, so the release that brings the last one home
                // comes back here and does it.
                return;
            }
            for (int i = 0; i < numMemory; i++) {
                memory[i].release();
                memory[i] = null;
            }
            numMemory = 0;
        }

        private ByteBuf refill() {
            if (drain() || extend()) {
                return slots[unused[--numUnused]].reuse();
            }
            fallbackAllocations.incrementAndGet();
            // Must not throw: IoUringBufferRing marks the ring as corrupted if allocate() fails.
            return allocator.directBuffer(bufferSize, bufferSize);
        }

        private boolean drain() {
            for (Slot slot = handback.poll(); slot != null; slot = handback.poll()) {
                unused[numUnused++] = slot.index();
            }
            return numUnused > 0;
        }

        private boolean extend() {
            if (extended) {
                return false;
            }
            extended = true;
            try {
                add(maxBuffers - numSlots);
            } catch (OutOfMemoryError ignore) {
                // Fall back instead of failing the read.
                return false;
            }
            return true;
        }

        private void add(int num) {
            // A buffer that was handed out must never move: the ring gave its address to the kernel. So extending
            // reserves more memory and leaves the existing buffers alone.
            int bytes = num * bufferSize;
            ByteBuf region = allocator.directBuffer(bytes + Native.PAGE_SIZE, bytes + Native.PAGE_SIZE);
            Slot[] added = new Slot[num];
            try {
                ByteBuffer nio = region.nioBuffer(0, region.capacity());
                // The address is read the same way the buffer ring reads the address of every buffer it hands the
                // kernel, so that the first buffer starts at a page boundary.
                long address = Buffer.memoryAddress(nio) + nio.position();
                int start = nio.position() + (int) (PlatformDependent.align(address, Native.PAGE_SIZE) - address);
                for (int i = 0; i < num; i++) {
                    ByteBuffer slice = nio.duplicate();
                    slice.position(start + i * bufferSize);
                    slice.limit(slice.position() + bufferSize);
                    int index = numSlots + i;
                    added[i] = unsafeSlots ? new UnsafeSlot(index, slice) : new DefaultSlot(index, slice);
                }
            } catch (Throwable t) {
                // Nothing is published yet, so the only thing to undo is the memory itself.
                region.release();
                throw t;
            }
            memory[numMemory++] = region;
            // Pushed high to low, so the buffers are taken in the order they are laid out in the region.
            for (int i = num - 1; i >= 0; i--) {
                slots[numSlots + i] = added[i];
                unused[numUnused++] = numSlots + i;
            }
            numSlots += num;
        }

        private final class UnsafeSlot extends UnpooledUnsafeDirectByteBuf implements Slot {
            private final int index;

            UnsafeSlot(int index, ByteBuffer memory) {
                super(allocator, memory, bufferSize);
                this.index = index;
                setIndex(0, 0);
            }

            @Override
            public int index() {
                return index;
            }

            @Override
            public ByteBuf reuse() {
                resetRefCnt();
                setIndex(0, 0);
                return this;
            }

            @Override
            protected void deallocate() {
                put(this);
            }
        }

        private final class DefaultSlot extends UnpooledDirectByteBuf implements Slot {
            private final int index;

            DefaultSlot(int index, ByteBuffer memory) {
                super(allocator, memory, bufferSize);
                this.index = index;
                setIndex(0, 0);
            }

            @Override
            public int index() {
                return index;
            }

            @Override
            public ByteBuf reuse() {
                resetRefCnt();
                setIndex(0, 0);
                return this;
            }

            @Override
            protected void deallocate() {
                put(this);
            }
        }
    }
}
