/*
 * Copyright 2012 The Netty Project
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

public class PoolArenaTest {

    private static final int PAGE_SIZE = 8192;
    private static final int PAGE_SHIFTS = 11;
    //chunkSize = pageSize * (2 ^ pageShifts)
    private static final int CHUNK_SIZE = 16777216;

    @Test
    public void testNormalizeCapacity() {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, PAGE_SIZE, PAGE_SHIFTS, CHUNK_SIZE, 0);
        int[] reqCapacities = {0, 15, 510, 1024, 1023, 1025};
        int[] expectedResult = {16, 16, 512, 1024, 1024, 1280};
        for (int i = 0; i < reqCapacities.length; i ++) {
            assertEquals(expectedResult[i], arena.sizeIdx2size(arena.size2SizeIdx(reqCapacities[i])));
        }
    }

    @Test
    public void testNormalizeAlignedCapacity() {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, PAGE_SIZE, PAGE_SHIFTS, CHUNK_SIZE, 64);
        int[] reqCapacities = {0, 15, 510, 1024, 1023, 1025};
        int[] expectedResult = {64, 64, 512, 1024, 1024, 1280};
        for (int i = 0; i < reqCapacities.length; i ++) {
            assertEquals(expectedResult[i], arena.sizeIdx2size(arena.size2SizeIdx(reqCapacities[i])));
        }
    }

    @Test
    public void testSize2SizeIdx() {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, PAGE_SIZE, PAGE_SHIFTS, CHUNK_SIZE, 0);

        for (int sz = 0; sz <= CHUNK_SIZE; sz++) {
            int sizeIdx = arena.size2SizeIdx(sz);
            assertTrue(sz <= arena.sizeIdx2size(sizeIdx));
            if (sizeIdx > 0) {
                assertTrue(sz > arena.sizeIdx2size(sizeIdx - 1));
            }
        }
    }

    @Test
    public void testPages2PageIdx() {
        int pageShifts = PAGE_SHIFTS;

        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, PAGE_SIZE, PAGE_SHIFTS, CHUNK_SIZE, 0);

        int maxPages = CHUNK_SIZE >> pageShifts;
        for (int pages = 1; pages <= maxPages; pages++) {
            int pageIdxFloor = arena.pages2pageIdxFloor(pages);
            assertTrue(pages << pageShifts >= arena.pageIdx2size(pageIdxFloor));
            if (pageIdxFloor > 0 && pages < maxPages) {
                assertTrue(pages << pageShifts < arena.pageIdx2size(pageIdxFloor + 1));
            }

            int pageIdxCeiling = arena.pages2pageIdx(pages);
            assertTrue(pages << pageShifts <= arena.pageIdx2size(pageIdxCeiling));
            if (pageIdxCeiling > 0) {
                assertTrue(pages << pageShifts > arena.pageIdx2size(pageIdxCeiling - 1));
            }
        }
    }

    @Test
    public void testSizeIdx2size() {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, PAGE_SIZE, PAGE_SHIFTS, CHUNK_SIZE, 0);
        for (int i = 0; i < arena.nSizes; i++) {
            assertEquals(arena.sizeIdx2sizeCompute(i), arena.sizeIdx2size(i));
        }
    }

    @Test
    public void testPageIdx2size() {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, PAGE_SIZE, PAGE_SHIFTS, CHUNK_SIZE, 0);
        for (int i = 0; i < arena.nPSizes; i++) {
            assertEquals(arena.pageIdx2sizeCompute(i), arena.pageIdx2size(i));
        }
    }

    @Test
    public void testAllocationCounter() {
        final PooledByteBufAllocator allocator = new PooledByteBufAllocator(
                true,   // preferDirect
                0,      // nHeapArena
                1,      // nDirectArena
                8192,   // pageSize
                11,     // maxOrder
                0,      // tinyCacheSize
                0,      // smallCacheSize
                0,      // normalCacheSize
                true    // useCacheForAllThreads
                );

        // create small buffer
        final ByteBuf b1 = allocator.directBuffer(800);
        // create normal buffer
        final ByteBuf b2 = allocator.directBuffer(8192 * 5);

        assertNotNull(b1);
        assertNotNull(b2);

        // then release buffer to deallocated memory while threadlocal cache has been disabled
        // allocations counter value must equals deallocations counter value
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

    @Test
    public void testDirectArenaMemoryCopy() {
        ByteBuf src = PooledByteBufAllocator.DEFAULT.directBuffer(512);
        ByteBuf dst = PooledByteBufAllocator.DEFAULT.directBuffer(512);

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
