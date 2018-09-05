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

import io.netty.util.internal.UnsafeInternal;

import java.nio.ByteBuffer;

/**
 * A NIO {@link ByteBuffer} based buffer. It is recommended to use
 * {@link UnpooledByteBufAllocator#directBuffer(int, int)}, {@link Unpooled#directBuffer(int)} and
 * {@link Unpooled#wrappedBuffer(ByteBuffer)} instead of calling the constructor explicitly.}
 */
public class UnpooledInternalUnsafeDirectByteBuf extends UnpooledUnsafeDirectByteBuf {

    /**
     * Creates a new direct buffer.
     *
     * @param initialCapacity the initial capacity of the underlying direct buffer
     * @param maxCapacity     the maximum capacity of the underlying direct buffer
     */
    public UnpooledInternalUnsafeDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(alloc, initialCapacity, maxCapacity);
    }

    /**
     * Creates a new direct buffer by wrapping the specified initial buffer.
     *
     * @param maxCapacity the maximum capacity of the underlying direct buffer
     */
    protected UnpooledInternalUnsafeDirectByteBuf(ByteBufAllocator alloc, ByteBuffer initialBuffer, int maxCapacity) {
        super(alloc, initialBuffer, maxCapacity);
    }

    @Override
    protected short _getShort(int index) {
        return UnsafeInternal.getShortUnaligned(null, addr(index), true);
    }

    @Override
    protected short _getShortLE(int index) {
        return UnsafeInternal.getShortUnaligned(null, addr(index), false);
    }

    @Override
    protected int _getInt(int index) {
        return UnsafeInternal.getIntUnaligned(null, addr(index), true);
    }

    @Override
    protected int _getIntLE(int index) {
        return UnsafeInternal.getIntUnaligned(null, addr(index), false);
    }

    @Override
    protected long _getLong(int index) {
        return UnsafeInternal.getLongUnaligned(null, addr(index), true);
    }

    @Override
    protected long _getLongLE(int index) {
        return UnsafeInternal.getLongUnaligned(null, addr(index), false);
    }

    @Override
    protected void _setShort(int index, int value) {
        UnsafeInternal.putShortUnaligned(null, addr(index), (short) value, true);
    }

    @Override
    protected void _setShortLE(int index, int value) {
        UnsafeInternal.putShortUnaligned(null, addr(index), (short) value, false);
    }

    @Override
    protected void _setInt(int index, int value) {
        UnsafeInternal.putIntUnaligned(null, addr(index), value, true);
    }

    @Override
    protected void _setIntLE(int index, int value) {
        UnsafeInternal.putIntUnaligned(null, addr(index), value, false);
    }

    @Override
    protected void _setLong(int index, long value) {
        UnsafeInternal.putLongUnaligned(null, addr(index), value, true);
    }

    @Override
    protected void _setLongLE(int index, long value) {
        UnsafeInternal.putLongUnaligned(null, addr(index), value, false);
    }
}
