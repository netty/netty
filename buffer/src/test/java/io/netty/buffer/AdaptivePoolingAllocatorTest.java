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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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

    /**
     * Fresh chunk allocations and used memory at fixed checkpoints of a seeded, single-stripe allocation trace.
     * One row per checkpoint: {@code {fresh chunks allocated so far, usedMemory}}. The values were recorded on
     * 004e4cc8a3, before the magazine's current chunk became the cache's active chunk, and must not change. They
     * move when the retention floor counts the active chunk, or when a poll takes the oldest reusable chunk
     * instead of the newest.
     */
    private static final long[][] EXPECTED_SHARED = {
            {0, 0}, {23, 19136512}, {38, 32505856}, {38, 32505856},
            {38, 16777216}, {38, 20971520}, {46, 37224448}, {46, 37224448},
            {46, 20971520}, {47, 27262976}, {47, 27262976}, {47, 23068672},
            {47, 20971520}, {48, 27262976}, {54, 38928384}, {54, 38928384},
            {54, 21102592}, {55, 27394048}, {59, 36831232}, {59, 36831232},
            {59, 21626880}, {59, 21626880}, {59, 21626880},
    };
    private static final long[][] EXPECTED_THREAD_LOCAL = {
            {0, 0}, {23, 19136512}, {38, 32505856}, {38, 32505856},
            {38, 17301504}, {38, 20971520}, {46, 37224448}, {46, 37224448},
            {46, 21495808}, {47, 27262976}, {47, 27262976}, {47, 23068672},
            {47, 20971520}, {48, 27262976}, {54, 38928384}, {54, 38928384},
            {54, 23724032}, {55, 27918336}, {59, 36831232}, {59, 36831232},
            {59, 21626880}, {59, 21626880}, {59, 21626880},
    };

    private static final int[] TRACE_SIZES = {64, 1024, 4096, 16384, 65536};
    private static final int TRACE_OPS = 40000;
    private static final int TRACE_PHASE_OPS = 4000;
    private static final int TRACE_CHECKPOINT_OPS = 2000;
    private static final int TRACE_SETTLE_OPS = 20000;

    /** Counts the chunk buffers the allocator asks for, which is every chunk not re-created from a recycled one. */
    private static final class CountingChunkAllocator implements AdaptivePoolingAllocator.ChunkAllocator {
        long count;

        @Override
        public AbstractByteBuf allocate(int initialCapacity, int maxCapacity) {
            count++;
            return new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, initialCapacity, maxCapacity);
        }
    }

    /**
     * A deterministic replay: one thread allocates (so one stripe, or its own thread-local heap), and every
     * cross-thread release is handed to a helper thread while the allocating thread waits for it. On the shared
     * stripe, some of those releases run while the allocating thread holds every stripe lock, so they cannot take
     * the lock and must leave a note; the rest take the lock. On the thread-local heap every foreign release leaves
     * a note.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void seededTraceKeepsChunkAllocationsAndUsedMemory(boolean threadLocal) throws Throwable {
        assumeFalse(isLowMemory(), "low-memory mode pools fewer size classes and has no thread-local heaps");
        final AtomicReference<Object> result = new AtomicReference<Object>();
        Runnable trace = () -> {
            try {
                result.set(runTrace(threadLocal));
            } catch (Throwable t) {
                result.set(t);
            }
        };
        Thread thread = threadLocal ? new FastThreadLocalThread(trace) : new Thread(trace);
        thread.start();
        thread.join();
        if (result.get() instanceof Throwable) {
            throw (Throwable) result.get();
        }
        long[][] actual = (long[][]) result.get();
        long[][] expected = threadLocal ? EXPECTED_THREAD_LOCAL : EXPECTED_SHARED;
        assertTrue(Arrays.deepEquals(expected, actual), "trace diverged (threadLocal=" + threadLocal
                + "); actual checkpoints: " + Arrays.deepToString(actual).replace("], [", "],\n ["));
    }

    private static long[][] runTrace(boolean threadLocal) throws Exception {
        CountingChunkAllocator counter = new CountingChunkAllocator();
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(counter, true);
        List<StampedLock> locks = threadLocal ? new ArrayList<StampedLock>() : stripeLocks(allocator);
        ExecutorService helper = Executors.newSingleThreadExecutor();
        SplittableRandom rng = new SplittableRandom(42);
        List<ByteBuf> live = new ArrayList<ByteBuf>();
        List<long[]> checkpoints = new ArrayList<long[]>();
        int target = 0;
        try {
            for (int op = 0; op < TRACE_OPS; op++) {
                if (op % TRACE_CHECKPOINT_OPS == 0) {
                    checkpoints.add(new long[] {counter.count, allocator.usedMemory()});
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
            checkpoints.add(new long[] {counter.count, allocator.usedMemory()});
            for (ByteBuf buf : live) {
                buf.release();
            }
            live.clear();
            checkpoints.add(new long[] {counter.count, allocator.usedMemory()});
            // Settle on a tiny working set: drives the drains and purge ticks on every size class used above.
            for (int i = 0; i < TRACE_SETTLE_OPS; i++) {
                allocator.allocate(TRACE_SIZES[i % TRACE_SIZES.length], Integer.MAX_VALUE).release();
            }
            checkpoints.add(new long[] {counter.count, allocator.usedMemory()});
            return checkpoints.toArray(new long[0][]);
        } finally {
            helper.shutdown();
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
