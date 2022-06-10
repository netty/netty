/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api;

import io.netty5.buffer.api.ComponentIterator.Next;
import io.netty5.util.Send;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

/**
 * A stub of a {@link Buffer} implementation that implements all buffer methods by delegating them to a wrapped buffer
 * instance.
 * <p>
 * This can be used when writing automated tests for code that integrates with {@link Buffer}, but should not be used in
 * production code.
 */
public class BufferStub implements Buffer {
    protected final Buffer delegate;

    /**
     * Create a new buffer stub that delegates all calls to the given instance.
     *
     * @param delegate The buffer instance to delegate all method calls to.
     */
    public BufferStub(Buffer delegate) {
        this.delegate = delegate;
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }

    @Override
    public int readerOffset() {
        return delegate.readerOffset();
    }

    @Override
    public Buffer skipReadableBytes(int delta) {
        delegate.skipReadableBytes(delta);
        return this;
    }

    @Override
    public Buffer readerOffset(int offset) {
        delegate.readerOffset(offset);
        return this;
    }

    @Override
    public int writerOffset() {
        return delegate.writerOffset();
    }

    @Override
    public Buffer skipWritableBytes(int delta) {
        delegate.skipWritableBytes(delta);
        return this;
    }

    @Override
    public Buffer writerOffset(int offset) {
        delegate.writerOffset(offset);
        return this;
    }

    @Override
    public int readableBytes() {
        return delegate.readableBytes();
    }

    @Override
    public int writableBytes() {
        return delegate.writableBytes();
    }

    @Override
    public Buffer fill(byte value) {
        delegate.fill(value);
        return this;
    }

    @Override
    public Buffer makeReadOnly() {
        delegate.makeReadOnly();
        return this;
    }

    @Override
    public boolean readOnly() {
        return delegate.readOnly();
    }

    @Override
    public boolean isDirect() {
        return delegate.isDirect();
    }

    @Override
    public Buffer implicitCapacityLimit(int limit) {
        delegate.implicitCapacityLimit(limit);
        return this;
    }

    @Override
    public int implicitCapacityLimit() {
        return delegate.implicitCapacityLimit();
    }

    @Override
    public void copyInto(int srcPos, byte[] dest, int destPos, int length) {
        delegate.copyInto(srcPos, dest, destPos, length);
    }

    @Override
    public void copyInto(int srcPos, ByteBuffer dest, int destPos, int length) {
        delegate.copyInto(srcPos, dest, destPos, length);
    }

    @Override
    public void copyInto(int srcPos, Buffer dest, int destPos, int length) {
        delegate.copyInto(srcPos, dest, destPos, length);
    }

    @Override
    public int transferTo(WritableByteChannel channel, int length) throws IOException {
        return delegate.transferTo(channel, length);
    }

    @Override
    public int transferFrom(FileChannel channel, long position, int length) throws IOException {
        return delegate.transferFrom(channel, position, length);
    }

    @Override
    public int transferFrom(ReadableByteChannel channel, int length) throws IOException {
        return delegate.transferFrom(channel, length);
    }

    @Override
    public Buffer writeBytes(Buffer source) {
        delegate.writeBytes(source);
        return this;
    }

    @Override
    public Buffer writeBytes(byte[] source) {
        delegate.writeBytes(source);
        return this;
    }

    @Override
    public Buffer resetOffsets() {
        delegate.resetOffsets();
        return this;
    }

    @Override
    public int bytesBefore(byte needle) {
        return delegate.bytesBefore(needle);
    }

    @Override
    public int bytesBefore(Buffer needle) {
        return delegate.bytesBefore(needle);
    }

    @Override
    public ByteCursor openCursor() {
        return delegate.openCursor();
    }

    @Override
    public ByteCursor openCursor(int fromOffset, int length) {
        return delegate.openCursor(fromOffset, length);
    }

    @Override
    public ByteCursor openReverseCursor() {
        return delegate.openReverseCursor();
    }

    @Override
    public ByteCursor openReverseCursor(int fromOffset, int length) {
        return delegate.openReverseCursor(fromOffset, length);
    }

    @Override
    public Buffer ensureWritable(int size) {
        delegate.ensureWritable(size);
        return this;
    }

