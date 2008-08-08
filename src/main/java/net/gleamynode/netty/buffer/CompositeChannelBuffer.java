/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.ArrayList;
import java.util.List;


/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class CompositeChannelBuffer extends AbstractChannelBuffer {

    private final ChannelBuffer[] slices;
    private final int[] indices;
    private int lastSliceId;

    public CompositeChannelBuffer(ChannelBuffer... buffers) {
        slices = new ChannelBuffer[buffers.length];
        for (int i = 0; i < buffers.length; i ++) {
            slices[i] = buffers[i].slice();
        }
        indices = new int[buffers.length + 1];
        for (int i = 1; i <= buffers.length; i ++) {
            indices[i] = indices[i - 1] + slices[i - 1].capacity();
        }
    }

    private CompositeChannelBuffer(CompositeChannelBuffer buffer) {
        slices = buffer.slices.clone();
        indices = buffer.indices.clone();
        readerIndex(buffer.readerIndex());
        writerIndex(buffer.writerIndex());
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
        } else {
            return (short) ((getByte(index) & 0xff) << 8 | getByte(index + 1) & 0xff);
        }
    }

    public int getMedium(int index) {
        int sliceId = sliceId(index);
        if (index + 3 <= indices[sliceId + 1]) {
            return slices[sliceId].getMedium(index - indices[sliceId]);
        } else {
            return (getShort(index) & 0xffff) << 8 | getByte(index + 2) & 0xff;
        }
    }

    public int getInt(int index) {
        int sliceId = sliceId(index);
        if (index + 4 <= indices[sliceId + 1]) {
            return slices[sliceId].getInt(index - indices[sliceId]);
        } else {
            return (getShort(index) & 0xffff) << 16 | getShort(index + 2) & 0xffff;
        }
    }

    public long getLong(int index) {
        int sliceId = sliceId(index);
        if (index + 8 <= indices[sliceId + 1]) {
            return slices[sliceId].getLong(index - indices[sliceId]);
        } else {
            return (getInt(index) & 0xffffffffL) << 32 | getInt(index + 4) & 0xffffffffL;
        }
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        int sliceId = sliceId(index);
        if (index + length >= capacity()) {
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
        if (index + length >= capacity()) {
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
        if (index + length >= capacity()) {
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
        if (index + length >= capacity()) {
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
        } else {
            setByte(index, (byte) (value >>> 8));
            setByte(index + 1, (byte) value);
        }
    }

    public void setMedium(int index, int value) {
        int sliceId = sliceId(index);
        if (index + 3 <= indices[sliceId + 1]) {
            slices[sliceId].setMedium(index - indices[sliceId], value);
        } else {
            setShort(index, (short) (value >>> 8));
            setByte(index + 2, (byte) value);
        }
    }

    public void setInt(int index, int value) {
        int sliceId = sliceId(index);
        if (index + 4 <= indices[sliceId + 1]) {
            slices[sliceId].setInt(index - indices[sliceId], value);
        } else {
            setShort(index, (short) (value >>> 16));
            setShort(index + 2, (short) value);
        }
    }

    public void setLong(int index, long value) {
        int sliceId = sliceId(index);
        if (index + 8 <= indices[sliceId + 1]) {
            slices[sliceId].setLong(index - indices[sliceId], value);
        } else {
            setInt(index, (int) (value >>> 32));
            setInt(index + 4, (int) value);
        }
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        int sliceId = sliceId(index);
        if (index + length >= capacity()) {
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
        if (index + length >= capacity()) {
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
        if (index + length >= capacity()) {
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

    public void setBytes(int index, InputStream in, int length)
            throws IOException {
        int sliceId = sliceId(index);
        if (index + length >= capacity()) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.setBytes(index - adjustment, in, localLength);
            index += localLength;
            length -= localLength;
            i ++;
        }
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        int sliceId = sliceId(index);
        if (index + length >= capacity()) {
            throw new IndexOutOfBoundsException();
        }

        int i = sliceId;
        int writtenBytes = 0;
        while (length > 0) {
            ChannelBuffer s = slices[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            int localWrittenBytes = s.setBytes(index - adjustment, in, localLength);
            writtenBytes += localWrittenBytes;
            if (localLength != localWrittenBytes) {
                break;
            }
            index += localLength;
            length -= localLength;
            i ++;
        }

        return writtenBytes;
    }

    public ChannelBuffer duplicate() {
        return new CompositeChannelBuffer(this);
    }

    public ChannelBuffer slice(int index, int length) {
        return new SlicedChannelBuffer(this, index, length);
    }

    public ByteBuffer toByteBuffer(int index, int length) {
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
        if (index + length >= capacity()) {
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
