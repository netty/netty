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
import io.netty.util.test.DisabledForSlowLeakDetection;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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

    @DisabledForSlowLeakDetection
    @RepeatedTest(100)
    void buddyAllocationConsistency(RepetitionInfo info) {
        SplittableRandom rng = new SplittableRandom(info.getCurrentRepetition());
        AdaptiveByteBufAllocator allocator = newAllocator(true);
        int small = 32768;
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

    @Test
    void chunksShouldBeReusableAfterQueueOverflow() {
        AdaptiveByteBufAllocator allocator = newAllocator(false);

        ByteBuf probe = allocator.heapBuffer(256);
        long chunkSize = allocator.usedHeapMemory();
        int buffersPerChunk = (int) (chunkSize / 256);
        probe.release();

        // Create enough chunks to overflow the reuse queue.
        // CHUNK_REUSE_QUEUE = max(2, availableProcessors() * 2).
        int queueCapacity = Math.max(2, NettyRuntime.availableProcessors() * 2);
        int totalChunks = queueCapacity + 3;
        int totalBuffers = totalChunks * buffersPerChunk;

        // Round 1: allocate all buffers (creates totalChunks exhausted chunks)
        List<ByteBuf> bufs = new ArrayList<>(totalBuffers);
        for (int i = 0; i < totalBuffers; i++) {
            bufs.add(allocator.heapBuffer(256));
        }
        // Release all — segments return to chunks.
        // With the current code: overflowed chunks get markToDeallocate'd and deallocate here.
        for (ByteBuf buf : bufs) {
            buf.release();
        }
        bufs.clear();
        long baselineAfterRound1 = allocator.usedHeapMemory();

        // Round 2: allocate the same amount
        for (int i = 0; i < totalBuffers; i++) {
            bufs.add(allocator.heapBuffer(256));
        }
        long round2Memory = allocator.usedHeapMemory();
        for (ByteBuf buf : bufs) {
            buf.release();
        }

        // If all chunks from round 1 were reusable, round 2 should not need more memory.
        // With the current code, chunks that overflowed the bounded queue were permanently lost,
        // so round 2 must allocate new chunks to replace them → round2Memory > baseline.
        assertTrue(round2Memory <= baselineAfterRound1 + chunkSize,
                "Round 2 should reuse chunks from round 1 without significant new allocation. " +
                "Baseline after round 1: " + baselineAfterRound1 + ", Round 2 peak: " + round2Memory +
                ", chunkSize: " + chunkSize);
    }

    @Test
    void chunkMemoryShouldStabilizeAcrossAllocationCycles() {
        AdaptiveByteBufAllocator allocator = newAllocator(false);

        ByteBuf probe = allocator.heapBuffer(256);
        long chunkSize = allocator.usedHeapMemory();
        int buffersPerChunk = (int) (chunkSize / 256);
        probe.release();

        int queueCapacity = Math.max(2, NettyRuntime.availableProcessors() * 2);
        int buffersPerRound = (queueCapacity + 2) * buffersPerChunk;

        long[] memoryPerRound = new long[6];
        List<ByteBuf> bufs = new ArrayList<>(buffersPerRound);

        for (int round = 0; round < 6; round++) {
            for (int i = 0; i < buffersPerRound; i++) {
                bufs.add(allocator.heapBuffer(256));
            }
            memoryPerRound[round] = allocator.usedHeapMemory();
            for (ByteBuf buf : bufs) {
                buf.release();
            }
            bufs.clear();
        }

        // Memory at peak should be the same across all rounds if chunks are properly reused.
        // Allow 1 chunk tolerance for the magazine's current/nextInLine slots.
        long maxDelta = chunkSize;
        assertEquals(memoryPerRound[1], memoryPerRound[5], maxDelta,
                "Memory should stabilize by round 2, not grow. Per-round peaks: " +
                Arrays.toString(memoryPerRound));
    }

    @Test
    void benchmarkPatternChunkReuse() throws Exception {
        AdaptiveByteBufAllocator allocator = newAllocator(false);

        int[] sizeFreq = {
            256, 21272,
            1024, 29289,
            636, 5443,
            15, 10344,
            16639, 9269,
            4574, 165,
            8670, 195,
            2048, 2636,
            4096, 26,
        };

        ArrayList<Integer> sizeList = new ArrayList<>();
        for (int i = 0; i < sizeFreq.length; i += 2) {
            int size = sizeFreq[i];
            int freq = Math.min(sizeFreq[i + 1], 2000);
            for (int j = 0; j < freq; j++) {
                sizeList.add(size);
            }
        }
        int[] allSizes = new int[sizeList.size()];
        for (int i = 0; i < allSizes.length; i++) {
            allSizes[i] = sizeList.get(i);
        }
        SplittableRandom rng = new SplittableRandom(42);
        for (int i = allSizes.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = allSizes[i];
            allSizes[i] = allSizes[j];
            allSizes[j] = tmp;
        }

        // Run on a FastThreadLocalThread with EventExecutor mapped (like the benchmark's HarnessExecutor)
        AtomicReference<Throwable> error = new AtomicReference<>();
        io.netty.util.concurrent.EventExecutor dummyExecutor =
                io.netty.util.concurrent.ImmediateEventExecutor.INSTANCE;
        Runnable task = () -> {
            try {
                int maxLiveBuffers = 8192;
                ByteBuf[] buffers = new ByteBuf[maxLiveBuffers];
                SplittableRandom r = new SplittableRandom(99);

                for (int i = 0; i < maxLiveBuffers; i++) {
                    buffers[i] = allocator.heapBuffer(allSizes[i % allSizes.length]);
                }
                long memoryAfterFill = allocator.usedHeapMemory();

                int ops = maxLiveBuffers * 4;
                long memoryMid = 0;
                for (int i = 0; i < ops; i++) {
                    int slot = r.nextInt(maxLiveBuffers);
                    buffers[slot].release();
                    buffers[slot] = allocator.heapBuffer(
                            allSizes[(maxLiveBuffers + i) % allSizes.length]);
                    if (i == ops / 2) {
                        memoryMid = allocator.usedHeapMemory();
                    }
                }
                long memoryEnd = allocator.usedHeapMemory();

                for (ByteBuf buf : buffers) {
                    buf.release();
                }

                long maxGrowth = memoryAfterFill / 10;
                assertTrue(memoryEnd - memoryAfterFill <= maxGrowth,
                        "Memory should stabilize. After fill: " + memoryAfterFill / 1024 +
                        " KiB, at end: " + memoryEnd / 1024 + " KiB");
            } catch (Throwable t) {
                error.set(t);
            }
        };
        Runnable mapped = io.netty.util.internal.ThreadExecutorMap.apply(task, dummyExecutor);
        Thread ftlt = new io.netty.util.concurrent.FastThreadLocalThread(mapped);
        ftlt.start();
        ftlt.join();
        if (error.get() != null) {
            fail("Test failed on FastThreadLocalThread", error.get());
        }
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
