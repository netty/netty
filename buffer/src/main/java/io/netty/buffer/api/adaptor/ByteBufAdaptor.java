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
package io.netty.buffer.api.adaptor;

import io.netty.buffer.ByteBufConvertible;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.DuplicatedByteBuf;
import io.netty.buffer.SlicedByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.BufferClosedException;
import io.netty.buffer.api.BufferReadOnlyException;
import io.netty.buffer.api.internal.Statics;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.internal.ResourceSupport;
import io.netty.util.ByteProcessor;
import io.netty.util.IllegalReferenceCountException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.api.internal.Statics.acquire;
import static io.netty.buffer.api.internal.Statics.isOwned;

public final class ByteBufAdaptor extends ByteBuf {
    private final ByteBufAllocatorAdaptor alloc;
    private final Buffer buffer;
    private final boolean hasMemoryAddress;

    public ByteBufAdaptor(ByteBufAllocatorAdaptor alloc, Buffer buffer) {
        this.alloc = alloc;
        this.buffer = buffer;
        hasMemoryAddress = buffer.nativeAddress() != 0;
    }

    /**
     * Extracts the underlying {@link Buffer} instance that is backing this {@link ByteBuf}, if any.
     * This is similar to {@link #unwrap()} except the return type is a {@link Buffer}.
     * If this {@link ByteBuf} does not wrap a {@link Buffer}, then {@code null} is returned.
     *
     * @param byteBuf The {@link ByteBuf} to extract the {@link Buffer} from.
     * @return The {@link Buffer} instance that is backing the given {@link ByteBuf}, or {@code null} if the given
     * {@link ByteBuf} is not backed by a {@link Buffer}.
     */
    public static Buffer extract(ByteBuf byteBuf) {
        if (byteBuf instanceof ByteBufAdaptor) {
            ByteBufAdaptor bba = (ByteBufAdaptor) byteBuf;
            return bba.buffer;
        }
        return null;
    }

    @Override
    public int capacity() {
        return buffer.capacity();
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        int diff = newCapacity - capacity() - buffer.writableBytes();
        if (diff > 0) {
            try {
                buffer.ensureWritable(diff);
            } catch (IllegalStateException e) {
                if (!isOwned((ResourceSupport<?, ?>) buffer)) {
                    throw new UnsupportedOperationException(e);
                }
                throw e;
            }
        }
        return this;
    }

    @Override
    public int maxCapacity() {
        return capacity();
    }

    @Override
    public ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public ByteOrder order() {
        return buffer.order();
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        buffer.order(endianness);
        return this;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }

    @Override
    public boolean isDirect() {
        return hasMemoryAddress;
    }

    @Override
    public boolean isReadOnly() {
        return buffer.readOnly();
    }

    @Override
    public ByteBuf asReadOnly() {
        return Unpooled.unreleasableBuffer(this);
    }

    @Override
    public int readerIndex() {
        return buffer.readerOffset();
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        buffer.readerOffset(readerIndex);
        return this;
    }

    @Override
    public int writerIndex() {
        return buffer.writerOffset();
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        buffer.writerOffset(writerIndex);
        return this;
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        buffer.reset().writerOffset(writerIndex).readerOffset(readerIndex);
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
        return writableBytes();
    }

    @Override
    public boolean isReadable() {
        return readableBytes() > 0;
    }

    @Override
    public boolean isReadable(int size) {
        return readableBytes() >= size;
    }

    @Override
    public boolean isWritable() {
        return writableBytes() > 0;
    }

    @Override
    public boolean isWritable(int size) {
        return writableBytes() >= size;
    }

    @Override
    public ByteBuf clear() {
        return setIndex(0, 0);
    }

    @Override
    public ByteBuf discardReadBytes() {
        checkAccess();
        buffer.compact();
        return this;
    }

    @Override
    public ByteBuf discardSomeReadBytes() {
        return discardReadBytes();
    }

