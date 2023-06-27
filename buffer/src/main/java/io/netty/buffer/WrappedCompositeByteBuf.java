/*
 * Copyright 2016 The Netty Project
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

class WrappedCompositeByteBuf extends CompositeByteBuf {

    private final CompositeByteBuf wrapped;

    WrappedCompositeByteBuf(CompositeByteBuf wrapped) {
        super(wrapped.alloc());
        this.wrapped = wrapped;
    }

    @Override
    public boolean release() {
        return wrapped.release();
    }

    @Override
    public boolean release(int decrement) {
        return wrapped.release(decrement);
    }

    @Override
    public final int maxCapacity() {
        return wrapped.maxCapacity();
    }

    @Override
    public final int readerIndex() {
        return wrapped.readerIndex();
    }

    @Override
    public final int writerIndex() {
        return wrapped.writerIndex();
    }

    @Override
    public final boolean isReadable() {
        return wrapped.isReadable();
    }

    @Override
    public final boolean isReadable(int numBytes) {
        return wrapped.isReadable(numBytes);
    }

    @Override
    public final boolean isWritable() {
        return wrapped.isWritable();
    }

    @Override
    public final boolean isWritable(int numBytes) {
        return wrapped.isWritable(numBytes);
    }

    @Override
    public final int readableBytes() {
        return wrapped.readableBytes();
    }

    @Override
    public final int writableBytes() {
        return wrapped.writableBytes();
    }

    @Override
    public final int maxWritableBytes() {
        return wrapped.maxWritableBytes();
    }

    @Override
    public int maxFastWritableBytes() {
        return wrapped.maxFastWritableBytes();
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        return wrapped.ensureWritable(minWritableBytes, force);
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        return wrapped.order(endianness);
    }

    @Override
    public boolean getBoolean(int index) {
        return wrapped.getBoolean(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        return wrapped.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        return wrapped.getShort(index);
    }

    @Override
    public short getShortLE(int index) {
        return wrapped.getShortLE(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        return wrapped.getUnsignedShort(index);
    }

    @Override
    public int getUnsignedShortLE(int index) {
        return wrapped.getUnsignedShortLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return wrapped.getUnsignedMedium(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return wrapped.getUnsignedMediumLE(index);
    }

    @Override
    public int getMedium(int index) {
        return wrapped.getMedium(index);
    }

    @Override
    public int getMediumLE(int index) {
        return wrapped.getMediumLE(index);
    }

    @Override
    public int getInt(int index) {
        return wrapped.getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        return wrapped.getIntLE(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return wrapped.getUnsignedInt(index);
    }

    @Override
    public long getUnsignedIntLE(int index) {
        return wrapped.getUnsignedIntLE(index);
    }

    @Override
    public long getLong(int index) {
        return wrapped.getLong(index);
    }

    @Override
    public long getLongLE(int index) {
        return wrapped.getLongLE(index);
    }

    @Override
    public char getChar(int index) {
        return wrapped.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        return wrapped.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        return wrapped.getDouble(index);
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        return wrapped.setShortLE(index, value);
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        return wrapped.setMediumLE(index, value);
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        return wrapped.setIntLE(index, value);
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        return wrapped.setLongLE(index, value);
    }

    @Override
    public byte readByte() {
        return wrapped.readByte();
    }

    @Override
    public boolean readBoolean() {
        return wrapped.readBoolean();
    }

    @Override
    public short readUnsignedByte() {
        return wrapped.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return wrapped.readShort();
    }

    @Override
    public short readShortLE() {
        return wrapped.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        return wrapped.readUnsignedShort();
    }

    @Override
    public int readUnsignedShortLE() {
        return wrapped.readUnsignedShortLE();
    }

    @Override
    public int readMedium() {
        return wrapped.readMedium();
    }

    @Override
    public int readMediumLE() {
        return wrapped.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        return wrapped.readUnsignedMedium();
    }

    @Override
    public int readUnsignedMediumLE() {
        return wrapped.readUnsignedMediumLE();
    }

    @Override
    public int readInt() {
        return wrapped.readInt();
    }

    @Override
    public int readIntLE() {
        return wrapped.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        return wrapped.readUnsignedInt();
    }

    @Override
    public long readUnsignedIntLE() {
        return wrapped.readUnsignedIntLE();
    }

    @Override
    public long readLong() {
        return wrapped.readLong();
    }

    @Override
    public long readLongLE() {
        return wrapped.readLongLE();
    }

    @Override
    public char readChar() {
        return wrapped.readChar();
    }

    @Override
    public float readFloat() {
        return wrapped.readFloat();
    }

    @Override
    public double readDouble() {
        return wrapped.readDouble();
    }

    @Override
    public ByteBuf readBytes(int length) {
        return wrapped.readBytes(length);
    }

    @Override
    public ByteBuf slice() {
        return wrapped.slice();
    }

    @Override
    public ByteBuf retainedSlice() {
        return wrapped.retainedSlice();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return wrapped.slice(index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return wrapped.retainedSlice(index, length);
    }

    @Override
    public ByteBuffer nioBuffer() {
        return wrapped.nioBuffer();
    }

    @Override
    public String toString(Charset charset) {
        return wrapped.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return wrapped.toString(index, length, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return wrapped.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return wrapped.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return wrapped.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return wrapped.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return wrapped.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return wrapped.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return wrapped.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return wrapped.forEachByteDesc(index, length, processor);
    }

    @Override
    protected int forEachByteAsc0(int start, int end, ByteProcessor processor) throws Exception {
        return wrapped.forEachByteAsc0(start, end, processor);
    }

    @Override
    protected int forEachByteDesc0(int rStart, int rEnd, ByteProcessor processor) throws Exception {
        return wrapped.forEachByteDesc0(rStart, rEnd, processor);
    }

    @Override
    public final int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        return wrapped.equals(o);
    }

    @Override
    public final int compareTo(ByteBuf that) {
        return wrapped.compareTo(that);
    }

    @Override
    public final int refCnt() {
        return wrapped.refCnt();
    }

    @Override
    final boolean isAccessible() {
        return wrapped.isAccessible();
    }

    @Override
    public ByteBuf duplicate() {
        return wrapped.duplicate();
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return wrapped.retainedDuplicate();
    }

    @Override
    public ByteBuf readSlice(int length) {
        return wrapped.readSlice(length);
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        return wrapped.readRetainedSlice(length);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return wrapped.readBytes(out, length);
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        return wrapped.writeShortLE(value);
    }

    @Override
    public ByteBuf writeMediumLE(int value) {
        return wrapped.writeMediumLE(value);
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        return wrapped.writeIntLE(value);
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        return wrapped.writeLongLE(value);
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        return wrapped.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        return wrapped.writeBytes(in, length);
    }

    @Override
    public ByteBuf copy() {
        return wrapped.copy();
    }

    @Override
    public CompositeByteBuf addComponent(ByteBuf buffer) {
        wrapped.addComponent(buffer);
        return this;
    }

    @Override
    public CompositeByteBuf addComponents(ByteBuf... buffers) {
        wrapped.addComponents(buffers);
        return this;
    }

    @Override
    public CompositeByteBuf addComponents(Iterable<ByteBuf> buffers) {
        wrapped.addComponents(buffers);
        return this;
    }

    @Override
    public CompositeByteBuf addComponent(int cIndex, ByteBuf buffer) {
        wrapped.addComponent(cIndex, buffer);
        return this;
    }

    @Override
    public CompositeByteBuf addComponents(int cIndex, ByteBuf... buffers) {
        wrapped.addComponents(cIndex, buffers);
        return this;
    }

    @Override
    public CompositeByteBuf addComponents(int cIndex, Iterable<ByteBuf> buffers) {
        wrapped.addComponents(cIndex, buffers);
        return this;
    }

    @Override
    public CompositeByteBuf addComponent(boolean increaseWriterIndex, ByteBuf buffer) {
        wrapped.addComponent(increaseWriterIndex, buffer);
        return this;
    }

    @Override
    public CompositeByteBuf addComponents(boolean increaseWriterIndex, ByteBuf... buffers) {
        wrapped.addComponents(increaseWriterIndex, buffers);
        return this;
    }

    @Override
    public CompositeByteBuf addComponents(boolean increaseWriterIndex, Iterable<ByteBuf> buffers) {
        wrapped.addComponents(increaseWriterIndex, buffers);
        return this;
    }

    @Override
    public CompositeByteBuf addComponent(boolean increaseWriterIndex, int cIndex, ByteBuf buffer) {
        wrapped.addComponent(increaseWriterIndex, cIndex, buffer);
        return this;
    }

    @Override
    public CompositeByteBuf addFlattenedComponents(boolean increaseWriterIndex, ByteBuf buffer) {
        wrapped.addFlattenedComponents(increaseWriterIndex, buffer);
        return this;
    }

    @Override
    public CompositeByteBuf removeComponent(int cIndex) {
        wrapped.removeComponent(cIndex);
        return this;
    }

    @Override
    public CompositeByteBuf removeComponents(int cIndex, int numComponents) {
        wrapped.removeComponents(cIndex, numComponents);
        return this;
    }

    @Override
    public Iterator<ByteBuf> iterator() {
        return wrapped.iterator();
    }

    @Override
    public List<ByteBuf> decompose(int offset, int length) {
        return wrapped.decompose(offset, length);
    }

    @Override
    public final boolean isDirect() {
        return wrapped.isDirect();
    }

    @Override
    public final boolean hasArray() {
        return wrapped.hasArray();
    }

    @Override
    public final byte[] array() {
        return wrapped.array();
    }

    @Override
    public final int arrayOffset() {
        return wrapped.arrayOffset();
    }

    @Override
    public final boolean hasMemoryAddress() {
        return wrapped.hasMemoryAddress();
    }

    @Override
    public final long memoryAddress() {
        return wrapped.memoryAddress();
    }

    @Override
    public final int capacity() {
        return wrapped.capacity();
    }

    @Override
    public CompositeByteBuf capacity(int newCapacity) {
        wrapped.capacity(newCapacity);
        return this;
    }

    @Override
    public final ByteBufAllocator alloc() {
        return wrapped.alloc();
    }

    @Override
    public final ByteOrder order() {
        return wrapped.order();
    }

    @Override
    public final int numComponents() {
        return wrapped.numComponents();
    }

    @Override
    public final int maxNumComponents() {
        return wrapped.maxNumComponents();
    }

    @Override
    public final int toComponentIndex(int offset) {
        return wrapped.toComponentIndex(offset);
    }

    @Override
    public final int toByteIndex(int cIndex) {
        return wrapped.toByteIndex(cIndex);
    }

    @Override
    public byte getByte(int index) {
        return wrapped.getByte(index);
    }

    @Override
    protected final byte _getByte(int index) {
        return wrapped._getByte(index);
    }

    @Override
    protected final short _getShort(int index) {
        return wrapped._getShort(index);
    }

    @Override
    protected final short _getShortLE(int index) {
        return wrapped._getShortLE(index);
    }

    @Override
    protected final int _getUnsignedMedium(int index) {
        return wrapped._getUnsignedMedium(index);
    }

    @Override
    protected final int _getUnsignedMediumLE(int index) {
        return wrapped._getUnsignedMediumLE(index);
    }

    @Override
    protected final int _getInt(int index) {
        return wrapped._getInt(index);
    }

    @Override
    protected final int _getIntLE(int index) {
        return wrapped._getIntLE(index);
    }

    @Override
    protected final long _getLong(int index) {
        return wrapped._getLong(index);
    }

    @Override
    protected final long _getLongLE(int index) {
        return wrapped._getLongLE(index);
    }

    @Override
    public CompositeByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        wrapped.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public CompositeByteBuf getBytes(int index, ByteBuffer dst) {
        wrapped.getBytes(index, dst);
        return this;
    }

    @Override
    public CompositeByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        wrapped.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return wrapped.getBytes(index, out, length);
    }

    @Override
    public CompositeByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        wrapped.getBytes(index, out, length);
        return this;
    }

    @Override
    public CompositeByteBuf setByte(int index, int value) {
        wrapped.setByte(index, value);
        return this;
    }

    @Override
    protected final void _setByte(int index, int value) {
        wrapped._setByte(index, value);
    }

    @Override
    public CompositeByteBuf setShort(int index, int value) {
        wrapped.setShort(index, value);
        return this;
    }

    @Override
    protected final void _setShort(int index, int value) {
        wrapped._setShort(index, value);
    }

    @Override
    protected final void _setShortLE(int index, int value) {
        wrapped._setShortLE(index, value);
    }

    @Override
    public CompositeByteBuf setMedium(int index, int value) {
        wrapped.setMedium(index, value);
        return this;
    }

    @Override
    protected final void _setMedium(int index, int value) {
        wrapped._setMedium(index, value);
    }

    @Override
    protected final void _setMediumLE(int index, int value) {
        wrapped._setMediumLE(index, value);
    }

    @Override
    public CompositeByteBuf setInt(int index, int value) {
        wrapped.setInt(index, value);
        return this;
    }

    @Override
    protected final void _setInt(int index, int value) {
        wrapped._setInt(index, value);
    }

    @Override
    protected final void _setIntLE(int index, int value) {
        wrapped._setIntLE(index, value);
    }

    @Override
    public CompositeByteBuf setLong(int index, long value) {
        wrapped.setLong(index, value);
        return this;
    }

    @Override
    protected final void _setLong(int index, long value) {
        wrapped._setLong(index, value);
    }

    @Override
    protected final void _setLongLE(int index, long value) {
        wrapped._setLongLE(index, value);
    }

    @Override
    public CompositeByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        wrapped.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public CompositeByteBuf setBytes(int index, ByteBuffer src) {
        wrapped.setBytes(index, src);
        return this;
    }

    @Override
    public CompositeByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        wrapped.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return wrapped.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return wrapped.setBytes(index, in, length);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return wrapped.copy(index, length);
    }

    @Override
    public final ByteBuf component(int cIndex) {
        return wrapped.component(cIndex);
    }

    @Override
    public final ByteBuf componentAtOffset(int offset) {
        return wrapped.componentAtOffset(offset);
    }

    @Override
    public final ByteBuf internalComponent(int cIndex) {
        return wrapped.internalComponent(cIndex);
    }

    @Override
    public final ByteBuf internalComponentAtOffset(int offset) {
        return wrapped.internalComponentAtOffset(offset);
    }

    @Override
    public int nioBufferCount() {
        return wrapped.nioBufferCount();
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return wrapped.internalNioBuffer(index, length);
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return wrapped.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return wrapped.nioBuffers(index, length);
    }

    @Override
    public CompositeByteBuf consolidate() {
        wrapped.consolidate();
        return this;
    }

    @Override
    public CompositeByteBuf consolidate(int cIndex, int numComponents) {
        wrapped.consolidate(cIndex, numComponents);
        return this;
    }

    @Override
    public CompositeByteBuf discardReadComponents() {
        wrapped.discardReadComponents();
        return this;
    }

    @Override
    public CompositeByteBuf discardReadBytes() {
        wrapped.discardReadBytes();
        return this;
    }

    @Override
    public final String toString() {
        return wrapped.toString();
    }

    @Override
    public final CompositeByteBuf readerIndex(int readerIndex) {
        wrapped.readerIndex(readerIndex);
        return this;
    }

    @Override
    public final CompositeByteBuf writerIndex(int writerIndex) {
        wrapped.writerIndex(writerIndex);
        return this;
    }

    @Override
    public final CompositeByteBuf setIndex(int readerIndex, int writerIndex) {
        wrapped.setIndex(readerIndex, writerIndex);
        return this;
    }

    @Override
    public final CompositeByteBuf clear() {
        wrapped.clear();
        return this;
    }

    @Override
    public final CompositeByteBuf markReaderIndex() {
        wrapped.markReaderIndex();
        return this;
    }

    @Override
    public final CompositeByteBuf resetReaderIndex() {
        wrapped.resetReaderIndex();
        return this;
    }

    @Override
    public final CompositeByteBuf markWriterIndex() {
        wrapped.markWriterIndex();
        return this;
    }

    @Override
    public final CompositeByteBuf resetWriterIndex() {
        wrapped.resetWriterIndex();
        return this;
    }

    @Override
    public CompositeByteBuf ensureWritable(int minWritableBytes) {
        wrapped.ensureWritable(minWritableBytes);
        return this;
    }

    @Override
    public CompositeByteBuf getBytes(int index, ByteBuf dst) {
        wrapped.getBytes(index, dst);
        return this;
    }

    @Override
    public CompositeByteBuf getBytes(int index, ByteBuf dst, int length) {
        wrapped.getBytes(index, dst, length);
        return this;
    }

    @Override
    public CompositeByteBuf getBytes(int index, byte[] dst) {
        wrapped.getBytes(index, dst);
        return this;
    }

    @Override
    public CompositeByteBuf setBoolean(int index, boolean value) {
        wrapped.setBoolean(index, value);
        return this;
    }

    @Override
    public CompositeByteBuf setChar(int index, int value) {
        wrapped.setChar(index, value);
        return this;
    }

    @Override
    public CompositeByteBuf setFloat(int index, float value) {
        wrapped.setFloat(index, value);
        return this;
    }

    @Override
    public CompositeByteBuf setDouble(int index, double value) {
        wrapped.setDouble(index, value);
        return this;
    }

    @Override
    public CompositeByteBuf setBytes(int index, ByteBuf src) {
        wrapped.setBytes(index, src);
        return this;
    }

    @Override
    public CompositeByteBuf setBytes(int index, ByteBuf src, int length) {
        wrapped.setBytes(index, src, length);
        return this;
    }

    @Override
    public CompositeByteBuf setBytes(int index, byte[] src) {
        wrapped.setBytes(index, src);
        return this;
    }

    @Override
    public CompositeByteBuf setZero(int index, int length) {
        wrapped.setZero(index, length);
        return this;
    }

    @Override
    public CompositeByteBuf readBytes(ByteBuf dst) {
        wrapped.readBytes(dst);
        return this;
    }

    @Override
    public CompositeByteBuf readBytes(ByteBuf dst, int length) {
        wrapped.readBytes(dst, length);
        return this;
    }

    @Override
    public CompositeByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        wrapped.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public CompositeByteBuf readBytes(byte[] dst) {
        wrapped.readBytes(dst);
        return this;
    }

    @Override
    public CompositeByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        wrapped.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public CompositeByteBuf readBytes(ByteBuffer dst) {
        wrapped.readBytes(dst);
        return this;
    }

    @Override
    public CompositeByteBuf readBytes(OutputStream out, int length) throws IOException {
        wrapped.readBytes(out, length);
        return this;
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return wrapped.getBytes(index, out, position, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        return wrapped.setBytes(index, in, position, length);
    }

    @Override
    public boolean isReadOnly() {
        return wrapped.isReadOnly();
    }

    @Override
    public ByteBuf asReadOnly() {
        return wrapped.asReadOnly();
    }

    @Override
    protected SwappedByteBuf newSwappedByteBuf() {
        return wrapped.newSwappedByteBuf();
    }

    @Override
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        return wrapped.getCharSequence(index, length, charset);
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return wrapped.readCharSequence(length, charset);
    }

    @Override
    public int setCharSequence(int index, CharSequence sequence, Charset charset) {
        return wrapped.setCharSequence(index, sequence, charset);
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        return wrapped.readBytes(out, position, length);
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        return wrapped.writeBytes(in, position, length);
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        return wrapped.writeCharSequence(sequence, charset);
    }

    @Override
    public CompositeByteBuf skipBytes(int length) {
        wrapped.skipBytes(length);
        return this;
    }

    @Override
    public CompositeByteBuf writeBoolean(boolean value) {
        wrapped.writeBoolean(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeByte(int value) {
        wrapped.writeByte(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeShort(int value) {
        wrapped.writeShort(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeMedium(int value) {
        wrapped.writeMedium(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeInt(int value) {
        wrapped.writeInt(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeLong(long value) {
        wrapped.writeLong(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeChar(int value) {
        wrapped.writeChar(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeFloat(float value) {
        wrapped.writeFloat(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeDouble(double value) {
        wrapped.writeDouble(value);
        return this;
    }

    @Override
    public CompositeByteBuf writeBytes(ByteBuf src) {
        wrapped.writeBytes(src);
        return this;
    }

    @Override
    public CompositeByteBuf writeBytes(ByteBuf src, int length) {
        wrapped.writeBytes(src, length);
        return this;
    }

    @Override
    public CompositeByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        wrapped.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public CompositeByteBuf writeBytes(byte[] src) {
        wrapped.writeBytes(src);
        return this;
    }

    @Override
    public CompositeByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        wrapped.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public CompositeByteBuf writeBytes(ByteBuffer src) {
        wrapped.writeBytes(src);
        return this;
    }

    @Override
    public CompositeByteBuf writeZero(int length) {
        wrapped.writeZero(length);
        return this;
    }

    @Override
    public CompositeByteBuf retain(int increment) {
        wrapped.retain(increment);
        return this;
    }

    @Override
    public CompositeByteBuf retain() {
        wrapped.retain();
        return this;
    }

    @Override
    public CompositeByteBuf touch() {
        wrapped.touch();
        return this;
    }

    @Override
    public CompositeByteBuf touch(Object hint) {
        wrapped.touch(hint);
        return this;
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return wrapped.nioBuffers();
    }

    @Override
    public CompositeByteBuf discardSomeReadBytes() {
        wrapped.discardSomeReadBytes();
        return this;
    }

    @Override
    public final void deallocate() {
        wrapped.deallocate();
    }

    @Override
    public final ByteBuf unwrap() {
        return wrapped;
    }
}
