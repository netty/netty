/*
 * Copyright 2021 The Netty Project
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AlignedPooledByteBufAllocatorTest extends PooledByteBufAllocatorTest {
    @Override
    protected PooledByteBufAllocator newAllocator(boolean preferDirect) {
        int directMemoryCacheAlignment = 1;
        return new PooledByteBufAllocator(
                preferDirect,
                PooledByteBufAllocator.defaultNumHeapArena(),
                PooledByteBufAllocator.defaultNumDirectArena(),
                PooledByteBufAllocator.defaultPageSize(),
                11,
                PooledByteBufAllocator.defaultSmallCacheSize(),
                64,
                PooledByteBufAllocator.defaultUseCacheForAllThreads(),
                directMemoryCacheAlignment);
    }

    // https://github.com/netty/netty/issues/11955
    @Test
    public void testCorrectElementSize() {
        ByteBufAllocator allocator = new PooledByteBufAllocator(
                true,
                PooledByteBufAllocator.defaultNumHeapArena(),
                PooledByteBufAllocator.defaultNumDirectArena(),
                PooledByteBufAllocator.defaultPageSize(),
                11,
                PooledByteBufAllocator.defaultSmallCacheSize(),
                64,
                PooledByteBufAllocator.defaultUseCacheForAllThreads(),
                64);

        ByteBuf a = allocator.directBuffer(0, 16384);
        ByteBuf b = allocator.directBuffer(0, 16384);
        a.capacity(16);
        assertEquals(16, a.capacity());
        b.capacity(16);
        assertEquals(16, b.capacity());
        a.capacity(17);
        assertEquals(17, a.capacity());
        b.capacity(18);
        assertEquals(18, b.capacity());
        assertTrue(a.release());
        assertTrue(b.release());
    }
}
