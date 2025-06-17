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
import io.netty.util.internal.PlatformDependent;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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

    @Test
    void chunkMustDeallocateOrReuseWthBufferRelease() throws Exception {
        AdaptiveByteBufAllocator allocator = newAllocator(false);
        ByteBuf a = allocator.heapBuffer(8192);
        assertEquals(128 * 1024, allocator.usedHeapMemory());
        ByteBuf b = allocator.heapBuffer(120 * 1024);
        assertEquals(128 * 1024, allocator.usedHeapMemory());
        b.release();
        a.release();
        assertEquals(128 * 1024, allocator.usedHeapMemory());
        a = allocator.heapBuffer(8192);
        assertEquals(128 * 1024, allocator.usedHeapMemory());
        b = allocator.heapBuffer(120 * 1024);
        assertEquals(128 * 1024, allocator.usedHeapMemory());
        a.release();
        ByteBuf c = allocator.heapBuffer(8192);
        assertEquals(2 * 128 * 1024, allocator.usedHeapMemory());
        c.release();
        assertEquals(2 * 128 * 1024, allocator.usedHeapMemory());
        b.release();
        assertEquals(2 * 128 * 1024, allocator.usedHeapMemory());
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
    public void jfrChunkAllocation() throws ExecutionException, InterruptedException {
        try (RecordingStream stream = new RecordingStream()) {
            CompletableFuture<RecordedEvent> allocateFuture = new CompletableFuture<>();

            stream.enable(AdaptivePoolingAllocator.AllocateChunkEvent.class);
            stream.onEvent(AdaptivePoolingAllocator.AllocateChunkEvent.class.getName(), allocateFuture::complete);
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
    public void jfrBufferAllocation() throws ExecutionException, InterruptedException {
        try (RecordingStream stream = new RecordingStream()) {
            CompletableFuture<RecordedEvent> allocateFuture = new CompletableFuture<>();
            CompletableFuture<RecordedEvent> releaseFuture = new CompletableFuture<>();

            stream.enable(AdaptivePoolingAllocator.AllocateBufferEvent.class);
            stream.onEvent(AdaptivePoolingAllocator.AllocateBufferEvent.class.getName(), allocateFuture::complete);
            stream.enable(AdaptivePoolingAllocator.FreeBufferEvent.class);
            stream.onEvent(AdaptivePoolingAllocator.FreeBufferEvent.class.getName(), releaseFuture::complete);
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
}
