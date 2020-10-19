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
import io.netty.util.ResourceLeakTracker;

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

import static io.netty.buffer.AdvancedLeakAwareByteBuf.recordLeakNonRefCountingOperation;

final class AdvancedLeakAwareCompositeByteBuf extends SimpleLeakAwareCompositeByteBuf {

    AdvancedLeakAwareCompositeByteBuf(CompositeByteBuf wrapped, ResourceLeakTracker<ByteBuf> leak) {
        super(wrapped, leak);
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        recordLeakNonRefCountingOperation(leak);
        return super.order(endianness);
    }

    @Override
    public ByteBuf slice() {
        recordLeakNonRefCountingOperation(leak);
        return super.slice();
    }

    @Override
    public ByteBuf retainedSlice() {
        recordLeakNonRefCountingOperation(leak);
        return super.retainedSlice();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.slice(index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.retainedSlice(index, length);
    }

    @Override
    public ByteBuf duplicate() {
        recordLeakNonRefCountingOperation(leak);
        return super.duplicate();
    }

    @Override
    public ByteBuf retainedDuplicate() {
        recordLeakNonRefCountingOperation(leak);
        return super.retainedDuplicate();
    }

    @Override
    public ByteBuf readSlice(int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.readSlice(length);
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.readRetainedSlice(length);
    }

    @Override
    public ByteBuf asReadOnly() {
        recordLeakNonRefCountingOperation(leak);
        return super.asReadOnly();
    }

    @Override
    public boolean isReadOnly() {
        recordLeakNonRefCountingOperation(leak);
        return super.isReadOnly();
    }

    @Override
    public CompositeByteBuf discardReadBytes() {
        recordLeakNonRefCountingOperation(leak);
        return super.discardReadBytes();
    }

    @Override
    public CompositeByteBuf discardSomeReadBytes() {
        recordLeakNonRefCountingOperation(leak);
        return super.discardSomeReadBytes();
    }

    @Override
    public CompositeByteBuf ensureWritable(int minWritableBytes) {
        recordLeakNonRefCountingOperation(leak);
        return super.ensureWritable(minWritableBytes);
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        recordLeakNonRefCountingOperation(leak);
        return super.ensureWritable(minWritableBytes, force);
    }

    @Override
    public boolean getBoolean(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getShort(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getUnsignedShort(index);
    }

    @Override
    public int getMedium(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getMedium(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getUnsignedMedium(index);
    }

    @Override
    public int getInt(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getInt(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getUnsignedInt(index);
    }

    @Override
    public long getLong(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getLong(index);
    }

    @Override
    public char getChar(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getDouble(index);
    }

    @Override
    public CompositeByteBuf getBytes(int index, ByteBuf dst) {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, dst);
    }

    @Override
    public CompositeByteBuf getBytes(int index, ByteBuf dst, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, dst, length);
    }

    @Override
    public CompositeByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public CompositeByteBuf getBytes(int index, byte[] dst) {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, dst);
    }

    @Override
    public CompositeByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public CompositeByteBuf getBytes(int index, ByteBuffer dst) {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, dst);
    }

