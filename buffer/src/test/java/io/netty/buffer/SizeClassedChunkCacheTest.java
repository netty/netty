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

    // --- pollChunk fallback: exhausted chunks that gained capacity ---

    @Test
    void pollChunkFallbackFindsExhaustedChunkThatGainedCapacity() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        assertEquals(1, cache.exhaustedCount);

        // Simulate external segment return
        when(chunk.hasRemainingCapacity()).thenReturn(true);

        assertSame(chunk, cache.pollChunk(256));
        assertEquals(0, cache.exhaustedCount);
    }

    // --- forcePurge: runs scan + returns reusable chunk ---

    @Test
    void forcePurgeDetectsCapacityGainOnExhaustedChunks() {
        ThreadLocalSizeClassedChunkCache cache = new ThreadLocalSizeClassedChunkCache(128 * 1024, null, 0);

        SizeClassedChunk chunk = chunkWithoutCapacity();
        cache.offerChunk(chunk);
        assertEquals(1, cache.exhaustedCount);
        assertEquals(0, cache.reusableCount);

        // Simulate external return giving capacity
        when(chunk.hasRemainingCapacity()).thenReturn(true);

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
