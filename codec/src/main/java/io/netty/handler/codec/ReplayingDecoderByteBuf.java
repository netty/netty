/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.SwappedByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ByteProcessor;
import io.netty.util.Signal;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

/**
 * Special {@link ByteBuf} implementation which is used by the {@link ReplayingDecoder}
 */
final class ReplayingDecoderByteBuf extends ByteBuf {

    private static final Signal REPLAY = ReplayingDecoder.REPLAY;

    private ByteBuf buffer;
    private boolean terminated;
    private SwappedByteBuf swapped;

    @SuppressWarnings("checkstyle:StaticFinalBuffer")  // Unpooled.EMPTY_BUFFER is not writeable or readable.
    static final ReplayingDecoderByteBuf EMPTY_BUFFER = new ReplayingDecoderByteBuf(Unpooled.EMPTY_BUFFER);

    static {
        EMPTY_BUFFER.terminate();
    }

    ReplayingDecoderByteBuf() { }

    ReplayingDecoderByteBuf(ByteBuf buffer) {
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
        throw reject();
    }

    @Override
    public int maxCapacity() {
        return capacity();
    }

    @Override
    public ByteBufAllocator alloc() {
        return buffer.alloc();
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ByteBuf asReadOnly() {
        return Unpooled.unmodifiableBuffer(this);
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
        throw reject();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int compareTo(ByteBuf buffer) {
        throw reject();
    }

    @Override
    public ByteBuf copy() {
        throw reject();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        return buffer.copy(index, length);
    }

    @Override
    public ByteBuf discardReadBytes() {
        throw reject();
    }

    @Override
    public ByteBuf ensureWritable(int writableBytes) {
        throw reject();
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        throw reject();
    }

    @Override
    public ByteBuf duplicate() {
        throw reject();
    }

    @Override
    public ByteBuf retainedDuplicate() {
        throw reject();
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
        throw reject();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        throw reject();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        throw reject();
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) {
        throw reject();
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) {
        throw reject();
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) {
        throw reject();
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return buffer.getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        checkIndex(index, 4);
        return buffer.getIntLE(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        checkIndex(index, 4);
        return buffer.getUnsignedInt(index);
    }

    @Override
    public long getUnsignedIntLE(int index) {
        checkIndex(index, 4);
        return buffer.getUnsignedIntLE(index);
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return buffer.getLong(index);
    }

    @Override
    public long getLongLE(int index) {
        checkIndex(index, 8);
        return buffer.getLongLE(index);
    }

    @Override
    public int getMedium(int index) {
        checkIndex(index, 3);
        return buffer.getMedium(index);
    }

    @Override
    public int getMediumLE(int index) {
        checkIndex(index, 3);
        return buffer.getMediumLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return buffer.getUnsignedMedium(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        checkIndex(index, 3);
        return buffer.getUnsignedMediumLE(index);
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return buffer.getShort(index);
    }

    @Override
    public short getShortLE(int index) {
        checkIndex(index, 2);
        return buffer.getShortLE(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        checkIndex(index, 2);
        return buffer.getUnsignedShort(index);
    }

    @Override
    public int getUnsignedShortLE(int index) {
        checkIndex(index, 2);
        return buffer.getUnsignedShortLE(index);
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
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        checkIndex(index, length);
        return buffer.getCharSequence(index, length, charset);
    }

    @Override
    public int hashCode() {
        throw reject();
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        if (fromIndex == toIndex) {
            return -1;
        }

        if (Math.max(fromIndex, toIndex) > buffer.writerIndex()) {
            throw REPLAY;
        }

        return buffer.indexOf(fromIndex, toIndex, value);
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
    public int bytesBefore(int length, byte value) {
        return bytesBefore(buffer.readerIndex(), length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        final int writerIndex = buffer.writerIndex();
        if (index >= writerIndex) {
            throw REPLAY;
        }

        if (index <= writerIndex - length) {
            return buffer.bytesBefore(index, length, value);
        }

        int res = buffer.bytesBefore(index, writerIndex - index, value);
        if (res < 0) {
            throw REPLAY;
        } else {
            return res;
        }
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        int ret = buffer.forEachByte(processor);
        if (ret < 0) {
            throw REPLAY;
        } else {
            return ret;
        }
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        final int writerIndex = buffer.writerIndex();
        if (index >= writerIndex) {
            throw REPLAY;
        }

        if (index <= writerIndex - length) {
            return buffer.forEachByte(index, length, processor);
        }

        int ret = buffer.forEachByte(index, writerIndex - index, processor);
        if (ret < 0) {
            throw REPLAY;
        } else {
            return ret;
        }
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        if (terminated) {
            return buffer.forEachByteDesc(processor);
        } else {
            throw reject();
        }
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        if (index + length > buffer.writerIndex()) {
            throw REPLAY;
        }

        return buffer.forEachByteDesc(index, length, processor);
    }

    @Override
    public ByteBuf markReaderIndex() {
        buffer.markReaderIndex();
        return this;
    }

    @Override
    public ByteBuf markWriterIndex() {
        throw reject();
    }

    @Override
    public ByteOrder order() {
        return buffer.order();
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        if (ObjectUtil.checkNotNull(endianness, "endianness") == order()) {
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
        return !terminated || buffer.isReadable();
    }

    @Override
    public boolean isReadable(int size) {
        return !terminated || buffer.isReadable(size);
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
        throw reject();
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        throw reject();
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        checkReadableBytes(dst.writableBytes());
        buffer.readBytes(dst);
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) {
        throw reject();
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) {
        throw reject();
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
    public ByteBuf readRetainedSlice(int length) {
        checkReadableBytes(length);
        return buffer.readRetainedSlice(length);
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) {
        throw reject();
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
    public int readIntLE() {
        checkReadableBytes(4);
        return buffer.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        checkReadableBytes(4);
        return buffer.readUnsignedInt();
    }

    @Override
    public long readUnsignedIntLE() {
        checkReadableBytes(4);
        return buffer.readUnsignedIntLE();
    }

    @Override
    public long readLong() {
        checkReadableBytes(8);
        return buffer.readLong();
    }

    @Override
    public long readLongLE() {
        checkReadableBytes(8);
        return buffer.readLongLE();
    }

    @Override
    public int readMedium() {
        checkReadableBytes(3);
        return buffer.readMedium();
    }

    @Override
    public int readMediumLE() {
        checkReadableBytes(3);
        return buffer.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        checkReadableBytes(3);
        return buffer.readUnsignedMedium();
    }

    @Override
    public int readUnsignedMediumLE() {
        checkReadableBytes(3);
        return buffer.readUnsignedMediumLE();
    }

    @Override
    public short readShort() {
        checkReadableBytes(2);
        return buffer.readShort();
    }

    @Override
    public short readShortLE() {
        checkReadableBytes(2);
        return buffer.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        checkReadableBytes(2);
        return buffer.readUnsignedShort();
    }

    @Override
    public int readUnsignedShortLE() {
        checkReadableBytes(2);
        return buffer.readUnsignedShortLE();
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
    public CharSequence readCharSequence(int length, Charset charset) {
        checkReadableBytes(length);
        return buffer.readCharSequence(length, charset);
    }

    @Override
    public ByteBuf resetReaderIndex() {
        buffer.resetReaderIndex();
        return this;
    }

    @Override
    public ByteBuf resetWriterIndex() {
        throw reject();
    }

    @Override
    public ByteBuf setBoolean(int index, boolean value) {
        throw reject();
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        throw reject();
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        throw reject();
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        throw reject();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        throw reject();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        throw reject();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        throw reject();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        throw reject();
    }

    @Override
    public int setBytes(int index, InputStream in, int length) {
        throw reject();
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        throw reject();
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) {
        throw reject();
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) {
        throw reject();
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        throw reject();
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        throw reject();
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        throw reject();
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        throw reject();
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        throw reject();
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        throw reject();
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        throw reject();
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        throw reject();
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        throw reject();
    }

    @Override
    public ByteBuf setChar(int index, int value) {
        throw reject();
    }

    @Override
    public ByteBuf setFloat(int index, float value) {
        throw reject();
    }

    @Override
    public ByteBuf setDouble(int index, double value) {
        throw reject();
    }

    @Override
    public ByteBuf skipBytes(int length) {
        checkReadableBytes(length);
        buffer.skipBytes(length);
        return this;
    }

    @Override
    public ByteBuf slice() {
        throw reject();
    }

    @Override
    public ByteBuf retainedSlice() {
        throw reject();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        checkIndex(index, length);
        return buffer.slice(index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        checkIndex(index, length);
        return buffer.retainedSlice(index, length);
    }

    @Override
    public int nioBufferCount() {
        return buffer.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        throw reject();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        return buffer.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        throw reject();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        checkIndex(index, length);
        return buffer.nioBuffers(index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return buffer.internalNioBuffer(index, length);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        checkIndex(index, length);
        return buffer.toString(index, length, charset);
    }

    @Override
    public String toString(Charset charsetName) {
        throw reject();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + '(' +
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
        throw reject();
    }

    @Override
    public ByteBuf writeByte(int value) {
        throw reject();
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        throw reject();
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        throw reject();
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        throw reject();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        throw reject();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        throw reject();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        throw reject();
    }

    @Override
    public int writeBytes(InputStream in, int length) {
        throw reject();
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) {
        throw reject();
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) {
        throw reject();
    }

    @Override
    public ByteBuf writeInt(int value) {
        throw reject();
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        throw reject();
    }

    @Override
    public ByteBuf writeLong(long value) {
        throw reject();
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        throw reject();
    }

    @Override
    public ByteBuf writeMedium(int value) {
        throw reject();
    }

    @Override
    public ByteBuf writeMediumLE(int value) {
        throw reject();
    }

    @Override
    public ByteBuf writeZero(int length) {
        throw reject();
    }

    @Override
    public int writerIndex() {
        return buffer.writerIndex();
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        throw reject();
    }

    @Override
    public ByteBuf writeShort(int value) {
        throw reject();
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        throw reject();
    }

    @Override
    public ByteBuf writeChar(int value) {
        throw reject();
    }

    @Override
    public ByteBuf writeFloat(float value) {
        throw reject();
    }

    @Override
    public ByteBuf writeDouble(double value) {
        throw reject();
    }

    @Override
    public int setCharSequence(int index, CharSequence sequence, Charset charset) {
        throw reject();
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        throw reject();
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
        throw reject();
    }

    @Override
    public int refCnt() {
        return buffer.refCnt();
    }

    @Override
    public ByteBuf retain() {
        throw reject();
    }

    @Override
    public ByteBuf retain(int increment) {
        throw reject();
    }

    @Override
    public ByteBuf touch() {
        buffer.touch();
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        buffer.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        throw reject();
    }

    @Override
    public boolean release(int decrement) {
        throw reject();
    }

    @Override
    public ByteBuf unwrap() {
        throw reject();
    }

    private static UnsupportedOperationException reject() {
        return new UnsupportedOperationException("not a replayable operation");
    }
}
