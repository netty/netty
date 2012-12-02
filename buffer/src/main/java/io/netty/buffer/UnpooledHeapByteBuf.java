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
package io.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * Big endian Java heap buffer implementation.
 */
public class UnpooledHeapByteBuf extends AbstractByteBuf {

    private final ByteBufAllocator alloc;
    private byte[] array;
    private ByteBuffer tmpNioBuf;
    private boolean freed;

    /**
     * Creates a new heap buffer with a newly allocated byte array.
     *
     * @param initialCapacity the initial capacity of the underlying byte array
     * @param maxCapacity the max capacity of the underlying byte array
     */
    public UnpooledHeapByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        this(alloc, new byte[initialCapacity], 0, 0, maxCapacity);
    }

    /**
     * Creates a new heap buffer with an existing byte array.
     *
     * @param initialArray the initial underlying byte array
     * @param maxCapacity the max capacity of the underlying byte array
     */
    public UnpooledHeapByteBuf(ByteBufAllocator alloc, byte[] initialArray, int maxCapacity) {
        this(alloc, initialArray, 0, initialArray.length, maxCapacity);
    }

    private UnpooledHeapByteBuf(
            ByteBufAllocator alloc, byte[] initialArray, int readerIndex, int writerIndex, int maxCapacity) {

        super(maxCapacity);

        if (alloc == null) {
            throw new NullPointerException("alloc");
        }
        if (initialArray == null) {
            throw new NullPointerException("initialArray");
        }
        if (initialArray.length > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialArray.length, maxCapacity));
        }

        this.alloc = alloc;
        setArray(initialArray);
        setIndex(readerIndex, writerIndex);
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
        assert !freed;
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        int oldCapacity = array.length;
        if (newCapacity > oldCapacity) {
            byte[] newArray = new byte[newCapacity];
            System.arraycopy(array, readerIndex(), newArray, readerIndex(), readableBytes());
            setArray(newArray);
        } else if (newCapacity < oldCapacity) {
            byte[] newArray = new byte[newCapacity];
            int readerIndex = readerIndex();
            if (readerIndex < newCapacity) {
                int writerIndex = writerIndex();
                if (writerIndex > newCapacity) {
                    writerIndex(writerIndex = newCapacity);
                }
                System.arraycopy(array, readerIndex, newArray, readerIndex, writerIndex - readerIndex);
            } else {
                setIndex(newCapacity, newCapacity);
            }
            setArray(newArray);
        }
        return this;
    }

    @Override
    public boolean hasArray() {
        return true;
    }

    @Override
    public byte[] array() {
        assert !freed;
        return array;
    }

    @Override
    public int arrayOffset() {
        return 0;
    }

    @Override
    public byte getByte(int index) {
        assert !freed;
        return array[index];
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        assert !freed;
        if (dst instanceof UnpooledHeapByteBuf) {
            getBytes(index, ((UnpooledHeapByteBuf) dst).array, dstIndex, length);
        } else {
            dst.setBytes(dstIndex, array, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        assert !freed;
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        assert !freed;
        dst.put(array, index, Math.min(capacity() - index, dst.remaining()));
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        assert !freed;
        out.write(array, index, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        assert !freed;
        return out.write((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length));
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        assert !freed;
        array[index] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        assert !freed;
        if (src instanceof UnpooledHeapByteBuf) {
            setBytes(index, ((UnpooledHeapByteBuf) src).array, srcIndex, length);
        } else {
            src.getBytes(srcIndex, array, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        assert !freed;
        System.arraycopy(src, srcIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        assert !freed;
        src.get(array, index, src.remaining());
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        assert !freed;
        return in.read(array, index, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        assert !freed;
        try {
            return in.read((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length));
        } catch (ClosedChannelException e) {
            return -1;
        }
    }

    @Override
    public boolean hasNioBuffer() {
        return true;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        assert !freed;
        return ByteBuffer.wrap(array, index, length);
    }

    @Override
    public boolean hasNioBuffers() {
        return false;
    }

    @Override
    public ByteBuffer[] nioBuffers(int offset, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public short getShort(int index) {
        assert !freed;
        return (short) (array[index] << 8 | array[index + 1] & 0xFF);
    }

    @Override
    public int getUnsignedMedium(int index) {
        assert !freed;
        return  (array[index]     & 0xff) << 16 |
                (array[index + 1] & 0xff) <<  8 |
                 array[index + 2] & 0xff;
    }

    @Override
    public int getInt(int index) {
        assert !freed;
        return  (array[index]     & 0xff) << 24 |
                (array[index + 1] & 0xff) << 16 |
                (array[index + 2] & 0xff) <<  8 |
                 array[index + 3] & 0xff;
    }

    @Override
    public long getLong(int index) {
        assert !freed;
        return  ((long) array[index]     & 0xff) << 56 |
                ((long) array[index + 1] & 0xff) << 48 |
                ((long) array[index + 2] & 0xff) << 40 |
                ((long) array[index + 3] & 0xff) << 32 |
                ((long) array[index + 4] & 0xff) << 24 |
                ((long) array[index + 5] & 0xff) << 16 |
                ((long) array[index + 6] & 0xff) <<  8 |
                 (long) array[index + 7] & 0xff;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        assert !freed;
        array[index]     = (byte) (value >>> 8);
        array[index + 1] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int   value) {
        assert !freed;
        array[index]     = (byte) (value >>> 16);
        array[index + 1] = (byte) (value >>> 8);
        array[index + 2] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int   value) {
        assert !freed;
        array[index]     = (byte) (value >>> 24);
        array[index + 1] = (byte) (value >>> 16);
        array[index + 2] = (byte) (value >>> 8);
        array[index + 3] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long  value) {
        assert !freed;
        array[index]     = (byte) (value >>> 56);
        array[index + 1] = (byte) (value >>> 48);
        array[index + 2] = (byte) (value >>> 40);
        array[index + 3] = (byte) (value >>> 32);
        array[index + 4] = (byte) (value >>> 24);
        array[index + 5] = (byte) (value >>> 16);
        array[index + 6] = (byte) (value >>> 8);
        array[index + 7] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        assert !freed;
        if (index < 0 || length < 0 || index + length > array.length) {
            throw new IndexOutOfBoundsException("Too many bytes to copy - Need "
                    + (index + length) + ", maximum is " + array.length);
        }

        byte[] copiedArray = new byte[length];
        System.arraycopy(array, index, copiedArray, 0, length);
        return new UnpooledHeapByteBuf(alloc(), copiedArray, maxCapacity());
    }

    @Override
    public ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = ByteBuffer.wrap(array);
        }
        return tmpNioBuf;
    }

    @Override
    public ByteBuffer[] internalNioBuffers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void discardSomeReadBytes() {
        final int readerIndex = readerIndex();
        if (readerIndex == writerIndex()) {
            discardReadBytes();
            return;
        }

        if (readerIndex > 0 && readerIndex >= capacity() >>> 1) {
            discardReadBytes();
        }
    }

    @Override
    public boolean isFreed() {
        return freed;
    }

    @Override
    public void free() {
        freed = true;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }
}
