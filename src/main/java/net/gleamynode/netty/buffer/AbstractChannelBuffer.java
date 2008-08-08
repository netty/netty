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
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.NoSuchElementException;


/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 */
public abstract class AbstractChannelBuffer implements ChannelBuffer {

    private int hashCode;
    private int readerIndex;
    private int writerIndex;
    private int markedReaderIndex;
    private int markedWriterIndex;

    public int readerIndex() {
        return readerIndex;
    }

    public void readerIndex(int readerIndex) {
        if (readerIndex < 0 || readerIndex > writerIndex) {
            throw new IndexOutOfBoundsException();
        }
        this.readerIndex = readerIndex;
    }

    public int writerIndex() {
        return writerIndex;
    }

    public void writerIndex(int writerIndex) {
        if (writerIndex < readerIndex || writerIndex > capacity()) {
            throw new IndexOutOfBoundsException();
        }
        this.writerIndex = writerIndex;
    }

    public boolean isReadable() {
        return readableBytes() > 0;
    }

    public boolean isWritable() {
        return writableBytes() > 0;
    }

    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    public int writableBytes() {
        return capacity() - writerIndex;
    }

    public void markReaderIndex() {
        markedReaderIndex = readerIndex;
    }

    public void resetReaderIndex() {
        readerIndex(markedReaderIndex);
    }

    public void markWriterIndex() {
        markedWriterIndex = writerIndex;
    }

    public void resetWriterIndex() {
        writerIndex = markedWriterIndex;
    }

    public void discardReadBytes() {
        if (readerIndex == 0) {
            return;
        }
        setBytes(0, this, readerIndex, writerIndex - readerIndex);
        writerIndex -= readerIndex;
        markedReaderIndex = Math.max(markedReaderIndex - readerIndex, 0);
        markedWriterIndex = Math.max(markedWriterIndex - readerIndex, 0);
        readerIndex = 0;
    }

    public void getBytes(int index, byte[] dst) {
        getBytes(index, dst, 0, dst.length);
    }

    public void getBytes(int index, ChannelBuffer dst) {
        getBytes(index, dst, dst.readerIndex(), dst.readableBytes());
    }

    public void setBytes(int index, byte[] src) {
        setBytes(index, src, 0, src.length);
    }

    public void setBytes(int index, ChannelBuffer src) {
        setBytes(index, src, src.readerIndex(), src.readableBytes());
    }

    public byte readByte() {
        if (readerIndex == writerIndex) {
            throw new IndexOutOfBoundsException();
        }
        return getByte(readerIndex ++);
    }

    public short readShort() {
        checkReaderRemaining(2);
        short v = getShort(readerIndex);
        readerIndex += 2;
        return v;
    }

    public int readMedium() {
        checkReaderRemaining(3);
        int v = getMedium(readerIndex);
        readerIndex += 3;
        return v;
    }

    public int readInt() {
        checkReaderRemaining(4);
        int v = getInt(readerIndex);
        readerIndex += 4;
        return v;
    }

    public long readLong() {
        checkReaderRemaining(8);
        long v = getLong(readerIndex);
        readerIndex += 8;
        return v;
    }

    public ChannelBuffer readBytes() {
        return readBytes(readableBytes());
    }

    public ChannelBuffer readBytes(int length) {
        checkReaderRemaining(length);
        if (length == 0) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        ChannelBuffer buf = ChannelBuffers.buffer(length);
        buf.writeBytes(this, readerIndex, length);
        readerIndex += length;
        return buf;
    }

    public ChannelBuffer readBytes(ChannelBufferIndexFinder endIndexFinder) {
        int endIndex = indexOf(readerIndex, writerIndex, endIndexFinder);
        if (endIndex < 0) {
            throw new NoSuchElementException();
        }
        return readBytes(endIndex);
    }

