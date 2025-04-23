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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

final class DirectIoByteBufAllocator implements ByteBufAllocator {

    private final ByteBufAllocator wrapped;

    DirectIoByteBufAllocator(ByteBufAllocator wrapped) {
        if (wrapped instanceof DirectIoByteBufAllocator) {
            wrapped = ((DirectIoByteBufAllocator) wrapped).wrapped();
        }
        this.wrapped = wrapped;
    }

    ByteBufAllocator wrapped() {
        return wrapped;
    }

    @Override
    public ByteBuf buffer() {
        return wrapped.buffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return wrapped.buffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return wrapped.buffer(initialCapacity, maxCapacity);
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
        return wrapped.heapBuffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return wrapped.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return wrapped.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return wrapped.directBuffer();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return wrapped.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return wrapped.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        return wrapped.compositeBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        return wrapped.compositeBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        return wrapped.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return wrapped.compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        return wrapped.compositeDirectBuffer();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return wrapped.compositeDirectBuffer(maxNumComponents);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return wrapped.isDirectBufferPooled();
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        return wrapped.calculateNewCapacity(minNewCapacity, maxCapacity);
    }
}
