/*
 * Copyright 2015 The Netty Project
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

/**
 * A special {@link AbstractUnpooledSlicedByteBuf} that can make optimizations because it knows the sliced buffer is of
 * type {@link AbstractByteBuf}.
 */
class UnpooledSlicedByteBuf extends AbstractUnpooledSlicedByteBuf {
    UnpooledSlicedByteBuf(AbstractByteBuf buffer, int index, int length) {
        super(buffer, index, length);
    }

    @Override
    public int capacity() {
        return maxCapacity();
    }

    @Override
    public AbstractByteBuf unwrap() {
        return (AbstractByteBuf) super.unwrap();
    }

    /**
     * The capacity of this buffer is fixed when it is created, but the capacity of the wrapped buffer may change
     * afterwards. Because accesses are forwarded to the wrapped buffer without any check beside the one done by
     * {@code checkIndex(...)} against this buffer's fixed capacity, we need to validate the adjusted index against
     * the wrapped buffer's current capacity as well. Otherwise the access may reach past the wrapped buffer's
     * actual allocation, e.g. if the wrapped buffer shrank and freed / reallocated its memory in the meantime.
     */
    private AbstractByteBuf checkedUnwrap(int index, int fieldLength) {
        AbstractByteBuf buf = unwrap();
        // CompositeByteBuf releases the memory of discarded components and detects outdated slices through its
        // own layout validation, which must not be preempted by this check. Its capacity can change without
        // making accesses through this slice unsafe.
        if (!(buf instanceof CompositeByteBuf)) {
            buf.checkIndex0(idx(index), fieldLength);
        }
        return buf;
    }

    @Override
    protected byte _getByte(int index) {
        return checkedUnwrap(index, 1)._getByte(idx(index));
    }

    @Override
    protected short _getShort(int index) {
        return checkedUnwrap(index, 2)._getShort(idx(index));
    }

    @Override
    protected short _getShortLE(int index) {
        return checkedUnwrap(index, 2)._getShortLE(idx(index));
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return checkedUnwrap(index, 3)._getUnsignedMedium(idx(index));
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return checkedUnwrap(index, 3)._getUnsignedMediumLE(idx(index));
    }

    @Override
    protected int _getInt(int index) {
        return checkedUnwrap(index, 4)._getInt(idx(index));
    }

    @Override
    protected int _getIntLE(int index) {
        return checkedUnwrap(index, 4)._getIntLE(idx(index));
    }

    @Override
    protected long _getLong(int index) {
        return checkedUnwrap(index, 8)._getLong(idx(index));
    }

    @Override
    protected long _getLongLE(int index) {
        return checkedUnwrap(index, 8)._getLongLE(idx(index));
    }

    @Override
    protected void _setByte(int index, int value) {
        checkedUnwrap(index, 1)._setByte(idx(index), value);
    }

    @Override
    protected void _setShort(int index, int value) {
        checkedUnwrap(index, 2)._setShort(idx(index), value);
    }

    @Override
    protected void _setShortLE(int index, int value) {
        checkedUnwrap(index, 2)._setShortLE(idx(index), value);
    }

    @Override
    protected void _setMedium(int index, int value) {
        checkedUnwrap(index, 3)._setMedium(idx(index), value);
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        checkedUnwrap(index, 3)._setMediumLE(idx(index), value);
    }

    @Override
    protected void _setInt(int index, int value) {
        checkedUnwrap(index, 4)._setInt(idx(index), value);
    }

    @Override
    protected void _setIntLE(int index, int value) {
        checkedUnwrap(index, 4)._setIntLE(idx(index), value);
    }

    @Override
    protected void _setLong(int index, long value) {
        checkedUnwrap(index, 8)._setLong(idx(index), value);
    }

    @Override
    protected void _setLongLE(int index, long value) {
        checkedUnwrap(index, 8)._setLongLE(idx(index), value);
    }
}
