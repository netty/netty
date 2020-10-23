/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.ByteProcessor;
import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectPool.ObjectCreator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import static io.netty.buffer.AbstractUnpooledSlicedByteBuf.checkSliceOutOfBounds;

final class PooledSlicedByteBuf extends AbstractPooledDerivedByteBuf {

    private static final ObjectPool<PooledSlicedByteBuf> RECYCLER = ObjectPool.newPool(
            new ObjectCreator<PooledSlicedByteBuf>() {
        @Override
        public PooledSlicedByteBuf newObject(Handle<PooledSlicedByteBuf> handle) {
            return new PooledSlicedByteBuf(handle);
        }
    });

    static PooledSlicedByteBuf newInstance(AbstractByteBuf unwrapped, ByteBuf wrapped,
                                           int index, int length) {
        checkSliceOutOfBounds(index, length, unwrapped);
        return newInstance0(unwrapped, wrapped, index, length);
    }

    private static PooledSlicedByteBuf newInstance0(AbstractByteBuf unwrapped, ByteBuf wrapped,
                                                    int adjustment, int length) {
        final PooledSlicedByteBuf slice = RECYCLER.get();
        slice.init(unwrapped, wrapped, 0, length, length);
        slice.discardMarks();
        slice.adjustment = adjustment;

        return slice;
    }

    int adjustment;

    private PooledSlicedByteBuf(Handle<PooledSlicedByteBuf> handle) {
        super(handle);
    }

    @Override
    public int capacity() {
        return maxCapacity();
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        throw new UnsupportedOperationException("sliced buffer");
    }

    @Override
    public int arrayOffset() {
        return idx(unwrap().arrayOffset());
    }

    @Override
    public long memoryAddress() {
        return unwrap().memoryAddress() + adjustment;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex0(index, length);
        return unwrap().nioBuffer(idx(index), length);
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        checkIndex0(index, length);
        return unwrap().nioBuffers(idx(index), length);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex0(index, length);
        return unwrap().copy(idx(index), length);
    }

