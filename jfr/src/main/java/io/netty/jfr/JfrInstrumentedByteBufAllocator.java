/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://wache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.jfr;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.internal.ObjectUtil;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import static io.netty.jfr.JfrInstrumentedByteBufAllocator.AllocationType.*;


/**
 * A {@link ByteBufAllocator} that creates JDK Flight Recorder events for all byte buffer allocations.
 */
public final class JfrInstrumentedByteBufAllocator implements ByteBufAllocator {

    private final ByteBufAllocator wrapped;

    private JfrInstrumentedByteBufAllocator(ByteBufAllocator wrapped) {
        this.wrapped = ObjectUtil.checkNotNull(wrapped, "wrapped");
    }

    public static ByteBufAllocator wrap(ByteBufAllocator wrapped) {
        return new JfrInstrumentedByteBufAllocator(wrapped);
    }

    @Override
    public ByteBuf buffer() {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(DEFAULT_TYPE);
        event.commit();
        return wrapped.buffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(DEFAULT_TYPE);
        event.setInitialCapacity(initialCapacity);
        event.commit();
        return wrapped.buffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(DEFAULT_TYPE);
        event.setInitialCapacity(initialCapacity);
        event.setMaxCapacity(maxCapacity);
        event.commit();
        return wrapped.buffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(IO);
        event.commit();
        return wrapped.ioBuffer();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(IO);
        event.commit();

        return wrapped.ioBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(IO);
        event.setInitialCapacity(initialCapacity);
        event.setMaxCapacity(maxCapacity);
        event.commit();

        return wrapped.ioBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(HEAP);
        event.commit();
        return wrapped.heapBuffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(HEAP);
        event.setInitialCapacity(initialCapacity);
        event.commit();
        return wrapped.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(HEAP);
        event.setInitialCapacity(initialCapacity);
        event.setMaxCapacity(maxCapacity);
        event.commit();
        return wrapped.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(DIRECT);
        event.commit();
        return wrapped.directBuffer();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(DIRECT);
        event.setInitialCapacity(initialCapacity);
        event.commit();
        return wrapped.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(DIRECT);
        event.setInitialCapacity(initialCapacity);
        event.setMaxCapacity(maxCapacity);
        event.commit();
        return wrapped.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(COMPOSITE);
        event.commit();
        return wrapped.compositeBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxComponentCount) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(COMPOSITE);
        event.setMaxComponentCount(maxComponentCount);
        event.commit();
        return wrapped.compositeBuffer(maxComponentCount);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(COMPOSITE_HEAP);
        event.commit();
        return wrapped.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxComponentCount) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(COMPOSITE_HEAP);
        event.setMaxComponentCount(maxComponentCount);
        event.commit();
        return wrapped.compositeHeapBuffer(maxComponentCount);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(COMPOSITE_DIRECT);
        event.commit();
        return wrapped.compositeDirectBuffer();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxComponentCount) {
        ByteBufAllocationEvent event = new ByteBufAllocationEvent();
        event.setAllocationType(COMPOSITE_DIRECT);
        event.setMaxComponentCount(maxComponentCount);
        event.commit();
        return wrapped.compositeDirectBuffer(maxComponentCount);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return wrapped.isDirectBufferPooled();
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        return wrapped.calculateNewCapacity(minNewCapacity, maxCapacity);
    }

    @Category({ "Netty", "Buffer" })
    @Label("Byte Buffer Allocation")
    @Name("io.netty.buffer.ByteBufferAllocation")
    @Description("Byte buffer allocation, can be of multiple types, fields filled in differently for different types.")
    @Enabled(true)
    private static final class ByteBufAllocationEvent extends Event {
        @Label("Allocation Type")
        private String allocationType;

        @Label("Initial Capacity")
        private int initialCapacity;

        @Label("Max Capacity")
        private int maxCapacity;

        @Label("Max Number of Components")
        private int maxComponentCount;

        public void setInitialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
        }

        public void setMaxCapacity(int maxCapacity) {
            this.maxCapacity = maxCapacity;
        }

        public void setAllocationType(AllocationType allocationType) {
            this.allocationType = allocationType.toString();
        }

        public void setMaxComponentCount(int maxComponentCount) {
            this.maxComponentCount = maxComponentCount;
        }
    }
    enum AllocationType {
        DEFAULT_TYPE, IO, HEAP, DIRECT, COMPOSITE, COMPOSITE_HEAP, COMPOSITE_DIRECT
    }
}
