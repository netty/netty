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
package net.gleamynode.netty.handler.codec.replay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.buffer.ChannelBufferIndexFinder;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
class ReplayingDecoderBuffer implements ChannelBuffer {

    private static final Error REPLAY = new ReplayError();


    private final ChannelBuffer buffer;

    ReplayingDecoderBuffer(ChannelBuffer buffer) {
        this.buffer = buffer;
    }

    public int capacity() {
        return Integer.MAX_VALUE;
    }

    public void clear() {
        reject();
    }

    public int compareTo(ChannelBuffer buffer) {
        reject();
        return 0;
    }

    public ChannelBuffer copy() {
        reject();
        return null;
    }

    public ChannelBuffer copy(int index, int length) {
        checkIndex(index, length);
        return buffer.copy(index, length);
    }

    public void discardReadBytes() {
        reject();
    }

    public ChannelBuffer duplicate() {
        reject();
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        reject();
        return false;
    }

    public byte getByte(int index) {
        checkIndex(index);
        return buffer.getByte(index);
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, byte[] dst) {
        checkIndex(index, dst.length);
        buffer.getBytes(index, dst);
    }

    public void getBytes(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        buffer.getBytes(index, dst);
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, ChannelBuffer dst) {
        checkIndex(index, dst.writableBytes());
        buffer.getBytes(index, dst);
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        reject();
        return -1;
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        reject();
    }

    public int getInt(int index) {
        checkIndex(index);
        return buffer.getInt(index);
    }

    public long getLong(int index) {
        checkIndex(index, 8);
        return buffer.getLong(index);
    }

    public int getMedium(int index) {
        checkIndex(index, 3);
        return buffer.getMedium(index);
    }

    public short getShort(int index) {
        checkIndex(index, 2);
        return buffer.getShort(index);
    }

    @Override
    public int hashCode() {
        reject();
        return 0;
    }

    public int indexOf(int fromIndex, int toIndex, byte value) {
        int endIndex = indexOf(buffer.readerIndex(), buffer.writerIndex(), value);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return endIndex;
    }

    public int indexOf(int fromIndex, int toIndex,
            ChannelBufferIndexFinder indexFinder) {
        int endIndex = indexOf(buffer.readerIndex(), buffer.writerIndex(), indexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return endIndex;
    }

    public void markReaderIndex() {
        buffer.markReaderIndex();
    }

    public void markWriterIndex() {
        reject();
    }

    public ByteOrder order() {
        return buffer.order();
    }

    public boolean readable() {
        return true;
    }

    public int readableBytes() {
        return Integer.MAX_VALUE - buffer.readerIndex();
    }

    public byte readByte() {
        checkReadableBytes(1);
        return buffer.readByte();
    }

    public ChannelBuffer readBytes() {
        reject();
        return null;
    }

    public void readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
    }

    public void readBytes(byte[] dst) {
        checkReadableBytes(dst.length);
        buffer.readBytes(dst);
    }

    public void readBytes(ByteBuffer dst) {
        checkReadableBytes(dst.remaining());
        buffer.readBytes(dst);
    }

    public void readBytes(ChannelBuffer dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
    }

