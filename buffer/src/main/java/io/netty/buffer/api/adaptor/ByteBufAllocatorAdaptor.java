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
package io.netty.buffer.api.adaptor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.api.BufferAllocator;

import java.util.Objects;

public class ByteBufAllocatorAdaptor implements ByteBufAllocator, AutoCloseable {
    private final BufferAllocator onheap;
    private final BufferAllocator offheap;
    private boolean closed;

    public ByteBufAllocatorAdaptor() {
        this(BufferAllocator.onHeapPooled(), BufferAllocator.offHeapPooled());
    }

    public ByteBufAllocatorAdaptor(BufferAllocator onheap, BufferAllocator offheap) {
        this.onheap = Objects.requireNonNull(onheap, "The on-heap allocator cannot be null.");
        this.offheap = Objects.requireNonNull(offheap, "The off-heap allocator cannot be null.");
    }

    @Override
    public ByteBuf buffer() {
        return buffer(256);
    }

    public BufferAllocator getOnHeap() {
        return onheap;
    }

    public BufferAllocator getOffHeap() {
        return offheap;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return new ByteBufAdaptor(this, onheap.allocate(initialCapacity));
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return buffer(maxCapacity);
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
        return new ByteBufAdaptor(this, offheap.allocate(initialCapacity));
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return directBuffer(maxCapacity);
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
        return 0;
    }

    @Override
    public void close() throws Exception {
        try (onheap) {
            try (offheap) {
                closed = true;
            }
        }
    }
}
