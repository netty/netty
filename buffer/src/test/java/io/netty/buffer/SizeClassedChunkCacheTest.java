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
import io.netty.buffer.AdaptivePoolingAllocator.SizeClassedChunkCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
        // All segments available → purge ages it.
        SizeClassedChunk chunk = mock(SizeClassedChunk.class);
        when(chunk.remainingCapacity()).thenReturn(4096);
        when(chunk.capacity()).thenReturn(4096);
        when(chunk.hasRemainingCapacity()).thenReturn(true);
        when(chunk.hasFullCapacity()).thenReturn(true);
        return chunk;
    }

    // --- purge: selection (both caches) ---

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void purgeSelectsFirstChunkWithCapacity(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        SizeClassedChunk noCap = chunkWithoutCapacity();
        SizeClassedChunk cap = chunkWithCapacity();
        cache.offerChunk(noCap);
        cache.offerChunk(cap);

        assertSame(cap, cache.forcePurge());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void purgeReturnsNullWhenCacheIsEmpty(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);
        assertNull(cache.forcePurge());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void purgeReturnsNullWhenNoChunkHasCapacity(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);
        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(chunkWithoutCapacity());

        assertNull(cache.forcePurge());
    }

    // --- purge: epoch aging and eviction (both caches) ---

    @Test
    void fullChunkAgesEachPurgeAndIsEvictedPastThresholdThreadLocal() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(true);

        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }
        SizeClassedChunk workingSet = chunkWithCapacity();
        cache.offerChunk(workingSet);
        SizeClassedChunk idle = fullChunk();
        cache.offerChunk(idle);

        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD; i++) {
            SizeClassedChunk polled = cache.forcePurge();
            assertSame(workingSet, polled);
            cache.offerChunk(workingSet);
            assertEquals(i + 1, idle.purgeEpoch);
            verify(idle, never()).markToDeallocate();
        }
        SizeClassedChunk polled = cache.forcePurge();
        assertSame(workingSet, polled);
        verify(idle).markToDeallocate();
    }

    @Test
    void fullChunkAgesAndIsEventuallyEvictedShared() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(false);

        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }
        SizeClassedChunk workingSet = chunkWithCapacity();
        cache.offerChunk(workingSet);
        SizeClassedChunk idle = fullChunk();
        cache.offerChunk(idle);

        int maxCycles = (AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD + 1) * 3;
        for (int i = 0; i < maxCycles; i++) {
            SizeClassedChunk polled = cache.forcePurge();
            if (polled != null) {
                cache.offerChunk(polled);
            }
        }
        verify(idle, atLeastOnce()).markToDeallocate();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nonFullChunkDoesNotAge(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        SizeClassedChunk chunk = chunkWithCapacity();
        cache.offerChunk(chunk);

        cache.forcePurge();
        assertEquals(0, chunk.purgeEpoch);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void selectedFullChunkHasEpochReset(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        SizeClassedChunk chunk = fullChunk();
        cache.offerChunk(chunk);

        SizeClassedChunk selected = cache.forcePurge();
        assertSame(chunk, selected);
        assertEquals(0, selected.purgeEpoch);
    }

    // --- scanForCapacity: fallback (both caches) ---

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void scanForCapacityFallbackFindsChunkThatGainedCapacity(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);

        // Purge: no capacity, nothing selected
        assertNull(cache.forcePurge());

        // External segment return gives the chunk capacity
        when(chunk.hasRemainingCapacity()).thenReturn(true);

        assertSame(chunk, cache.pollChunk(256));
    }

    // --- thread-local only: capacity-first ordering ---

    @Test
    void purgeMovesCapacityChunksBeforeNoCapacityChunks() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(true);

        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithCapacity());

        // Purge selects one capacity chunk, partitions the rest: [cap, cap | noCap, noCap]
        SizeClassedChunk selected = cache.forcePurge();
        assertNotNull(selected);
        assertTrue(selected.hasRemainingCapacity());

        // Both remaining capacity chunks come out before any no-capacity chunk
        assertTrue(cache.pollChunk(256).hasRemainingCapacity());
        assertTrue(cache.pollChunk(256).hasRemainingCapacity());
        assertNull(cache.pollChunk(256));
    }

    @Test
    void scanForCapacityUsesO1FastPathAfterPurge() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(true);

        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithoutCapacity());

        // Purge partitions: [cap | noCap], selects one cap
        assertNotNull(cache.forcePurge());

        // Next poll hits the O(1) fast path — capacity chunk is at head
        SizeClassedChunk fast = cache.pollChunk(256);
        assertNotNull(fast);
        assertTrue(fast.hasRemainingCapacity());
    }

    // --- thread-local only: ring buffer mechanics ---

    @Test
    void offerGrowsRingWhenFull() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(true);

        // Initial ring size is 8 — offer 9 to trigger growth
        for (int i = 0; i < 9; i++) {
            cache.offerChunk(chunkWithCapacity());
        }

        // Purge selects one, 8 remain — all should be retrievable
        assertNotNull(cache.forcePurge());
        for (int i = 0; i < 8; i++) {
            assertNotNull(cache.pollChunk(256));
        }
        assertNull(cache.pollChunk(256));
    }

    @Test
    void purgeHandlesWrappedRingCorrectly() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(true);

        // Fill with 4, purge (linearizes to head=0), consume 3 to advance head
        for (int i = 0; i < 4; i++) {
            cache.offerChunk(chunkWithCapacity());
        }
        cache.forcePurge();
        cache.pollChunk(256);
        cache.pollChunk(256);
        cache.pollChunk(256);

        // Offer more — tail wraps around past the array end
        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithoutCapacity());
        cache.offerChunk(chunkWithCapacity());

        // Purge with wrapped ring should still partition correctly
        SizeClassedChunk selected = cache.forcePurge();
        assertNotNull(selected);
        assertTrue(selected.hasRemainingCapacity());

        // Remaining capacity chunk at head
        SizeClassedChunk next = cache.pollChunk(256);
        if (next != null) {
            assertTrue(next.hasRemainingCapacity());
        }
    }

    @Test
    void wrappedRingCompactionLeavesNoStaleReferences() {
        AdaptivePoolingAllocator.ThreadLocalSizeClassedChunkCache cache =
                (AdaptivePoolingAllocator.ThreadLocalSizeClassedChunkCache)
                        SizeClassedChunkCache.create(true);

        // Fill 6 slots of the initial ring (size=8), purge, drain to advance head
        for (int i = 0; i < 6; i++) {
            cache.offerChunk(chunkWithCapacity());
        }
        cache.forcePurge();
        while (cache.pollChunk(256) != null) {
            // drain
        }
        assertTrue(cache.head > 0, "head should have advanced past 0");

        // Offer 4 chunks — wraps past the array boundary
        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(chunkWithCapacity());
        cache.offerChunk(fullChunk());
        cache.offerChunk(fullChunk());
        assertTrue(cache.tail < cache.head,
                "ring should wrap: tail=" + cache.tail + " < head=" + cache.head);

        // Purge partitions on the wrapped ring
        SizeClassedChunk polled = cache.forcePurge();
        assertNotNull(polled);

        // Verify the backing array: exactly count non-null entries, no stale refs
        int nonNull = 0;
        for (int i = 0; i < cache.chunks.length; i++) {
            if (cache.chunks[i] != null) {
                nonNull++;
            }
        }
        assertEquals(cache.count, nonNull,
                "backing array should have exactly count=" + cache.count
                        + " non-null entries, but found " + nonNull);
    }

    // --- bursty traffic: idle chunks are eventually evicted ---

    @Test
    void cacheSettlesAtRetentionFloorAfterBurstThreadLocal() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(true);

        int floor = AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE;
        int excess = 10;

        SizeClassedChunk workingSet = chunkWithCapacity();
        cache.offerChunk(workingSet);
        for (int i = 0; i < floor - 1; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }
        SizeClassedChunk[] excessChunks = new SizeClassedChunk[excess];
        for (int i = 0; i < excess; i++) {
            excessChunks[i] = fullChunk();
            cache.offerChunk(excessChunks[i]);
        }

        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD + 1; i++) {
            SizeClassedChunk polled = cache.forcePurge();
            assertSame(workingSet, polled);
            cache.offerChunk(workingSet);
        }

        for (SizeClassedChunk chunk : excessChunks) {
            verify(chunk, atLeastOnce()).markToDeallocate();
        }
        verify(workingSet, never()).markToDeallocate();
    }

    @Test
    void cacheSettlesAfterBurstShared() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(false);

        int excess = 10;
        SizeClassedChunk workingSet = chunkWithCapacity();
        cache.offerChunk(workingSet);
        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE - 1; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }
        SizeClassedChunk[] excessChunks = new SizeClassedChunk[excess];
        for (int i = 0; i < excess; i++) {
            excessChunks[i] = fullChunk();
            cache.offerChunk(excessChunks[i]);
        }

        int maxCycles = (AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD + 1) * 3;
        for (int i = 0; i < maxCycles; i++) {
            SizeClassedChunk polled = cache.forcePurge();
            if (polled != null) {
                cache.offerChunk(polled);
            }
        }

        for (SizeClassedChunk chunk : excessChunks) {
            verify(chunk, atLeastOnce()).markToDeallocate();
        }
    }

    // --- epoch aging with working set ---
    // Scan resets epoch on pick (the chunk is being used). Partition sub-ordering puts
    // epoch=0 (recently used) at head, epoch>0 (idle) behind. Scan prefers head, so
    // idle chunks age undisturbed behind the working set.

    @Test
    void excessFullChunksAgeWhileWorkingSetIsPreferredThreadLocal() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(true);

        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }
        SizeClassedChunk workingSet = chunkWithCapacity();
        cache.offerChunk(workingSet);
        int excess = 3;
        SizeClassedChunk[] idleChunks = new SizeClassedChunk[excess];
        for (int i = 0; i < excess; i++) {
            idleChunks[i] = fullChunk();
            cache.offerChunk(idleChunks[i]);
        }

        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD + 1; i++) {
            SizeClassedChunk polled = cache.forcePurge();
            assertSame(workingSet, polled, "cycle " + i + ": scan should prefer working-set chunk");
            cache.offerChunk(workingSet);
        }

        for (SizeClassedChunk idle : idleChunks) {
            verify(idle, atLeastOnce()).markToDeallocate();
        }
        verify(workingSet, never()).markToDeallocate();
    }

    @Test
    void excessFullChunksEventuallyEvictedShared() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(false);

        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }
        SizeClassedChunk workingSet = chunkWithCapacity();
        cache.offerChunk(workingSet);
        int excess = 3;
        SizeClassedChunk[] idleChunks = new SizeClassedChunk[excess];
        for (int i = 0; i < excess; i++) {
            idleChunks[i] = fullChunk();
            cache.offerChunk(idleChunks[i]);
        }

        int maxCycles = (AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD + 1) * 3;
        for (int i = 0; i < maxCycles; i++) {
            SizeClassedChunk polled = cache.forcePurge();
            if (polled != null) {
                cache.offerChunk(polled);
            }
        }

        for (SizeClassedChunk idle : idleChunks) {
            verify(idle, atLeastOnce()).markToDeallocate();
        }
    }

    // --- full-but-active chunk must not be prematurely evicted ---
    // A chunk that is polled every purge cycle but whose buffers are short-lived
    // (all segments return before next purge) looks "full" (remaining == capacity)
    // at purge time. Purge must not treat it as idle.

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void activeChunkWithShortLivedBuffersShouldNotBeEvicted(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        // Pad above retention floor so eviction is allowed
        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }

        // Full chunk: remaining==capacity>0, has capacity.
        // Simulates short-lived buffers: chunk polled, used, all segments return before next purge.
        SizeClassedChunk active = fullChunk();
        cache.offerChunk(active);

        int cycles = AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD + 2;
        for (int cycle = 0; cycle < cycles; cycle++) {
            SizeClassedChunk polled = cache.forcePurge();
            assertSame(active, polled, "cycle " + cycle + ": chunk should be polled, not evicted");
            assertEquals(0, polled.purgeEpoch,
                    "cycle " + cycle + ": actively-used chunk epoch should be reset");
            cache.offerChunk(active);
        }

        verify(active, never()).markToDeallocate();
    }

    // --- shared cache: concurrent scanForCapacity must not livelock ---

    @Test
    void concurrentScansTerminateWhenNoCapacity() throws Exception {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(false);

        // Fill with no-capacity chunks — no scan can find anything
        for (int i = 0; i < 10; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }

        int threadCount = 4;
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 1000; i++) {
                        assertNull(cache.pollChunk(256));
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        // With the == sentinel check, threads could livelock here.
        // With >= ordering, all scans terminate promptly.
        boolean finished = doneLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(finished, "Concurrent scans should terminate within 30 seconds, not livelock");
        assertNull(error.get());
    }

    // --- free: draining all chunks (thread-local) ---

    @Test
    void pollChunkCannotDrainNoCapChunksThreadLocal() {
        AdaptivePoolingAllocator.ThreadLocalSizeClassedChunkCache cache =
                new AdaptivePoolingAllocator.ThreadLocalSizeClassedChunkCache();

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

        // pollChunk uses scanForCapacity which skips noCap chunks — they're stuck.
        // This is why free() is needed instead of a pollChunk drain loop.
        assertEquals(2, cache.count);
        verify(cache.chunks[cache.head], never()).markToDeallocate();
    }

    @Test
    void freeDrainsAllChunksIncludingNoCapThreadLocal() {
        AdaptivePoolingAllocator.ThreadLocalSizeClassedChunkCache cache =
                new AdaptivePoolingAllocator.ThreadLocalSizeClassedChunkCache();

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

    @Test
    void freeDrainsAllChunksShared() {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(false);

        SizeClassedChunk cap = chunkWithCapacity();
        SizeClassedChunk noCap = chunkWithoutCapacity();

        cache.offerChunk(cap);
        cache.offerChunk(noCap);

        cache.free();

        assertTrue(cache.isEmpty());
        verify(cap, atLeastOnce()).markToDeallocate();
        verify(noCap, atLeastOnce()).markToDeallocate();
    }
}
