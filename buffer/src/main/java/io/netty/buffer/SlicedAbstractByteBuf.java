/*
 * Copyright 2015 The Netty Project
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

/**
 * A special {@link SlicedByteBuf} that can make optimizations because it knows the sliced buffer is of type
 * {@link AbstractByteBuf}.
 */
final class SlicedAbstractByteBuf extends SlicedByteBuf {

    SlicedAbstractByteBuf(AbstractByteBuf buffer, int index, int length) {
        super(buffer, index, length);
    }

    @Override
    protected byte _getByte(int index) {
        return unwrap0()._getByte(idx(index));
    }

    @Override
    protected short _getShort(int index) {
        return unwrap0()._getShort(idx(index));
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return unwrap0()._getUnsignedMedium(idx(index));
    }

    @Override
    protected int _getInt(int index) {
        return unwrap0()._getInt(idx(index));
    }

    @Override
    protected long _getLong(int index) {
        return unwrap0()._getLong(idx(index));
    }

    @Override
    protected void _setByte(int index, int value) {
        unwrap0()._setByte(idx(index), value);
    }

    @Override
    protected void _setShort(int index, int value) {
        unwrap0()._setShort(idx(index), value);
    }

    @Override
    protected void _setMedium(int index, int value) {
        unwrap0()._setMedium(idx(index), value);
    }

    @Override
    protected void _setInt(int index, int value) {
        unwrap0()._setInt(idx(index), value);
    }

    @Override
    protected void _setLong(int index, long value) {
        unwrap0()._setLong(idx(index), value);
    }

    private AbstractByteBuf unwrap0() {
        return (AbstractByteBuf) unwrap();
    }
}
