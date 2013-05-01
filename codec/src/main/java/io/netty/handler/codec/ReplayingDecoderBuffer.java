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
package io.netty.handler.codec;

import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufIndexFinder;
import io.netty.buffer.SwappedByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.Signal;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

/**
 * Special {@link ByteBuf} implementation which is used by the {@link ReplayingDecoder}
 */
final class ReplayingDecoderBuffer implements ByteBuf {

    private static final Signal REPLAY = ReplayingDecoder.REPLAY;

    private ByteBuf buffer;
    private boolean terminated;
    private SwappedByteBuf swapped;

    static final ReplayingDecoderBuffer EMPTY_BUFFER = new ReplayingDecoderBuffer(Unpooled.EMPTY_BUFFER);

    static {
        EMPTY_BUFFER.terminate();
    }

    ReplayingDecoderBuffer() { }

    ReplayingDecoderBuffer(ByteBuf buffer) {
        setCumulation(buffer);
    }

    void setCumulation(ByteBuf buffer) {
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
    public ByteBuf capacity(int newCapacity) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int maxCapacity() {
        return capacity();
    }

    @Override
    public BufType type() {
        return BufType.BYTE;
    }

    @Override
    public ByteBufAllocator alloc() {
        return buffer.alloc();
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
    public boolean hasMemoryAddress() {
        return false;
    }

    @Override
    public long memoryAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf clear() {
        throw new UnreplayableOperationException();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int compareTo(ByteBuf buffer) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf copy() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        return buffer.copy(index, length);
    }

    @Override
    public ByteBuf discardReadBytes() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf ensureWritable(int writableBytes) {
        throw new UnreplayableOperationException();
    }

    @Override
    @Deprecated
    public ByteBuf ensureWritableBytes(int writableBytes) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf duplicate() {
        throw new UnreplayableOperationException();
    }

    @Override
    public boolean getBoolean(int index) {
        checkIndex(index, 1);
        return buffer.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        checkIndex(index, 1);
        return buffer.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        checkIndex(index, 1);
        return buffer.getUnsignedByte(index);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        checkIndex(index, dst.length);
        buffer.getBytes(index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) {
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
            ByteBufIndexFinder indexFinder) {
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
    public int bytesBefore(ByteBufIndexFinder indexFinder) {
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
    public int bytesBefore(int length, ByteBufIndexFinder indexFinder) {
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
            ByteBufIndexFinder indexFinder) {
        int bytes = buffer.bytesBefore(index, length, indexFinder);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    @Override
    public ByteBuf markReaderIndex() {
        buffer.markReaderIndex();
        return this;
    }

    @Override
    public ByteBuf markWriterIndex() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteOrder order() {
        return buffer.order();
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }
        if (endianness == order()) {
            return this;
        }

        SwappedByteBuf swapped = this.swapped;
        if (swapped == null) {
            this.swapped = swapped = new SwappedByteBuf(this);
        }
        return swapped;
    }

    @Override
    public boolean isReadable() {
        return terminated? buffer.isReadable() : true;
    }

    @Override
    @Deprecated
    public boolean readable() {
        return isReadable();
    }

    @Override
    public boolean isReadable(int size) {
        return terminated? buffer.isReadable(size) : true;
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
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        checkReadableBytes(dst.length);
        buffer.readBytes(dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        checkReadableBytes(dst.writableBytes());
        buffer.readBytes(dst);
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf readBytes(int length) {
        checkReadableBytes(length);
        return buffer.readBytes(length);
    }

    @Override
    public ByteBuf readSlice(int length) {
        checkReadableBytes(length);
        return buffer.readSlice(length);
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int readerIndex() {
        return buffer.readerIndex();
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        buffer.readerIndex(readerIndex);
        return this;
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
    public ByteBuf resetReaderIndex() {
        buffer.resetReaderIndex();
        return this;
    }

    @Override
    public ByteBuf resetWriterIndex() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setBoolean(int index, boolean value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int setBytes(int index, InputStream in, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setChar(int index, int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setFloat(int index, float value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf setDouble(int index, double value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf skipBytes(int length) {
        checkReadableBytes(length);
        buffer.skipBytes(length);
        return this;
    }

    @Override
    public ByteBuf slice() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        checkIndex(index, length);
        return buffer.slice(index, length);
    }

    @Override
    public int nioBufferCount() {
        return buffer.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        return buffer.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        checkIndex(index, length);
        return buffer.nioBuffers(index, length);
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
    public boolean isWritable() {
        return false;
    }

    @Override
    @Deprecated
    public boolean writable() {
        return false;
    }

    @Override
    public boolean isWritable(int size) {
        return false;
    }

    @Override
    public int writableBytes() {
        return 0;
    }

    @Override
    public int maxWritableBytes() {
        return 0;
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeByte(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int writeBytes(InputStream in, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeInt(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeLong(long value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeMedium(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeZero(int length) {
        throw new UnreplayableOperationException();
    }

    @Override
    public int writerIndex() {
        return buffer.writerIndex();
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeShort(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeChar(int value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeFloat(float value) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf writeDouble(double value) {
        throw new UnreplayableOperationException();
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

    @Override
    public ByteBuf discardSomeReadBytes() {
        throw new UnreplayableOperationException();
    }

    @Override
    public int refCnt() {
        return buffer.refCnt();
    }

    @Override
    public ByteBuf retain() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf retain(int increment) {
        throw new UnreplayableOperationException();
    }

    @Override
    public boolean release() {
        throw new UnreplayableOperationException();
    }

    @Override
    public boolean release(int decrement) {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf suspendIntermediaryDeallocations() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf resumeIntermediaryDeallocations() {
        throw new UnreplayableOperationException();
    }

    @Override
    public ByteBuf unwrap() {
        throw new UnreplayableOperationException();
    }
}
