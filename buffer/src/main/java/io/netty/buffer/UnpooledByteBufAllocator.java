/*
 * Copyright 2012 The Netty Project
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

import io.netty.util.internal.CleanableDirectBuffer;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.LongAdder;

/**
 * Simplistic {@link ByteBufAllocator} implementation that does not pool anything.
 */
public final class UnpooledByteBufAllocator extends AbstractByteBufAllocator implements ByteBufAllocatorMetricProvider {

    private final UnpooledByteBufAllocatorMetric metric = new UnpooledByteBufAllocatorMetric();
    private final boolean disableLeakDetector;

    /**
     * Default instance which uses leak-detection for direct buffers.
     */
    public static final UnpooledByteBufAllocator DEFAULT =
            new UnpooledByteBufAllocator(PlatformDependent.directBufferPreferred());

    /**
     * Create a new instance which uses leak-detection for direct buffers.
     *
     * @param preferDirect {@code true} if {@link #buffer(int)} should try to allocate a direct buffer rather than
     *                     a heap buffer
     */
    public UnpooledByteBufAllocator(boolean preferDirect) {
        this(preferDirect, false);
    }

    /**
     * Create a new instance
     *
     * @param preferDirect {@code true} if {@link #buffer(int)} should try to allocate a direct buffer rather than
     *                     a heap buffer
     * @param disableLeakDetector {@code true} if the leak-detection should be disabled completely for this
     *                            allocator. This can be useful if the user just want to depend on the GC to handle
     *                            direct buffers when not explicit released.
     */
    public UnpooledByteBufAllocator(boolean preferDirect, boolean disableLeakDetector) {
        this(preferDirect, disableLeakDetector, PlatformDependent.useDirectBufferNoCleaner());
    }

    /**
     * Create a new instance
     *
     * @param preferDirect {@code true} if {@link #buffer(int)} should try to allocate a direct buffer rather than
     *                     a heap buffer
     * @param disableLeakDetector {@code true} if the leak-detection should be disabled completely for this
     *                            allocator. This can be useful if the user just want to depend on the GC to handle
     *                            direct buffers when not explicit released.
     * @param tryNoCleaner This option is ignored.
     * @deprecated The {@code tryNoCleaner} parameter is now ignored, so there is no point in passing it.
     * Use {@link #UnpooledByteBufAllocator(boolean)} or {@link #UnpooledByteBufAllocator(boolean, boolean)} instead.
     */
    @SuppressWarnings("unused") // The 'tryNoCleaner' parameter.
    @Deprecated
    public UnpooledByteBufAllocator(boolean preferDirect, boolean disableLeakDetector, boolean tryNoCleaner) {
        super(preferDirect);
        this.disableLeakDetector = disableLeakDetector;
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return PlatformDependent.hasUnsafe() ?
                new InstrumentedUnpooledUnsafeHeapByteBuf(this, initialCapacity, maxCapacity) :
                new InstrumentedUnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        final ByteBuf buf = new InstrumentedUnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
        return disableLeakDetector ? buf : toLeakAwareBuffer(buf);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        CompositeByteBuf buf = new CompositeByteBuf(this, false, maxNumComponents);
        return disableLeakDetector ? buf : toLeakAwareBuffer(buf);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        CompositeByteBuf buf = new CompositeByteBuf(this, true, maxNumComponents);
        return disableLeakDetector ? buf : toLeakAwareBuffer(buf);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }

    @Override
    public ByteBufAllocatorMetric metric() {
        return metric;
    }

    void incrementDirect(int amount) {
        metric.directCounter.add(amount);
    }

    void decrementDirect(int amount) {
        metric.directCounter.add(-amount);
    }

    void incrementHeap(int amount) {
        metric.heapCounter.add(amount);
    }

    void decrementHeap(int amount) {
        metric.heapCounter.add(-amount);
    }

    private static final class InstrumentedUnpooledUnsafeHeapByteBuf extends UnpooledUnsafeHeapByteBuf {
        InstrumentedUnpooledUnsafeHeapByteBuf(UnpooledByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
            super(alloc, initialCapacity, maxCapacity);
        }

        @Override
        protected byte[] allocateArray(int initialCapacity) {
            byte[] bytes = super.allocateArray(initialCapacity);
            ((UnpooledByteBufAllocator) alloc()).incrementHeap(bytes.length);
            return bytes;
        }

        @Override
        protected void freeArray(byte[] array) {
            int length = array.length;
            super.freeArray(array);
            ((UnpooledByteBufAllocator) alloc()).decrementHeap(length);
        }
    }

    private static final class InstrumentedUnpooledHeapByteBuf extends UnpooledHeapByteBuf {
        InstrumentedUnpooledHeapByteBuf(UnpooledByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
            super(alloc, initialCapacity, maxCapacity);
        }

        @Override
        protected byte[] allocateArray(int initialCapacity) {
            byte[] bytes = super.allocateArray(initialCapacity);
            ((UnpooledByteBufAllocator) alloc()).incrementHeap(bytes.length);
            return bytes;
        }

        @Override
        protected void freeArray(byte[] array) {
            int length = array.length;
            super.freeArray(array);
            ((UnpooledByteBufAllocator) alloc()).decrementHeap(length);
        }
    }

    private static final class InstrumentedUnpooledDirectByteBuf extends UnpooledDirectByteBuf {
        InstrumentedUnpooledDirectByteBuf(
                UnpooledByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
            super(alloc, initialCapacity, maxCapacity);
        }

        @Override
        protected CleanableDirectBuffer allocateDirectBuffer(int initialCapacity) {
            CleanableDirectBuffer buffer = super.allocateDirectBuffer(initialCapacity);
            ((UnpooledByteBufAllocator) alloc()).incrementDirect(buffer.buffer().capacity());
            return new CleanableDirectBuffer() {
                @Override
                public ByteBuffer buffer() {
                    return buffer.buffer();
                }

                @Override
                public void clean() {
                    buffer.clean();
                    ((UnpooledByteBufAllocator) alloc()).decrementDirect(initialCapacity);
                }
            };
        }
    }

    private static final class UnpooledByteBufAllocatorMetric implements ByteBufAllocatorMetric {
        final LongAdder directCounter = new LongAdder();
        final LongAdder heapCounter = new LongAdder();

        @Override
        public long usedHeapMemory() {
            return heapCounter.sum();
        }

        @Override
        public long usedDirectMemory() {
            return directCounter.sum();
        }

        @Override
        public String toString() {
            return StringUtil.simpleClassName(this) +
                    "(usedHeapMemory: " + usedHeapMemory() + "; usedDirectMemory: " + usedDirectMemory() + ')';
        }
    }
}
