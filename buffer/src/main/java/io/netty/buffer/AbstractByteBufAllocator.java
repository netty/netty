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

public abstract class AbstractByteBufAllocator implements ByteBufAllocator {

    private final boolean directByDefault;
    private final ByteBuf emptyBuf;

    protected AbstractByteBufAllocator() {
        this(false);
    }

    protected AbstractByteBufAllocator(boolean directByDefault) {
        this.directByDefault = directByDefault;
        emptyBuf = new UnpooledHeapByteBuf(this, 0, 0);
    }

    @Override
    public ByteBuf buffer() {
        if (directByDefault) {
            return directBuffer();
        }
        return heapBuffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    public ByteBuf heapBuffer() {
        return heapBuffer(256, Integer.MAX_VALUE);
    }

    public ByteBuf heapBuffer(int initialCapacity) {
        return buffer(initialCapacity, Integer.MAX_VALUE);
    }

    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

    public ByteBuf directBuffer() {
        return directBuffer(256, Integer.MAX_VALUE);
    }

    public ByteBuf directBuffer(int initialCapacity) {
        return directBuffer(initialCapacity, Integer.MAX_VALUE);
    }

    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        return newDirectBuffer(initialCapacity, maxCapacity);
    }

    protected abstract ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity);
    protected abstract ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity);
}
