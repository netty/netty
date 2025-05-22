/*
 * Copyright 2017 The Netty Project
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

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class AbstractByteBufAllocatorTest<T extends AbstractByteBufAllocator> extends ByteBufAllocatorTest {

    @Override
    protected abstract T newAllocator(boolean preferDirect);

    protected abstract T newUnpooledAllocator();

    @Override
    protected boolean isDirectExpected(boolean preferDirect) {
        return preferDirect && PlatformDependent.hasUnsafe();
    }

    @Override
    protected final int defaultMaxCapacity() {
        return AbstractByteBufAllocator.DEFAULT_MAX_CAPACITY;
    }

    @Override
    protected final int defaultMaxComponents() {
        return AbstractByteBufAllocator.DEFAULT_MAX_COMPONENTS;
    }

    @Test
    public void testCalculateNewCapacity() {
        testCalculateNewCapacity(true);
        testCalculateNewCapacity(false);
    }

    private void testCalculateNewCapacity(boolean preferDirect) {
        T allocator = newAllocator(preferDirect);
        assertEquals(8, allocator.calculateNewCapacity(1, 8));
        assertEquals(7, allocator.calculateNewCapacity(1, 7));
        assertEquals(64, allocator.calculateNewCapacity(1, 129));
        assertEquals(AbstractByteBufAllocator.CALCULATE_THRESHOLD,
                allocator.calculateNewCapacity(AbstractByteBufAllocator.CALCULATE_THRESHOLD,
                        AbstractByteBufAllocator.CALCULATE_THRESHOLD + 1));
        assertEquals(AbstractByteBufAllocator.CALCULATE_THRESHOLD * 2,
                allocator.calculateNewCapacity(AbstractByteBufAllocator.CALCULATE_THRESHOLD + 1,
                        AbstractByteBufAllocator.CALCULATE_THRESHOLD * 4));
        try {
            allocator.calculateNewCapacity(8, 7);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            allocator.calculateNewCapacity(-1, 8);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testUnsafeHeapBufferAndUnsafeDirectBuffer() {
        T allocator = newUnpooledAllocator();
        ByteBuf directBuffer = allocator.directBuffer();
        assertInstanceOf(directBuffer,
                PlatformDependent.hasUnsafe() ? UnpooledUnsafeDirectByteBuf.class : UnpooledDirectByteBuf.class);
        directBuffer.release();

        ByteBuf heapBuffer = allocator.heapBuffer();
        assertInstanceOf(heapBuffer,
                PlatformDependent.hasUnsafe() ? UnpooledUnsafeHeapByteBuf.class : UnpooledHeapByteBuf.class);
        heapBuffer.release();
    }

    protected static void assertInstanceOf(ByteBuf buffer, Class<? extends ByteBuf> clazz) {
        // Unwrap if needed
        if (buffer instanceof SimpleLeakAwareByteBuf) {
            buffer = buffer.unwrap();
        }
        assertThat(buffer).isInstanceOf(clazz);
    }

    protected static void assertSameBuffer(ByteBuf expected, ByteBuf buffer) {
        // Unwrap if needed
        assertSame(expected, buffer instanceof SimpleLeakAwareByteBuf ? buffer.unwrap() : buffer);
    }

    @Test
    public void testUsedDirectMemory() {
        T allocator =  newAllocator(true);
        ByteBufAllocatorMetric metric = ((ByteBufAllocatorMetricProvider) allocator).metric();
        assertEquals(0, metric.usedDirectMemory());
        ByteBuf buffer = allocator.directBuffer(1024, 4096);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory(), buffer.toString());

        buffer.release();
        assertEquals(expectedUsedMemoryAfterRelease(allocator, capacity), metric.usedDirectMemory());
    }

    @Test
    public void testUsedHeapMemory() {
        T allocator =  newAllocator(true);
        ByteBufAllocatorMetric metric = ((ByteBufAllocatorMetricProvider) allocator).metric();

        assertEquals(0, metric.usedHeapMemory());
        ByteBuf buffer = allocator.heapBuffer(1024, 4096);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());

        buffer.release();
        assertEquals(expectedUsedMemoryAfterRelease(allocator, capacity), metric.usedHeapMemory());
    }

    @Test
    public void shouldReuseChunks() throws Exception {
        int bufSize = 1024 * 1024;
        ByteBufAllocator allocator = newAllocator(false);
        allocator.heapBuffer(bufSize, bufSize).release();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        Class<?> cls = null;
        try {
            cls = Class.forName("com.sun.management.ThreadMXBean");
        } catch (ClassNotFoundException e) {
            abort("Internal ThreadMXBean not available");
        }
        assumeThat(threadMXBean).isInstanceOf(cls);
        Method getThreadAllocatedBytes = cls.getDeclaredMethod("getThreadAllocatedBytes", long.class);
        long allocBefore = (long) getThreadAllocatedBytes.invoke(threadMXBean, Thread.currentThread().getId());
        assumeTrue(allocBefore != -1);
        for (int i = 0; i < 100; ++i) {
            allocator.heapBuffer(bufSize, bufSize).release();
        }
        long allocAfter = (long) getThreadAllocatedBytes.invoke(threadMXBean, Thread.currentThread().getId());
        assumeTrue(allocAfter != -1);
        assertThat(allocAfter - allocBefore)
                .as("allocated MB: %.3f", (allocAfter - allocBefore) / 1024.0 / 1024.0)
                .isLessThan(8 * 1024 * 1024);
    }

    protected long expectedUsedMemory(T allocator, int capacity) {
        return capacity;
    }

    protected long expectedUsedMemoryAfterRelease(T allocator, int capacity) {
        return 0;
    }

    protected void trimCaches(T allocator) {
    }
}
