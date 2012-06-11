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

    /**
     * The underlying heap byte array that this buffer is wrapping.
     */
    protected final byte[] array;

    protected final ByteBuffer nioBuf;

    /**
     * Creates a new heap buffer with a newly allocated byte array.
     *
     * @param length the length of the new byte array
     */
    public HeapByteBuf(int length) {
        this(new byte[length], 0, 0);
    }

    /**
     * Creates a new heap buffer with an existing byte array.
     *
     * @param array the byte array to wrap
     */
    public HeapByteBuf(byte[] array) {
        this(array, 0, array.length);
    }

    /**
     * Creates a new heap buffer with an existing byte array.
     *
     * @param array        the byte array to wrap
     * @param readerIndex  the initial reader index of this buffer
     * @param writerIndex  the initial writer index of this buffer
     */
    protected HeapByteBuf(byte[] array, int readerIndex, int writerIndex) {
        super(ByteOrder.BIG_ENDIAN);
        if (array == null) {
            throw new NullPointerException("array");
        }
        this.array = array;
        setIndex(readerIndex, writerIndex);
        nioBuf = ByteBuffer.wrap(array);
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
    public void getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        if (dst instanceof HeapByteBuf) {
            getBytes(index, ((HeapByteBuf) dst).array, dstIndex, length);
        } else {
            dst.setBytes(dstIndex, array, index, length);
        }
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        System.arraycopy(array, index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, ByteBuffer dst) {
        dst.put(array, index, Math.min(capacity() - index, dst.remaining()));
    }

    @Override
    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        out.write(array, index, length);
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return out.write((ByteBuffer) nioBuf.clear().position(index).limit(index + length));
    }

    @Override
    public void setByte(int index, int value) {
        array[index] = (byte) value;
    }

    @Override
    public void setBytes(int index, ByteBuf src, int srcIndex, int length) {
        if (src instanceof HeapByteBuf) {
            setBytes(index, ((HeapByteBuf) src).array, srcIndex, length);
        } else {
            src.getBytes(srcIndex, array, index, length);
        }
    }

    @Override
    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        System.arraycopy(src, srcIndex, array, index, length);
    }

    @Override
    public void setBytes(int index, ByteBuffer src) {
        src.get(array, index, src.remaining());
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
    public ByteBuf slice(int index, int length) {
        if (index == 0) {
            if (length == 0) {
                return Unpooled.EMPTY_BUFFER;
            }
            if (length == array.length) {
                ByteBuf slice = duplicate();
                slice.setIndex(0, length);
                return slice;
            } else {
                return new TruncatedByteBuf(this, length);
            }
        } else {
            if (length == 0) {
                return Unpooled.EMPTY_BUFFER;
            }
            return new SlicedByteBuf(this, index, length);
        }
    }

    @Override
    public boolean hasNioBuffer() {
        return true;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return ByteBuffer.wrap(array, index, length).order(order());
    }

    @Override
    public ByteBufFactory factory() {
        return HeapByteBufFactory.getInstance(ByteOrder.BIG_ENDIAN);
    }

    @Override
    public short getShort(int index) {
        return (short) (array[index] << 8 | array[index + 1] & 0xFF);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return  (array[index]     & 0xff) << 16 |
                (array[index + 1] & 0xff) <<  8 |
                (array[index + 2] & 0xff) <<  0;
    }

    @Override
    public int getInt(int index) {
        return  (array[index]     & 0xff) << 24 |
                (array[index + 1] & 0xff) << 16 |
                (array[index + 2] & 0xff) <<  8 |
                (array[index + 3] & 0xff) <<  0;
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
                ((long) array[index + 7] & 0xff) <<  0;
    }

    @Override
    public void setShort(int index, int value) {
        array[index]     = (byte) (value >>> 8);
        array[index + 1] = (byte) (value >>> 0);
    }

    @Override
    public void setMedium(int index, int   value) {
        array[index]     = (byte) (value >>> 16);
        array[index + 1] = (byte) (value >>> 8);
        array[index + 2] = (byte) (value >>> 0);
    }

    @Override
    public void setInt(int index, int   value) {
        array[index]     = (byte) (value >>> 24);
        array[index + 1] = (byte) (value >>> 16);
        array[index + 2] = (byte) (value >>> 8);
        array[index + 3] = (byte) (value >>> 0);
    }

    @Override
    public void setLong(int index, long  value) {
        array[index]     = (byte) (value >>> 56);
        array[index + 1] = (byte) (value >>> 48);
        array[index + 2] = (byte) (value >>> 40);
        array[index + 3] = (byte) (value >>> 32);
        array[index + 4] = (byte) (value >>> 24);
        array[index + 5] = (byte) (value >>> 16);
        array[index + 6] = (byte) (value >>> 8);
        array[index + 7] = (byte) (value >>> 0);
    }

    @Override
    public ByteBuf duplicate() {
        return new HeapByteBuf(array, readerIndex(), writerIndex());
    }

    @Override
    public ByteBuf copy(int index, int length) {
        if (index < 0 || length < 0 || index + length > array.length) {
            throw new IndexOutOfBoundsException("Too many bytes to copy - Need "
                    + (index + length) + ", maximum is " + array.length);
        }

        byte[] copiedArray = new byte[length];
        System.arraycopy(array, index, copiedArray, 0, length);
        return new HeapByteBuf(copiedArray);
    }
}
