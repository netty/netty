/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class PoolArenaTest {

    @Test
    public void testNormalizeCapacity() throws Exception {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, 0, 0, 9, 999999, 0);
        int[] reqCapacities = {0, 15, 510, 1024, 1023, 1025};
        int[] expectedResult = {0, 16, 512, 1024, 1024, 2048};
        for (int i = 0; i < reqCapacities.length; i ++) {
            Assert.assertEquals(expectedResult[i], arena.normalizeCapacity(reqCapacities[i]));
        }
    }

    @Test
    public void testNormalizeAlignedCapacity() throws Exception {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, 0, 0, 9, 999999, 64);
        int[] reqCapacities = {0, 15, 510, 1024, 1023, 1025};
        int[] expectedResult = {0, 64, 512, 1024, 1024, 2048};
        for (int i = 0; i < reqCapacities.length; i ++) {
            Assert.assertEquals(expectedResult[i], arena.normalizeCapacity(reqCapacities[i]));
        }
    }

    @Test
    public void testDirectArenaOffsetCacheLine() throws Exception {
        int capacity = 5;
        int alignment = 128;

        for (int i = 0; i < 1000; i++) {
            ByteBuffer bb = PlatformDependent.useDirectBufferNoCleaner()
                    ? PlatformDependent.allocateDirectNoCleaner(capacity + alignment)
                    : ByteBuffer.allocateDirect(capacity + alignment);

            PoolArena.DirectArena arena = new PoolArena.DirectArena(null, 0, 0, 9, 9, alignment);
            int offset = arena.offsetCacheLine(bb);
            long address = PlatformDependent.directBufferAddress(bb);

            Assert.assertEquals(0, (offset + address) & (alignment - 1));
            PlatformDependent.freeDirectBuffer(bb);
        }
    }

    @Test
    public final void testAllocationCounter() {
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

        // create tiny buffer
        final ByteBuf b1 = allocator.directBuffer(24);
        // create small buffer
        final ByteBuf b2 = allocator.directBuffer(800);
        // create normal buffer
        final ByteBuf b3 = allocator.directBuffer(8192 * 2);

        Assert.assertNotNull(b1);
        Assert.assertNotNull(b2);
        Assert.assertNotNull(b3);

        // then release buffer to deallocated memory while threadlocal cache has been disabled
        // allocations counter value must equals deallocations counter value
        Assert.assertTrue(b1.release());
        Assert.assertTrue(b2.release());
        Assert.assertTrue(b3.release());

        Assert.assertTrue(allocator.directArenas().size() >= 1);
        final PoolArenaMetric metric = allocator.directArenas().get(0);

        Assert.assertEquals(3, metric.numDeallocations());
        Assert.assertEquals(3, metric.numAllocations());

        Assert.assertEquals(1, metric.numTinyDeallocations());
        Assert.assertEquals(1, metric.numTinyAllocations());
        Assert.assertEquals(1, metric.numSmallDeallocations());
        Assert.assertEquals(1, metric.numSmallAllocations());
        Assert.assertEquals(1, metric.numNormalDeallocations());
        Assert.assertEquals(1, metric.numNormalAllocations());
    }
}
