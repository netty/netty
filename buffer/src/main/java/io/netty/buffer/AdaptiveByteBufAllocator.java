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

import io.netty.buffer.AdaptivePoolingAllocator.MagazineCaching;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * An auto-tuning pooling {@link ByteBufAllocator}, that follows an anti-generational hypothesis.
 * <p>
 * <strong>Note:</strong> this allocator is <strong>experimental</strong>. It is recommended to roll out usage slowly,
 * and to carefully monitor application performance in the process.
 * <p>
 * See the {@link AdaptivePoolingAllocator} class documentation for implementation details.
 */
public final class AdaptiveByteBufAllocator extends AbstractByteBufAllocator
        implements ByteBufAllocatorMetricProvider, ByteBufAllocatorMetric {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AdaptiveByteBufAllocator.class);
    private static final boolean DEFAULT_USE_CACHED_MAGAZINES_FOR_NON_EVENT_LOOP_THREADS;

    static {
        DEFAULT_USE_CACHED_MAGAZINES_FOR_NON_EVENT_LOOP_THREADS = SystemPropertyUtil.getBoolean(
                "io.netty.allocator.useCachedMagazinesForNonEventLoopThreads", false);
        logger.debug("-Dio.netty.allocator.useCachedMagazinesForNonEventLoopThreads: {}",
                     DEFAULT_USE_CACHED_MAGAZINES_FOR_NON_EVENT_LOOP_THREADS);
    }

    private final AdaptivePoolingAllocator direct;
    private final AdaptivePoolingAllocator heap;

    public AdaptiveByteBufAllocator() {
        this(PlatformDependent.directBufferPreferred());
    }

    public AdaptiveByteBufAllocator(boolean preferDirect) {
        this(preferDirect, DEFAULT_USE_CACHED_MAGAZINES_FOR_NON_EVENT_LOOP_THREADS);
    }

    public AdaptiveByteBufAllocator(boolean preferDirect, boolean useCacheForNonEventLoopThreads) {
        super(preferDirect);
        MagazineCaching magazineCaching = useCacheForNonEventLoopThreads?
                MagazineCaching.FastThreadLocalThreads : MagazineCaching.EventLoopThreads;
        direct = new AdaptivePoolingAllocator(new DirectChunkAllocator(this), magazineCaching);
        heap = new AdaptivePoolingAllocator(new HeapChunkAllocator(this), magazineCaching);
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return toLeakAwareBuffer(heap.allocate(initialCapacity, maxCapacity));
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        return toLeakAwareBuffer(direct.allocate(initialCapacity, maxCapacity));
    }

    @Override
    public boolean isDirectBufferPooled() {
        return true;
    }

    @Override
    public long usedHeapMemory() {
        return heap.usedMemory();
    }

    @Override
    public long usedDirectMemory() {
        return direct.usedMemory();
    }

    @Override
    public ByteBufAllocatorMetric metric() {
        return this;
    }

    private static final class HeapChunkAllocator implements AdaptivePoolingAllocator.ChunkAllocator {
        private final ByteBufAllocator allocator;

        private HeapChunkAllocator(ByteBufAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public AbstractByteBuf allocate(int initialCapacity, int maxCapacity) {
            return PlatformDependent.hasUnsafe() ?
                    new UnpooledUnsafeHeapByteBuf(allocator, initialCapacity, maxCapacity) :
                    new UnpooledHeapByteBuf(allocator, initialCapacity, maxCapacity);
        }
    }

    private static final class DirectChunkAllocator implements AdaptivePoolingAllocator.ChunkAllocator {
        private final ByteBufAllocator allocator;

        private DirectChunkAllocator(ByteBufAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public AbstractByteBuf allocate(int initialCapacity, int maxCapacity) {
            return PlatformDependent.hasUnsafe() ?
                    UnsafeByteBufUtil.newUnsafeDirectByteBuf(allocator, initialCapacity, maxCapacity) :
                    new UnpooledDirectByteBuf(allocator, initialCapacity, maxCapacity);
        }
    }
}
