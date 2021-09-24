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
package io.netty.buffer;

import io.netty.util.ByteProcessor;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

/**
 * Wrapper which swap the {@link ByteOrder} of a {@link ByteBuf}.
 *
 * @deprecated use the Little Endian accessors, e.g. {@code getShortLE}, {@code getIntLE}
 * instead.
 */
@Deprecated
public class SwappedByteBuf extends ByteBuf {

    private final ByteBuf buf;
    private final ByteOrder order;

    public SwappedByteBuf(ByteBuf buf) {
        this.buf = ObjectUtil.checkNotNull(buf, "buf");
        if (buf.order() == ByteOrder.BIG_ENDIAN) {
            order = ByteOrder.LITTLE_ENDIAN;
        } else {
            order = ByteOrder.BIG_ENDIAN;
        }
    }

    @Override
    public ByteOrder order() {
        return order;
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        if (ObjectUtil.checkNotNull(endianness, "endianness") == order) {
            return this;
        }
        return buf;
    }

    @Override
    public ByteBuf unwrap() {
        return buf;
    }

    @Override
    public ByteBufAllocator alloc() {
        return buf.alloc();
    }

    @Override
    public int capacity() {
        return buf.capacity();
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        buf.capacity(newCapacity);
        return this;
    }

    @Override
    public int maxCapacity() {
        return buf.maxCapacity();
    }

    @Override
    public boolean isReadOnly() {
        return buf.isReadOnly();
    }

    @Override
    public ByteBuf asReadOnly() {
        return Unpooled.unmodifiableBuffer(this);
    }

    @Override
    public boolean isDirect() {
        return buf.isDirect();
    }

    @Override
    public int readerIndex() {
        return buf.readerIndex();
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        buf.readerIndex(readerIndex);
        return this;
    }

    @Override
    public int writerIndex() {
        return buf.writerIndex();
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        buf.writerIndex(writerIndex);
        return this;
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        buf.setIndex(readerIndex, writerIndex);
        return this;
    }

    @Override
    public int readableBytes() {
        return buf.readableBytes();
    }

    @Override
    public int writableBytes() {
        return buf.writableBytes();
    }

    @Override
    public int maxWritableBytes() {
        return buf.maxWritableBytes();
    }

    @Override
    public int maxFastWritableBytes() {
        return buf.maxFastWritableBytes();
    }

    @Override
    public boolean isReadable() {
        return buf.isReadable();
    }

    @Override
    public boolean isReadable(int size) {
        return buf.isReadable(size);
    }

    @Override
    public boolean isWritable() {
        return buf.isWritable();
    }

    @Override
    public boolean isWritable(int size) {
        return buf.isWritable(size);
    }

    @Override
    public ByteBuf clear() {
        buf.clear();
        return this;
    }

    @Override
    public ByteBuf markReaderIndex() {
        buf.markReaderIndex();
        return this;
    }

    @Override
    public ByteBuf resetReaderIndex() {
        buf.resetReaderIndex();
        return this;
    }

    @Override
    public ByteBuf markWriterIndex() {
        buf.markWriterIndex();
        return this;
    }

    @Override
    public ByteBuf resetWriterIndex() {
        buf.resetWriterIndex();
        return this;
    }

    @Override
    public ByteBuf discardReadBytes() {
        buf.discardReadBytes();
        return this;
    }

    @Override
    public ByteBuf discardSomeReadBytes() {
        buf.discardSomeReadBytes();
        return this;
    }

    @Override
    public ByteBuf ensureWritable(int writableBytes) {
        buf.ensureWritable(writableBytes);
        return this;
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        return buf.ensureWritable(minWritableBytes, force);
    }

    @Override
    public boolean getBoolean(int index) {
        return buf.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        return buf.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        return buf.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        return ByteBufUtil.swapShort(buf.getShort(index));
    }

