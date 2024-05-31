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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class AlignedPooledByteBufAllocatorTest extends PooledByteBufAllocatorTest {
    @Override
    protected PooledByteBufAllocator newAllocator(boolean preferDirect) {
        assumeTrue(PooledByteBufAllocator.isDirectMemoryCacheAlignmentSupported());
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
        assumeTrue(PooledByteBufAllocator.isDirectMemoryCacheAlignmentSupported());
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

    @Test
    public void testDirectSubpageReleaseLock() {
        assumeTrue(PooledByteBufAllocator.isDirectMemoryCacheAlignmentSupported());
        int initialCapacity = 0;
        int directMemoryCacheAlignment = 32;
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(
                true,
                0,
                1,
                PooledByteBufAllocator.defaultPageSize(),
                PooledByteBufAllocator.defaultMaxOrder(),
                0,
                0,
                false,
                directMemoryCacheAlignment);

        final PooledByteBuf<?> byteBuf = pooledByteBuf(allocator.directBuffer(initialCapacity, 16));
        // Get the smallSubpagePools[] array in arena.
        @SuppressWarnings("unchecked")
        PoolSubpage<byte[]>[] smallSubpagePools = (PoolSubpage<byte[]>[]) byteBuf.chunk.arena.smallSubpagePools;
        PoolSubpage<byte[]> head = null;
        for (PoolSubpage<byte[]> subpage : smallSubpagePools) {
            if (subpage.next != subpage) {
                // Find the head subpage which the byteBuf belongs to.
                head = subpage;
                break;
            }
        }
        assertNotNull(head);
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                // Because the head subpage was already locked in the main thread, so this should hang and wait.
                byteBuf.release();
            }
        });
        t1.setDaemon(true);
        // Intentionally lock the head subpage in main thread.
        head.lock();
        try {
            t1.start();
            long start = System.nanoTime();
            while (!head.lock.hasQueuedThread(t1)) {
                if ((System.nanoTime() - start) > TimeUnit.SECONDS.toNanos(3)) {
                    break;
                }
            }
            assertTrue(head.lock.hasQueuedThread(t1),
                    "The t1 thread should still be waiting for the head lock.");
        } finally {
            head.unlock();
        }
    }

    private static PooledByteBuf<?> pooledByteBuf(ByteBuf buffer) {
        // might need to unwrap if swapped (LE) and/or leak-aware-wrapped
        while (!(buffer instanceof PooledByteBuf)) {
            buffer = buffer.unwrap();
        }
        return (PooledByteBuf<?>) buffer;
    }
}
