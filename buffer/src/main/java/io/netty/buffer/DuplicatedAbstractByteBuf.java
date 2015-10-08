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
 * {@link DuplicatedByteBuf} implementation that can do optimizations because it knows the duplicated buffer
 * is of type {@link AbstractByteBuf}.
 */
final class DuplicatedAbstractByteBuf extends DuplicatedByteBuf {
    public DuplicatedAbstractByteBuf(AbstractByteBuf buffer) {
        super(buffer);
    }

    @Override
    protected byte _getByte(int index) {
        return unwrap0()._getByte(index);
    }

    @Override
    protected short _getShort(int index) {
        return unwrap0()._getShort(index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return unwrap0()._getUnsignedMedium(index);
    }

    @Override
    protected int _getInt(int index) {
        return unwrap0()._getInt(index);
    }

    @Override
    protected long _getLong(int index) {
        return unwrap0()._getLong(index);
    }

    @Override
    protected void _setByte(int index, int value) {
        unwrap0()._setByte(index, value);
    }

    @Override
    protected void _setShort(int index, int value) {
        unwrap0()._setShort(index, value);
    }

    @Override
    protected void _setMedium(int index, int value) {
        unwrap0()._setMedium(index, value);
    }

    @Override
    protected void _setInt(int index, int value) {
        unwrap0()._setInt(index, value);
    }

    @Override
    protected void _setLong(int index, long value) {
        unwrap0()._setLong(index, value);
    }

    private AbstractByteBuf unwrap0() {
        return (AbstractByteBuf) unwrap();
    }
}
