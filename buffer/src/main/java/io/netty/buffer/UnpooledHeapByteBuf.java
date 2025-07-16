/*
 * Copyright 2012 The Netty Project
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

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Big endian Java heap buffer implementation. It is recommended to use
 * {@link UnpooledByteBufAllocator#heapBuffer(int, int)}, {@link Unpooled#buffer(int)} and
 * {@link Unpooled#wrappedBuffer(byte[])} instead of calling the constructor explicitly.
 */
public class UnpooledHeapByteBuf extends AbstractReferenceCountedByteBuf {

    private final ByteBufAllocator alloc;
    byte[] array;
    private ByteBuffer tmpNioBuf;

    /**
     * Creates a new heap buffer with a newly allocated byte array.
     *
     * @param initialCapacity the initial capacity of the underlying byte array
     * @param maxCapacity the max capacity of the underlying byte array
     */
    public UnpooledHeapByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(maxCapacity);

        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        this.alloc = checkNotNull(alloc, "alloc");
        setArray(allocateArray(initialCapacity));
        setIndex(0, 0);
    }

    /**
     * Creates a new heap buffer with an existing byte array.
     *
     * @param initialArray the initial underlying byte array
     * @param maxCapacity the max capacity of the underlying byte array
     */
    protected UnpooledHeapByteBuf(ByteBufAllocator alloc, byte[] initialArray, int maxCapacity) {
        super(maxCapacity);

        checkNotNull(alloc, "alloc");
        checkNotNull(initialArray, "initialArray");
        if (initialArray.length > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialArray.length, maxCapacity));
        }

        this.alloc = alloc;
        setArray(initialArray);
        setIndex(0, initialArray.length);
    }

    protected byte[] allocateArray(int initialCapacity) {
        return new byte[initialCapacity];
    }

    protected void freeArray(byte[] array) {
        // NOOP
    }

    private void setArray(byte[] initialArray) {
        array = initialArray;
        tmpNioBuf = null;
    }

    @Override
    public ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public int capacity() {
        return array.length;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        checkNewCapacity(newCapacity);
        byte[] oldArray = array;
        int oldCapacity = oldArray.length;
        if (newCapacity == oldCapacity) {
            return this;
        }

        int bytesToCopy;
        if (newCapacity > oldCapacity) {
            bytesToCopy = oldCapacity;
        } else {
            trimIndicesToCapacity(newCapacity);
            bytesToCopy = newCapacity;
        }
        byte[] newArray = allocateArray(newCapacity);
        System.arraycopy(oldArray, 0, newArray, 0, bytesToCopy);
        setArray(newArray);
        freeArray(oldArray);
        return this;
    }

    @Override
    public boolean hasArray() {
        return true;
    }

    @Override
    public byte[] array() {
        ensureAccessible();
        return array;
    }

    @Override
    public int arrayOffset() {
        return 0;
    }

    @Override
    public boolean hasMemoryAddress() {
        return false;
    }

    @Override
    public long memoryAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasMemoryAddress() && PlatformDependent.hasUnsafe()) {
            PlatformDependent.copyMemory(array, index, dst.memoryAddress() + dstIndex, length);
        } else if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, array, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        ensureAccessible();
        dst.put(array, index, dst.remaining());
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        ensureAccessible();
        out.write(array, index, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        ensureAccessible();
        return getBytes(index, out, length, false);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        ensureAccessible();
        return getBytes(index, out, position, length, false);
    }

    private int getBytes(int index, GatheringByteChannel out, int length, boolean internal) throws IOException {
        ensureAccessible();
        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = internalNioBuffer();
        } else {
            tmpBuf = ByteBuffer.wrap(array);
        }
        return out.write((ByteBuffer) tmpBuf.clear().position(index).limit(index + length));
    }

    private int getBytes(int index, FileChannel out, long position, int length, boolean internal) throws IOException {
        ensureAccessible();
        ByteBuffer tmpBuf = internal ? internalNioBuffer() : ByteBuffer.wrap(array);
        return out.write((ByteBuffer) tmpBuf.clear().position(index).limit(index + length), position);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = getBytes(readerIndex, out, length, true);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = getBytes(readerIndex, out, position, length, true);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        if (src.hasMemoryAddress() && PlatformDependent.hasUnsafe()) {
            PlatformDependent.copyMemory(src.memoryAddress() + srcIndex, array, index, length);
        } else  if (src.hasArray()) {
            setBytes(index, src.array(), src.arrayOffset() + srcIndex, length);
        } else {
            src.getBytes(srcIndex, array, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.length);
        System.arraycopy(src, srcIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        ensureAccessible();
        src.get(array, index, src.remaining());
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        ensureAccessible();
        return in.read(array, index, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        ensureAccessible();
        try {
            return in.read((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length));
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        ensureAccessible();
        try {
            return in.read((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length), position);
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        ensureAccessible();
        return ByteBuffer.wrap(array, index, length).slice();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return (ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length);
    }

    @Override
    public final boolean isContiguous() {
        return true;
    }

    @Override
    public byte readByte() {
        ensureAccessible();
        int i = readerIndex;
        byte b = _getByte(i);
        readerIndex = i + 1;
        return b;
    }

    @Override
    public byte getByte(int index) {
        ensureAccessible();
        return _getByte(index);
    }

    @Override
    protected byte _getByte(int index) {
        return HeapByteBufUtil.getByte(array, index);
    }

    @Override
    public short readShort() {
        ensureAccessible();
        int i = readerIndex;
        short s = _getShort(i);
        readerIndex = i + 2;
        return s;
    }

    @Override
    public short getShort(int index) {
        ensureAccessible();
        return _getShort(index);
    }

    @Override
    protected short _getShort(int index) {
        return HeapByteBufUtil.getShort(array, index);
    }

    @Override
    public short readShortLE() {
        ensureAccessible();
        int i = readerIndex;
        short s = _getShortLE(i);
        readerIndex = i + 2;
        return s;
    }

    @Override
    public short getShortLE(int index) {
        ensureAccessible();
        return _getShortLE(index);
    }

    @Override
    protected short _getShortLE(int index) {
        return HeapByteBufUtil.getShortLE(array, index);
    }

    @Override
    public int readUnsignedMedium() {
        ensureAccessible();
        int i = readerIndex;
        int m = _getUnsignedMedium(i);
        readerIndex = i + 3;
        return m;
    }

    @Override
    public int getUnsignedMedium(int index) {
        ensureAccessible();
        return _getUnsignedMedium(index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return HeapByteBufUtil.getUnsignedMedium(array, index);
    }

    @Override
    public int readUnsignedMediumLE() {
        ensureAccessible();
        int i = readerIndex;
        int m = _getUnsignedMediumLE(i);
        readerIndex = i + 3;
        return m;
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        ensureAccessible();
        return _getUnsignedMediumLE(index);
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return HeapByteBufUtil.getUnsignedMediumLE(array, index);
    }

    @Override
    public int readInt() {
        ensureAccessible();
        int i = readerIndex;
        int value = _getInt(i);
        readerIndex = i + 4;
        return value;
    }

    @Override
    public int getInt(int index) {
        ensureAccessible();
        return _getInt(index);
    }

    @Override
    protected int _getInt(int index) {
        return HeapByteBufUtil.getInt(array, index);
    }

    @Override
    public int readIntLE() {
        ensureAccessible();
        int i = readerIndex;
        int value = _getIntLE(i);
        readerIndex = i + 4;
        return value;
    }

    @Override
    public int getIntLE(int index) {
        ensureAccessible();
        return _getIntLE(index);
    }

    @Override
    protected int _getIntLE(int index) {
        return HeapByteBufUtil.getIntLE(array, index);
    }

    @Override
    public long readLong() {
        ensureAccessible();
        int i = readerIndex;
        long value = _getLong(i);
        readerIndex = i + 8;
        return value;
    }

    @Override
    public long getLong(int index) {
        ensureAccessible();
        return _getLong(index);
    }

    @Override
    protected long _getLong(int index) {
        return HeapByteBufUtil.getLong(array, index);
    }

    @Override
    public long readLongLE() {
        ensureAccessible();
        int i = readerIndex;
        long value = _getLongLE(i);
        readerIndex = i + 8;
        return value;
    }

    @Override
    public long getLongLE(int index) {
        ensureAccessible();
        return _getLongLE(index);
    }

    @Override
    protected long _getLongLE(int index) {
        return HeapByteBufUtil.getLongLE(array, index);
    }

    @Override
    public ByteBuf writeByte(int value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setByte(wIndex, value);
        writerIndex = wIndex + 1;
        return this;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        ensureAccessible();
        _setByte(index, value);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        HeapByteBufUtil.setByte(array, index, value);
    }

    @Override
    public ByteBuf writeShort(int value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setShort(wIndex, value);
        writerIndex = wIndex + 2;
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        ensureAccessible();
        _setShort(index, value);
        return this;
    }

    @Override
    protected void _setShort(int index, int value) {
        HeapByteBufUtil.setShort(array, index, value);
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setShortLE(wIndex, value);
        writerIndex = wIndex + 2;
        return this;
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        ensureAccessible();
        _setShortLE(index, value);
        return this;
    }

    @Override
    protected void _setShortLE(int index, int value) {
        HeapByteBufUtil.setShortLE(array, index, value);
    }

    @Override
    public ByteBuf writeMedium(int value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setMedium(wIndex, value);
        writerIndex = wIndex + 3;
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int   value) {
        ensureAccessible();
        _setMedium(index, value);
        return this;
    }

    @Override
    protected void _setMedium(int index, int value) {
        HeapByteBufUtil.setMedium(array, index, value);
    }

    @Override
    public ByteBuf writeMediumLE(int value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setMediumLE(wIndex, value);
        writerIndex = wIndex + 3;
        return this;
    }

    @Override
    public ByteBuf setMediumLE(int index, int   value) {
        ensureAccessible();
        _setMediumLE(index, value);
        return this;
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        HeapByteBufUtil.setMediumLE(array, index, value);
    }

    @Override
    public ByteBuf writeInt(int value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setInt(wIndex, value);
        writerIndex = wIndex + 4;
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int   value) {
        ensureAccessible();
        _setInt(index, value);
        return this;
    }

    @Override
    protected void _setInt(int index, int value) {
        HeapByteBufUtil.setInt(array, index, value);
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setIntLE(wIndex, value);
        writerIndex = wIndex + 4;
        return this;
    }

    @Override
    public ByteBuf setIntLE(int index, int   value) {
        ensureAccessible();
        _setIntLE(index, value);
        return this;
    }

    @Override
    protected void _setIntLE(int index, int value) {
        HeapByteBufUtil.setIntLE(array, index, value);
    }

    @Override
    public ByteBuf writeLong(long value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setLong(wIndex, value);
        writerIndex = wIndex + 8;
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long  value) {
        ensureAccessible();
        _setLong(index, value);
        return this;
    }

    @Override
    protected void _setLong(int index, long value) {
        HeapByteBufUtil.setLong(array, index, value);
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        ensureAccessible();
        int wIndex = writerIndex;
        _setLongLE(wIndex, value);
        writerIndex = wIndex + 8;
        return this;
    }

    @Override
    public ByteBuf setLongLE(int index, long  value) {
        ensureAccessible();
        _setLongLE(index, value);
        return this;
    }

    @Override
    protected void _setLongLE(int index, long value) {
        HeapByteBufUtil.setLongLE(array, index, value);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        return alloc().heapBuffer(length, maxCapacity()).writeBytes(array, index, length);
    }

    private ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = ByteBuffer.wrap(array);
        }
        return tmpNioBuf;
    }

    @Override
    protected void deallocate() {
        freeArray(array);
        array = EmptyArrays.EMPTY_BYTES;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }
}
