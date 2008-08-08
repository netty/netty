/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;


/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class DynamicChannelBuffer extends AbstractChannelBuffer {

    private final int initialCapacity;
    private final ByteOrder endianness;
    private ChannelBuffer buffer = ChannelBuffer.EMPTY_BUFFER;

    public DynamicChannelBuffer(int estimatedLength) {
        this(ByteOrder.BIG_ENDIAN, estimatedLength);
    }

    public DynamicChannelBuffer(ByteOrder endianness, int estimatedLength) {
        if (estimatedLength <= 0) {
            throw new IllegalArgumentException("estimatedLength");
        }
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }

        initialCapacity = estimatedLength;
        this.endianness = endianness;
    }

    public ByteOrder order() {
        return endianness;
    }

    public int capacity() {
        return buffer.capacity();
    }

    public byte getByte(int index) {
        return buffer.getByte(index);
    }

    public short getShort(int index) {
        return buffer.getShort(index);
    }

    public int getMedium(int index) {
        return buffer.getMedium(index);
    }

    public int getInt(int index) {
        return buffer.getInt(index);
    }

    public long getLong(int index) {
        return buffer.getLong(index);
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, ByteBuffer dst) {
        buffer.getBytes(index, dst);
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return buffer.getBytes(index, out, length);
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        buffer.getBytes(index, out, length);
    }

    public void setByte(int index, byte value) {
        buffer.setByte(index, value);
    }

    public void setShort(int index, short value) {
        buffer.setShort(index, value);
    }

    public void setMedium(int index, int value) {
        buffer.setMedium(index, value);
    }

    public void setInt(int index, int value) {
        buffer.setInt(index, value);
    }

    public void setLong(int index, long value) {
        buffer.setLong(index, value);
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
    }

    public void setBytes(int index, ByteBuffer src) {
        buffer.setBytes(index, src);
    }

    public void setBytes(int index, InputStream in, int length)
            throws IOException {
        buffer.setBytes(index, in, length);
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        return buffer.setBytes(index, in, length);
    }

    @Override
    public void writeByte(byte value) {
        ensureWriteRemaining(1);
        super.writeByte(value);
    }

    @Override
    public void writeShort(short value) {
        ensureWriteRemaining(2);
        super.writeShort(value);
    }

    @Override
    public void writeMedium(int value) {
        ensureWriteRemaining(3);
        super.writeMedium(value);
    }

    @Override
    public void writeInt(int value) {
        ensureWriteRemaining(4);
        super.writeInt(value);
    }

    @Override
    public void writeLong(long value) {
        ensureWriteRemaining(8);
        super.writeLong(value);
    }

    @Override
    public void writeBytes(byte[] src, int srcIndex, int length) {
        ensureWriteRemaining(length);
        super.writeBytes(src, srcIndex, length);
    }

    @Override
    public void writeBytes(ChannelBuffer src, int srcIndex, int length) {
        ensureWriteRemaining(length);
        super.writeBytes(src, srcIndex, length);
    }

    @Override
    public void writeBytes(ByteBuffer src) {
        ensureWriteRemaining(src.remaining());
        super.writeBytes(src);
    }

    @Override
    public void writePlaceholder(int length) {
        ensureWriteRemaining(length);
        super.writePlaceholder(length);
    }

    public ChannelBuffer duplicate() {
        return new DuplicatedChannelBuffer(this);
    }

    public ChannelBuffer copy(int index, int length) {
        DynamicChannelBuffer copiedBuffer = new DynamicChannelBuffer(endianness, Math.max(length, 64));
        copiedBuffer.buffer = buffer.copy();
        if (copiedBuffer.buffer.capacity() == 0) {
            copiedBuffer.buffer = ChannelBuffer.EMPTY_BUFFER;
        }
        return copiedBuffer;
    }

    public ChannelBuffer slice(int index, int length) {
        if (index == 0) {
            return new TruncatedChannelBuffer(this, length);
        } else {
            return new SlicedChannelBuffer(this, index, length);
        }
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        return buffer.toByteBuffer(index, length);
    }

    private void ensureWriteRemaining(int requestedBytes) {
        if (requestedBytes <= writableBytes()) {
            return;
        }

        int newCapacity;
        if (capacity() == 0) {
            newCapacity = initialCapacity;
        } else {
            newCapacity = capacity();
        }
        int minNewCapacity = writerIndex() + requestedBytes;
        while (newCapacity < minNewCapacity) {
            newCapacity <<= 1;
        }

        ChannelBuffer newBuffer = ChannelBuffers.buffer(endianness, newCapacity);
        newBuffer.writeBytes(buffer, readerIndex(), readableBytes());
        buffer = newBuffer;
    }
}