    @Override
    public Buffer ensureWritable(int size, int minimumGrowth, boolean allowCompaction) {
        delegate.ensureWritable(size, minimumGrowth, allowCompaction);
        return this;
    }

    @Override
    public Buffer copy() {
        return delegate.copy();
    }

    @Override
    public Buffer copy(int offset, int length, boolean readOnly) {
        return delegate.copy(offset, length, readOnly);
    }

    @Override
    public Buffer split() {
        return delegate.split();
    }

    @Override
    public Buffer split(int splitOffset) {
        return delegate.split(splitOffset);
    }

    @Override
    public Buffer compact() {
        delegate.compact();
        return this;
    }

    @Override
    public int countComponents() {
        return delegate.countComponents();
    }

    @Override
    public int countReadableComponents() {
        return delegate.countReadableComponents();
    }

    @Override
    public int countWritableComponents() {
        return delegate.countWritableComponents();
    }

    @Override
    public <E extends Exception> int forEachReadable(int initialIndex,
                                                     ReadableComponentProcessor<E> processor) throws E {
        return delegate.forEachReadable(initialIndex, processor);
    }

    @Override
    public <T extends ReadableComponent & Next> ComponentIterator<T> forEachReadable() {
        return delegate.forEachReadable();
    }

    @Override
    public <E extends Exception> int forEachWritable(int initialIndex,
                                                     WritableComponentProcessor<E> processor) throws E {
        return delegate.forEachWritable(initialIndex, processor);
    }

    @Override
    public <T extends WritableComponent & Next> ComponentIterator<T> forEachWritable() {
        return delegate.forEachWritable();
    }

    @Override
    public byte readByte() {
        return delegate.readByte();
    }

    @Override
    public byte getByte(int roff) {
        return delegate.getByte(roff);
    }

    @Override
    public int readUnsignedByte() {
        return delegate.readUnsignedByte();
    }

    @Override
    public int getUnsignedByte(int roff) {
        return delegate.getUnsignedByte(roff);
    }

    @Override
    public Buffer writeByte(byte value) {
        delegate.writeByte(value);
        return this;
    }

    @Override
    public Buffer setByte(int woff, byte value) {
        delegate.setByte(woff, value);
        return this;
    }

    @Override
    public Buffer writeUnsignedByte(int value) {
        delegate.writeUnsignedByte(value);
        return this;
    }

    @Override
    public Buffer setUnsignedByte(int woff, int value) {
        delegate.setUnsignedByte(woff, value);
        return this;
    }

    @Override
    public char readChar() {
        return delegate.readChar();
    }

    @Override
    public char getChar(int roff) {
        return delegate.getChar(roff);
    }

    @Override
    public Buffer writeChar(char value) {
        delegate.writeChar(value);
        return this;
    }

    @Override
    public Buffer setChar(int woff, char value) {
        delegate.setChar(woff, value);
        return this;
    }

    @Override
    public short readShort() {
        return delegate.readShort();
    }

    @Override
    public short getShort(int roff) {
        return delegate.getShort(roff);
    }

    @Override
    public int readUnsignedShort() {
        return delegate.readUnsignedShort();
    }

    @Override
    public int getUnsignedShort(int roff) {
        return delegate.getUnsignedShort(roff);
    }

    @Override
    public Buffer writeShort(short value) {
        delegate.writeShort(value);
        return this;
    }

    @Override
    public Buffer setShort(int woff, short value) {
        delegate.setShort(woff, value);
        return this;
    }

    @Override
    public Buffer writeUnsignedShort(int value) {
        delegate.writeUnsignedShort(value);
        return this;
    }

    @Override
    public Buffer setUnsignedShort(int woff, int value) {
        delegate.setUnsignedShort(woff, value);
        return this;
    }

    @Override
    public int readMedium() {
        return delegate.readMedium();
    }

    @Override
    public int getMedium(int roff) {
        return delegate.getMedium(roff);
    }

    @Override
    public int readUnsignedMedium() {
        return delegate.readUnsignedMedium();
    }

    @Override
    public int getUnsignedMedium(int roff) {
        return delegate.getUnsignedMedium(roff);
    }

    @Override
    public Buffer writeMedium(int value) {
        delegate.writeMedium(value);
        return this;
    }

    @Override
    public Buffer setMedium(int woff, int value) {
        delegate.setMedium(woff, value);
        return this;
    }

