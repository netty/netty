/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.replay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBufferIndexFinder;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
class ReplayingDecoderBuffer implements ChannelBuffer {

    private static final Error REPLAY = new ReplayError();

    private final ChannelBuffer buffer;
    private boolean terminated;

    public static ReplayingDecoderBuffer EMPTY_BUFFER = new ReplayingDecoderBuffer(ChannelBuffers.EMPTY_BUFFER);

    static {
        EMPTY_BUFFER.terminate();
    }

    ReplayingDecoderBuffer(ChannelBuffer buffer) {
        this.buffer = buffer;
    }

    void terminate() {
        terminated = true;
    }

    @Override
    public int capacity() {
        if (terminated) {
            return buffer.capacity();
        } else {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int arrayOffset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnreplayableOperationException();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int compareTo(ChannelBuffer buffer) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ChannelBuffer copy() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ChannelBuffer copy(int index, int length) {
        checkIndex(index, length);
        return buffer.copy(index, length);
    }

    @Override
    public void discardReadBytes() {
        throw new UnreplayableOperationException();
    }

    @Override
    public void ensureWritableBytes(int writableBytes) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ChannelBuffer duplicate() {
        throw new UnreplayableOperationException();
    }
    
    @Override
    public boolean getBoolean(int index) {
        checkIndex(index);
        return buffer.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        checkIndex(index);
        return buffer.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        checkIndex(index);
        return buffer.getUnsignedByte(index);
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, byte[] dst) {
        checkIndex(index, dst.length);
        buffer.getBytes(index, dst);
    }

    @Override
    public void getBytes(int index, ByteBuffer dst) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, ChannelBuffer dst, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void getBytes(int index, ChannelBuffer dst) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    @Override
    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return buffer.getInt(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        checkIndex(index, 4);
        return buffer.getUnsignedInt(index);
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return buffer.getLong(index);
    }

    @Override
    public int getMedium(int index) {
        checkIndex(index, 3);
        return buffer.getMedium(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return buffer.getUnsignedMedium(index);
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return buffer.getShort(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        checkIndex(index, 2);
        return buffer.getUnsignedShort(index);
    }

    @Override
    public char getChar(int index) {
        checkIndex(index, 2);
        return buffer.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        checkIndex(index, 4);
        return buffer.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        checkIndex(index, 8);
        return buffer.getDouble(index);
    }

    @Override
    public int hashCode() {
        throw new UnreplayableOperationException();
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        int endIndex = buffer.indexOf(fromIndex, toIndex, value);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return endIndex;
    }

    @Override
    public int indexOf(int fromIndex, int toIndex,
            ChannelBufferIndexFinder indexFinder) {
        int endIndex = buffer.indexOf(fromIndex, toIndex, indexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return endIndex;
    }

    @Override
    public int bytesBefore(byte value) {
        int bytes = buffer.bytesBefore(value);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    @Override
    public int bytesBefore(ChannelBufferIndexFinder indexFinder) {
        int bytes = buffer.bytesBefore(indexFinder);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    @Override
    public int bytesBefore(int length, byte value) {
        checkReadableBytes(length);
        int bytes = buffer.bytesBefore(length, value);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    @Override
    public int bytesBefore(int length, ChannelBufferIndexFinder indexFinder) {
        checkReadableBytes(length);
        int bytes = buffer.bytesBefore(length, indexFinder);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        int bytes = buffer.bytesBefore(index, length, value);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    @Override
    public int bytesBefore(int index, int length,
            ChannelBufferIndexFinder indexFinder) {
        int bytes = buffer.bytesBefore(index, length, indexFinder);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    @Override
    public void markReaderIndex() {
        buffer.markReaderIndex();
    }

    @Override
    public void markWriterIndex() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ChannelBufferFactory factory() {
        return buffer.factory();
    }

    @Override
    public ByteOrder order() {
        return buffer.order();
    }

    @Override
    public boolean readable() {
        return terminated? buffer.readable() : true;
    }

    @Override
    public int readableBytes() {
        if (terminated) {
            return buffer.readableBytes();
        } else {
            return Integer.MAX_VALUE - buffer.readerIndex();
        }
    } 
    
    @Override
    public boolean readBoolean() {
        checkReadableBytes(1);
        return buffer.readBoolean();
    }

    @Override
    public byte readByte() {
        checkReadableBytes(1);
        return buffer.readByte();
    }

    @Override
    public short readUnsignedByte() {
        checkReadableBytes(1);
        return buffer.readUnsignedByte();
    }

    @Override
    public void readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
    }

    @Override
    public void readBytes(byte[] dst) {
        checkReadableBytes(dst.length);
        buffer.readBytes(dst);
    }

    @Override
    public void readBytes(ByteBuffer dst) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void readBytes(ChannelBuffer dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
    }

    @Override
    public void readBytes(ChannelBuffer dst, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void readBytes(ChannelBuffer dst) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    @Override
    public ChannelBuffer readBytes(int length) {
        checkReadableBytes(length);
        return buffer.readBytes(length);
    }

    @Override
    public ChannelBuffer readSlice(int length) {
        checkReadableBytes(length);
        return buffer.readSlice(length);
    }

    @Override
    public void readBytes(OutputStream out, int length) throws IOException {
        throw new UnreplayableOperationException();
    }

    @Override
    public int readerIndex() {
        return buffer.readerIndex();
    }

    @Override
    public void readerIndex(int readerIndex) {
        buffer.readerIndex(readerIndex);
    }

    @Override
    public int readInt() {
        checkReadableBytes(4);
        return buffer.readInt();
    }

    @Override
    public long readUnsignedInt() {
        checkReadableBytes(4);
        return buffer.readUnsignedInt();
    }

    @Override
    public long readLong() {
        checkReadableBytes(8);
        return buffer.readLong();
    }

    @Override
    public int readMedium() {
        checkReadableBytes(3);
        return buffer.readMedium();
    }

    @Override
    public int readUnsignedMedium() {
        checkReadableBytes(3);
        return buffer.readUnsignedMedium();
    }

    @Override
    public short readShort() {
        checkReadableBytes(2);
        return buffer.readShort();
    }

    @Override
    public int readUnsignedShort() {
        checkReadableBytes(2);
        return buffer.readUnsignedShort();
    }

    @Override
    public char readChar() {
        checkReadableBytes(2);
        return buffer.readChar();
    }

    @Override
    public float readFloat() {
        checkReadableBytes(4);
        return buffer.readFloat();
    }

    @Override
    public double readDouble() {
        checkReadableBytes(8);
        return buffer.readDouble();
    }

    @Override
    public void resetReaderIndex() {
        buffer.resetReaderIndex();
    }

    @Override
    public void resetWriterIndex() {
        throw new UnreplayableOperationException();
    }
    
    @Override
    public void setBoolean(int index, boolean value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setByte(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setBytes(int index, byte[] src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setBytes(int index, ByteBuffer src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setBytes(int index, ChannelBuffer src, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setBytes(int index, ChannelBuffer src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setZero(int index, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setIndex(int readerIndex, int writerIndex) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setInt(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setLong(int index, long value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setMedium(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setShort(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setChar(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setFloat(int index, float value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void setDouble(int index, double value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void skipBytes(int length) {
        checkReadableBytes(length);
        buffer.skipBytes(length);
    }

    @Override
    public ChannelBuffer slice() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
        return buffer.slice(index, length);
    }

    @Override
    public ByteBuffer toByteBuffer() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuffer toByteBuffer(int index, int length) {
        checkIndex(index, length);
        return buffer.toByteBuffer(index, length);
    }

    @Override
    public ByteBuffer[] toByteBuffers() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuffer[] toByteBuffers(int index, int length) {
        checkIndex(index, length);
        return buffer.toByteBuffers(index, length);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        checkIndex(index, length);
        return buffer.toString(index, length, charset);
    }

    @Override
    public String toString(Charset charsetName) {
        throw new UnreplayableOperationException();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' +
               "ridx=" +
               readerIndex() +
               ", " +
               "widx=" +
               writerIndex() +
               ')';
    }

    @Override
    public boolean writable() {
        return false;
    }

    @Override
    public int writableBytes() {
        return 0;
    }
    
    @Override
    public void writeBoolean(boolean value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeByte(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeBytes(byte[] src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeBytes(byte[] src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeBytes(ByteBuffer src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeBytes(ChannelBuffer src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeBytes(ChannelBuffer src, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeBytes(ChannelBuffer src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        throw new UnreplayableOperationException();
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeInt(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeLong(long value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeMedium(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeZero(int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int writerIndex() {
        return buffer.writerIndex();
    }

    @Override
    public void writerIndex(int writerIndex) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeShort(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeChar(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeFloat(float value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public void writeDouble(double value) {
        throw new UnreplayableOperationException();
    }

    private void checkIndex(int index) {
        if (index > buffer.writerIndex()) {
            throw REPLAY;
        }
    }

    private void checkIndex(int index, int length) {
        if (index + length > buffer.writerIndex()) {
            throw REPLAY;
        }
    }

    private void checkReadableBytes(int readableBytes) {
        if (buffer.readableBytes() < readableBytes) {
            throw REPLAY;
        }
    }
}
