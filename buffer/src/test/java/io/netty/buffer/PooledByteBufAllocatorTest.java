/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.SystemPropertyUtil;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PooledByteBufAllocatorTest {

    @Test
    public void testFreePoolChunk() {
        int chunkSize = 16 * 1024 * 1024;
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 0, 8192, 11, 0, 0, 0);
        ByteBuf buffer = allocator.heapBuffer(chunkSize);
        List<PoolArenaMetric> arenas = allocator.heapArenas();
        assertEquals(1, arenas.size());
        List<PoolChunkListMetric> lists = arenas.get(0).chunkLists();
        assertEquals(6, lists.size());

        assertFalse(lists.get(0).iterator().hasNext());
        assertFalse(lists.get(1).iterator().hasNext());
        assertFalse(lists.get(2).iterator().hasNext());
        assertFalse(lists.get(3).iterator().hasNext());
        assertFalse(lists.get(4).iterator().hasNext());

        // Must end up in the 6th PoolChunkList
        assertTrue(lists.get(5).iterator().hasNext());
        assertTrue(buffer.release());

        // Should be completely removed and so all PoolChunkLists must be empty
        assertFalse(lists.get(0).iterator().hasNext());
        assertFalse(lists.get(1).iterator().hasNext());
        assertFalse(lists.get(2).iterator().hasNext());
        assertFalse(lists.get(3).iterator().hasNext());
        assertFalse(lists.get(4).iterator().hasNext());
        assertFalse(lists.get(5).iterator().hasNext());
    }

    // The ThreadDeathWatcher sleeps 1s, give it double that time.
    @Test (timeout = 2000)
    public void testThreadCacheDestroyedByThreadDeathWatcher() {
        int numArenas = 11;
        final PooledByteBufAllocator allocator =
            new PooledByteBufAllocator(numArenas, numArenas, 8192, 1);

        final AtomicBoolean threadCachesCreated = new AtomicBoolean(true);

        for (int i = 0; i < numArenas; i++) {
            new FastThreadLocalThread(new Runnable() {
                @Override
                public void run() {
                    ByteBuf buf = allocator.newHeapBuffer(1024, 1024);
                    for (int i = 0; i < buf.capacity(); i++) {
                        buf.writeByte(0);
                    }

                    // Make sure that thread caches are actually created,
                    // so that down below we are not testing for zero
                    // thread caches without any of them ever having been initialized.
                    if (allocator.numThreadLocalCaches() == 0) {
                        threadCachesCreated.set(false);
                    }

                    buf.release();
                }
            }).start();
        }

        // Wait for the ThreadDeathWatcher to have destroyed all thread caches
        while (allocator.numThreadLocalCaches() > 0) {
            LockSupport.parkNanos(MILLISECONDS.toNanos(100));
        }

        assertTrue(threadCachesCreated.get());
    }

    @Test
    public void testNumThreadCachesWithNoDirectArenas() {
        int numHeapArenas = 1;
        final PooledByteBufAllocator allocator =
            new PooledByteBufAllocator(numHeapArenas, 0, 8192, 1);

        CountDownLatch tcache0 = createNewThreadCache(allocator);
        assertEquals(1, allocator.numThreadLocalCaches());

        CountDownLatch tcache1 = createNewThreadCache(allocator);
        assertEquals(2, allocator.numThreadLocalCaches());

        destroyThreadCache(tcache0);
        assertEquals(1, allocator.numThreadLocalCaches());

        destroyThreadCache(tcache1);
        assertEquals(0, allocator.numThreadLocalCaches());
    }

    @Test
    public void testThreadCacheToArenaMappings() throws InterruptedException {
        int numArenas = 2;
        final PooledByteBufAllocator allocator =
            new PooledByteBufAllocator(numArenas, numArenas, 8192, 1);

        CountDownLatch tcache0 = createNewThreadCache(allocator);
        CountDownLatch tcache1 = createNewThreadCache(allocator);
        assertEquals(2, allocator.numThreadLocalCaches());
        destroyThreadCache(tcache1);
        assertEquals(1, allocator.numThreadLocalCaches());

        CountDownLatch tcache2 = createNewThreadCache(allocator);
        assertEquals(2, allocator.numThreadLocalCaches());

        destroyThreadCache(tcache0);
        assertEquals(1, allocator.numThreadLocalCaches());

        destroyThreadCache(tcache2);
        assertEquals(0, allocator.numThreadLocalCaches());
    }

    private static void destroyThreadCache(CountDownLatch tcache) {
        tcache.countDown();
        LockSupport.parkNanos(MILLISECONDS.toNanos(100));
    }

    private static CountDownLatch createNewThreadCache(final PooledByteBufAllocator allocator) {
        final CountDownLatch latch = new CountDownLatch(1);

        Thread t = new FastThreadLocalThread(new Runnable() {

            @Override
            public void run() {
                ByteBuf buf = allocator.newHeapBuffer(1024, 1024);
                for (int i = 0; i < buf.capacity(); i++) {
                    buf.writeByte(0);
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }

                buf.release();

                FastThreadLocal.removeAll();
            }
        });
        t.start();

        // Wait a bit for the thread & thread cache to be created.
        LockSupport.parkNanos(MILLISECONDS.toNanos(100));

        return latch;
    }

    @Test
    public void testConcurrentUsage() throws Throwable {
        long runningTime = MILLISECONDS.toNanos(SystemPropertyUtil.getLong(
                "io.netty.buffer.PooledByteBufAllocatorTest.testConcurrentUsageTime", 15000));

        // We use no caches and only one arena to maximize the chance of hitting the race-condition we
        // had before.
        ByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 1, 8192, 11, 0, 0, 0);
        List<AllocationThread> threads = new ArrayList<AllocationThread>();
        try {
            for (int i = 0; i < 512; i++) {
                AllocationThread thread = new AllocationThread(allocator);
                thread.start();
                threads.add(thread);
            }

            long start = System.nanoTime();
            while (!isExpired(start, runningTime)) {
                checkForErrors(threads);
                Thread.sleep(100);
            }
        } finally {
            for (AllocationThread t : threads) {
                t.finish();
            }
        }
    }

    private static boolean isExpired(long start, long expireTime) {
        return System.nanoTime() - start > expireTime;
    }

    private static void checkForErrors(List<AllocationThread> threads) throws Throwable {
        for (AllocationThread t : threads) {
            if (t.isFinished()) {
                t.checkForError();
            }
        }
    }

    private static final class AllocationThread extends Thread {

        private static final int[] ALLOCATION_SIZES = new int[16 * 1024];
        static {
            for (int i = 0; i < ALLOCATION_SIZES.length; i++) {
                ALLOCATION_SIZES[i] = i;
            }
        }

        private final CountDownLatch latch = new CountDownLatch(1);
        private final Queue<ByteBuf> buffers = new ArrayDeque<ByteBuf>(10);
        private final ByteBufAllocator allocator;
        private volatile boolean finished;
        private volatile Throwable error;

        public AllocationThread(ByteBufAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public void run() {
            try {
                int idx = 0;
                while (!finished) {
                    for (int i = 0; i < 10; i++) {
                        buffers.add(allocator.directBuffer(
                                ALLOCATION_SIZES[Math.abs(idx++ % ALLOCATION_SIZES.length)],
                                Integer.MAX_VALUE));
                    }
                    releaseBuffers();
                }
            } catch (Throwable cause) {
                error = cause;
                finished = true;
            } finally {
                releaseBuffers();
            }
            latch.countDown();
        }

        private void releaseBuffers() {
            for (;;) {
                ByteBuf buf = buffers.poll();
                if (buf == null) {
                    break;
                }
                buf.release();
            }
        }

        public boolean isFinished() {
            return finished;
        }

        public void finish() throws Throwable {
            try {
                finished = true;
                latch.await();
                checkForError();
            } finally {
                releaseBuffers();
            }
        }

        public void checkForError() throws Throwable {
            if (error != null) {
                throw error;
            }
        }
    }
}