    @Override
    public ByteBuf slice(int index, int length) {
        checkIndex0(index, length);
        return super.slice(idx(index), length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        checkIndex0(index, length);
        return PooledSlicedByteBuf.newInstance0(unwrap(), this, idx(index), length);
    }

    @Override
    public ByteBuf duplicate() {
        return duplicate0().setIndex(idx(readerIndex()), idx(writerIndex()));
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return PooledDuplicatedByteBuf.newInstance(unwrap(), this, idx(readerIndex()), idx(writerIndex()));
    }

    @Override
    public byte getByte(int index) {
        checkIndex0(index, 1);
        return unwrap().getByte(idx(index));
    }

    @Override
    protected byte _getByte(int index) {
        return unwrap()._getByte(idx(index));
    }

    @Override
    public short getShort(int index) {
        checkIndex0(index, 2);
        return unwrap().getShort(idx(index));
    }

    @Override
    protected short _getShort(int index) {
        return unwrap()._getShort(idx(index));
    }

    @Override
    public short getShortLE(int index) {
        checkIndex0(index, 2);
        return unwrap().getShortLE(idx(index));
    }

    @Override
    protected short _getShortLE(int index) {
        return unwrap()._getShortLE(idx(index));
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex0(index, 3);
        return unwrap().getUnsignedMedium(idx(index));
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return unwrap()._getUnsignedMedium(idx(index));
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        checkIndex0(index, 3);
        return unwrap().getUnsignedMediumLE(idx(index));
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return unwrap()._getUnsignedMediumLE(idx(index));
    }

    @Override
    public int getInt(int index) {
        checkIndex0(index, 4);
        return unwrap().getInt(idx(index));
    }

    @Override
    protected int _getInt(int index) {
        return unwrap()._getInt(idx(index));
    }

    @Override
    public int getIntLE(int index) {
        checkIndex0(index, 4);
        return unwrap().getIntLE(idx(index));
    }

    @Override
    protected int _getIntLE(int index) {
        return unwrap()._getIntLE(idx(index));
    }

    @Override
    public long getLong(int index) {
        checkIndex0(index, 8);
        return unwrap().getLong(idx(index));
    }

    @Override
    protected long _getLong(int index) {
        return unwrap()._getLong(idx(index));
    }

    @Override
    public long getLongLE(int index) {
        checkIndex0(index, 8);
        return unwrap().getLongLE(idx(index));
    }

    @Override
    protected long _getLongLE(int index) {
        return unwrap()._getLongLE(idx(index));
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex0(index, length);
        unwrap().getBytes(idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex0(index, length);
        unwrap().getBytes(idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex0(index, dst.remaining());
        unwrap().getBytes(idx(index), dst);
        return this;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        checkIndex0(index, 1);
        unwrap().setByte(idx(index), value);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        unwrap()._setByte(idx(index), value);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        checkIndex0(index, 2);
        unwrap().setShort(idx(index), value);
        return this;
    }

    @Override
    protected void _setShort(int index, int value) {
        unwrap()._setShort(idx(index), value);
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        checkIndex0(index, 2);
        unwrap().setShortLE(idx(index), value);
        return this;
    }

    @Override
    protected void _setShortLE(int index, int value) {
        unwrap()._setShortLE(idx(index), value);
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        checkIndex0(index, 3);
        unwrap().setMedium(idx(index), value);
        return this;
    }

    @Override
    protected void _setMedium(int index, int value) {
        unwrap()._setMedium(idx(index), value);
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        checkIndex0(index, 3);
        unwrap().setMediumLE(idx(index), value);
        return this;
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        unwrap()._setMediumLE(idx(index), value);
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        checkIndex0(index, 4);
        unwrap().setInt(idx(index), value);
        return this;
    }

    @Override
    protected void _setInt(int index, int value) {
        unwrap()._setInt(idx(index), value);
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        checkIndex0(index, 4);
        unwrap().setIntLE(idx(index), value);
        return this;
    }

    @Override
    protected void _setIntLE(int index, int value) {
        unwrap()._setIntLE(idx(index), value);
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        checkIndex0(index, 8);
        unwrap().setLong(idx(index), value);
        return this;
    }

    @Override
    protected void _setLong(int index, long value) {
        unwrap()._setLong(idx(index), value);
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        checkIndex0(index, 8);
        unwrap().setLongLE(idx(index), value);
        return this;
    }

    @Override
    protected void _setLongLE(int index, long value) {
        unwrap().setLongLE(idx(index), value);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex0(index, length);
        unwrap().setBytes(idx(index), src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkIndex0(index, length);
        unwrap().setBytes(idx(index), src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        checkIndex0(index, src.remaining());
        unwrap().setBytes(idx(index), src);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length)
            throws IOException {
        checkIndex0(index, length);
        unwrap().getBytes(idx(index), out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        checkIndex0(index, length);
        return unwrap().getBytes(idx(index), out, length);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length)
            throws IOException {
        checkIndex0(index, length);
        return unwrap().getBytes(idx(index), out, position, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        checkIndex0(index, length);
        return unwrap().setBytes(idx(index), in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        checkIndex0(index, length);
        return unwrap().setBytes(idx(index), in, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length)
            throws IOException {
        checkIndex0(index, length);
        return unwrap().setBytes(idx(index), in, position, length);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        checkIndex0(index, length);
        int ret = unwrap().forEachByte(idx(index), length, processor);
        if (ret < adjustment) {
            return -1;
        }
        return ret - adjustment;
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        checkIndex0(index, length);
        int ret = unwrap().forEachByteDesc(idx(index), length, processor);
        if (ret < adjustment) {
            return -1;
        }
        return ret - adjustment;
    }

    private int idx(int index) {
        return index + adjustment;
    }
}
