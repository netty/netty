/*
 * Copyright 2025 The Netty Project
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
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MiByteBufAllocatorTest extends AbstractByteBufAllocatorTest<MiByteBufAllocator> {

    @Override
    protected MiByteBufAllocator newAllocator(boolean preferDirect) {
        return new MiByteBufAllocator(preferDirect);
    }

    @Override
    protected MiByteBufAllocator newUnpooledAllocator() {
        return newAllocator(false);
    }

    @Override
    @Test
    public void testUnsafeHeapBufferAndUnsafeDirectBuffer() {
        MiByteBufAllocator allocator = newUnpooledAllocator();
        ByteBuf directBuffer = allocator.directBuffer();
        assertInstanceOf(directBuffer, MiMallocByteBufAllocator.MiByteBuf.class);
        assertTrue(directBuffer.isDirect());
        directBuffer.release();

        ByteBuf heapBuffer = allocator.heapBuffer();
        assertInstanceOf(heapBuffer, MiMallocByteBufAllocator.MiByteBuf.class);
        assertFalse(heapBuffer.isDirect());
        heapBuffer.release();
    }

    @Override
    @Test
    public void testUsedDirectMemory() {
        MiByteBufAllocator allocator =  newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();
        assertEquals(0, metric.usedDirectMemory());
        ByteBuf buffer = allocator.directBuffer(1024, 4096);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        // This is a new size class, and a new magazine with a new chunk
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory(), buffer.toString());

        buffer.release();
        // Memory is still held by the magazines
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory());
    }

    @Override
    @Test
    public void testUsedHeapMemory() {
        MiByteBufAllocator allocator =  newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();
        assertEquals(0, metric.usedHeapMemory());
        ByteBuf buffer = allocator.heapBuffer(1024, 4096);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        // This is a new size class, and a new magazine with a new chunk
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory(), buffer.toString());

        buffer.release();
        // Memory is still held by the magazines
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());
    }

    @Override
    protected long expectedUsedMemory(MiByteBufAllocator allocator, int capacity) {
        return 1 << 22; // Default segment size: 4MiB
    }

    @Test
    public void testAbandonAndReclaim() throws InterruptedException {
        MiByteBufAllocator allocator = newAllocator(true);
        AtomicReference<ByteBuf> buf1 = new AtomicReference<>();
        AtomicReference<ByteBuf> buf2 = new AtomicReference<>();
        FastThreadLocalThread t1 = new FastThreadLocalThread(new Runnable() {
            @Override
            public void run() {
                buf1.set(allocator.directBuffer());
            }
        });
        t1.start();
        t1.join();
        assertEquals(1, allocator.abandonedDirectSegmentCount());
        FastThreadLocalThread t2 = new FastThreadLocalThread(new Runnable() {
            @Override
            public void run() {
                buf2.set(allocator.directBuffer());
                assertEquals(0, allocator.abandonedDirectSegmentCount());
            }
        });
        t2.start();
        t2.join();
        assertEquals(1, allocator.abandonedDirectSegmentCount());
        buf1.get().release();
        buf2.get().release();
    }
}
