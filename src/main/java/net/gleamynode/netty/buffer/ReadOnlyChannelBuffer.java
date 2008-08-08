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

public class ReadOnlyChannelBuffer extends AbstractChannelBuffer implements WrappedChannelBuffer {

    private final ChannelBuffer buffer;

    public ReadOnlyChannelBuffer(ChannelBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    private ReadOnlyChannelBuffer(ReadOnlyChannelBuffer buffer) {
        this.buffer = buffer.buffer;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    public ChannelBuffer unwrap() {
        return buffer;
    }

    public ByteOrder order() {
        return buffer.order();
    }

    @Override
    public void discardReadBytes() {
        rejectModification();
    }

    public void setByte(int index, byte value) {
        rejectModification();
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        rejectModification();
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        rejectModification();
    }

    public void setBytes(int index, ByteBuffer src) {
        rejectModification();
    }

    public void setShort(int index, short value) {
        rejectModification();
    }

    public void setMedium(int index, int value) {
        rejectModification();
    }

    public void setInt(int index, int value) {
        rejectModification();
    }

    public void setLong(int index, long value) {
        rejectModification();
    }

    public void setBytes(int index, InputStream in, int length)
            throws IOException {
        rejectModification();
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        rejectModification();
        return 0;
    }

    protected void rejectModification() {
        throw new UnsupportedOperationException("read-only");
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return buffer.getBytes(index, out, length);
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        buffer.getBytes(index, out, length);
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

    public ChannelBuffer duplicate() {
        return new ReadOnlyChannelBuffer(this);
    }

    public ChannelBuffer copy(int index, int length) {
        return new ReadOnlyChannelBuffer(buffer.copy(index, length));
    }

    public ChannelBuffer slice(int index, int length) {
        return new ReadOnlyChannelBuffer(buffer.slice(index, length));
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

    public ByteBuffer toByteBuffer(int index, int length) {
        return buffer.toByteBuffer(index, length).asReadOnlyBuffer();
    }

    public int capacity() {
        return buffer.capacity();
    }
}