    @Override
    public Buffer writeUnsignedMedium(int value) {
        delegate.writeUnsignedMedium(value);
        return this;
    }

    @Override
    public Buffer setUnsignedMedium(int woff, int value) {
        delegate.setUnsignedMedium(woff, value);
        return this;
    }

    @Override
    public int readInt() {
        return delegate.readInt();
    }

    @Override
    public int getInt(int roff) {
        return delegate.getInt(roff);
    }

    @Override
    public long readUnsignedInt() {
        return delegate.readUnsignedInt();
    }

    @Override
    public long getUnsignedInt(int roff) {
        return delegate.getUnsignedInt(roff);
    }

    @Override
    public Buffer writeInt(int value) {
        delegate.writeInt(value);
        return this;
    }

    @Override
    public Buffer setInt(int woff, int value) {
        delegate.setInt(woff, value);
        return this;
    }

    @Override
    public Buffer writeUnsignedInt(long value) {
        delegate.writeUnsignedInt(value);
        return this;
    }

    @Override
    public Buffer setUnsignedInt(int woff, long value) {
        delegate.setUnsignedInt(woff, value);
        return this;
    }

    @Override
    public float readFloat() {
        return delegate.readFloat();
    }

    @Override
    public float getFloat(int roff) {
        return delegate.getFloat(roff);
    }

    @Override
    public Buffer writeFloat(float value) {
        delegate.writeFloat(value);
        return this;
    }

    @Override
    public Buffer setFloat(int woff, float value) {
        delegate.setFloat(woff, value);
        return this;
    }

    @Override
    public long readLong() {
        return delegate.readLong();
    }

    @Override
    public long getLong(int roff) {
        return delegate.getLong(roff);
    }

    @Override
    public Buffer writeLong(long value) {
        delegate.writeLong(value);
        return this;
    }

    @Override
    public Buffer setLong(int woff, long value) {
        delegate.setLong(woff, value);
        return this;
    }

    @Override
    public double readDouble() {
        return delegate.readDouble();
    }

    @Override
    public double getDouble(int roff) {
        return delegate.getDouble(roff);
    }

    @Override
    public Buffer writeDouble(double value) {
        delegate.writeDouble(value);
        return this;
    }

    @Override
    public Buffer setDouble(int woff, double value) {
        delegate.setDouble(woff, value);
        return this;
    }

    @Override
    public Buffer writeCharSequence(CharSequence source, Charset charset) {
        delegate.writeCharSequence(source, charset);
        return this;
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return delegate.readCharSequence(length, charset);
    }

    @Override
    public Buffer writeBytes(byte[] source, int srcPos, int length) {
        delegate.writeBytes(source, srcPos, length);
        return this;
    }

    @Override
    public Buffer readBytes(byte[] destination, int destPos, int length) {
        delegate.readBytes(destination, destPos, length);
        return this;
    }

    @Override
    public Buffer copy(int offset, int length) {
        return delegate.copy(offset, length);
    }

    @Override
    public Buffer readSplit(int length) {
        return delegate.readSplit(length);
    }

    @Override
    public Buffer writeSplit(int length) {
        return delegate.writeSplit(length);
    }

    @Override
    public boolean readBoolean() {
        return delegate.readBoolean();
    }

    @Override
    public boolean getBoolean(int roff) {
        return delegate.getBoolean(roff);
    }

    @Override
    public Buffer writeBoolean(boolean value) {
       delegate.writeBoolean(value);
       return this;
    }

    @Override
    public Buffer setBoolean(int woff, boolean value) {
       delegate.setBoolean(woff, value);
       return this;
    }

    @Override
    public Buffer writeBytes(ByteBuffer source) {
        delegate.writeBytes(source);
        return this;
    }

    @Override
    public Buffer readBytes(ByteBuffer destination) {
        delegate.readBytes(destination);
        return this;
    }

    @Override
    public Buffer copy(boolean readOnly) {
        return delegate.copy(readOnly);
    }

    @Override
    public Send<Buffer> send() {
        return delegate.send();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isAccessible() {
        return delegate.isAccessible();
    }

    @Override
    public Buffer touch(Object hint) {
        delegate.touch(hint);
        return this;
    }

    @Override
    public String toString(Charset charset) {
        return delegate.toString(charset);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Buffer && delegate.equals(obj);
    }
}
