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
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
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

    @SuppressWarnings("Since15")
    @Test
    @EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
    @Timeout(10)
    public void jfrChunkAllocation() throws Exception {
        try (RecordingStream stream = new RecordingStream()) {
            CompletableFuture<RecordedEvent> allocateFuture = new CompletableFuture<>();

            stream.enable(AllocateChunkEvent.class);
            stream.onEvent(AllocateChunkEvent.NAME, allocateFuture::complete);
            stream.startAsync();

            AdaptiveByteBufAllocator alloc = new AdaptiveByteBufAllocator(true, false);
            alloc.directBuffer(128).release();

            RecordedEvent allocate = allocateFuture.get();
            assertEquals(AdaptivePoolingAllocator.MIN_CHUNK_SIZE, allocate.getInt("capacity"));
            assertTrue(allocate.getBoolean("pooled"));
            assertFalse(allocate.getBoolean("threadLocal"));
            assertTrue(allocate.getBoolean("direct"));
        }
    }

    @SuppressWarnings("Since15")
    @Test
    @EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
    @Timeout(10)
    public void shouldCreateTwoChunks() throws Exception {
        try (RecordingStream stream = new RecordingStream()) {
            final CountDownLatch eventsFlushed = new CountDownLatch(2);
            stream.enable(AllocateChunkEvent.class);
            stream.onEvent(AllocateChunkEvent.NAME,
                           event -> {
                                eventsFlushed.countDown();
                           });
            stream.startAsync();
            ByteBufAllocator allocator = newAllocator(false);
            int bufSize = 16896;
            int minSegmentsPerChunk = 32; // See AdaptivePoolingAllocator.SizeClassChunkController.
            int bufsToAllocate = 1 + minSegmentsPerChunk;
            List<ByteBuf> buffers = new ArrayList<>(bufsToAllocate);
            for (int i = 0; i < bufsToAllocate; ++i) {
                buffers.add(allocator.heapBuffer(bufSize, bufSize));
            }
            // release all buffers
            for (ByteBuf buffer : buffers) {
                buffer.release();
            }
            buffers.clear();
            eventsFlushed.await();
            assertEquals(0, eventsFlushed.getCount());
        }
    }

    @SuppressWarnings("Since15")
    @Test
    @EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
    @Timeout(10)
    public void shouldReuseTheSameChunk() throws Exception {
        try (RecordingStream stream = new RecordingStream()) {
            final CountDownLatch eventsFlushed = new CountDownLatch(1);
            final AtomicInteger chunksAllocations = new AtomicInteger();
            stream.enable(AllocateChunkEvent.class);
            stream.onEvent(AllocateChunkEvent.NAME,
                           event -> {
                               chunksAllocations.incrementAndGet();
                               eventsFlushed.countDown();
                           });
            stream.startAsync();
            int bufSize = 16896;
            ByteBufAllocator allocator = newAllocator(false);
            List<ByteBuf> buffers = new ArrayList<>(32);
            for (int i = 0; i < 30; ++i) {
                buffers.add(allocator.heapBuffer(bufSize, bufSize));
            }
            // we still have 2 available segments in the chunk, so we should not allocate a new one
            for (int i = 0; i < 128; ++i) {
                allocator.heapBuffer(bufSize, bufSize).release();
            }
            // release all buffers
            for (ByteBuf buffer : buffers) {
                buffer.release();
            }
            buffers.clear();
            eventsFlushed.await();
            assertEquals(1, chunksAllocations.get());
        }
    }

    @SuppressWarnings("Since15")
    @Test
    @EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
    @Timeout(10)
    public void jfrBufferAllocation() throws Exception {
        try (RecordingStream stream = new RecordingStream()) {
            CompletableFuture<RecordedEvent> allocateFuture = new CompletableFuture<>();
            CompletableFuture<RecordedEvent> releaseFuture = new CompletableFuture<>();

            stream.enable(AllocateBufferEvent.class);
            stream.onEvent(AllocateBufferEvent.NAME, allocateFuture::complete);
            stream.enable(FreeBufferEvent.class);
            stream.onEvent(FreeBufferEvent.NAME, releaseFuture::complete);
            stream.startAsync();

            AdaptiveByteBufAllocator alloc = new AdaptiveByteBufAllocator(true, false);
            alloc.directBuffer(128).release();

            RecordedEvent allocate = allocateFuture.get();
            assertEquals(128, allocate.getInt("size"));
            assertEquals(128, allocate.getInt("maxFastCapacity"));
            assertEquals(Integer.MAX_VALUE, allocate.getInt("maxCapacity"));
            assertTrue(allocate.getBoolean("chunkPooled"));
            assertFalse(allocate.getBoolean("chunkThreadLocal"));
            assertTrue(allocate.getBoolean("direct"));

            RecordedEvent release = releaseFuture.get();
            assertEquals(128, release.getInt("size"));
            assertEquals(128, release.getInt("maxFastCapacity"));
            assertEquals(Integer.MAX_VALUE, release.getInt("maxCapacity"));
            assertTrue(release.getBoolean("direct"));
        }
    }

    @SuppressWarnings("Since15")
    @Test
    @EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
    @Timeout(10)
    public void jfrBufferAllocationThreadLocal() throws Exception {
        ByteBufAllocator alloc = new AdaptiveByteBufAllocator(true, true);

        Callable<Void> allocateAndRelease = () -> {
            try (RecordingStream stream = new RecordingStream()) {
                CompletableFuture<RecordedEvent> allocateFuture = new CompletableFuture<>();
                CompletableFuture<RecordedEvent> releaseFuture = new CompletableFuture<>();

                // Prime the cache.
                alloc.directBuffer(128).release();

                stream.enable(AllocateBufferEvent.class);
                stream.onEvent(AllocateBufferEvent.NAME, allocateFuture::complete);
                stream.enable(FreeBufferEvent.class);
                stream.onEvent(FreeBufferEvent.NAME, releaseFuture::complete);
                stream.startAsync();

                // Allocate out of the cache.
                alloc.directBuffer(128).release();

                RecordedEvent allocate = allocateFuture.get();
                assertEquals(128, allocate.getInt("size"));
                assertEquals(128, allocate.getInt("maxFastCapacity"));
                assertEquals(Integer.MAX_VALUE, allocate.getInt("maxCapacity"));
                assertTrue(allocate.getBoolean("chunkPooled"));
                assertTrue(allocate.getBoolean("chunkThreadLocal"));
                assertTrue(allocate.getBoolean("direct"));

                RecordedEvent release = releaseFuture.get();
                assertEquals(128, release.getInt("size"));
                assertEquals(128, release.getInt("maxFastCapacity"));
                assertEquals(Integer.MAX_VALUE, release.getInt("maxCapacity"));
                assertTrue(release.getBoolean("direct"));
                return null;
            }
        };
        FutureTask<Void> task = new FutureTask<>(allocateAndRelease);
        FastThreadLocalThread thread = new FastThreadLocalThread(task);
        thread.start();
        task.get();
    }
}