    public void readBytes(ChannelBuffer dst, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, length);
    }

    public void readBytes(ChannelBuffer dst) {
        checkReadableBytes(dst.writableBytes());
        buffer.readBytes(dst);
    }

    public ChannelBuffer readBytes(ChannelBufferIndexFinder endIndexFinder) {
        int endIndex = buffer.indexOf(buffer.readerIndex(), buffer.writerIndex(), endIndexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return readBytes(endIndex);
    }

    public int readBytes(GatheringByteChannel out, int length)
            throws IOException {
        reject();
        return -1;
    }

    public ChannelBuffer readBytes(int length) {
        checkReadableBytes(length);
        return buffer.readBytes(length);
    }

    public void readBytes(OutputStream out, int length) throws IOException {
        reject();
    }

    public int readerIndex() {
        return buffer.readerIndex();
    }

    public void readerIndex(int readerIndex) {
        buffer.readerIndex(readerIndex);
    }

    public int readInt() {
        checkReadableBytes(4);
        return buffer.readInt();
    }

    public long readLong() {
        checkReadableBytes(8);
        return buffer.readLong();
    }

    public int readMedium() {
        checkReadableBytes(3);
        return buffer.readMedium();
    }

    public short readShort() {
        checkReadableBytes(2);
        return buffer.readShort();
    }

    public void resetReaderIndex() {
        buffer.resetReaderIndex();
    }

    public void resetWriterIndex() {
        reject();
    }

    public void setByte(int index, byte value) {
        reject();
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        reject();
    }

    public void setBytes(int index, byte[] src) {
        reject();
    }

    public void setBytes(int index, ByteBuffer src) {
        reject();
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        reject();
    }

    public void setBytes(int index, ChannelBuffer src) {
        reject();
    }

    public void setBytes(int index, InputStream in, int length)
            throws IOException {
        reject();
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        reject();
        return -1;
    }

    public void setIndex(int readerIndex, int writerIndex) {
        reject();
    }

    public void setInt(int index, int value) {
        reject();
    }

    public void setLong(int index, long value) {
        reject();
    }

    public void setMedium(int index, int value) {
        reject();
    }

    public void setShort(int index, short value) {
        reject();
    }

    public int skipBytes(ChannelBufferIndexFinder firstIndexFinder) {
        int oldReaderIndex = buffer.readerIndex();
        int newReaderIndex = buffer.indexOf(oldReaderIndex, buffer.writerIndex(), firstIndexFinder);
        if (newReaderIndex < 0) {
            throw REPLAY;
        }
        buffer.readerIndex(newReaderIndex);
        return newReaderIndex - oldReaderIndex;
    }

    public void skipBytes(int length) {
        checkReadableBytes(length);
        buffer.skipBytes(length);
    }

    public ChannelBuffer slice() {
        reject();
        return null;
    }

    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
        return buffer.slice(index, length);
    }

    public ByteBuffer toByteBuffer() {
        reject();
        return null;
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        return buffer.toByteBuffer(index, length);
    }

    public ByteBuffer[] toByteBuffers() {
        reject();
        return null;
    }

    public ByteBuffer[] toByteBuffers(int index, int length) {
        checkIndex(index, length);
        return buffer.toByteBuffers(index, length);
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    public boolean writable() {
        return false;
    }

    public int writableBytes() {
        return 0;
    }

    public void writeByte(byte value) {
        reject();
    }

    public void writeBytes(byte[] src, int srcIndex, int length) {
        reject();
    }

    public void writeBytes(byte[] src) {
        reject();
    }

    public void writeBytes(ByteBuffer src) {
        reject();
    }

    public void writeBytes(ChannelBuffer src, int srcIndex, int length) {
        reject();
    }

    public void writeBytes(ChannelBuffer src, int length) {
        reject();
    }

    public void writeBytes(ChannelBuffer src) {
        reject();
    }

    public void writeBytes(InputStream in, int length) throws IOException {
        reject();
    }

    public int writeBytes(ScatteringByteChannel in, int length)
            throws IOException {
        reject();
        return -1;
    }

    public void writeInt(int value) {
        reject();
    }

    public void writeLong(long value) {
        reject();
    }

    public void writeMedium(int value) {
        reject();
    }

    public void writePlaceholder(int length) {
        reject();
    }

    public int writerIndex() {
        return buffer.writerIndex();
    }

    public void writerIndex(int writerIndex) {
        reject();
    }

    public void writeShort(short value) {
        reject();
    }

    private void checkIndex(int index) {
        if (index > buffer.readerIndex() + buffer.readableBytes()) {
            throw REPLAY;
        }
    }

    private void checkIndex(int index, int length) {
        if (index + length > buffer.readerIndex() + buffer.readableBytes()) {
            throw REPLAY;
        }
    }

    private void checkReadableBytes(int readableBytes) {
        if (buffer.readableBytes() < readableBytes) {
            throw REPLAY;
        }
    }

    private void reject() {
        throw new UnsupportedOperationException(
                "Unsupported in " + ReplayingDecoder.class.getSimpleName());
    }
}