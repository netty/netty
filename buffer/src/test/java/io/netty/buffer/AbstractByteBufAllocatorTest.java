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

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
        assertTrue(clazz.isInstance(buffer instanceof SimpleLeakAwareByteBuf ? buffer.unwrap() : buffer));
    }

    @Test
    public void testUsedDirectMemory() {
        for (int power = 0; power < 8; power++) {
            int initialCapacity = 1024 << power;
            testUsedDirectMemory(initialCapacity);
        }
    }

    private void testUsedDirectMemory(int initialCapacity) {
        T allocator = newAllocator(true);
        ByteBufAllocatorMetric metric = ((ByteBufAllocatorMetricProvider) allocator).metric();
        assertEquals(0, metric.usedDirectMemory());
        assertEquals(0, metric.pinnedDirectMemory());
        ByteBuf buffer = allocator.directBuffer(initialCapacity, 4 * initialCapacity);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory());
        assertThat(metric.pinnedDirectMemory())
                .isGreaterThanOrEqualTo(capacity)
                .isLessThanOrEqualTo(metric.usedDirectMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory(), buffer.toString());
        assertThat(metric.pinnedDirectMemory())
                .isGreaterThanOrEqualTo(capacity)
                .isLessThanOrEqualTo(metric.usedDirectMemory());

        buffer.release();
        assertEquals(expectedUsedMemoryAfterRelease(allocator, capacity), metric.usedDirectMemory());
        assertThat(metric.pinnedDirectMemory())
                .isGreaterThanOrEqualTo(0)
                .isLessThanOrEqualTo(metric.usedDirectMemory());
        trimCaches(allocator);
        assertEquals(0, metric.pinnedDirectMemory());

        int[] capacities = new int[30];
        Random rng = new Random();
        for (int i = 0; i < capacities.length; i++) {
            capacities[i] = initialCapacity / 4 + rng.nextInt(8 * initialCapacity);
        }
        ByteBuf[] bufs = new ByteBuf[capacities.length];
        for (int i = 0; i < 20; i++) {
            bufs[i] = allocator.directBuffer(capacities[i], 2 * capacities[i]);
        }
        for (int i = 0; i < 10; i++) {
            bufs[i].release();
        }
        for (int i = 20; i < 30; i++) {
            bufs[i] = allocator.directBuffer(capacities[i], 2 * capacities[i]);
        }
        for (int i = 0; i < 10; i++) {
            bufs[i] = allocator.directBuffer(capacities[i], 2 * capacities[i]);
        }
        for (int i = 0; i < 30; i++) {
            bufs[i].release();
        }
        trimCaches(allocator);
        assertEquals(0, metric.pinnedDirectMemory());
    }

    @Test
    public void testUsedHeapMemory() {
        for (int power = 0; power < 8; power++) {
            int initialCapacity = 1024 << power;
            testUsedHeapMemory(initialCapacity);
        }
    }

    private void testUsedHeapMemory(int initialCapacity) {
        T allocator = newAllocator(true);
        ByteBufAllocatorMetric metric = ((ByteBufAllocatorMetricProvider) allocator).metric();

        assertEquals(0, metric.usedHeapMemory());
        assertEquals(0, metric.pinnedDirectMemory());
        ByteBuf buffer = allocator.heapBuffer(initialCapacity, 4 * initialCapacity);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());
        assertThat(metric.pinnedHeapMemory())
                .isGreaterThanOrEqualTo(capacity)
                .isLessThanOrEqualTo(metric.usedHeapMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());
        assertThat(metric.pinnedHeapMemory())
                .isGreaterThanOrEqualTo(capacity)
                .isLessThanOrEqualTo(metric.usedHeapMemory());

        buffer.release();
        assertEquals(expectedUsedMemoryAfterRelease(allocator, capacity), metric.usedHeapMemory());
        assertThat(metric.pinnedHeapMemory())
                .isGreaterThanOrEqualTo(0)
                .isLessThanOrEqualTo(metric.usedHeapMemory());
        trimCaches(allocator);
        assertEquals(0, metric.pinnedHeapMemory());

        int[] capacities = new int[30];
        Random rng = new Random();
        for (int i = 0; i < capacities.length; i++) {
            capacities[i] = initialCapacity / 4 + rng.nextInt(8 * initialCapacity);
        }
        ByteBuf[] bufs = new ByteBuf[capacities.length];
        for (int i = 0; i < 20; i++) {
            bufs[i] = allocator.heapBuffer(capacities[i], 2 * capacities[i]);
        }
        for (int i = 0; i < 10; i++) {
            bufs[i].release();
        }
        for (int i = 20; i < 30; i++) {
            bufs[i] = allocator.heapBuffer(capacities[i], 2 * capacities[i]);
        }
        for (int i = 0; i < 10; i++) {
            bufs[i] = allocator.heapBuffer(capacities[i], 2 * capacities[i]);
        }
        for (int i = 0; i < 30; i++) {
            bufs[i].release();
        }
        trimCaches(allocator);
        assertEquals(0, metric.pinnedDirectMemory());
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
