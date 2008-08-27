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
package org.jboss.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;


/**
 * Virtual buffer which shows multiple buffers as a single merged buffer.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class CompositeChannelBuffer extends AbstractChannelBuffer {

    private final ChannelBuffer[] slices;
    private final ByteOrder order;
    private final int[] indices;
    private int lastSliceId;

    public CompositeChannelBuffer(ChannelBuffer... buffers) {
        if (buffers.length == 0) {
            throw new IllegalArgumentException("buffers should not be empty.");
        }

        ByteOrder expectedEndianness = null;
        for (ChannelBuffer buffer : buffers) {
            if (buffer.capacity() != 0) {
                expectedEndianness = buffer.order();
            }
        }

        if (expectedEndianness == null) {
            throw new IllegalArgumentException("buffers have only empty buffers.");
        }

        order = expectedEndianness;
        slices = new ChannelBuffer[buffers.length];
        for (int i = 0; i < buffers.length; i ++) {
            if (buffers[i].capacity() != 0 && buffers[i].order() != expectedEndianness) {
                throw new IllegalArgumentException(
                        "All buffers must have the same endianness.");
            }
            slices[i] = buffers[i].slice();
        }
        indices = new int[buffers.length + 1];
        for (int i = 1; i <= buffers.length; i ++) {
            indices[i] = indices[i - 1] + slices[i - 1].capacity();
        }
        writerIndex(capacity());
    }

    private CompositeChannelBuffer(CompositeChannelBuffer buffer) {
        order = buffer.order;
        slices = buffer.slices.clone();
        indices = buffer.indices.clone();
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    public ByteOrder order() {
        return order;
    }

    public int capacity() {
        return indices[slices.length];
    }

    public byte getByte(int index) {
        int sliceId = sliceId(index);
        return slices[sliceId].getByte(index - indices[sliceId]);
    }

    public short getShort(int index) {
        int sliceId = sliceId(index);
        if (index + 2 <= indices[sliceId + 1]) {
            return slices[sliceId].getShort(index - indices[sliceId]);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (short) ((getByte(index) & 0xff) << 8 | getByte(index + 1) & 0xff);
        } else {
            return (short) (getByte(index) & 0xff | (getByte(index + 1) & 0xff) << 8);
        }
    }

    public int getUnsignedMedium(int index) {
        int sliceId = sliceId(index);
        if (index + 3 <= indices[sliceId + 1]) {
            return slices[sliceId].getUnsignedMedium(index - indices[sliceId]);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getShort(index) & 0xffff) << 8 | getByte(index + 2) & 0xff;
        } else {
            return getShort(index) & 0xFFFF | (getByte(index + 2) & 0xFF) << 16;
        }
    }

    public int getInt(int index) {
        int sliceId = sliceId(index);
        if (index + 4 <= indices[sliceId + 1]) {
            return slices[sliceId].getInt(index - indices[sliceId]);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getShort(index) & 0xffff) << 16 | getShort(index + 2) & 0xffff;
        } else {
            return getShort(index) & 0xFFFF | (getShort(index + 2) & 0xFFFF) << 16;
        }
    }

    public long getLong(int index) {
        int sliceId = sliceId(index);
        if (index + 8 <= indices[sliceId + 1]) {
            return slices[sliceId].getLong(index - indices[sliceId]);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getInt(index) & 0xffffffffL) << 32 | getInt(index + 4) & 0xffffffffL;
        } else {
            return getInt(index) & 0xFFFFFFFFL | (getInt(index + 4) & 0xFFFFFFFFL) << 32;
        }
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        int sliceId = sliceId(index);
        if (index > capacity() - length || dstIndex > dst.length - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, dst, dstIndex, localLength);
            index += localLength;
            dstIndex += localLength;
            length -= localLength;
            i ++;
        }
    }

    public void getBytes(int index, ByteBuffer dst) {
        int sliceId = sliceId(index);
        int limit = dst.limit();
        int length = dst.remaining();
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        try {
            while (length > 0) {
                ChannelBuffer s = slices[i];
                int adjustment = indices[i];
                int localLength = Math.min(length, s.capacity() - (index - adjustment));
                dst.limit(dst.position() + localLength);
                s.getBytes(index - adjustment, dst);
                index += localLength;
                length -= localLength;
                i ++;
            }
        } finally {
            dst.limit(limit);
        }
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        int sliceId = sliceId(index);
        if (index > capacity() - length || dstIndex > dst.capacity() - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, dst, dstIndex, localLength);
            index += localLength;
            dstIndex += localLength;
            length -= localLength;
            i ++;
        }
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        // XXX Gathering write is not supported because of a known issue.
        //     See http://bugs.sun.com/view_bug.do?bug_id=6210541
        return out.write(toByteBuffer());
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        int sliceId = sliceId(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, out, localLength);
            index += localLength;
            length -= localLength;
            i ++;
        }
    }

    public void setByte(int index, byte value) {
        int sliceId = sliceId(index);
        slices[sliceId].setByte(index - indices[sliceId], value);
    }

    public void setShort(int index, short value) {
        int sliceId = sliceId(index);
        if (index + 2 <= indices[sliceId + 1]) {
            slices[sliceId].setShort(index - indices[sliceId], value);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            setByte(index, (byte) (value >>> 8));
            setByte(index + 1, (byte) value);
        } else {
            setByte(index    , (byte) value);
            setByte(index + 1, (byte) (value >>> 8));
        }
    }

    public void setMedium(int index, int value) {
        int sliceId = sliceId(index);
        if (index + 3 <= indices[sliceId + 1]) {
            slices[sliceId].setMedium(index - indices[sliceId], value);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            setShort(index, (short) (value >>> 8));
            setByte(index + 2, (byte) value);
        } else {
            setShort(index    , (short) value);
            setByte (index + 2, (byte) (value >>> 16));
        }
    }

    public void setInt(int index, int value) {
        int sliceId = sliceId(index);
        if (index + 4 <= indices[sliceId + 1]) {
            slices[sliceId].setInt(index - indices[sliceId], value);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            setShort(index, (short) (value >>> 16));
            setShort(index + 2, (short) value);
        } else {
            setShort(index    , (short) value);
            setShort(index + 2, (short) (value >>> 16));
        }
    }

    public void setLong(int index, long value) {
        int sliceId = sliceId(index);
        if (index + 8 <= indices[sliceId + 1]) {
            slices[sliceId].setLong(index - indices[sliceId], value);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            setInt(index, (int) (value >>> 32));
            setInt(index + 4, (int) value);
        } else {
            setInt(index    , (int) value);
            setInt(index + 4, (int) (value >>> 32));
        }
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        int sliceId = sliceId(index);
        if (index > capacity() - length || srcIndex > src.length - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.setBytes(index - adjustment, src, srcIndex, localLength);
            index += localLength;
            srcIndex += localLength;
            length -= localLength;
            i ++;
        }
    }

    public void setBytes(int index, ByteBuffer src) {
        int sliceId = sliceId(index);
        int limit = src.limit();
        int length = src.remaining();
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        try {
            while (length > 0) {
                ChannelBuffer s = slices[i];
                int adjustment = indices[i];
                int localLength = Math.min(length, s.capacity() - (index - adjustment));
                src.limit(src.position() + localLength);
                s.setBytes(index - adjustment, src);
                index += localLength;
                length -= localLength;
                i ++;
            }
        } finally {
            src.limit(limit);
        }
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        int sliceId = sliceId(index);
        if (index > capacity() - length || srcIndex > src.capacity() - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.setBytes(index - adjustment, src, srcIndex, localLength);
            index += localLength;
            srcIndex += localLength;
            length -= localLength;
            i ++;
        }
    }

    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        int sliceId = sliceId(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        int readBytes = 0;

        do {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            int localReadBytes = s.setBytes(index - adjustment, in, localLength);
            if (localReadBytes < 0) {
                if (readBytes == 0) {
                    return -1;
                } else {
                    break;
                }
            }

            if (localReadBytes == localLength) {
                index += localLength;
                length -= localLength;
                readBytes += localLength;
                i ++;
            } else {
                index += localReadBytes;
                length -= localReadBytes;
                readBytes += localReadBytes;
            }
        } while (length > 0);

        return readBytes;
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        int sliceId = sliceId(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        int readBytes = 0;
        do {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            int localReadBytes = s.setBytes(index - adjustment, in, localLength);

            if (localReadBytes == localLength) {
                index += localLength;
                length -= localLength;
                readBytes += localLength;
                i ++;
            } else {
                index += localReadBytes;
                length -= localReadBytes;
                readBytes += localReadBytes;
            }
        } while (length > 0);

        return readBytes;
    }

    public ChannelBuffer duplicate() {
        ChannelBuffer duplicate = new CompositeChannelBuffer(this);
        duplicate.setIndex(readerIndex(), writerIndex());
        return duplicate;
    }

    public ChannelBuffer copy(int index, int length) {
        int sliceId = sliceId(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException();
        }

        ChannelBuffer dst = ChannelBuffers.buffer(order(), length);
        int dstIndex = 0;
        int i = sliceId;

        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, dst, dstIndex, localLength);
            index += localLength;
            dstIndex += localLength;
            length -= localLength;
            i ++;
        }

        dst.writerIndex(dst.capacity());
        return dst;
    }

    public ChannelBuffer slice(int index, int length) {
        if (index == 0) {
            if (length == 0) {
                return ChannelBuffers.EMPTY_BUFFER;
            } else {
                return new TruncatedChannelBuffer(this, length);
            }
        } else if (index < 0 || index > capacity() - length) {
            throw new IndexOutOfBoundsException();
        } else if (length == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        } else {
            return new SlicedChannelBuffer(this, index, length);
        }
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        if (slices.length == 1) {
            return slices[0].toByteBuffer(index, length);
        }

        ByteBuffer[] buffers = toByteBuffers(index, length);
        ByteBuffer merged = ByteBuffer.allocate(length);
        for (ByteBuffer b: buffers) {
            merged.put(b);
        }
        merged.flip();
        return merged;
    }

    @Override
    public ByteBuffer[] toByteBuffers(int index, int length) {
        int sliceId = sliceId(index);
        if (index + length > capacity()) {
            throw new IndexOutOfBoundsException();
        }

        List<ByteBuffer> buffers = new ArrayList<ByteBuffer>(slices.length);

        int i = sliceId;
        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            buffers.add(s.toByteBuffer(index - adjustment, localLength));
            index += localLength;
            length -= localLength;
            i ++;
        }

        return buffers.toArray(new ByteBuffer[buffers.size()]);
    }

    public String toString(int index, int length, String charsetName) {
        int sliceId = sliceId(index);
        if (index + length <= indices[sliceId + 1]) {
            return slices[sliceId].toString(
                    index - indices[sliceId], length, charsetName);
        }

        byte[] data = new byte[length];
        int dataIndex = 0;
        int i = sliceId;

        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, data, dataIndex, localLength);
            index += localLength;
            dataIndex += localLength;
            length -= localLength;
            i ++;
        }

        try {
            return new String(data, charsetName);
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(charsetName);
        }
    }

    private int sliceId(int index) {
        int lastSliceId = this.lastSliceId;
        if (index >= indices[lastSliceId]) {
            if (index < indices[lastSliceId + 1]) {
                return lastSliceId;
            }

            // Search right
            for (int i = lastSliceId + 1; i < slices.length; i ++) {
                if (index < indices[i + 1]) {
                    this.lastSliceId = i;
                    return i;
                }
            }
        } else {
            // Search left
            for (int i = lastSliceId - 1; i >= 0; i --) {
                if (index >= indices[i]) {
                    this.lastSliceId = i;
                    return i;
                }
            }
        }

        throw new IndexOutOfBoundsException();
    }
}
