/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static io.netty.buffer.PoolChunk.runOffset;
import static io.netty.buffer.PoolChunk.runPages;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PooledByteBufAllocatorTest extends AbstractByteBufAllocatorTest<PooledByteBufAllocator> {

    @Override
    protected PooledByteBufAllocator newAllocator(boolean preferDirect) {
        return new PooledByteBufAllocator(preferDirect);
    }

    @Override
    protected PooledByteBufAllocator newUnpooledAllocator() {
        return new PooledByteBufAllocator(0, 0, 8192, 1);
    }

    @Override
    protected long expectedUsedMemory(PooledByteBufAllocator allocator, int capacity) {
        return allocator.metric().chunkSize();
    }

    @Override
    protected long expectedUsedMemoryAfterRelease(PooledByteBufAllocator allocator, int capacity) {
        // This is the case as allocations will start in qInit and chunks in qInit will never be released until
        // these are moved to q000.
        // See https://www.bsdcan.org/2006/papers/jemalloc.pdf
        return allocator.metric().chunkSize();
    }

    @Override
    protected void trimCaches(PooledByteBufAllocator allocator) {
        allocator.trimCurrentThreadCache();
    }

    @Test
    public void testTrim() {
        PooledByteBufAllocator allocator = newAllocator(true);

        // Should return false as we never allocated from this thread yet.
        assertFalse(allocator.trimCurrentThreadCache());

        ByteBuf directBuffer = allocator.directBuffer();

        assertTrue(directBuffer.release());

        // Should return true now a cache exists for the calling thread.
        assertTrue(allocator.trimCurrentThreadCache());
    }

    @Test
    public void testPooledUnsafeHeapBufferAndUnsafeDirectBuffer() {
        PooledByteBufAllocator allocator = newAllocator(true);
        ByteBuf directBuffer = allocator.directBuffer();
        assertInstanceOf(directBuffer,
                PlatformDependent.hasUnsafe() ? PooledUnsafeDirectByteBuf.class : PooledDirectByteBuf.class);
        directBuffer.release();

        ByteBuf heapBuffer = allocator.heapBuffer();
        assertInstanceOf(heapBuffer,
                PlatformDependent.hasUnsafe() ? PooledUnsafeHeapByteBuf.class : PooledHeapByteBuf.class);
        heapBuffer.release();
    }

    @Test
    public void testIOBuffersAreDirectWhenUnsafeAvailableOrDirectBuffersPooled() {
        PooledByteBufAllocator allocator = newAllocator(true);
        ByteBuf ioBuffer = allocator.ioBuffer();

        assertTrue(ioBuffer.isDirect());
        ioBuffer.release();

        PooledByteBufAllocator unpooledAllocator = newUnpooledAllocator();
        ioBuffer = unpooledAllocator.ioBuffer();

        if (PlatformDependent.hasUnsafe()) {
            assertTrue(ioBuffer.isDirect());
        } else {
            assertFalse(ioBuffer.isDirect());
        }
        ioBuffer.release();
    }

    @Test
    public void testWithoutUseCacheForAllThreads() {
        assertThat(Thread.currentThread()).isNotInstanceOf(FastThreadLocalThread.class);

        PooledByteBufAllocator pool = new PooledByteBufAllocator(
                /*preferDirect=*/ false,
                /*nHeapArena=*/ 1,
                /*nDirectArena=*/ 1,
                /*pageSize=*/8192,
                /*maxOrder=*/ 9,
                /*tinyCacheSize=*/ 0,
                /*smallCacheSize=*/ 0,
                /*normalCacheSize=*/ 0,
                /*useCacheForAllThreads=*/ false);
        ByteBuf buf = pool.buffer(1);
        buf.release();
    }

    @Test
    public void testArenaMetricsNoCache() {
        testArenaMetrics0(new PooledByteBufAllocator(true, 2, 2, 8192, 9, 0, 0, 0), 100, 0, 100, 100);
    }

    @Test
    public void testArenaMetricsCache() {
        testArenaMetrics0(new PooledByteBufAllocator(true, 2, 2, 8192, 9, 1000, 1000, 1000, true, 0), 100, 1, 1, 0);
    }

    @Test
    public void testArenaMetricsNoCacheAlign() {
        assumeTrue(PooledByteBufAllocator.isDirectMemoryCacheAlignmentSupported());
        testArenaMetrics0(new PooledByteBufAllocator(true, 2, 2, 8192, 9, 0, 0, 0, true, 64), 100, 0, 100, 100);
    }

    @Test
    public void testArenaMetricsCacheAlign() {
        assumeTrue(PooledByteBufAllocator.isDirectMemoryCacheAlignmentSupported());
        testArenaMetrics0(new PooledByteBufAllocator(true, 2, 2, 8192, 9, 1000, 1000, 1000, true, 64), 100, 1, 1, 0);
    }

    private static void testArenaMetrics0(
            PooledByteBufAllocator allocator, int num, int expectedActive, int expectedAlloc, int expectedDealloc) {
        for (int i = 0; i < num; i++) {
            assertTrue(allocator.directBuffer().release());
            assertTrue(allocator.heapBuffer().release());
        }

        assertArenaMetrics(allocator.metric().directArenas(), expectedActive, expectedAlloc, expectedDealloc);
        assertArenaMetrics(allocator.metric().heapArenas(), expectedActive, expectedAlloc, expectedDealloc);
    }

    private static void assertArenaMetrics(
            List<PoolArenaMetric> arenaMetrics, int expectedActive, int expectedAlloc, int expectedDealloc) {
        long active = 0;
        long alloc = 0;
        long dealloc = 0;
        for (PoolArenaMetric arena : arenaMetrics) {
            active += arena.numActiveAllocations();
            alloc += arena.numAllocations();
            dealloc += arena.numDeallocations();
        }
        assertEquals(expectedActive, active);
        assertEquals(expectedAlloc, alloc);
        assertEquals(expectedDealloc, dealloc);
    }

    @Test
    public void testPoolChunkListMetric() {
        for (PoolArenaMetric arenaMetric: PooledByteBufAllocator.DEFAULT.metric().heapArenas()) {
            assertPoolChunkListMetric(arenaMetric);
        }
    }

    private static void assertPoolChunkListMetric(PoolArenaMetric arenaMetric) {
        List<PoolChunkListMetric> lists = arenaMetric.chunkLists();
        assertEquals(6, lists.size());
        assertPoolChunkListMetric(lists.get(0), 1, 25);
        assertPoolChunkListMetric(lists.get(1), 1, 50);
        assertPoolChunkListMetric(lists.get(2), 25, 75);
        assertPoolChunkListMetric(lists.get(4), 75, 100);
        assertPoolChunkListMetric(lists.get(5), 100, 100);
    }

    private static void assertPoolChunkListMetric(PoolChunkListMetric m, int min, int max) {
        assertEquals(min, m.minUsage());
        assertEquals(max, m.maxUsage());
    }

    @Test
    public void testSmallSubpageMetric() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 1, 8192, 9, 0, 0, 0);
        ByteBuf buffer = allocator.heapBuffer(500);
        try {
            PoolArenaMetric metric = allocator.metric().heapArenas().get(0);
            PoolSubpageMetric subpageMetric = metric.smallSubpages().get(0);
            assertEquals(1, subpageMetric.maxNumElements() - subpageMetric.numAvailable());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testAllocNotNull() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 1, 8192, 9, 0, 0, 0);
        // Huge allocation
        testAllocNotNull(allocator, allocator.metric().chunkSize() + 1);
        // Normal allocation
        testAllocNotNull(allocator, 1024);
        // Small allocation
        testAllocNotNull(allocator, 512);
        testAllocNotNull(allocator, 1);
    }

    private static void testAllocNotNull(PooledByteBufAllocator allocator, int capacity) {
        ByteBuf buffer = allocator.heapBuffer(capacity);
        assertNotNull(buffer.alloc());
        assertTrue(buffer.release());
        assertNotNull(buffer.alloc());
    }

    @Test
    public void testFreePoolChunk() {
        int chunkSize = 16 * 1024 * 1024;
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 0, 8192, 11, 0, 0, 0);
        ByteBuf buffer = allocator.heapBuffer(chunkSize);
        List<PoolArenaMetric> arenas = allocator.metric().heapArenas();
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

    @Test
    public void testCollapse() {
        int pageSize = 8192;
        //no cache
        ByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 1, 8192, 9, 0, 0, 0);

        ByteBuf b1 = allocator.buffer(pageSize * 4);
        ByteBuf b2 = allocator.buffer(pageSize * 5);
        ByteBuf b3 = allocator.buffer(pageSize * 6);

        b2.release();
        b3.release();

        ByteBuf b4 = allocator.buffer(pageSize * 10);

        PooledByteBuf<ByteBuffer> b = unwrapIfNeeded(b4);

        //b2 and b3 are collapsed, b4 should start at offset 4
        assertEquals(4, runOffset(b.handle));
        assertEquals(10, runPages(b.handle));

        b1.release();
        b4.release();

        //all ByteBuf are collapsed, b5 should start at offset 0
        ByteBuf b5 = allocator.buffer(pageSize * 20);
        b = unwrapIfNeeded(b5);

        assertEquals(0, runOffset(b.handle));
        assertEquals(20, runPages(b.handle));

        b5.release();
    }

    @Test
    public void testAllocateSmallOffset() {
        int pageSize = 8192;
        ByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 1, 8192, 9, 0, 0, 0);

        int size = pageSize * 5;

        ByteBuf[] bufs = new ByteBuf[10];
        for (int i = 0; i < 10; i++) {
            bufs[i] = allocator.buffer(size);
        }

        for (int i = 0; i < 5; i++) {
            bufs[i].release();
        }

        //make sure we always allocate runs with small offset
        for (int i = 0; i < 5; i++) {
            ByteBuf buf = allocator.buffer(size);
            PooledByteBuf<ByteBuffer> unwrapedBuf = unwrapIfNeeded(buf);
            assertEquals(runOffset(unwrapedBuf.handle), i * 5);
            bufs[i] = buf;
        }

        //release at reverse order
        for (int i = 10 - 1; i >= 5; i--) {
            bufs[i].release();
        }

        for (int i = 5; i < 10; i++) {
            ByteBuf buf = allocator.buffer(size);
            PooledByteBuf<ByteBuffer> unwrapedBuf = unwrapIfNeeded(buf);
            assertEquals(runOffset(unwrapedBuf.handle), i * 5);
            bufs[i] = buf;
        }

        for (int i = 0; i < 10; i++) {
            bufs[i].release();
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testThreadCacheDestroyedByThreadCleaner() throws InterruptedException {
        testThreadCacheDestroyed(false);
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testThreadCacheDestroyedAfterExitRun() throws InterruptedException {
        testThreadCacheDestroyed(true);
    }

    private static void testThreadCacheDestroyed(boolean useRunnable) throws InterruptedException {
        int numArenas = 11;
        final PooledByteBufAllocator allocator =
            new PooledByteBufAllocator(numArenas, numArenas, 8192, 1);

        final AtomicBoolean threadCachesCreated = new AtomicBoolean(true);

        final Runnable task = new Runnable() {
            @Override
            public void run() {
                ByteBuf buf = allocator.newHeapBuffer(1024, 1024);
                for (int i = 0; i < buf.capacity(); i++) {
                    buf.writeByte(0);
                }

                // Make sure that thread caches are actually created,
                // so that down below we are not testing for zero
                // thread caches without any of them ever having been initialized.
                if (allocator.metric().numThreadLocalCaches() == 0) {
                    threadCachesCreated.set(false);
                }

                buf.release();
            }
        };

        for (int i = 0; i < numArenas; i++) {
            final FastThreadLocalThread thread;
            if (useRunnable) {
                thread = new FastThreadLocalThread(task);
                assertTrue(thread.willCleanupFastThreadLocals());
            } else {
                thread = new FastThreadLocalThread() {
                    @Override
                    public void run() {
                        task.run();
                    }
                };
                assertFalse(thread.willCleanupFastThreadLocals());
            }
            thread.start();
            thread.join();
        }

        // Wait for the ThreadDeathWatcher to have destroyed all thread caches
        while (allocator.metric().numThreadLocalCaches() > 0) {
            // Signal we want to have a GC run to ensure we can process our ThreadCleanerReference
            System.gc();
            System.runFinalization();
            LockSupport.parkNanos(MILLISECONDS.toNanos(100));
        }

        assertTrue(threadCachesCreated.get());
    }

    @Test
    @Timeout(value = 3000, unit = MILLISECONDS)
    public void testNumThreadCachesWithNoDirectArenas() throws InterruptedException {
        int numHeapArenas = 1;
        final PooledByteBufAllocator allocator =
            new PooledByteBufAllocator(numHeapArenas, 0, 8192, 1);

        ThreadCache tcache0 = createNewThreadCache(allocator, false);
        assertEquals(1, allocator.metric().numThreadLocalCaches());

        ThreadCache tcache1 = createNewThreadCache(allocator, false);
        assertEquals(2, allocator.metric().numThreadLocalCaches());

        tcache0.destroy();
        assertEquals(1, allocator.metric().numThreadLocalCaches());

        tcache1.destroy();
        assertEquals(0, allocator.metric().numThreadLocalCaches());
    }

    @Test
    @Timeout(value = 3000, unit = MILLISECONDS)
    public void testNumThreadCachesAccountForDirectAndHeapArenas() throws InterruptedException {
        int numHeapArenas = 1;
        final PooledByteBufAllocator allocator =
                new PooledByteBufAllocator(numHeapArenas, 0, 8192, 1);

        ThreadCache tcache0 = createNewThreadCache(allocator, false);
        assertEquals(1, allocator.metric().numThreadLocalCaches());

        ThreadCache tcache1 = createNewThreadCache(allocator, true);
        assertEquals(2, allocator.metric().numThreadLocalCaches());

        tcache0.destroy();
        assertEquals(1, allocator.metric().numThreadLocalCaches());

        tcache1.destroy();
        assertEquals(0, allocator.metric().numThreadLocalCaches());
    }

    @Test
    @Timeout(value = 3000, unit = MILLISECONDS)
    public void testThreadCacheToArenaMappings() throws InterruptedException {
        int numArenas = 2;
        final PooledByteBufAllocator allocator =
            new PooledByteBufAllocator(numArenas, numArenas, 8192, 1);

        ThreadCache tcache0 = createNewThreadCache(allocator, false);
        ThreadCache tcache1 = createNewThreadCache(allocator, false);
        assertEquals(2, allocator.metric().numThreadLocalCaches());
        assertEquals(1, allocator.metric().heapArenas().get(0).numThreadCaches());
        assertEquals(1, allocator.metric().heapArenas().get(1).numThreadCaches());
        assertEquals(1, allocator.metric().directArenas().get(0).numThreadCaches());
        assertEquals(1, allocator.metric().directArenas().get(0).numThreadCaches());

        tcache1.destroy();

        assertEquals(1, allocator.metric().numThreadLocalCaches());
        assertEquals(1, allocator.metric().heapArenas().get(0).numThreadCaches());
        assertEquals(0, allocator.metric().heapArenas().get(1).numThreadCaches());
        assertEquals(1, allocator.metric().directArenas().get(0).numThreadCaches());
        assertEquals(0, allocator.metric().directArenas().get(1).numThreadCaches());

        ThreadCache tcache2 = createNewThreadCache(allocator, false);
        assertEquals(2, allocator.metric().numThreadLocalCaches());
        assertEquals(1, allocator.metric().heapArenas().get(0).numThreadCaches());
        assertEquals(1, allocator.metric().heapArenas().get(1).numThreadCaches());
        assertEquals(1, allocator.metric().directArenas().get(0).numThreadCaches());
        assertEquals(1, allocator.metric().directArenas().get(1).numThreadCaches());

        tcache0.destroy();
        assertEquals(1, allocator.metric().numThreadLocalCaches());

        tcache2.destroy();
        assertEquals(0, allocator.metric().numThreadLocalCaches());
        assertEquals(0, allocator.metric().heapArenas().get(0).numThreadCaches());
        assertEquals(0, allocator.metric().heapArenas().get(1).numThreadCaches());
        assertEquals(0, allocator.metric().directArenas().get(0).numThreadCaches());
        assertEquals(0, allocator.metric().directArenas().get(1).numThreadCaches());
    }

    private static ThreadCache createNewThreadCache(final PooledByteBufAllocator allocator, final boolean direct)
            throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch cacheLatch = new CountDownLatch(1);
        final Thread t = new FastThreadLocalThread(new Runnable() {

            @Override
            public void run() {
                final ByteBuf buf;

                if (direct) {
                    buf = allocator.newDirectBuffer(1024, 1024);
                } else {
                    buf = allocator.newHeapBuffer(1024, 1024);
                }

                // Countdown the latch after we allocated a buffer. At this point the cache must exists.
                cacheLatch.countDown();

                buf.writeZero(buf.capacity());

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

        // Wait until we allocated a buffer and so be sure the thread was started and the cache exists.
        cacheLatch.await();

        return new ThreadCache() {
            @Override
            public void destroy() throws InterruptedException {
                latch.countDown();
                t.join();
            }
        };
    }

    private interface ThreadCache {
        void destroy() throws InterruptedException;
    }

    @Test
    public void testConcurrentUsage() throws Throwable {
        long runningTime = MILLISECONDS.toNanos(SystemPropertyUtil.getLong(
                "io.netty.buffer.PooledByteBufAllocatorTest.testConcurrentUsageTime", 15000));

        // We use no caches and only one arena to maximize the chance of hitting the race-condition we
        // had before.
        ByteBufAllocator allocator = new PooledByteBufAllocator(true, 0, 1, 8192, 9, 0, 0, 0);
        List<AllocationThread> threads = new ArrayList<AllocationThread>();
        try {
            for (int i = 0; i < 64; i++) {
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
            // First mark all AllocationThreads to complete their work and then wait until these are complete
            // and rethrow if there was any error.
            for (AllocationThread t : threads) {
                t.markAsFinished();
            }

            for (AllocationThread t: threads) {
                t.joinAndCheckForError();
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

        private final Queue<ByteBuf> buffers = new ConcurrentLinkedQueue<ByteBuf>();
        private final ByteBufAllocator allocator;
        private final AtomicReference<Object> finish = new AtomicReference<Object>();

        AllocationThread(ByteBufAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public void run() {
            try {
                int idx = 0;
                while (finish.get() == null) {
                    for (int i = 0; i < 10; i++) {
                        int len = ALLOCATION_SIZES[Math.abs(idx++ % ALLOCATION_SIZES.length)];
                        ByteBuf buf = allocator.directBuffer(len, Integer.MAX_VALUE);
                        assertEquals(len, buf.writableBytes());
                        while (buf.isWritable()) {
                            buf.writeByte(i);
                        }

                        buffers.offer(buf);
                    }
                    releaseBuffersAndCheckContent();
                }
            } catch (Throwable cause) {
                finish.set(cause);
            } finally {
                releaseBuffersAndCheckContent();
            }
        }

        private void releaseBuffersAndCheckContent() {
            int i = 0;
            while (!buffers.isEmpty()) {
                ByteBuf buf = buffers.poll();
                while (buf.isReadable()) {
                    assertEquals(i, buf.readByte());
                }
                buf.release();
                i++;
            }
        }

        boolean isFinished() {
            return finish.get() != null;
        }

        void markAsFinished() {
            finish.compareAndSet(null, Boolean.TRUE);
        }

        void joinAndCheckForError() throws Throwable {
            try {
                // Mark as finish if not already done but ensure we not override the previous set error.
                join();
            } finally {
                releaseBuffersAndCheckContent();
            }
            checkForError();
        }

        void checkForError() throws Throwable {
            Object obj = finish.get();
            if (obj instanceof Throwable) {
                throw (Throwable) obj;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> PooledByteBuf<T> unwrapIfNeeded(ByteBuf buf) {
        return (PooledByteBuf<T>) (buf instanceof PooledByteBuf ? buf : buf.unwrap());
    }

    @Test
    public void testCacheWorksForNormalAllocations() {
        int maxCachedBufferCapacity = PooledByteBufAllocator.DEFAULT_MAX_CACHED_BUFFER_CAPACITY;
        final PooledByteBufAllocator allocator =
                new PooledByteBufAllocator(true, 0, 1,
                        PooledByteBufAllocator.defaultPageSize(), PooledByteBufAllocator.defaultMaxOrder(),
                        128, 128, true);
        ByteBuf buffer = allocator.directBuffer(maxCachedBufferCapacity);
        assertEquals(1, allocator.metric().directArenas().get(0).numNormalAllocations());
        buffer.release();

        buffer = allocator.directBuffer(maxCachedBufferCapacity);
        // Should come out of the cache so the count should not be incremented
        assertEquals(1, allocator.metric().directArenas().get(0).numNormalAllocations());
        buffer.release();

        // Should be allocated without cache and also not put back in a cache.
        buffer = allocator.directBuffer(maxCachedBufferCapacity + 1);
        assertEquals(2, allocator.metric().directArenas().get(0).numNormalAllocations());
        buffer.release();

        buffer = allocator.directBuffer(maxCachedBufferCapacity + 1);
        assertEquals(3, allocator.metric().directArenas().get(0).numNormalAllocations());
        buffer.release();
    }

    @Test
    public void testNormalPoolSubpageRelease() {
        // 16 < elemSize <= 7168 or 8192 < elemSize <= 28672, 1 < subpage.maxNumElems <= 256
        // 7168 <= elemSize <= 8192, subpage.maxNumElems == 1
        int elemSize = 8192;
        int length = 1024;
        ByteBuf[] byteBufs = new ByteBuf[length];
        final PooledByteBufAllocator allocator = new PooledByteBufAllocator(false, 32, 32, 8192, 11, 256, 64, false, 0);

        for (int i = 0; i < length; i++) {
            byteBufs[i] = allocator.heapBuffer(elemSize, elemSize);
        }
        PoolChunk<Object> chunk = unwrapIfNeeded(byteBufs[0]).chunk;

        int beforeFreeBytes = chunk.freeBytes();
        for (int i = 0; i < length; i++) {
            byteBufs[i].release();
        }
        int afterFreeBytes = chunk.freeBytes();

        assertTrue(beforeFreeBytes < afterFreeBytes);
    }

    @Override
    @Test
    public void testUsedDirectMemory() {
        for (int power = 0; power < 8; power++) {
            int initialCapacity = 1024 << power;
            testUsedDirectMemory(initialCapacity);
        }
    }

    private void testUsedDirectMemory(int initialCapacity) {
        PooledByteBufAllocator allocator = newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();
        assertEquals(0, metric.usedDirectMemory());
        assertEquals(0, allocator.pinnedDirectMemory());
        ByteBuf buffer = allocator.directBuffer(initialCapacity, 4 * initialCapacity);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory());
        assertThat(allocator.pinnedDirectMemory())
                .isGreaterThanOrEqualTo(capacity)
                .isLessThanOrEqualTo(metric.usedDirectMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedDirectMemory(), buffer.toString());
        assertThat(allocator.pinnedDirectMemory())
                .isGreaterThanOrEqualTo(capacity)
                .isLessThanOrEqualTo(metric.usedDirectMemory());

        buffer.release();
        assertEquals(expectedUsedMemoryAfterRelease(allocator, capacity), metric.usedDirectMemory());
        assertThat(allocator.pinnedDirectMemory())
                .isGreaterThanOrEqualTo(0)
                .isLessThanOrEqualTo(metric.usedDirectMemory());
        trimCaches(allocator);
        assertEquals(0, allocator.pinnedDirectMemory());

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
        assertEquals(0, allocator.pinnedDirectMemory());
    }

    @Override
    @Test
    public void testUsedHeapMemory() {
        for (int power = 0; power < 8; power++) {
            int initialCapacity = 1024 << power;
            testUsedHeapMemory(initialCapacity);
        }
    }

    private void testUsedHeapMemory(int initialCapacity) {
        PooledByteBufAllocator allocator = newAllocator(true);
        ByteBufAllocatorMetric metric = allocator.metric();

        assertEquals(0, metric.usedHeapMemory());
        assertEquals(0, allocator.pinnedDirectMemory());
        ByteBuf buffer = allocator.heapBuffer(initialCapacity, 4 * initialCapacity);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());
        assertThat(allocator.pinnedHeapMemory())
                .isGreaterThanOrEqualTo(capacity)
                .isLessThanOrEqualTo(metric.usedHeapMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), metric.usedHeapMemory());
        assertThat(allocator.pinnedHeapMemory())
                .isGreaterThanOrEqualTo(capacity)
                .isLessThanOrEqualTo(metric.usedHeapMemory());

        buffer.release();
        assertEquals(expectedUsedMemoryAfterRelease(allocator, capacity), metric.usedHeapMemory());
        assertThat(allocator.pinnedHeapMemory())
                .isGreaterThanOrEqualTo(0)
                .isLessThanOrEqualTo(metric.usedHeapMemory());
        trimCaches(allocator);
        assertEquals(0, allocator.pinnedHeapMemory());

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
        assertEquals(0, allocator.pinnedDirectMemory());
    }

    @Test
    public void pinnedMemoryMustReflectBuffersInUseWithThreadLocalCaching() {
        pinnedMemoryMustReflectBuffersInUse(true);
    }

    @Test
    public void pinnedMemoryMustReflectBuffersInUseWithoutThreadLocalCaching() {
        pinnedMemoryMustReflectBuffersInUse(false);
    }

    private static void pinnedMemoryMustReflectBuffersInUse(boolean useThreadLocalCaching) {
        int smallCacheSize;
        int normalCacheSize;
        if (useThreadLocalCaching) {
            smallCacheSize = PooledByteBufAllocator.defaultSmallCacheSize();
            normalCacheSize = PooledByteBufAllocator.defaultNormalCacheSize();
        } else {
            smallCacheSize = 0;
            normalCacheSize = 0;
        }
        int directMemoryCacheAlignment = 0;
        PooledByteBufAllocator alloc = new PooledByteBufAllocator(
                PooledByteBufAllocator.defaultPreferDirect(),
                1,
                1,
                PooledByteBufAllocator.defaultPageSize(),
                PooledByteBufAllocator.defaultMaxOrder(),
                smallCacheSize,
                normalCacheSize,
                useThreadLocalCaching,
                directMemoryCacheAlignment);
        PooledByteBufAllocatorMetric metric = alloc.metric();
        AtomicLong capSum = new AtomicLong();

        for (long index = 0; index < 10000; index++) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            int bufCount = rnd.nextInt(1, 100);
            List<ByteBuf> buffers = new ArrayList<ByteBuf>(bufCount);

            if (index % 2 == 0) {
                // ensure that we allocate a small buffer
                for (int i = 0; i < bufCount; i++) {
                    ByteBuf buf = alloc.directBuffer(rnd.nextInt(8, 128));
                    buffers.add(buf);
                    capSum.addAndGet(buf.capacity());
                }
            } else {
                // allocate a larger buffer
                for (int i = 0; i < bufCount; i++) {
                    ByteBuf buf = alloc.directBuffer(rnd.nextInt(1024, 1024 * 100));
                    buffers.add(buf);
                    capSum.addAndGet(buf.capacity());
                }
            }

            if (index % 100 == 0) {
                long used = usedMemory(metric.directArenas());
                long pinned = alloc.pinnedDirectMemory();
                assertThat(capSum.get()).isLessThanOrEqualTo(pinned);
                assertThat(pinned).isLessThanOrEqualTo(used);
            }

            for (ByteBuf buffer : buffers) {
                buffer.release();
            }
            capSum.set(0);
            // After releasing all buffers, pinned memory must be zero
            assertThat(alloc.pinnedDirectMemory()).isZero();
        }
    }

    /**
     * Returns an estimate of bytes used by currently in-use buffers
     */
    private static long usedMemory(List<PoolArenaMetric> arenas) {
        long totalUsed = 0;
        for (PoolArenaMetric arenaMetrics : arenas) {
            for (PoolChunkListMetric arenaMetric : arenaMetrics.chunkLists()) {
                for (PoolChunkMetric chunkMetric : arenaMetric) {
                    // chunkMetric.chunkSize() returns maximum of bytes that can be served out of the chunk
                    // and chunkMetric.freeBytes() returns the bytes that are not yet allocated by in-use buffers
                    totalUsed += chunkMetric.chunkSize() - chunkMetric.freeBytes();
                }
            }
        }
        return totalUsed;
    }

    @Test
    public void testCapacityChangeDoesntThrowAssertionError() throws Exception {
        ByteBufAllocator allocator = newAllocator(true);
        List<ByteBuf> buffers = new ArrayList<ByteBuf>();
        try {
            for (int i = 0; i < 31; i++) {
                buffers.add(allocator.heapBuffer());
            }

            final ByteBuf buf = allocator.heapBuffer();
            buffers.add(buf);
            final AtomicReference<AssertionError> assertionRef = new AtomicReference<AssertionError>();
            Runnable capacityChangeTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        buf.capacity(512);
                    } catch (AssertionError e) {
                        assertionRef.compareAndSet(null, e);
                        throw e;
                    }
                }
            };
            Thread thread1 = new Thread(capacityChangeTask);
            Thread thread2 = new Thread(capacityChangeTask);

            thread1.start();
            thread2.start();

            thread1.join();
            thread2.join();

            buffers.add(allocator.heapBuffer());
            buffers.add(allocator.heapBuffer());

            AssertionError error = assertionRef.get();
            if (error != null) {
                throw error;
            }
        } finally {
            for (ByteBuf buffer: buffers) {
                buffer.release();
            }
        }
    }
}
