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
package io.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

public class SwappedByteBuf implements WrappedByteBuf {

    private final ByteBuf buf;
    private final ByteOrder order;

    public SwappedByteBuf(ByteBuf buf) {
        if (buf == null) {
            throw new NullPointerException("buf");
        }
        this.buf = buf;
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
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }
        if (endianness == order) {
            return this;
        }
        return buf;
    }

    @Override
    public ByteBuf unwrap() {
        return buf;
    }

    @Override
    public boolean isPooled() {
        return buf.isPooled();
    }

    @Override
    public ChannelBufType type() {
        return ChannelBufType.MESSAGE;
    }

    @Override
    public ByteBufFactory factory() {
        return buf.factory();
    }

    @Override
    public int capacity() {
        return buf.capacity();
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
    public void readerIndex(int readerIndex) {
        buf.readerIndex(readerIndex);
    }

    @Override
    public int writerIndex() {
        return buf.writerIndex();
    }

    @Override
    public void writerIndex(int writerIndex) {
        buf.writerIndex(writerIndex);
    }

    @Override
    public void setIndex(int readerIndex, int writerIndex) {
        buf.setIndex(readerIndex, writerIndex);
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
    public boolean readable() {
        return buf.readable();
    }

    @Override
    public boolean writable() {
        return buf.writable();
    }

    @Override
    public void clear() {
        buf.clear();
    }

    @Override
    public void markReaderIndex() {
        buf.markReaderIndex();
    }

    @Override
    public void resetReaderIndex() {
        buf.resetReaderIndex();
    }

    @Override
    public void markWriterIndex() {
        buf.markWriterIndex();
    }

    @Override
    public void resetWriterIndex() {
        buf.resetWriterIndex();
    }

    @Override
    public void discardReadBytes() {
        buf.discardReadBytes();
    }

    @Override
    public void ensureWritableBytes(int writableBytes) {
        buf.ensureWritableBytes(writableBytes);
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
    public int getUnsignedShort(int index) {
        return getShort(index) & 0xFFFF;
    }

    @Override
    public int getMedium(int index) {
        return ByteBufUtil.swapMedium(buf.getMedium(index));
    }

    @Override
    public int getUnsignedMedium(int index) {
        return getMedium(index) & 0xFFFFFF;
    }

    @Override
    public int getInt(int index) {
        return ByteBufUtil.swapInt(buf.getInt(index));
    }

    @Override
    public long getUnsignedInt(int index) {
        return getInt(index) & 0xFFFFFFFFL;
    }

    @Override
    public long getLong(int index) {
        return ByteBufUtil.swapLong(buf.getLong(index));
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
    public void getBytes(int index, ByteBuf dst) {
        buf.getBytes(index, dst);
    }

    @Override
    public void getBytes(int index, ByteBuf dst, int length) {
        buf.getBytes(index, dst, length);
    }

    @Override
    public void getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        buf.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, byte[] dst) {
        buf.getBytes(index, dst);
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        buf.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, ByteBuffer dst) {
        buf.getBytes(index, dst);
    }

    @Override
    public void getBytes(int index, OutputStream out, int length) throws IOException {
        buf.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return buf.getBytes(index, out, length);
    }

    @Override
    public void setBoolean(int index, boolean value) {
        buf.setBoolean(index, value);
    }

    @Override
    public void setByte(int index, int value) {
        buf.setByte(index, value);
    }

    @Override
    public void setShort(int index, int value) {
        buf.setShort(index, ByteBufUtil.swapShort((short) value));
    }

    @Override
    public void setMedium(int index, int value) {
        buf.setMedium(index, ByteBufUtil.swapMedium(value));
    }

    @Override
    public void setInt(int index, int value) {
        buf.setInt(index, ByteBufUtil.swapInt(value));
    }

    @Override
    public void setLong(int index, long value) {
        buf.setLong(index, ByteBufUtil.swapLong(value));
    }

    @Override
    public void setChar(int index, int value) {
        setShort(index, value);
    }

    @Override
    public void setFloat(int index, float value) {
        setInt(index, Float.floatToRawIntBits(value));
    }

    @Override
    public void setDouble(int index, double value) {
        setLong(index, Double.doubleToRawLongBits(value));
    }

    @Override
    public void setBytes(int index, ByteBuf src) {
        buf.setBytes(index, src);
    }

    @Override
    public void setBytes(int index, ByteBuf src, int length) {
        buf.setBytes(index, src, length);
    }

    @Override
    public void setBytes(int index, ByteBuf src, int srcIndex, int length) {
        buf.setBytes(index, src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, byte[] src) {
        buf.setBytes(index, src);
    }

    @Override
    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        buf.setBytes(index, src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, ByteBuffer src) {
        buf.setBytes(index, src);
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
    public void setZero(int index, int length) {
        buf.setZero(index, length);
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
    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    @Override
    public int readMedium() {
        return ByteBufUtil.swapMedium(buf.readMedium());
    }

    @Override
    public int readUnsignedMedium() {
        return readMedium() & 0xFFFFFF;
    }

    @Override
    public int readInt() {
        return ByteBufUtil.swapInt(buf.readInt());
    }

    @Override
    public long readUnsignedInt() {
        return readInt() & 0xFFFFFFFFL;
    }

    @Override
    public long readLong() {
        return ByteBufUtil.swapLong(buf.readLong());
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
        return buf.readBytes(length);
    }

    @Override
    public ByteBuf readSlice(int length) {
        return buf.readSlice(length);
    }

    @Override
    public void readBytes(ByteBuf dst) {
        buf.readBytes(dst);
    }

    @Override
    public void readBytes(ByteBuf dst, int length) {
        buf.readBytes(dst, length);
    }

    @Override
    public void readBytes(ByteBuf dst, int dstIndex, int length) {
        buf.readBytes(dst, dstIndex, length);
    }

    @Override
    public void readBytes(byte[] dst) {
        buf.readBytes(dst);
    }

    @Override
    public void readBytes(byte[] dst, int dstIndex, int length) {
        buf.readBytes(dst, dstIndex, length);
    }

    @Override
    public void readBytes(ByteBuffer dst) {
        buf.readBytes(dst);
    }

    @Override
    public void readBytes(OutputStream out, int length) throws IOException {
        buf.readBytes(out, length);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return buf.readBytes(out, length);
    }

    @Override
    public void skipBytes(int length) {
        buf.skipBytes(length);
    }

    @Override
    public void writeBoolean(boolean value) {
        buf.writeBoolean(value);
    }

    @Override
    public void writeByte(int value) {
        buf.writeByte(value);
    }

    @Override
    public void writeShort(int value) {
        buf.writeShort(ByteBufUtil.swapShort((short) value));
    }

    @Override
    public void writeMedium(int value) {
        buf.writeMedium(ByteBufUtil.swapMedium(value));
    }

    @Override
    public void writeInt(int value) {
        buf.writeInt(ByteBufUtil.swapInt(value));
    }

    @Override
    public void writeLong(long value) {
        buf.writeLong(ByteBufUtil.swapLong(value));
    }

    @Override
    public void writeChar(int value) {
        writeShort(value);
    }

    @Override
    public void writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
    }

    @Override
    public void writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
    }

    @Override
    public void writeBytes(ByteBuf src) {
        buf.writeBytes(src);
    }

    @Override
    public void writeBytes(ByteBuf src, int length) {
        buf.writeBytes(src, length);
    }

    @Override
    public void writeBytes(ByteBuf src, int srcIndex, int length) {
        buf.writeBytes(src, srcIndex, length);
    }

    @Override
    public void writeBytes(byte[] src) {
        buf.writeBytes(src);
    }

    @Override
    public void writeBytes(byte[] src, int srcIndex, int length) {
        buf.writeBytes(src, srcIndex, length);
    }

    @Override
    public void writeBytes(ByteBuffer src) {
        buf.writeBytes(src);
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
    public void writeZero(int length) {
        buf.writeZero(length);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return buf.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, ByteBufIndexFinder indexFinder) {
        return buf.indexOf(fromIndex, toIndex, indexFinder);
    }

    @Override
    public int bytesBefore(byte value) {
        return buf.bytesBefore(value);
    }

    @Override
    public int bytesBefore(ByteBufIndexFinder indexFinder) {
        return buf.bytesBefore(indexFinder);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return buf.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int length, ByteBufIndexFinder indexFinder) {
        return buf.bytesBefore(length, indexFinder);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return buf.bytesBefore(index, length, value);
    }

    @Override
    public int bytesBefore(int index, int length, ByteBufIndexFinder indexFinder) {
        return buf.bytesBefore(index, length, indexFinder);
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
    public ByteBuf slice(int index, int length) {
        return buf.slice(index, length).order(order);
    }

    @Override
    public ByteBuf duplicate() {
        return buf.duplicate().order(order);
    }

    @Override
    public boolean hasNioBuffer() {
        return buf.hasNioBuffer();
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
    public String toString(Charset charset) {
        return buf.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return buf.toString(index, length, charset);
    }

    @Override
    public int hashCode() {
        return buf.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
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
        return "Swapped(" + buf.toString() + ')';
    }
}
