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

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoThreadLocalPoolArenaTest extends PoolArenaTest {

    @Override
    protected PoolArena<ByteBuffer> newDirectArena(SizeClasses sc) {
        return new PoolArena.DirectArena(null, sc, false);
    }

    @Override
    @Test
    public void testAllocationCounter() {
        final PooledByteBufAllocator allocator = new PooledByteBufAllocator(
                true,   // preferDirect
                0,      // nHeapArena
                1,      // nDirectArena
                8192,   // pageSize
                11,     // maxOrder
                10,     // smallCacheSize
                10,     // normalCacheSize
                false,  // useCacheForAllThreads
                0,      // directMemoryCacheAlignment
                false   // useThreadLocal
        );

        // create small buffer
        final ByteBuf b1 = allocator.directBuffer(800);
        // create normal buffer
        final ByteBuf b2 = allocator.directBuffer(8192 * 5);

        assertNotNull(b1);
        assertNotNull(b2);

        // then release buffer to deallocated memory while thread-local has been disabled
        // allocations counter value must equals deallocations counter value.
        assertTrue(b1.release());
        assertTrue(b2.release());

        assertTrue(allocator.directArenas().size() >= 1);
        final PoolArenaMetric metric = allocator.directArenas().get(0);

        assertEquals(2, metric.numDeallocations());
        assertEquals(2, metric.numAllocations());

        assertEquals(1, metric.numSmallDeallocations());
        assertEquals(1, metric.numSmallAllocations());
        assertEquals(1, metric.numNormalDeallocations());
        assertEquals(1, metric.numNormalAllocations());
    }

    @Override
    @Test
    public void testDirectArenaMemoryCopy() {
        ByteBuf src = PooledByteBufAllocator.DEFAULT_NO_TL.directBuffer(512);
        ByteBuf dst = PooledByteBufAllocator.DEFAULT_NO_TL.directBuffer(512);

        PooledByteBuf<ByteBuffer> pooledSrc = unwrapIfNeeded(src);
        PooledByteBuf<ByteBuffer> pooledDst = unwrapIfNeeded(dst);

        // This causes the internal reused ByteBuffer duplicate limit to be set to 128
        pooledDst.writeBytes(ByteBuffer.allocate(128));
        // Ensure internal ByteBuffer duplicate limit is properly reset (used in memoryCopy non-Unsafe case)
        pooledDst.chunk.arena.memoryCopy(pooledSrc.memory, 0, pooledDst, 512);

        src.release();
        dst.release();
    }

    @SuppressWarnings("unchecked")
    private PooledByteBuf<ByteBuffer> unwrapIfNeeded(ByteBuf buf) {
        return (PooledByteBuf<ByteBuffer>) (buf instanceof PooledByteBuf ? buf : buf.unwrap());
    }
}
