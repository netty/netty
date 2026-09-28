/*
 * Copyright 2024 The Netty Project
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

import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.test.DisabledForSlowLeakDetection;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.netty.buffer.AdaptivePoolingAllocator.IdleDecay;
import io.netty.buffer.AdaptivePoolingAllocator.PendingChunks;
import io.netty.buffer.AdaptivePoolingAllocator.SizeClassChunkRecycler;
import io.netty.buffer.AdaptivePoolingAllocator.SizeClassedChunk;
import io.netty.buffer.AdaptivePoolingAllocator.SizeClassedChunkCache;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import io.netty.buffer.AbstractByteBufTest.TestGatheringByteChannel;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class AdaptiveByteBufAllocatorTest extends AbstractByteBufAllocatorTest<AdaptiveByteBufAllocator> {
    @Override
    protected AdaptiveByteBufAllocator newAllocator(boolean preferDirect) {
        return new AdaptiveByteBufAllocator(preferDirect);
    }

    @Override
    protected AdaptiveByteBufAllocator newUnpooledAllocator() {
        return newAllocator(false);
    }

    @Override
    protected long expectedUsedMemory(AdaptiveByteBufAllocator allocator, int capacity) {
        return 128 * 1024; // Min chunk size
    }

    @Override
    protected long expectedUsedMemoryAfterRelease(AdaptiveByteBufAllocator allocator, int capacity) {
        return 128 * 1024; // Min chunk size
    }

    @Override
    @Test
    public void testUnsafeHeapBufferAndUnsafeDirectBuffer() {
        AdaptiveByteBufAllocator allocator = newUnpooledAllocator();
        ByteBuf directBuffer = allocator.directBuffer();
        assertInstanceOf(directBuffer, AdaptivePoolingAllocator.AdaptiveByteBuf.class);
        assertTrue(directBuffer.isDirect());
        directBuffer.release();

        ByteBuf heapBuffer = allocator.heapBuffer();
        assertInstanceOf(heapBuffer, AdaptivePoolingAllocator.AdaptiveByteBuf.class);
        assertFalse(heapBuffer.isDirect());
        heapBuffer.release();
    }

    @Override
    @Test
    public void testUsedDirectMemory() {
        AdaptiveByteBufAllocator allocator =  newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();
        assertEquals(0, metric.usedDirectMemory());
        ByteBuf buffer = allocator.directBuffer(1024, 4096);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        // This is a new size class, and a new magazine with a new chunk
        assertEquals(2 * expectedUsedMemory(allocator, capacity), metric.usedDirectMemory(), buffer.toString());

        buffer.release();
        // Memory is still held by the magazines
        assertEquals(2 * expectedUsedMemory(allocator, capacity), metric.usedDirectMemory());
    }

    @Override
    @Test
    public void testUsedHeapMemory() {
        AdaptiveByteBufAllocator allocator =  newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();
        assertEquals(0, metric.usedHeapMemory());
        ByteBuf buffer = allocator.heapBuffer(1024, 4096);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        // This is a new size class, and a new magazine with a new chunk
        assertEquals(2 * expectedUsedMemory(allocator, capacity), metric.usedHeapMemory(), buffer.toString());

        buffer.release();
        // Memory is still held by the magazines
        assertEquals(2 * expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());
    }

    /**
     * Buffers above the largest pooled size get a one-shot chunk of their own: accounted while the buffer lives,
     * replaced on growth with the content kept, and freed as soon as the buffer is released.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void oneShotChunkIsFreedWithItsBuffer(boolean direct) {
        AdaptiveByteBufAllocator allocator = newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();
        int size = 2 * 1024 * 1024;
        ByteBuf buffer = direct ? allocator.directBuffer(size, Integer.MAX_VALUE) :
                allocator.heapBuffer(size, Integer.MAX_VALUE);
        assertEquals(size, buffer.capacity());
        assertEquals(size, direct ? metric.usedDirectMemory() : metric.usedHeapMemory());
        buffer.writeLong(0x0123456789ABCDEFL);
        buffer.setLong(size - 8, 0xFEDCBA9876543210L);

        buffer.capacity(2 * size);
        assertEquals(2 * size, buffer.capacity());
        // The first chunk was freed when the buffer moved to the second.
        assertEquals(2 * size, direct ? metric.usedDirectMemory() : metric.usedHeapMemory());
        assertEquals(0x0123456789ABCDEFL, buffer.getLong(0));
        assertEquals(0xFEDCBA9876543210L, buffer.getLong(size - 8));

        assertTrue(buffer.release());
        assertEquals(0, direct ? metric.usedDirectMemory() : metric.usedHeapMemory());
    }

    @Test
    void adaptiveChunkMustDeallocateOrReuseWthBufferRelease() throws Exception {
        AdaptiveByteBufAllocator allocator = newAllocator(false);
        Deque<ByteBuf> bufs = new ArrayDeque<>();
        assertEquals(0, allocator.usedHeapMemory());
        assertEquals(0, allocator.usedHeapMemory());
        bufs.add(allocator.heapBuffer(256));
        long usedHeapMemory = allocator.usedHeapMemory();
        int buffersPerChunk = Math.toIntExact(usedHeapMemory / 256);
        for (int i = 0; i < buffersPerChunk; i++) {
            bufs.add(allocator.heapBuffer(256));
        }
        assertEquals(2 * usedHeapMemory, allocator.usedHeapMemory());
        bufs.pop().release();
        assertEquals(2 * usedHeapMemory, allocator.usedHeapMemory());
        while (!bufs.isEmpty()) {
            bufs.pop().release();
        }
        assertEquals(2 * usedHeapMemory, allocator.usedHeapMemory());
        for (int i = 0; i < 2 * buffersPerChunk; i++) {
            bufs.add(allocator.heapBuffer(256));
        }
        assertEquals(2 * usedHeapMemory, allocator.usedHeapMemory());
        while (!bufs.isEmpty()) {
            bufs.pop().release();
        }
    }

    @Test
    public void getBytesBoundaryCheckWithFileChannel() {
        AdaptiveByteBufAllocator allocator = newAllocator(false);
        final ByteBuf buf = allocator.directBuffer(7);
        try {
            assertThrows(IndexOutOfBoundsException.class, new Executable() {
                @Override
                public void execute() throws IOException {
                    // capacity 7，4+8=12 <= maxFastCapacity 32
                    buf.getBytes(4, (FileChannel) null, 0L, 8);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testGetBytesBoundaryCheckWithGatheringByteChannel() throws Exception {
        AdaptiveByteBufAllocator allocator = newAllocator(false);
        TestGatheringByteChannel channel = new TestGatheringByteChannel();
        final ByteBuf buf = allocator.directBuffer(7);
        try {
            assertThrows(IndexOutOfBoundsException.class, new Executable() {
                @Override
                public void execute() throws IOException {
                    // capacity 7，4+8=12 <= maxFastCapacity 32
                    buf.getBytes(4, channel, 8);
                }
            });
        } finally {
            channel.close();
            buf.release();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void sliceOrDuplicateUnwrapLetNotEscapeRootParent(boolean slice) {
        AdaptiveByteBufAllocator allocator = newAllocator(false);
        ByteBuf buffer = allocator.buffer(8);
        assertInstanceOf(buffer, AdaptivePoolingAllocator.AdaptiveByteBuf.class);
        // Unwrap if this is wrapped by a leak aware buffer.
        if (buffer instanceof SimpleLeakAwareByteBuf) {
            assertNull(buffer.unwrap().unwrap());
        } else {
            assertNull(buffer.unwrap());
        }

        ByteBuf derived = slice ? buffer.slice(0, 4) : buffer.duplicate();
        // When we unwrap the derived buffer we should get our original buffer of type AdaptiveByteBuf back.
        ByteBuf unwrapped = derived instanceof SimpleLeakAwareByteBuf ?
                derived.unwrap().unwrap() : derived.unwrap();
        assertInstanceOf(unwrapped, AdaptivePoolingAllocator.AdaptiveByteBuf.class);
        assertSameBuffer(buffer instanceof SimpleLeakAwareByteBuf ? buffer.unwrap() : buffer, unwrapped);

        ByteBuf retainedDerived = slice ? buffer.retainedSlice(0, 4) : buffer.retainedDuplicate();
        // When we unwrap the derived buffer we should get our original buffer of type AdaptiveByteBuf back.
        ByteBuf unwrappedRetained = retainedDerived instanceof SimpleLeakAwareByteBuf ?
                retainedDerived.unwrap().unwrap() :  retainedDerived.unwrap();
        assertInstanceOf(unwrappedRetained, AdaptivePoolingAllocator.AdaptiveByteBuf.class);
        assertSameBuffer(buffer instanceof SimpleLeakAwareByteBuf ? buffer.unwrap() : buffer, unwrappedRetained);
        retainedDerived.release();

        assertTrue(buffer.release());
    }

    @Test
    public void testAllocateWithoutLock() throws InterruptedException {
        final AdaptiveByteBufAllocator alloc = new AdaptiveByteBufAllocator();
        // Make `threadCount` bigger than `AdaptivePoolingAllocator.MAX_STRIPES`, to let thread collision easily happen.
        int threadCount = NettyRuntime.availableProcessors() * 4;
        final CountDownLatch countDownLatch = new CountDownLatch(threadCount);
        final AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<Throwable>();
        for (int i = 0; i < threadCount; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < 1024; j++) {
                        try {
                            ByteBuf buffer = null;
                            try {
                                buffer = alloc.heapBuffer(128);
                                buffer.ensureWritable(ThreadLocalRandom.current().nextInt(512, 32769));
                            } finally {
                                if (buffer != null) {
                                    buffer.release();
                                }
                            }
                        } catch (Throwable t) {
                            throwableAtomicReference.set(t);
                        }
                    }
                    countDownLatch.countDown();
                }
            }).start();
        }
        countDownLatch.await();
        Throwable throwable = throwableAtomicReference.get();
        if (throwable != null) {
            fail("Expected no exception, but got", throwable);
        }
    }

    /**
     * Blocks released on another thread make their buddy chunks usable again for the thread that allocates: a second
     * round of the same allocations after a foreign release needs no new memory.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void buddyChunksReleasedByAnotherThreadAreReused(boolean direct) throws Exception {
        final AdaptiveByteBufAllocator allocator = newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();
        final int size = 512 * 1024; // above the largest size class, below the unpooled fallback
        final ByteBuf[] bufs = new ByteBuf[24];
        for (int i = 0; i < bufs.length; i++) {
            bufs[i] = direct ? allocator.directBuffer(size, size) : allocator.heapBuffer(size, size);
        }
        long used = direct ? metric.usedDirectMemory() : metric.usedHeapMemory();
        Thread releaser = new Thread(() -> {
            for (ByteBuf buf : bufs) {
                buf.release();
            }
        });
        releaser.start();
        releaser.join();
        for (int i = 0; i < bufs.length; i++) {
            bufs[i] = direct ? allocator.directBuffer(size, size) : allocator.heapBuffer(size, size);
        }
        assertEquals(used, direct ? metric.usedDirectMemory() : metric.usedHeapMemory());
        for (ByteBuf buf : bufs) {
            buf.release();
        }
    }

    /**
     * Idle memory above the size classes is bounded in bytes, whatever the size of the chunks, and the bound is
     * applied by the releases: after a burst of large buffers is released, no more than the idle bound plus the
     * chunk the magazine allocates from is held, without any further allocation.
     */
    @Test
    void idleBuddyMemoryIsBoundedInBytes() {
        AdaptiveByteBufAllocator allocator = newAllocator(true);
        int size = 1024 * 1024; // the largest pooled size: the largest chunks
        ByteBuf first = allocator.heapBuffer(size, size);
        long chunkSize = allocator.usedHeapMemory();
        List<ByteBuf> bufs = new ArrayList<ByteBuf>();
        bufs.add(first);
        // Four times the idle bound, in whole chunks.
        long burst = 4L * AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES;
        while (allocator.usedHeapMemory() < burst) {
            bufs.add(allocator.heapBuffer(size, size));
        }
        long peak = allocator.usedHeapMemory();
        for (ByteBuf buf : bufs) {
            buf.release();
        }
        // No allocation follows: a heap that goes quiet must not keep the burst. The releases themselves apply the
        // bound (nothing else holds the stripe lock here, so every release acts in place).
        long settled = allocator.usedHeapMemory();
        long bound = AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES;
        assertTrue(settled <= bound + chunkSize, "peak " + peak + ", settled " + settled + ", bound " + bound);
    }

    /**
     * Wholly free buddy chunks are not kept beyond the reuse limit: after a burst is released, the next allocations
     * leave at most {@link AdaptivePoolingAllocator#CHUNK_REUSE_QUEUE} idle chunks plus the ones in use.
     */
    @Test
    void idleBuddyChunksAboveTheReuseLimitAreFreed() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool buffers above its size classes");
        AdaptiveByteBufAllocator allocator = newAllocator(true);
        int size = 256 * 1024; // above the largest size class, so buddy chunks
        // The first buffer creates the first chunk: its size and how many buffers it holds come from the allocator,
        // not from the sizing formula copied here.
        ByteBuf first = allocator.heapBuffer(size, size);
        long chunkSize = allocator.usedHeapMemory();
        int buffersPerChunk = (int) (chunkSize / size);
        assertTrue(buffersPerChunk >= 2, "buffers per chunk " + buffersPerChunk);
        int chunks = AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE + 8;
        List<ByteBuf> bufs = new ArrayList<ByteBuf>();
        bufs.add(first);
        while (bufs.size() < chunks * buffersPerChunk) {
            bufs.add(allocator.heapBuffer(size, size));
        }
        long peak = allocator.usedHeapMemory();
        assertEquals((long) chunks * chunkSize, peak, "one chunk per " + buffersPerChunk + " buffers");
        for (ByteBuf buf : bufs) {
            buf.release();
        }
        // Everything is idle now: allocating a chunk's worth again plus one takes the slow path, which applies the
        // releases and frees what is kept beyond the limit.
        List<ByteBuf> again = new ArrayList<ByteBuf>();
        for (int i = 0; i <= buffersPerChunk; i++) {
            again.add(allocator.heapBuffer(size, size));
        }
        long settled = allocator.usedHeapMemory();
        assertTrue(settled <= (AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE + 2) * chunkSize,
                "peak " + peak + ", settled " + settled + ", chunk " + chunkSize);
        for (ByteBuf buf : again) {
            buf.release();
        }
    }

    /**
     * Several threads allocate buddy-sized buffers and hand them to each other to check and release, so blocks go
     * back through the chunks' free lists from threads that did not allocate them while the stripe's magazine keeps
     * allocating: every buffer keeps its content until it is released, and nothing fails (run with assertions on).
     */
    @DisabledForSlowLeakDetection
    @Test
    void buddyBuffersReleasedAcrossThreadsKeepTheirContent() throws Throwable {
        final AdaptiveByteBufAllocator allocator = newAllocator(true);
        final int[] sizes = {140 * 1024, 256 * 1024, 300 * 1024, 512 * 1024, 700 * 1024, 1024 * 1024};
        final int threads = 8;
        final int rounds = 3000;
        final BlockingQueue<ByteBuf> handoff = new ArrayBlockingQueue<ByteBuf>(64);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        List<Thread> workers = new ArrayList<Thread>();
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            Thread worker = new Thread(() -> {
                SplittableRandom rng = new SplittableRandom(seed);
                try {
                    for (int i = 0; i < rounds && failure.get() == null; i++) {
                        int size = sizes[rng.nextInt(sizes.length)];
                        ByteBuf buf = rng.nextBoolean() ? allocator.heapBuffer(size, size) :
                                allocator.directBuffer(size, size);
                        byte mark = (byte) rng.nextInt();
                        buf.writerIndex(size);
                        buf.setByte(0, mark);
                        buf.setByte(size - 1, mark);
                        buf.setByte(size / 2, mark);
                        if (!handoff.offer(buf)) {
                            buf.release();
                        }
                        ByteBuf other = handoff.poll();
                        if (other != null) {
                            int n = other.capacity();
                            byte m = other.getByte(0);
                            assertEquals(m, other.getByte(n - 1));
                            assertEquals(m, other.getByte(n / 2));
                            other.release();
                        }
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        ByteBuf left;
        while ((left = handoff.poll()) != null) {
            left.release();
        }
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    /**
     * A magazine picks the chunk with the largest free block, not the smallest one that fits: the chunk it then
     * allocates from serves many requests before it runs out, instead of one.
     */
    @Test
    void buddyAllocationPrefersTheChunkWithTheLargestFreeBlock() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool buffers above its size classes");
        AdaptiveByteBufAllocator allocator = newAllocator(true);
        int size = 256 * 1024; // above the largest size class, so buddy chunks
        ByteBuf first = allocator.heapBuffer(size, size);
        long chunkSize = allocator.usedHeapMemory();
        int perChunk = (int) (chunkSize / size);
        if (perChunk < 8 || perChunk % 4 != 0) {
            first.release();
            assumeTrue(false, "chunk holds " + perChunk + " buffers");
        }

        // Three chunks, each filled completely: A, B, then C, which stays the magazine's active chunk.
        List<ByteBuf> bufs = new ArrayList<ByteBuf>();
        bufs.add(first);
        while (bufs.size() < 3 * perChunk) {
            bufs.add(allocator.heapBuffer(size, size));
        }
        Object chunkA = anyChunkOf(bufs.get(0));
        Object chunkB = anyChunkOf(bufs.get(perChunk));
        Object chunkC = anyChunkOf(bufs.get(2 * perChunk));
        assumeTrue(chunkA != chunkB && chunkB != chunkC, "one chunk per " + perChunk + " buffers");

        // A is left with one free block; B with four neighbours, which merge into a block four times as large.
        bufs.get(0).release();
        for (int i = 0; i < 4; i++) {
            bufs.get(perChunk + i).release();
        }

        // C is full, so this takes the slow path: it applies the releases and picks between A and B.
        ByteBuf next = allocator.heapBuffer(size, size);
        assertSame(chunkB, anyChunkOf(next), "expected the chunk with the larger free block");

        next.release();
        for (int i = 0; i < bufs.size(); i++) {
            if (i != 0 && (i < perChunk || i >= perChunk + 4)) {
                bufs.get(i).release();
            }
        }
    }

    /**
     * Buddy chunks given up by a magazine are reused by it: allocating and releasing the same set of large buffers
     * over and over from one thread does not grow the memory held after the first round.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void buddyChunksAreReusedAcrossRounds(boolean direct) throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool buffers above its size classes");
        AdaptiveByteBufAllocator allocator = newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();
        int size = 512 * 1024; // above the largest size class, below the unpooled fallback
        ByteBuf[] bufs = new ByteBuf[24];
        long afterFirstRound = -1;
        for (int round = 0; round < 50; round++) {
            for (int i = 0; i < bufs.length; i++) {
                bufs[i] = direct ? allocator.directBuffer(size, size) : allocator.heapBuffer(size, size);
            }
            for (ByteBuf buf : bufs) {
                buf.release();
            }
            long used = direct ? metric.usedDirectMemory() : metric.usedHeapMemory();
            if (afterFirstRound < 0) {
                afterFirstRound = used;
                assertTrue(used >= (long) size * bufs.length, "used " + used);
            } else {
                assertEquals(afterFirstRound, used, "round " + round);
            }
        }
    }

    @DisabledForSlowLeakDetection
    @RepeatedTest(100)
    void buddyAllocationConsistency(RepetitionInfo info) {
        SplittableRandom rng = new SplittableRandom(info.getCurrentRepetition());
        AdaptiveByteBufAllocator allocator = newAllocator(true);
        int small = 256 * 1024; // above the largest size class, so every size here takes the buddy path
        int large = 2 * small;
        int xlarge = 2 * large;

        int[] allocationSizes = {
                small, small, small, small, small, small, small, small,
                large, large, large, large,
                xlarge, xlarge,
        };

        shuffle(rng, allocationSizes);

        ByteBuf[] bufs = new ByteBuf[allocationSizes.length];
        Arrays.setAll(bufs, i -> allocator.buffer(allocationSizes[i], allocationSizes[i]));

        shuffle(rng, bufs);

        int[] reallocations = new int[bufs.length / 2];
        for (int i = 0; i < reallocations.length; i++) {
            reallocations[i] = bufs[i].capacity();
            bufs[i].release();
            bufs[i] = null;
        }
        for (int i = 0; i < reallocations.length; i++) {
            assertNull(bufs[i]);
            bufs[i] = allocator.buffer(reallocations[i], reallocations[i]);
        }

        for (int i = 0; i < bufs.length; i++) {
            while (bufs[i].isWritable()) {
                bufs[i].writeByte(i + 1);
            }
        }
        try {
            for (int i = 0; i < bufs.length; i++) {
                while (bufs[i].isReadable()) {
                    int b = Byte.toUnsignedInt(bufs[i].readByte());
                    if (b != i + 1) {
                        fail("Expected byte " + (i + 1) +
                                " at index " + (bufs[i].readerIndex() - 1) +
                                " but got " + b);
                    }
                }
            }
        } finally {
            for (ByteBuf buf : bufs) {
                buf.release();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void idleChunksAreEvictedAfterRelease(boolean threadLocal) throws Exception {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, threadLocal);
        Runnable test = () -> assertIdleChunksEvictedAfterRelease(allocator);
        if (threadLocal) {
            FastThreadLocalThread.runWithFastThreadLocal(test);
        } else {
            test.run();
        }
    }

    private static void assertIdleChunksEvictedAfterRelease(AdaptiveByteBufAllocator allocator) {
        ByteBuf probe = allocator.heapBuffer(256);
        long chunkSize = allocator.usedHeapMemory();
        int buffersPerChunk = (int) (chunkSize / 256);
        probe.release();

        // Create a burst: allocate many chunks' worth of buffers, twice what the heap's recycler keeps, so that some
        // must be freed whatever the number of processors (usedMemory() counts what the recycler keeps).
        int recyclerChunks = (int) (SizeClassChunkRecycler.RECYCLED_BYTES_BUDGET / chunkSize);
        int totalChunks = Math.max(Math.max(16, AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE) * 4,
                2 * recyclerChunks) + 10;
        int totalBuffers = totalChunks * buffersPerChunk;
        List<ByteBuf> bufs = new ArrayList<>(totalBuffers);
        for (int i = 0; i < totalBuffers; i++) {
            bufs.add(allocator.heapBuffer(256));
        }
        long memoryDuringBurst = allocator.usedHeapMemory();

        // Release all buffers. With inline Signal B detection, fully-free chunks
        // above the retention floor are evicted immediately during release.
        for (ByteBuf buf : bufs) {
            buf.release();
        }
        bufs.clear();

        // Do a few allocation cycles to trigger purge for cross-thread return detection
        for (int poll = 0; poll < 20; poll++) {
            for (int i = 0; i < buffersPerChunk; i++) {
                bufs.add(allocator.heapBuffer(256));
            }
            for (ByteBuf buf : bufs) {
                buf.release();
            }
            bufs.clear();
        }

        long memoryAfterSettled = allocator.usedHeapMemory();
        assertTrue(memoryAfterSettled < memoryDuringBurst,
                "Memory should decrease after burst release. " +
                "During burst: " + memoryDuringBurst + ", after settled: " + memoryAfterSettled);
        // What stays is what the recycler keeps plus the few chunks the size class still holds.
        assertTrue(memoryAfterSettled <= SizeClassChunkRecycler.RECYCLED_BYTES_BUDGET + 4 * chunkSize,
                "After settled: " + memoryAfterSettled + ", recycler budget: " +
                SizeClassChunkRecycler.RECYCLED_BYTES_BUDGET + ", chunk: " + chunkSize);
    }

    /**
     * The chunk a size-class magazine allocates from must keep serving it after becoming fully free, whichever path
     * the return takes and whatever the drain and the purge do, even with the cache above its retention floor.
     *
     * <ul>
     *   <li>{@code owner}: thread-local heap, released by its owner thread (inline, no lock).</li>
     *   <li>{@code locked}: shared stripe, released by another thread that wins the stripe lock.</li>
     *   <li>{@code notified}: thread-local heap, released by another thread, which leaves a note that the
     *       owner drains.</li>
     * </ul>
     */
    @ParameterizedTest
    @ValueSource(strings = {"owner", "locked", "notified"})
    void activeChunkKeepsServingAllocationsWhenFullyFreeAboveTheFloor(String releasePath) throws Exception {
        final boolean threadLocal = !"locked".equals(releasePath);
        final boolean foreignRelease = !"owner".equals(releasePath);
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, threadLocal);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Runnable test = () -> {
            try {
                assertActiveChunkKeepsServingAllocations(allocator, !threadLocal, foreignRelease);
            } catch (Throwable t) {
                failure.set(t);
            }
        };
        if (threadLocal) {
            FastThreadLocalThread.runWithFastThreadLocal(test);
        } else {
            test.run();
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static void assertActiveChunkKeepsServingAllocations(
            AdaptiveByteBufAllocator allocator, boolean sharedStripe, boolean foreignRelease) throws Exception {
        List<ByteBuf> held = new ArrayList<ByteBuf>();
        try {
            // Hand out every segment of the first chunk: the allocation after that lands in another chunk.
            for (int i = 0; i < BURST_SEGMENTS_PER_CHUNK; i++) {
                held.add(allocator.heapBuffer(BURST_BUF_SIZE));
            }
            byte[] firstArray = held.get(0).array();
            for (ByteBuf buf : held) {
                assertSame(firstArray, buf.array());
            }
            final SizeClassedChunkCache cache = chunkOf(held.get(0)).owningCache;

            // Fill the cache above its retention floor with chunks that have no free segment.
            int floor = 1; // the cache never gives up the last chunk of its size class
            for (int i = BURST_SEGMENTS_PER_CHUNK; i < (floor + 1) * BURST_SEGMENTS_PER_CHUNK; i++) {
                held.add(allocator.heapBuffer(BURST_BUF_SIZE));
            }
            assertEquals((long) (floor + 1) * BURST_CHUNK_SIZE, allocator.usedHeapMemory());

            // One segment of a fresh chunk, returned: that chunk is fully free, above the floor.
            ByteBuf probe = allocator.heapBuffer(BURST_BUF_SIZE);
            byte[] activeArray = probe.array();
            SizeClassedChunk active = chunkOf(probe);
            long used = allocator.usedHeapMemory();
            assertEquals((long) (floor + 2) * BURST_CHUNK_SIZE, used);
            release(probe, foreignRelease);
            underStripeLocks(allocator, sharedStripe, cache::drainPending);
            assertEquals(used, allocator.usedHeapMemory(),
                    "a fully free active chunk must not be evicted on release");
            underStripeLocks(allocator, sharedStripe, cache::tickPurge);
            assertEquals(used, allocator.usedHeapMemory(),
                    "a fully free active chunk must not be evicted by the purge");
            // The one representation check: it is still the cache's active chunk.
            assertSame(active, cache.active);

            ByteBuf next = allocator.heapBuffer(BURST_BUF_SIZE);
            held.add(next);
            assertSame(activeArray, next.array(), "the next allocation must land in the same chunk");
            assertEquals(used, allocator.usedHeapMemory());

            // Control: in the same state, a fully free chunk that is not active is evicted, so the assertions
            // above are about the active chunk and not about a cache that would evict nothing. Its buffer goes to
            // the heap's recycler, where it is still the allocator's memory.
            int recycled = cache.chunkRecycler.retainedBytes();
            for (int i = 0; i < BURST_SEGMENTS_PER_CHUNK; i++) {
                release(held.get(i), foreignRelease);
            }
            held.subList(0, BURST_SEGMENTS_PER_CHUNK).clear();
            underStripeLocks(allocator, sharedStripe, cache::drainPending);
            assertEquals(recycled + BURST_CHUNK_SIZE, cache.chunkRecycler.retainedBytes(), "evicted to the recycler");
            assertEquals(used, allocator.usedHeapMemory());
        } finally {
            for (ByteBuf buf : held) {
                buf.release();
            }
        }
    }

    // --- Where a note is applied -----------------------------------------------------------------------------
    //
    // A release that cannot apply itself - another thread's, on a thread-local heap or while the stripe lock is
    // taken - puts the segment or block on its chunk's free list and leaves a note for the chunk's cache. The tests
    // below pin each place that applies notes, and set things up so that nothing else could have: a size class that
    // went idle never takes its own slow path again, so its notes wait for another size class's slow path or for a
    // purge tick of its heap; a buddy chunk's note waits for the magazine's next slow path, and a block whose note
    // is still in flight is found by the bounded probe of the full chunks.

    private static final int NOTE_ALLOCATING_SIZE = 4096;
    private static final int NOTE_IDLE_SIZE = 1024;

    /** A size class left idle with notes outstanding on two of its three chunks; see {@link #leaveNotes}. */
    private static final class IdleSizeClass {
        final SizeClassedChunkCache cache;
        final List<ByteBuf> stillHeld;
        final int chunkSize;
        final int recycledBefore;

        IdleSizeClass(SizeClassedChunkCache cache, List<ByteBuf> stillHeld, int chunkSize, int recycledBefore) {
            this.cache = cache;
            this.stillHeld = stillHeld;
            this.chunkSize = chunkSize;
            this.recycledBefore = recycledBefore;
        }

        /** The two emptied chunks left the cache, which keeps the one still in use, for the heap's recycler. */
        void assertNotesApplied(String when) {
            assertEquals(0, cache.pendingCount(), when + ": the notes must be applied");
            assertEquals(0, cache.reusable.size, when);
            assertEquals(1, cache.exhausted.size, when + ": only the chunk still in use stays");
        }

        void releaseRest() {
            for (ByteBuf buf : stillHeld) {
                buf.release();
            }
        }
    }

    /**
     * Fill three chunks of {@link #NOTE_IDLE_SIZE} exactly, so all three are filed as exhausted, then release the
     * buffers of the first two from another thread that cannot apply the release: on a thread-local heap it is not
     * the owner, and on a stripe it runs while the stripe lock is held. Each of the two chunks gets one note.
     */
    private static IdleSizeClass leaveNotes(AdaptiveByteBufAllocator allocator, boolean sharedStripe)
            throws Exception {
        int chunkSize = AdaptivePoolingAllocator.chunkSizeOf(NOTE_IDLE_SIZE);
        int perChunk = chunkSize / NOTE_IDLE_SIZE;
        final List<ByteBuf> released = new ArrayList<ByteBuf>();
        List<ByteBuf> stillHeld = new ArrayList<ByteBuf>();
        for (int i = 0; i < 3 * perChunk; i++) {
            ByteBuf buf = allocator.heapBuffer(NOTE_IDLE_SIZE, NOTE_IDLE_SIZE);
            (i < 2 * perChunk ? released : stillHeld).add(buf);
        }
        SizeClassedChunkCache cache = chunkOf(stillHeld.get(0)).owningCache;
        assertEquals(3, cache.exhausted.size, "three full chunks");
        int recycledBefore = cache.chunkRecycler.retainedBytes();
        underStripeLocks(allocator, sharedStripe, () -> {
            try {
                Thread t = new Thread(() -> {
                    for (ByteBuf buf : released) {
                        buf.release();
                    }
                });
                t.start();
                t.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
        assertEquals(2, cache.pendingCount(), "one note per emptied chunk");
        assertEquals(3, cache.exhausted.size, "nothing applied the notes yet");
        return new IdleSizeClass(cache, stillHeld, chunkSize, recycledBefore);
    }

    /** Run on the thread that owns a thread-local heap, or on a plain thread that allocates from a stripe. */
    private static void onHeapThread(boolean threadLocal, final ThrowingRunnable body) throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Runnable task = () -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        };
        Thread thread = threadLocal ? new FastThreadLocalThread(task) : new Thread(task);
        thread.start();
        thread.join();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * The slow path of one size class applies the notes of every other size class of its heap, so the notes of a
     * size class that went idle are applied by the first allocation of another one. The chunk that allocation needs
     * is then built from a buffer the idle size class just gave up: no memory is allocated.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void anotherSizeClassSlowPathAppliesTheNotesOfAnIdleOne(final boolean threadLocal) throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, threadLocal);
        onHeapThread(threadLocal, () -> {
            IdleSizeClass idle = leaveNotes(allocator, !threadLocal);
            ByteBuf first = null;
            try {
                long used = allocator.usedHeapMemory();
                assertEquals(AdaptivePoolingAllocator.chunkSizeOf(NOTE_ALLOCATING_SIZE), idle.chunkSize,
                        "both size classes must share a chunk size, and so a recycler pool");

                first = allocator.heapBuffer(NOTE_ALLOCATING_SIZE, NOTE_ALLOCATING_SIZE);
                idle.assertNotesApplied("after another size class's slow path");
                // Two chunk buffers went to the recycler, and the new size class took one of them for its chunk.
                assertEquals(idle.recycledBefore + idle.chunkSize, idle.cache.chunkRecycler.retainedBytes());
                assertEquals(used, allocator.usedHeapMemory(), "the new chunk must be built from a recycled buffer");
            } finally {
                if (first != null) {
                    first.release();
                }
                idle.releaseRest();
            }
        });
    }

    /**
     * A size class that keeps allocating from its active chunk never takes its slow path, so the notes of an idle
     * size class of the same heap wait for its purge tick: exactly {@code chunkPurgeInterval} chunks' worth of its
     * allocations, not one fewer.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void purgeTickAppliesTheNotesOfAnIdleSizeClassAtItsInterval(final boolean threadLocal) throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, threadLocal);
        onHeapThread(threadLocal, () -> {
            // The allocating size class gets its active chunk first: from here on, allocating and releasing one
            // buffer at a time never runs it out of segments, so it never takes its slow path again.
            allocator.heapBuffer(NOTE_ALLOCATING_SIZE, NOTE_ALLOCATING_SIZE).release();
            IdleSizeClass idle = leaveNotes(allocator, !threadLocal);
            try {
                long used = allocator.usedHeapMemory();
                int interval = (int) AdaptivePoolingAllocator.CHUNK_PURGE_INTERVAL
                        * (AdaptivePoolingAllocator.chunkSizeOf(NOTE_ALLOCATING_SIZE) / NOTE_ALLOCATING_SIZE);

                // One allocation is counted already; the tick comes with the interval-th.
                for (int i = 1; i < interval - 1; i++) {
                    allocator.heapBuffer(NOTE_ALLOCATING_SIZE, NOTE_ALLOCATING_SIZE).release();
                }
                assertEquals(2, idle.cache.pendingCount(),
                        "one allocation before the tick, the notes must still wait");

                allocator.heapBuffer(NOTE_ALLOCATING_SIZE, NOTE_ALLOCATING_SIZE).release();
                idle.assertNotesApplied("after the purge tick of another size class");
                assertEquals(idle.recycledBefore + 2 * idle.chunkSize, idle.cache.chunkRecycler.retainedBytes(),
                        "both emptied chunks must go to the recycler");
                assertEquals(used, allocator.usedHeapMemory(), "recycled, not freed");
            } finally {
                idle.releaseRest();
            }
        });
    }

    /**
     * A heap's own purge ticks drive the decay of its recycler: chunk buffers its size classes gave up and nobody
     * took are freed, half of them per interval, once the interval and the allocations have passed.
     */
    @Test
    void purgeTicksDecayTheBuffersNobodyTakesFromTheRecycler() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onHeapThread(true, () -> {
            // A burst of 16 chunks, released by the owner: all but the one a size class keeps go to the recycler.
            int perChunk = AdaptivePoolingAllocator.chunkSizeOf(NOTE_ALLOCATING_SIZE) / NOTE_ALLOCATING_SIZE;
            List<ByteBuf> burst = new ArrayList<ByteBuf>();
            for (int i = 0; i < 16 * perChunk; i++) {
                burst.add(allocator.heapBuffer(NOTE_ALLOCATING_SIZE, NOTE_ALLOCATING_SIZE));
            }
            SizeClassChunkRecycler recycler = chunkOf(burst.get(0)).owningCache.chunkRecycler;
            IdleDecay idleDecay = threadLocalIdleDecay(allocator);
            for (ByteBuf buf : burst) {
                buf.release();
            }
            int sizeClass = AdaptivePoolingAllocator.sizeClassIndexOf(NOTE_ALLOCATING_SIZE);
            int pooled = recycler.size(sizeClass);
            assertTrue(pooled >= 8, pooled + " chunk buffers pooled");
            long used = allocator.usedHeapMemory();

            // Allocations served by the chunk still in use: they tick, and take nothing from the recycler.
            int allocations = (int) IdleDecay.DECAY_MIN_ALLOCATIONS + perChunk;
            idleDecay.lastDecayNanos -= 2 * IdleDecay.DECAY_INTERVAL_NANOS;
            for (int i = 0; i < allocations; i++) {
                allocator.heapBuffer(NOTE_ALLOCATING_SIZE, NOTE_ALLOCATING_SIZE).release();
            }
            assertEquals(pooled, recycler.size(sizeClass), "the first decay only finds out what sat idle");

            idleDecay.lastDecayNanos -= 2 * IdleDecay.DECAY_INTERVAL_NANOS;
            for (int i = 0; i < allocations; i++) {
                allocator.heapBuffer(NOTE_ALLOCATING_SIZE, NOTE_ALLOCATING_SIZE).release();
            }
            int freed = (pooled + 1) / 2;
            assertEquals(pooled - freed, recycler.size(sizeClass), "the second frees half, rounded up");
            assertEquals(used - (long) freed * AdaptivePoolingAllocator.chunkSizeOf(NOTE_ALLOCATING_SIZE),
                    allocator.usedHeapMemory());

            // Without the interval passing, more ticks free nothing more.
            for (int i = 0; i < allocations; i++) {
                allocator.heapBuffer(NOTE_ALLOCATING_SIZE, NOTE_ALLOCATING_SIZE).release();
            }
            assertEquals(pooled - freed, recycler.size(sizeClass));
        });
    }

    /** The {@link IdleDecay} of the calling thread's thread-local heap. */
    private static IdleDecay threadLocalIdleDecay(AdaptiveByteBufAllocator allocator) throws Exception {
        Field heapField = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        heapField.setAccessible(true);
        Object pooling = heapField.get(allocator);
        Field tlField = pooling.getClass().getDeclaredField("threadLocalSizeClassHeap");
        tlField.setAccessible(true);
        Object heap = ((io.netty.util.concurrent.FastThreadLocal<?>) tlField.get(pooling)).get();
        Field decayField = heap.getClass().getDeclaredField("idleDecay");
        decayField.setAccessible(true);
        return (IdleDecay) decayField.get(heap);
    }

    /** The {@link IdleDecay} of the one stripe that allocated buffers above the size classes. */
    private static IdleDecay buddyStripeIdleDecay(AdaptiveByteBufAllocator allocator) throws Exception {
        Field heapField = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        heapField.setAccessible(true);
        Object pooling = heapField.get(allocator);
        Field stripesField = pooling.getClass().getDeclaredField("stripedHeaps");
        stripesField.setAccessible(true);
        IdleDecay found = null;
        for (Object stripe : (Object[]) stripesField.get(pooling)) {
            Field magField = stripe.getClass().getDeclaredField("buddyMagazine");
            magField.setAccessible(true);
            if (magField.get(stripe) != null) {
                assertNull(found, "one stripe only");
                Field decayField = stripe.getClass().getDeclaredField("idleDecay");
                decayField.setAccessible(true);
                found = (IdleDecay) decayField.get(stripe);
            }
        }
        assertNotNull(found, "no stripe allocated above the size classes");
        return found;
    }

    /**
     * Chunks for buffers above the size classes that stay wholly free are given back like the recycler's buffers:
     * half, rounded up, of those idle through a whole interval per decay. A chunk that became idle during the
     * interval is not among them, and the chunk the magazine allocates from only once no buffer in it is out.
     */
    @Test
    void idleBuddyChunksAreGivenBackHalfPerInterval() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        assumeTrue(AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE >= 3, "keeps fewer than three idle chunks");
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        // Four chunks' worth: chunks 1 to 3 filled, chunk 4 the one the magazine allocates from.
        List<ByteBuf> held = new ArrayList<ByteBuf>();
        held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        long chunk = allocator.usedHeapMemory();
        if (AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES < 3 * chunk) {
            held.get(0).release();
            assumeTrue(false, "keeps fewer than three idle chunks' bytes");
        }
        int perChunk = (int) (chunk / BUDDY_NOTE_SIZE);
        while (held.size() < 4 * perChunk) {
            held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        }
        try {
            IdleDecay idleDecay = buddyStripeIdleDecay(allocator);
            long t = System.nanoTime();
            releaseChunk(held, 0, perChunk);
            releaseChunk(held, 1, perChunk);
            assertEquals(4 * chunk, allocator.usedHeapMemory(), "chunks 1 and 2 idle, kept");

            idleDecay.decay(t += IdleDecay.DECAY_INTERVAL_NANOS);
            assertEquals(4 * chunk, allocator.usedHeapMemory(), "idle for part of the interval only: none is cold");

            releaseChunk(held, 2, perChunk);
            idleDecay.decay(t += IdleDecay.DECAY_INTERVAL_NANOS);
            assertEquals(3 * chunk, allocator.usedHeapMemory(),
                    "chunks 1 and 2 were idle through the interval, chunk 3 only since its middle: one of two freed");

            idleDecay.decay(t += IdleDecay.DECAY_INTERVAL_NANOS);
            assertEquals(2 * chunk, allocator.usedHeapMemory(), "now chunk 3 counts too: one of two freed");
            idleDecay.decay(t += IdleDecay.DECAY_INTERVAL_NANOS);
            assertEquals(chunk, allocator.usedHeapMemory(), "the last idle one");

            // Chunk 4 is the one the magazine allocates from: it stays while a buffer in it is out, and once wholly
            // free it is filed with the idle chunks at a decay and given back at the next, like any other.
            idleDecay.decay(t += IdleDecay.DECAY_INTERVAL_NANOS);
            assertEquals(chunk, allocator.usedHeapMemory(), "the active chunk has buffers out: it stays");
            releaseChunk(held, 3, perChunk);
            idleDecay.decay(t += IdleDecay.DECAY_INTERVAL_NANOS);
            assertEquals(chunk, allocator.usedHeapMemory(), "wholly free since this decay only");
            idleDecay.decay(t + IdleDecay.DECAY_INTERVAL_NANOS);
            assertEquals(0, allocator.usedHeapMemory(), "wholly free through a whole interval: given back");
        } finally {
            for (ByteBuf buf : held) {
                if (buf != null) {
                    buf.release();
                }
            }
        }
    }

    /**
     * End to end: the slow path of an allocation above the size classes that completes the count, after the interval,
     * gives back the stripe's idle large-buffer chunks.
     */
    @Test
    void buddySlowPathDrivesTheDecayOfIdleBuddyChunks() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        assumeTrue(AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE >= 2, "keeps fewer than two idle chunks");
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        List<ByteBuf> held = new ArrayList<ByteBuf>();
        held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        long chunk = allocator.usedHeapMemory();
        if (AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES < 2 * chunk) {
            held.get(0).release();
            assumeTrue(false, "keeps fewer than two idle chunks' bytes");
        }
        int perChunk = (int) (chunk / BUDDY_NOTE_SIZE);
        while (held.size() < 3 * perChunk) {
            held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        }
        try {
            // Chunks 1 and 2 idle, chunk 3 active and full; one decay has passed since they became idle.
            releaseChunk(held, 0, perChunk);
            releaseChunk(held, 1, perChunk);
            IdleDecay idleDecay = buddyStripeIdleDecay(allocator);
            idleDecay.decay(System.nanoTime() - 2 * IdleDecay.DECAY_INTERVAL_NANOS);
            assertEquals(3 * chunk, allocator.usedHeapMemory());

            // The next allocation finds chunk 3 full: its slow path completes the count and the decay runs. It
            // takes one of the two idle chunks as its new active chunk first, so one cold chunk is left, and freed.
            idleDecay.allocationsSinceCheck = IdleDecay.DECAY_MIN_ALLOCATIONS - 1;
            held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            assertEquals(0, idleDecay.allocationsSinceCheck, "the count completed on that slow path");
            assertEquals(2 * chunk, allocator.usedHeapMemory(), "the other idle chunk is given back");
        } finally {
            for (ByteBuf buf : held) {
                if (buf != null) {
                    buf.release();
                }
            }
        }
    }

    /**
     * Release the buffers of one chunk and forget them: a released buffer's wrapper goes back to a pool and serves
     * the next allocation, so it must not be looked at, or released, through the old reference again.
     */
    private static void releaseChunk(List<ByteBuf> held, int chunk, int perChunk) {
        for (int i = chunk * perChunk; i < (chunk + 1) * perChunk; i++) {
            held.get(i).release();
            held.set(i, null);
        }
    }

    /**
     * Allocations above the size classes feed the stripe's {@link IdleDecay} on the fast path as on the slow one, each
     * with its block size in units of the smallest size class: a 160 KiB buffer takes a 256 KiB block, 8192 units, so
     * the second one completes a count.
     */
    @Test
    void buddyAllocationsCountTheirBlockSizeTowardTheIdleDecay() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 160 KiB buffers");
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        int size = 160 * 1024;
        List<ByteBuf> held = new ArrayList<ByteBuf>();
        try {
            // The first allocation opens a chunk on the slow path.
            held.add(allocator.heapBuffer(size, size));
            IdleDecay idleDecay = buddyStripeIdleDecay(allocator);
            assertEquals(256 * 1024 / 32, idleDecay.allocationsSinceCheck, "the slow path counts the block");
            // The second one comes from the same chunk, on the fast path: it counts too, and completes the count.
            held.add(allocator.heapBuffer(size, size));
            assertEquals(0, idleDecay.allocationsSinceCheck, "the fast path counted and completed the count");
        } finally {
            for (ByteBuf buf : held) {
                buf.release();
            }
        }
    }

    /**
     * A stripe serving a trickle of large buffers from the chunk it allocates from, with no slow path at all, still
     * gives its idle large-buffer chunks back by halves: the fast path counts.
     */
    @Test
    void largeBufferTrickleAgesTheStripesIdleChunks() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        assumeTrue(AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE >= 2, "keeps fewer than two idle chunks");
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        List<ByteBuf> held = new ArrayList<ByteBuf>();
        held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        long chunk = allocator.usedHeapMemory();
        if (AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES < 2 * chunk) {
            held.get(0).release();
            assumeTrue(false, "keeps fewer than two idle chunks' bytes");
        }
        int perChunk = (int) (chunk / BUDDY_NOTE_SIZE);
        // Three chunks' worth: chunks 1 and 2 become idle, chunk 3 is the one the stripe allocates from.
        while (held.size() < 3 * perChunk) {
            held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        }
        for (ByteBuf buf : held) {
            buf.release();
        }
        IdleDecay idleDecay = buddyStripeIdleDecay(allocator);
        assertEquals(2, buddyIdleChunks(idleDecay));
        long used = allocator.usedHeapMemory();
        // Each round: the interval has passed, and one large buffer comes from chunk 3, on the fast path.
        idleDecay.lastDecayNanos -= 2 * IdleDecay.DECAY_INTERVAL_NANOS;
        allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE).release();
        assertEquals(used, allocator.usedHeapMemory(), "idle since before the first decay: not a whole interval");
        idleDecay.lastDecayNanos -= 2 * IdleDecay.DECAY_INTERVAL_NANOS;
        allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE).release();
        assertEquals(1, buddyIdleChunks(idleDecay), "half of two");
        assertEquals(used - chunk, allocator.usedHeapMemory());
    }

    private static final int BUDDY_NOTE_SIZE = 512 * 1024;

    /** Buddy buffers held by a test, and how many of them a chunk holds. */
    private static final class BuddyHeld {
        final List<ByteBuf> held = new ArrayList<ByteBuf>();
        int perChunk;

        void releaseAll() {
            for (ByteBuf buf : held) {
                buf.release();
            }
        }
    }

    /**
     * Fill {@code fullChunks} buddy chunks and open one more, then have another thread release one block of the
     * {@code chunk}-th of the full ones while the stripe lock is held, so that it leaves a note.
     */
    private static BuddyHeld buddyReleaseThatLeavesANote(AdaptiveByteBufAllocator allocator, int fullChunks,
                                                         int chunk) throws Exception {
        BuddyHeld b = new BuddyHeld();
        b.held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        b.perChunk = (int) (allocator.usedHeapMemory() / BUDDY_NOTE_SIZE);
        assumeTrue(b.perChunk >= 2, "a buddy chunk holds " + b.perChunk + " buffers");
        // fullChunks full, and one more block that opens the chunk the magazine allocates from next.
        while (b.held.size() < fullChunks * b.perChunk + 1) {
            b.held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        }
        final ByteBuf victim = b.held.remove(chunk * b.perChunk);
        underStripeLocks(allocator, true, () -> {
            try {
                release(victim, true);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
        return b;
    }

    /** Allocate until the chunk the magazine allocates from is full, then once more: that one takes the slow path. */
    private static void allocateThroughTheNextSlowPath(AdaptiveByteBufAllocator allocator, BuddyHeld b,
                                                       PendingChunks pending, int notesBefore) {
        long used = allocator.usedHeapMemory();
        for (int i = 1; i < b.perChunk; i++) {
            b.held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        }
        assertEquals(notesBefore, pending.size(), "no slow path yet, the notes wait");
        b.held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
        assertEquals(0, pending.size(), "the slow path must apply the notes");
        assertEquals(used, allocator.usedHeapMemory(), "the released block must be reused, not a new chunk allocated");
    }

    /**
     * A buddy block released by a thread that could not take the stripe lock is found through its note by the
     * magazine's next slow path, even in a chunk beyond the reach of the probe of the full chunks.
     */
    @Test
    void buddyReleaseNoteIsAppliedByTheNextSlowPath() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        // The probe looks at the newest full chunks only: the oldest of this many is out of its reach.
        int fullChunks = maxFullProbe() + 2;
        BuddyHeld b = buddyReleaseThatLeavesANote(allocator, fullChunks, 0);
        try {
            PendingChunks pending = buddyPending(allocator);
            assertEquals(1, pending.size(), "the release must leave a note");
            allocateThroughTheNextSlowPath(allocator, b, pending, 1);
        } finally {
            b.releaseAll();
        }
    }

    /**
     * A buddy block whose note is not there yet - the releaser offered it and has not pushed the note, or pushed it
     * after the drain - is found by the bounded probe of the full chunks.
     */
    @Test
    void buddyBlockWhoseNoteIsStillInFlightIsFoundByTheProbe() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        int fullChunks = 2;
        // The newest full chunk, which the probe reaches first.
        BuddyHeld b = buddyReleaseThatLeavesANote(allocator, fullChunks, fullChunks - 1);
        try {
            PendingChunks pending = buddyPending(allocator);
            assertEquals(1, pending.size(), "the release must leave a note");
            // Take the note away, as a drain that ran before the releaser pushed it would never have seen it.
            for (AdaptivePoolingAllocator.Chunk c = pending.takeAll(); c != null; c = PendingChunks.rearm(c)) {
                // Only unlink.
            }
            allocateThroughTheNextSlowPath(allocator, b, pending, 0);
        } finally {
            b.releaseAll();
        }
    }

    /** Runs {@code body} on a new thread whose heap is thread-local, freed when the body returns. */
    private static void onThreadLocalHeap(final ThrowingRunnable body) throws Throwable {
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread thread = new Thread(() -> FastThreadLocalThread.runWithFastThreadLocal(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        }));
        thread.start();
        thread.join();
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    /** How many stripes created a magazine for buffers above the size classes. */
    private static int stripesWithABuddyMagazine(AdaptiveByteBufAllocator allocator) throws Exception {
        Field heapField = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        heapField.setAccessible(true);
        Object pooling = heapField.get(allocator);
        Field stripesField = pooling.getClass().getDeclaredField("stripedHeaps");
        stripesField.setAccessible(true);
        int stripes = 0;
        for (Object stripe : (Object[]) stripesField.get(pooling)) {
            Field magField = stripe.getClass().getDeclaredField("buddyMagazine");
            magField.setAccessible(true);
            if (magField.get(stripe) != null) {
                stripes++;
            }
        }
        return stripes;
    }

    /** The wholly free chunks the buddy magazine of {@code idleDecay}'s heap keeps. */
    private static int buddyIdleChunks(IdleDecay idleDecay) throws Exception {
        Object magazine = idleDecay.buddyMagazine;
        assertNotNull(magazine, "no magazine for buffers above the size classes");
        Method idleChunks = magazine.getClass().getDeclaredMethod("idleChunks");
        idleChunks.setAccessible(true);
        return (Integer) idleChunks.invoke(magazine);
    }

    /** Small allocations on the calling thread's heap, enough to complete one count of its {@link IdleDecay}. */
    private static void sizeClassTraffic(AdaptiveByteBufAllocator allocator) {
        for (int i = 0; i < 4 * IdleDecay.DECAY_MIN_ALLOCATIONS; i++) {
            allocator.heapBuffer(256).release();
        }
    }

    /**
     * The chunk a large-buffer magazine allocates from is only filed with the idle ones while they have room: filing it
     * over the bound would free it at once, before it stayed idle through any interval.
     */
    @Test
    void activeLargeChunkWaitsForRoomAmongTheIdleOnes() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 1 MiB buffers");
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        final int size = 1024 * 1024;
        List<ByteBuf> held = new ArrayList<ByteBuf>();
        held.add(allocator.heapBuffer(size, size));
        long chunk = allocator.usedHeapMemory();
        int perChunk = (int) (chunk / size);
        int idle = (int) Math.min(AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE,
                AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES / chunk);
        while (held.size() < (idle + 1) * perChunk) {
            held.add(allocator.heapBuffer(size, size));
        }
        for (int c = 0; c < idle; c++) {
            releaseChunk(held, c, perChunk);
        }
        IdleDecay idleDecay = buddyStripeIdleDecay(allocator);
        assertEquals(idle, buddyIdleChunks(idleDecay), "the idle bound is full");
        releaseChunk(held, idle, perChunk);
        long used = allocator.usedHeapMemory();
        idleDecay.decay(System.nanoTime());
        assertEquals(used, allocator.usedHeapMemory(), "no room: the active chunk stays");
        idleDecay.decay(System.nanoTime());
        assertTrue(allocator.usedHeapMemory() < used, "the idle ones are given back by halves");
        assertTrue(buddyIdleChunks(idleDecay) <= idle);
    }

    /** Buffers per chunk for buffers above the size classes: the chunk is sized for about this many. */
    private static final int BUFS_PER_LARGE_CHUNK = 8;

    /** The calling thread's thread-local heap. */
    private static Object threadLocalHeap(AdaptiveByteBufAllocator allocator) throws Exception {
        Field heapField = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        heapField.setAccessible(true);
        Object pooling = heapField.get(allocator);
        Field tlField = pooling.getClass().getDeclaredField("threadLocalSizeClassHeap");
        tlField.setAccessible(true);
        return ((io.netty.util.concurrent.FastThreadLocal<?>) tlField.get(pooling)).get();
    }

    /** The current chunk of the calling thread's size-class magazine for {@code size}, or null. */
    private static Object currentChunk(AdaptiveByteBufAllocator allocator, int size) throws Exception {
        Object heap = threadLocalHeap(allocator);
        Field magsField = heap.getClass().getDeclaredField("magazines");
        magsField.setAccessible(true);
        Object mag = ((Object[]) magsField.get(heap))[AdaptivePoolingAllocator.sizeClassIndexOf(size)];
        Field currentField = mag.getClass().getDeclaredField("current");
        currentField.setAccessible(true);
        return currentField.get(mag);
    }

    /**
     * A size class that made no allocation through a whole decay interval gives up its chunks, the one it keeps as
     * its floor included, to the heap's recycler, which gives them back by halves; a class still allocating keeps its
     * own.
     */
    @Test
    void sizeClassIdleForAWholeIntervalGivesUpItsChunks() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            final int idleSize = 64 * 1024;
            final int busySize = 256;
            allocator.heapBuffer(idleSize, idleSize).release();
            long idleChunk = allocator.usedHeapMemory();
            allocator.heapBuffer(busySize).release();
            IdleDecay idleDecay = threadLocalIdleDecay(allocator);
            SizeClassChunkRecycler recycler = idleDecay.recycler;
            long used = allocator.usedHeapMemory();
            assertNotNull(currentChunk(allocator, idleSize));

            // The first decay only records where each class stands.
            idleDecay.decay(System.nanoTime());
            assertNotNull(currentChunk(allocator, idleSize), "allocated since the heap was created: not idle");
            allocator.heapBuffer(busySize).release();

            // Idle through a whole interval: its chunk goes to the recycler, still counted as used.
            idleDecay.decay(System.nanoTime());
            assertNull(currentChunk(allocator, idleSize), "the idle class gave its chunk up");
            assertNotNull(currentChunk(allocator, busySize), "the class in use keeps its chunk");
            assertEquals(idleChunk, recycler.retainedBytes());
            assertEquals(used, allocator.usedHeapMemory());

            // Then the recycler gives it back at the next decay, once it stayed there through a whole interval.
            allocator.heapBuffer(busySize).release();
            idleDecay.decay(System.nanoTime());
            assertEquals(0, recycler.retainedBytes());
            assertEquals(used - idleChunk, allocator.usedHeapMemory());
        });
    }

    /**
     * An idle size class whose chunks the recycler has no room for keeps them until the recycler's halving makes room:
     * idle memory only reaches the chunk allocator by halves.
     */
    @Test
    void idleSizeClassWaitsForRoomInTheRecycler() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            final int idleSize = 64 * 1024;
            final int idleClass = AdaptivePoolingAllocator.sizeClassIndexOf(idleSize);
            allocator.heapBuffer(idleSize, idleSize).release();
            // Fill the recycler past its budget with the chunks of another class.
            int perChunk = AdaptivePoolingAllocator.chunkSizeOf(256) / 256;
            int chunks = SizeClassChunkRecycler.RECYCLED_BYTES_BUDGET / AdaptivePoolingAllocator.chunkSizeOf(256) + 4;
            List<ByteBuf> burst = new ArrayList<ByteBuf>();
            for (int i = 0; i < chunks * perChunk; i++) {
                burst.add(allocator.heapBuffer(256, 256));
            }
            for (ByteBuf b : burst) {
                b.release();
            }
            IdleDecay idleDecay = threadLocalIdleDecay(allocator);
            SizeClassChunkRecycler recycler = idleDecay.recycler;
            idleDecay.decay(System.nanoTime());
            allocator.heapBuffer(256).release();
            long used = allocator.usedHeapMemory();
            long retained = recycler.retainedBytes();

            idleDecay.decay(System.nanoTime());
            assertNull(currentChunk(allocator, idleSize), "the idle class gave its chunk up");
            assertEquals(0, recycler.size(idleClass), "no room: kept by the class");
            assertEquals(retained - recycler.retainedBytes(), used - allocator.usedHeapMemory(),
                    "only the recycler's halving freed memory");

            allocator.heapBuffer(256).release();
            idleDecay.decay(System.nanoTime());
            assertEquals(1, recycler.size(idleClass), "offered once the halving made room");
        });
    }

    /** A size class that allocated exactly one purge tick's worth since the previous decay is not idle. */
    @Test
    void sizeClassWithOneTickBetweenDecaysIsNotIdle() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            final int size = 64 * 1024;
            allocator.heapBuffer(size, size).release();
            IdleDecay idleDecay = threadLocalIdleDecay(allocator);
            idleDecay.decay(System.nanoTime());
            int threshold = (int) AdaptivePoolingAllocator.CHUNK_PURGE_INTERVAL
                    * (AdaptivePoolingAllocator.chunkSizeOf(size) / size);
            for (int i = 0; i < threshold; i++) {
                allocator.heapBuffer(size, size).release();
            }
            Object current = currentChunk(allocator, size);
            assertNotNull(current);
            idleDecay.decay(System.nanoTime());
            assertSame(current, currentChunk(allocator, size), "its allocation count came back to where it was");
        });
    }

    /** On a stripe too, a size class idle through a whole interval gives up its chunk while another one allocates. */
    @Test
    void sizeClassIdleOnAStripeGivesUpItsChunks() throws Throwable {
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        onHeapThread(false, () -> {
            final int idleSize = 16 * 1024;
            allocator.heapBuffer(idleSize, idleSize).release();
            long idleChunk = allocator.usedHeapMemory();
            allocator.heapBuffer(256).release();
            Object stripe = stripeHeapWithMagazines(allocator);
            Field decayField = stripe.getClass().getDeclaredField("idleDecay");
            decayField.setAccessible(true);
            IdleDecay idleDecay = (IdleDecay) decayField.get(stripe);
            Field lockField = stripe.getClass().getDeclaredField("lock");
            lockField.setAccessible(true);
            StampedLock lock = (StampedLock) lockField.get(stripe);
            for (int round = 0; round < 2; round++) {
                long stamp = lock.writeLock();
                try {
                    idleDecay.decay(System.nanoTime());
                } finally {
                    lock.unlockWrite(stamp);
                }
                allocator.heapBuffer(256).release();
            }
            assertNull(magazineCurrent(stripe, idleSize), "the idle class gave its chunk up");
            assertNotNull(magazineCurrent(stripe, 256), "the class in use keeps its chunk");
            assertEquals(idleChunk, idleDecay.recycler.retainedBytes());
        });
    }

    /** A current chunk emptied by another thread's release, still only noted, is given up like any other. */
    @Test
    void idleSizeClassGivesUpAChunkAnotherThreadEmptied() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            final int idleSize = 64 * 1024;
            ByteBuf buf = allocator.heapBuffer(idleSize, idleSize);
            long idleChunk = allocator.usedHeapMemory();
            SizeClassedChunk chunk = chunkOf(buf);
            release(buf, true);
            assertEquals(1, chunk.owningCache.pendingCount(), "the other thread's release left a note");
            allocator.heapBuffer(256).release();
            IdleDecay idleDecay = threadLocalIdleDecay(allocator);
            idleDecay.decay(System.nanoTime());
            allocator.heapBuffer(256).release();
            idleDecay.decay(System.nanoTime());
            assertNull(currentChunk(allocator, idleSize));
            assertEquals(idleChunk, idleDecay.recycler.retainedBytes());
            assertEquals(0, chunk.owningCache.pendingCount());
        });
    }

    /** The one stripe that created size-class magazines. */
    private static Object stripeHeapWithMagazines(AdaptiveByteBufAllocator allocator) throws Exception {
        Field heapField = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        heapField.setAccessible(true);
        Object pooling = heapField.get(allocator);
        Field stripesField = pooling.getClass().getDeclaredField("stripedHeaps");
        stripesField.setAccessible(true);
        Object found = null;
        for (Object stripe : (Object[]) stripesField.get(pooling)) {
            Field magsField = stripe.getClass().getDeclaredField("magazines");
            magsField.setAccessible(true);
            if (magsField.get(stripe) != null) {
                assertNull(found, "one stripe only");
                found = stripe;
            }
        }
        assertNotNull(found);
        return found;
    }

    /** The current chunk of {@code heap}'s size-class magazine for {@code size}. */
    private static Object magazineCurrent(Object heap, int size) throws Exception {
        Field magsField = heap.getClass().getDeclaredField("magazines");
        magsField.setAccessible(true);
        Object mag = ((Object[]) magsField.get(heap))[AdaptivePoolingAllocator.sizeClassIndexOf(size)];
        Field currentField = mag.getClass().getDeclaredField("current");
        currentField.setAccessible(true);
        return currentField.get(mag);
    }

    /** A chunk with a buffer out is never given up, however long its class stays idle. */
    @Test
    void idleSizeClassKeepsAChunkWithABufferOut() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            final int size = 64 * 1024;
            ByteBuf out = allocator.heapBuffer(size, size);
            try {
                IdleDecay idleDecay = threadLocalIdleDecay(allocator);
                long used = allocator.usedHeapMemory();
                Object chunk = currentChunk(allocator, size);
                for (int i = 0; i < 4; i++) {
                    idleDecay.decay(System.nanoTime());
                }
                assertSame(chunk, currentChunk(allocator, size));
                assertEquals(used, allocator.usedHeapMemory());
            } finally {
                out.release();
            }
        });
    }

    /**
     * The chunk a large-buffer magazine allocates from is given back like the others once nothing in it is in use
     * through a whole interval; one with a buffer out stays.
     */
    @Test
    void activeLargeChunkIdleForAWholeIntervalIsGivenBack() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        ByteBuf out = allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE);
        long chunk = allocator.usedHeapMemory();
        IdleDecay idleDecay = buddyStripeIdleDecay(allocator);
        idleDecay.decay(System.nanoTime());
        idleDecay.decay(System.nanoTime());
        assertEquals(chunk, allocator.usedHeapMemory(), "a buffer is out: the chunk stays");
        out.release();

        idleDecay.decay(System.nanoTime());
        assertEquals(1, buddyIdleChunks(idleDecay), "no longer in use: filed with the idle chunks");
        assertEquals(chunk, allocator.usedHeapMemory(), "idle since this decay only");
        idleDecay.decay(System.nanoTime());
        assertEquals(0, allocator.usedHeapMemory(), "idle through a whole interval: given back");

        // The next large allocation opens a new chunk.
        allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE).release();
        assertEquals(chunk, allocator.usedHeapMemory());
    }

    /**
     * A thread with its own heap takes buffers above the size classes from that heap too: no stripe, no lock.
     */
    @Test
    void threadLocalHeapServesBuffersAboveTheSizeClasses() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            ByteBuf buf = allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE);
            try {
                assertNotNull(threadLocalIdleDecay(allocator).buddyMagazine, "the heap's own magazine");
                assertEquals(0, stripesWithABuddyMagazine(allocator), "no stripe involved");
            } finally {
                buf.release();
            }
        });
    }

    /**
     * The point of a thread-local heap's own magazine for large buffers: its idle chunks age with the heap's other
     * allocations. Only small buffers are allocated after the burst, and they give the idle chunks back by halves.
     */
    @Test
    void idleLargeChunksOfAThreadLocalHeapAgeWithItsSmallAllocations() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        assumeTrue(AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE >= 2, "keeps fewer than two idle chunks");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            List<ByteBuf> held = new ArrayList<ByteBuf>();
            held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            long chunk = allocator.usedHeapMemory();
            if (AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES < 2 * chunk) {
                held.get(0).release();
                assumeTrue(false, "keeps fewer than two idle chunks' bytes");
            }
            int perChunk = (int) (chunk / BUDDY_NOTE_SIZE);
            // Three chunks' worth: chunks 1 and 2 become wholly free; chunk 3, the one the magazine allocates from,
            // keeps one buffer out, so it stays the magazine's.
            while (held.size() < 3 * perChunk) {
                held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            }
            ByteBuf out = held.remove(held.size() - 1);
            for (ByteBuf buf : held) {
                buf.release();
            }
            held.clear();
            IdleDecay idleDecay = threadLocalIdleDecay(allocator);
            assertEquals(2, buddyIdleChunks(idleDecay));

            // Each round: the interval has passed, and only small buffers are allocated.
            idleDecay.lastDecayNanos -= 2 * IdleDecay.DECAY_INTERVAL_NANOS;
            sizeClassTraffic(allocator);
            assertEquals(2, buddyIdleChunks(idleDecay), "idle since before the first decay: not a whole interval");
            long used = allocator.usedHeapMemory();

            idleDecay.lastDecayNanos -= 2 * IdleDecay.DECAY_INTERVAL_NANOS;
            sizeClassTraffic(allocator);
            assertEquals(1, buddyIdleChunks(idleDecay), "half of two");
            assertEquals(used - chunk, allocator.usedHeapMemory());

            idleDecay.lastDecayNanos -= 2 * IdleDecay.DECAY_INTERVAL_NANOS;
            sizeClassTraffic(allocator);
            assertEquals(0, buddyIdleChunks(idleDecay), "half of one, rounded up");
            assertEquals(used - 2 * chunk, allocator.usedHeapMemory());
            out.release();
        });
    }

    /**
     * Another thread cannot touch a thread-local heap's magazine: its release leaves a note. The decay applies the
     * notes first, so a chunk those releases emptied starts aging at once instead of waiting for a large allocation.
     */
    @Test
    void largeChunkEmptiedByAnotherThreadIsNotedAndAgesToo() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            final List<ByteBuf> held = new ArrayList<ByteBuf>();
            held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            int perChunk = (int) (allocator.usedHeapMemory() / BUDDY_NOTE_SIZE);
            // Chunk 1 full, chunk 2 the one the magazine allocates from.
            while (held.size() < perChunk + 1) {
                held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            }
            Thread releaser = new Thread(() -> {
                for (int i = 0; i < perChunk; i++) {
                    held.get(i).release();
                }
            });
            releaser.start();
            releaser.join();
            IdleDecay idleDecay = threadLocalIdleDecay(allocator);
            assertEquals(0, buddyIdleChunks(idleDecay), "only noted, not filed yet");
            // The decay itself, with no allocation before it that could apply the note first.
            idleDecay.decay(System.nanoTime());
            assertEquals(1, buddyIdleChunks(idleDecay), "the decay applied the note");
            held.get(perChunk).release();
        });
    }

    /**
     * A thread-local heap that dies gives its large chunks back; one with a buffer still out goes when that buffer
     * is released, from whatever thread.
     */
    @Test
    void largeChunksOfADeadThreadLocalHeapAreFreed() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        final AtomicReference<ByteBuf> survivor = new AtomicReference<ByteBuf>();
        onThreadLocalHeap(() -> {
            List<ByteBuf> held = new ArrayList<ByteBuf>();
            held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            int perChunk = (int) (allocator.usedHeapMemory() / BUDDY_NOTE_SIZE);
            while (held.size() < 2 * perChunk + 1) {
                held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            }
            survivor.set(held.remove(held.size() - 1));
            for (ByteBuf buf : held) {
                buf.release();
            }
        });
        assertEquals(survivor.get().capacity() * (long) BUFS_PER_LARGE_CHUNK, allocator.usedHeapMemory(),
                "only the chunk of the buffer still out");
        survivor.get().release();
        assertEquals(0, allocator.usedHeapMemory(), "the last buffer freed its chunk");
    }

    /**
     * On a thread-local heap every other thread's release is a note, so the idle bound only holds once the notes are
     * applied: the heap's next size-class slow path applies them too, without waiting for a decay or a large
     * allocation.
     */
    @Test
    void idleBoundHoldsForLargeChunksAnotherThreadEmptied() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            final List<ByteBuf> held = new ArrayList<ByteBuf>();
            held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            long chunk = allocator.usedHeapMemory();
            int perChunk = (int) (chunk / BUDDY_NOTE_SIZE);
            long bound = Math.min(AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE * chunk,
                    AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES / chunk * chunk);
            // Four chunks beyond the bound, plus the one the magazine allocates from.
            int chunks = (int) (bound / chunk) + 5;
            while (held.size() < chunks * perChunk) {
                held.add(allocator.heapBuffer(BUDDY_NOTE_SIZE, BUDDY_NOTE_SIZE));
            }
            ByteBuf last = held.remove(held.size() - 1);
            try {
                Thread releaser = new Thread(() -> {
                    for (ByteBuf buf : held) {
                        buf.release();
                    }
                });
                releaser.start();
                releaser.join();
                IdleDecay idleDecay = threadLocalIdleDecay(allocator);
                assertEquals(0, buddyIdleChunks(idleDecay), "only noted so far");
                assertEquals(chunks * chunk, allocator.usedHeapMemory());

                // Two chunks' worth of small buffers: the size class goes to its slow path, which applies the notes.
                List<ByteBuf> small = new ArrayList<ByteBuf>();
                for (int i = 0; i < 2 * 1024; i++) {
                    small.add(allocator.heapBuffer(256));
                }
                for (ByteBuf buf : small) {
                    buf.release();
                }
                assertEquals(bound / chunk, buddyIdleChunks(idleDecay), "idle large chunks kept to the bound");
                // The rest was freed: the bound, the active chunk, and what the small buffers took (under 2 MiB).
                long used = allocator.usedHeapMemory();
                assertTrue(used <= bound + chunk + 2 * 1024 * 1024, "used " + used + ", bound " + bound);
            } finally {
                last.release();
            }
        });
    }

    /**
     * A buffer that grows from a size class into the sizes above them on a thread-local heap moves to the heap's own
     * magazine, keeping its content, without a stripe.
     */
    @Test
    void reallocationIntoTheLargeSizesStaysOnTheThreadLocalHeap() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode does not pool 512 KiB buffers");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        onThreadLocalHeap(() -> {
            ByteBuf buf = allocator.heapBuffer(64 * 1024);
            try {
                for (int i = 0; i < 64 * 1024; i++) {
                    buf.writeByte(i);
                }
                buf.capacity(BUDDY_NOTE_SIZE);
                assertEquals(BUDDY_NOTE_SIZE, buf.capacity());
                for (int i = 0; i < 64 * 1024; i++) {
                    assertEquals((byte) i, buf.getByte(i));
                }
                assertNotNull(threadLocalIdleDecay(allocator).buddyMagazine, "the heap's own magazine");
                assertEquals(0, stripesWithABuddyMagazine(allocator), "no stripe involved");
            } finally {
                buf.release();
            }
        });
    }

    private static boolean isLowMemory() throws Exception {
        Field f = AdaptivePoolingAllocator.class.getDeclaredField("IS_LOW_MEM");
        f.setAccessible(true);
        return f.getBoolean(null);
    }

    /** How many full chunks the buddy magazine's last-resort probe looks at. */
    private static int maxFullProbe() throws Exception {
        for (Class<?> c : AdaptivePoolingAllocator.class.getDeclaredClasses()) {
            if ("BuddyMagazine".equals(c.getSimpleName())) {
                Field f = c.getDeclaredField("MAX_FULL_PROBE");
                f.setAccessible(true);
                return f.getInt(null);
            }
        }
        throw new AssertionError("no BuddyMagazine");
    }

    /** The notes of the buddy magazine of the one stripe this test's thread allocated on. */
    private static PendingChunks buddyPending(AdaptiveByteBufAllocator allocator) throws Exception {
        Field heapField = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        heapField.setAccessible(true);
        Object pooling = heapField.get(allocator);
        Field stripesField = pooling.getClass().getDeclaredField("stripedHeaps");
        stripesField.setAccessible(true);
        PendingChunks found = null;
        for (Object stripe : (Object[]) stripesField.get(pooling)) {
            Field magField = stripe.getClass().getDeclaredField("buddyMagazine");
            magField.setAccessible(true);
            Object magazine = magField.get(stripe);
            if (magazine != null) {
                assertNull(found, "one stripe only");
                Field pendingField = magazine.getClass().getDeclaredField("pending");
                pendingField.setAccessible(true);
                found = (PendingChunks) pendingField.get(magazine);
            }
        }
        assertNotNull(found, "no buddy magazine");
        return found;
    }

    /** Runs a cache operation the way the allocator does: under the stripe lock when the cache is a stripe's. */
    private static void underStripeLocks(AdaptiveByteBufAllocator allocator, boolean sharedStripe, Runnable action)
            throws Exception {
        List<StampedLock> locks = sharedStripe ? stripeLocks(allocator) : Collections.<StampedLock>emptyList();
        List<Long> stamps = new ArrayList<Long>();
        for (StampedLock l : locks) {
            stamps.add(l.writeLock());
        }
        try {
            action.run();
        } finally {
            for (int i = 0; i < locks.size(); i++) {
                locks.get(i).unlockWrite(stamps.get(i));
            }
        }
    }

    /** The chunk a buffer was carved from, whatever its kind. */
    private static Object anyChunkOf(ByteBuf buf) {
        while (!(buf instanceof AdaptivePoolingAllocator.AdaptiveByteBuf)) {
            buf = buf.unwrap();
        }
        return ((AdaptivePoolingAllocator.AdaptiveByteBuf) buf).chunk;
    }

    private static SizeClassedChunk chunkOf(ByteBuf buf) {
        // Unwrap the leak-aware wrapper, if any.
        while (!(buf instanceof AdaptivePoolingAllocator.AdaptiveByteBuf)) {
            buf = buf.unwrap();
        }
        return (SizeClassedChunk) ((AdaptivePoolingAllocator.AdaptiveByteBuf) buf).chunk;
    }

    private static void release(ByteBuf buf, boolean foreignThread) throws InterruptedException {
        if (!foreignThread) {
            buf.release();
            return;
        }
        Thread t = new Thread(buf::release);
        t.start();
        t.join();
    }

    // Regression: on the shared (striped) path a segment returned after the allocator was
    // freed was absorbed into the chunk's local free list by the lock-holding release path,
    // which skipped the deallocation accounting entirely -- so the chunk never deallocated.
    @Test
    void segmentReturnedAfterFreeMustStillDeallocateChunk() throws Exception {
        // useCacheForNonEventLoopThreads=false -> a plain thread takes the shared path
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        ByteBuf buf = allocator.heapBuffer(256);
        assertTrue(allocator.usedHeapMemory() > 0);

        freeHeap(allocator);

        // last outstanding segment comes back from another thread
        Thread t = new Thread(buf::release);
        t.start();
        t.join();

        assertEquals(0, allocator.usedHeapMemory(),
                "chunk must deallocate once its last segment is returned");
    }

    // The thread-local counterpart: the owner thread exits (its FastThreadLocal heap is removed and freed) while
    // buffers of its magazine's active chunk are still live, and they come back from another thread.
    @Test
    void segmentReturnedAfterThreadLocalHeapFreeMustStillDeallocateChunk() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode has no thread-local heaps");
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        final List<ByteBuf> live = new ArrayList<ByteBuf>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread owner = new Thread(() -> FastThreadLocalThread.runWithFastThreadLocal(() -> {
            try {
                for (int i = 0; i < 4; i++) {
                    live.add(allocator.heapBuffer(256));
                }
                // Some segments come back on the owner thread, some stay live past the heap's removal.
                live.remove(0).release();
                live.remove(0).release();
                assertSame(live.get(0).array(), live.get(1).array(), "both live buffers share the active chunk");
            } catch (Throwable t) {
                failure.set(t);
            }
        }));
        owner.start();
        owner.join();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertTrue(allocator.usedHeapMemory() > 0, "the live buffers still hold their chunk");

        // The test thread is not the owner, so these take the cross-thread release path.
        live.remove(0).release();
        assertTrue(allocator.usedHeapMemory() > 0, "one segment is still outstanding");
        live.remove(0).release();
        assertEquals(0, allocator.usedHeapMemory(),
                "chunk must deallocate once its last segment is returned");
    }

    /**
     * The fallback in the allocation slow path: a polled chunk without a free segment is given up and a fresh
     * chunk serves the allocation. The cache never hands out such a chunk, so the test makes one by taking every
     * free segment out of a cached chunk behind the cache's back. With assertions enabled the allocation fails
     * the assertion, but only after the fresh chunk is in place, so the allocator keeps working.
     */
    @Test
    void polledChunkWithoutAFreeSegmentFallsBackToAFreshChunk() throws Exception {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        List<ByteBuf> held = new ArrayList<ByteBuf>();
        try {
            // Chunk A: every segment handed out. Chunk B: the active chunk, one segment handed out.
            for (int i = 0; i <= BURST_SEGMENTS_PER_CHUNK; i++) {
                held.add(allocator.heapBuffer(BURST_BUF_SIZE));
            }
            byte[] arrayA = held.get(0).array();
            byte[] arrayB = held.get(BURST_SEGMENTS_PER_CHUNK).array();
            SizeClassedChunk chunkA = chunkOf(held.get(0));
            SizeClassedChunkCache cache = chunkA.owningCache;
            // Chunk A is the only queued chunk of its class, so it is retained once fully free.

            // Return A's segments from another thread while the stripe lock is held, so they land in A's MPSC
            // free list and leave a note; the drain then files A as reusable.
            List<StampedLock> locks = stripeLocks(allocator);
            List<Long> stamps = new ArrayList<Long>();
            for (StampedLock l : locks) {
                stamps.add(l.writeLock());
            }
            try {
                for (int i = 0; i < BURST_SEGMENTS_PER_CHUNK; i++) {
                    release(held.get(i), true);
                }
                cache.drainPending();
                // Behind the cache's back: take every free segment out of A.
                int taken = 0;
                while (chunkA.externalFreeList.poll() != -1) {
                    taken++;
                }
                assertEquals(BURST_SEGMENTS_PER_CHUNK, taken);
            } finally {
                for (int i = 0; i < locks.size(); i++) {
                    locks.get(i).unlockWrite(stamps.get(i));
                }
            }
            held.subList(0, BURST_SEGMENTS_PER_CHUNK).clear();
            assertEquals(2L * BURST_CHUNK_SIZE, allocator.usedHeapMemory());

            // Run B out of segments; the next allocation polls A.
            for (int i = 1; i < BURST_SEGMENTS_PER_CHUNK; i++) {
                held.add(allocator.heapBuffer(BURST_BUF_SIZE));
            }
            assertEquals(2L * BURST_CHUNK_SIZE, allocator.usedHeapMemory());
            if (AdaptivePoolingAllocator.class.desiredAssertionStatus()) {
                AssertionError failed = null;
                try {
                    allocator.heapBuffer(BURST_BUF_SIZE).release();
                } catch (AssertionError e) {
                    failed = e;
                }
                assertNotNull(failed, "the fallback must fail its assertion when assertions are enabled");
                assertEquals("the cache handed out a chunk without a free segment", failed.getMessage());
            } else {
                held.add(allocator.heapBuffer(BURST_BUF_SIZE));
            }
            // A fresh chunk C serves the allocations, and the allocator keeps working.
            assertEquals(3L * BURST_CHUNK_SIZE, allocator.usedHeapMemory());
            ByteBuf next = allocator.heapBuffer(BURST_BUF_SIZE);
            held.add(next);
            assertFalse(next.array() == arrayA || next.array() == arrayB, "the allocation must land in chunk C");
            assertEquals(3L * BURST_CHUNK_SIZE, allocator.usedHeapMemory());
        } finally {
            for (ByteBuf buf : held) {
                buf.release();
            }
        }
    }

    // --- Cross-thread returns that miss the stripe lock ---
    //
    // A releaser that cannot take the stripe lock puts its segment in the chunk's MPSC free list and
    // leaves a note on the owning cache. Nothing scans for such chunks any more, so if a note is lost
    // the chunk stays on the exhausted list forever: it has capacity nobody can find, and it is never
    // fully free either, so the purge sweep will not evict it. Both tests below are about that.

    /** Buffer size whose size class has a 128 KiB chunk of 32 segments. */
    private static final int BURST_BUF_SIZE = 4096;
    private static final int BURST_SEGMENTS_PER_CHUNK = 32;
    private static final int BURST_CHUNK_SIZE = BURST_BUF_SIZE * BURST_SEGMENTS_PER_CHUNK;
    private static final int BURST_CHUNKS = 400;

    /**
     * Runs the burst with every stripe write lock held, so no releaser can apply the exhausted -&gt;
     * reusable transition inline and the notification is the only thing that can move a chunk. Without
     * that this assertion is at the mercy of the scheduler: with the locks free, most chunks are moved
     * by the lock-winning path and deleting the drain's {@code moveToReusable} still leaves only a
     * handful stranded.
     */
    @Test
    void noChunkIsStrandedAfterABurstWithCrossThreadReleases() throws Exception {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        runBurstWithCrossThreadReleases(allocator, true);

        // Every worker has been joined, so the lists are quiescent and safe to walk from here.
        for (SizeClassedChunkCache cache : sizeClassChunkCaches(allocator)) {
            int stranded = 0;
            for (AdaptivePoolingAllocator.Chunk c = cache.exhausted.head; c != null; c = c.nextInQueue) {
                if (((SizeClassedChunk) c).hasRemainingCapacity()) {
                    stranded++;
                }
            }
            assertEquals(0, stranded,
                    "chunks left on the exhausted list with capacity: neither reusable nor evictable");
        }
    }

    @Test
    void memoryFallsBackToOneChunkPerSizeClassAndTheRecyclerBudgetAfterAnIdleBurst() throws Exception {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        long peak = runBurstWithCrossThreadReleases(allocator, false);

        int caches = sizeClassChunkCaches(allocator).size();
        // Per cache: the one chunk it never gives up, the magazine's active chunk, and slack; and the stripe's
        // recycler, which keeps the chunk buffers the caches let go up to its byte budget. The burst ran on one
        // stripe (see runBurstWithCrossThreadReleases).
        long bound = (long) caches * 4 * BURST_CHUNK_SIZE + SizeClassChunkRecycler.RECYCLED_BYTES_BUDGET;
        long settled = allocator.usedHeapMemory();

        assertTrue(peak > bound, "the burst must go beyond what may be retained, or this tests nothing: peak "
                + peak + ", bound " + bound);
        assertTrue(settled <= bound,
                "after the burst went idle the caches must fall back to one chunk each and the recycler to its "
                        + "budget: settled " + settled + " > " + bound + " (" + caches + " caches, chunks of "
                        + BURST_CHUNK_SIZE + "), peak was " + peak);
    }

    /**
     * Allocate a large live set on one thread, then hand every buffer to a pool of releaser threads
     * that contend with each other for the same stripe lock, so most returns take the lock-free MPSC
     * path and have to leave a note behind. Returns the peak used memory, and leaves the allocator
     * settled on a small working set.
     *
     * <p>All allocation happens on one thread, and never while the releasers are running: a stripe
     * whose lock is contended makes the allocation path fall through to another stripe, and a stripe
     * that is never allocated on again is also never purged (that is true of this allocator with or
     * without the notification queue). Keeping to a single stripe is what makes the assertions here
     * about the mechanism rather than about stripe scheduling.
     */
    private static long runBurstWithCrossThreadReleases(final AdaptiveByteBufAllocator allocator,
            final boolean forceNotifyPath) throws Exception {
        final BlockingQueue<ByteBuf> toRelease = new ArrayBlockingQueue<ByteBuf>(1024);
        final AtomicBoolean handedOver = new AtomicBoolean();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final AtomicReference<Long> peak = new AtomicReference<Long>(0L);
        final CountDownLatch releasersDone = new CountDownLatch(8);

        Thread[] releasers = new Thread[8];
        for (int i = 0; i < releasers.length; i++) {
            releasers[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (;;) {
                            ByteBuf buf = toRelease.poll(1, TimeUnit.MILLISECONDS);
                            if (buf != null) {
                                buf.release();
                            } else if (handedOver.get() && toRelease.isEmpty()) {
                                return;
                            }
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        releasersDone.countDown();
                    }
                }
            }, "releaser-" + i);
            releasers[i].start();
        }

        Thread allocatorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int burstBuffers = BURST_CHUNKS * BURST_SEGMENTS_PER_CHUNK;
                    ByteBuf[] live = new ByteBuf[burstBuffers];
                    for (int i = 0; i < burstBuffers; i++) {
                        live[i] = allocator.heapBuffer(BURST_BUF_SIZE);
                    }
                    peak.set(allocator.usedHeapMemory());

                    // Optionally hold every stripe write lock across the release phase. Contention
                    // alone only makes *most* returns take the notify path - how many is up to the
                    // scheduler, and if every releaser happens to win the lock the assertions below
                    // test nothing. With the locks held no releaser can win, so the notification is
                    // the only thing that can move a chunk, deterministically.
                    List<Long> stamps = new ArrayList<Long>();
                    List<StampedLock> locks = forceNotifyPath ?
                            stripeLocks(allocator) : Collections.<StampedLock>emptyList();
                    for (StampedLock l : locks) {
                        stamps.add(l.writeLock());
                    }

                    // Hand the live set to the releasers, which now contend with each other.
                    for (int i = 0; i < burstBuffers; i++) {
                        toRelease.put(live[i]);
                        live[i] = null;
                    }
                    handedOver.set(true);
                    releasersDone.await();
                    for (int i = 0; i < locks.size(); i++) {
                        locks.get(i).unlockWrite(stamps.get(i));
                    }

                    // Settle on a tiny working set, on the same thread and so the same stripe. These
                    // allocations are what drives the heap-wide drain and the purge tick; the releases
                    // are uncontended now, so they take the inline path and leave no new notes.
                    int allocations = 8 * BURST_SEGMENTS_PER_CHUNK
                            * (int) AdaptivePoolingAllocator.CHUNK_PURGE_INTERVAL * 4;
                    for (int i = 0; i < allocations; i++) {
                        allocator.heapBuffer(BURST_BUF_SIZE).release();
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                    handedOver.set(true);
                }
            }
        }, "burst-allocator");
        allocatorThread.start();
        allocatorThread.join();
        for (Thread t : releasers) {
            t.join();
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return peak.get();
    }

    private static List<StampedLock> stripeLocks(AdaptiveByteBufAllocator allocator) throws Exception {
        Field heapField = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        heapField.setAccessible(true);
        Object pooling = heapField.get(allocator);
        Field stripesField = pooling.getClass().getDeclaredField("stripedHeaps");
        stripesField.setAccessible(true);
        Object[] stripes = (Object[]) stripesField.get(pooling);
        List<StampedLock> out = new ArrayList<StampedLock>();
        for (Object stripe : stripes) {
            if (stripe == null) {
                continue;
            }
            Field lockField = stripe.getClass().getDeclaredField("lock");
            lockField.setAccessible(true);
            out.add((StampedLock) lockField.get(stripe));
        }
        return out;
    }

    private static List<SizeClassedChunkCache> sizeClassChunkCaches(
            AdaptiveByteBufAllocator allocator) throws Exception {
        Field heapField = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        heapField.setAccessible(true);
        Object pooling = heapField.get(allocator);
        Field stripesField = pooling.getClass().getDeclaredField("stripedHeaps");
        stripesField.setAccessible(true);
        Object[] stripes = (Object[]) stripesField.get(pooling);
        List<SizeClassedChunkCache> caches = new ArrayList<SizeClassedChunkCache>();
        for (Object stripe : stripes) {
            if (stripe == null) {
                continue;
            }
            Field magsField = stripe.getClass().getDeclaredField("magazines");
            magsField.setAccessible(true);
            Object[] magazines = (Object[]) magsField.get(stripe);
            if (magazines == null) {
                continue;
            }
            for (Object magazine : magazines) {
                if (magazine == null) {
                    continue;
                }
                Field cacheField = magazine.getClass().getDeclaredField("chunkCache");
                cacheField.setAccessible(true);
                Object cache = cacheField.get(magazine);
                if (cache instanceof SizeClassedChunkCache) {
                    caches.add((SizeClassedChunkCache) cache);
                }
            }
        }
        return caches;
    }

    private static void shuffle(SplittableRandom rng, Object array) {
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            int n = rng.nextInt(i, len);
            Object value = Array.get(array, i);
            Array.set(array, i, Array.get(array, n));
            Array.set(array, n, value);
        }
    }

    /**
     * What a chunk says about its free segments - whether it has one, whether it has all of them, and the bytes it
     * last counted - through every way a segment comes back: never handed out, released under the lock, released
     * by another thread that could not take the lock and not yet polled, and polled. Each segment that came back
     * is then handed out again, once.
     */
    @Test
    void capacityQueriesFollowEveryWayASegmentComesBack() throws Exception {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        List<ByteBuf> held = new ArrayList<ByteBuf>();
        // One buffer: chunk A is active and the other 31 segments were never handed out.
        held.add(allocator.heapBuffer(BURST_BUF_SIZE));
        SizeClassedChunk chunkA = chunkOf(held.get(0));
        byte[] arrayA = held.get(0).array();
        assertTrue(chunkA.hasRemainingCapacity());
        assertFalse(chunkA.hasFullCapacity());

        // Chunk A: every segment handed out. Chunk B: the active chunk, one segment handed out.
        for (int i = 1; i <= BURST_SEGMENTS_PER_CHUNK; i++) {
            held.add(allocator.heapBuffer(BURST_BUF_SIZE));
        }
        assertSame(arrayA, held.get(BURST_SEGMENTS_PER_CHUNK - 1).array());
        assertNotSame(arrayA, held.get(BURST_SEGMENTS_PER_CHUNK).array());
        assertFalse(chunkA.hasRemainingCapacity());
        assertEquals(0, chunkA.remainingCapacity());

        // Released by a thread that could take the stripe lock: straight into the chunk's local free list.
        release(held.get(0), false);
        assertTrue(chunkA.hasRemainingCapacity());
        assertEquals(BURST_BUF_SIZE, chunkA.remainingCapacity());

        // Released by a thread that cannot take the lock: counted as free before anyone takes them over. The last
        // buffer of A stays in use, so that A is never given up and the same chunk serves what follows.
        ByteBuf lastOfA = held.get(BURST_SEGMENTS_PER_CHUNK - 1);
        List<StampedLock> locks = stripeLocks(allocator);
        List<Long> stamps = new ArrayList<Long>();
        for (StampedLock l : locks) {
            stamps.add(l.writeLock());
        }
        try {
            for (int i = 1; i < BURST_SEGMENTS_PER_CHUNK - 1; i++) {
                release(held.get(i), true);
            }
            assertTrue(chunkA.hasRemainingCapacity());
            assertFalse(chunkA.hasFullCapacity());
            assertEquals((BURST_SEGMENTS_PER_CHUNK - 1) * BURST_BUF_SIZE, chunkA.remainingCapacity());
        } finally {
            for (int i = 0; i < locks.size(); i++) {
                locks.get(i).unlockWrite(stamps.get(i));
            }
        }
        ByteBuf firstOfB = held.get(BURST_SEGMENTS_PER_CHUNK);
        held.clear();

        // Run B out of segments; then A serves 31 allocations: the one released under the lock, then the 30 taken
        // over from the other thread, each once and never the segment still in use.
        List<ByteBuf> fromB = new ArrayList<ByteBuf>();
        fromB.add(firstOfB);
        for (int i = 1; i < BURST_SEGMENTS_PER_CHUNK; i++) {
            fromB.add(allocator.heapBuffer(BURST_BUF_SIZE));
            assertNotSame(arrayA, fromB.get(i).array());
        }
        Set<Integer> offsets = new HashSet<Integer>();
        offsets.add(lastOfA.arrayOffset());
        for (int i = 1; i < BURST_SEGMENTS_PER_CHUNK; i++) {
            ByteBuf buf = allocator.heapBuffer(BURST_BUF_SIZE);
            held.add(buf);
            assertSame(arrayA, buf.array());
            assertSame(chunkA, chunkOf(buf));
            assertTrue(offsets.add(buf.arrayOffset()), "segment handed out twice");
        }
        assertFalse(chunkA.hasRemainingCapacity());
        assertEquals(0, chunkA.remainingCapacity());

        // Every segment of A back, from another thread that cannot take the lock: all of them free, none polled yet.
        held.add(lastOfA);
        for (StampedLock l : locks) {
            stamps.set(locks.indexOf(l), l.writeLock());
        }
        try {
            for (ByteBuf buf : held) {
                release(buf, true);
            }
            assertTrue(chunkA.hasFullCapacity());
            assertTrue(chunkA.hasRemainingCapacity());
        } finally {
            for (int i = 0; i < locks.size(); i++) {
                locks.get(i).unlockWrite(stamps.get(i));
            }
        }
        for (ByteBuf buf : fromB) {
            buf.release();
        }
    }

    /**
     * The last segment of a chunk comes back after the allocator was freed, from a thread that cannot take the
     * stripe lock: it goes on the external list, and that release must be the one that deallocates the chunk.
     */
    @Test
    void segmentReturnedExternallyAfterFreeMustStillDeallocateChunk() throws Exception {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        ByteBuf buf = allocator.heapBuffer(256);
        List<StampedLock> locks = stripeLocks(allocator);
        freeHeap(allocator);
        assertTrue(allocator.usedHeapMemory() > 0);
        List<Long> stamps = new ArrayList<Long>();
        for (StampedLock l : locks) {
            stamps.add(l.writeLock());
        }
        try {
            release(buf, true);
        } finally {
            for (int i = 0; i < locks.size(); i++) {
                locks.get(i).unlockWrite(stamps.get(i));
            }
        }
        assertEquals(0, allocator.usedHeapMemory(), "chunk must deallocate once its last segment is returned");
    }

    /**
     * A buffer that outgrows its segment moves to a larger one: its bytes move with it, and the segment it left is
     * free again - the next buffer of that size gets it.
     */
    @Test
    void aBufferThatOutgrowsItsSegmentGivesItBack() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        ByteBuf buf = allocator.heapBuffer(256, 8192);
        byte[] firstArray = buf.array();
        int firstOffset = buf.arrayOffset();
        for (int i = 0; i < 256; i++) {
            buf.writeByte(i);
        }
        buf.capacity(1024);
        assertTrue(buf.array() != firstArray || buf.arrayOffset() != firstOffset, "the buffer did not move");
        for (int i = 0; i < 256; i++) {
            assertEquals((byte) i, buf.getByte(i));
        }
        ByteBuf next = allocator.heapBuffer(256, 256);
        assertSame(firstArray, next.array());
        assertEquals(firstOffset, next.arrayOffset());
        next.writeLong(42);
        for (int i = 0; i < 256; i++) {
            assertEquals((byte) i, buf.getByte(i));
        }
        next.release();
        buf.release();
    }

    /**
     * The owner thread keeps allocating while other threads release what it allocated: their segments reach it
     * through the chunk's external MPSC free list, which it polls one at a time while they offer. A segment must
     * never be handed out while the buffer that holds it is still in use, whatever the interleaving.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void segmentsReleasedByOtherThreadsAreNeverHandedOutTwice() throws Exception {
        final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, true);
        final int releasers = 3;
        final int allocations = 300000;
        final BlockingQueue<ByteBuf> toRelease = new ArrayBlockingQueue<ByteBuf>(256);
        final java.util.concurrent.ConcurrentHashMap<Long, Boolean> inUse =
                new java.util.concurrent.ConcurrentHashMap<Long, Boolean>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final ByteBuf poison = Unpooled.buffer(1);
        Thread[] threads = new Thread[releasers];
        for (int i = 0; i < releasers; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (;;) {
                            ByteBuf buf = toRelease.take();
                            if (buf == poison) {
                                return;
                            }
                            // Forget the segment before releasing it: it can only be handed out again afterwards.
                            inUse.remove(buf.memoryAddress());
                            buf.release();
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                }
            });
            threads[i].start();
        }
        Thread owner = new FastThreadLocalThread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < allocations && failure.get() == null; i++) {
                        ByteBuf buf = allocator.directBuffer(64, 64);
                        if (inUse.putIfAbsent(buf.memoryAddress(), Boolean.TRUE) != null) {
                            throw new AssertionError("segment at " + buf.memoryAddress() + " handed out twice");
                        }
                        toRelease.put(buf);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        });
        owner.start();
        owner.join();
        for (int i = 0; i < releasers; i++) {
            toRelease.put(poison);
        }
        for (Thread thread : threads) {
            thread.join();
        }
        poison.release();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertTrue(inUse.isEmpty());
    }

    private static void freeHeap(AdaptiveByteBufAllocator allocator) throws Exception {
        Field f = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        f.setAccessible(true);
        Object inner = f.get(allocator);
        Method free = inner.getClass().getDeclaredMethod("free");
        free.setAccessible(true);
        free.invoke(inner);
    }
}
