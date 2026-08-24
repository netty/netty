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

import io.netty.buffer.AdaptivePoolingAllocator.SizeClassedChunk;
import io.netty.buffer.AdaptivePoolingAllocator.ThreadLocalSizeClassedChunkCache;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SizeClassedChunkCacheTest {

    private static SizeClassedChunk chunkWithCapacity() {
        SizeClassedChunk chunk = mock(SizeClassedChunk.class);
        when(chunk.remainingCapacity()).thenReturn(512);
        when(chunk.capacity()).thenReturn(4096);
        when(chunk.hasRemainingCapacity()).thenReturn(true);
        when(chunk.hasFullCapacity()).thenReturn(false);
        return chunk;
    }

    private static SizeClassedChunk chunkWithoutCapacity() {
        SizeClassedChunk chunk = mock(SizeClassedChunk.class);
        when(chunk.remainingCapacity()).thenReturn(0);
        when(chunk.capacity()).thenReturn(4096);
        when(chunk.hasRemainingCapacity()).thenReturn(false);
        when(chunk.hasFullCapacity()).thenReturn(false);
        return chunk;
    }

    private static SizeClassedChunk fullChunk() {
        SizeClassedChunk chunk = mock(SizeClassedChunk.class);
        when(chunk.remainingCapacity()).thenReturn(4096);
        when(chunk.capacity()).thenReturn(4096);
        when(chunk.hasRemainingCapacity()).thenReturn(true);
        when(chunk.hasFullCapacity()).thenReturn(true);
        return chunk;
    }

    // --- Two-list structure: basic operations ---

    @Test
    void offerChunkCategorizesByCapacity() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(chunkWithCapacity());

        assertEquals(2, cache.reusableCount);
        assertEquals(1, cache.exhaustedCount);
    }

    @Test
    void offerChunkNeverRejects() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        // Offer far more than any cap would allow
        for (int i = 0; i < 200; i++) {
            assertTrue(cache.offerChunk(chunkWithCapacity()));
        }
        assertEquals(200, cache.reusableCount);
    }

    // --- pollChunk: O(1) from reusable list ---

    @Test
    void pollChunkTakesFromReusableList() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk cap = chunkWithCapacity();
        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(cap);

        SizeClassedChunk polled = cache.pollChunk(256);
        assertSame(cap, polled);
        assertEquals(1, cache.exhaustedCount);
        assertEquals(0, cache.reusableCount);
    }

    @Test
    void pollChunkReturnsNullWhenEmpty() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);
        assertNull(cache.pollChunk(256));
    }

    @Test
    void pollChunkReturnsNullWhenOnlyExhaustedChunks() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);
        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(chunkWithoutCapacity());

        assertNull(cache.pollChunk(256));
    }

    // --- Notification: exhausted chunks that gained capacity ---
    //
    // A chunk that gains capacity from a cross-thread return that could not take the stripe lock is
    // discovered only through a notification. Nothing searches the exhausted list any more, so a
    // capacity gain with no note left behind is invisible -- by design.

    @Test
    void pollChunkFindsExhaustedChunkThatGainedCapacityAfterNotification() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        assertEquals(1, cache.exhaustedCount);

        // Simulate a cross-thread segment return that could not take the lock: the segment lands in
        // the external free list, and the releaser leaves a note.
        when(chunk.hasRemainingCapacity()).thenReturn(true);
        cache.notifyHasCapacity(chunk);

        assertSame(chunk, cache.pollChunk(256));
        assertEquals(0, cache.exhaustedCount);
    }

    @Test
    void pollChunkDoesNotSearchTheExhaustedListWithoutANotification() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        when(chunk.hasRemainingCapacity()).thenReturn(true);

        assertNull(cache.pollChunk(256));
        assertEquals(1, cache.exhaustedCount);
    }

    // --- forcePurge: drains notifications + returns reusable chunk ---

    @Test
    void forcePurgeDetectsCapacityGainOnExhaustedChunks() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        assertEquals(1, cache.exhaustedCount);
        assertEquals(0, cache.reusableCount);

        // Simulate external return giving capacity
        when(chunk.hasRemainingCapacity()).thenReturn(true);
        cache.notifyHasCapacity(chunk);

        SizeClassedChunk polled = cache.forcePurge();
        assertSame(chunk, polled);
    }

    // --- Eviction: fully-free chunks above retention floor ---

    @Test
    void purgeEvictsFullyFreeChunksAboveFloor() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        int floor = cache.purgeRetentionFloor;

        // Fill to floor with working-set chunks
        for (int i = 0; i < floor; i++) {
            cache.offerChunk(chunkWithCapacity());
        }

        // Add excess fully-free chunk
        SizeClassedChunk idle = fullChunk();
        cache.offerChunk(idle);

        // Purge should evict the fully-free chunk (above floor)
        cache.tickPurge();
        verify(idle).recycleOrDeallocate(null, 0);
    }

    @Test
    void purgeKeepsFullyFreeChunksAtOrBelowFloor() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        // Add just one fully-free chunk — below retention floor
        SizeClassedChunk idle = fullChunk();
        cache.offerChunk(idle);

        cache.tickPurge();
        verify(idle, never()).recycleOrDeallocate(null, 0);
    }

    @Test
    void cacheEvictsExcessFullyFreeChunksAfterBurst() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        int floor = cache.purgeRetentionFloor;
        int excess = 10;

        // Working set at floor
        SizeClassedChunk workingSet = chunkWithCapacity();
        cache.offerChunk(workingSet);
        for (int i = 0; i < floor - 1; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }

        // Excess fully-free chunks
        SizeClassedChunk[] excessChunks = new SizeClassedChunk[excess];
        for (int i = 0; i < excess; i++) {
            excessChunks[i] = fullChunk();
            cache.offerChunk(excessChunks[i]);
        }

        // Single purge should evict all excess
        cache.tickPurge();

        for (SizeClassedChunk chunk : excessChunks) {
            verify(chunk, atLeastOnce()).recycleOrDeallocate(null, 0);
        }
        verify(workingSet, never()).recycleOrDeallocate(null, 0);
    }

    // --- Active chunk should not be evicted ---

    @Test
    void activeChunkWithCapacityIsNotEvicted() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        // Pad above retention floor
        for (int i = 0; i < cache.purgeRetentionFloor; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }

        // Active chunk: has capacity but is NOT fully free
        SizeClassedChunk active = chunkWithCapacity();
        cache.offerChunk(active);

        // Poll and re-offer multiple times
        for (int cycle = 0; cycle < 10; cycle++) {
            SizeClassedChunk polled = cache.forcePurge();
            assertSame(active, polled, "cycle " + cycle + ": active chunk should be polled");
            cache.offerChunk(active);
        }

        verify(active, never()).recycleOrDeallocate(null, 0);
        verify(active, never()).markToDeallocate();
    }

    // --- Signal A: exhausted → reusable via releaseSegment ---

    @Test
    void signalAMovesExhaustedToReusable() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        assertEquals(1, cache.exhaustedCount);
        assertEquals(0, cache.reusableCount);
        assertEquals(SizeClassedChunk.CACHE_EXHAUSTED, chunk.cacheListState);

        // Simulate Signal A: inline call from releaseSegment
        cache.moveToReusable(chunk);

        assertEquals(0, cache.exhaustedCount);
        assertEquals(1, cache.reusableCount);
        assertEquals(SizeClassedChunk.CACHE_REUSABLE, chunk.cacheListState);
    }

    // --- Signal B: reusable → eviction ---

    @Test
    void signalBEvictsAboveFloor() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        // Fill above retention floor
        for (int i = 0; i < cache.purgeRetentionFloor; i++) {
            cache.offerChunk(chunkWithCapacity());
        }

        SizeClassedChunk chunk = fullChunk();
        cache.offerChunk(chunk);
        int countBefore = cache.reusableCount;

        // Simulate Signal B: chunk is fully free, above floor → evict
        cache.evictIfAboveFloor(chunk);

        assertEquals(countBefore - 1, cache.reusableCount);
        verify(chunk).recycleOrDeallocate(null, 0);
    }

    @Test
    void signalBKeepsAtFloor() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        // Only one chunk — at or below floor
        SizeClassedChunk chunk = chunkWithCapacity();
        cache.offerChunk(chunk);

        cache.evictIfAboveFloor(chunk);

        // Chunk stays in reusable list
        assertEquals(1, cache.reusableCount);
        verify(chunk, never()).recycleOrDeallocate(null, 0);
    }

    // --- free: draining all chunks ---

    @Test
    void pollChunkCannotDrainExhaustedChunks() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithoutCapacity());

        int drained = 0;
        while (cache.pollChunk(0) != null) {
            drained++;
            if (drained > 100) {
                break;
            }
        }

        assertEquals(2, drained);
        assertEquals(2, cache.exhaustedCount);
    }

    // --- No scanning: a poll must not walk the exhausted list at all ---
    // The predecessor of the notification queue walked the exhausted list looking for chunks that
    // had regained capacity. It was the only discovery mechanism, so the walk up to the first hit
    // could not be bounded, and a mostly-exhausted list made every poll O(cache size).

    @Test
    void pollDoesNotTouchTheExhaustedList() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        int tail = 200;
        SizeClassedChunk[] rest = new SizeClassedChunk[tail];
        for (int i = 0; i < tail; i++) {
            rest[i] = chunkWithoutCapacity();
            cache.offerChunk(rest[i]);
        }
        // Offered last, so it sits at the head of the exhausted list. It is classified as
        // exhausted, then gains capacity -- exactly what a cross-thread segment return does.
        SizeClassedChunk notified = chunkWithoutCapacity();
        cache.offerChunk(notified);
        when(notified.hasRemainingCapacity()).thenReturn(true);
        cache.notifyHasCapacity(notified);

        for (SizeClassedChunk c : rest) {
            clearInvocations(c);
        }

        assertSame(notified, cache.pollChunk(256));

        int visited = 0;
        for (SizeClassedChunk c : rest) {
            visited += mockingDetails(c).getInvocations().size();
        }
        assertEquals(0, visited, "a poll must not visit any chunk on the exhausted list");
    }

    // --- Notification queue ---

    @Test
    void concurrentReturnsOnOneChunkQueueItAtMostOncePerDrain() throws Exception {
        final ThreadLocalSizeClassedChunkCache cache =
                new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        final SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        when(chunk.hasRemainingCapacity()).thenReturn(true);

        final int threads = 8;
        final int notificationsPerThread = 10000;
        final CyclicBarrier start = new CyclicBarrier(threads);
        Thread[] releasers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            releasers[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    for (int n = 0; n < notificationsPerThread; n++) {
                        cache.notifyHasCapacity(chunk);
                    }
                }
            });
            releasers[i].start();
        }
        for (Thread t : releasers) {
            t.join();
        }

        assertEquals(1, cache.pendingCount(), "80000 returns on one chunk must leave one note");

        cache.drainPending();
        assertEquals(0, cache.pendingCount());
        assertEquals(0, cache.exhaustedCount);
        assertEquals(1, cache.reusableCount);
    }

    @Test
    void returnLandingDuringProcessingRequeuesTheChunk() {
        final ThreadLocalSizeClassedChunkCache cache =
                new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        final SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        cache.notifyHasCapacity(chunk);
        assertEquals(1, cache.pendingCount());

        // A cross-thread return lands while the drain is inside processPending. Re-arming the link
        // only after processing would swallow this notification and strand the chunk.
        when(chunk.hasRemainingCapacity()).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) {
                cache.notifyHasCapacity(chunk);
                return true;
            }
        });

        cache.drainPending();

        assertEquals(1, cache.pendingCount(),
                "a return that lands during processing must leave the chunk queued again");
        assertNotNull(chunk.pendingNext);
    }

    @Test
    void drainMovesNotifiedExhaustedChunkToReusable() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        assertEquals(1, cache.exhaustedCount);

        when(chunk.hasRemainingCapacity()).thenReturn(true);
        cache.notifyHasCapacity(chunk);
        cache.drainPending();

        assertEquals(0, cache.exhaustedCount);
        assertEquals(1, cache.reusableCount);
        assertEquals(SizeClassedChunk.CACHE_REUSABLE, chunk.cacheListState);
        assertNull(chunk.pendingNext);
    }

    @Test
    void drainEvictsNotifiedReusableChunkThatBecameFullyFree() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        // Pad above the retention floor so eviction is allowed.
        for (int i = 0; i < cache.purgeRetentionFloor; i++) {
            cache.offerChunk(chunkWithCapacity());
        }
        SizeClassedChunk chunk = chunkWithCapacity();
        cache.offerChunk(chunk);
        int countBefore = cache.reusableCount;

        // Last outstanding segment comes back on a foreign thread.
        when(chunk.hasFullCapacity()).thenReturn(true);
        cache.notifyHasCapacity(chunk);
        cache.drainPending();

        assertEquals(countBefore - 1, cache.reusableCount);
        assertEquals(SizeClassedChunk.CACHE_NONE, chunk.cacheListState);
        verify(chunk).recycleOrDeallocate(null, 0);
    }

    // Regression for Invariant N property 4: offerChunk classifies a chunk by reading
    // hasRemainingCapacity(), which the MPSC free list lets any thread change at any instant. A
    // return that lands right after that read files the chunk as exhausted while it holds capacity,
    // and with nothing scanning any more, the releaser's note is the only thing that can fix it.
    @Test
    void chunkMisclassifiedAtOfferTimeStillBecomesReusable() {
        final ThreadLocalSizeClassedChunkCache cache =
                new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        final SizeClassedChunk chunk = chunkWithoutCapacity();
        // The releasing thread offered its segment just after offerChunk read the capacity, so the
        // chunk lands on the exhausted list, and the note is left behind.
        cache.offerChunk(chunk);
        assertEquals(SizeClassedChunk.CACHE_EXHAUSTED, chunk.cacheListState);
        cache.notifyHasCapacity(chunk);

        // The first drain looks before the segment is visible, and the release completes while
        // processPending is running -- so the drain must leave the chunk queued for another look.
        when(chunk.hasRemainingCapacity()).thenAnswer(new Answer<Boolean>() {
            private boolean landed;

            @Override
            public Boolean answer(InvocationOnMock invocation) {
                if (!landed) {
                    landed = true;
                    cache.notifyHasCapacity(chunk);
                    return false;
                }
                return true;
            }
        });

        cache.drainPending();
        assertEquals(SizeClassedChunk.CACHE_EXHAUSTED, chunk.cacheListState,
                "no capacity was visible yet, so the chunk must stay on the exhausted list");
        assertEquals(1, cache.pendingCount(), "the return that landed during processing must requeue");

        cache.drainPending();
        assertEquals(SizeClassedChunk.CACHE_REUSABLE, chunk.cacheListState);
        assertSame(chunk, cache.pollChunk(256));
    }

    @Test
    void drainIgnoresChunksThatLeftTheCache() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        when(chunk.hasRemainingCapacity()).thenReturn(true);
        cache.notifyHasCapacity(chunk);

        // Polled into a magazine before the drain got to it: the note is now stale.
        assertSame(chunk, cache.pollChunk(256));
        assertEquals(SizeClassedChunk.CACHE_NONE, chunk.cacheListState);
        assertEquals(0, cache.pendingCount());
        assertEquals(0, cache.exhaustedCount);
        assertEquals(0, cache.reusableCount);
    }

    @Test
    void freeDrainsAllChunks() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk cap1 = chunkWithCapacity();
        SizeClassedChunk cap2 = chunkWithCapacity();
        SizeClassedChunk noCap1 = chunkWithoutCapacity();
        SizeClassedChunk noCap2 = chunkWithoutCapacity();

        cache.offerChunk(cap1);
        cache.offerChunk(noCap1);
        cache.offerChunk(cap2);
        cache.offerChunk(noCap2);

        cache.free();

        assertTrue(cache.isEmpty());
        verify(cap1, atLeastOnce()).markToDeallocate();
        verify(cap2, atLeastOnce()).markToDeallocate();
        verify(noCap1, atLeastOnce()).markToDeallocate();
        verify(noCap2, atLeastOnce()).markToDeallocate();
    }
}
