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
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;

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
    private static final ByteBufAllocator IO_BUFFER_ALLOCATOR = new UnpooledByteBufAllocator(true, true, false);

    private static final FastThreadLocal<DirectIoByteBufPool> POOL = new FastThreadLocal<DirectIoByteBufPool>() {
        @Override
        protected DirectIoByteBufPool initialValue() {
            return new DirectIoByteBufPool(Thread.currentThread());
        }

        @Override
        protected void onRemoval(DirectIoByteBufPool value) {
            value.freeAll();
        }
    };

    private static final int ARRAY_LENGTH = Math.min(MathUtil.findNextPositivePowerOfTwo(Limits.IOV_MAX), 4096);
    // We use a circular array to store the cached buffers.
    private final PooledIoByteBuf[] buffers = new PooledIoByteBuf[ARRAY_LENGTH];

    // Just used to assert for the correct thread when releasing the buffer back to the pool.
    private final Thread ioThread;

    private int idx;

    private DirectIoByteBufPool(Thread ioThread) {
        this.ioThread = ioThread;
    }

    private static int next(int i) {
        return (i + 1) & (ARRAY_LENGTH - 1);
    }

    static ByteBuf acquire(int capacity) {
        if (capacity > MAX_CACHED_SIZE) {
            // The requested buffer is to big just allocate a new one.
            return IO_BUFFER_ALLOCATOR.directBuffer(capacity);
        }

        ByteBuf buffer = POOL.get().get(capacity);
        return buffer != null ? buffer : IO_BUFFER_ALLOCATOR.directBuffer(capacity);
    }

    private ByteBuf get(int reqCapacity) {
        // Let's scan the array, which is not very large and will have the biggest buffers first in the array after
        // some time.
        int i = idx;
        do {
            PooledIoByteBuf buf = buffers[i];
            if (buf == null) {
                // No matching buffer found but there is still space in the array.
                return null;
            }

            if (buf.capacity() >= reqCapacity) {
                // Alright we found a buffer that is big enough, let just move the first cached buffer to the spot
                // where we found this one. This way we will have no empty slots.
                PooledIoByteBuf first = buffers[idx];
                assert first != null;
                buffers[i] = first;
                buffers[idx] = null;

                // reader and writer index should always be 0.
                assert buf.readerIndex() == 0 && buf.writerIndex() == 0;
                return buf;
            }

            i = next(i);
        } while (i != idx);

        // We did not find a buffer that was big enough but walked the whole array,
        // let's drop one from the cache so we may be able to cache it later on when it is released.
        removeAndFreeFirst();
        return null;
    }

    private void cache(PooledIoByteBuf buf) {
        int i = (idx - 1) & (ARRAY_LENGTH - 1);
        if (buffers[i] != null) {
            // Array is full, let's free the provided buffer.
            buf.free();
            return;
        }
        // Ensure we reset the reader and writer index before we put the buffer back.
        buf.clear();
        buffers[i] = buf;
        idx = i;
    }

    private boolean removeAndFreeFirst() {
        int i = idx;
        PooledIoByteBuf buf = buffers[i];
        if (buf == null) {
            return false;
        }
        buffers[i] = null;
        buf.free();
        idx = next(i);
        return true;
    }

    private void freeAll() {
        while (removeAndFreeFirst()) {
            // Free all buffers.
        }
        assert isArrayEmpty();
    }

    private boolean isArrayEmpty() {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null) {
                return false;
            }
        }
        return true;
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
            buffer.clear();
            return this;
        }

        @Override
        public ByteBuf markReaderIndex() {
            buffer.markReaderIndex();
            return this;
        }

        @Override
        public ByteBuf resetReaderIndex() {
            buffer.resetReaderIndex();
            return this;
        }

        @Override
        public ByteBuf markWriterIndex() {
            buffer.markWriterIndex();
            return this;
        }

        @Override
        public ByteBuf resetWriterIndex() {
            buffer.resetWriterIndex();
            return this;
        }

        @Override
        public ByteBuf discardReadBytes() {
            buffer.discardReadBytes();
            return this;
        }

        @Override
        public ByteBuf discardSomeReadBytes() {
            buffer.discardSomeReadBytes();
            return this;
        }

        @Override
        public ByteBuf ensureWritable(int minWritableBytes) {
            buffer.ensureWritable(minWritableBytes);
            return this;
        }

        @Override
        public int ensureWritable(int minWritableBytes, boolean force) {
            return buffer.ensureWritable(minWritableBytes, force);
        }

        @Override
        public boolean getBoolean(int index) {
            return buffer.getBoolean(index);
        }

        @Override
        public byte getByte(int index) {
            return buffer.getByte(index);
        }

        @Override
        public short getUnsignedByte(int index) {
            return buffer.getUnsignedByte(index);
        }

        @Override
        public short getShort(int index) {
            return buffer.getShort(index);
        }

        @Override
        public short getShortLE(int index) {
            return buffer.getShortLE(index);
        }

        @Override
        public int getUnsignedShort(int index) {
            return buffer.getUnsignedShort(index);
        }

        @Override
        public int getUnsignedShortLE(int index) {
            return buffer.getUnsignedShortLE(index);
        }

        @Override
        public int getMedium(int index) {
            return buffer.getMedium(index);
        }

        @Override
        public int getMediumLE(int index) {
            return buffer.getMediumLE(index);
        }

        @Override
        public int getUnsignedMedium(int index) {
            return buffer.getUnsignedMedium(index);
        }

        @Override
        public int getUnsignedMediumLE(int index) {
            return buffer.getUnsignedMediumLE(index);
        }

        @Override
        public int getInt(int index) {
            return buffer.getInt(index);
        }

        @Override
        public int getIntLE(int index) {
            return buffer.getIntLE(index);
        }

        @Override
        public long getUnsignedInt(int index) {
            return buffer.getUnsignedInt(index);
        }

        @Override
        public long getUnsignedIntLE(int index) {
            return buffer.getUnsignedIntLE(index);
        }

        @Override
        public long getLong(int index) {
            return buffer.getLong(index);
        }

        @Override
        public long getLongLE(int index) {
            return buffer.getLongLE(index);
        }

        @Override
        public char getChar(int index) {
            return buffer.getChar(index);
        }

        @Override
        public float getFloat(int index) {
            return buffer.getFloat(index);
        }

        @Override
        public double getDouble(int index) {
            return buffer.getDouble(index);
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst) {
            buffer.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int length) {
            buffer.getBytes(index, dst, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            buffer.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst) {
            buffer.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            buffer.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            buffer.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
            buffer.getBytes(index, out, length);
            return this;
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
            return buffer.getBytes(index, out, length);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
            return buffer.getBytes(index, out, position, length);
        }

        @Override
        public CharSequence getCharSequence(int index, int length, Charset charset) {
            return buffer.getCharSequence(index, length, charset);
        }

        @Override
        public ByteBuf setBoolean(int index, boolean value) {
            buffer.setBoolean(index, value);
            return this;
        }

        @Override
        public ByteBuf setByte(int index, int value) {
            buffer.setByte(index, value);
            return this;
        }

        @Override
        public ByteBuf setShort(int index, int value) {
            buffer.setShort(index, value);
            return this;
        }

        @Override
        public ByteBuf setShortLE(int index, int value) {
            buffer.setShortLE(index, value);
            return this;
        }

        @Override
        public ByteBuf setMedium(int index, int value) {
            buffer.setMedium(index, value);
            return this;
        }

        @Override
        public ByteBuf setMediumLE(int index, int value) {
            buffer.setMediumLE(index, value);
            return this;
        }

        @Override
        public ByteBuf setInt(int index, int value) {
            buffer.setInt(index, value);
            return this;
        }

        @Override
        public ByteBuf setIntLE(int index, int value) {
            buffer.setIntLE(index, value);
            return this;
        }

        @Override
        public ByteBuf setLong(int index, long value) {
            buffer.setLong(index, value);
            return this;
        }

        @Override
        public ByteBuf setLongLE(int index, long value) {
            buffer.setLongLE(index, value);
            return this;
        }

        @Override
        public ByteBuf setChar(int index, int value) {
            buffer.setChar(index, value);
            return this;
        }

        @Override
        public ByteBuf setFloat(int index, float value) {
            buffer.setFloat(index, value);
            return this;
        }

        @Override
        public ByteBuf setDouble(int index, double value) {
            buffer.setDouble(index, value);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src) {
            buffer.setBytes(index, src);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int length) {
            buffer.setBytes(index, src, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            buffer.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src) {
            buffer.setBytes(index, src);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            buffer.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            buffer.setBytes(index, src);
            return this;
        }

        @Override
        public int setBytes(int index, InputStream in, int length) throws IOException {
            return buffer.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
            return buffer.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
            return buffer.setBytes(index, in, position, length);
        }

        @Override
        public ByteBuf setZero(int index, int length) {
            buffer.setZero(index, length);
            return this;
        }

        @Override
        public int setCharSequence(int index, CharSequence sequence, Charset charset) {
            return buffer.setCharSequence(index, sequence, charset);
        }

        @Override
        public boolean readBoolean() {
            return buffer.readBoolean();
        }

        @Override
        public byte readByte() {
            return buffer.readByte();
        }

        @Override
        public short readUnsignedByte() {
            return buffer.readUnsignedByte();
        }

        @Override
        public short readShort() {
            return buffer.readShort();
        }

        @Override
        public short readShortLE() {
            return buffer.readShortLE();
        }

        @Override
        public int readUnsignedShort() {
            return buffer.readUnsignedShort();
        }

        @Override
        public int readUnsignedShortLE() {
            return buffer.readUnsignedShortLE();
        }

        @Override
        public int readMedium() {
            return buffer.readMedium();
        }

        @Override
        public int readMediumLE() {
            return buffer.readMediumLE();
        }

        @Override
        public int readUnsignedMedium() {
            return buffer.readUnsignedMedium();
        }

        @Override
        public int readUnsignedMediumLE() {
            return buffer.readUnsignedMediumLE();
        }

        @Override
        public int readInt() {
            return buffer.readInt();
        }

        @Override
        public int readIntLE() {
            return buffer.readIntLE();
        }

        @Override
        public long readUnsignedInt() {
            return buffer.readUnsignedInt();
        }

        @Override
        public long readUnsignedIntLE() {
            return buffer.readUnsignedIntLE();
        }

        @Override
        public long readLong() {
            return buffer.readLong();
        }

        @Override
        public long readLongLE() {
            return buffer.readLongLE();
        }

        @Override
        public char readChar() {
            return buffer.readChar();
        }

        @Override
        public float readFloat() {
            return buffer.readFloat();
        }

        @Override
        public double readDouble() {
            return buffer.readDouble();
        }

        @Override
        public ByteBuf readBytes(int length) {
            return buffer.readBytes(length);
        }

        @Override
        public ByteBuf readSlice(int length) {
            @SuppressWarnings("deprecation")
            ByteBuf slice = new SlicedByteBuf(this, readerIndex(), length);
            readerIndex(readerIndex() + length);
            return slice;
        }

        @Override
        public ByteBuf readRetainedSlice(int length) {
            return readSlice(length).retain();
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst) {
            buffer.readBytes(dst);
            return this;
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst, int length) {
            buffer.readBytes(dst, length);
            return this;
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
            buffer.readBytes(dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf readBytes(byte[] dst) {
            buffer.readBytes(dst);
            return this;
        }

        @Override
        public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
            buffer.readBytes(dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf readBytes(ByteBuffer dst) {
            buffer.readBytes(dst);
            return this;
        }

        @Override
        public ByteBuf readBytes(OutputStream out, int length) throws IOException {
            buffer.readBytes(out, length);
            return this;
        }

        @Override
        public int readBytes(GatheringByteChannel out, int length) throws IOException {
            return buffer.readBytes(out, length);
        }

        @Override
        public CharSequence readCharSequence(int length, Charset charset) {
            return buffer.readCharSequence(length, charset);
        }

        @Override
        public int readBytes(FileChannel out, long position, int length) throws IOException {
            return buffer.readBytes(out, position, length);
        }

        @Override
        public ByteBuf skipBytes(int length) {
            buffer.skipBytes(length);
            return this;
        }

        @Override
        public ByteBuf writeBoolean(boolean value) {
            buffer.writeBoolean(value);
            return this;
        }

        @Override
        public ByteBuf writeByte(int value) {
            buffer.writeByte(value);
            return this;
        }

        @Override
        public ByteBuf writeShort(int value) {
            buffer.writeShort(value);
            return this;
        }

        @Override
        public ByteBuf writeShortLE(int value) {
            buffer.writeShortLE(value);
            return this;
        }

        @Override
        public ByteBuf writeMedium(int value) {
            buffer.writeMedium(value);
            return this;
        }

        @Override
        public ByteBuf writeMediumLE(int value) {
            buffer.writeMediumLE(value);
            return this;
        }

        @Override
        public ByteBuf writeInt(int value) {
            buffer.writeInt(value);
            return this;
        }

        @Override
        public ByteBuf writeIntLE(int value) {
            buffer.writeIntLE(value);
            return this;
        }

        @Override
        public ByteBuf writeLong(long value) {
            buffer.writeLong(value);
            return this;
        }

        @Override
        public ByteBuf writeLongLE(long value) {
            buffer.writeLongLE(value);
            return this;
        }

        @Override
        public ByteBuf writeChar(int value) {
            buffer.writeChar(value);
            return this;
        }

        @Override
        public ByteBuf writeFloat(float value) {
            buffer.writeFloat(value);
            return this;
        }

        @Override
        public ByteBuf writeDouble(double value) {
            buffer.writeDouble(value);
            return this;
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src) {
            buffer.writeBytes(src);
            return this;
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src, int length) {
            buffer.writeBytes(src, length);
            return this;
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
            buffer.writeBytes(src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf writeBytes(byte[] src) {
            buffer.writeBytes(src);
            return this;
        }

        @Override
        public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
            buffer.writeBytes(src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf writeBytes(ByteBuffer src) {
            buffer.writeBytes(src);
            return this;
        }

        @Override
        public int writeBytes(InputStream in, int length) throws IOException {
            return buffer.writeBytes(in, length);
        }

        @Override
        public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
            return buffer.writeBytes(in, length);
        }

        @Override
        public int writeBytes(FileChannel in, long position, int length) throws IOException {
            return buffer.writeBytes(in, position, length);
        }

        @Override
        public ByteBuf writeZero(int length) {
            buffer.writeZero(length);
            return this;
        }

        @Override
        public int writeCharSequence(CharSequence sequence, Charset charset) {
            return buffer.writeCharSequence(sequence, charset);
        }

        @Override
        public int indexOf(int fromIndex, int toIndex, byte value) {
            return buffer.indexOf(fromIndex, toIndex, value);
        }

        @Override
        public int bytesBefore(byte value) {
            return buffer.bytesBefore(value);
        }

        @Override
        public int bytesBefore(int length, byte value) {
            return buffer.bytesBefore(length, value);
        }

        @Override
        public int bytesBefore(int index, int length, byte value) {
            return buffer.bytesBefore(index, length, value);
        }

        @Override
        public int forEachByte(ByteProcessor processor) {
            return buffer.forEachByte(processor);
        }

        @Override
        public int forEachByte(int index, int length, ByteProcessor processor) {
            return buffer.forEachByte(index, length, processor);
        }

        @Override
        public int forEachByteDesc(ByteProcessor processor) {
            return buffer.forEachByteDesc(processor);
        }

        @Override
        public int forEachByteDesc(int index, int length, ByteProcessor processor) {
            return buffer.forEachByteDesc(index, length, processor);
        }

        @Override
        public ByteBuf copy() {
            return buffer.copy();
        }

        @Override
        public ByteBuf copy(int index, int length) {
            return buffer.copy(index, length);
        }

        @Override
        public ByteBuf slice() {
            return slice(readerIndex(), readableBytes());
        }

        @Override
        public ByteBuf retainedSlice() {
            return slice().retain();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ByteBuf slice(int index, int length) {
            return new SlicedByteBuf(this, index, length);
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return slice(index, length).retain();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ByteBuf duplicate() {
            return new DuplicatedByteBuf(this);
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return duplicate().retain();
        }

        @Override
        public int nioBufferCount() {
            return buffer.nioBufferCount();
        }

        @Override
        public ByteBuffer nioBuffer() {
            return buffer.nioBuffer();
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            return buffer.nioBuffer(index, length);
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            return buffer.internalNioBuffer(index, length);
        }

        @Override
        public ByteBuffer[] nioBuffers() {
            return buffer.nioBuffers();
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            return buffer.nioBuffers(index, length);
        }

        @Override
        public boolean hasArray() {
            return buffer.hasArray();
        }

        @Override
        public byte[] array() {
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
            return buffer.memoryAddress();
        }

        @Override
        public String toString(Charset charset) {
            return buffer.toString(charset);
        }

        @Override
        public String toString(int index, int length, Charset charset) {
            return buffer.toString(index, length, charset);
        }

        @Override
        public int hashCode() {
            return buffer.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return buffer.equals(obj);
        }

        @Override
        public int compareTo(ByteBuf buffer) {
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
                cache(this);
                return true;
            }
            return false;
        }

        void free() {
            refCnt = 0;
            buffer.release();
        }
    }
}
