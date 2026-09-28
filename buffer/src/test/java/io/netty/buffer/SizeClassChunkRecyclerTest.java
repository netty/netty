/*
 * Copyright 2026 The Netty Project
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

import io.netty.buffer.AdaptivePoolingAllocator.IntStack;
import io.netty.buffer.AdaptivePoolingAllocator.SizeClassChunkRecycler;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.MpscIntQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SizeClassChunkRecyclerTest {
    // 2048 and 4096 share the MIN_CHUNK_SIZE pool; 8192 has a chunk size of its own (asserted below).
    private static final int SMALL = AdaptivePoolingAllocator.sizeClassIndexOf(2048);
    private static final int SMALL2 = AdaptivePoolingAllocator.sizeClassIndexOf(4096);
    private static final int LARGE = AdaptivePoolingAllocator.sizeClassIndexOf(8192);

    private static int chunkSize(int sizeClassIndex) {
        return AdaptivePoolingAllocator.chunkSizeOf(AdaptivePoolingAllocator.getSizeClasses()[sizeClassIndex]);
    }

    private static AbstractByteBuf buffer(int sizeClassIndex) {
        return (AbstractByteBuf) Unpooled.buffer(chunkSize(sizeClassIndex));
    }

    private static MpscIntQueue freeList(int capacity) {
        return MpscIntQueue.create(capacity, -1);
    }

    private static IntStack localFreeList(int capacity) {
        return new IntStack(new int[capacity]);
    }

    /** A recycler whose allocator only serves as the account the pooled buffers are released from. */
    private static SizeClassChunkRecycler newRecycler() {
        return new SizeClassChunkRecycler(new AdaptivePoolingAllocator(new UnpooledHeapChunkAllocator(), false));
    }

    private static ChunkInfo chunkInfo(final AbstractByteBuf buffer) {
        return new ChunkInfo() {
            @Override
            public int capacity() {
                return buffer.capacity();
            }

            @Override
            public boolean isDirect() {
                return buffer.isDirect();
            }

            @Override
            public long memoryAddress() {
                return 0;
            }
        };
    }

    private static final class UnpooledHeapChunkAllocator implements AdaptivePoolingAllocator.ChunkAllocator {
        @Override
        public AbstractByteBuf allocate(int initialCapacity, int maxCapacity) {
            return new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, initialCapacity, maxCapacity);
        }
    }

    @Test
    public void poolSharingFollowsChunkSize() {
        assertEquals(chunkSize(SMALL), chunkSize(SMALL2));
        assertNotEquals(chunkSize(SMALL2), chunkSize(LARGE));
    }

    @Test
    public void bufferComesBackWithItsFreeLists() {
        SizeClassChunkRecycler recycler = newRecycler();
        AbstractByteBuf buf = buffer(SMALL);
        MpscIntQueue fl = freeList(64);
        IntStack local = localFreeList(64);

        assertTrue(recycler.offer(buf, fl, local, SMALL));
        assertEquals(1, recycler.size(SMALL));

        assertTrue(recycler.poll(SMALL));
        assertSame(buf, recycler.takeBuffer());
        assertSame(fl, recycler.takeFreeList());
        assertSame(local, recycler.takeLocalFreeList());
        assertEquals(0, recycler.size(SMALL));
        assertFalse(recycler.poll(SMALL));
        buf.release();
    }

    @Test
    public void sizeClassesWithTheSameChunkSizeShareOnePool() {
        SizeClassChunkRecycler recycler = newRecycler();
        AbstractByteBuf buf = buffer(SMALL);
        MpscIntQueue fl = freeList(64);

        assertTrue(recycler.offer(buf, fl, localFreeList(64), SMALL));
        assertEquals(1, recycler.size(SMALL2));
        assertEquals(0, recycler.size(LARGE));

        assertTrue(recycler.poll(SMALL2));
        assertSame(buf, recycler.takeBuffer());
        assertSame(fl, recycler.takeFreeList());
        recycler.takeLocalFreeList();
        buf.release();
    }

    @Test
    public void poolIsBoundedPerChunkSize() {
        SizeClassChunkRecycler recycler = newRecycler();
        int capacity = SizeClassChunkRecycler.poolCapacity(SMALL);
        assertTrue(capacity > 1);
        List<AbstractByteBuf> offered = new ArrayList<AbstractByteBuf>();
        for (int i = 0; i < capacity; i++) {
            AbstractByteBuf buf = buffer(SMALL);
            offered.add(buf);
            assertTrue(recycler.offer(buf, freeList(64), localFreeList(64), SMALL));
        }
        AbstractByteBuf refused = buffer(SMALL);
        assertFalse(recycler.offer(refused, freeList(64), localFreeList(64), SMALL));
        assertEquals(capacity, recycler.size(SMALL));
        refused.release();

        // LIFO: the most recently freed buffer, still warm, is the first to be reused.
        for (int i = capacity - 1; i >= 0; i--) {
            assertTrue(recycler.poll(SMALL));
            assertSame(offered.get(i), recycler.takeBuffer());
            recycler.takeFreeList();
            recycler.takeLocalFreeList();
        }
        assertFalse(recycler.poll(SMALL));
        for (AbstractByteBuf buf : offered) {
            buf.release();
        }
    }

    @Test
    public void oneByteBudgetBoundsAllThePools() {
        SizeClassChunkRecycler recycler = newRecycler();
        // Fill the budget with buffers of one chunk size.
        int capacity = SizeClassChunkRecycler.poolCapacity(LARGE);
        List<AbstractByteBuf> offered = new ArrayList<AbstractByteBuf>();
        for (int i = 0; i < capacity; i++) {
            AbstractByteBuf buf = buffer(LARGE);
            offered.add(buf);
            assertTrue(recycler.offer(buf, freeList(64), localFreeList(64), LARGE));
        }
        // The budget is per recycler, not per chunk size: a buffer of another chunk size is refused too.
        AbstractByteBuf other = buffer(SMALL);
        assertFalse(recycler.offer(other, freeList(64), localFreeList(64), SMALL));
        // Taking one out makes room again.
        assertTrue(recycler.poll(LARGE));
        recycler.takeBuffer().release();
        recycler.takeFreeList();
        recycler.takeLocalFreeList();
        assertTrue(recycler.offer(other, freeList(64), localFreeList(64), SMALL));
        recycler.freeAll();
    }

    @Test
    public void freeAllReleasesPooledBuffersAndDropsLists() {
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(new UnpooledHeapChunkAllocator(), false);
        SizeClassChunkRecycler recycler = new SizeClassChunkRecycler(allocator);
        AbstractByteBuf a = buffer(SMALL);
        AbstractByteBuf b = buffer(LARGE);
        // As the allocator does when it takes a chunk buffer from its chunk allocator.
        allocator.chunkBufferAllocated(chunkInfo(a), true, false);
        allocator.chunkBufferAllocated(chunkInfo(b), true, false);
        assertTrue(recycler.offer(a, freeList(64), localFreeList(64), SMALL));
        assertTrue(recycler.offer(b, freeList(32), localFreeList(32), LARGE));
        // Pooled buffers are still the allocator's memory.
        assertEquals(a.capacity() + b.capacity(), recycler.retainedBytes());
        assertEquals(a.capacity() + b.capacity(), allocator.usedMemory());

        recycler.freeAll();

        assertEquals(0, allocator.usedMemory());
        assertEquals(0, recycler.retainedBytes());
        assertEquals(0, a.refCnt());
        assertEquals(0, b.refCnt());
        assertEquals(0, recycler.size(SMALL));
        assertEquals(0, recycler.size(LARGE));
        assertFalse(recycler.poll(SMALL));
        assertNull(recycler.takeBuffer());
    }

    /** Offer {@code count} accounted buffers of {@code sizeClassIndex}, oldest first. */
    private static List<AbstractByteBuf> offerAccounted(AdaptivePoolingAllocator allocator,
                                                        SizeClassChunkRecycler recycler,
                                                        int sizeClassIndex, int count) {
        List<AbstractByteBuf> offered = new ArrayList<AbstractByteBuf>();
        for (int i = 0; i < count; i++) {
            AbstractByteBuf buf = buffer(sizeClassIndex);
            allocator.chunkBufferAllocated(chunkInfo(buf), true, false);
            assertTrue(recycler.offer(buf, freeList(64), localFreeList(64), sizeClassIndex));
            offered.add(buf);
        }
        return offered;
    }

    /**
     * Freeing the oldest buffers moves the others down their pool: each must still come out with its own free lists.
     */
    @Test
    public void buffersKeepTheirFreeListsAcrossADecay() {
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(new UnpooledHeapChunkAllocator(), false);
        SizeClassChunkRecycler recycler = new SizeClassChunkRecycler(allocator);
        List<AbstractByteBuf> offered = new ArrayList<AbstractByteBuf>();
        List<MpscIntQueue> external = new ArrayList<MpscIntQueue>();
        List<IntStack> local = new ArrayList<IntStack>();
        for (int i = 0; i < 6; i++) {
            AbstractByteBuf buf = buffer(LARGE);
            allocator.chunkBufferAllocated(chunkInfo(buf), true, false);
            external.add(freeList(64));
            local.add(localFreeList(64));
            assertTrue(recycler.offer(buf, external.get(i), local.get(i), LARGE));
            offered.add(buf);
        }
        long t = System.nanoTime();
        recycler.decay(t += SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);
        recycler.decay(t + SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);
        assertEquals(3, recycler.size(LARGE));
        for (int i = offered.size() - 1; i >= 3; i--) {
            assertTrue(recycler.poll(LARGE));
            assertSame(offered.get(i), recycler.takeBuffer(), "newest first");
            assertSame(external.get(i), recycler.takeFreeList(), "with its own external free list");
            assertSame(local.get(i), recycler.takeLocalFreeList(), "and its own local free list");
            offered.get(i).release();
        }
        assertFalse(recycler.poll(LARGE));
    }

    /**
     * Half of the cold buffers, rounded up once over all the pools: two pools with one cold buffer each give up one,
     * not two.
     */
    @Test
    public void halfIsRoundedUpOnceOverAllThePools() {
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(new UnpooledHeapChunkAllocator(), false);
        SizeClassChunkRecycler recycler = new SizeClassChunkRecycler(allocator);
        offerAccounted(allocator, recycler, SMALL, 1);
        offerAccounted(allocator, recycler, LARGE, 1);
        long t = System.nanoTime();
        recycler.decay(t += SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);
        recycler.decay(t + SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);
        assertEquals(1, recycler.size(SMALL) + recycler.size(LARGE), "ceil(2 / 2) = 1 freed in total");
        recycler.freeAll();
        assertEquals(0, allocator.usedMemory());
    }

    /**
     * Buffers that sat in a pool through a whole interval are freed half at a time, oldest first: buffers offered
     * during an interval survive the decay that ends it, and a pool of 8 then keeps 4, 2, 1, 0 over consecutive
     * intervals.
     */
    @Test
    public void coldBuffersAreFreedHalfAtATimeOldestFirst() {
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(new UnpooledHeapChunkAllocator(), false);
        SizeClassChunkRecycler recycler = new SizeClassChunkRecycler(allocator);
        List<AbstractByteBuf> offered = offerAccounted(allocator, recycler, LARGE, 8);
        int chunk = chunkSize(LARGE);
        long t = System.nanoTime();

        recycler.decay(t += SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);
        assertEquals(8, recycler.size(LARGE), "offered during this interval: not cold yet");

        int[] expected = {4, 2, 1, 0, 0};
        for (int remaining : expected) {
            recycler.decay(t += SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);
            assertEquals(remaining, recycler.size(LARGE));
            assertEquals((long) remaining * chunk, recycler.retainedBytes());
            assertEquals((long) remaining * chunk, allocator.usedMemory(), "freed buffers leave the account");
            for (int i = 0; i < offered.size(); i++) {
                assertEquals(i < offered.size() - remaining ? 0 : 1, offered.get(i).refCnt(),
                        "the oldest are freed first, buffer " + i);
            }
        }
    }

    /** A buffer taken during an interval is not cold, even when it comes back before the decay. */
    @Test
    public void buffersTakenDuringTheIntervalAreNotCold() {
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(new UnpooledHeapChunkAllocator(), false);
        SizeClassChunkRecycler recycler = new SizeClassChunkRecycler(allocator);
        offerAccounted(allocator, recycler, LARGE, 8);
        long t = System.nanoTime();
        recycler.decay(t += SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);

        // Three are taken and given back: five sat untouched.
        List<AbstractByteBuf> taken = new ArrayList<AbstractByteBuf>();
        for (int i = 0; i < 3; i++) {
            assertTrue(recycler.poll(LARGE));
            taken.add(recycler.takeBuffer());
            recycler.takeFreeList();
            recycler.takeLocalFreeList();
        }
        for (AbstractByteBuf buf : taken) {
            assertTrue(recycler.offer(buf, freeList(64), localFreeList(64), LARGE));
        }

        recycler.decay(t + SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);
        assertEquals(8 - 3, recycler.size(LARGE), "half of the five cold ones, rounded up, are freed");
        for (AbstractByteBuf buf : taken) {
            assertEquals(1, buf.refCnt(), "a buffer taken during the interval survives");
        }
        recycler.freeAll();
    }

    /**
     * A purge tick decays only once the interval has passed and the heap made enough allocations since the last
     * decay, whichever comes last; the ticks themselves are far more frequent.
     */
    @Test
    public void ticksDecayOnlyAfterTheIntervalAndEnoughAllocations() {
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator(new UnpooledHeapChunkAllocator(), false);
        SizeClassChunkRecycler recycler = new SizeClassChunkRecycler(allocator);
        offerAccounted(allocator, recycler, LARGE, 2);
        // Both buffers are cold from here, and the last decay was long ago.
        recycler.decay(System.nanoTime() - 2 * SizeClassChunkRecycler.DECAY_INTERVAL_NANOS);

        recycler.tick(SizeClassChunkRecycler.DECAY_MIN_ALLOCATIONS - 1);
        assertEquals(2, recycler.size(LARGE), "the interval passed, but not enough allocations");
        recycler.tick(1);
        assertEquals(1, recycler.size(LARGE), "both passed: half of the cold ones are freed");
        recycler.tick(2 * SizeClassChunkRecycler.DECAY_MIN_ALLOCATIONS);
        assertEquals(1, recycler.size(LARGE), "enough allocations, but the interval started again");
        // That look at the clock started a new count: the interval passing now is not enough on its own.
        recycler.lastDecayNanos -= 2 * SizeClassChunkRecycler.DECAY_INTERVAL_NANOS;
        recycler.tick(SizeClassChunkRecycler.DECAY_MIN_ALLOCATIONS - 1);
        assertEquals(1, recycler.size(LARGE), "the interval passed, but the count restarted at the last look");
        recycler.freeAll();
        assertEquals(0, allocator.usedMemory());
    }

    /**
     * Whatever the chunk sizes are, two size classes share a recycler pool exactly when their chunks have the same
     * size, adjacent or not.
     */
    @Test
    public void sizeClassesShareAPoolExactlyWhenTheirChunkSizesAreEqual() {
        int[] sizeClasses = AdaptivePoolingAllocator.getSizeClasses();
        for (int a = 0; a < sizeClasses.length; a++) {
            for (int b = 0; b < sizeClasses.length; b++) {
                boolean sameChunkSize = AdaptivePoolingAllocator.chunkSizeOf(sizeClasses[a])
                        == AdaptivePoolingAllocator.chunkSizeOf(sizeClasses[b]);
                boolean samePool = AdaptivePoolingAllocator.chunkPoolOf(a) == AdaptivePoolingAllocator.chunkPoolOf(b);
                assertEquals(sameChunkSize, samePool, sizeClasses[a] + " and " + sizeClasses[b]);
            }
        }
    }

    /**
     * The same property for size class tables other than today's: a changed table is how the pools of size classes
     * that are not adjacent were once left unshared. The tables are random selections of the size classes in random
     * order, so that equal chunk sizes are rarely adjacent.
     */
    @Test
    public void anyTableOfSizeClassesSharesAPoolExactlyWhenChunkSizesAreEqual() {
        int[] all = AdaptivePoolingAllocator.getSizeClasses();
        Random random = new Random(42);
        for (int round = 0; round < 1000; round++) {
            int[] sizeClasses = new int[1 + random.nextInt(all.length)];
            for (int i = 0; i < sizeClasses.length; i++) {
                sizeClasses[i] = all[random.nextInt(all.length)];
            }
            int[] chunkSizes = AdaptivePoolingAllocator.distinctChunkSizes(sizeClasses);
            byte[] pools = AdaptivePoolingAllocator.chunkPools(sizeClasses, chunkSizes);

            Set<Integer> distinct = new HashSet<Integer>();
            for (int chunkSize : chunkSizes) {
                assertTrue(distinct.add(chunkSize), "chunk size listed twice: " + chunkSize);
            }
            assertEquals(sizeClasses.length, pools.length);
            for (int a = 0; a < sizeClasses.length; a++) {
                assertEquals(AdaptivePoolingAllocator.chunkSizeOf(sizeClasses[a]), chunkSizes[pools[a]]);
                for (int b = 0; b < sizeClasses.length; b++) {
                    boolean sameChunkSize = AdaptivePoolingAllocator.chunkSizeOf(sizeClasses[a])
                            == AdaptivePoolingAllocator.chunkSizeOf(sizeClasses[b]);
                    assertEquals(sameChunkSize, pools[a] == pools[b], sizeClasses[a] + " and " + sizeClasses[b]);
                }
            }
        }
    }

    /**
     * End to end through the allocator: chunks of one size class are freed past the cache floor, so their buffers go
     * to the recycler, and a different size class with the same chunk size must reuse them with the free lists that
     * came along - smaller than it needs (4096 then 32), larger (32 then 4096), or one of each (1152: 113 segments,
     * an external list rounded up to 128 entries and a local one of exactly 113; then 1024: 128 segments).
     * Size classes that are not adjacent share chunks too: 16896 then 67584. From 16 KiB up a whole family (2^n, and
     * 2^n plus header) shares one chunk size: 131072 then 16384.
     */
    @ParameterizedTest
    @CsvSource({
            "4096, 32, false", "32, 4096, false", "1152, 1024, false", "16896, 67584, false", "131072, 16384, false",
            "4096, 32, true", "32, 4096, true", "1152, 1024, true", "16896, 67584, true", "131072, 16384, true",
    })
    public void chunksAreReusedAcrossSizeClassesWithTheirFreeLists(int freedSize, int reusingSize, boolean threadLocal)
            throws Exception {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false, threadLocal);
        Runnable test = () -> assertReusedAcrossSizeClasses(allocator, freedSize, reusingSize);
        if (threadLocal) {
            FastThreadLocalThread.runWithFastThreadLocal(test);
        } else {
            test.run();
        }
    }

    private static void assertReusedAcrossSizeClasses(AdaptiveByteBufAllocator allocator, int freedSize,
                                                      int reusingSize) {
        int chunkSize = AdaptivePoolingAllocator.chunkSizeOf(freedSize);
        assertEquals(chunkSize, AdaptivePoolingAllocator.chunkSizeOf(reusingSize));
        // 64 chunks: well past the one chunk a size class keeps, so the rest go to the recycler.
        int chunks = 64;

        Set<byte[]> freedArrays = Collections.newSetFromMap(new IdentityHashMap<byte[], Boolean>());
        List<ByteBuf> bufs = new ArrayList<ByteBuf>();
        for (int i = 0; i < chunks * (chunkSize / freedSize); i++) {
            ByteBuf buf = allocator.heapBuffer(freedSize, freedSize);
            freedArrays.add(buf.array());
            bufs.add(buf);
        }
        for (ByteBuf buf : bufs) {
            buf.release();
        }
        bufs.clear();

        boolean reused = false;
        IdentityHashMap<byte[], Set<Integer>> segments = new IdentityHashMap<byte[], Set<Integer>>();
        int count = chunks * (chunkSize / reusingSize);
        for (int i = 0; i < count; i++) {
            ByteBuf buf = allocator.heapBuffer(reusingSize, reusingSize);
            buf.writeInt(i);
            reused |= freedArrays.contains(buf.array());
            Set<Integer> offsets = segments.get(buf.array());
            if (offsets == null) {
                offsets = new HashSet<Integer>();
                segments.put(buf.array(), offsets);
            }
            assertTrue(offsets.add(buf.arrayOffset()), "segment handed out twice");
            bufs.add(buf);
        }
        assertTrue(reused, "no chunk buffer of the first size class was reused by the second");
        for (int i = 0; i < bufs.size(); i++) {
            assertEquals(i, bufs.get(i).readInt());
        }
        for (ByteBuf buf : bufs) {
            buf.release();
        }
    }
}
