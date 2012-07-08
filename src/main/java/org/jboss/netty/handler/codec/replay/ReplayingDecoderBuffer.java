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
package org.jboss.netty.handler.codec.replay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBufferIndexFinder;

class ReplayingDecoderBuffer implements ChannelBuffer {

    private static final Error REPLAY = new ReplayError();

    private final ReplayingDecoder<?> parent;
    private boolean terminated;

    ReplayingDecoderBuffer(ReplayingDecoder<?> parent) {
        this.parent = parent;
    }

    private ChannelBuffer buf() {
        return parent.internalBuffer();
    }

    void terminate() {
        terminated = true;
    }

    public int capacity() {
        if (terminated) {
            return buf().capacity();
        } else {
            return Integer.MAX_VALUE;
        }
    }

    public boolean isDirect() {
        return buf().isDirect();
    }

    public boolean hasArray() {
        return false;
    }

    public byte[] array() {
        throw new UnsupportedOperationException();
    }

    public int arrayOffset() {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        throw new UnreplayableOperationException();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    public int compareTo(ChannelBuffer buffer) {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer copy() {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer copy(int index, int length) {
        checkIndex(index, length);
        return buf().copy(index, length);
    }

    public void discardReadBytes() {
        throw new UnreplayableOperationException();
    }

    public void ensureWritableBytes(int writableBytes) {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer duplicate() {
        throw new UnreplayableOperationException();
    }

    public byte getByte(int index) {
        checkIndex(index, 1);
        return buf().getByte(index);
    }

    public short getUnsignedByte(int index) {
        checkIndex(index, 1);
        return buf().getUnsignedByte(index);
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buf().getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, byte[] dst) {
        checkIndex(index, dst.length);
        buf().getBytes(index, dst);
    }

    public void getBytes(int index, ByteBuffer dst) {
        throw new UnreplayableOperationException();
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        buf().getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, ChannelBuffer dst, int length) {
        throw new UnreplayableOperationException();
    }

    public void getBytes(int index, ChannelBuffer dst) {
        throw new UnreplayableOperationException();
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    public int getInt(int index) {
        checkIndex(index, 4);
        return buf().getInt(index);
    }

    public long getUnsignedInt(int index) {
        checkIndex(index, 4);
        return buf().getUnsignedInt(index);
    }

    public long getLong(int index) {
        checkIndex(index, 8);
        return buf().getLong(index);
    }

    public int getMedium(int index) {
        checkIndex(index, 3);
        return buf().getMedium(index);
    }

    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return buf().getUnsignedMedium(index);
    }

    public short getShort(int index) {
        checkIndex(index, 2);
        return buf().getShort(index);
    }

    public int getUnsignedShort(int index) {
        checkIndex(index, 2);
        return buf().getUnsignedShort(index);
    }

    public char getChar(int index) {
        checkIndex(index, 2);
        return buf().getChar(index);
    }

    public float getFloat(int index) {
        checkIndex(index, 4);
        return buf().getFloat(index);
    }

    public double getDouble(int index) {
        checkIndex(index, 8);
        return buf().getDouble(index);
    }

    @Override
    public int hashCode() {
        throw new UnreplayableOperationException();
    }

    public int indexOf(int fromIndex, int toIndex, byte value) {
        int endIndex = buf().indexOf(fromIndex, toIndex, value);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return endIndex;
    }

    public int indexOf(int fromIndex, int toIndex,
            ChannelBufferIndexFinder indexFinder) {
        int endIndex = buf().indexOf(fromIndex, toIndex, indexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return endIndex;
    }

    public int bytesBefore(byte value) {
        int bytes = buf().bytesBefore(value);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    public int bytesBefore(ChannelBufferIndexFinder indexFinder) {
        int bytes = buf().bytesBefore(indexFinder);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    public int bytesBefore(int length, byte value) {
        checkReadableBytes(length);
        int bytes = buf().bytesBefore(length, value);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    public int bytesBefore(int length, ChannelBufferIndexFinder indexFinder) {
        checkReadableBytes(length);
        int bytes = buf().bytesBefore(length, indexFinder);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    public int bytesBefore(int index, int length, byte value) {
        int bytes = buf().bytesBefore(index, length, value);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    public int bytesBefore(int index, int length,
            ChannelBufferIndexFinder indexFinder) {
        int bytes = buf().bytesBefore(index, length, indexFinder);
        if (bytes < 0) {
            throw REPLAY;
        }
        return bytes;
    }

    public void markReaderIndex() {
        buf().markReaderIndex();
    }

    public void markWriterIndex() {
        throw new UnreplayableOperationException();
    }

    public ChannelBufferFactory factory() {
        return buf().factory();
    }

    public ByteOrder order() {
        return buf().order();
    }

    public boolean readable() {
        return terminated? buf().readable() : true;
    }

    public int readableBytes() {
        if (terminated) {
            return buf().readableBytes();
        } else {
            return Integer.MAX_VALUE - buf().readerIndex();
        }
    }

    public byte readByte() {
        checkReadableBytes(1);
        return buf().readByte();
    }

    public short readUnsignedByte() {
        checkReadableBytes(1);
        return buf().readUnsignedByte();
    }

    public void readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buf().readBytes(dst, dstIndex, length);
    }

    public void readBytes(byte[] dst) {
        checkReadableBytes(dst.length);
        buf().readBytes(dst);
    }

    public void readBytes(ByteBuffer dst) {
        throw new UnreplayableOperationException();
    }

    public void readBytes(ChannelBuffer dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buf().readBytes(dst, dstIndex, length);
    }

    public void readBytes(ChannelBuffer dst, int length) {
        throw new UnreplayableOperationException();
    }

    public void readBytes(ChannelBuffer dst) {
        throw new UnreplayableOperationException();
    }

    @Deprecated
    public ChannelBuffer readBytes(ChannelBufferIndexFinder endIndexFinder) {
        int endIndex = buf().indexOf(buf().readerIndex(), buf().writerIndex(), endIndexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return buf().readBytes(endIndex - buf().readerIndex());
    }

    public int readBytes(GatheringByteChannel out, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer readBytes(int length) {
        checkReadableBytes(length);
        return buf().readBytes(length);
    }

    @Deprecated
    public ChannelBuffer readSlice(
            ChannelBufferIndexFinder endIndexFinder) {
        int endIndex = buf().indexOf(buf().readerIndex(), buf().writerIndex(), endIndexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return buf().readSlice(endIndex - buf().readerIndex());
    }

    public ChannelBuffer readSlice(int length) {
        checkReadableBytes(length);
        return buf().readSlice(length);
    }

    public void readBytes(OutputStream out, int length) throws IOException {
        throw new UnreplayableOperationException();
    }

    public int readerIndex() {
        return buf().readerIndex();
    }

    public void readerIndex(int readerIndex) {
        buf().readerIndex(readerIndex);
    }

    public int readInt() {
        checkReadableBytes(4);
        return buf().readInt();
    }

    public long readUnsignedInt() {
        checkReadableBytes(4);
        return buf().readUnsignedInt();
    }

    public long readLong() {
        checkReadableBytes(8);
        return buf().readLong();
    }

    public int readMedium() {
        checkReadableBytes(3);
        return buf().readMedium();
    }

    public int readUnsignedMedium() {
        checkReadableBytes(3);
        return buf().readUnsignedMedium();
    }

    public short readShort() {
        checkReadableBytes(2);
        return buf().readShort();
    }

    public int readUnsignedShort() {
        checkReadableBytes(2);
        return buf().readUnsignedShort();
    }

    public char readChar() {
        checkReadableBytes(2);
        return buf().readChar();
    }

    public float readFloat() {
        checkReadableBytes(4);
        return buf().readFloat();
    }

    public double readDouble() {
        checkReadableBytes(8);
        return buf().readDouble();
    }

    public void resetReaderIndex() {
        buf().resetReaderIndex();
    }

    public void resetWriterIndex() {
        throw new UnreplayableOperationException();
    }

    public void setByte(int index, int value) {
        throw new UnreplayableOperationException();
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    public void setBytes(int index, byte[] src) {
        throw new UnreplayableOperationException();
    }

    public void setBytes(int index, ByteBuffer src) {
        throw new UnreplayableOperationException();
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    public void setBytes(int index, ChannelBuffer src, int length) {
        throw new UnreplayableOperationException();
    }

    public void setBytes(int index, ChannelBuffer src) {
        throw new UnreplayableOperationException();
    }

    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    public void setZero(int index, int length) {
        throw new UnreplayableOperationException();
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    public void setIndex(int readerIndex, int writerIndex) {
        throw new UnreplayableOperationException();
    }

    public void setInt(int index, int value) {
        throw new UnreplayableOperationException();
    }

    public void setLong(int index, long value) {
        throw new UnreplayableOperationException();
    }

    public void setMedium(int index, int value) {
        throw new UnreplayableOperationException();
    }

    public void setShort(int index, int value) {
        throw new UnreplayableOperationException();
    }

    public void setChar(int index, int value) {
        throw new UnreplayableOperationException();
    }

    public void setFloat(int index, float value) {
        throw new UnreplayableOperationException();
    }

    public void setDouble(int index, double value) {
        throw new UnreplayableOperationException();
    }

    @Deprecated
    public int skipBytes(ChannelBufferIndexFinder firstIndexFinder) {
        int oldReaderIndex = buf().readerIndex();
        int newReaderIndex = buf().indexOf(oldReaderIndex, buf().writerIndex(), firstIndexFinder);
        if (newReaderIndex < 0) {
            throw REPLAY;
        }
        buf().readerIndex(newReaderIndex);
        return newReaderIndex - oldReaderIndex;
    }

    public void skipBytes(int length) {
        checkReadableBytes(length);
        buf().skipBytes(length);
    }

    public ChannelBuffer slice() {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
        return buf().slice(index, length);
    }

    public ByteBuffer toByteBuffer() {
        throw new UnreplayableOperationException();
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        checkIndex(index, length);
        return buf().toByteBuffer(index, length);
    }

    public ByteBuffer[] toByteBuffers() {
        throw new UnreplayableOperationException();
    }

    public ByteBuffer[] toByteBuffers(int index, int length) {
        checkIndex(index, length);
        return buf().toByteBuffers(index, length);
    }

    public String toString(int index, int length, Charset charset) {
        checkIndex(index, length);
        return buf().toString(index, length, charset);
    }

    public String toString(Charset charsetName) {
        throw new UnreplayableOperationException();
    }

    @Deprecated
    public String toString(int index, int length, String charsetName) {
        checkIndex(index, length);
        return buf().toString(index, length, charsetName);
    }

    @Deprecated
    public String toString(
            int index, int length, String charsetName,
            ChannelBufferIndexFinder terminatorFinder) {
        checkIndex(index, length);
        return buf().toString(index, length, charsetName, terminatorFinder);
    }

    @Deprecated
    public String toString(String charsetName) {
        throw new UnreplayableOperationException();
    }

    @Deprecated
    public String toString(
            String charsetName, ChannelBufferIndexFinder terminatorFinder) {
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

    public boolean writable() {
        return false;
    }

    public int writableBytes() {
        return 0;
    }

    public void writeByte(int value) {
        throw new UnreplayableOperationException();
    }

    public void writeBytes(byte[] src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    public void writeBytes(byte[] src) {
        throw new UnreplayableOperationException();
    }

    public void writeBytes(ByteBuffer src) {
        throw new UnreplayableOperationException();
    }

    public void writeBytes(ChannelBuffer src, int srcIndex, int length) {
        throw new UnreplayableOperationException();
    }

    public void writeBytes(ChannelBuffer src, int length) {
        throw new UnreplayableOperationException();
    }

    public void writeBytes(ChannelBuffer src) {
        throw new UnreplayableOperationException();
    }

    public int writeBytes(InputStream in, int length) throws IOException {
        throw new UnreplayableOperationException();
    }

    public int writeBytes(ScatteringByteChannel in, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    public void writeInt(int value) {
        throw new UnreplayableOperationException();
    }

    public void writeLong(long value) {
        throw new UnreplayableOperationException();
    }

    public void writeMedium(int value) {
        throw new UnreplayableOperationException();
    }

    public void writeZero(int length) {
        throw new UnreplayableOperationException();
    }

    public int writerIndex() {
        return buf().writerIndex();
    }

    public void writerIndex(int writerIndex) {
        throw new UnreplayableOperationException();
    }

    public void writeShort(int value) {
        throw new UnreplayableOperationException();
    }

    public void writeChar(int value) {
        throw new UnreplayableOperationException();
    }

    public void writeFloat(float value) {
        throw new UnreplayableOperationException();
    }

    public void writeDouble(double value) {
        throw new UnreplayableOperationException();
    }

    private void checkIndex(int index, int length) {
        if (index + length > buf().writerIndex()) {
            throw REPLAY;
        }
    }

    private void checkReadableBytes(int readableBytes) {
        if (buf().readableBytes() < readableBytes) {
            throw REPLAY;
        }
    }
}
