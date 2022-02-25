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
package io.netty5.buffer.api.adaptor;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.CompositeByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.internal.AdaptableBuffer;
import io.netty5.util.internal.PlatformDependent;

import java.util.Objects;

import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

public class ByteBufAllocatorAdaptor implements ByteBufAllocator, AutoCloseable {
    private static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;
    public static final ByteBufAllocatorAdaptor DEFAULT_INSTANCE = new ByteBufAllocatorAdaptor(
            DefaultBufferAllocators.onHeapAllocator(), DefaultBufferAllocators.offHeapAllocator());

    private final BufferAllocator onHeap;
    private final BufferAllocator offHeap;
    private boolean closed;

    public ByteBufAllocatorAdaptor(BufferAllocator onHeap, BufferAllocator offHeap) {
        this.onHeap = Objects.requireNonNull(onHeap, "The on-heap allocator cannot be null.");
        this.offHeap = Objects.requireNonNull(offHeap, "The off-heap allocator cannot be null.");
    }

    @Override
    public ByteBuf buffer() {
        return buffer(256);
    }

    public BufferAllocator getOnHeap() {
        return onHeap;
    }

    public BufferAllocator getOffHeap() {
        return offHeap;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return buffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return initialise(onHeap.allocate(initialCapacity), maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        return directBuffer();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        return directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        return directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return buffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return buffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return buffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return directBuffer(256);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return directBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return initialise(offHeap.allocate(initialCapacity), maxCapacity);
    }

    private ByteBuf initialise(Buffer buffer, int maxCapacity) {
        AdaptableBuffer<?> adaptableBuffer = (AdaptableBuffer<?>) buffer;
        return adaptableBuffer.initialise(this, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        return compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        return compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        return compositeHeapBuffer(1024);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return new CompositeByteBuf(this, false, maxNumComponents, heapBuffer());
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        return compositeDirectBuffer(1024);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return new CompositeByteBuf(this, true, maxNumComponents, directBuffer());
    }

    @Override
    public boolean isDirectBufferPooled() {
        return true;
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        checkPositiveOrZero(minNewCapacity, "minNewCapacity");
        if (minNewCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "minNewCapacity: %d (expected: not greater than maxCapacity(%d)",
                    minNewCapacity, maxCapacity));
        }
        int newCapacity = PlatformDependent.roundToPowerOfTwo(minNewCapacity);
        return Math.min(maxCapacity, newCapacity);
    }

    @Override
    public void close() throws Exception {
        try (onHeap) {
            try (offHeap) {
                closed = true;
            }
        }
    }
}
