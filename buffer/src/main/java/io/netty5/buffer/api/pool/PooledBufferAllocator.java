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

import io.netty5.buffer.api.AllocationType;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.buffer.api.internal.ArcDrop;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.NettyRuntime;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.concurrent.FastThreadLocalThread;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.ThreadExecutorMap;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.netty5.buffer.api.internal.Statics.allocatorClosedException;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.util.Objects.requireNonNull;

public class PooledBufferAllocator implements BufferAllocator, BufferAllocatorMetricProvider {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PooledBufferAllocator.class);
    private static final int DEFAULT_NUM_HEAP_ARENA;
    private static final int DEFAULT_NUM_DIRECT_ARENA;

    private static final int DEFAULT_PAGE_SIZE;
    private static final int DEFAULT_MAX_ORDER; // 8192 << 9 = 4 MiB per chunk
    private static final int DEFAULT_SMALL_CACHE_SIZE;
    private static final int DEFAULT_NORMAL_CACHE_SIZE;
    static final int DEFAULT_MAX_CACHED_BUFFER_CAPACITY;
    private static final int DEFAULT_CACHE_TRIM_INTERVAL;
    private static final long DEFAULT_CACHE_TRIM_INTERVAL_MILLIS;
    private static final boolean DEFAULT_USE_CACHE_FOR_ALL_THREADS;
    private static final int DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT;
    static final int DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK;

    private static final int MIN_PAGE_SIZE = 4096;
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    private final Runnable trimTask = this::trimCurrentThreadCache;

    static {
        int defaultAlignment = SystemPropertyUtil.getInt(
                "io.netty5.allocator.directMemoryCacheAlignment", 0);
        int defaultPageSize = SystemPropertyUtil.getInt("io.netty5.allocator.pageSize", 8192);
        Throwable pageSizeFallbackCause = null;
        try {
            validateAndCalculatePageShifts(defaultPageSize, defaultAlignment);
        } catch (Throwable t) {
            pageSizeFallbackCause = t;
            defaultPageSize = 8192;
            defaultAlignment = 0;
        }
        DEFAULT_PAGE_SIZE = defaultPageSize;
        DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT = defaultAlignment;

        int defaultMaxOrder = SystemPropertyUtil.getInt("io.netty5.allocator.maxOrder", 9);
        Throwable maxOrderFallbackCause = null;
        try {
            validateAndCalculateChunkSize(DEFAULT_PAGE_SIZE, defaultMaxOrder);
        } catch (Throwable t) {
            maxOrderFallbackCause = t;
            defaultMaxOrder = 11;
        }
        DEFAULT_MAX_ORDER = defaultMaxOrder;

        // Determine reasonable default for nHeapArena and nDirectArena.
        // Assuming each arena has 3 chunks, the pool should not consume more than 50% of max memory.
        final Runtime runtime = Runtime.getRuntime();

        /*
         * We use 2 * available processors by default to reduce contention as we use 2 * available processors for the
         * number of EventLoops in NIO and EPOLL as well. If we choose a smaller number we will run into hot spots as
         * allocation and de-allocation needs to be synchronized on the PoolArena.
         *
         * See https://github.com/netty/netty/issues/3888.
         */
        final int defaultMinNumArena = NettyRuntime.availableProcessors() * 2;
        final int defaultChunkSize = DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER;
        DEFAULT_NUM_HEAP_ARENA = Math.max(0,
                SystemPropertyUtil.getInt(
                        "io.netty5.allocator.numArenas",
                        (int) Math.min(
                                defaultMinNumArena,
                                runtime.maxMemory() / defaultChunkSize / 2 / 3)));
        DEFAULT_NUM_DIRECT_ARENA = Math.max(0,
                SystemPropertyUtil.getInt(
                        "io.netty5.allocator.numDirectArenas",
                        (int) Math.min(
                                defaultMinNumArena,
                                PlatformDependent.maxDirectMemory() / defaultChunkSize / 2 / 3)));

        // cache sizes
        DEFAULT_SMALL_CACHE_SIZE = SystemPropertyUtil.getInt("io.netty5.allocator.smallCacheSize", 256);
        DEFAULT_NORMAL_CACHE_SIZE = SystemPropertyUtil.getInt("io.netty5.allocator.normalCacheSize", 64);

        // 32 kb is the default maximum capacity of the cached buffer. Similar to what is explained in
        // 'Scalable memory allocation using jemalloc'
        DEFAULT_MAX_CACHED_BUFFER_CAPACITY = SystemPropertyUtil.getInt(
                "io.netty5.allocator.maxCachedBufferCapacity", 32 * 1024);

        // the number of threshold of allocations when cached entries will be freed up if not frequently used
        DEFAULT_CACHE_TRIM_INTERVAL = SystemPropertyUtil.getInt(
                "io.netty5.allocator.cacheTrimInterval", 8192);

        DEFAULT_CACHE_TRIM_INTERVAL_MILLIS = SystemPropertyUtil.getLong(
                "io.netty5.allocator.cacheTrimIntervalMillis", 0);

        DEFAULT_USE_CACHE_FOR_ALL_THREADS = SystemPropertyUtil.getBoolean(
                "io.netty5.allocator.useCacheForAllThreads", false);

        // Use 1023 by default as we use an ArrayDeque as backing storage which will then allocate an internal array
        // of 1024 elements. Otherwise, we would allocate 2048 and only use 1024 which is wasteful.
        DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK = SystemPropertyUtil.getInt(
                "io.netty5.allocator.maxCachedByteBuffersPerChunk", 1023);

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty5.allocator.numArenas: {}", DEFAULT_NUM_HEAP_ARENA);
            logger.debug("-Dio.netty5.allocator.numDirectArenas: {}", DEFAULT_NUM_DIRECT_ARENA);
            if (pageSizeFallbackCause == null) {
                logger.debug("-Dio.netty5.allocator.pageSize: {}", DEFAULT_PAGE_SIZE);
            } else {
                logger.debug("-Dio.netty5.allocator.pageSize: {}", DEFAULT_PAGE_SIZE, pageSizeFallbackCause);
            }
            if (maxOrderFallbackCause == null) {
                logger.debug("-Dio.netty5.allocator.maxOrder: {}", DEFAULT_MAX_ORDER);
            } else {
                logger.debug("-Dio.netty5.allocator.maxOrder: {}", DEFAULT_MAX_ORDER, maxOrderFallbackCause);
            }
            logger.debug("-Dio.netty5.allocator.chunkSize: {}", DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER);
            logger.debug("-Dio.netty5.allocator.smallCacheSize: {}", DEFAULT_SMALL_CACHE_SIZE);
            logger.debug("-Dio.netty5.allocator.normalCacheSize: {}", DEFAULT_NORMAL_CACHE_SIZE);
            logger.debug("-Dio.netty5.allocator.maxCachedBufferCapacity: {}", DEFAULT_MAX_CACHED_BUFFER_CAPACITY);
            logger.debug("-Dio.netty5.allocator.cacheTrimInterval: {}", DEFAULT_CACHE_TRIM_INTERVAL);
            logger.debug("-Dio.netty5.allocator.cacheTrimIntervalMillis: {}", DEFAULT_CACHE_TRIM_INTERVAL_MILLIS);
            logger.debug("-Dio.netty5.allocator.useCacheForAllThreads: {}", DEFAULT_USE_CACHE_FOR_ALL_THREADS);
            logger.debug("-Dio.netty5.allocator.maxCachedByteBuffersPerChunk: {}",
                    DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK);
        }
    }

    private final MemoryManager manager;
    private final AllocationType allocationType;
    private final PoolArena[] arenas;
    private final int smallCacheSize;
    private final int normalCacheSize;
    private final List<PoolArenaMetric> arenaMetrics;
    private final List<PoolArenaMetric> arenaMetricsView;
    private final PoolThreadLocalCache threadCache;
    private final int chunkSize;
    private final PooledBufferAllocatorMetric metric;
    private volatile boolean closed;

    public PooledBufferAllocator(MemoryManager manager, boolean direct) {
        this(manager, direct, direct? DEFAULT_NUM_DIRECT_ARENA : DEFAULT_NUM_HEAP_ARENA,
                DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER, DEFAULT_SMALL_CACHE_SIZE,
                DEFAULT_NORMAL_CACHE_SIZE, DEFAULT_USE_CACHE_FOR_ALL_THREADS,
                DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT);
    }

    public PooledBufferAllocator(MemoryManager manager, boolean direct, int numArenas, int pageSize, int maxOrder) {
        this(manager, direct, numArenas, pageSize, maxOrder, DEFAULT_SMALL_CACHE_SIZE,
                DEFAULT_NORMAL_CACHE_SIZE, DEFAULT_USE_CACHE_FOR_ALL_THREADS,
                DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT);
    }

    public PooledBufferAllocator(MemoryManager manager, boolean direct, int numArenas, int pageSize, int maxOrder,
                                 int smallCacheSize, int normalCacheSize,
                                 boolean useCacheForAllThreads) {
        this(manager, direct, numArenas, pageSize, maxOrder,
             smallCacheSize, normalCacheSize,
             useCacheForAllThreads, DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT);
    }

    public PooledBufferAllocator(MemoryManager manager, boolean direct, int numArenas, int pageSize, int maxOrder,
                                 int smallCacheSize, int normalCacheSize,
                                 boolean useCacheForAllThreads, int directMemoryCacheAlignment) {
        this.manager = requireNonNull(manager, "MemoryManager");
        allocationType = direct? StandardAllocationTypes.OFF_HEAP : StandardAllocationTypes.ON_HEAP;
        threadCache = new PoolThreadLocalCache(useCacheForAllThreads);
        this.smallCacheSize = smallCacheSize;
        this.normalCacheSize = normalCacheSize;

        if (directMemoryCacheAlignment != 0) {
            if (!PlatformDependent.hasAlignDirectByteBuffer()) {
                throw new UnsupportedOperationException("Buffer alignment is not supported. " +
                        "Either Unsafe or ByteBuffer.alignSlice() must be available.");
            }

            // Ensure page size is a whole multiple of the alignment, or bump it to the next whole multiple.
            pageSize = (int) PlatformDependent.align(pageSize, directMemoryCacheAlignment);
        }

        chunkSize = validateAndCalculateChunkSize(pageSize, maxOrder);

        checkPositiveOrZero(numArenas, "numArenas");

        checkPositiveOrZero(directMemoryCacheAlignment, "directMemoryCacheAlignment");
        if (directMemoryCacheAlignment > 0 && !isDirectMemoryCacheAlignmentSupported()) {
            throw new IllegalArgumentException("directMemoryCacheAlignment is not supported");
        }

        if ((directMemoryCacheAlignment & -directMemoryCacheAlignment) != directMemoryCacheAlignment) {
            throw new IllegalArgumentException("directMemoryCacheAlignment: "
                    + directMemoryCacheAlignment + " (expected: power of two)");
        }

        int pageShifts = validateAndCalculatePageShifts(pageSize, directMemoryCacheAlignment);

        if (numArenas > 0) {
            arenas = newArenaArray(numArenas);
            List<PoolArenaMetric> metrics = new ArrayList<>(arenas.length);
            for (int i = 0; i < arenas.length; i ++) {
                PoolArena arena = new PoolArena(this, manager, allocationType,
                        pageSize, pageShifts, chunkSize,
                        directMemoryCacheAlignment);
                arenas[i] = arena;
                metrics.add(arena);
            }
            arenaMetrics = metrics;
            arenaMetricsView = Collections.unmodifiableList(metrics);
        } else {
            arenas = null;
            arenaMetrics = new ArrayList<>(1);
            arenaMetricsView = Collections.emptyList();
        }

        metric = new PooledBufferAllocatorMetric(this);
    }

    private static PoolArena[] newArenaArray(int size) {
        return new PoolArena[size];
    }

    private static int validateAndCalculatePageShifts(int pageSize, int alignment) {
        if (pageSize < MIN_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: " + MIN_PAGE_SIZE + ')');
        }

        if ((pageSize & pageSize - 1) != 0) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: power of 2)");
        }

        if (pageSize < alignment) {
            throw new IllegalArgumentException("Alignment cannot be greater than page size. " +
                    "Alignment: " + alignment + ", page size: " + pageSize + '.');
        }

        // Logarithm base 2. At this point we know that pageSize is a power of two.
        return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(pageSize);
    }

    private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        int chunkSize = pageSize;
        for (int i = maxOrder; i > 0; i--) {
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format(
                        "pageSize (%d) << maxOrder (%d) must not exceed %d", pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
        }
        return chunkSize;
    }

    @Override
    public boolean isPooling() {
        return true;
    }

    @Override
    public AllocationType getAllocationType() {
        return allocationType;
    }

    @Override
    public Buffer allocate(int size) {
        if (closed) {
            throw allocatorClosedException();
        }
        Statics.assertValidBufferSize(size);
        PooledAllocatorControl control = new PooledAllocatorControl(this);
        UntetheredMemory memory = allocateUntethered(size);
        Drop<Buffer> drop = memory.drop();
        Buffer buffer = manager.recoverMemory(control, memory.memory(), drop);
        drop.attach(buffer);
        return buffer.fill((byte) 0);
    }

    @Override
    public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
        if (closed) {
            throw allocatorClosedException();
        }
        PooledAllocatorControl control = new PooledAllocatorControl(this);
        Buffer constantBuffer = manager.allocateShared(
                control, bytes.length, ArcDrop::wrap, allocationType);
        constantBuffer.writeBytes(bytes).makeReadOnly();
        return () -> manager.allocateConstChild(constantBuffer);
    }

    UntetheredMemory allocateUntethered(int size) {
        PoolThreadCache cache = threadCache.get();
        PoolArena arena = cache.getArena();

        if (arena != null) {
            return arena.allocate(cache, size);
        }
        return allocateUnpooled(size);
    }

    private UntetheredMemory allocateUnpooled(int size) {
        return new UnpooledUntetheredMemory(this, manager, allocationType, size);
    }

    @Override
    public void close() {
        closed = true;
        trimCurrentThreadCache();
        threadCache.remove();
        for (int i = 0, arenasLength = arenas.length; i < arenasLength; i++) {
            PoolArena arena = arenas[i];
            if (arena != null) {
                arena.close();
                arenas[i] = null;
            }
        }
        arenaMetrics.clear();
    }

    /**
     * Default number of heap arenas - System Property: io.netty5.allocator.numHeapArenas - default 2 * cores
     */
    public static int defaultNumHeapArena() {
        return DEFAULT_NUM_HEAP_ARENA;
    }

    /**
     * Default number of direct arenas - System Property: io.netty5.allocator.numDirectArenas - default 2 * cores
     */
    public static int defaultNumDirectArena() {
        return DEFAULT_NUM_DIRECT_ARENA;
    }

    /**
     * Default buffer page size - System Property: io.netty5.allocator.pageSize - default 8192
     */
    public static int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    /**
     * Default maximum order - System Property: io.netty5.allocator.maxOrder - default 11
     */
    public static int defaultMaxOrder() {
        return DEFAULT_MAX_ORDER;
    }

    /**
     * Default thread caching behavior - System Property: io.netty5.allocator.useCacheForAllThreads - default true
     */
    public static boolean defaultUseCacheForAllThreads() {
        return DEFAULT_USE_CACHE_FOR_ALL_THREADS;
    }

    /**
     * Default prefer direct - System Property: io.netty5.noPreferDirect - default false
     */
    public static boolean defaultPreferDirect() {
        return PlatformDependent.directBufferPreferred();
    }

    /**
     * Default small cache size - System Property: io.netty5.allocator.smallCacheSize - default 256
     */
    public static int defaultSmallCacheSize() {
        return DEFAULT_SMALL_CACHE_SIZE;
    }

    /**
     * Default normal cache size - System Property: io.netty5.allocator.normalCacheSize - default 64
     */
    public static int defaultNormalCacheSize() {
        return DEFAULT_NORMAL_CACHE_SIZE;
    }

    /**
     * Return {@code true} if direct memory cache alignment is supported, {@code false} otherwise.
     */
    public static boolean isDirectMemoryCacheAlignmentSupported() {
        return PlatformDependent.hasUnsafe();
    }

    public boolean isDirectBufferPooled() {
        return allocationType == StandardAllocationTypes.OFF_HEAP;
    }

    public int numArenas() {
        return arenas.length;
    }

    final class PoolThreadLocalCache extends FastThreadLocal<PoolThreadCache> {
        private final boolean useCacheForAllThreads;

        PoolThreadLocalCache(boolean useCacheForAllThreads) {
            this.useCacheForAllThreads = useCacheForAllThreads;
        }

        @Override
        protected synchronized PoolThreadCache initialValue() {
            final PoolArena arena = leastUsedArena(arenas);

            final Thread current = Thread.currentThread();
            final EventExecutor executor = ThreadExecutorMap.currentExecutor();
            if (useCacheForAllThreads ||
                // If the current thread is a FastThreadLocalThread we will always use the cache
                current instanceof FastThreadLocalThread ||
                // The Thread is used by an EventExecutor, let's use the cache as the chances are good that we
                // will allocate a lot!
                executor != null) {
                final PoolThreadCache cache = new PoolThreadCache(
                        arena, smallCacheSize, normalCacheSize,
                        DEFAULT_MAX_CACHED_BUFFER_CAPACITY, DEFAULT_CACHE_TRIM_INTERVAL);

                if (DEFAULT_CACHE_TRIM_INTERVAL_MILLIS > 0) {
                    if (executor != null) {
                        executor.scheduleAtFixedRate(trimTask, DEFAULT_CACHE_TRIM_INTERVAL_MILLIS,
                                                     DEFAULT_CACHE_TRIM_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
                    }
                }
                return cache;
            }
            // No caching so just use 0 as sizes.
            return new PoolThreadCache(arena, 0, 0, 0, 0);
        }

        @Override
        protected void onRemoval(PoolThreadCache threadCache) {
            threadCache.free();
        }
    }

    static PoolArena leastUsedArena(PoolArena[] arenas) {
        if (arenas == null || arenas.length == 0) {
            return null;
        }

        PoolArena minArena = arenas[0];
        for (int i = 1; i < arenas.length; i++) {
            PoolArena arena = arenas[i];
            if (arena.numThreadCaches.get() < minArena.numThreadCaches.get()) {
                minArena = arena;
            }
        }

        return minArena;
    }

    @Override
    public BufferAllocatorMetric metric() {
        return metric;
    }

    /**
     * Return a {@link List} of all heap {@link PoolArenaMetric}s that are provided by this pool.
     */
    List<PoolArenaMetric> arenaMetrics() {
        return arenaMetricsView;
    }

    /**
     * Return the number of thread local caches used by this {@link PooledBufferAllocator}.
     */
    int numThreadLocalCaches() {
        if (arenas == null) {
            return 0;
        }

        int total = 0;
        for (PoolArena arena : arenas) {
            total += arena.numThreadCaches.get();
        }

        return total;
    }

    /**
     * Return the size of the small cache.
     */
    int smallCacheSize() {
        return smallCacheSize;
    }

    /**
     * Return the size of the normal cache.
     */
    int normalCacheSize() {
        return normalCacheSize;
    }

    /**
     * Return the chunk size for an arena.
     */
    final int chunkSize() {
        return chunkSize;
    }

    final long usedMemory() {
        return usedMemory(arenas);
    }

    private static long usedMemory(PoolArena[] arenas) {
        if (arenas == null) {
            return -1;
        }
        long used = 0;
        for (PoolArena arena : arenas) {
            used += arena.numActiveBytes();
            if (used < 0) {
                return Long.MAX_VALUE;
            }
        }
        return used;
    }

    final PoolThreadCache threadCache() {
        PoolThreadCache cache =  threadCache.get();
        assert cache != null;
        return cache;
    }

    /**
     * Trim thread local cache for the current {@link Thread}, which will give back any cached memory that was not
     * allocated frequently since the last trim operation.
     *
     * Returns {@code true} if a cache for the current {@link Thread} exists and so was trimmed, false otherwise.
     */
    public boolean trimCurrentThreadCache() {
        PoolThreadCache cache = threadCache.getIfExists();
        if (cache != null) {
            cache.trim();
            return true;
        }
        return false;
    }

    /**
     * Returns the status of the allocator (which contains all metrics) as string. Be aware this may be expensive
     * and so should not be called too frequently.
     */
    public String dumpStats() {
        int heapArenasLen = arenas == null ? 0 : arenas.length;
        StringBuilder buf = new StringBuilder(512)
                .append(heapArenasLen)
                .append(" arena(s):")
                .append(StringUtil.NEWLINE);
        if (heapArenasLen > 0) {
            for (PoolArena a: arenas) {
                buf.append(a);
            }
        }

        return buf.toString();
    }
}
