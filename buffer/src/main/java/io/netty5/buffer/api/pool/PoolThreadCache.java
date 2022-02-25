/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.pool;

import io.netty5.buffer.api.pool.PoolArena.SizeClass;
import io.netty5.util.internal.MathUtil;
import io.netty5.util.internal.ObjectPool;
import io.netty5.util.internal.ObjectPool.Handle;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty5.buffer.api.pool.PoolArena.SizeClass.Normal;
import static io.netty5.buffer.api.pool.PoolArena.SizeClass.Small;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Acts a Thread cache for allocations. This implementation is modelled after
 * <a href="https://people.freebsd.org/~jasone/jemalloc/bsdcan2006/jemalloc.pdf">jemalloc</a> and the described
 * techniques of
 * <a href="https://www.facebook.com/notes/facebook-engineering/scalable-memory-allocation-using-jemalloc/480222803919">
 * Scalable memory allocation using jemalloc</a>.
 */
final class PoolThreadCache {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PoolThreadCache.class);
    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    final AtomicInteger arenaReferenceCounter;

    private final WeakReference<Cache> cacheRef;

    private final int freeSweepAllocationThreshold;

    private int allocations;

    PoolThreadCache(PoolArena arena,
                    int smallCacheSize, int normalCacheSize, int maxCachedBufferCapacity,
                    int freeSweepAllocationThreshold) {
        checkPositiveOrZero(maxCachedBufferCapacity, "maxCachedBufferCapacity");
        this.freeSweepAllocationThreshold = freeSweepAllocationThreshold;
        if (arena != null) {
            // Create the caches for the heap allocations
            MemoryRegionCache[] smallSubPageCaches = createSubPageCaches(
                    smallCacheSize, arena.numSmallSubpagePools);

            MemoryRegionCache[] normalCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, arena);

            // Only check if there are caches in use.
            if ((smallSubPageCaches != null || normalCaches != null)
                && freeSweepAllocationThreshold < 1) {
                throw new IllegalArgumentException("freeSweepAllocationThreshold: "
                                                   + freeSweepAllocationThreshold + " (expected: > 0)");
            }

            cacheRef = new WeakReference<>(new Cache(arena, smallSubPageCaches, normalCaches));
            arenaReferenceCounter = arena.numThreadCaches;
            arenaReferenceCounter.getAndIncrement();
        } else {
            // No heapArea is configured so just null out all caches
            cacheRef = null;
            arenaReferenceCounter = null;
        }
    }

    private static MemoryRegionCache[] createSubPageCaches(
            int cacheSize, int numCaches) {
        if (cacheSize > 0 && numCaches > 0) {
            MemoryRegionCache[] cache = new MemoryRegionCache[numCaches];
            for (int i = 0; i < cache.length; i++) {
                // TODO: maybe use cacheSize / cache.length
                cache[i] = new SubPageMemoryRegionCache(cacheSize);
            }
            return cache;
        } else {
            return null;
        }
    }

    private static MemoryRegionCache[] createNormalCaches(
            int cacheSize, int maxCachedBufferCapacity, PoolArena area) {
        if (cacheSize > 0 && maxCachedBufferCapacity > 0) {
            int max = Math.min(area.chunkSize, maxCachedBufferCapacity);

            // Create as many normal caches as we support based on how many sizeIdx we have and what the upper
            // bound is that we want to cache in general.
            List<MemoryRegionCache> cache = new ArrayList<>() ;
            for (int idx = area.numSmallSubpagePools; idx < area.nSizes && area.sizeIdx2size(idx) <= max ; idx++) {
                cache.add(new NormalMemoryRegionCache(cacheSize));
            }
            return cache.toArray(MemoryRegionCache[]::new);
        } else {
            return null;
        }
    }

    PoolArena getArena() {
        Cache cache = getCache();
        if (cache != null) {
            return cache.arena;
        }
        return null;
    }

    private Cache getCache() {
        return cacheRef == null ? null : cacheRef.get();
    }

    // val > 0
    static int log2(int val) {
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

    /**
     * Try to allocate a small buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    UntetheredMemory allocateSmall(int size, int sizeIdx) {
        return allocate(cacheForSmall(sizeIdx), size);
    }

    /**
     * Try to allocate a normal buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    UntetheredMemory allocateNormal(PoolArena area, int size, int sizeIdx) {
        return allocate(cacheForNormal(area, sizeIdx), size);
    }

    private UntetheredMemory allocate(MemoryRegionCache cache, int size) {
        if (cache == null) {
            // no cache found so just return false here
            return null;
        }
        UntetheredMemory allocated = cache.allocate(size, this);
        if (++allocations >= freeSweepAllocationThreshold) {
            allocations = 0;
            trim();
        }
        return allocated;
    }

    /**
     * Add {@link PoolChunk} and {@code handle} to the cache if there is enough room.
     * Returns {@code true} if it fit into the cache {@code false} otherwise.
     */
    boolean add(PoolArena area, PoolChunk chunk,
                long handle, int normCapacity, SizeClass sizeClass) {
        int sizeIdx = area.size2SizeIdx(normCapacity);
        MemoryRegionCache cache = cache(area, sizeIdx, sizeClass);
        if (cache == null) {
            return false;
        }
        return cache.add(chunk, handle, normCapacity);
    }

    private MemoryRegionCache cache(PoolArena area, int sizeIdx, SizeClass sizeClass) {
        if (sizeClass == Normal) {
            return cacheForNormal(area, sizeIdx);
        }
        if (sizeClass == Small) {
            return cacheForSmall(sizeIdx);
        }
        throw new AssertionError("Unexpected size class: " + sizeClass);
    }

    /**
     *  Should be called if the Thread that uses this cache is about to exist to release resources out of the cache
     */
    void free() {
        Cache cache = getCache();
        if (cache != null) {
            int numFreed = free(cache.smallSubPageCaches) + free(cache.normalCaches);

            if (numFreed > 0 && logger.isDebugEnabled()) {
                logger.debug("Freed {} thread-local buffer(s) from thread: {}", numFreed,
                             Thread.currentThread().getName());
            }
        }

        if (arenaReferenceCounter != null) {
            arenaReferenceCounter.getAndDecrement();
        }
    }

    private static int free(MemoryRegionCache[] caches) {
        if (caches == null) {
            return 0;
        }

        int numFreed = 0;
        for (MemoryRegionCache c: caches) {
            numFreed += free(c);
        }
        return numFreed;
    }

    private static int free(MemoryRegionCache cache) {
        if (cache == null) {
            return 0;
        }
        return cache.free();
    }

    void trim() {
        Cache cache = getCache();
        if (cache != null) {
            trim(cache.smallSubPageCaches);
            trim(cache.normalCaches);
        }
    }

    private static void trim(MemoryRegionCache[] caches) {
        if (caches == null) {
            return;
        }
        for (MemoryRegionCache c: caches) {
            trim(c);
        }
    }

    private static void trim(MemoryRegionCache cache) {
        if (cache == null) {
            return;
        }
        cache.trim();
    }

    private MemoryRegionCache cacheForSmall(int sizeIdx) {
        Cache cache = getCache();
        if (cache != null) {
            return cache(cache.smallSubPageCaches, sizeIdx);
        }
        return null;
    }

    private MemoryRegionCache cacheForNormal(PoolArena area, int sizeIdx) {
        Cache cache = getCache();
        if (cache != null) {
            // We need to substract area.numSmallSubpagePools as sizeIdx is the overall index for all sizes.
            int idx = sizeIdx - area.numSmallSubpagePools;
            return cache(cache.normalCaches, idx);
        }
        return null;
    }

    private static  MemoryRegionCache cache(MemoryRegionCache[] cache, int sizeIdx) {
        if (cache == null || sizeIdx > cache.length - 1) {
            return null;
        }
        return cache[sizeIdx];
    }

    /**
     * Cache used for buffers which are backed by SMALL size.
     */
    private static final class SubPageMemoryRegionCache extends MemoryRegionCache {
        SubPageMemoryRegionCache(int size) {
            super(size, Small);
        }

        @Override
        protected UntetheredMemory allocBuf(PoolChunk chunk, long handle, int size, PoolThreadCache threadCache) {
            return chunk.allocateBufferWithSubpage(handle, size, threadCache);
        }
    }

    /**
     * Cache used for buffers which are backed by NORMAL size.
     */
    private static final class NormalMemoryRegionCache extends MemoryRegionCache {
        NormalMemoryRegionCache(int size) {
            super(size, Normal);
        }

        @Override
        protected UntetheredMemory allocBuf(PoolChunk chunk, long handle, int size, PoolThreadCache threadCache) {
            return chunk.allocateBuffer(handle, size, threadCache);
        }
    }

    private static final class Cache {
        final PoolArena arena;
        // Hold the caches for the different size classes, which are small and normal.
        final MemoryRegionCache[] smallSubPageCaches;
        final MemoryRegionCache[] normalCaches;

        private Cache(PoolArena arena, MemoryRegionCache[] smallSubPageCaches,
                      MemoryRegionCache[] normalCaches) {
            this.arena = arena;
            this.smallSubPageCaches = smallSubPageCaches;
            this.normalCaches = normalCaches;
        }
    }

    private abstract static class MemoryRegionCache {
        private final int size;
        private final Queue<Entry> queue;
        private final SizeClass sizeClass;
        private int allocations;

        MemoryRegionCache(int size, SizeClass sizeClass) {
            this.size = MathUtil.safeFindNextPositivePowerOfTwo(size);
            queue = PlatformDependent.newFixedMpscQueue(this.size);
            this.sizeClass = sizeClass;
        }

        /**
         * Allocate a new {@link UntetheredMemory} using the provided chunk and handle with the capacity restrictions.
         */
        protected abstract UntetheredMemory allocBuf(
                PoolChunk chunk, long handle, int size, PoolThreadCache threadCache);

        /**
         * Add to cache if not already full.
         */
        public final boolean add(PoolChunk chunk, long handle, int normCapacity) {
            Entry entry = newEntry(chunk, handle, normCapacity);
            boolean queued = queue.offer(entry);
            if (!queued) {
                // If it was not possible to cache the chunk, immediately recycle the entry
                entry.recycle();
            }

            return queued;
        }

        /**
         * Allocate something out of the cache if possible and remove the entry from the cache.
         */
        public final UntetheredMemory allocate(int size, PoolThreadCache threadCache) {
            Entry entry = queue.poll();
            if (entry == null) {
                return null;
            }
            UntetheredMemory buffer = allocBuf(entry.chunk, entry.handle, size, threadCache);
            entry.recycle();

            // allocations are not thread-safe which is fine as this is only called from the same thread all time.
            allocations++;
            return buffer;
        }

        /**
         * Clear out this cache and free up all previous cached {@link PoolChunk}s and {@code handle}s.
         */
        public final int free() {
            return free(Integer.MAX_VALUE);
        }

        private int free(int max) {
            int numFreed = 0;
            for (; numFreed < max; numFreed++) {
                Entry entry = queue.poll();
                if (entry != null) {
                    freeEntry(entry);
                } else {
                    // all cleared
                    return numFreed;
                }
            }
            return numFreed;
        }

        /**
         * Free up cached {@link PoolChunk}s if not allocated frequently enough.
         */
        public final void trim() {
            int free = size - allocations;
            allocations = 0;

            // We not even allocated all the number that are
            if (free > 0) {
                free(free);
            }
        }

        private  void freeEntry(Entry entry) {
            PoolChunk chunk = entry.chunk;
            long handle = entry.handle;

            entry.recycle();
            chunk.arena.freeChunk(chunk, handle, entry.normCapacity, sizeClass);
        }

        static final class Entry {
            final Handle<Entry> recyclerHandle;
            PoolChunk chunk;
            long handle = -1;
            int normCapacity;

            Entry(Handle<Entry> recyclerHandle) {
                this.recyclerHandle = recyclerHandle;
            }

            void recycle() {
                chunk = null;
                handle = -1;
                recyclerHandle.recycle(this);
            }
        }

        private static Entry newEntry(PoolChunk chunk, long handle, int normCapacity) {
            Entry entry = RECYCLER.get();
            entry.chunk = chunk;
            entry.handle = handle;
            entry.normCapacity = normCapacity;
            return entry;
        }

        private static final ObjectPool<Entry> RECYCLER = ObjectPool.newPool(handle -> new Entry(handle));
    }
}
