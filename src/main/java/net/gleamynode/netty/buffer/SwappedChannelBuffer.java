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
public class SwappedChannelBuffer extends AbstractChannelBuffer implements WrappedChannelBuffer {

    private final ChannelBuffer buffer;

    public SwappedChannelBuffer(ChannelBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
        readerIndex(buffer.readerIndex());
        writerIndex(buffer.writerIndex());
    }

    private SwappedChannelBuffer(SwappedChannelBuffer buffer) {
        this.buffer = buffer.buffer;
        readerIndex(buffer.readerIndex());
        writerIndex(buffer.writerIndex());
    }

    public ChannelBuffer unwrap() {
        return buffer;
    }

    public byte getByte(int index) {
        return buffer.getByte(index);
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, ByteBuffer dst) {
        buffer.getBytes(index, dst);
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        buffer.getBytes(index, out, length);
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return buffer.getBytes(index, out, length);
    }

    public int getInt(int index) {
        return swapInt(buffer.getInt(index));
    }

    public long getLong(int index) {
        return swapLong(buffer.getLong(index));
    }

    public int getMedium(int index) {
        return swapMedium(buffer.getMedium(index));
    }

    public short getShort(int index) {
        return swapShort(buffer.getShort(index));
    }

    public int capacity() {
        return buffer.capacity();
    }

    public void setByte(int index, byte value) {
        buffer.setByte(index, value);
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
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

    public void setInt(int index, int value) {
        buffer.setInt(index, swapInt(value));
    }

    public void setLong(int index, long value) {
        buffer.setLong(index, swapLong(value));
    }

    public void setMedium(int index, int value) {
        buffer.setMedium(index, swapMedium(value));
    }

    public void setShort(int index, short value) {
        buffer.setShort(index, swapShort(value));
    }

    public ChannelBuffer duplicate() {
        return new SwappedChannelBuffer(this);
    }

    public ChannelBuffer slice(int index, int length) {
        if (index == 0) {
            if (length == capacity()) {
                return duplicate();
            } else {
                return new TruncatedChannelBuffer(new SwappedChannelBuffer(buffer), length);
            }
        } else {
            return new SlicedChannelBuffer(new SwappedChannelBuffer(buffer), index, length);
        }
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        ByteBuffer buf = buffer.toByteBuffer(index, length);
        if (buf.order() == ByteOrder.BIG_ENDIAN) {
            return buf.order(ByteOrder.LITTLE_ENDIAN);
        } else {
            return buf.order(ByteOrder.BIG_ENDIAN);
        }
    }


    private static short swapShort(short value) {
        return (short) (value << 8 | value >>> 8 & 0xff);
    }

    private static int swapMedium(int value) {
        return value << 16 & 0xff0000 | value & 0xff00 | value >>> 16 & 0xff;
    }

    private static int swapInt(int value) {
        return swapShort((short) value) <<  16 |
               swapShort((short) (value >>> 16)) & 0xffff;
    }

    private static long swapLong(long value) {
        return (long) swapInt((int) value) <<  32 |
                      swapInt((int) (value >>> 32)) & 0xffffffffL;
    }
}
