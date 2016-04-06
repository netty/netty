/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import java.util.List;

/**
 * Expose metrics for an arena.
 */
public interface PoolArenaMetric {

    /**
     * Returns the number of tiny sub-pages for the arena.
     */
    int numTinySubpages();

    /**
     * Returns the number of small sub-pages for the arena.
     */
    int numSmallSubpages();

    /**
     * Returns the number of chunk lists for the arena.
     */
    int numChunkLists();

    /**
     * Returns an unmodifiable {@link List} which holds {@link PoolSubpageMetric}s for tiny sub-pages.
     */
    List<PoolSubpageMetric> tinySubpages();

    /**
     * Returns an unmodifiable {@link List} which holds {@link PoolSubpageMetric}s for small sub-pages.
     */
    List<PoolSubpageMetric> smallSubpages();

    /**
     * Returns an unmodifiable {@link List} which holds {@link PoolChunkListMetric}s.
     */
    List<PoolChunkListMetric> chunkLists();

    /**
     * Return the number of allocations done via the arena. This includes all sizes.
     */
    long numAllocations();

    /**
     * Return the number of tiny allocations done via the arena.
     */
    long numTinyAllocations();

    /**
     * Return the number of small allocations done via the arena.
     */
    long numSmallAllocations();

    /**
     * Return the number of normal allocations done via the arena.
     */
    long numNormalAllocations();

    /**
     * Return the number of huge allocations done via the arena.
     */
    long numHugeAllocations();

    /**
     * Return the number of deallocations done via the arena. This includes all sizes.
     */
    long numDeallocations();

    /**
     * Return the number of tiny deallocations done via the arena.
     */
    long numTinyDeallocations();

    /**
     * Return the number of small deallocations done via the arena.
     */
    long numSmallDeallocations();

    /**
     * Return the number of normal deallocations done via the arena.
     */
    long numNormalDeallocations();

    /**
     * Return the number of huge deallocations done via the arena.
     */
    long numHugeDeallocations();

    /**
     * Return the number of currently active allocations.
     */
    long numActiveAllocations();

    /**
     * Return the number of currently active tiny allocations.
     */
    long numActiveTinyAllocations();

    /**
     * Return the number of currently active small allocations.
     */
    long numActiveSmallAllocations();

    /**
     * Return the number of currently active normal allocations.
     */
    long numActiveNormalAllocations();

    /**
     * Return the number of currently active huge allocations.
     */
    long numActiveHugeAllocations();

    /**
     * Return the number of active bytes that are currently allocated by the arena.
     */
    long numActiveBytes();
}
