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

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.nio.ByteOrder;

import static org.junit.Assert.assertSame;

public class PooledAlignedBigEndianDirectByteBufTest extends PooledBigEndianDirectByteBufTest {
    private static final int directMemoryCacheAlignment = 1;
    private static PooledByteBufAllocator allocator;

    @BeforeClass
    public static void setUpAllocator() {
        allocator = new PooledByteBufAllocator(
                true,
                PooledByteBufAllocator.defaultNumHeapArena(),
                PooledByteBufAllocator.defaultNumDirectArena(),
                PooledByteBufAllocator.defaultPageSize(),
                11,
                PooledByteBufAllocator.defaultSmallCacheSize(),
                64,
                PooledByteBufAllocator.defaultUseCacheForAllThreads(),
                directMemoryCacheAlignment);
    }

    @AfterClass
    public static void releaseAllocator() {
        allocator = null;
    }

    @Override
    protected ByteBuf alloc(int length, int maxCapacity) {
        ByteBuf buffer = allocator.directBuffer(length, maxCapacity);
        assertSame(ByteOrder.BIG_ENDIAN, buffer.order());
        return buffer;
    }
}