    @Override
    public CompositeByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, out, length);
    }

    @Override
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        recordLeakNonRefCountingOperation(leak);
        return super.getCharSequence(index, length, charset);
    }

    @Override
    public CompositeByteBuf setBoolean(int index, boolean value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setBoolean(index, value);
    }

    @Override
    public CompositeByteBuf setByte(int index, int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setByte(index, value);
    }

    @Override
    public CompositeByteBuf setShort(int index, int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setShort(index, value);
    }

    @Override
    public CompositeByteBuf setMedium(int index, int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setMedium(index, value);
    }

    @Override
    public CompositeByteBuf setInt(int index, int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setInt(index, value);
    }

    @Override
    public CompositeByteBuf setLong(int index, long value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setLong(index, value);
    }

    @Override
    public CompositeByteBuf setChar(int index, int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setChar(index, value);
    }

    @Override
    public CompositeByteBuf setFloat(int index, float value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setFloat(index, value);
    }

    @Override
    public CompositeByteBuf setDouble(int index, double value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setDouble(index, value);
    }

    @Override
    public CompositeByteBuf setBytes(int index, ByteBuf src) {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, src);
    }

    @Override
    public CompositeByteBuf setBytes(int index, ByteBuf src, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, src, length);
    }

    @Override
    public CompositeByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, src, srcIndex, length);
    }

    @Override
    public CompositeByteBuf setBytes(int index, byte[] src) {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, src);
    }

    @Override
    public CompositeByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, src, srcIndex, length);
    }

    @Override
    public CompositeByteBuf setBytes(int index, ByteBuffer src) {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, src);
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, in, length);
    }

    @Override
    public CompositeByteBuf setZero(int index, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.setZero(index, length);
    }

    @Override
    public boolean readBoolean() {
        recordLeakNonRefCountingOperation(leak);
        return super.readBoolean();
    }

    @Override
    public byte readByte() {
        recordLeakNonRefCountingOperation(leak);
        return super.readByte();
    }

    @Override
    public short readUnsignedByte() {
        recordLeakNonRefCountingOperation(leak);
        return super.readUnsignedByte();
    }

    @Override
    public short readShort() {
        recordLeakNonRefCountingOperation(leak);
        return super.readShort();
    }

    @Override
    public int readUnsignedShort() {
        recordLeakNonRefCountingOperation(leak);
        return super.readUnsignedShort();
    }

    @Override
    public int readMedium() {
        recordLeakNonRefCountingOperation(leak);
        return super.readMedium();
    }

    @Override
    public int readUnsignedMedium() {
        recordLeakNonRefCountingOperation(leak);
        return super.readUnsignedMedium();
    }

    @Override
    public int readInt() {
        recordLeakNonRefCountingOperation(leak);
        return super.readInt();
    }

    @Override
    public long readUnsignedInt() {
        recordLeakNonRefCountingOperation(leak);
        return super.readUnsignedInt();
    }

    @Override
    public long readLong() {
        recordLeakNonRefCountingOperation(leak);
        return super.readLong();
    }

    @Override
    public char readChar() {
        recordLeakNonRefCountingOperation(leak);
        return super.readChar();
    }

    @Override
    public float readFloat() {
        recordLeakNonRefCountingOperation(leak);
        return super.readFloat();
    }

    @Override
    public double readDouble() {
        recordLeakNonRefCountingOperation(leak);
        return super.readDouble();
    }

    @Override
    public ByteBuf readBytes(int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(length);
    }

    @Override
    public CompositeByteBuf readBytes(ByteBuf dst) {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(dst);
    }

    @Override
    public CompositeByteBuf readBytes(ByteBuf dst, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(dst, length);
    }

    @Override
    public CompositeByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(dst, dstIndex, length);
    }

    @Override
    public CompositeByteBuf readBytes(byte[] dst) {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(dst);
    }

    @Override
    public CompositeByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(dst, dstIndex, length);
    }

    @Override
    public CompositeByteBuf readBytes(ByteBuffer dst) {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(dst);
    }

    @Override
    public CompositeByteBuf readBytes(OutputStream out, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(out, length);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(out, length);
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        recordLeakNonRefCountingOperation(leak);
        return super.readCharSequence(length, charset);
    }

    @Override
    public CompositeByteBuf skipBytes(int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.skipBytes(length);
    }

    @Override
    public CompositeByteBuf writeBoolean(boolean value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBoolean(value);
    }

    @Override
    public CompositeByteBuf writeByte(int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeByte(value);
    }

    @Override
    public CompositeByteBuf writeShort(int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeShort(value);
    }

    @Override
    public CompositeByteBuf writeMedium(int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeMedium(value);
    }

    @Override
    public CompositeByteBuf writeInt(int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeInt(value);
    }

    @Override
    public CompositeByteBuf writeLong(long value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeLong(value);
    }

    @Override
    public CompositeByteBuf writeChar(int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeChar(value);
    }

    @Override
    public CompositeByteBuf writeFloat(float value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeFloat(value);
    }

    @Override
    public CompositeByteBuf writeDouble(double value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeDouble(value);
    }

    @Override
    public CompositeByteBuf writeBytes(ByteBuf src) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(src);
    }

    @Override
    public CompositeByteBuf writeBytes(ByteBuf src, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(src, length);
    }

    @Override
    public CompositeByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(src, srcIndex, length);
    }

    @Override
    public CompositeByteBuf writeBytes(byte[] src) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(src);
    }

    @Override
    public CompositeByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(src, srcIndex, length);
    }

    @Override
    public CompositeByteBuf writeBytes(ByteBuffer src) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(src);
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(in, length);
    }

    @Override
    public CompositeByteBuf writeZero(int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeZero(length);
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeCharSequence(sequence, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        recordLeakNonRefCountingOperation(leak);
        return super.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        recordLeakNonRefCountingOperation(leak);
        return super.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        recordLeakNonRefCountingOperation(leak);
        return super.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        recordLeakNonRefCountingOperation(leak);
        return super.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        recordLeakNonRefCountingOperation(leak);
        return super.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        recordLeakNonRefCountingOperation(leak);
        return super.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        recordLeakNonRefCountingOperation(leak);
        return super.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        recordLeakNonRefCountingOperation(leak);
        return super.forEachByteDesc(index, length, processor);
    }

    @Override
    public ByteBuf copy() {
        recordLeakNonRefCountingOperation(leak);
        return super.copy();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.copy(index, length);
    }

    @Override
    public int nioBufferCount() {
        recordLeakNonRefCountingOperation(leak);
        return super.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        recordLeakNonRefCountingOperation(leak);
        return super.nioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        recordLeakNonRefCountingOperation(leak);
        return super.nioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.nioBuffers(index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.internalNioBuffer(index, length);
    }

    @Override
    public String toString(Charset charset) {
        recordLeakNonRefCountingOperation(leak);
        return super.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        recordLeakNonRefCountingOperation(leak);
        return super.toString(index, length, charset);
    }

    @Override
    public CompositeByteBuf capacity(int newCapacity) {
        recordLeakNonRefCountingOperation(leak);
        return super.capacity(newCapacity);
    }

    @Override
    public short getShortLE(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getShortLE(index);
    }

    @Override
    public int getUnsignedShortLE(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getUnsignedShortLE(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getUnsignedMediumLE(index);
    }

    @Override
    public int getMediumLE(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getMediumLE(index);
    }

    @Override
    public int getIntLE(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getIntLE(index);
    }

    @Override
    public long getUnsignedIntLE(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getUnsignedIntLE(index);
    }

    @Override
    public long getLongLE(int index) {
        recordLeakNonRefCountingOperation(leak);
        return super.getLongLE(index);
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setShortLE(index, value);
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setMediumLE(index, value);
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setIntLE(index, value);
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        recordLeakNonRefCountingOperation(leak);
        return super.setLongLE(index, value);
    }

    @Override
    public int setCharSequence(int index, CharSequence sequence, Charset charset) {
        recordLeakNonRefCountingOperation(leak);
        return super.setCharSequence(index, sequence, charset);
    }

    @Override
    public short readShortLE() {
        recordLeakNonRefCountingOperation(leak);
        return super.readShortLE();
    }

    @Override
    public int readUnsignedShortLE() {
        recordLeakNonRefCountingOperation(leak);
        return super.readUnsignedShortLE();
    }

    @Override
    public int readMediumLE() {
        recordLeakNonRefCountingOperation(leak);
        return super.readMediumLE();
    }

    @Override
    public int readUnsignedMediumLE() {
        recordLeakNonRefCountingOperation(leak);
        return super.readUnsignedMediumLE();
    }

    @Override
    public int readIntLE() {
        recordLeakNonRefCountingOperation(leak);
        return super.readIntLE();
    }

    @Override
    public long readUnsignedIntLE() {
        recordLeakNonRefCountingOperation(leak);
        return super.readUnsignedIntLE();
    }

    @Override
    public long readLongLE() {
        recordLeakNonRefCountingOperation(leak);
        return super.readLongLE();
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeShortLE(value);
    }

    @Override
    public ByteBuf writeMediumLE(int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeMediumLE(value);
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeIntLE(value);
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        recordLeakNonRefCountingOperation(leak);
        return super.writeLongLE(value);
    }

    @Override
    public CompositeByteBuf addComponent(ByteBuf buffer) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponent(buffer);
    }

    @Override
    public CompositeByteBuf addComponents(ByteBuf... buffers) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponents(buffers);
    }

    @Override
    public CompositeByteBuf addComponents(Iterable<ByteBuf> buffers) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponents(buffers);
    }

    @Override
    public CompositeByteBuf addComponent(int cIndex, ByteBuf buffer) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponent(cIndex, buffer);
    }

    @Override
    public CompositeByteBuf addComponents(int cIndex, ByteBuf... buffers) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponents(cIndex, buffers);
    }

    @Override
    public CompositeByteBuf addComponents(int cIndex, Iterable<ByteBuf> buffers) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponents(cIndex, buffers);
    }

    @Override
    public CompositeByteBuf addComponent(boolean increaseWriterIndex, ByteBuf buffer) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponent(increaseWriterIndex, buffer);
    }

    @Override
    public CompositeByteBuf addComponents(boolean increaseWriterIndex, ByteBuf... buffers) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponents(increaseWriterIndex, buffers);
    }

    @Override
    public CompositeByteBuf addComponents(boolean increaseWriterIndex, Iterable<ByteBuf> buffers) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponents(increaseWriterIndex, buffers);
    }

    @Override
    public CompositeByteBuf addComponent(boolean increaseWriterIndex, int cIndex, ByteBuf buffer) {
        recordLeakNonRefCountingOperation(leak);
        return super.addComponent(increaseWriterIndex, cIndex, buffer);
    }

    @Override
    public CompositeByteBuf addFlattenedComponents(boolean increaseWriterIndex, ByteBuf buffer) {
        recordLeakNonRefCountingOperation(leak);
        return super.addFlattenedComponents(increaseWriterIndex, buffer);
    }

    @Override
    public CompositeByteBuf removeComponent(int cIndex) {
        recordLeakNonRefCountingOperation(leak);
        return super.removeComponent(cIndex);
    }

    @Override
    public CompositeByteBuf removeComponents(int cIndex, int numComponents) {
        recordLeakNonRefCountingOperation(leak);
        return super.removeComponents(cIndex, numComponents);
    }

    @Override
    public Iterator<ByteBuf> iterator() {
        recordLeakNonRefCountingOperation(leak);
        return super.iterator();
    }

    @Override
    public List<ByteBuf> decompose(int offset, int length) {
        recordLeakNonRefCountingOperation(leak);
        return super.decompose(offset, length);
    }

    @Override
    public CompositeByteBuf consolidate() {
        recordLeakNonRefCountingOperation(leak);
        return super.consolidate();
    }

    @Override
    public CompositeByteBuf discardReadComponents() {
        recordLeakNonRefCountingOperation(leak);
        return super.discardReadComponents();
    }

    @Override
    public CompositeByteBuf consolidate(int cIndex, int numComponents) {
        recordLeakNonRefCountingOperation(leak);
        return super.consolidate(cIndex, numComponents);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.getBytes(index, out, position, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.setBytes(index, in, position, length);
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.readBytes(out, position, length);
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        recordLeakNonRefCountingOperation(leak);
        return super.writeBytes(in, position, length);
    }

    @Override
    public CompositeByteBuf retain() {
        leak.record();
        return super.retain();
    }

    @Override
    public CompositeByteBuf retain(int increment) {
        leak.record();
        return super.retain(increment);
    }

    @Override
    public boolean release() {
        leak.record();
        return super.release();
    }

    @Override
    public boolean release(int decrement) {
        leak.record();
        return super.release(decrement);
    }

    @Override
    public CompositeByteBuf touch() {
        leak.record();
        return this;
    }

    @Override
    public CompositeByteBuf touch(Object hint) {
        leak.record(hint);
        return this;
    }

    @Override
    protected AdvancedLeakAwareByteBuf newLeakAwareByteBuf(
            ByteBuf wrapped, ByteBuf trackedByteBuf, ResourceLeakTracker<ByteBuf> leakTracker) {
        return new AdvancedLeakAwareByteBuf(wrapped, trackedByteBuf, leakTracker);
    }
}
