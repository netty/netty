/*
 * Copyright 2025 The Netty Project
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

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.UnstableApi;

/**
 * A Free-List {@link ByteBufAllocator} based on mimalloc:
 * <a href="https://www.microsoft.com/en-us/research/wp-content/uploads/2019/06/mimalloc-tr-v1.pdf">mimalloc-paper</a>.
 * <a href="https://github.com/microsoft/mimalloc">mimalloc-code</a>
 * <p>
 * <strong>Note:</strong> This allocator is <strong>experimental</strong>. It is recommended to roll out usage slowly,
 * and to carefully monitor application performance in the process.
 * <p>
 * See the {@link MiMallocByteBufAllocator} class documentation for implementation details.
 */
@UnstableApi
public final class MiByteBufAllocator extends AbstractByteBufAllocator
        implements ByteBufAllocatorMetricProvider, ByteBufAllocatorMetric {

    private final MiMallocByteBufAllocator direct;
    private final MiMallocByteBufAllocator heap;

    public MiByteBufAllocator() {
        this(!PlatformDependent.isExplicitNoPreferDirect());
    }

    public MiByteBufAllocator(boolean preferDirect) {
        super(preferDirect);
        direct = new MiMallocByteBufAllocator(new DirectChunkAllocator(this));
        heap = new MiMallocByteBufAllocator(new HeapChunkAllocator(this));
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

    long abandonedHeapSegmentCount() {
        return heap.abandonedSegmentCount();
    }

    long abandonedDirectSegmentCount() {
        return direct.abandonedSegmentCount();
    }

    @Override
    public ByteBufAllocatorMetric metric() {
        return this;
    }

    private static final class HeapChunkAllocator implements MiMallocByteBufAllocator.ChunkAllocator {
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

    private static final class DirectChunkAllocator implements MiMallocByteBufAllocator.ChunkAllocator {
        private final ByteBufAllocator allocator;

        private DirectChunkAllocator(ByteBufAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public AbstractByteBuf allocate(int initialCapacity, int maxCapacity) {
            return PlatformDependent.hasUnsafe() ?
                    UnsafeByteBufUtil.newUnsafeDirectByteBuf(allocator, initialCapacity, maxCapacity, false) :
                    new UnpooledDirectByteBuf(allocator, initialCapacity, maxCapacity, false);
        }
    }
}
