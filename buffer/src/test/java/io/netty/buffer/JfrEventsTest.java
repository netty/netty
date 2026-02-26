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
package io.netty.buffer;

import io.netty.util.concurrent.FastThreadLocalThread;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
@EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
@Isolated
public class JfrEventsTest {
    PooledByteBufAllocator newPooledAllocator(boolean preferDirect) {
        return new PooledByteBufAllocator(preferDirect);
    }

    AdaptiveByteBufAllocator newAdaptiveAllocator(boolean preferDirect) {
        return new AdaptiveByteBufAllocator(preferDirect);
    }

    @SuppressWarnings("Since15")
    @Test
    public void pooledJfrChunkAllocation() throws Exception {
        try (RecordingStream stream = new RecordingStream()) {
            CompletableFuture<RecordedEvent> allocateFuture = new CompletableFuture<>();

            stream.enable(AllocateChunkEvent.class);
            stream.onEvent(AllocateChunkEvent.NAME, allocateFuture::complete);
            stream.startAsync();

            PooledByteBufAllocator alloc = newPooledAllocator(true);
            alloc.directBuffer(128).release();

            RecordedEvent allocate = allocateFuture.get();
            assertEquals(alloc.metric().chunkSize(), allocate.getInt("capacity"));
            assertTrue(allocate.getBoolean("pooled"));
            assertFalse(allocate.getBoolean("threadLocal"));
            assertTrue(allocate.getBoolean("direct"));
        }
    }

    @SuppressWarnings("Since15")
    @Test
    public void pooledShouldCreateTwoChunks() throws Exception {
        try (RecordingStream stream = new RecordingStream()) {
            final CountDownLatch eventsFlushed = new CountDownLatch(2);
            stream.enable(AllocateChunkEvent.class);
            stream.onEvent(AllocateChunkEvent.NAME,
                    event -> {
                        eventsFlushed.countDown();
                    });
            stream.startAsync();
            PooledByteBufAllocator allocator = newPooledAllocator(false);
            int bufSize = 16896;
            int bufsToAllocate = 1 + allocator.metric().chunkSize() / bufSize;
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
    public void pooledShouldReuseTheSameChunk() throws Exception {
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
            PooledByteBufAllocator allocator = newPooledAllocator(false);
            ByteBuf buf = allocator.heapBuffer(bufSize, bufSize);
            int bufPin = Math.toIntExact(allocator.pinnedHeapMemory());
            buf.release();
            int bufsPerChunk = allocator.metric().chunkSize() / bufPin;
            List<ByteBuf> buffers = new ArrayList<>(bufsPerChunk);
            for (int i = 0; i < bufsPerChunk - 2; ++i) {
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
    public void pooledJfrBufferAllocation() throws Exception {
        try (RecordingStream stream = new RecordingStream()) {
            CompletableFuture<RecordedEvent> allocateFuture = new CompletableFuture<>();
            CompletableFuture<RecordedEvent> releaseFuture = new CompletableFuture<>();

            stream.enable(AllocateBufferEvent.class);
            stream.onEvent(AllocateBufferEvent.NAME, allocateFuture::complete);
            stream.enable(FreeBufferEvent.class);
            stream.onEvent(FreeBufferEvent.NAME, releaseFuture::complete);
            stream.startAsync();

            PooledByteBufAllocator alloc = newPooledAllocator(true);
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
    public void pooledJfrBufferAllocationThreadLocal() throws Exception {
        ByteBufAllocator alloc = newPooledAllocator(true);

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

    @SuppressWarnings("Since15")
    @Test
    public void adaptiveJfrChunkAllocation() throws Exception {
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
    public void adaptiveShouldCreateTwoChunks() throws Exception {
        try (RecordingStream stream = new RecordingStream()) {
            final CountDownLatch eventsFlushed = new CountDownLatch(2);
            stream.enable(AllocateChunkEvent.class);
            stream.onEvent(AllocateChunkEvent.NAME,
                    event -> {
                        eventsFlushed.countDown();
                    });
            stream.startAsync();
            ByteBufAllocator allocator = newAdaptiveAllocator(false);
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
    public void adaptiveShouldReuseTheSameChunk() throws Exception {
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
            ByteBufAllocator allocator = newAdaptiveAllocator(false);
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
    public void adaptiveJfrBufferAllocation() throws Exception {
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
    public void adaptiveJfrBufferAllocationThreadLocal() throws Exception {
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
