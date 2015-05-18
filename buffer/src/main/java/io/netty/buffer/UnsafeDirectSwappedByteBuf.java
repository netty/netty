/*
* Copyright 2014 The Netty Project
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

import java.nio.ByteOrder;

/**
 * Special {@link SwappedByteBuf} for {@link ByteBuf}s that are backed by a {@code memoryAddress}.
 */
final class UnsafeDirectSwappedByteBuf extends SwappedByteBuf {
    private static final boolean NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private final boolean nativeByteOrder;
    private final AbstractByteBuf wrapped;

    UnsafeDirectSwappedByteBuf(AbstractByteBuf buf) {
        super(buf);
        wrapped = buf;
        nativeByteOrder = NATIVE_ORDER == (order() == ByteOrder.BIG_ENDIAN);
    }

    @Override
    public long getLong(int index) {
        wrapped.checkIndex(index, 8);
        return UnsafeDirectByteBufUtil._getLong(wrapped, index, nativeByteOrder);
    }

    @Override
    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    @Override
    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    @Override
    public char getChar(int index) {
        return (char) getShort(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return getInt(index) & 0xFFFFFFFFL;
    }

    @Override
    public int getInt(int index) {
        wrapped.checkIndex(index, 4);
        return UnsafeDirectByteBufUtil._getInt(wrapped, index, nativeByteOrder);
    }

    @Override
    public int getUnsignedShort(int index) {
        return getShort(index) & 0xFFFF;
    }

    @Override
    public short getShort(int index) {
        wrapped.checkIndex(index, 2);
        return UnsafeDirectByteBufUtil._getShort(wrapped, index, nativeByteOrder);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        wrapped.checkIndex(index, 2);
        UnsafeDirectByteBufUtil._setShort(wrapped, index, value, nativeByteOrder);
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        wrapped.checkIndex(index, 4);
        UnsafeDirectByteBufUtil._setInt(wrapped, index, value, nativeByteOrder);
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        wrapped.checkIndex(index, 8);
        UnsafeDirectByteBufUtil._setLong(wrapped, index, value, nativeByteOrder);
        return this;
    }

    @Override
    public ByteBuf setChar(int index, int value) {
        setShort(index, value);
        return this;
    }

    @Override
    public ByteBuf setFloat(int index, float value) {
        setInt(index, Float.floatToRawIntBits(value));
        return this;
    }

    @Override
    public ByteBuf setDouble(int index, double value) {
        setLong(index, Double.doubleToRawLongBits(value));
        return this;
    }

    @Override
    public ByteBuf writeShort(int value) {
        wrapped.ensureAccessible();
        wrapped.ensureWritable(2);
        UnsafeDirectByteBufUtil._setShort(wrapped, wrapped.writerIndex, value, nativeByteOrder);
        wrapped.writerIndex += 2;
        return this;
    }

    @Override
    public ByteBuf writeInt(int value) {
        wrapped.ensureAccessible();
        wrapped.ensureWritable(4);
        UnsafeDirectByteBufUtil._setInt(wrapped, wrapped.writerIndex, value, nativeByteOrder);
        wrapped.writerIndex += 4;
        return this;
    }

    @Override
    public ByteBuf writeLong(long value) {
        wrapped.ensureAccessible();
        wrapped.ensureWritable(8);
        UnsafeDirectByteBufUtil._setLong(wrapped, wrapped.writerIndex, value, nativeByteOrder);
        wrapped.writerIndex += 8;
        return this;
    }

    @Override
    public ByteBuf writeChar(int value) {
        writeShort(value);
        return this;
    }

    @Override
    public ByteBuf writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
        return this;
    }

    @Override
    public ByteBuf writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
        return this;
    }
}
