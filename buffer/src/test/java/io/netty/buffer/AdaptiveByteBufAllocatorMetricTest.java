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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveByteBufAllocatorMetricTest {

    @Test
    void metricStartsAtZero() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        assertEquals(0, metric.usedHeapMemory());
        assertEquals(0, metric.usedDirectMemory());
        assertEquals(0, metric.pinnedHeapMemory());
        assertEquals(0, metric.pinnedDirectMemory());
        assertEquals(0, metric.numHeapAllocations());
        assertEquals(0, metric.numDirectAllocations());
        assertEquals(0, metric.numHeapDeallocations());
        assertEquals(0, metric.numDirectDeallocations());
        assertEquals(0, metric.numHeapFallbackAllocations());
        assertEquals(0, metric.numDirectFallbackAllocations());
        assertEquals(0, metric.numHeapActiveChunks());
        assertEquals(0, metric.numDirectActiveChunks());
    }

    @Test
    void allocationIncrementsCounters() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        ByteBuf buf = allocator.directBuffer(256);

        assertEquals(1, metric.numDirectAllocations());
        assertEquals(0, metric.numDirectDeallocations());
        assertTrue(metric.usedDirectMemory() > 0, "usedDirectMemory should be > 0 after allocation");
        assertTrue(metric.pinnedDirectMemory() > 0, "pinnedDirectMemory should be > 0 after allocation");
        assertTrue(metric.numDirectActiveChunks() > 0, "should have at least one active chunk");

        // Heap should be untouched
        assertEquals(0, metric.numHeapAllocations());
        assertEquals(0, metric.usedHeapMemory());

        buf.release();
    }

    @Test
    void deallocationIncrementsCounters() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        ByteBuf buf = allocator.heapBuffer(128);
        assertEquals(1, metric.numHeapAllocations());
        long pinnedAfterAlloc = metric.pinnedHeapMemory();
        assertTrue(pinnedAfterAlloc > 0);

        buf.release();

        assertEquals(1, metric.numHeapAllocations());
        assertEquals(1, metric.numHeapDeallocations());
        assertEquals(0, metric.pinnedHeapMemory(), "pinnedHeapMemory should be 0 after releasing all buffers");
    }

    @Test
    void multipleAllocationsTrackCorrectly() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        int count = 10;
        ByteBuf[] bufs = new ByteBuf[count];
        for (int i = 0; i < count; i++) {
            bufs[i] = allocator.directBuffer(64);
        }

        assertEquals(count, metric.numDirectAllocations());
        assertEquals(0, metric.numDirectDeallocations());
        assertTrue(metric.pinnedDirectMemory() > 0);

        for (int i = 0; i < count; i++) {
            bufs[i].release();
        }

        assertEquals(count, metric.numDirectAllocations());
        assertEquals(count, metric.numDirectDeallocations());
        assertEquals(0, metric.pinnedDirectMemory());
    }

    @Test
    void pinnedMemoryLessThanOrEqualUsedMemory() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        ByteBuf buf = allocator.directBuffer(512);

        long used = metric.usedDirectMemory();
        long pinned = metric.pinnedDirectMemory();
        assertTrue(pinned <= used,
                "pinned (" + pinned + ") should be <= used (" + used + ")");
        assertTrue(pinned > 0);

        buf.release();

        // After release, used stays (chunk still alive in magazine), pinned drops to 0
        assertTrue(metric.usedDirectMemory() > 0, "chunk should still be held by magazine");
        assertEquals(0, metric.pinnedDirectMemory());
    }

    @Test
    void reallocationTracksPinnedBytesCorrectly() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        ByteBuf buf = allocator.directBuffer(64, Integer.MAX_VALUE);
        long pinnedBefore = metric.pinnedDirectMemory();
        assertTrue(pinnedBefore > 0);

        // Force a reallocation by growing beyond the current segment
        buf.capacity(32768);
        long pinnedAfter = metric.pinnedDirectMemory();
        assertTrue(pinnedAfter >= 32768,
                "pinnedDirectMemory (" + pinnedAfter + ") should be >= 32768 after capacity increase");

        buf.release();
        assertEquals(0, metric.pinnedDirectMemory());
    }

    @Test
    void magazineGroupMetricsAreExposed() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        List<AdaptiveMagazineGroupMetric> heapGroups = metric.heapMagazineGroups();
        List<AdaptiveMagazineGroupMetric> directGroups = metric.directMagazineGroups();

        // 16 size classes + 1 buddy = 17 groups per pool
        assertEquals(17, heapGroups.size());
        assertEquals(17, directGroups.size());

        // First 16 groups should have positive segment sizes matching the size classes
        int[] sizeClasses = AdaptivePoolingAllocator.getSizeClasses();
        for (int i = 0; i < sizeClasses.length; i++) {
            AdaptiveMagazineGroupMetric group = directGroups.get(i);
            assertEquals(sizeClasses[i], group.segmentSize());
            assertTrue(group.chunkSize() > 0);
            assertTrue(group.numMagazines() >= 1);
            assertFalse(group.isThreadLocal());
        }

        // Last group should be the buddy (large) group
        AdaptiveMagazineGroupMetric buddyGroup = directGroups.get(16);
        assertEquals(-1, buddyGroup.segmentSize());
        assertEquals(-1, buddyGroup.chunkSize());
    }

    @Test
    void magazineGroupAllocationCountsMatchPool() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        // Allocate buffers of different sizes to hit different groups
        ByteBuf small = allocator.directBuffer(32);
        ByteBuf medium = allocator.directBuffer(512);
        ByteBuf large = allocator.directBuffer(4096);

        // Total allocations should match sum of group allocations
        long totalFromGroups = 0;
        for (AdaptiveMagazineGroupMetric group : metric.directMagazineGroups()) {
            totalFromGroups += group.numAllocations();
        }
        assertEquals(metric.numDirectAllocations(), totalFromGroups,
                "sum of group allocations should match total");

        small.release();
        medium.release();
        large.release();
    }

    @Test
    void activeChunksTrackCorrectly() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        assertEquals(0, metric.numDirectActiveChunks());

        ByteBuf buf = allocator.directBuffer(128);
        assertTrue(metric.numDirectActiveChunks() > 0);

        buf.release();
        // Chunks may still be active (held by magazines) after buffer release
        // so we just verify it's non-negative
        assertTrue(metric.numDirectActiveChunks() >= 0);
    }

    @Test
    void dumpStatsProducesNonEmptyOutput() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        // Allocate a buffer so there's something to report
        ByteBuf buf = allocator.directBuffer(256);

        String stats = metric.dumpStats();
        assertNotNull(stats);
        assertTrue(stats.contains("AdaptiveByteBufAllocator:"));
        assertTrue(stats.contains("Heap pool:"));
        assertTrue(stats.contains("Direct pool:"));
        assertTrue(stats.contains("Magazine groups"));
        assertTrue(stats.contains("[large/buddy]"));
        // Should contain at least one size-classed group
        assertTrue(stats.contains("B segments,"));

        buf.release();
    }

    @Test
    void toStringProducesSummary() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        AdaptiveByteBufAllocatorMetric metric = (AdaptiveByteBufAllocatorMetric) allocator.metric();

        String str = metric.toString();
        assertNotNull(str);
        assertTrue(str.contains("usedHeapMemory:"));
        assertTrue(str.contains("usedDirectMemory:"));
        assertTrue(str.contains("pinnedHeapMemory:"));
        assertTrue(str.contains("pinnedDirectMemory:"));
    }
}
