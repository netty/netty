/*
 * Copyright 2019 The Netty Project
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
package io.netty.channel.unix;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.DuplicatedByteBuf;
import io.netty.buffer.ReadOnlyByteBuf;
import io.netty.buffer.SlicedByteBuf;
import io.netty.buffer.SwappedByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.ByteProcessor;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

import static io.netty.util.internal.ObjectUtil.checkPositive;

final class DirectIoByteBufPool {

    private static final int MAX_CACHED_SIZE = SystemPropertyUtil.getInt(
            "jdk.nio.maxCachedBufferSize", Integer.MAX_VALUE); // Use the same property name as the JDK.

    // We need the Cleaner to ensure the memory will eventually be released as the user may not use a
    // FastThreadLocalThread.
    private static final ByteBufAllocator IO_BUFFER_ALLOCATOR = new UnpooledByteBufAllocator(true, false, false);

    private static final FastThreadLocal<DirectIoByteBufPool> POOL = new FastThreadLocal<DirectIoByteBufPool>() {
        @Override
        protected DirectIoByteBufPool initialValue() {
            return new DirectIoByteBufPool(Thread.currentThread());
        }

        @Override
        protected void onRemoval(DirectIoByteBufPool value) {
            value.free();
        }
    };

    private ByteBuf parent;

    // Just used to assert for the correct thread when releasing the buffer back to the pool.
    private final Thread ioThread;

    private DirectIoByteBufPool(Thread ioThread) {
        this.ioThread = ioThread;
        parent = IO_BUFFER_ALLOCATOR.directBuffer(4096);
    }

    static ByteBuf acquire(int capacity) {
        ByteBuf buffer = POOL.get().get(capacity);
        return buffer != null ? buffer : IO_BUFFER_ALLOCATOR.directBuffer(capacity);
    }

    private ByteBuf get(int reqCapacity) {
        if (parent.capacity() + reqCapacity > MAX_CACHED_SIZE) {
            return null;
        }

        // No space left...
        if (parent.writableBytes() < reqCapacity) {
            int capacity = IO_BUFFER_ALLOCATOR.calculateNewCapacity(parent.capacity() + reqCapacity,
                    parent.capacity() + reqCapacity * 2);
            parent = IO_BUFFER_ALLOCATOR.directBuffer(capacity);
        }

        int writerIndex = parent.writerIndex();
        // TODO: Improve GC if possible
        PooledIoByteBuf buffer =  new PooledIoByteBuf(parent.retainedSlice(writerIndex, reqCapacity).clear());
        parent.writerIndex(writerIndex + reqCapacity);
        return buffer;
    }

    private void free() {
        boolean released = parent.release();
        assert released;
    }

    private void assertThread() {
        assert Thread.currentThread() == ioThread;
    }

    private final class PooledIoByteBuf extends ByteBuf {

        private final ByteBuf buffer;
        private int refCnt = 1; // Non volatile as we enforce the Thread to be the same for all release / retain calls.

        PooledIoByteBuf(ByteBuf buffer) {
            this.buffer = buffer;
        }

        @Override
        public int capacity() {
            return buffer.capacity();
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            checkRefCnt();
            buffer.capacity(newCapacity);
            return this;
        }

        @Override
        public int maxCapacity() {
            return buffer.maxCapacity();
        }

        @Override
        public ByteBufAllocator alloc() {
            return buffer.alloc();
        }

        @Override
        @Deprecated
        public ByteOrder order() {
            return buffer.order();
        }

        @Override
        @Deprecated
        public ByteBuf order(ByteOrder endianness) {
            if (order() == endianness) {
                return this;
            }
            return new SwappedByteBuf(this);
        }

        @Override
        public ByteBuf unwrap() {
            // Never allow the buffer to escape.
            return null;
        }

        @Override
        public boolean isDirect() {
            return buffer.isDirect();
        }

        @Override
        public boolean isReadOnly() {
            return buffer.isReadOnly();
        }

        @Override
        public ByteBuf asReadOnly() {
            checkRefCnt();
            if (isReadOnly()) {
                return this;
            }
            return new ReadOnlyByteBuf(this);
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
        public int writerIndex() {
            return buffer.writerIndex();
        }

        @Override
        public ByteBuf writerIndex(int writerIndex) {
            buffer.writerIndex(writerIndex);
            return this;
        }

        @Override
        public ByteBuf setIndex(int readerIndex, int writerIndex) {
            buffer.setIndex(readerIndex, writerIndex);
            return this;
        }

        @Override
        public int readableBytes() {
            return buffer.readableBytes();
        }

        @Override
        public int writableBytes() {
            return buffer.writableBytes();
        }

        @Override
        public int maxWritableBytes() {
            return buffer.maxWritableBytes();
        }

        @Override
        public boolean isReadable() {
            return buffer.isReadable();
        }

        @Override
        public boolean isReadable(int size) {
            return buffer.isReadable(size);
        }

        @Override
        public boolean isWritable() {
            return buffer.isWritable();
        }

        @Override
        public boolean isWritable(int size) {
            return buffer.isWritable(size);
        }

        @Override
        public ByteBuf clear() {
            checkRefCnt();
            buffer.clear();
            return this;
        }

        @Override
        public ByteBuf markReaderIndex() {
            checkRefCnt();
            buffer.markReaderIndex();
            return this;
        }

        @Override
        public ByteBuf resetReaderIndex() {
            checkRefCnt();
            buffer.resetReaderIndex();
            return this;
        }

        @Override
        public ByteBuf markWriterIndex() {
            checkRefCnt();
            buffer.markWriterIndex();
            return this;
        }

        @Override
        public ByteBuf resetWriterIndex() {
            checkRefCnt();
            buffer.resetWriterIndex();
            return this;
        }

        @Override
        public ByteBuf discardReadBytes() {
            checkRefCnt();
            buffer.discardReadBytes();
            return this;
        }

        @Override
        public ByteBuf discardSomeReadBytes() {
            checkRefCnt();
            buffer.discardSomeReadBytes();
            return this;
        }

        @Override
        public ByteBuf ensureWritable(int minWritableBytes) {
            checkRefCnt();
            buffer.ensureWritable(minWritableBytes);
            return this;
        }

        @Override
        public int ensureWritable(int minWritableBytes, boolean force) {
            checkRefCnt();
            return buffer.ensureWritable(minWritableBytes, force);
        }

        @Override
        public boolean getBoolean(int index) {
            checkRefCnt();
            return buffer.getBoolean(index);
        }

        @Override
        public byte getByte(int index) {
            checkRefCnt();
            return buffer.getByte(index);
        }

        @Override
        public short getUnsignedByte(int index) {
            checkRefCnt();
            return buffer.getUnsignedByte(index);
        }

        @Override
        public short getShort(int index) {
            checkRefCnt();
            return buffer.getShort(index);
        }

        @Override
        public short getShortLE(int index) {
            checkRefCnt();
            return buffer.getShortLE(index);
        }

        @Override
        public int getUnsignedShort(int index) {
            checkRefCnt();
            return buffer.getUnsignedShort(index);
        }

        @Override
        public int getUnsignedShortLE(int index) {
            checkRefCnt();
            return buffer.getUnsignedShortLE(index);
        }

        @Override
        public int getMedium(int index) {
            checkRefCnt();
            return buffer.getMedium(index);
        }

        @Override
        public int getMediumLE(int index) {
            checkRefCnt();
            return buffer.getMediumLE(index);
        }

        @Override
        public int getUnsignedMedium(int index) {
            checkRefCnt();
            return buffer.getUnsignedMedium(index);
        }

        @Override
        public int getUnsignedMediumLE(int index) {
            checkRefCnt();
            return buffer.getUnsignedMediumLE(index);
        }

        @Override
        public int getInt(int index) {
            checkRefCnt();
            return buffer.getInt(index);
        }

        @Override
        public int getIntLE(int index) {
            checkRefCnt();
            return buffer.getIntLE(index);
        }

        @Override
        public long getUnsignedInt(int index) {
            checkRefCnt();
            return buffer.getUnsignedInt(index);
        }

        @Override
        public long getUnsignedIntLE(int index) {
            checkRefCnt();
            return buffer.getUnsignedIntLE(index);
        }

        @Override
        public long getLong(int index) {
            checkRefCnt();
            return buffer.getLong(index);
        }

        @Override
        public long getLongLE(int index) {
            checkRefCnt();
            return buffer.getLongLE(index);
        }

        @Override
        public char getChar(int index) {
            checkRefCnt();
            return buffer.getChar(index);
        }

        @Override
        public float getFloat(int index) {
            checkRefCnt();
            return buffer.getFloat(index);
        }

        @Override
        public double getDouble(int index) {
            checkRefCnt();
            return buffer.getDouble(index);
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst) {
            checkRefCnt();
            buffer.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int length) {
            checkRefCnt();
            buffer.getBytes(index, dst, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            checkRefCnt();
            buffer.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst) {
            checkRefCnt();
            buffer.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            checkRefCnt();
            buffer.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            checkRefCnt();
            buffer.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
            checkRefCnt();
            buffer.getBytes(index, out, length);
            return this;
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
            checkRefCnt();
            return buffer.getBytes(index, out, length);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
            checkRefCnt();
            return buffer.getBytes(index, out, position, length);
        }

        @Override
        public CharSequence getCharSequence(int index, int length, Charset charset) {
            checkRefCnt();
            return buffer.getCharSequence(index, length, charset);
        }

        @Override
        public ByteBuf setBoolean(int index, boolean value) {
            checkRefCnt();
            buffer.setBoolean(index, value);
            return this;
        }

        @Override
        public ByteBuf setByte(int index, int value) {
            checkRefCnt();
            buffer.setByte(index, value);
            return this;
        }

        @Override
        public ByteBuf setShort(int index, int value) {
            checkRefCnt();
            buffer.setShort(index, value);
            return this;
        }

        @Override
        public ByteBuf setShortLE(int index, int value) {
            checkRefCnt();
            buffer.setShortLE(index, value);
            return this;
        }

        @Override
        public ByteBuf setMedium(int index, int value) {
            checkRefCnt();
            buffer.setMedium(index, value);
            return this;
        }

        @Override
        public ByteBuf setMediumLE(int index, int value) {
            checkRefCnt();
            buffer.setMediumLE(index, value);
            return this;
        }

        @Override
        public ByteBuf setInt(int index, int value) {
            checkRefCnt();
            buffer.setInt(index, value);
            return this;
        }

        @Override
        public ByteBuf setIntLE(int index, int value) {
            checkRefCnt();
            buffer.setIntLE(index, value);
            return this;
        }

        @Override
        public ByteBuf setLong(int index, long value) {
            checkRefCnt();
            buffer.setLong(index, value);
            return this;
        }

        @Override
        public ByteBuf setLongLE(int index, long value) {
            checkRefCnt();
            buffer.setLongLE(index, value);
            return this;
        }

        @Override
        public ByteBuf setChar(int index, int value) {
            checkRefCnt();
            buffer.setChar(index, value);
            return this;
        }

        @Override
        public ByteBuf setFloat(int index, float value) {
            checkRefCnt();
            buffer.setFloat(index, value);
            return this;
        }

        @Override
        public ByteBuf setDouble(int index, double value) {
            checkRefCnt();
            buffer.setDouble(index, value);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src) {
            checkRefCnt();
            buffer.setBytes(index, src);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int length) {
            checkRefCnt();
            buffer.setBytes(index, src, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            checkRefCnt();
            buffer.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src) {
            checkRefCnt();
            buffer.setBytes(index, src);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            checkRefCnt();
            buffer.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            checkRefCnt();
            buffer.setBytes(index, src);
            return this;
        }

        @Override
        public int setBytes(int index, InputStream in, int length) throws IOException {
            checkRefCnt();
            return buffer.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
            checkRefCnt();
            return buffer.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
            checkRefCnt();
            return buffer.setBytes(index, in, position, length);
        }

        @Override
        public ByteBuf setZero(int index, int length) {
            checkRefCnt();
            buffer.setZero(index, length);
            return this;
        }

        @Override
        public int setCharSequence(int index, CharSequence sequence, Charset charset) {
            checkRefCnt();
            return buffer.setCharSequence(index, sequence, charset);
        }

        @Override
        public boolean readBoolean() {
            checkRefCnt();
            return buffer.readBoolean();
        }

        @Override
        public byte readByte() {
            checkRefCnt();
            return buffer.readByte();
        }

        @Override
        public short readUnsignedByte() {
            checkRefCnt();
            return buffer.readUnsignedByte();
        }

        @Override
        public short readShort() {
            checkRefCnt();
            return buffer.readShort();
        }

        @Override
        public short readShortLE() {
            checkRefCnt();
            return buffer.readShortLE();
        }

        @Override
        public int readUnsignedShort() {
            checkRefCnt();
            return buffer.readUnsignedShort();
        }

        @Override
        public int readUnsignedShortLE() {
            checkRefCnt();
            return buffer.readUnsignedShortLE();
        }

        @Override
        public int readMedium() {
            checkRefCnt();
            return buffer.readMedium();
        }

        @Override
        public int readMediumLE() {
            checkRefCnt();
            return buffer.readMediumLE();
        }

        @Override
        public int readUnsignedMedium() {
            checkRefCnt();
            return buffer.readUnsignedMedium();
        }

        @Override
        public int readUnsignedMediumLE() {
            checkRefCnt();
            return buffer.readUnsignedMediumLE();
        }

        @Override
        public int readInt() {
            checkRefCnt();
            return buffer.readInt();
        }

        @Override
        public int readIntLE() {
            checkRefCnt();
            return buffer.readIntLE();
        }

        @Override
        public long readUnsignedInt() {
            checkRefCnt();
            return buffer.readUnsignedInt();
        }

        @Override
        public long readUnsignedIntLE() {
            checkRefCnt();
            return buffer.readUnsignedIntLE();
        }

        @Override
        public long readLong() {
            checkRefCnt();
            return buffer.readLong();
        }

        @Override
        public long readLongLE() {
            checkRefCnt();
            return buffer.readLongLE();
        }

        @Override
        public char readChar() {
            checkRefCnt();
            return buffer.readChar();
        }

        @Override
        public float readFloat() {
            checkRefCnt();
            return buffer.readFloat();
        }

        @Override
        public double readDouble() {
            checkRefCnt();
            return buffer.readDouble();
        }

        @Override
        public ByteBuf readBytes(int length) {
            checkRefCnt();
            return buffer.readBytes(length);
        }

        @Override
        public ByteBuf readSlice(int length) {
            checkRefCnt();
            @SuppressWarnings("deprecation")
            ByteBuf slice = new SlicedByteBuf(this, readerIndex(), length);
            readerIndex(readerIndex() + length);
            return slice;
        }

        @Override
        public ByteBuf readRetainedSlice(int length) {
            checkRefCnt();
            return readSlice(length).retain();
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst) {
            checkRefCnt();
            buffer.readBytes(dst);
            return this;
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst, int length) {
            checkRefCnt();
            buffer.readBytes(dst, length);
            return this;
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
            checkRefCnt();
            buffer.readBytes(dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf readBytes(byte[] dst) {
            checkRefCnt();
            buffer.readBytes(dst);
            return this;
        }

        @Override
        public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
            checkRefCnt();
            buffer.readBytes(dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf readBytes(ByteBuffer dst) {
            checkRefCnt();
            buffer.readBytes(dst);
            return this;
        }

        @Override
        public ByteBuf readBytes(OutputStream out, int length) throws IOException {
            checkRefCnt();
            buffer.readBytes(out, length);
            return this;
        }

        @Override
        public int readBytes(GatheringByteChannel out, int length) throws IOException {
            checkRefCnt();
            return buffer.readBytes(out, length);
        }

        @Override
        public CharSequence readCharSequence(int length, Charset charset) {
            checkRefCnt();
            return buffer.readCharSequence(length, charset);
        }

        @Override
        public int readBytes(FileChannel out, long position, int length) throws IOException {
            checkRefCnt();
            return buffer.readBytes(out, position, length);
        }

        @Override
        public ByteBuf skipBytes(int length) {
            checkRefCnt();
            buffer.skipBytes(length);
            return this;
        }

        @Override
        public ByteBuf writeBoolean(boolean value) {
            checkRefCnt();
            buffer.writeBoolean(value);
            return this;
        }

        @Override
        public ByteBuf writeByte(int value) {
            checkRefCnt();
            buffer.writeByte(value);
            return this;
        }

        @Override
        public ByteBuf writeShort(int value) {
            checkRefCnt();
            buffer.writeShort(value);
            return this;
        }

        @Override
        public ByteBuf writeShortLE(int value) {
            checkRefCnt();
            buffer.writeShortLE(value);
            return this;
        }

        @Override
        public ByteBuf writeMedium(int value) {
            checkRefCnt();
            buffer.writeMedium(value);
            return this;
        }

        @Override
        public ByteBuf writeMediumLE(int value) {
            checkRefCnt();
            buffer.writeMediumLE(value);
            return this;
        }

        @Override
        public ByteBuf writeInt(int value) {
            checkRefCnt();
            buffer.writeInt(value);
            return this;
        }

        @Override
        public ByteBuf writeIntLE(int value) {
            checkRefCnt();
            buffer.writeIntLE(value);
            return this;
        }

        @Override
        public ByteBuf writeLong(long value) {
            checkRefCnt();
            buffer.writeLong(value);
            return this;
        }

        @Override
        public ByteBuf writeLongLE(long value) {
            checkRefCnt();
            buffer.writeLongLE(value);
            return this;
        }

        @Override
        public ByteBuf writeChar(int value) {
            checkRefCnt();
            buffer.writeChar(value);
            return this;
        }

        @Override
        public ByteBuf writeFloat(float value) {
            checkRefCnt();
            buffer.writeFloat(value);
            return this;
        }

        @Override
        public ByteBuf writeDouble(double value) {
            checkRefCnt();
            buffer.writeDouble(value);
            return this;
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src) {
            checkRefCnt();
            buffer.writeBytes(src);
            return this;
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src, int length) {
            checkRefCnt();
            buffer.writeBytes(src, length);
            return this;
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
            checkRefCnt();
            buffer.writeBytes(src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf writeBytes(byte[] src) {
            checkRefCnt();
            buffer.writeBytes(src);
            return this;
        }

        @Override
        public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
            checkRefCnt();
            buffer.writeBytes(src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf writeBytes(ByteBuffer src) {
            checkRefCnt();
            buffer.writeBytes(src);
            return this;
        }

        @Override
        public int writeBytes(InputStream in, int length) throws IOException {
            checkRefCnt();
            return buffer.writeBytes(in, length);
        }

        @Override
        public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
            checkRefCnt();
            return buffer.writeBytes(in, length);
        }

        @Override
        public int writeBytes(FileChannel in, long position, int length) throws IOException {
            checkRefCnt();
            return buffer.writeBytes(in, position, length);
        }

        @Override
        public ByteBuf writeZero(int length) {
            checkRefCnt();
            buffer.writeZero(length);
            return this;
        }

        @Override
        public int writeCharSequence(CharSequence sequence, Charset charset) {
            checkRefCnt();
            return buffer.writeCharSequence(sequence, charset);
        }

        @Override
        public int indexOf(int fromIndex, int toIndex, byte value) {
            checkRefCnt();
            return buffer.indexOf(fromIndex, toIndex, value);
        }

        @Override
        public int bytesBefore(byte value) {
            checkRefCnt();
            return buffer.bytesBefore(value);
        }

        @Override
        public int bytesBefore(int length, byte value) {
            checkRefCnt();
            return buffer.bytesBefore(length, value);
        }

        @Override
        public int bytesBefore(int index, int length, byte value) {
            checkRefCnt();
            return buffer.bytesBefore(index, length, value);
        }

        @Override
        public int forEachByte(ByteProcessor processor) {
            checkRefCnt();
            return buffer.forEachByte(processor);
        }

        @Override
        public int forEachByte(int index, int length, ByteProcessor processor) {
            checkRefCnt();
            return buffer.forEachByte(index, length, processor);
        }

        @Override
        public int forEachByteDesc(ByteProcessor processor) {
            checkRefCnt();
            return buffer.forEachByteDesc(processor);
        }

        @Override
        public int forEachByteDesc(int index, int length, ByteProcessor processor) {
            checkRefCnt();
            return buffer.forEachByteDesc(index, length, processor);
        }

        @Override
        public ByteBuf copy() {
            checkRefCnt();
            return buffer.copy();
        }

        @Override
        public ByteBuf copy(int index, int length) {
            checkRefCnt();
            return buffer.copy(index, length);
        }

        @Override
        public ByteBuf slice() {
            checkRefCnt();
            return slice(readerIndex(), readableBytes());
        }

        @Override
        public ByteBuf retainedSlice() {
            checkRefCnt();
            return slice().retain();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ByteBuf slice(int index, int length) {
            checkRefCnt();
            return new SlicedByteBuf(this, index, length);
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            checkRefCnt();
            return slice(index, length).retain();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ByteBuf duplicate() {
            checkRefCnt();
            return new DuplicatedByteBuf(this);
        }

        @Override
        public ByteBuf retainedDuplicate() {
            checkRefCnt();
            return duplicate().retain();
        }

        @Override
        public int nioBufferCount() {
            checkRefCnt();
            return buffer.nioBufferCount();
        }

        @Override
        public ByteBuffer nioBuffer() {
            checkRefCnt();
            return buffer.nioBuffer();
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            checkRefCnt();
            return buffer.nioBuffer(index, length);
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            checkRefCnt();
            return buffer.internalNioBuffer(index, length);
        }

        @Override
        public ByteBuffer[] nioBuffers() {
            checkRefCnt();
            return buffer.nioBuffers();
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            checkRefCnt();
            return buffer.nioBuffers(index, length);
        }

        @Override
        public boolean hasArray() {
            return buffer.hasArray();
        }

        @Override
        public byte[] array() {
            checkRefCnt();
            return buffer.array();
        }

        @Override
        public int arrayOffset() {
            return buffer.arrayOffset();
        }

        @Override
        public boolean hasMemoryAddress() {
            return buffer.hasMemoryAddress();
        }

        @Override
        public long memoryAddress() {
            checkRefCnt();
            return buffer.memoryAddress();
        }

        @Override
        public String toString(Charset charset) {
            checkRefCnt();
            return buffer.toString(charset);
        }

        @Override
        public String toString(int index, int length, Charset charset) {
            checkRefCnt();
            return buffer.toString(index, length, charset);
        }

        @Override
        public int hashCode() {
            return buffer.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            checkRefCnt();
            return buffer.equals(obj);
        }

        @Override
        public int compareTo(ByteBuf buffer) {
            checkRefCnt();
            return this.buffer.compareTo(buffer);
        }

        @Override
        public String toString() {
            return buffer.toString();
        }

        @Override
        public ByteBuf retain(int increment) {
            return retain0(checkPositive(increment, "increment"));
        }

        @Override
        public ByteBuf retain() {
            return retain0(1);
        }

        private ByteBuf retain0(int increment) {
            assertThread();
            if (refCnt <= 0) {
                throw new IllegalReferenceCountException(refCnt, increment);
            }
            int cnt = refCnt + increment;
            if (cnt <= 0) {
                // overflow
                throw new IllegalReferenceCountException(refCnt, increment);
            }
            refCnt = cnt;
            return this;
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
        public int refCnt() {
            assertThread();
            return refCnt;
        }

        @Override
        public boolean release() {
            return release0(1);
        }

        @Override
        public boolean release(int decrement) {
            return release0(checkPositive(decrement, "decrement"));
        }

        private boolean release0(int decrement) {
            assertThread();
            if (refCnt < decrement) {
                throw new IllegalReferenceCountException(refCnt, -decrement);
            }
            refCnt -= decrement;

            if (refCnt == 0) {
                boolean released = buffer.release();
                assert !released;

                ByteBuf unwrapped = buffer.unwrap();
                if (unwrapped.refCnt() == 1) {
                    if (unwrapped == parent) {
                        // Once the parent reaches a refCnt of 1 we are able to call clear() and so have the
                        // reader / writer index reset to 0.
                        buffer.unwrap().clear();
                    } else {
                        // We replaced the parent buffer in the meantime just release the original.
                        unwrapped.release();
                    }
                }
                return true;
            }
            return false;
        }

        private void checkRefCnt() {
            if (refCnt <= 0) {
                throw new IllegalReferenceCountException();
            }
        }
    }
}
