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

import io.netty.util.internal.UnstableApi;

/**
 * Expose metrics for a magazine group within an {@link AdaptiveByteBufAllocator}.
 * <p>
 * A magazine group manages allocation for a specific size class (e.g. 512-byte segments)
 * or for large/buddy allocations. Each group contains one or more magazines that spread
 * allocation contention across threads.
 */
@UnstableApi
public interface AdaptiveMagazineGroupMetric {

    /**
     * Returns the segment size in bytes for this group, or {@code -1} if this group uses
     * variable-size (buddy) allocation.
     */
    int segmentSize();

    /**
     * Returns the fixed chunk size in bytes for this group, or {@code -1} if this group uses
     * variable-size chunks (buddy allocation).
     */
    int chunkSize();

    /**
     * Returns the current number of magazines in this group. This may increase over time
     * as the allocator adapts to contention.
     */
    int numMagazines();

    /**
     * Returns {@code true} if this group is thread-local (owned by a single event loop thread).
     */
    boolean isThreadLocal();

    /**
     * Returns the total number of buffer allocations served by this group.
     */
    long numAllocations();

    /**
     * Returns the total number of chunks allocated for this group.
     */
    long numChunkAllocations();

    /**
     * Returns the total number of chunks deallocated from this group.
     */
    long numChunkDeallocations();

    /**
     * Returns the number of currently active (live) chunks in this group.
     */
    long numActiveChunks();

    /**
     * Returns the total capacity in bytes of all active chunks in this group.
     */
    long activeChunkCapacity();
}
