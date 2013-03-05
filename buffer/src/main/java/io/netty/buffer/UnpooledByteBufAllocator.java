/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;

/**
 * Simplistic {@link ByteBufAllocator} implementation that does not pool anything.
 */
public final class UnpooledByteBufAllocator extends AbstractByteBufAllocator {

    public static final UnpooledByteBufAllocator HEAP_BY_DEFAULT = new UnpooledByteBufAllocator(false);
    public static final UnpooledByteBufAllocator DIRECT_BY_DEFAULT = new UnpooledByteBufAllocator(true);

    private UnpooledByteBufAllocator(boolean directByDefault) {
        super(directByDefault);
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        return new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        if (PlatformDependent.hasUnsafe()) {
            return directBuffer(0);
        }
        return heapBuffer(0);
    }
}
