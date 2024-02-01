/*
 * Copyright 2024 The Netty Project
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

/**
 * This class visits the possible wrapped child buffers of a Netty {@link ByteBuf} for a given offset and length.
 * <p>
 * The Netty ByteBuf API does not provide a method to visit the wrapped child buffers. The
 * {@link ByteBuf#unwrap()} method is not suitable for this purpose as it loses the
 * {@link ByteBuf#readerIndex()} state, resulting in incorrect offset and length information. For instance,
 * unwrapping the slice wrapper of a ByteBuf will lose the offset and the length of the slice.
 * <p>
 * Despite Netty not having a public API for visiting the sub buffers, it is possible to achieve this using
 * the {@link ByteBuf#getBytes(int, ByteBuf, int, int)} method. This class uses this method to visit the
 * wrapped child buffers by providing a suitable {@link ByteBuf} implementation. This implementation supports
 * the role of the destination buffer for the getBytes call. It requires implementing the
 * {@link ByteBuf#setBytes(int, ByteBuf, int, int)} method and other methods required by getBytes such as
 * {@link ByteBuf#hasArray()}, {@link ByteBuf#hasMemoryAddress()}, {@link ByteBuf#nioBufferCount()} and
 * {@link ByteBuf#capacity()}. All other methods in the internal ByteBuf implementation are not supported and will
 * throw an exception. This is to ensure correctness and to fail fast if some ByteBuf implementation is not
 * following the expected and supported interface contract.
 */
public final class ByteBufVisitor {
    private static final int DEFAULT_VISIT_MAX_DEPTH = 10;

    private ByteBufVisitor() {
        // utility class
    }

    /**
     * This method traverses the potential nested composite buffers of the provided buffer, given a specific offset and
     * length. The traversal continues until it encounters a buffer that is backed by an array or a memory address,
     * which allows for the inspection of individual buffer segments without the need for data duplication.
     * If no such wrapped buffer is found, the callback function is invoked with the original buffer, offset,
     * and length as parameters.
     *
     * @param buffer   the buffer to visit
     * @param offset   the offset for the buffer
     * @param length   the length for the buffer
     * @param callback the callback to call for each visited buffer
     */
    public static void visitBuffers(ByteBuf buffer, int offset, int length, ByteBufVisitorCallback callback) {
        visitBuffers(buffer, offset, length, callback, DEFAULT_VISIT_MAX_DEPTH);
    }

    /**
     * The callback interface for visiting buffers.
     */
    public interface ByteBufVisitorCallback {
        void visitBuffer(ByteBuf visitBuffer, int visitIndex, int visitLength);
    }

    /**
     * See @{@link #visitBuffers(ByteBuf, int, int, ByteBufVisitorCallback)}. This method allows to specify
     * the maximum depth of recursion for visiting wrapped buffers.
     */
    public static void visitBuffers(ByteBuf buffer, int offset, int length, ByteBufVisitorCallback callback,
                                    int maxDepth) {
        doRecursivelyVisitBuffers(buffer, offset, length, callback, maxDepth, 0);
    }

    private static void doRecursivelyVisitBuffers(final ByteBuf buffer, final int offset, final int length,
                                                  final ByteBufVisitorCallback callback, final int maxDepth,
                                                  final int depth) {
        if (length == 0) {
            // skip visiting empty buffers
            return;
        }
        // visit the wrapped buffers recursively if the buffer is not backed by an array or memory address
        // and the max depth has not been reached
        if (depth < maxDepth && !buffer.hasMemoryAddress() && !buffer.hasArray()) {
            visitBuffersImpl(buffer, offset, length, new ByteBufVisitorCallback() {
                @Override
                public void visitBuffer(ByteBuf visitBuffer, int visitIndex, int visitLength) {
                    if (visitBuffer == buffer && visitIndex == offset && visitLength == length) {
                        // visit the buffer since it was already passed to visitBuffersImpl and further recursion
                        // would cause unnecessary recursion up to the max depth of recursion
                        callback.visitBuffer(visitBuffer, visitIndex, visitLength);
                    } else {
                        // use the doRecursivelyVisitBuffers method to visit the wrapped buffer, possibly recursively
                        doRecursivelyVisitBuffers(visitBuffer, visitIndex, visitLength, callback, maxDepth, depth + 1);
                    }
                }
            });
        } else {
            // visit the buffer
            callback.visitBuffer(buffer, offset, length);
        }
    }

    // Implementation for visiting a single buffer level using {@link ByteBuf#getBytes(int, ByteBuf, int, int)}
    private static void visitBuffersImpl(ByteBuf parentBuffer, int parentOffset, int parentLength,
                                        ByteBufVisitorCallback callback) {
        // call getBytes to trigger the wrapped buffer visit
        parentBuffer.getBytes(parentOffset, new GetBytesCallbackByteBuf(callback), 0, parentLength);
    }

