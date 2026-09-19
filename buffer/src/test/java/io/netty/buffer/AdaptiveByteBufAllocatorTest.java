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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.netty.buffer.AdaptivePoolingAllocator.SizeClassedChunk;
import io.netty.buffer.AdaptivePoolingAllocator.SizeClassedChunkCache;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import io.netty.buffer.AbstractByteBufTest.TestGatheringByteChannel;

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
     * Buddy chunks given up by a magazine are reused by it: allocating and releasing the same set of large buffers
     * over and over from one thread does not grow the memory held after the first round.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void buddyChunksAreReusedAcrossRounds(boolean direct) {
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

        // Create a burst: allocate many chunks' worth of buffers
        int totalChunks = Math.max(16, AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE) * 4 + 10;
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
            int floor = cache.purgeRetentionFloor;
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
            // above are about the active chunk and not about a cache that would evict nothing.
            for (int i = 0; i < BURST_SEGMENTS_PER_CHUNK; i++) {
                release(held.get(i), foreignRelease);
            }
            held.subList(0, BURST_SEGMENTS_PER_CHUNK).clear();
            underStripeLocks(allocator, sharedStripe, cache::drainPending);
            assertEquals(used - BURST_CHUNK_SIZE, allocator.usedHeapMemory());
        } finally {
            for (ByteBuf buf : held) {
                buf.release();
            }
        }
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

        java.lang.reflect.Field f = AdaptiveByteBufAllocator.class.getDeclaredField("heap");
        f.setAccessible(true);
        Object inner = f.get(allocator);
        java.lang.reflect.Method free = inner.getClass().getDeclaredMethod("free");
        free.setAccessible(true);
        free.invoke(inner);

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
            assertTrue(cache.purgeRetentionFloor >= 1, "chunk A must be retained once fully free");

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
    void memoryFallsBackToTheRetentionFloorAfterAnIdleBurst() throws Exception {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, false);
        long peak = runBurstWithCrossThreadReleases(allocator, false);

        int caches = sizeClassChunkCaches(allocator).size();
        int floor = Math.max(1, AdaptivePoolingAllocator.THREAD_LOCAL_CACHE_MIN_BYTES / BURST_CHUNK_SIZE);
        // Per cache: the floor it is allowed to retain, plus the magazine's current chunk, plus slack.
        long bound = (long) caches * (floor + 4) * BURST_CHUNK_SIZE;
        long settled = allocator.usedHeapMemory();

        assertTrue(settled <= bound,
                "after the burst went idle the cache must fall back to the retention floor: settled "
                        + settled + " > " + bound + " (" + caches + " caches, floor " + floor
                        + " chunks of " + BURST_CHUNK_SIZE + "), peak was " + peak);
        assertTrue(settled * 2 < peak,
                "the burst must not still be resident: settled " + settled + ", peak " + peak);
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
                            * (int) AdaptivePoolingAllocator.CHUNK_PURGE_POLLS_THREAD_LOCAL * 4;
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
}