    @Override
    public short getShortLE(int index) {
        return buf.getShortLE(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        return getShort(index) & 0xFFFF;
    }

    @Override
    public int getUnsignedShortLE(int index) {
        return getShortLE(index) & 0xFFFF;
    }

    @Override
    public int getMedium(int index) {
        return ByteBufUtil.swapMedium(buf.getMedium(index));
    }

    @Override
    public int getMediumLE(int index) {
        return buf.getMediumLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return getMedium(index) & 0xFFFFFF;
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return getMediumLE(index) & 0xFFFFFF;
    }

    @Override
    public int getInt(int index) {
        return ByteBufUtil.swapInt(buf.getInt(index));
    }

    @Override
    public int getIntLE(int index) {
        return buf.getIntLE(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return getInt(index) & 0xFFFFFFFFL;
    }

    @Override
    public long getUnsignedIntLE(int index) {
        return getIntLE(index) & 0xFFFFFFFFL;
    }

    @Override
    public long getLong(int index) {
        return ByteBufUtil.swapLong(buf.getLong(index));
    }

    @Override
    public long getLongLE(int index) {
        return buf.getLongLE(index);
    }

    @Override
    public char getChar(int index) {
        return (char) getShort(index);
    }

    @Override
    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    @Override
    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        buf.getBytes(index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        buf.getBytes(index, dst, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        buf.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        buf.getBytes(index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        buf.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        buf.getBytes(index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        buf.getBytes(index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return buf.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return buf.getBytes(index, out, position, length);
    }

    @Override
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        return buf.getCharSequence(index, length, charset);
    }

    @Override
    public ByteBuf setBoolean(int index, boolean value) {
        buf.setBoolean(index, value);
        return this;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        buf.setByte(index, value);
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        buf.setShort(index, ByteBufUtil.swapShort((short) value));
        return this;
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        buf.setShortLE(index, (short) value);
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        buf.setMedium(index, ByteBufUtil.swapMedium(value));
        return this;
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        buf.setMediumLE(index, value);
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        buf.setInt(index, ByteBufUtil.swapInt(value));
        return this;
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        buf.setIntLE(index, value);
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        buf.setLong(index, ByteBufUtil.swapLong(value));
        return this;
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        buf.setLongLE(index, value);
        return this;
    }

    @Override
    public ByteBuf setChar(int index, int value) {
        setShort(index, value);
        return this;
    }

    @Override
    public ByteBuf setFloat(int index, float value) {
        setInt(index, Float.floatToRawIntBits(value));
        return this;
    }

    @Override
    public ByteBuf setDouble(int index, double value) {
        setLong(index, Double.doubleToRawLongBits(value));
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        buf.setBytes(index, src);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        buf.setBytes(index, src, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        buf.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        buf.setBytes(index, src);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        buf.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        buf.setBytes(index, src);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return buf.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return buf.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        return buf.setBytes(index, in, position, length);
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        buf.setZero(index, length);
        return this;
    }

    @Override
    public int setCharSequence(int index, CharSequence sequence, Charset charset) {
        return buf.setCharSequence(index, sequence, charset);
    }

    @Override
    public boolean readBoolean() {
        return buf.readBoolean();
    }

    @Override
    public byte readByte() {
        return buf.readByte();
    }

    @Override
    public short readUnsignedByte() {
        return buf.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return ByteBufUtil.swapShort(buf.readShort());
    }

    @Override
    public short readShortLE() {
        return buf.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    @Override
    public int readUnsignedShortLE() {
        return readShortLE() & 0xFFFF;
    }

    @Override
    public int readMedium() {
        return ByteBufUtil.swapMedium(buf.readMedium());
    }

    @Override
    public int readMediumLE() {
        return buf.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        return readMedium() & 0xFFFFFF;
    }

    @Override
    public int readUnsignedMediumLE() {
        return readMediumLE() & 0xFFFFFF;
    }

    @Override
    public int readInt() {
        return ByteBufUtil.swapInt(buf.readInt());
    }

    @Override
    public int readIntLE() {
        return buf.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        return readInt() & 0xFFFFFFFFL;
    }

    @Override
    public long readUnsignedIntLE() {
        return readIntLE() & 0xFFFFFFFFL;
    }

    @Override
    public long readLong() {
        return ByteBufUtil.swapLong(buf.readLong());
    }

    @Override
    public long readLongLE() {
        return buf.readLongLE();
    }

    @Override
    public char readChar() {
        return (char) readShort();
    }

    @Override
    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public ByteBuf readBytes(int length) {
        return buf.readBytes(length).order(order());
    }

    @Override
    public ByteBuf readSlice(int length) {
        return buf.readSlice(length).order(order);
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        return buf.readRetainedSlice(length).order(order);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        buf.readBytes(dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        buf.readBytes(dst, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        buf.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        buf.readBytes(dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        buf.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        buf.readBytes(dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        buf.readBytes(out, length);
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return buf.readBytes(out, length);
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        return buf.readBytes(out, position, length);
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return buf.readCharSequence(length, charset);
    }

    @Override
    public ByteBuf skipBytes(int length) {
        buf.skipBytes(length);
        return this;
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        buf.writeBoolean(value);
        return this;
    }

    @Override
    public ByteBuf writeByte(int value) {
        buf.writeByte(value);
        return this;
    }

    @Override
    public ByteBuf writeShort(int value) {
        buf.writeShort(ByteBufUtil.swapShort((short) value));
        return this;
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        buf.writeShortLE((short) value);
        return this;
    }

    @Override
    public ByteBuf writeMedium(int value) {
        buf.writeMedium(ByteBufUtil.swapMedium(value));
        return this;
    }

    @Override
    public ByteBuf writeMediumLE(int value) {
        buf.writeMediumLE(value);
        return this;
    }

    @Override
    public ByteBuf writeInt(int value) {
        buf.writeInt(ByteBufUtil.swapInt(value));
        return this;
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        buf.writeIntLE(value);
        return this;
    }

    @Override
    public ByteBuf writeLong(long value) {
        buf.writeLong(ByteBufUtil.swapLong(value));
        return this;
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        buf.writeLongLE(value);
        return this;
    }

    @Override
    public ByteBuf writeChar(int value) {
        writeShort(value);
        return this;
    }

    @Override
    public ByteBuf writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
        return this;
    }

    @Override
    public ByteBuf writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        buf.writeBytes(src);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        buf.writeBytes(src, length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        buf.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        buf.writeBytes(src);
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        buf.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        buf.writeBytes(src);
        return this;
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        return buf.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        return buf.writeBytes(in, length);
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        return buf.writeBytes(in, position, length);
    }

    @Override
    public ByteBuf writeZero(int length) {
        buf.writeZero(length);
        return this;
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        return buf.writeCharSequence(sequence, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return buf.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return buf.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return buf.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return buf.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return buf.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return buf.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return buf.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return buf.forEachByteDesc(index, length, processor);
    }

    @Override
    public ByteBuf copy() {
        return buf.copy().order(order);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return buf.copy(index, length).order(order);
    }

    @Override
    public ByteBuf slice() {
        return buf.slice().order(order);
    }

    @Override
    public ByteBuf retainedSlice() {
        return buf.retainedSlice().order(order);
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return buf.slice(index, length).order(order);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return buf.retainedSlice(index, length).order(order);
    }

    @Override
    public ByteBuf duplicate() {
        return buf.duplicate().order(order);
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return buf.retainedDuplicate().order(order);
    }

    @Override
    public int nioBufferCount() {
        return buf.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        return buf.nioBuffer().order(order);
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return buf.nioBuffer(index, length).order(order);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        ByteBuffer[] nioBuffers = buf.nioBuffers();
        for (int i = 0; i < nioBuffers.length; i++) {
            nioBuffers[i] = nioBuffers[i].order(order);
        }
        return nioBuffers;
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        ByteBuffer[] nioBuffers = buf.nioBuffers(index, length);
        for (int i = 0; i < nioBuffers.length; i++) {
            nioBuffers[i] = nioBuffers[i].order(order);
        }
        return nioBuffers;
    }

    @Override
    public boolean hasArray() {
        return buf.hasArray();
    }

    @Override
    public byte[] array() {
        return buf.array();
    }

    @Override
    public int arrayOffset() {
        return buf.arrayOffset();
    }

    @Override
    public boolean hasMemoryAddress() {
        return buf.hasMemoryAddress();
    }

    @Override
    public boolean isContiguous() {
        return buf.isContiguous();
    }

    @Override
    public long memoryAddress() {
        return buf.memoryAddress();
    }

    @Override
    public String toString(Charset charset) {
        return buf.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return buf.toString(index, length, charset);
    }

    @Override
    public int refCnt() {
        return buf.refCnt();
    }

    @Override
    final boolean isAccessible() {
        return buf.isAccessible();
    }

    @Override
    public ByteBuf retain() {
        buf.retain();
        return this;
    }

    @Override
    public ByteBuf retain(int increment) {
        buf.retain(increment);
        return this;
    }

    @Override
    public ByteBuf touch() {
        buf.touch();
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        buf.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return buf.release();
    }

    @Override
    public boolean release(int decrement) {
        return buf.release(decrement);
    }

    @Override
    public int hashCode() {
        return buf.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ByteBuf) {
            return ByteBufUtil.equals(this, (ByteBuf) obj);
        }
        return false;
    }

    @Override
    public int compareTo(ByteBuf buffer) {
        return ByteBufUtil.compare(this, buffer);
    }

    @Override
    public String toString() {
        return "Swapped(" + buf + ')';
    }
}
