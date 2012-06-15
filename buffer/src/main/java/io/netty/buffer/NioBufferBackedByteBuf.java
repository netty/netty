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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * A NIO {@link ByteBuffer} based buffer.  It is recommended to use {@link Unpooled#directBuffer(int)}
 * and {@link Unpooled#wrappedBuffer(ByteBuffer)} instead of calling the
 * constructor explicitly.
 */
public class NioBufferBackedByteBuf extends AbstractByteBuf {

    private final ByteBuffer buffer;
    private final ByteBuffer tmpBuf;
    private final int capacity;

    /**
     * Creates a new buffer which wraps the specified buffer's slice.
     */
    public NioBufferBackedByteBuf(ByteBuffer buffer) {
        super(buffer.order());
        this.buffer = buffer.slice().order(order());
        tmpBuf = this.buffer.duplicate();
        capacity = buffer.remaining();
        writerIndex(capacity);
    }

    private NioBufferBackedByteBuf(NioBufferBackedByteBuf buffer) {
        super(buffer.order());
        this.buffer = buffer.buffer;
        tmpBuf = this.buffer.duplicate();
        capacity = buffer.capacity;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    @Override
    public ByteBufFactory factory() {
        if (buffer.isDirect()) {
            return DirectByteBufFactory.getInstance(order());
        } else {
            return HeapByteBufFactory.getInstance(order());
        }
    }

    @Override
    public boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
    public int capacity() {
        return capacity;
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
    public byte getByte(int index) {
        return buffer.get(index);
    }

    @Override
    public short getShort(int index) {
        return buffer.getShort(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return  (getByte(index)     & 0xff) << 16 |
                (getByte(index + 1) & 0xff) <<  8 |
                (getByte(index + 2) & 0xff) <<  0;
    }

    @Override
    public int getInt(int index) {
        return buffer.getInt(index);
    }

    @Override
    public long getLong(int index) {
        return buffer.getLong(index);
    }

    @Override
    public void getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        if (dst instanceof NioBufferBackedByteBuf) {
            NioBufferBackedByteBuf bbdst = (NioBufferBackedByteBuf) dst;
            ByteBuffer data = bbdst.tmpBuf;
            data.clear().position(dstIndex).limit(dstIndex + length);
            getBytes(index, data);
        } else if (buffer.hasArray()) {
            dst.setBytes(dstIndex, buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        try {
            tmpBuf.clear().position(index).limit(index + length);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need "
                    + (index + length) + ", maximum is " + buffer.limit());
        }
        tmpBuf.get(dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, ByteBuffer dst) {
        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        try {
            tmpBuf.clear().position(index).limit(index + bytesToCopy);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need "
                    + (index + bytesToCopy) + ", maximum is " + buffer.limit());
        }
        dst.put(tmpBuf);
    }

    @Override
    public void setByte(int index, int value) {
        buffer.put(index, (byte) value);
    }

    @Override
    public void setShort(int index, int value) {
        buffer.putShort(index, (short) value);
    }

    @Override
    public void setMedium(int index, int   value) {
        setByte(index,     (byte) (value >>> 16));
        setByte(index + 1, (byte) (value >>>  8));
        setByte(index + 2, (byte) (value >>>  0));
    }

    @Override
    public void setInt(int index, int   value) {
        buffer.putInt(index, value);
    }

    @Override
    public void setLong(int index, long  value) {
        buffer.putLong(index, value);
    }

    @Override
    public void setBytes(int index, ByteBuf src, int srcIndex, int length) {
        if (src instanceof NioBufferBackedByteBuf) {
            NioBufferBackedByteBuf bbsrc = (NioBufferBackedByteBuf) src;
            ByteBuffer data = bbsrc.tmpBuf;

            data.clear().position(srcIndex).limit(srcIndex + length);
            setBytes(index, data);
        } else if (buffer.hasArray()) {
            src.getBytes(srcIndex, buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            src.getBytes(srcIndex, this, index, length);
        }
    }

    @Override
    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.put(src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, ByteBuffer src) {
        if (src == tmpBuf) {
            src = src.duplicate();
        }

        tmpBuf.clear().position(index).limit(index + src.remaining());
        tmpBuf.put(src);
    }

    @Override
    public void getBytes(int index, OutputStream out, int length) throws IOException {
        if (length == 0) {
            return;
        }

        if (buffer.hasArray()) {
            out.write(
                    buffer.array(),
                    index + buffer.arrayOffset(),
                    length);
        } else {
            byte[] tmp = new byte[length];
            tmpBuf.clear().position(index);
            tmpBuf.get(tmp);
            out.write(tmp);
        }
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        if (length == 0) {
            return 0;
        }

        tmpBuf.clear().position(index).limit(index + length);
        return out.write(tmpBuf);
    }

    @Override
    public int setBytes(int index, InputStream in, int length)
            throws IOException {

        if (buffer.hasArray()) {
            return in.read(buffer.array(), buffer.arrayOffset() + index, length);
        } else {
            byte[] tmp = new byte[length];
            int readBytes = in.read(tmp);
            tmpBuf.clear().position(index);
            tmpBuf.put(tmp);
            return readBytes;
        }
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {

        tmpBuf.clear().position(index).limit(index + length);
        try {
            return in.read(tmpBuf);
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
        if (index == 0 && length == capacity()) {
            return buffer.duplicate().order(order());
        } else {
            return ((ByteBuffer) tmpBuf.clear().position(
                    index).limit(index + length)).slice().order(order());
        }
    }

    @Override
    public ByteBuf slice(int index, int length) {
        if (index == 0 && length == capacity()) {
            ByteBuf slice = duplicate();
            slice.setIndex(0, length);
            return slice;
        } else {
            if (index >= 0 && length == 0) {
                return Unpooled.EMPTY_BUFFER;
            }
            return new NioBufferBackedByteBuf(
                    ((ByteBuffer) tmpBuf.clear().position(
                            index).limit(index + length)).order(order()));
        }
    }

    @Override
    public ByteBuf duplicate() {
        return new NioBufferBackedByteBuf(this);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        ByteBuffer src;
        try {
            src = (ByteBuffer) tmpBuf.clear().position(index).limit(index + length);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need "
                    + (index + length));
        }

        ByteBuffer dst = src.isDirect() ? ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
        dst.put(src);
        dst.order(order());
        dst.clear();
        return new NioBufferBackedByteBuf(dst);
    }
}
