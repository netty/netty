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
        return chunk;
    }

    private static SizeClassedChunk chunkWithoutCapacity() {
        SizeClassedChunk chunk = mock(SizeClassedChunk.class);
        when(chunk.remainingCapacity()).thenReturn(0);
        when(chunk.capacity()).thenReturn(4096);
        when(chunk.hasRemainingCapacity()).thenReturn(false);
        return chunk;
    }

    private static SizeClassedChunk idleChunk() {
        // remaining == capacity → purge ages it. remaining == 0 → never selected.
        SizeClassedChunk chunk = mock(SizeClassedChunk.class);
        when(chunk.remainingCapacity()).thenReturn(0);
        when(chunk.capacity()).thenReturn(0);
        when(chunk.hasRemainingCapacity()).thenReturn(false);
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void idleChunkAgesEachPurgeAndIsEvictedPastThreshold(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        // Pad above retention floor so eviction is allowed
        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }
        SizeClassedChunk idle = idleChunk();
        cache.offerChunk(idle);

        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD; i++) {
            cache.forcePurge();
            assertEquals(i + 1, idle.purgeEpoch);
            verify(idle, never()).markToDeallocate();
        }

        cache.forcePurge();
        verify(idle).markToDeallocate();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void purgeResetsEpochForNonIdleChunks(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        SizeClassedChunk chunk = chunkWithCapacity();
        chunk.purgeEpoch = 5;
        cache.offerChunk(chunk);

        cache.forcePurge();
        assertEquals(0, chunk.purgeEpoch);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void selectedChunkHasEpochReset(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        SizeClassedChunk chunk = chunkWithCapacity();
        chunk.purgeEpoch = 2;
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

    // --- bursty traffic: idle chunks are eventually evicted ---

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cacheSettlesAtRetentionFloorAfterBurst(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        int floor = AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE;
        int excess = 10;
        int total = floor + excess;

        // All chunks are idle — after purge, only the floor should survive
        SizeClassedChunk[] allChunks = new SizeClassedChunk[total];
        for (int i = 0; i < total; i++) {
            allChunks[i] = idleChunk();
            cache.offerChunk(allChunks[i]);
        }

        // Run enough purge cycles to evict all excess
        int purgesNeeded = AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD + 1;
        for (int i = 0; i < purgesNeeded; i++) {
            cache.forcePurge();
        }

        // Exactly `excess` chunks evicted, exactly `floor` retained
        int evicted = 0;
        int retained = 0;
        for (SizeClassedChunk chunk : allChunks) {
            try {
                verify(chunk, atLeastOnce()).markToDeallocate();
                evicted++;
            } catch (AssertionError e) {
                retained++;
            }
        }
        assertEquals(excess, evicted, "excess chunks should be evicted");
        assertEquals(floor, retained, "retention floor chunks should survive");
    }

    // --- epoch aging: polled chunks carry their epoch (no reset on poll) ---
    // Thread-local: partition sub-ordering puts epoch=0 at head, epoch>0 behind.
    //   Epoch reset on poll would defeat aging when polls > chunks (e.g., 2-core: 16 polls, 7 chunks).
    // Shared: LRU preference in scanForCapacity achieves the same without epoch reset.

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void excessChunksAgeAndEvictDespiteBeingPolled(boolean threadLocal) {
        SizeClassedChunkCache cache = SizeClassedChunkCache.create(threadLocal);

        // Pad to retention floor
        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_REUSE_QUEUE; i++) {
            cache.offerChunk(chunkWithoutCapacity());
        }

        // Add excess idle chunks above the floor
        int excess = 3;
        SizeClassedChunk[] idleChunks = new SizeClassedChunk[excess];
        for (int i = 0; i < excess; i++) {
            idleChunks[i] = idleChunk();
            cache.offerChunk(idleChunks[i]);
        }

        // Run enough purge cycles for excess to age past threshold and be evicted
        for (int i = 0; i < AdaptivePoolingAllocator.CHUNK_PURGE_THRESHOLD + 1; i++) {
            cache.forcePurge();
        }

        // All excess should be evicted — cache settles at the retention floor
        for (SizeClassedChunk idle : idleChunks) {
            verify(idle, atLeastOnce()).markToDeallocate();
        }
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
        boolean finished = doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(finished, "Concurrent scans should terminate within 5 seconds, not livelock");
        assertNull(error.get());
    }
}
