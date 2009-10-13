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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * A virtual buffer which shows multiple buffers as a single merged buffer.  It
 * is recommended to use {@link ChannelBuffers#wrappedBuffer(ChannelBuffer...)}
 * instead of calling the constructor explicitly.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class CompositeChannelBuffer extends AbstractChannelBuffer {

    private final ByteOrder order;
    private ChannelBuffer[] slices;
    private int[] indices;
    private int lastSliceId;

    public CompositeChannelBuffer(ChannelBuffer... buffers) {
        if (buffers.length == 0) {
            throw new IllegalArgumentException("buffers should not be empty.");
        }

        // Get the list of the component, while guessing the byte order.
        ByteOrder expectedEndianness = null;
        final List<ChannelBuffer> bufferList = new ArrayList<ChannelBuffer>(buffers.length);
        for (ChannelBuffer buffer: buffers) {
            if (buffer.readableBytes() > 0) {
                expectedEndianness = buffer.order();
                if (buffer instanceof CompositeChannelBuffer) {
                    // Expand nested composition.
                    CompositeChannelBuffer child = (CompositeChannelBuffer) buffer;
                    bufferList.addAll(
                            child.slice0(child.readerIndex(), child.readableBytes()));
                } else {
                    // An ordinary buffer (non-composite)
                    bufferList.add(buffer.slice());
                }
            } else if (buffer.capacity() != 0) {
                expectedEndianness = buffer.order();
            }
        }

        if (expectedEndianness == null) {
            throw new IllegalArgumentException(
                    "buffers have only empty buffers.");
        }

        order = expectedEndianness;
        setFromList(bufferList);
    }

   /**
    * Same with {@link #slice(int, int)} except that this method returns a list.
    */
   private List<ChannelBuffer> slice0(int index, int length) {
       if (length == 0) {
           return Collections.emptyList();
       }

       if (index + length > capacity()) {
           throw new IndexOutOfBoundsException();
       }

       int sliceId = sliceId(index);
       List<ChannelBuffer> bufferList = new ArrayList<ChannelBuffer>(slices.length);
       // first one is not complete
       // each slice will be duplicated before assign to the list (maintain original indexes)
       ChannelBuffer buf = slices[sliceId].duplicate();
       buf.readerIndex(index - indices[sliceId]);
       buf.writerIndex(slices[sliceId].writerIndex());
       // as writerIndex can be less than capacity, check too for the end
       int bytesToSlice = length;
       while (bytesToSlice > 0) {
           int leftInBuffer = buf.capacity() - buf.readerIndex();
           if (bytesToSlice <= leftInBuffer) {
               // final buffer
               buf.writerIndex(buf.readerIndex() + bytesToSlice);
               bufferList.add(buf);
               bytesToSlice = 0;
               break;
           } else {
               // not final buffer
               bufferList.add(buf);
               bytesToSlice -= leftInBuffer;
               sliceId ++;
               buf = slices[sliceId].duplicate();
               buf.readerIndex(0);
               buf.writerIndex(slices[sliceId].writerIndex());
               // length is > 0
           }
       }
       return bufferList;
   }

   /**
    * Setup this ChannelBuffer from the list and the Endianness
    * @param listBuf
    * @param expectedEndianness
    */
   private void setFromList(List<ChannelBuffer> listBuf) {

       // Reset the cached slice position.
       lastSliceId = 0;

       int number = listBuf.size();
       if (number == 0) {
           slices = new ChannelBuffer[1];
           // to prevent remove too early
           slices[0] = ChannelBuffers.EMPTY_BUFFER.slice();
           indices = new int[2];
           indices[1] = indices[0] + slices[0].capacity();
           readerIndex(0);
           writerIndex(capacity());
           return;
       }
       slices = new ChannelBuffer[number];
       int i = 0;
       for (ChannelBuffer buffer: listBuf) {
           if (buffer.order() != order()) {
               throw new IllegalArgumentException(
                       "All buffers must have the same endianness.");
           }
           slices[i] = buffer;
           i ++;
       }
       indices = new int[number + 1];
       indices[0] = 0;
       for (i = 1; i <= number; i ++) {
           indices[i] = indices[i - 1] + slices[i - 1].capacity();
       }
       setIndex(0, capacity());
   }

    private CompositeChannelBuffer(CompositeChannelBuffer buffer) {
        order = buffer.order;
        slices = buffer.slices.clone();
        indices = buffer.indices.clone();
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    public ChannelBufferFactory factory() {
        return HeapChannelBufferFactory.getInstance(order());
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
        //     This issue appeared in 2004 and is still unresolved!?
        return out.write(toByteBuffer(index, length));
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
            setShort(index, (short) (value >> 8));
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

        ChannelBuffer dst = factory().getBuffer(order(), length);
        copyTo(index, length, sliceId, dst);
        return dst;
    }

    private void copyTo(int index, int length, int sliceId, ChannelBuffer dst) {
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
    }

    public ChannelBuffer slice(int index, int length) {
        if (index == 0) {
            if (length == 0) {
                return ChannelBuffers.EMPTY_BUFFER;
            }
        } else if (index < 0 || index > capacity() - length) {
            throw new IndexOutOfBoundsException();
        } else if (length == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }

        List<ChannelBuffer> listBuffer = slice0(index, length);
        ChannelBuffer[] buffers = listBuffer.toArray(new ChannelBuffer[listBuffer.size()]);
        return new CompositeChannelBuffer(buffers);
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        if (slices.length == 1) {
            return slices[0].toByteBuffer(index, length);
        }

        ByteBuffer[] buffers = toByteBuffers(index, length);
        ByteBuffer merged = ByteBuffer.allocate(length).order(order());
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

    @Override
    public void discardReadBytes() {
        // Only the bytes between readerIndex and writerIndex will be kept.
        // New readerIndex and writerIndex will become 0 and
        // (previous writerIndex - previous readerIndex) respectively.

        final int localReaderIndex = this.readerIndex();
        if (localReaderIndex == 0) {
            return;
        }
        int localWriterIndex = this.writerIndex();

        final int bytesToMove = capacity() - localReaderIndex;
        List<ChannelBuffer> list = slice0(localReaderIndex, bytesToMove);

        // Truncate the discardable bytes of the first buffer.
        list.set(0, list.get(0).slice());

        // Add a new buffer so that the capacity of this composite buffer does
        // not decrease due to the discarded components.
        list.add(ChannelBuffers.buffer(order(), localReaderIndex));

        // Reset the index markers to get the index marker values.
        int localMarkedReaderIndex = localReaderIndex;
        try {
            resetReaderIndex();
            localMarkedReaderIndex = this.readerIndex();
        } catch (IndexOutOfBoundsException e) {
            // ignore
        }
        int localMarkedWriterIndex = localWriterIndex;
        try {
            resetWriterIndex();
            localMarkedWriterIndex = this.writerIndex();
        } catch (IndexOutOfBoundsException e) {
            // ignore
        }

        setFromList(list);

        // reset marked Indexes
        localMarkedReaderIndex = Math.max(localMarkedReaderIndex - localReaderIndex, 0);
        localMarkedWriterIndex = Math.max(localMarkedWriterIndex - localReaderIndex, 0);
        setIndex(localMarkedReaderIndex, localMarkedWriterIndex);
        markReaderIndex();
        markWriterIndex();
        // reset real indexes
        localWriterIndex = Math.max(localWriterIndex - localReaderIndex, 0);
        setIndex(0, localWriterIndex);
    }

    @Override
    public String toString() {
        String result = super.toString();
        result = result.substring(0, result.length() - 1);
        return result + ", slices=" + slices.length + ")";
    }
}
