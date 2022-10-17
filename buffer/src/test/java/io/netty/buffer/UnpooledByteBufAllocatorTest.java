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

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnpooledByteBufAllocatorTest extends AbstractByteBufAllocatorTest<UnpooledByteBufAllocator> {

    @Override
    protected UnpooledByteBufAllocator newAllocator(boolean preferDirect) {
        return new UnpooledByteBufAllocator(preferDirect);
    }

    @Override
    protected UnpooledByteBufAllocator newUnpooledAllocator() {
        return new UnpooledByteBufAllocator(false);
    }

    @Test
    public void testGlobalPinnedDirectMemoryWithDirectAllocator() throws Exception {
        Field field = PlatformDependent.class.getDeclaredField("PINNED_DIRECT_MEMORY_COUNTER");
        field.setAccessible(true);
        AtomicLong pinnedDirectMemory = (AtomicLong) field.get(null);
        if (pinnedDirectMemory == null) {
            return;
        }

        UnpooledByteBufAllocator allocator = newUnpooledAllocator();
        ByteBuf byteBuf = allocator.directBuffer(16);
        assertEquals(16, pinnedDirectMemory.get());
        byteBuf.release();
        assertEquals(0, pinnedDirectMemory.get());

        byteBuf = allocator.directBuffer(16);
        assertEquals(16, pinnedDirectMemory.get());

        //test extend byteBuf
        byteBuf = byteBuf.capacity(32);
        assertEquals(32, pinnedDirectMemory.get());

        byteBuf.release();
        assertEquals(0, pinnedDirectMemory.get());
    }

    @Test
    public void testGlobalPinnedDirectMemoryWithMultiDirectAllocator() throws Exception {
        Field field = PlatformDependent.class.getDeclaredField("PINNED_DIRECT_MEMORY_COUNTER");
        field.setAccessible(true);
        AtomicLong pinnedDirectMemory = (AtomicLong) field.get(null);
        if (pinnedDirectMemory == null) {
            return;
        }

        UnpooledByteBufAllocator allocator = newUnpooledAllocator();
        ByteBuf byteBuf = allocator.directBuffer(16);
        assertEquals(16, pinnedDirectMemory.get());

        UnpooledByteBufAllocator allocator1 = newUnpooledAllocator();
        ByteBuf byteBuf1 = allocator1.directBuffer(16);
        assertEquals(32, pinnedDirectMemory.get());

        byteBuf.release();
        assertEquals(16, pinnedDirectMemory.get());

        byteBuf1.release();
        assertEquals(0, pinnedDirectMemory.get());
    }

    @Test
    public void testGlobalPinnedDirectMemoryWithHeapAllocator() throws Exception {
        Field field = PlatformDependent.class.getDeclaredField("PINNED_DIRECT_MEMORY_COUNTER");
        field.setAccessible(true);
        AtomicLong pinnedDirectMemory = (AtomicLong) field.get(null);
        if (pinnedDirectMemory == null) {
            return;
        }

        UnpooledByteBufAllocator allocator = newUnpooledAllocator();
        ByteBuf byteBuf = allocator.heapBuffer(16);

        //heap memory allocation didn't increase `PINNED_DIRECT_MEMORY_COUNTER`
        assertEquals(0, pinnedDirectMemory.get());

        byteBuf.release();
        assertEquals(0, pinnedDirectMemory.get());
    }
}
