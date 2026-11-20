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
 * {@link DuplicatedByteBuf} implementation that can do optimizations because it knows the duplicated buffer
 * is of type {@link AbstractByteBuf}.
 */
class UnpooledDuplicatedByteBuf extends DuplicatedByteBuf {
    UnpooledDuplicatedByteBuf(AbstractByteBuf buffer) {
        super(buffer);
    }

    @Override
    public AbstractByteBuf unwrap() {
        return (AbstractByteBuf) super.unwrap();
    }

    private AbstractByteBuf checkedUnwrap(int index, int fieldLength) {
        AbstractByteBuf buf = unwrap();
        // CompositeByteBuf releases the memory of discarded components and detects outdated derived buffers
        // through its own layout validation, which must not be preempted by this check. Its capacity can
        // change without making accesses through this buffer unsafe.
        if (!(buf instanceof CompositeByteBuf)) {
            buf.checkIndex0(index, fieldLength);
        }
        return buf;
    }

    @Override
    protected byte _getByte(int index) {
        return checkedUnwrap(index, 1)._getByte(index);
    }

    @Override
    protected short _getShort(int index) {
        return checkedUnwrap(index, 2)._getShort(index);
    }

    @Override
    protected short _getShortLE(int index) {
        return checkedUnwrap(index, 2)._getShortLE(index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return checkedUnwrap(index, 3)._getUnsignedMedium(index);
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return checkedUnwrap(index, 3)._getUnsignedMediumLE(index);
    }

    @Override
    protected int _getInt(int index) {
        return checkedUnwrap(index, 4)._getInt(index);
    }

    @Override
    protected int _getIntLE(int index) {
        return checkedUnwrap(index, 4)._getIntLE(index);
    }

    @Override
    protected long _getLong(int index) {
        return checkedUnwrap(index, 8)._getLong(index);
    }

    @Override
    protected long _getLongLE(int index) {
        return checkedUnwrap(index, 8)._getLongLE(index);
    }

    @Override
    protected void _setByte(int index, int value) {
        checkedUnwrap(index, 1)._setByte(index, value);
    }

    @Override
    protected void _setShort(int index, int value) {
        checkedUnwrap(index, 2)._setShort(index, value);
    }

    @Override
    protected void _setShortLE(int index, int value) {
        checkedUnwrap(index, 2)._setShortLE(index, value);
    }

    @Override
    protected void _setMedium(int index, int value) {
        checkedUnwrap(index, 3)._setMedium(index, value);
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        checkedUnwrap(index, 3)._setMediumLE(index, value);
    }

    @Override
    protected void _setInt(int index, int value) {
        checkedUnwrap(index, 4)._setInt(index, value);
    }

    @Override
    protected void _setIntLE(int index, int value) {
        checkedUnwrap(index, 4)._setIntLE(index, value);
    }

    @Override
    protected void _setLong(int index, long value) {
        checkedUnwrap(index, 8)._setLong(index, value);
    }

    @Override
    protected void _setLongLE(int index, long value) {
        checkedUnwrap(index, 8)._setLongLE(index, value);
    }
}
