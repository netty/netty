/*
 * Copyright 2024 The Netty Project
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

import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AdaptivePoolingAllocatorTest {
    @Test
    void sizeClassComputations() throws Exception {
        final int[] sizeClasses = AdaptivePoolingAllocator.getSizeClasses();
        for (int sizeClassIndex = 0; sizeClassIndex < sizeClasses.length; sizeClassIndex++) {
            final int previousSizeIncluded = sizeClassIndex == 0? 0 : sizeClasses[sizeClassIndex - 1] + 1;
            assertSizeClassOf(sizeClassIndex, previousSizeIncluded, sizeClasses[sizeClassIndex]);
        }
        // beyond the last size class, we return the size class array's length
        assertSizeClassOf(sizeClasses.length, sizeClasses[sizeClasses.length - 1] + 1,
                          sizeClasses[sizeClasses.length - 1] + 1);
    }

    private static void assertSizeClassOf(int expectedSizeClass, int previousSizeIncluded, int maxSizeIncluded) {
        for (int size = previousSizeIncluded; size <= maxSizeIncluded; size++) {
            int sizeToTest = size;
            assertEquals(expectedSizeClass, AdaptivePoolingAllocator.sizeClassIndexOf(size),
                         () -> "size = " + sizeToTest);
        }
    }

    private static final int[] TRACE_SIZES = {64, 1024, 4096, 16384, 65536};
    private static final int TRACE_OPS = 40000;
    private static final int TRACE_PHASE_OPS = 4000;
    private static final int TRACE_CHECKPOINT_OPS = 2000;
    private static final int TRACE_SETTLE_OPS = 20000;

    /** Counts the chunk buffers the allocator asks for, which is every chunk not re-created from a recycled one. */
    private static final class CountingChunkAllocator implements AdaptivePoolingAllocator.ChunkAllocator {
        long count;
        private final List<AbstractByteBuf> allocated = new ArrayList<AbstractByteBuf>();

        @Override
        public AbstractByteBuf allocate(int initialCapacity, int maxCapacity) {
            count++;
            AbstractByteBuf buf =
                    new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, initialCapacity, maxCapacity);
            allocated.add(buf);
            return buf;
        }

        /** The bytes of the buffers handed out and not released yet: what the allocator holds, seen from outside. */
        long unreleasedBytes() {
            long bytes = 0;
            for (AbstractByteBuf buf : allocated) {
                if (buf.refCnt() > 0) {
                    bytes += buf.capacity();
                }
            }
            return bytes;
        }
    }

    /**
     * One checkpoint row, after checking {@link AdaptivePoolingAllocator#usedMemory()} against the chunk allocator's
     * own view of what it handed out and got back: they must agree whatever the allocator did with the memory in
     * between (a chunk, a recycler pool, a one-shot buffer).
     */
    private static long[] checkpoint(CountingChunkAllocator counter, AdaptivePoolingAllocator allocator) {
        long used = allocator.usedMemory();
        assertEquals(counter.unreleasedBytes(), used, "usedMemory() and the chunk allocator disagree");
        return new long[] {counter.count, used};
    }

    /**
     * A seeded allocation trace replayed on a stripe and on a thread-local heap. One row per checkpoint:
     * {@code {fresh chunks allocated so far, usedMemory}}, where {@code usedMemory} counts the chunk buffers the
     * heap's recycler holds too. The two heaps must go through the same rows (the notes a foreign release leaves are
     * applied before any decision they could change), {@link AdaptivePoolingAllocator#usedMemory()} must agree with
     * the chunk allocator's own view at every row, and what a heap holds once the trace has settled must stay under
     * the bound its parts document. The rows themselves are not pinned: they move with the chunk sizes, the floors
     * and the recycler's budget, and none of those is what this test is about. No decay runs during the trace (it
     * takes well under the decay interval), so the rows do not depend on time.
     */
    @Test
    void seededTraceKeepsChunkAllocationsAndUsedMemory() throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode pools fewer size classes and has no thread-local heaps");
        long[][] stripe = traceOnNewThread(false);
        long[][] threadLocal = traceOnNewThread(true);
        assertTrue(Arrays.deepEquals(stripe, threadLocal), "the two heaps diverged; stripe: "
                + Arrays.deepToString(stripe).replace("], [", "],\n [") + "\nthread-local: "
                + Arrays.deepToString(threadLocal).replace("], [", "],\n ["));
        long settled = stripe[stripe.length - 1][1];
        assertTrue(settled <= idleBound(), "settled on " + settled + " bytes, bound " + idleBound());
    }

    /**
     * A deterministic replay: one thread allocates (so one stripe, or its own thread-local heap), and every
     * cross-thread release is handed to a helper thread while the allocating thread waits for it. On the shared
     * stripe, some of those releases run while the allocating thread holds every stripe lock, so they cannot take
     * the lock and must leave a note; the rest take the lock. On the thread-local heap every foreign release leaves
     * a note.
     */
    private static long[][] traceOnNewThread(boolean threadLocal) throws Throwable {
        final CountingChunkAllocator counter = new CountingChunkAllocator();
        final AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(counter, true);
        final CompletableFuture<long[][]> result = new CompletableFuture<long[][]>();
        Runnable trace = () -> {
            try {
                result.complete(runTrace(counter, allocator, threadLocal));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        };
        // A FastThreadLocalThread for the thread-local heap, which is freed when the thread ends: hence the join.
        Thread thread = threadLocal ? new FastThreadLocalThread(trace) : new Thread(trace);
        long start = System.nanoTime();
        thread.start();
        long[][] checkpoints;
        try {
            checkpoints = result.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        } finally {
            thread.join();
        }
        // A trace that took a whole decay interval may have seen a decay, which the other heap's run did not.
        assumeTrue(System.nanoTime() - start < AdaptivePoolingAllocator.IdleDecay.DECAY_INTERVAL_NANOS,
                "the trace ran longer than a decay interval");
        // The thread is gone: a thread-local heap has been freed, chunks and recycler alike.
        assertEquals(counter.unreleasedBytes(), allocator.usedMemory(), "after the allocating thread ended");
        return checkpoints;
    }

    /**
     * What one heap may hold once a trace has settled, from the bounds its parts document: the recycler's byte
     * budget, the wholly free chunks above the size classes plus the one allocated from (each within their byte
     * bound), and for every size class the chunk allocated from plus the one it keeps.
     */
    private static long idleBound() {
        long bound = AdaptivePoolingAllocator.SizeClassChunkRecycler.RECYCLED_BYTES_BUDGET
                + 2L * AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE_BYTES;
        for (int sizeClass : AdaptivePoolingAllocator.getSizeClasses()) {
            bound += 2L * AdaptivePoolingAllocator.chunkSizeOf(sizeClass);
        }
        return bound;
    }

    private static long[][] runTrace(CountingChunkAllocator counter, AdaptivePoolingAllocator allocator,
                                     boolean threadLocal) throws Exception {
        List<StampedLock> locks = threadLocal ? new ArrayList<StampedLock>() : stripeLocks(allocator);
        ExecutorService helper = Executors.newSingleThreadExecutor();
        SplittableRandom rng = new SplittableRandom(42);
        List<ByteBuf> live = new ArrayList<ByteBuf>();
        List<long[]> checkpoints = new ArrayList<long[]>();
        int target = 0;
        try {
            for (int op = 0; op < TRACE_OPS; op++) {
                if (op % TRACE_CHECKPOINT_OPS == 0) {
                    checkpoints.add(checkpoint(counter, allocator));
                }
                if (op % TRACE_PHASE_OPS == 0) {
                    // Alternate bursts and idle phases, so caches grow above their floors and are purged back.
                    target = (op / TRACE_PHASE_OPS) % 2 == 0 ? 500 + rng.nextInt(2000) : 5 + rng.nextInt(20);
                }
                boolean allocate = live.isEmpty() ||
                        (live.size() < target ? rng.nextInt(10) < 7 : rng.nextInt(10) < 3);
                if (allocate) {
                    int size = TRACE_SIZES[rng.nextInt(TRACE_SIZES.length)] - rng.nextInt(32);
                    ByteBuf buf = allocator.allocate(size, Integer.MAX_VALUE);
                    if (rng.nextInt(20) == 0) {
                        // Reallocation into a bigger size class.
                        buf.capacity(size * 3);
                    }
                    live.add(buf);
                } else {
                    int idx = rng.nextInt(live.size());
                    ByteBuf buf = live.get(idx);
                    live.set(idx, live.get(live.size() - 1));
                    live.remove(live.size() - 1);
                    int how = rng.nextInt(100);
                    if (how < 70) {
                        buf.release();
                    } else if (how < 85) {
                        releaseOn(helper, buf, null);
                    } else {
                        releaseOn(helper, buf, locks);
                    }
                }
            }
            checkpoints.add(checkpoint(counter, allocator));
            for (ByteBuf buf : live) {
                buf.release();
            }
            live.clear();
            checkpoints.add(checkpoint(counter, allocator));
            // Settle on a tiny working set: drives the drains and purge ticks on every size class used above.
            for (int i = 0; i < TRACE_SETTLE_OPS; i++) {
                allocator.allocate(TRACE_SIZES[i % TRACE_SIZES.length], Integer.MAX_VALUE).release();
            }
            checkpoints.add(checkpoint(counter, allocator));
            return checkpoints.toArray(new long[0][]);
        } finally {
            helper.shutdown();
        }
    }

    /**
     * What a heap pays to hold one buffer of each size class from 16 KiB up: a 512 KiB chunk for each 2^n class and
     * a 528 KiB one for each class that adds a header, whatever the segment size. At 32 segments per chunk the six
     * classes from 32 KiB up alone cost 14.2 MiB.
     */
    @Test
    void sizeClassesFromSixteenKibShareTheChunkSizeOfTheirFamily() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode pools fewer size classes");
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(new CountingChunkAllocator(), true);
        List<ByteBuf> live = new ArrayList<ByteBuf>();
        long expected = 0;
        for (int size : AdaptivePoolingAllocator.getSizeClasses()) {
            if (size >= 16384) {
                live.add(allocator.allocate(size, size));
                expected += Integer.bitCount(size) == 1 ? 512 * 1024 : 528 * 1024;
            }
        }
        assertEquals(8, live.size(), "size classes from 16 KiB up");
        assertEquals(expected, allocator.usedMemory());
        for (ByteBuf buf : live) {
            buf.release();
        }
    }

    /**
     * Chunks of a large size class that empty during a burst are given up at once, and their buffers must come back
     * from the heap's recycler instead of being allocated again: the recycler is bounded in bytes per heap, not to a
     * buffer or two per chunk size. Emptying four chunks and allocating their worth again allocates no chunk.
     */
    @Test
    void emptiedLargeSizeClassChunksAreReusedFromTheRecycler() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode pools fewer size classes");
        CountingChunkAllocator counter = new CountingChunkAllocator();
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(counter, true);
        int size = 65536;
        List<ByteBuf> live = new ArrayList<ByteBuf>();
        live.add(allocator.allocate(size, size));
        assertEquals(1, counter.count);
        int chunkSize = (int) allocator.usedMemory();
        int perChunk = chunkSize / size;
        assumeTrue(perChunk >= 4, "chunk holds " + perChunk + " segments");
        // More chunks in use than the one a size class keeps, so a chunk that empties is given up.
        int chunks = 1 + 6;
        while (live.size() < chunks * perChunk) {
            live.add(allocator.allocate(size, size));
        }
        long allocated = counter.count;
        assertEquals(chunks, allocated, "one chunk per " + perChunk + " buffers");
        int emptied = 4;
        for (int i = 0; i < emptied * perChunk; i++) {
            live.remove(0).release();
        }
        for (int i = 0; i < emptied * perChunk; i++) {
            live.add(allocator.allocate(size, size));
        }
        assertEquals(allocated, counter.count, "chunks allocated");
        for (ByteBuf buf : live) {
            buf.release();
        }
    }

    /**
     * A buddy chunk that empties while its magazine holds many chunks still in use is kept and reused: only wholly
     * free chunks count against {@link AdaptivePoolingAllocator#CHUNK_REUSE_QUEUE}. Allocating the emptied chunk's
     * worth again needs no new chunk.
     */
    @Test
    void emptiedBuddyChunkIsReusedWhileManyChunksAreInUse() throws Exception {
        assumeFalse(isLowMemory(), "low-memory mode has no buddy magazines");
        CountingChunkAllocator counter = new CountingChunkAllocator();
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(counter, true);
        int size = 256 * 1024; // above the largest size class, so buddy chunks
        List<ByteBuf> live = new ArrayList<ByteBuf>();
        live.add(allocator.allocate(size, size));
        // How many buffers a chunk holds comes from the allocator: one chunk was allocated for the first buffer.
        assertEquals(1, counter.count);
        int buffersPerChunk = (int) (allocator.usedMemory() / size);
        assertTrue(buffersPerChunk >= 2, "buffers per chunk " + buffersPerChunk);
        int chunks = AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE + 4;
        while (live.size() < chunks * buffersPerChunk) {
            live.add(allocator.allocate(size, size));
        }
        long allocated = counter.count;
        assertEquals(chunks, allocated, "one chunk per " + buffersPerChunk + " buffers");
        // Empty the first chunk, which served the first buffers; every other chunk stays full.
        for (int i = 0; i < buffersPerChunk; i++) {
            live.remove(0).release();
        }
        // The next allocations take the slow path, which applies the releases and finds the emptied chunk.
        for (int i = 0; i < buffersPerChunk; i++) {
            live.add(allocator.allocate(size, size));
        }
        assertEquals(allocated, counter.count, "chunks allocated");
        for (ByteBuf buf : live) {
            buf.release();
        }
    }

    /** Release {@code buf} on {@code helper} and wait; with {@code locks}, while holding every one of them. */
    private static void releaseOn(ExecutorService helper, ByteBuf buf, List<StampedLock> locks) throws Exception {
        List<Long> stamps = new ArrayList<Long>();
        if (locks != null) {
            for (StampedLock l : locks) {
                stamps.add(l.writeLock());
            }
        }
        try {
            helper.submit((Runnable) buf::release).get();
        } finally {
            for (int i = 0; i < stamps.size(); i++) {
                locks.get(i).unlockWrite(stamps.get(i));
            }
        }
    }

    private static List<StampedLock> stripeLocks(AdaptivePoolingAllocator allocator) throws Exception {
        Field stripesField = AdaptivePoolingAllocator.class.getDeclaredField("stripedHeaps");
        stripesField.setAccessible(true);
        Object[] stripes = (Object[]) stripesField.get(allocator);
        List<StampedLock> out = new ArrayList<StampedLock>();
        for (Object stripe : stripes) {
            if (stripe == null) {
                continue;
            }
            Field lockField = stripe.getClass().getDeclaredField("lock");
            lockField.setAccessible(true);
            out.add((StampedLock) lockField.get(stripe));
        }
        return out;
    }

    private static boolean isLowMemory() throws Exception {
        Field f = AdaptivePoolingAllocator.class.getDeclaredField("IS_LOW_MEM");
        f.setAccessible(true);
        return f.getBoolean(null);
    }
}
