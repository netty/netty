/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.replay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBufferIndexFinder;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class ReplayingDecoderBuffer implements ChannelBuffer {

    private static final Error REPLAY = new ReplayError();

    private final ChannelBuffer buffer;

    ReplayingDecoderBuffer(ChannelBuffer buffer) {
        this.buffer = buffer;
    }

    public int capacity() {
        return Integer.MAX_VALUE;
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
        return buffer.copy(index, length);
    }

    public void discardReadBytes() {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer duplicate() {
        throw new UnreplayableOperationException();
    }

    public byte getByte(int index) {
        checkIndex(index);
        return buffer.getByte(index);
    }

    public short getUnsignedByte(int index) {
        checkIndex(index);
        return buffer.getUnsignedByte(index);
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, byte[] dst) {
        checkIndex(index, dst.length);
        buffer.getBytes(index, dst);
    }

    public void getBytes(int index, ByteBuffer dst) {
        throw new UnreplayableOperationException();
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
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
        return buffer.getInt(index);
    }

    public long getUnsignedInt(int index) {
        checkIndex(index, 4);
        return buffer.getUnsignedInt(index);
    }

    public long getLong(int index) {
        checkIndex(index, 8);
        return buffer.getLong(index);
    }

    public int getMedium(int index) {
        checkIndex(index, 3);
        return buffer.getMedium(index);
    }

    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return buffer.getUnsignedMedium(index);
    }

    public short getShort(int index) {
        checkIndex(index, 2);
        return buffer.getShort(index);
    }

    public int getUnsignedShort(int index) {
        checkIndex(index, 2);
        return buffer.getUnsignedShort(index);
    }

    @Override
    public int hashCode() {
        throw new UnreplayableOperationException();
    }

    public int indexOf(int fromIndex, int toIndex, byte value) {
        int endIndex = buffer.indexOf(fromIndex, toIndex, value);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return endIndex;
    }

    public int indexOf(int fromIndex, int toIndex,
            ChannelBufferIndexFinder indexFinder) {
        int endIndex = buffer.indexOf(fromIndex, toIndex, indexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return endIndex;
    }

    public void markReaderIndex() {
        buffer.markReaderIndex();
    }

    public void markWriterIndex() {
        throw new UnreplayableOperationException();
    }

    public ChannelBufferFactory factory() {
        return buffer.factory();
    }

    public ByteOrder order() {
        return buffer.order();
    }

    public boolean readable() {
        return true;
    }

    public int readableBytes() {
        return Integer.MAX_VALUE - buffer.readerIndex();
    }

    public byte readByte() {
        checkReadableBytes(1);
        return buffer.readByte();
    }

    public short readUnsignedByte() {
        checkReadableBytes(1);
        return buffer.readUnsignedByte();
    }

    public void readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
    }

    public void readBytes(byte[] dst) {
        checkReadableBytes(dst.length);
        buffer.readBytes(dst);
    }

    public void readBytes(ByteBuffer dst) {
        throw new UnreplayableOperationException();
    }

    public void readBytes(ChannelBuffer dst, int dstIndex, int length) {
        checkReadableBytes(length);
        buffer.readBytes(dst, dstIndex, length);
    }

    public void readBytes(ChannelBuffer dst, int length) {
        throw new UnreplayableOperationException();
    }

    public void readBytes(ChannelBuffer dst) {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer readBytes(ChannelBufferIndexFinder endIndexFinder) {
        int endIndex = buffer.indexOf(buffer.readerIndex(), buffer.writerIndex(), endIndexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return buffer.readBytes(endIndex - buffer.readerIndex());
    }

    public int readBytes(GatheringByteChannel out, int length)
            throws IOException {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer readBytes(int length) {
        checkReadableBytes(length);
        return buffer.readBytes(length);
    }

    public ChannelBuffer readSlice(
            ChannelBufferIndexFinder endIndexFinder) {
        int endIndex = buffer.indexOf(buffer.readerIndex(), buffer.writerIndex(), endIndexFinder);
        if (endIndex < 0) {
            throw REPLAY;
        }
        return buffer.readSlice(endIndex - buffer.readerIndex());
    }

    public ChannelBuffer readSlice(int length) {
        checkReadableBytes(length);
        return buffer.readSlice(length);
    }

    public void readBytes(OutputStream out, int length) throws IOException {
        throw new UnreplayableOperationException();
    }

    public int readerIndex() {
        return buffer.readerIndex();
    }

    public void readerIndex(int readerIndex) {
        buffer.readerIndex(readerIndex);
    }

    public int readInt() {
        checkReadableBytes(4);
        return buffer.readInt();
    }

    public long readUnsignedInt() {
        checkReadableBytes(4);
        return buffer.readUnsignedInt();
    }

    public long readLong() {
        checkReadableBytes(8);
        return buffer.readLong();
    }

    public int readMedium() {
        checkReadableBytes(3);
        return buffer.readMedium();
    }

    public int readUnsignedMedium() {
        checkReadableBytes(3);
        return buffer.readUnsignedMedium();
    }

    public short readShort() {
        checkReadableBytes(2);
        return buffer.readShort();
    }

    public int readUnsignedShort() {
        checkReadableBytes(2);
        return buffer.readUnsignedShort();
    }

    public void resetReaderIndex() {
        buffer.resetReaderIndex();
    }

    public void resetWriterIndex() {
        throw new UnreplayableOperationException();
    }

    public void setByte(int index, byte value) {
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

    public void setShort(int index, short value) {
        throw new UnreplayableOperationException();
    }

    public int skipBytes(ChannelBufferIndexFinder firstIndexFinder) {
        int oldReaderIndex = buffer.readerIndex();
        int newReaderIndex = buffer.indexOf(oldReaderIndex, buffer.writerIndex(), firstIndexFinder);
        if (newReaderIndex < 0) {
            throw REPLAY;
        }
        buffer.readerIndex(newReaderIndex);
        return newReaderIndex - oldReaderIndex;
    }

    public void skipBytes(int length) {
        checkReadableBytes(length);
        buffer.skipBytes(length);
    }

    public ChannelBuffer slice() {
        throw new UnreplayableOperationException();
    }

    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
        return buffer.slice(index, length);
    }

    public ByteBuffer toByteBuffer() {
        throw new UnreplayableOperationException();
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        return buffer.toByteBuffer(index, length);
    }

    public ByteBuffer[] toByteBuffers() {
        throw new UnreplayableOperationException();
    }

    public ByteBuffer[] toByteBuffers(int index, int length) {
        checkIndex(index, length);
        return buffer.toByteBuffers(index, length);
    }

    public String toString(int index, int length, String charsetName) {
        checkIndex(index, length);
        return buffer.toString(index, length, charsetName);
    }

    public String toString(
            int index, int length, String charsetName,
            ChannelBufferIndexFinder terminatorFinder) {
        checkIndex(index, length);
        return buffer.toString(index, length, charsetName, terminatorFinder);
    }

    public String toString(String charsetName) {
        throw new UnreplayableOperationException();
    }

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

    public void writeByte(byte value) {
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
        return buffer.writerIndex();
    }

    public void writerIndex(int writerIndex) {
        throw new UnreplayableOperationException();
    }

    public void writeShort(short value) {
        throw new UnreplayableOperationException();
    }

    private void checkIndex(int index) {
        if (index > buffer.writerIndex()) {
            throw REPLAY;
        }
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
}