    /**
     * A ByteBuf implementation that can be used as the destination buffer for
     * a {@link ByteBuf#getBytes(int, ByteBuf)} for visiting the wrapped child buffers.
     */
    static class GetBytesCallbackByteBuf extends ByteBuf {
        private final ByteBufVisitorCallback callback;
        private final int capacity;

        GetBytesCallbackByteBuf(ByteBufVisitorCallback callback) {
            this(callback, Integer.MAX_VALUE);
        }
        GetBytesCallbackByteBuf(ByteBufVisitorCallback callback, int capacity) {
            this.callback = callback;
            this.capacity = capacity;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            callback.visitBuffer(src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            // TODO: It would be more optimal to have a callback method that takes a byte array
            callback.visitBuffer(Unpooled.wrappedBuffer(src), srcIndex, length);
            return this;
        }

        @Override
        public boolean hasArray() {
            // return false so that the wrapped buffer is visited
            return false;
        }

        @Override
        public boolean hasMemoryAddress() {
            // return false so that the wrapped buffer is visited
            return false;
        }

        @Override
        public int nioBufferCount() {
            // return 0 so that the wrapped buffer is visited
            return 0;
        }

        @Override
        public int capacity() {
            // should return sufficient capacity for the total length
            return capacity;
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int maxCapacity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBufAllocator alloc() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteOrder order() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf order(ByteOrder endianness) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf unwrap() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDirect() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReadOnly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf asReadOnly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readerIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readerIndex(int readerIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int writerIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writerIndex(int writerIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setIndex(int readerIndex, int writerIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readableBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int writableBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int maxWritableBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReadable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReadable(int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWritable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWritable(int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf markReaderIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf resetReaderIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf markWriterIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf resetWriterIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf discardReadBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf discardSomeReadBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf ensureWritable(int minWritableBytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int ensureWritable(int minWritableBytes, boolean force) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getBoolean(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte getByte(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public short getUnsignedByte(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public short getShort(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public short getShortLE(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getUnsignedShort(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getUnsignedShortLE(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMedium(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMediumLE(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getUnsignedMedium(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getUnsignedMediumLE(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getIntLE(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getUnsignedInt(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getUnsignedIntLE(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLongLE(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public char getChar(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getDouble(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharSequence getCharSequence(int index, int length, Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setBoolean(int index, boolean value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setByte(int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setShort(int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setShortLE(int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setMedium(int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setMediumLE(int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setInt(int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setIntLE(int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setLong(int index, long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setLongLE(int index, long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setChar(int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setFloat(int index, float value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setDouble(int index, double value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int setBytes(int index, InputStream in, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf setZero(int index, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int setCharSequence(int index, CharSequence sequence, Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean readBoolean() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte readByte() {
            throw new UnsupportedOperationException();
        }

        @Override
        public short readUnsignedByte() {
            throw new UnsupportedOperationException();
        }

        @Override
        public short readShort() {
            throw new UnsupportedOperationException();
        }

        @Override
        public short readShortLE() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readUnsignedShort() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readUnsignedShortLE() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readMedium() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readMediumLE() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readUnsignedMedium() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readUnsignedMediumLE() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readInt() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readIntLE() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readUnsignedInt() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readUnsignedIntLE() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readLong() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readLongLE() {
            throw new UnsupportedOperationException();
        }

        @Override
        public char readChar() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float readFloat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double readDouble() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readBytes(int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readSlice(int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readRetainedSlice(int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readBytes(byte[] dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readBytes(ByteBuffer dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf readBytes(OutputStream out, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readBytes(GatheringByteChannel out, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharSequence readCharSequence(int length, Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readBytes(FileChannel out, long position, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf skipBytes(int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeBoolean(boolean value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeByte(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeShort(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeShortLE(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeMedium(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeMediumLE(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeInt(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeIntLE(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeLong(long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeLongLE(long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeChar(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeFloat(float value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeDouble(double value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeBytes(byte[] src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeBytes(ByteBuffer src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int writeBytes(InputStream in, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int writeBytes(FileChannel in, long position, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf writeZero(int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int writeCharSequence(CharSequence sequence, Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int indexOf(int fromIndex, int toIndex, byte value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int bytesBefore(byte value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int bytesBefore(int length, byte value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int bytesBefore(int index, int length, byte value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int forEachByte(ByteProcessor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int forEachByte(int index, int length, ByteProcessor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int forEachByteDesc(ByteProcessor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int forEachByteDesc(int index, int length, ByteProcessor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf copy() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf copy(int index, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf slice() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf retainedSlice() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf slice(int index, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf duplicate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf retainedDuplicate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuffer nioBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuffer[] nioBuffers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            throw new UnsupportedOperationException();
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
        public long memoryAddress() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString(Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString(int index, int length, Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(ByteBuf buffer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf retain(int increment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int refCnt() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf retain() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf touch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf touch(Object hint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean release() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean release(int decrement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this));
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }
    }
}
