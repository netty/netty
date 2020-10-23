/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

/**
 * Wraps another {@link ByteBufAllocator} and use heapbuffers everywhere except when a direct buffer is explicit
 * requested.
 */
@UnstableApi
public final class PreferHeapByteBufAllocator implements ByteBufAllocator {
    private final ByteBufAllocator allocator;

    public PreferHeapByteBufAllocator(ByteBufAllocator allocator) {
        this.allocator = ObjectUtil.checkNotNull(allocator, "allocator");
    }

    @Override
    public ByteBuf buffer() {
        return allocator.heapBuffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return allocator.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return allocator.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        return allocator.heapBuffer();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        return allocator.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        return allocator.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return allocator.heapBuffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return allocator.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return allocator.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return allocator.directBuffer();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return allocator.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return allocator.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        return allocator.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        return allocator.compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        return allocator.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return allocator.compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        return allocator.compositeDirectBuffer();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return allocator.compositeDirectBuffer(maxNumComponents);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return allocator.isDirectBufferPooled();
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        return allocator.calculateNewCapacity(minNewCapacity, maxCapacity);
    }
}