    @Override
    public ByteBuf ensureWritable(int minWritableBytes) {
        checkAccess();
        if (writableBytes() < minWritableBytes) {
            try {
                if (isOwned((ResourceSupport<?, ?>) buffer)) {
                    // Good place.
                    buffer.ensureWritable(minWritableBytes);
                } else {
                    // Highly questionable place, but ByteBuf technically allows this, so we have to emulate.
                    int borrows = countBorrows();
                    release(borrows);
                    try {
                        buffer.ensureWritable(minWritableBytes);
                    } finally {
                        retain(borrows);
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new IndexOutOfBoundsException(e.getMessage());
            }
        }
        return this;
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        ensureWritable(minWritableBytes);
        return minWritableBytes;
    }

    @Override
    public boolean getBoolean(int index) {
        return getByte(index) != 0;
    }

    @Override
    public byte getByte(int index) {
        try {
            return buffer.getByte(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public short getUnsignedByte(int index) {
        try {
            return (short) buffer.getUnsignedByte(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public short getShort(int index) {
        try {
            return buffer.getShort(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public short getShortLE(int index) {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).getShort(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public int getUnsignedShort(int index) {
        try {
            return buffer.getUnsignedShort(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public int getUnsignedShortLE(int index) {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).getUnsignedShort(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public int getMedium(int index) {
        try {
            return buffer.getMedium(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public int getMediumLE(int index) {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).getMedium(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public int getUnsignedMedium(int index) {
        try {
            return buffer.getUnsignedMedium(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).getUnsignedMedium(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public int getInt(int index) {
        try {
            return buffer.getInt(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public int getIntLE(int index) {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).getInt(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public long getUnsignedInt(int index) {
        try {
            return buffer.getUnsignedInt(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public long getUnsignedIntLE(int index) {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).getUnsignedInt(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public long getLong(int index) {
        try {
            return buffer.getLong(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public long getLongLE(int index) {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).getLong(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public char getChar(int index) {
        try {
            return buffer.getChar(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public float getFloat(int index) {
        try {
            return buffer.getFloat(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public double getDouble(int index) {
        try {
            return buffer.getDouble(index);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        while (dst.isWritable()) {
            dst.writeByte(getByte(index++));
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        for (int i = 0; i < length; i++) {
            dst.writeByte(getByte(index + i));
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        for (int i = 0; i < length; i++) {
            dst.setByte(dstIndex + i, getByte(index + i));
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        return getBytes(index, dst, 0, dst.length);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkAccess();
        if (index < 0 || capacity() < index + length || dst.length < dstIndex + length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = 0; i < length; i++) {
            dst[dstIndex + i] = getByte(index + i);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkAccess();
        if (index < 0 || capacity() < index + dst.remaining()) {
            throw new IndexOutOfBoundsException();
        }
        while (dst.hasRemaining()) {
            dst.put(getByte(index));
            index++;
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        for (int i = 0; i < length; i++) {
            out.write(getByte(index + i));
        }
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        checkAccess();
        ByteBuffer transfer = ByteBuffer.allocate(length);
        buffer.copyInto(index, transfer, 0, length);
        return out.write(transfer);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        checkAccess();
        ByteBuffer transfer = ByteBuffer.allocate(length);
        buffer.copyInto(index, transfer, 0, length);
        return out.write(transfer, position);
    }

    @Override
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        byte[] bytes = new byte[length];
        getBytes(index, bytes);
        return new String(bytes, charset);
    }

    @Override
    public ByteBuf setBoolean(int index, boolean value) {
        return setByte(index, value? 1 : 0);
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        try {
            buffer.setByte(index, (byte) value);
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        try {
            buffer.setShort(index, (short) value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN).setShort(index, (short) value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        try {
            buffer.setMedium(index, value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN).setMedium(index, value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        try {
            buffer.setInt(index, value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN).setInt(index, value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        try {
            buffer.setLong(index, value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN).setLong(index, value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public ByteBuf setChar(int index, int value) {
        try {
            buffer.setChar(index, (char) value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf setFloat(int index, float value) {
        try {
            buffer.setFloat(index, value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf setDouble(int index, double value) {
        try {
            buffer.setDouble(index, value);
            return this;
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        checkAccess();
        while (src.isReadable() && index < capacity()) {
            setByte(index++, src.readByte());
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        checkAccess();
        for (int i = 0; i < length; i++) {
            setByte(index + i, src.readByte());
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        for (int i = 0; i < length; i++) {
            setByte(index + i, src.getByte(srcIndex + i));
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        return setBytes(index, src, 0, src.length);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        for (int i = 0; i < length; i++) {
            setByte(index + i, src[srcIndex + i]);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        while (src.hasRemaining()) {
            setByte(index, src.get());
            index++;
        }
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkAccess();
        byte[] bytes = in.readNBytes(length);
        setBytes(index, bytes, 0, length);
        return bytes.length;
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        checkAccess();
        ByteBuffer transfer = ByteBuffer.allocate(length);
        int bytes = in.read(transfer);
        transfer.flip();
        setBytes(index, transfer);
        return bytes;
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        checkAccess();
        ByteBuffer transfer = ByteBuffer.allocate(length);
        int bytes = in.read(transfer, position);
        transfer.flip();
        setBytes(index, transfer);
        return bytes;
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        for (int i = 0; i < length; i++) {
            setByte(index + i, 0);
        }
        return this;
    }

    @Override
    public int setCharSequence(int index, CharSequence sequence, Charset charset) {
        byte[] bytes = sequence.toString().getBytes(charset);
        for (int i = 0; i < bytes.length; i++) {
            setByte(index + i, bytes[i]);
        }
        return bytes.length;
    }

    @Override
    public boolean readBoolean() {
        return readByte() != 0;
    }

    @Override
    public byte readByte() {
        try {
            return buffer.readByte();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public short readUnsignedByte() {
        try {
            return (short) buffer.readUnsignedByte();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public short readShort() {
        try {
            return buffer.readShort();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public short readShortLE() {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).readShort();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public int readUnsignedShort() {
        try {
            return buffer.readUnsignedShort();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public int readUnsignedShortLE() {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).readUnsignedShort();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public int readMedium() {
        try {
            return buffer.readMedium();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public int readMediumLE() {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).readMedium();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public int readUnsignedMedium() {
        try {
            return buffer.readUnsignedMedium();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public int readUnsignedMediumLE() {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).readUnsignedMedium();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public int readInt() {
        try {
            return buffer.readInt();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public int readIntLE() {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).readInt();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public long readUnsignedInt() {
        try {
            return buffer.readUnsignedInt();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public long readUnsignedIntLE() {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).readUnsignedInt();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public long readLong() {
        try {
            return buffer.readLong();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public long readLongLE() {
        ByteOrder originalOrder = buffer.order();
        try {
            return buffer.order(ByteOrder.LITTLE_ENDIAN).readLong();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public char readChar() {
        try {
            return buffer.readChar();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public float readFloat() {
        try {
            return buffer.readFloat();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public double readDouble() {
        try {
            return buffer.readDouble();
        } catch (IllegalStateException | BufferClosedException e) {
            throw new IllegalReferenceCountException(e);
        }
    }

    @Override
    public ByteBuf readBytes(int length) {
        checkAccess();
        Buffer copy = preferredBufferAllocator().allocate(length);
        buffer.copyInto(readerIndex(), copy, 0, length);
        readerIndex(readerIndex() + length);
        return wrap(copy).writerIndex(length);
    }

    @Override
    public ByteBuf readSlice(int length) {
        ByteBuf slice = slice(readerIndex(), length);
        buffer.readerOffset(buffer.readerOffset() + length);
        return slice;
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        ByteBuf slice = retainedSlice(readerIndex(), length);
        buffer.readerOffset(buffer.readerOffset() + length);
        return slice;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        while (dst.isWritable()) {
            dst.writeByte(readByte());
        }
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        for (int i = 0; i < length; i++) {
            dst.writeByte(readByte());
        }
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        for (int i = 0; i < length; i++) {
            dst.setByte(dstIndex + i, readByte());
        }
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        return readBytes(dst, 0, dst.length);
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        for (int i = 0; i < length; i++) {
            dst[dstIndex + i] = readByte();
        }
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        while (dst.hasRemaining()) {
            dst.put(readByte());
        }
        return this;
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        for (int i = 0; i < length; i++) {
            out.write(readByte());
        }
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkAccess();
        ByteBuffer[] components = new ByteBuffer[buffer.countReadableComponents()];
        buffer.forEachReadable(0, (i, component) -> {
            components[i] = component.readableBuffer();
            return true;
        });
        int written = (int) out.write(components);
        skipBytes(written);
        return written;
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        byte[] bytes = new byte[length];
        readBytes(bytes);
        return new String(bytes, charset);
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        ByteBuffer[] components = new ByteBuffer[buffer.countReadableComponents()];
        buffer.forEachReadable(0, (i, component) -> {
            components[i] = component.readableBuffer();
            return true;
        });
        int written = 0;
        for (ByteBuffer component : components) {
            written += out.write(component, position + written);
            if (component.hasRemaining()) {
                break;
            }
        }
        skipBytes(written);
        return written;
    }

    @Override
    public ByteBuf skipBytes(int length) {
        buffer.readerOffset(length + buffer.readerOffset());
        return this;
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        return writeByte(value? 1 : 0);
    }

    @Override
    public ByteBuf writeByte(int value) {
        ensureWritable(1);
        buffer.writeByte((byte) value);
        return this;
    }

    @Override
    public ByteBuf writeShort(int value) {
        ensureWritable(2);
        buffer.writeShort((short) value);
        return this;
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        ensureWritable(2);
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN).writeShort((short) value);
            return this;
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public ByteBuf writeMedium(int value) {
        ensureWritable(3);
        buffer.writeMedium(value);
        return this;
    }

    @Override
    public ByteBuf writeMediumLE(int value) {
        ensureWritable(3);
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN).writeMedium(value);
            return this;
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public ByteBuf writeInt(int value) {
        ensureWritable(4);
        buffer.writeInt(value);
        return this;
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        ensureWritable(4);
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN).writeInt(value);
            return this;
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public ByteBuf writeLong(long value) {
        ensureWritable(8);
        buffer.writeLong(value);
        return this;
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        ensureWritable(8);
        ByteOrder originalOrder = buffer.order();
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN).writeLong(value);
            return this;
        } finally {
            buffer.order(originalOrder);
        }
    }

    @Override
    public ByteBuf writeChar(int value) {
        ensureWritable(2);
        buffer.writeChar((char) value);
        return this;
    }

    @Override
    public ByteBuf writeFloat(float value) {
        ensureWritable(4);
        buffer.writeFloat(value);
        return this;
    }

    @Override
    public ByteBuf writeDouble(double value) {
        ensureWritable(8);
        buffer.writeDouble(value);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        return writeBytes(src, src.readableBytes());
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        ensureWritable(length);
        for (int i = 0; i < length; i++) {
            writeByte(src.readByte());
        }
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        ensureWritable(length);
        for (int i = 0; i < length; i++) {
            writeByte(src.getByte(srcIndex + i));
        }
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        ensureWritable(src.length);
        for (byte b : src) {
            writeByte(b);
        }
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        ensureWritable(length);
        for (int i = 0; i < length; i++) {
            writeByte(src[srcIndex + i]);
        }
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        ensureWritable(src.remaining());
        while (src.hasRemaining()) {
            writeByte(src.get());
        }
        return this;
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        byte[] bytes = in.readNBytes(length);
        writeBytes(bytes);
        return bytes.length;
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        ensureWritable(length);
        ByteBuffer[] components = new ByteBuffer[buffer.countWritableComponents()];
        buffer.forEachWritable(0, (i, component) -> {
            components[i] = component.writableBuffer();
            return true;
        });

        int read = (int) in.read(components);

        if (read > 0) {
            writerIndex(read + writerIndex());
        }
        return read;
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        ensureWritable(length);
        ByteBuffer[] components = new ByteBuffer[buffer.countWritableComponents()];
        buffer.forEachWritable(0, (i, component) -> {
            components[i] = component.writableBuffer();
            return true;
        });
        int read = 0;
        for (ByteBuffer component : components) {
            int r = in.read(component, position + read);
            if (r > 0) {
                read += r;
            }
            if (component.hasRemaining()) {
                break;
            }
        }
        writerIndex(read + writerIndex());
        return read;
    }

    @Override
    public ByteBuf writeZero(int length) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        ensureWritable(length);
        for (int i = 0; i < length; i++) {
            writeByte(0);
        }
        return this;
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        byte[] bytes = sequence.toString().getBytes(charset);
        writeBytes(bytes);
        return bytes.length;
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        if (!buffer.isAccessible()) {
            return -1;
        }
        if (fromIndex <= toIndex) {
            if (fromIndex < 0) {
                fromIndex = 0; // Required to pass regression tests.
            }
            if (capacity() < toIndex) {
                throw new IndexOutOfBoundsException();
            }
            for (; fromIndex < toIndex; fromIndex++) {
                if (getByte(fromIndex) == value) {
                    return fromIndex;
                }
            }
        } else {
            if (capacity() < fromIndex) {
                fromIndex = capacity(); // Required to pass regression tests.
            }
            fromIndex--;
            if (toIndex < 0) {
                throw new IndexOutOfBoundsException();
            }
            for (; fromIndex > toIndex; fromIndex--) {
                if (getByte(fromIndex) == value) {
                    return fromIndex;
                }
            }
        }
        return -1;
    }

    @Override
    public int bytesBefore(byte value) {
        return bytesBefore(readerIndex(), writerIndex(), value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return bytesBefore(readerIndex(), readerIndex() + length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        int i = indexOf(index, index + length, value);
        if (i != -1) {
            i -= index;
        }
        return i;
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        checkAccess();
        int index = readerIndex();
        int bytes = buffer.openCursor().process(processor);
        return bytes == -1 ? -1 : index + bytes;
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        checkAccess();
        int bytes = buffer.openCursor(index, length).process(processor);
        return bytes == -1 ? -1 : index + bytes;
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        checkAccess();
        int index = readerIndex();
        int bytes = buffer.openReverseCursor().process(processor);
        return bytes == -1 ? -1 : index - bytes;
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        checkAccess();
        int bytes = buffer.openReverseCursor(index + length - 1, length).process(processor);
        return bytes == -1 ? -1 : index - bytes;
    }

    @Override
    public ByteBuf copy() {
        return copy(readerIndex(), readableBytes());
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkAccess();
        try {
            BufferAllocator allocator = preferredBufferAllocator();
            Buffer copy = allocator.allocate(length);
            buffer.copyInto(index, copy, 0, length);
            copy.order(buffer.order());
            copy.writerOffset(length);
            return wrap(copy);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException(e.getMessage());
        }
    }

    @Override
    public ByteBuf slice() {
        return slice(readerIndex(), readableBytes());
    }

    @Override
    public ByteBuf retainedSlice() {
        return retainedSlice(readerIndex(), readableBytes());
    }

    @Override
    public ByteBuf slice(int index, int length) {
        checkAccess();
        return new Slice(this, index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        checkAccess();
        Slice slice = new Slice(this, index, length);
        retain();
        return slice;
    }

    private static final class Slice extends SlicedByteBuf {
        private final int indexAdjustment;
        private final int lengthAdjustment;

        Slice(ByteBuf buffer, int index, int length) {
            super(buffer, index, length);
            indexAdjustment = index;
            lengthAdjustment = length;
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return new Slice(unwrap().retainedDuplicate(), indexAdjustment, lengthAdjustment)
                    .setIndex(readerIndex(), writerIndex());
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            checkIndex(index, length);
            return unwrap().retainedSlice(indexAdjustment + index, length);
        }
    }

    private static final class Duplicate extends DuplicatedByteBuf {
        Duplicate(ByteBufAdaptor byteBuf) {
            super(byteBuf);
        }

        @Override
        public ByteBuf duplicate() {
            ((ByteBufAdaptor) unwrap()).checkAccess();
            return new Duplicate((ByteBufAdaptor) unwrap())
                    .setIndex(readerIndex(), writerIndex());
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return unwrap().retainedDuplicate();
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return unwrap().retainedSlice(index, length);
        }
    }

    @Override
    public ByteBuf duplicate() {
        checkAccess();
        Duplicate duplicatedByteBuf = new Duplicate(this);
        return duplicatedByteBuf.setIndex(readerIndex(), writerIndex());
    }

    @Override
    public ByteBuf retainedDuplicate() {
        checkAccess();
        retain();
        Duplicate duplicatedByteBuf = new Duplicate(this);
        return duplicatedByteBuf.setIndex(readerIndex(), writerIndex());
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer nioBuffer() {
        return nioBuffer(readerIndex(), readableBytes());
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkAccess();
        ByteBuffer copy = isDirect() ? ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
        while (index < length) {
            copy.put(getByte(index++));
        }
        return copy.flip();
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        checkAccess();
        if (readerIndex() <= index && index < writerIndex() && length <= readableBytes()) {
            // We wish to read from the internal buffer.
            if (buffer.countReadableComponents() != 1) {
                throw new UnsupportedOperationException(
                        "Unsupported number of readable components: " + buffer.countReadableComponents() + '.');
            }
            AtomicReference<ByteBuffer> bufRef = new AtomicReference<>();
            buffer.forEachReadable(0, (i, component) -> {
                bufRef.set(component.readableBuffer());
                return false;
            });
            ByteBuffer buffer = bufRef.get();
            if (index != readerIndex() || length != readableBytes()) {
                buffer = Statics.bbslice(buffer, index - readerIndex(), length);
            }
            return buffer;
        } else if (writerIndex() <= index && length <= writableBytes()) {
            // We wish to write to the internal buffer.
            if (buffer.countWritableComponents() != 1) {
                throw new UnsupportedOperationException(
                        "Unsupported number of writable components: " + buffer.countWritableComponents() + '.');
            }
            AtomicReference<ByteBuffer> bufRef = new AtomicReference<>();
            buffer.forEachWritable(0, (i, component) -> {
                bufRef.set(component.writableBuffer());
                return false;
            });
            ByteBuffer buffer = bufRef.get();
            if (index != writerIndex() || length != writableBytes()) {
                buffer = Statics.bbslice(buffer, index - writerIndex(), length);
            }
            return buffer;
        } else {
            String message = "Cannot provide internal NIO buffer for range from " + index + " for length " + length +
                    ", when writerIndex() is " + writerIndex() + " and writable bytes are " + writableBytes() +
                    ", and readerIndex() is " + readerIndex() + " and readable bytes are " + readableBytes() +
                    ". The requested range must fall within EITHER the readable area OR the writable area. " +
                    "Straddling the two areas, or reaching outside of their bounds, is not allowed.";
            throw new UnsupportedOperationException(message);
        }
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return new ByteBuffer[] { nioBuffer() };
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { internalNioBuffer(index, length) };
    }

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        throw new UnsupportedOperationException("This buffer has no array.");
    }

    @Override
    public int arrayOffset() {
        throw new UnsupportedOperationException("This buffer has no array.");
    }

    @Override
    public boolean hasMemoryAddress() {
        return hasMemoryAddress;
    }

    @Override
    public long memoryAddress() {
        if (!hasMemoryAddress()) {
            throw new UnsupportedOperationException("No memory address associated with this buffer.");
        }
        return buffer.nativeAddress();
    }

    @Override
    public String toString(Charset charset) {
        return toString(readerIndex(), readableBytes(), charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        byte[] bytes = new byte[length];
        getBytes(index, bytes);
        return new String(bytes, charset);
    }

    @Override
    public int hashCode() {
        return ByteBufUtil.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ByteBufConvertible) {
            ByteBuf other = ((ByteBufConvertible) obj).asByteBuf();
            return this == other || ByteBufUtil.equals(this, other);
        }
        return false;
    }

    @Override
    public int compareTo(ByteBuf buffer) {
        ByteOrder orderThis = order();
        ByteOrder orderThat = buffer.order();
        try {
            // Little-ending implementation of the compare seems to be broken.
            return ByteBufUtil.compare(order(ByteOrder.BIG_ENDIAN), buffer.order(ByteOrder.BIG_ENDIAN));
        } finally {
            order(orderThis);
            buffer.order(orderThat);
        }
    }

    @Override
    public String toString() {
        return "ByteBuf(" + readerIndex() + ", " + writerIndex() + ", " + capacity() + ')';
    }

    @Override
    public ByteBuf retain(int increment) {
        for (int i = 0; i < increment; i++) {
            acquire((ResourceSupport<?, ?>) buffer);
        }
        return this;
    }

    @Override
    public int refCnt() {
        return 1 + countBorrows();
    }

    private int countBorrows() {
        if (!buffer.isAccessible()) {
            return -1;
        }
        if (buffer instanceof ResourceSupport) {
            var rc = (ResourceSupport<?, ?>) buffer;
            return Statics.countBorrows(rc);
        }
        return isOwned((ResourceSupport<?, ?>) buffer)? 0 : 1;
    }

    @Override
    public ByteBuf retain() {
        return retain(1);
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return release(1);
    }

    @Override
    public boolean release(int decrement) {
        int refCount = 1 + Statics.countBorrows((ResourceSupport<?, ?>) buffer);
        if (!buffer.isAccessible() || decrement > refCount) {
            throw new IllegalReferenceCountException(refCount, -decrement);
        }
        for (int i = 0; i < decrement; i++) {
            try {
                buffer.close();
            } catch (RuntimeException e) {
                throw new IllegalReferenceCountException(e);
            }
        }
        return !buffer.isAccessible();
    }

    private void checkAccess() {
        if (!buffer.isAccessible()) {
            throw new IllegalReferenceCountException();
        }
    }

    private ByteBufAdaptor wrap(Buffer copy) {
        return new ByteBufAdaptor(alloc, copy);
    }

    private BufferAllocator preferredBufferAllocator() {
        return isDirect()? alloc.getOffHeap() : alloc.getOnHeap();
    }
}
