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
     * afterwards, and if it shrank it may have freed and reallocated its memory. Checking the original index once
     * against the minimum of this buffer's fixed capacity and whatever capacity the wrapped buffer currently
     * provides for this slice covers both bounds with a single check, and keeps the sign / overflow parts of it
     * constant-foldable because they only depend on the original index and field length.
     */
    private AbstractByteBuf checkedUnwrapLimit(int index, int fieldLength) {
        ensureAccessible();
        AbstractByteBuf buf = unwrap();
        int limit = maxCapacity();
        // CompositeByteBuf releases the memory of discarded components and detects outdated slices through its
        // own layout validation, which must not be preempted by this check. Its capacity can change without
        // making accesses through this slice unsafe.
        if (!(buf instanceof CompositeByteBuf)) {
            limit = Math.min(limit, buf.capacity() - idx(0));
        }
        checkIndex0(index, fieldLength, limit);
        return buf;
    }

    /**
     * Variant of {@link #checkedUnwrapLimit(int, int)} for the protected {@code _get/_set} accessors, whose callers
     * (e.g. the {@code read/write} accessors) have already validated accessibility and the index against this
     * buffer's fixed capacity: only the capacity the wrapped buffer currently provides for this slice still needs
     * to be checked.
     */
    private AbstractByteBuf checkedUnwrap(int index, int fieldLength) {
        AbstractByteBuf buf = unwrap();
        // CompositeByteBuf releases the memory of discarded components and detects outdated slices through its
        // own layout validation, which must not be preempted by this check. Its capacity can change without
        // making accesses through this slice unsafe.
        if (!(buf instanceof CompositeByteBuf)) {
            checkIndex0(index, fieldLength, buf.capacity() - idx(0));
        }
        return buf;
    }

    @Override
    public byte getByte(int index) {
        return checkedUnwrapLimit(index, 1)._getByte(idx(index));
    }

    @Override
    public short getShort(int index) {
        return checkedUnwrapLimit(index, 2)._getShort(idx(index));
    }

    @Override
    public short getShortLE(int index) {
        return checkedUnwrapLimit(index, 2)._getShortLE(idx(index));
    }

    @Override
    public int getUnsignedMedium(int index) {
        return checkedUnwrapLimit(index, 3)._getUnsignedMedium(idx(index));
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return checkedUnwrapLimit(index, 3)._getUnsignedMediumLE(idx(index));
    }

    @Override
    public int getInt(int index) {
        return checkedUnwrapLimit(index, 4)._getInt(idx(index));
    }

    @Override
    public int getIntLE(int index) {
        return checkedUnwrapLimit(index, 4)._getIntLE(idx(index));
    }

    @Override
    public long getLong(int index) {
        return checkedUnwrapLimit(index, 8)._getLong(idx(index));
    }

    @Override
    public long getLongLE(int index) {
        return checkedUnwrapLimit(index, 8)._getLongLE(idx(index));
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
    public ByteBuf setByte(int index, int value) {
        checkedUnwrapLimit(index, 1)._setByte(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        checkedUnwrapLimit(index, 2)._setShort(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        checkedUnwrapLimit(index, 2)._setShortLE(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        checkedUnwrapLimit(index, 3)._setMedium(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        checkedUnwrapLimit(index, 3)._setMediumLE(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        checkedUnwrapLimit(index, 4)._setInt(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        checkedUnwrapLimit(index, 4)._setIntLE(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        checkedUnwrapLimit(index, 8)._setLong(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        checkedUnwrapLimit(index, 8)._setLongLE(idx(index), value);
        return this;
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