    public void readBytes(byte[] dst, int dstIndex, int length) {
        checkReaderRemaining(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
    }

    public void readBytes(byte[] dst) {
        readBytes(dst, 0, dst.length);
    }

    public void readBytes(ChannelBuffer dst) {
        readBytes(dst, dst.writableBytes());
    }

    public void readBytes(ChannelBuffer dst, int length) {
        readBytes(dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
    }

    public void readBytes(ChannelBuffer dst, int dstIndex, int length) {
        checkReaderRemaining(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
    }

    public void readBytes(ByteBuffer dst) {
        int length = dst.remaining();
        checkReaderRemaining(length);
        getBytes(readerIndex, dst);
        readerIndex += length;
    }

    public int readBytes(GatheringByteChannel out, int length)
            throws IOException {
        checkReaderRemaining(length);
        int readBytes = getBytes(readerIndex, out, length);
        readerIndex += readBytes;
        return readBytes;
    }

    public void readBytes(OutputStream out, int length) throws IOException {
        checkReaderRemaining(length);
        getBytes(readerIndex, out, length);
        readerIndex += length;
    }

    public void skipBytes(int length) {
        int newReaderIndex = readerIndex + length;
        if (newReaderIndex > writerIndex) {
            throw new IndexOutOfBoundsException();
        }
        readerIndex = newReaderIndex;
    }

    public int skipBytes(ChannelBufferIndexFinder firstIndexFindex) {
        int oldReaderIndex = readerIndex;
        int newReaderIndex = indexOf(oldReaderIndex, writerIndex, firstIndexFindex);
        if (newReaderIndex < 0) {
            throw new NoSuchElementException();
        }
        readerIndex(newReaderIndex);
        return newReaderIndex - oldReaderIndex;
    }

    public void writeByte(byte value) {
        setByte(writerIndex ++, value);
    }

    public void writeShort(short value) {
        setShort(writerIndex, value);
        writerIndex += 2;
    }

    public void writeMedium(int value) {
        setMedium(writerIndex, value);
        writerIndex += 3;
    }

    public void writeInt(int value) {
        setInt(writerIndex, value);
        writerIndex += 4;
    }

    public void writeLong(long value) {
        setLong(writerIndex, value);
        writerIndex += 8;
    }

    public void writeBytes(byte[] src, int srcIndex, int length) {
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
    }

    public void writeBytes(byte[] src) {
        writeBytes(src, 0, src.length);
    }

    public void writeBytes(ChannelBuffer src) {
        writeBytes(src, src.readableBytes());
    }

    public void writeBytes(ChannelBuffer src, int length) {
        writeBytes(src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
    }

    public void writeBytes(ChannelBuffer src, int srcIndex, int length) {
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
    }

    public void writeBytes(ByteBuffer src) {
        int length = src.remaining();
        setBytes(writerIndex, src);
        writerIndex += length;
    }

    public void writeBytes(InputStream in, int length)
            throws IOException {
        setBytes(writerIndex, in, length);
        writerIndex += length;
    }

    public int writeBytes(ScatteringByteChannel in, int length)
            throws IOException {
        int writtenBytes = setBytes(writerIndex, in, length);
        writerIndex += writtenBytes;
        return writtenBytes;
    }

    public void writePlaceholder(int length) {
        if (length == 0) {
            return;
        }
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length must be 0 or greater than 0.");
        }
        int nLong = length >>> 3;
        int nBytes = length & 7;
        for (int i = nLong; i > 0; i --) {
            writeLong(0);
        }
        if (nBytes == 4) {
            writeInt(0);
        } else if (nBytes < 4) {
            for (int i = nBytes; i > 0; i --) {
                writeByte((byte) 0);
            }
        } else {
            writeInt(0);
            for (int i = nBytes - 4; i > 0; i --) {
                writeByte((byte) 0);
            }
        }
    }

    public ChannelBuffer slice() {
        return slice(readerIndex, readableBytes());
    }

    public ByteBuffer toByteBuffer() {
        return toByteBuffer(readerIndex, readableBytes());
    }

    public ByteBuffer[] toByteBuffers() {
        return toByteBuffers(readerIndex, readableBytes());
    }

    public ByteBuffer[] toByteBuffers(int index, int length) {
        return new ByteBuffer[] { toByteBuffer(index, length) };
    }

    public int indexOf(int fromIndex, int toIndex, byte value) {
        return ChannelBuffers.indexOf(this, fromIndex, toIndex, value);
    }

    public int indexOf(int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder) {
        return ChannelBuffers.indexOf(this, fromIndex, toIndex, indexFinder);
    }

    @Override
    public int hashCode() {
        if (hashCode != 0) {
            return hashCode;
        }
        return hashCode = ChannelBuffers.hashCode(this);
    }

    protected void clearHashCode() {
        hashCode = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChannelBuffer)) {
            return false;
        }
        return ChannelBuffers.equals(this, (ChannelBuffer) o);
    }

    public int compareTo(ChannelBuffer that) {
        return ChannelBuffers.compare(this, that);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' +
               "ridx=" + readerIndex + ", " +
               "widx=" + writerIndex + ", " +
               "cap=" + capacity() +
               ')';
    }

    private void checkReaderRemaining(int minReaderRemaining) {
        if (readableBytes() < minReaderRemaining) {
            throw new IndexOutOfBoundsException();
        }
    }
}
