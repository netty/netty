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
public class HeapByteBuf extends AbstractByteBuf {

    private final Unsafe unsafe = new HeapUnsafe();

    private byte[] array;
    private ByteBuffer nioBuf;

    /**
     * Creates a new heap buffer with a newly allocated byte array.
     *
     * @param initialCapacity the initial capacity of the underlying byte array
     * @param maxCapacity the max capacity of the underlying byte array
     */
    public HeapByteBuf(int initialCapacity, int maxCapacity) {
        this(new byte[initialCapacity], 0, 0, maxCapacity);
    }

    /**
     * Creates a new heap buffer with an existing byte array.
     *
     * @param initialArray the initial underlying byte array
     * @param maxCapacity the max capacity of the underlying byte array
     */
    public HeapByteBuf(byte[] initialArray, int maxCapacity) {
        this(initialArray, 0, initialArray.length, maxCapacity);
    }

    private HeapByteBuf(byte[] initialArray, int readerIndex, int writerIndex, int maxCapacity) {
        super(ByteOrder.BIG_ENDIAN, maxCapacity);
        if (initialArray == null) {
            throw new NullPointerException("initialArray");
        }
        if (initialArray.length > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialArray.length, maxCapacity));
        }

        setArray(initialArray);
        setIndex(readerIndex, writerIndex);
    }

    private void setArray(byte[] initialArray) {
        array = initialArray;
        nioBuf = ByteBuffer.wrap(initialArray);
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
        return array;
    }

    @Override
    public int arrayOffset() {
        return 0;
    }

    @Override
    public byte getByte(int index) {
        return array[index];
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        if (dst instanceof HeapByteBuf) {
            getBytes(index, ((HeapByteBuf) dst).array, dstIndex, length);
        } else {
            dst.setBytes(dstIndex, array, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        dst.put(array, index, Math.min(capacity() - index, dst.remaining()));
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length)
            throws IOException {
        out.write(array, index, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return out.write((ByteBuffer) nioBuf.clear().position(index).limit(index + length));
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        array[index] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        if (src instanceof HeapByteBuf) {
            setBytes(index, ((HeapByteBuf) src).array, srcIndex, length);
        } else {
            src.getBytes(srcIndex, array, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        System.arraycopy(src, srcIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        src.get(array, index, src.remaining());
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return in.read(array, index, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        try {
            return in.read((ByteBuffer) nioBuf.clear().position(index).limit(index + length));
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
        return (short) (array[index] << 8 | array[index + 1] & 0xFF);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return  (array[index]     & 0xff) << 16 |
                (array[index + 1] & 0xff) <<  8 |
                 array[index + 2] & 0xff;
    }

    @Override
    public int getInt(int index) {
        return  (array[index]     & 0xff) << 24 |
                (array[index + 1] & 0xff) << 16 |
                (array[index + 2] & 0xff) <<  8 |
                 array[index + 3] & 0xff;
    }

    @Override
    public long getLong(int index) {
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
        array[index]     = (byte) (value >>> 8);
        array[index + 1] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int   value) {
        array[index]     = (byte) (value >>> 16);
        array[index + 1] = (byte) (value >>> 8);
        array[index + 2] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int   value) {
        array[index]     = (byte) (value >>> 24);
        array[index + 1] = (byte) (value >>> 16);
        array[index + 2] = (byte) (value >>> 8);
        array[index + 3] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long  value) {
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
        if (index < 0 || length < 0 || index + length > array.length) {
            throw new IndexOutOfBoundsException("Too many bytes to copy - Need "
                    + (index + length) + ", maximum is " + array.length);
        }

        byte[] copiedArray = new byte[length];
        System.arraycopy(array, index, copiedArray, 0, length);
        return new HeapByteBuf(copiedArray, maxCapacity());
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    private class HeapUnsafe implements Unsafe {
        @Override
        public ByteBuffer nioBuffer() {
            return nioBuf;
        }

        @Override
        public ByteBuffer[] nioBuffers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf newBuffer(int initialCapacity) {
            return new HeapByteBuf(initialCapacity, Math.max(initialCapacity, maxCapacity()));
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
        public void acquire() {
            if (refCnt <= 0) {
                throw new IllegalStateException();
            }
            refCnt ++;
        }

        @Override
        public void release() {
            if (refCnt <= 0) {
                throw new IllegalStateException();
            }
            refCnt --;
            if (refCnt == 0) {
                array = null;
                nioBuf = null;
            }
        }
    }
}
