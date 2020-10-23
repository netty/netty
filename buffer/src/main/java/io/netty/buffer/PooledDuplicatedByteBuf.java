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

final class PooledDuplicatedByteBuf extends AbstractPooledDerivedByteBuf {

    private static final ObjectPool<PooledDuplicatedByteBuf> RECYCLER = ObjectPool.newPool(
            new ObjectCreator<PooledDuplicatedByteBuf>() {
        @Override
        public PooledDuplicatedByteBuf newObject(Handle<PooledDuplicatedByteBuf> handle) {
            return new PooledDuplicatedByteBuf(handle);
        }
    });

    static PooledDuplicatedByteBuf newInstance(AbstractByteBuf unwrapped, ByteBuf wrapped,
                                               int readerIndex, int writerIndex) {
        final PooledDuplicatedByteBuf duplicate = RECYCLER.get();
        duplicate.init(unwrapped, wrapped, readerIndex, writerIndex, unwrapped.maxCapacity());
        duplicate.markReaderIndex();
        duplicate.markWriterIndex();

        return duplicate;
    }

    private PooledDuplicatedByteBuf(Handle<PooledDuplicatedByteBuf> handle) {
        super(handle);
    }

    @Override
    public int capacity() {
        return unwrap().capacity();
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        unwrap().capacity(newCapacity);
        return this;
    }

    @Override
    public int arrayOffset() {
        return unwrap().arrayOffset();
    }

    @Override
    public long memoryAddress() {
        return unwrap().memoryAddress();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return unwrap().nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return unwrap().nioBuffers(index, length);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return unwrap().copy(index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return PooledSlicedByteBuf.newInstance(unwrap(), this, index, length);
    }

    @Override
    public ByteBuf duplicate() {
        return duplicate0().setIndex(readerIndex(), writerIndex());
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return PooledDuplicatedByteBuf.newInstance(unwrap(), this, readerIndex(), writerIndex());
    }

    @Override
    public byte getByte(int index) {
        return unwrap().getByte(index);
    }

    @Override
    protected byte _getByte(int index) {
        return unwrap()._getByte(index);
    }

    @Override
    public short getShort(int index) {
        return unwrap().getShort(index);
    }

    @Override
    protected short _getShort(int index) {
        return unwrap()._getShort(index);
    }

    @Override
    public short getShortLE(int index) {
        return unwrap().getShortLE(index);
    }

    @Override
    protected short _getShortLE(int index) {
        return unwrap()._getShortLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return unwrap().getUnsignedMedium(index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return unwrap()._getUnsignedMedium(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return unwrap().getUnsignedMediumLE(index);
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return unwrap()._getUnsignedMediumLE(index);
    }

    @Override
    public int getInt(int index) {
        return unwrap().getInt(index);
    }

    @Override
    protected int _getInt(int index) {
        return unwrap()._getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        return unwrap().getIntLE(index);
    }

    @Override
    protected int _getIntLE(int index) {
        return unwrap()._getIntLE(index);
    }

    @Override
    public long getLong(int index) {
        return unwrap().getLong(index);
    }

    @Override
    protected long _getLong(int index) {
        return unwrap()._getLong(index);
    }

    @Override
    public long getLongLE(int index) {
        return unwrap().getLongLE(index);
    }

    @Override
    protected long _getLongLE(int index) {
        return unwrap()._getLongLE(index);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        unwrap().getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        unwrap().getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        unwrap().getBytes(index, dst);
        return this;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        unwrap().setByte(index, value);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        unwrap()._setByte(index, value);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        unwrap().setShort(index, value);
        return this;
    }

    @Override
    protected void _setShort(int index, int value) {
        unwrap()._setShort(index, value);
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        unwrap().setShortLE(index, value);
        return this;
    }

    @Override
    protected void _setShortLE(int index, int value) {
        unwrap()._setShortLE(index, value);
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        unwrap().setMedium(index, value);
        return this;
    }

    @Override
    protected void _setMedium(int index, int value) {
        unwrap()._setMedium(index, value);
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        unwrap().setMediumLE(index, value);
        return this;
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        unwrap()._setMediumLE(index, value);
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        unwrap().setInt(index, value);
        return this;
    }

    @Override
    protected void _setInt(int index, int value) {
        unwrap()._setInt(index, value);
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        unwrap().setIntLE(index, value);
        return this;
    }

    @Override
    protected void _setIntLE(int index, int value) {
        unwrap()._setIntLE(index, value);
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        unwrap().setLong(index, value);
        return this;
    }

    @Override
    protected void _setLong(int index, long value) {
        unwrap()._setLong(index, value);
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        unwrap().setLongLE(index, value);
        return this;
    }

    @Override
    protected void _setLongLE(int index, long value) {
        unwrap().setLongLE(index, value);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        unwrap().setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        unwrap().setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        unwrap().setBytes(index, src);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length)
            throws IOException {
        unwrap().getBytes(index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return unwrap().getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length)
            throws IOException {
        return unwrap().getBytes(index, out, position, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        return unwrap().setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        return unwrap().setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length)
            throws IOException {
        return unwrap().setBytes(index, in, position, length);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return unwrap().forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return unwrap().forEachByteDesc(index, length, processor);
    }
}
