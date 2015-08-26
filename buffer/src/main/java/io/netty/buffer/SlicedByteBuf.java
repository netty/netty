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
        return buffer.arrayOffset() + adjustment;
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
        return buffer.getByte(index + adjustment);
    }

    @Override
    protected short _getShort(int index) {
        return buffer.getShort(index + adjustment);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return buffer.getUnsignedMedium(index + adjustment);
    }

    @Override
    protected int _getInt(int index) {
        return buffer.getInt(index + adjustment);
    }

    @Override
    protected long _getLong(int index) {
        return buffer.getLong(index + adjustment);
    }

    @Override
    public ByteBuf duplicate() {
        ByteBuf duplicate = buffer.slice(adjustment, length);
        duplicate.setIndex(readerIndex(), writerIndex());
        return duplicate;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        return buffer.copy(index + adjustment, length);
    }

    @Override
    public ByteBuf slice(int index, int length) {
        checkIndex(index, length);
        if (length == 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        return buffer.slice(index + adjustment, length);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        buffer.getBytes(index + adjustment, dst);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        buffer.setByte(index + adjustment, value);
    }

    @Override
    protected void _setShort(int index, int value) {
        buffer.setShort(index + adjustment, value);
    }

    @Override
    protected void _setMedium(int index, int value) {
        buffer.setMedium(index + adjustment, value);
    }

    @Override
    protected void _setInt(int index, int value) {
        buffer.setInt(index + adjustment, value);
    }

    @Override
    protected void _setLong(int index, long value) {
        buffer.setLong(index + adjustment, value);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index + adjustment, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index + adjustment, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        checkIndex(index, src.remaining());
        buffer.setBytes(index + adjustment, src);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        checkIndex(index, length);
        return buffer.getBytes(index + adjustment, out, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index + adjustment, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index + adjustment, in, length);
    }

    @Override
    public int nioBufferCount() {
        return buffer.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        return buffer.nioBuffer(index + adjustment, length);
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        checkIndex(index, length);
        return buffer.nioBuffers(index + adjustment, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return nioBuffer(index, length);
    }

    @Override
    public int forEachByte(int index, int length, ByteBufProcessor processor) {
        int ret = buffer.forEachByte(index + adjustment, length, processor);
        if (ret >= adjustment) {
            return ret - adjustment;
        } else {
            return -1;
        }
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteBufProcessor processor) {
        int ret = buffer.forEachByteDesc(index + adjustment, length, processor);
        if (ret >= adjustment) {
            return ret - adjustment;
        } else {
            return -1;
        }
    }
}
