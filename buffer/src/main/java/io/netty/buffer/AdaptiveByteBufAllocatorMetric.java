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

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Exposed metric for {@link AdaptiveByteBufAllocator}.
 */
@UnstableApi
public final class AdaptiveByteBufAllocatorMetric implements ByteBufAllocatorMetric {

    private final AdaptiveByteBufAllocator allocator;

    AdaptiveByteBufAllocatorMetric(AdaptiveByteBufAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public long usedHeapMemory() {
        return allocator.usedHeapMemory();
    }

    @Override
    public long usedDirectMemory() {
        return allocator.usedDirectMemory();
    }

    /**
     * Returns the number of bytes of heap memory that is currently pinned to heap buffers,
     * or {@code -1} if unknown. Pinned memory is the portion of chunk memory actively used
     * by live buffers, as opposed to total chunk capacity.
     */
    public long pinnedHeapMemory() {
        return allocator.pinnedHeapMemory();
    }

    /**
     * Returns the number of bytes of direct memory that is currently pinned to direct buffers,
     * or {@code -1} if unknown.
     */
    public long pinnedDirectMemory() {
        return allocator.pinnedDirectMemory();
    }

    /**
     * Returns the total number of heap buffer allocations.
     */
    public long numHeapAllocations() {
        return allocator.numHeapAllocations();
    }

    /**
     * Returns the total number of direct buffer allocations.
     */
    public long numDirectAllocations() {
        return allocator.numDirectAllocations();
    }

    /**
     * Returns the total number of heap buffer deallocations.
     */
    public long numHeapDeallocations() {
        return allocator.numHeapDeallocations();
    }

    /**
     * Returns the total number of direct buffer deallocations.
     */
    public long numDirectDeallocations() {
        return allocator.numDirectDeallocations();
    }

    /**
     * Returns the total number of heap fallback allocations (allocations that bypassed the pool).
     */
    public long numHeapFallbackAllocations() {
        return allocator.numHeapFallbackAllocations();
    }

    /**
     * Returns the total number of direct fallback allocations (allocations that bypassed the pool).
     */
    public long numDirectFallbackAllocations() {
        return allocator.numDirectFallbackAllocations();
    }

    /**
     * Returns the number of active heap chunks.
     */
    public long numHeapActiveChunks() {
        return allocator.numHeapActiveChunks();
    }

    /**
     * Returns the number of active direct chunks.
     */
    public long numDirectActiveChunks() {
        return allocator.numDirectActiveChunks();
    }

    /**
     * Returns the per-magazine-group metrics for the heap pool.
     */
    public List<AdaptiveMagazineGroupMetric> heapMagazineGroups() {
        return allocator.heapMagazineGroups();
    }

    /**
     * Returns the per-magazine-group metrics for the direct pool.
     */
    public List<AdaptiveMagazineGroupMetric> directMagazineGroups() {
        return allocator.directMagazineGroups();
    }

    /**
     * Returns the status of the allocator (which contains all metrics) as string. Be aware this may be expensive
     * and so should not be called too frequently.
     */
    public String dumpStats() {
        StringBuilder buf = new StringBuilder(4096);
        buf.append("AdaptiveByteBufAllocator:").append(StringUtil.NEWLINE);
        buf.append("  used heap memory: ").append(usedHeapMemory()).append(StringUtil.NEWLINE);
        buf.append("  used direct memory: ").append(usedDirectMemory()).append(StringUtil.NEWLINE);
        buf.append("  pinned heap memory: ").append(pinnedHeapMemory()).append(StringUtil.NEWLINE);
        buf.append("  pinned direct memory: ").append(pinnedDirectMemory()).append(StringUtil.NEWLINE);

        appendPoolStats(buf, "Heap", numHeapAllocations(), numHeapDeallocations(),
                numHeapFallbackAllocations(), numHeapActiveChunks(), usedHeapMemory(), heapMagazineGroups());
        appendPoolStats(buf, "Direct", numDirectAllocations(), numDirectDeallocations(),
                numDirectFallbackAllocations(), numDirectActiveChunks(), usedDirectMemory(),
                directMagazineGroups());

        return buf.toString();
    }

    private static void appendPoolStats(StringBuilder buf, String name,
                                         long allocs, long deallocs, long fallback,
                                         long activeChunks, long chunkCapacity,
                                         List<AdaptiveMagazineGroupMetric> groups) {
        buf.append("  ").append(name).append(" pool:").append(StringUtil.NEWLINE);
        buf.append("    allocations: ").append(allocs);
        buf.append(", deallocations: ").append(deallocs);
        buf.append(", fallback: ").append(fallback).append(StringUtil.NEWLINE);
        buf.append("    active chunks: ").append(activeChunks);
        buf.append(", total chunk capacity: ").append(chunkCapacity).append(StringUtil.NEWLINE);

        buf.append("    Magazine groups (").append(groups.size()).append("):").append(StringUtil.NEWLINE);
        for (AdaptiveMagazineGroupMetric group : groups) {
            buf.append("      ");
            int segSize = group.segmentSize();
            if (segSize > 0) {
                buf.append('[').append(segSize).append("B segments, ");
                buf.append(group.chunkSize()).append("B chunks]");
            } else {
                buf.append("[large/buddy]");
            }
            buf.append(" mags=").append(group.numMagazines());
            buf.append(", allocs=").append(group.numAllocations());
            buf.append(", chunks=").append(group.numActiveChunks());
            buf.append('/').append(group.numChunkAllocations()).append(" created");
            buf.append(" (").append(group.activeChunkCapacity()).append("B)");
            buf.append(StringUtil.NEWLINE);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(StringUtil.simpleClassName(this))
                .append("(usedHeapMemory: ").append(usedHeapMemory())
                .append("; usedDirectMemory: ").append(usedDirectMemory())
                .append("; pinnedHeapMemory: ").append(pinnedHeapMemory())
                .append("; pinnedDirectMemory: ").append(pinnedDirectMemory())
                .append("; numHeapAllocations: ").append(numHeapAllocations())
                .append("; numDirectAllocations: ").append(numDirectAllocations())
                .append("; numHeapActiveChunks: ").append(numHeapActiveChunks())
                .append("; numDirectActiveChunks: ").append(numDirectActiveChunks())
                .append(')');
        return sb.toString();
    }
}
