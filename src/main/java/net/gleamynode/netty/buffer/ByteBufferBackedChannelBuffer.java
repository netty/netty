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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;


public class ByteBufferBackedChannelBuffer extends AbstractChannelBuffer {

    private final ByteBuffer buffer;
    private final int capacity;

    public ByteBufferBackedChannelBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }

        this.buffer = buffer.slice();
        capacity = buffer.remaining();
        writerIndex(capacity);
    }

    private ByteBufferBackedChannelBuffer(ByteBufferBackedChannelBuffer buffer) {
        this.buffer = buffer.buffer;
        capacity = buffer.capacity;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    public ByteOrder order() {
        return buffer.order();
    }

    public int capacity() {
        return capacity;
    }

    public byte getByte(int index) {
        return buffer.get(index);
    }

    public short getShort(int index) {
        return buffer.getShort(index);
    }

    public int getMedium(int index) {
        return  (getByte(index)   & 0xff) << 16 |
                (getByte(index+1) & 0xff) <<  8 |
                (getByte(index+2) & 0xff) <<  0;
    }

    public int getInt(int index) {
        return buffer.getInt(index);
    }

    public long getLong(int index) {
        return buffer.getLong(index);
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        if (dst instanceof ByteBufferBackedChannelBuffer) {
            ByteBufferBackedChannelBuffer bbdst = (ByteBufferBackedChannelBuffer) dst;
            ByteBuffer data = bbdst.buffer.duplicate();

            data.limit(dstIndex + length).position(dstIndex);
            getBytes(index, data);
        } else {
            final int srcEndIndex = index + length;
            for (int i = index; i < srcEndIndex; i ++) {
                dst.setByte(dstIndex ++, getByte(i));
            }
        }
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        ByteBuffer data = buffer.duplicate();
        try {
            data.limit(index + length).position(index);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException();
        }
        data.get(dst, dstIndex, length);
    }

    public void getBytes(int index, ByteBuffer dst) {
        ByteBuffer data = buffer.duplicate();
        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        try {
            data.limit(index + bytesToCopy).position(index);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException();
        }
        dst.put(data);
    }

    public void setByte(int index, byte value) {
        buffer.put(index, value);
    }

    public void setShort(int index, short value) {
        buffer.putShort(index, value);
    }

    public void setMedium(int index, int   value) {
        setByte(index,   (byte) (value >>> 16));
        setByte(index+1, (byte) (value >>>  8));
        setByte(index+2, (byte) (value >>>  0));
    }

    public void setInt(int index, int   value) {
        buffer.putInt(index, value);
    }

    public void setLong(int index, long  value) {
        buffer.putLong(index, value);
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        if (src instanceof ByteBufferBackedChannelBuffer) {
            ByteBufferBackedChannelBuffer bbsrc = (ByteBufferBackedChannelBuffer) src;
            ByteBuffer data = bbsrc.buffer.duplicate();

            data.limit(srcIndex + length).position(srcIndex);
            setBytes(index, data);
        } else {
            final int srcEndIndex = srcIndex + length;
            for (int i = srcIndex; i < srcEndIndex; i ++) {
                setByte(index++, src.getByte(i));
            }
        }
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        ByteBuffer data = buffer.duplicate();
        data.limit(index + length).position(index);
        data.put(src, srcIndex, length);
    }

    public void setBytes(int index, ByteBuffer src) {
        ByteBuffer data = buffer.duplicate();
        data.limit(index + src.remaining()).position(index);
        data.put(src);
    }

    public void getBytes(int index, OutputStream out, int length) throws IOException {
        if (length == 0) {
            return;
        }

        if (!buffer.isReadOnly() && buffer.hasArray()) {
            out.write(
                    buffer.array(),
                    index + buffer.arrayOffset(),
                    length);
        } else {
            byte[] tmp = new byte[length];
            ((ByteBuffer) buffer.duplicate().position(index)).get(tmp);
            out.write(tmp);
        }
    }

    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        if (length == 0) {
            return 0;
        }

        return out.write((ByteBuffer) buffer.duplicate().position(index).limit(index + length));
    }

    public void setBytes(int index, InputStream in, int length)
            throws IOException {
        if (length == 0) {
            return;
        }

        if (!buffer.isReadOnly() && buffer.hasArray()) {
            index += buffer.arrayOffset();
            do {
                int readBytes = in.read(
                        buffer.array(), index, length);
                if (readBytes < 0) {
                    throw new EOFException();
                }
                index += readBytes;
                length -= readBytes;
            } while (length > 0);
        } else {
            byte[] tmp = new byte[length];
            for (int i = 0; i < tmp.length;) {
                int readBytes = in.read(tmp, i, tmp.length - i);
                if (readBytes < 0) {
                    throw new EOFException();
                }
                i += readBytes;
            }
            ((ByteBuffer) buffer.duplicate().position(index)).get(tmp);
        }
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        return in.read((ByteBuffer) buffer.duplicate().limit(index + length).position(index));
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        if (index == 0 && length == capacity()) {
            return buffer.duplicate();
        } else {
            return ((ByteBuffer) buffer.duplicate().position(index).limit(index + length)).slice();
        }
    }

    public ChannelBuffer slice(int index, int length) {
        if (index == 0 && length == capacity()) {
            return duplicate();
        } else {
            return new ByteBufferBackedChannelBuffer(
                    ((ByteBuffer) buffer.duplicate().position(index).limit(index + length)));
        }
    }

    public ChannelBuffer duplicate() {
        return new ByteBufferBackedChannelBuffer(this);
    }

    public ChannelBuffer copy(int index, int length) {
        ByteBuffer src = (ByteBuffer) buffer.duplicate().position(index).limit(index + length);
        ByteBuffer dst = buffer.isDirect() ? ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
        dst.put(src);
        dst.clear();
        return new ByteBufferBackedChannelBuffer(dst);
    }
}
