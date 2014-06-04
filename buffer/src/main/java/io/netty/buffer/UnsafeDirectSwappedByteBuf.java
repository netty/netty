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

import io.netty.util.internal.PlatformDependent;

import java.nio.ByteOrder;

/**
 * Special {@link SwappedByteBuf} for {@link ByteBuf}s that are backed by a {@code memoryAddress}.
 */
final class UnsafeDirectSwappedByteBuf extends SwappedByteBuf {
    private static final boolean NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private final boolean nativeByteOrder;
    private final AbstractByteBuf wrapped;
    private final long memoryAddress;

    UnsafeDirectSwappedByteBuf(AbstractByteBuf buf, long memoryAddress) {
        super(buf);
        wrapped = buf;
        this.memoryAddress = memoryAddress;
        nativeByteOrder = NATIVE_ORDER == (order() == ByteOrder.BIG_ENDIAN);
    }

    private long addr(int index) {
        return memoryAddress + index;
    }

    @Override
    public long getLong(int index) {
        wrapped.checkIndex(index, 8);
        long v = PlatformDependent.getLong(addr(index));
        return nativeByteOrder? v : Long.reverseBytes(v);
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
        int v = PlatformDependent.getInt(addr(index));
        return nativeByteOrder? v : Integer.reverseBytes(v);
    }

    @Override
    public int getUnsignedShort(int index) {
        return getShort(index) & 0xFFFF;
    }

    @Override
    public short getShort(int index) {
        wrapped.checkIndex(index, 2);
        short v = PlatformDependent.getShort(addr(index));
        return nativeByteOrder? v : Short.reverseBytes(v);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        wrapped.checkIndex(index, 2);
        _setShort(index, value);
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        wrapped.checkIndex(index, 4);
        _setInt(index, value);
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        wrapped.checkIndex(index, 8);
        _setLong(index, value);
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
        wrapped.ensureWritable(2);
        _setShort(wrapped.writerIndex, value);
        wrapped.writerIndex += 2;
        return this;
    }

    @Override
    public ByteBuf writeInt(int value) {
        wrapped.ensureWritable(4);
        _setInt(wrapped.writerIndex, value);
        wrapped.writerIndex += 4;
        return this;
    }

    @Override
    public ByteBuf writeLong(long value) {
        wrapped.ensureWritable(8);
        _setLong(wrapped.writerIndex, value);
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

    private void _setShort(int index, int value) {
        PlatformDependent.putShort(addr(index), nativeByteOrder ? (short) value : Short.reverseBytes((short) value));
    }

    private void _setInt(int index, int value) {
        PlatformDependent.putInt(addr(index), nativeByteOrder ? value : Integer.reverseBytes(value));
    }

    private void _setLong(int index, long value) {
        PlatformDependent.putLong(addr(index), nativeByteOrder ? value : Long.reverseBytes(value));
    }
}
