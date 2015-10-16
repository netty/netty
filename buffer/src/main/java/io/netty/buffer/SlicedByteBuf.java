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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;


/**
 * A derived buffer which exposes its parent's sub-region only.  It is
 * recommended to use {@link ByteBuf#slice()} and
 * {@link ByteBuf#slice(int, int)} instead of calling the constructor
 * explicitly.
 */
public class SlicedByteBuf extends AbstractDerivedByteBuf {

    private final ByteBuf buffer;
    private final int adjustment;
    private final int length;

    public SlicedByteBuf(ByteBuf buffer, int index, int length) {
        super(length);
        if (index < 0 || index > buffer.capacity() - length) {
            throw new IndexOutOfBoundsException(buffer + ".slice(" + index + ", " + length + ')');
        }

        if (buffer instanceof SlicedByteBuf) {
            this.buffer = ((SlicedByteBuf) buffer).buffer;
            adjustment = ((SlicedByteBuf) buffer).adjustment + index;
        } else if (buffer instanceof DuplicatedByteBuf) {
            this.buffer = buffer.unwrap();
            adjustment = index;
        } else {
            this.buffer = buffer;
            adjustment = index;
        }
        this.length = length;

        writerIndex(length);
    }

    @Override
    public ByteBuf unwrap() {
        return buffer;
    }

    @Override
    public ByteBufAllocator alloc() {
        return buffer.alloc();
    }

    @Override
    public ByteOrder order() {
        return buffer.order();
    }

    @Override
    public boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
    public int capacity() {
        return length;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        throw new UnsupportedOperationException("sliced buffer");
    }

    @Override
    public boolean hasArray() {
        return buffer.hasArray();
    }

    @Override
    public byte[] array() {
        return buffer.array();
    }

    @Override
    public int arrayOffset() {
        return idx(buffer.arrayOffset());
    }

    @Override
    public boolean hasMemoryAddress() {
        return buffer.hasMemoryAddress();
    }

    @Override
    public long memoryAddress() {
        return buffer.memoryAddress() + adjustment;
    }

    @Override
    protected byte _getByte(int index) {
        return buffer.getByte(idx(index));
    }

    @Override
    protected short _getShort(int index) {
        return buffer.getShort(idx(index));
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return buffer.getUnsignedMedium(idx(index));
    }

    @Override
    protected int _getInt(int index) {
        return buffer.getInt(idx(index));
    }

    @Override
    protected long _getLong(int index) {
        return buffer.getLong(idx(index));
    }

    @Override
    public ByteBuf duplicate() {
        ByteBuf duplicate = buffer.slice(adjustment, length);
        duplicate.setIndex(readerIndex(), writerIndex());
        return duplicate;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex0(index, length);
        return buffer.copy(idx(index), length);
    }

    @Override
    public ByteBuf slice(int index, int length) {
        checkIndex0(index, length);
        return buffer.slice(idx(index), length);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex0(index, length);
        buffer.getBytes(idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex0(index, length);
        buffer.getBytes(idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex0(index, dst.remaining());
        buffer.getBytes(idx(index), dst);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        buffer.setByte(idx(index), value);
    }

    @Override
    protected void _setShort(int index, int value) {
        buffer.setShort(idx(index), value);
    }

    @Override
    protected void _setMedium(int index, int value) {
        buffer.setMedium(idx(index), value);
    }

    @Override
    protected void _setInt(int index, int value) {
        buffer.setInt(idx(index), value);
    }

    @Override
    protected void _setLong(int index, long value) {
        buffer.setLong(idx(index), value);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex0(index, length);
        buffer.setBytes(idx(index), src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkIndex0(index, length);
        buffer.setBytes(idx(index), src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        checkIndex0(index, src.remaining());
        buffer.setBytes(idx(index), src);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex0(index, length);
        buffer.getBytes(idx(index), out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        checkIndex0(index, length);
        return buffer.getBytes(idx(index), out, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkIndex0(index, length);
        return buffer.setBytes(idx(index), in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        checkIndex0(index, length);
        return buffer.setBytes(idx(index), in, length);
    }

    @Override
    public int nioBufferCount() {
        return buffer.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex0(index, length);
        return buffer.nioBuffer(idx(index), length);
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        checkIndex0(index, length);
        return buffer.nioBuffers(idx(index), length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return nioBuffer(index, length);
    }

    @Override
    public int forEachByte(int index, int length, ByteBufProcessor processor) {
        checkIndex0(index, length);
        int ret = buffer.forEachByte(idx(index), length, processor);
        if (ret >= adjustment) {
            return ret - adjustment;
        } else {
            return -1;
        }
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteBufProcessor processor) {
        checkIndex0(index, length);
        int ret = buffer.forEachByteDesc(idx(index), length, processor);
        if (ret >= adjustment) {
            return ret - adjustment;
        } else {
            return -1;
        }
    }

    /**
     * Returns the index with the needed adjustment.
     */
    final int idx(int index) {
        return index + adjustment;
    }
}
