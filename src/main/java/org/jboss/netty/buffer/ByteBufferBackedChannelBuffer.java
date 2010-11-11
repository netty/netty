/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * A NIO {@link ByteBuffer} based buffer.  It is recommended to use {@link ChannelBuffers#directBuffer(int)}
 * and {@link ChannelBuffers#wrappedBuffer(ByteBuffer)} instead of calling the
 * constructor explicitly.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2309 $, $Date: 2010-06-21 16:00:03 +0900 (Mon, 21 Jun 2010) $
 *
 */
public class ByteBufferBackedChannelBuffer extends AbstractChannelBuffer {

    private final ByteBuffer buffer;
    private final ByteOrder order;
    private final int capacity;

    /**
     * Creates a new buffer which wraps the specified buffer's slice.
     */
    public ByteBufferBackedChannelBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }

        order = buffer.order();
        this.buffer = buffer.slice().order(order);
        capacity = buffer.remaining();
        writerIndex(capacity);
    }

    private ByteBufferBackedChannelBuffer(ByteBufferBackedChannelBuffer buffer) {
        this.buffer = buffer.buffer;
        order = buffer.order;
        capacity = buffer.capacity;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    public ChannelBufferFactory factory() {
        if (buffer.isDirect()) {
            return DirectChannelBufferFactory.getInstance(order());
        } else {
            return HeapChannelBufferFactory.getInstance(order());
        }
    }

    public boolean isDirect() {
        return buffer.isDirect();
    }

    public ByteOrder order() {
        return order;
    }

    public int capacity() {
        return capacity;
    }

    public boolean hasArray() {
        return buffer.hasArray();
    }

    public byte[] array() {
        return buffer.array();
    }

    public int arrayOffset() {
        return buffer.arrayOffset();
    }

    public byte getByte(int index) {
        return buffer.get(index);
    }

    public short getShort(int index) {
        return buffer.getShort(index);
    }

    public int getUnsignedMedium(int index) {
        return  (getByte(index)   & 0xff) << 16 |
                (getByte(index+1) & 0xff) <<  8 |
                (getByte(index+2) & 0xff) <<  0;
    }

    public int getInt(int index) {
        return buffer.getInt(index);
    }

    public long getLong(int index) {
        return buffer.getLong(index);
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        if (dst instanceof ByteBufferBackedChannelBuffer) {
            ByteBufferBackedChannelBuffer bbdst = (ByteBufferBackedChannelBuffer) dst;
            ByteBuffer data = bbdst.buffer.duplicate();

            data.limit(dstIndex + length).position(dstIndex);
            getBytes(index, data);
        } else if (buffer.hasArray()) {
            dst.setBytes(dstIndex, buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        ByteBuffer data = buffer.duplicate();
        try {
            data.limit(index + length).position(index);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException();
        }
        data.get(dst, dstIndex, length);
    }

    public void getBytes(int index, ByteBuffer dst) {
        ByteBuffer data = buffer.duplicate();
        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        try {
            data.limit(index + bytesToCopy).position(index);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException();
        }
        dst.put(data);
    }

    public void setByte(int index, int value) {
        buffer.put(index, (byte) value);
    }

    public void setShort(int index, int value) {
        buffer.putShort(index, (short) value);
    }

    public void setMedium(int index, int   value) {
        setByte(index,   (byte) (value >>> 16));
        setByte(index+1, (byte) (value >>>  8));
        setByte(index+2, (byte) (value >>>  0));
    }

    public void setInt(int index, int   value) {
        buffer.putInt(index, value);
    }

    public void setLong(int index, long  value) {
        buffer.putLong(index, value);
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        if (src instanceof ByteBufferBackedChannelBuffer) {
            ByteBufferBackedChannelBuffer bbsrc = (ByteBufferBackedChannelBuffer) src;
            ByteBuffer data = bbsrc.buffer.duplicate();

            data.limit(srcIndex + length).position(srcIndex);
            setBytes(index, data);
        } else if (buffer.hasArray()) {
            src.getBytes(srcIndex, buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            src.getBytes(srcIndex, this, index, length);
        }
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        ByteBuffer data = buffer.duplicate();
        data.limit(index + length).position(index);
        data.put(src, srcIndex, length);
    }

    public void setBytes(int index, ByteBuffer src) {
        ByteBuffer data = buffer.duplicate();
        data.limit(index + src.remaining()).position(index);
        data.put(src);
    }

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
            ((ByteBuffer) buffer.duplicate().position(index)).get(tmp);
            out.write(tmp);
        }
    }

    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        if (length == 0) {
            return 0;
        }

        return out.write((ByteBuffer) buffer.duplicate().position(index).limit(index + length));
    }

    public int setBytes(int index, InputStream in, int length)
            throws IOException {

        int readBytes = 0;

        if (buffer.hasArray()) {
            index += buffer.arrayOffset();
            do {
                int localReadBytes = in.read(buffer.array(), index, length);
                if (localReadBytes < 0) {
                    if (readBytes == 0) {
                        return -1;
                    } else {
                        break;
                    }
                }
                readBytes += localReadBytes;
                index += localReadBytes;
                length -= localReadBytes;
            } while (length > 0);
        } else {
            byte[] tmp = new byte[length];
            int i = 0;
            do {
                int localReadBytes = in.read(tmp, i, tmp.length - i);
                if (localReadBytes < 0) {
                    if (readBytes == 0) {
                        return -1;
                    } else {
                        break;
                    }
                }
                readBytes += localReadBytes;
                i += readBytes;
            } while (i < tmp.length);
            ((ByteBuffer) buffer.duplicate().position(index)).put(tmp);
        }

        return readBytes;
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {

        ByteBuffer slice = (ByteBuffer) buffer.duplicate().limit(index + length).position(index);
        int readBytes = 0;

        while (readBytes < length) {
            int localReadBytes;
            try {
                localReadBytes = in.read(slice);
            } catch (ClosedChannelException e) {
                localReadBytes = -1;
            }
            if (localReadBytes < 0) {
                if (readBytes == 0) {
                    return -1;
                } else {
                    return readBytes;
                }
            } else if (localReadBytes == 0) {
                break;
            }
            readBytes += localReadBytes;
        }

        return readBytes;
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        if (index == 0 && length == capacity()) {
            return buffer.duplicate().order(order());
        } else {
            return ((ByteBuffer) buffer.duplicate().position(
                    index).limit(index + length)).slice().order(order());
        }
    }

    public ChannelBuffer slice(int index, int length) {
        if (index == 0 && length == capacity()) {
            ChannelBuffer slice = duplicate();
            slice.setIndex(0, length);
            return slice;
        } else {
            if (index >= 0 && length == 0) {
                return ChannelBuffers.EMPTY_BUFFER;
            }
            return new ByteBufferBackedChannelBuffer(
                    ((ByteBuffer) buffer.duplicate().position(
                            index).limit(index + length)).order(order()));
        }
    }

    public ChannelBuffer duplicate() {
        return new ByteBufferBackedChannelBuffer(this);
    }

    public ChannelBuffer copy(int index, int length) {
        ByteBuffer src;
        try {
            src = (ByteBuffer) buffer.duplicate().position(index).limit(index + length);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException();
        }

        ByteBuffer dst = buffer.isDirect() ? ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
        dst.put(src);
        dst.order(order());
        dst.clear();
        return new ByteBufferBackedChannelBuffer(dst);
    }
}
