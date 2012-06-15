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

import io.netty.util.internal.DetectionUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * A virtual buffer which shows multiple buffers as a single merged buffer.  It
 * is recommended to use {@link Unpooled#wrappedBuffer(ByteBuf...)}
 * instead of calling the constructor explicitly.
 */
public class CompositeByteBuf extends AbstractByteBuf {

    private ByteBuf[] components;
    private int[] indices;
    private int lastAccessedComponentId;

    public CompositeByteBuf(ByteOrder endianness, List<ByteBuf> buffers) {
        super(endianness);
        setComponents(buffers);
    }

    /**
     * Same with {@link #slice(int, int)} except that this method returns a list.
     */
    public List<ByteBuf> decompose(int index, int length) {
        if (length == 0) {
            return Collections.emptyList();
        }

        if (index + length > capacity()) {
            throw new IndexOutOfBoundsException("Too many bytes to decompose - Need "
                    + (index + length) + ", capacity is " + capacity());
        }

        int componentId = componentId(index);
        List<ByteBuf> slice = new ArrayList<ByteBuf>(components.length);

        // The first component
        ByteBuf first = components[componentId].duplicate();
        first.readerIndex(index - indices[componentId]);

        ByteBuf buf = first;
        int bytesToSlice = length;
        do {
            int readableBytes = buf.readableBytes();
            if (bytesToSlice <= readableBytes) {
                // Last component
                buf.writerIndex(buf.readerIndex() + bytesToSlice);
                slice.add(buf);
                break;
            } else {
                // Not the last component
                slice.add(buf);
                bytesToSlice -= readableBytes;
                componentId ++;

                // Fetch the next component.
                buf = components[componentId].duplicate();
            }
        } while (bytesToSlice > 0);

        // Slice all components because only readable bytes are interesting.
        for (int i = 0; i < slice.size(); i ++) {
            slice.set(i, slice.get(i).slice());
        }

        return slice;
    }

    /**
     * Setup this ChannelBuffer from the list
     */
    private void setComponents(List<ByteBuf> newComponents) {
        assert !newComponents.isEmpty();

        // Clear the cache.
        lastAccessedComponentId = 0;

        // Build the component array.
        components = new ByteBuf[newComponents.size()];
        for (int i = 0; i < components.length; i ++) {
            ByteBuf c = newComponents.get(i);
            if (c.order() != order()) {
                throw new IllegalArgumentException(
                        "All buffers must have the same endianness.");
            }

            assert c.readerIndex() == 0;
            assert c.writerIndex() == c.capacity();

            components[i] = c;
        }

        // Build the component lookup table.
        indices = new int[components.length + 1];
        indices[0] = 0;
        for (int i = 1; i <= components.length; i ++) {
            indices[i] = indices[i - 1] + components[i - 1].capacity();
        }

        // Reset the indexes.
        setIndex(0, capacity());
    }

    private CompositeByteBuf(CompositeByteBuf buffer) {
        super(buffer.order());
        components = buffer.components.clone();
        indices = buffer.indices.clone();
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    @Override
    public ByteBufFactory factory() {
        return HeapByteBufFactory.getInstance(order());
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int arrayOffset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int capacity() {
        return indices[components.length];
    }

    @Override
    public byte getByte(int index) {
        int componentId = componentId(index);
        return components[componentId].getByte(index - indices[componentId]);
    }

    @Override
    public short getShort(int index) {
        int componentId = componentId(index);
        if (index + 2 <= indices[componentId + 1]) {
            return components[componentId].getShort(index - indices[componentId]);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (short) ((getByte(index) & 0xff) << 8 | getByte(index + 1) & 0xff);
        } else {
            return (short) (getByte(index) & 0xff | (getByte(index + 1) & 0xff) << 8);
        }
    }

    @Override
    public int getUnsignedMedium(int index) {
        int componentId = componentId(index);
        if (index + 3 <= indices[componentId + 1]) {
            return components[componentId].getUnsignedMedium(index - indices[componentId]);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getShort(index) & 0xffff) << 8 | getByte(index + 2) & 0xff;
        } else {
            return getShort(index) & 0xFFFF | (getByte(index + 2) & 0xFF) << 16;
        }
    }

    @Override
    public int getInt(int index) {
        int componentId = componentId(index);
        if (index + 4 <= indices[componentId + 1]) {
            return components[componentId].getInt(index - indices[componentId]);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getShort(index) & 0xffff) << 16 | getShort(index + 2) & 0xffff;
        } else {
            return getShort(index) & 0xFFFF | (getShort(index + 2) & 0xFFFF) << 16;
        }
    }

    @Override
    public long getLong(int index) {
        int componentId = componentId(index);
        if (index + 8 <= indices[componentId + 1]) {
            return components[componentId].getLong(index - indices[componentId]);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getInt(index) & 0xffffffffL) << 32 | getInt(index + 4) & 0xffffffffL;
        } else {
            return getInt(index) & 0xFFFFFFFFL | (getInt(index + 4) & 0xFFFFFFFFL) << 32;
        }
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        int componentId = componentId(index);
        if (index > capacity() - length || dstIndex > dst.length - length) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Needs "
                    + (index + length) + ", maximum is " + capacity() + " or "
                    + dst.length);
        }

        int i = componentId;
        while (length > 0) {
            ByteBuf s = components[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, dst, dstIndex, localLength);
            index += localLength;
            dstIndex += localLength;
            length -= localLength;
            i ++;
        }
    }

    @Override
    public void getBytes(int index, ByteBuffer dst) {
        int componentId = componentId(index);
        int limit = dst.limit();
        int length = dst.remaining();
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be read - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        int i = componentId;
        try {
            while (length > 0) {
                ByteBuf s = components[i];
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

    @Override
    public void getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        int componentId = componentId(index);
        if (index > capacity() - length || dstIndex > dst.capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be read - Needs "
                    + (index + length) + " or " + (dstIndex + length) + ", maximum is "
                    + capacity() + " or " + dst.capacity());
        }

        int i = componentId;
        while (length > 0) {
            ByteBuf s = components[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, dst, dstIndex, localLength);
            index += localLength;
            dstIndex += localLength;
            length -= localLength;
            i ++;
        }
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        if (DetectionUtil.javaVersion() < 7) {
            // XXX Gathering write is not supported because of a known issue.
            //     See http://bugs.sun.com/view_bug.do?bug_id=6210541
            return out.write(copiedNioBuffer(index, length));
        } else {
            long writtenBytes = out.write(nioBuffers(index, length));
            if (writtenBytes > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            } else {
                return (int) writtenBytes;
            }
        }
    }

    @Override
    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        int componentId = componentId(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be read - needs "
                    + (index + length) + ", maximum of " + capacity());
        }

        int i = componentId;
        while (length > 0) {
            ByteBuf s = components[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, out, localLength);
            index += localLength;
            length -= localLength;
            i ++;
        }
    }

    @Override
    public void setByte(int index, int value) {
        int componentId = componentId(index);
        components[componentId].setByte(index - indices[componentId], value);
    }

    @Override
    public void setShort(int index, int value) {
        int componentId = componentId(index);
        if (index + 2 <= indices[componentId + 1]) {
            components[componentId].setShort(index - indices[componentId], value);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            setByte(index, (byte) (value >>> 8));
            setByte(index + 1, (byte) value);
        } else {
            setByte(index    , (byte) value);
            setByte(index + 1, (byte) (value >>> 8));
        }
    }

    @Override
    public void setMedium(int index, int value) {
        int componentId = componentId(index);
        if (index + 3 <= indices[componentId + 1]) {
            components[componentId].setMedium(index - indices[componentId], value);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            setShort(index, (short) (value >> 8));
            setByte(index + 2, (byte) value);
        } else {
            setShort(index    , (short) value);
            setByte(index + 2, (byte) (value >>> 16));
        }
    }

    @Override
    public void setInt(int index, int value) {
        int componentId = componentId(index);
        if (index + 4 <= indices[componentId + 1]) {
            components[componentId].setInt(index - indices[componentId], value);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            setShort(index, (short) (value >>> 16));
            setShort(index + 2, (short) value);
        } else {
            setShort(index    , (short) value);
            setShort(index + 2, (short) (value >>> 16));
        }
    }

    @Override
    public void setLong(int index, long value) {
        int componentId = componentId(index);
        if (index + 8 <= indices[componentId + 1]) {
            components[componentId].setLong(index - indices[componentId], value);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            setInt(index, (int) (value >>> 32));
            setInt(index + 4, (int) value);
        } else {
            setInt(index    , (int) value);
            setInt(index + 4, (int) (value >>> 32));
        }
    }

    @Override
    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        int componentId = componentId(index);
        if (index > capacity() - length || srcIndex > src.length - length) {
            throw new IndexOutOfBoundsException("Too many bytes to read - needs "
                    + (index + length) + " or " + (srcIndex + length) + ", maximum is "
                    + capacity() + " or " + src.length);
        }

        int i = componentId;
        while (length > 0) {
            ByteBuf s = components[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.setBytes(index - adjustment, src, srcIndex, localLength);
            index += localLength;
            srcIndex += localLength;
            length -= localLength;
            i ++;
        }
    }

    @Override
    public void setBytes(int index, ByteBuffer src) {
        int componentId = componentId(index);
        int limit = src.limit();
        int length = src.remaining();
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be written - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        int i = componentId;
        try {
            while (length > 0) {
                ByteBuf s = components[i];
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

    @Override
    public void setBytes(int index, ByteBuf src, int srcIndex, int length) {
        int componentId = componentId(index);
        if (index > capacity() - length || srcIndex > src.capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be written - Needs "
                    + (index + length) + " or " + (srcIndex + length) + ", maximum is "
                    + capacity() + " or " + src.capacity());
        }

        int i = componentId;
        while (length > 0) {
            ByteBuf s = components[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.setBytes(index - adjustment, src, srcIndex, localLength);
            index += localLength;
            srcIndex += localLength;
            length -= localLength;
            i ++;
        }
    }

    @Override
    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        int componentId = componentId(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to write - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        int i = componentId;
        int readBytes = 0;

        do {
            ByteBuf s = components[i];
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

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        int componentId = componentId(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to write - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        int i = componentId;
        int readBytes = 0;
        do {
            ByteBuf s = components[i];
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

    @Override
    public ByteBuf duplicate() {
        ByteBuf duplicate = new CompositeByteBuf(this);
        duplicate.setIndex(readerIndex(), writerIndex());
        return duplicate;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        int componentId = componentId(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to copy - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        ByteBuf dst = factory().getBuffer(order(), length);
        copyTo(index, length, componentId, dst);
        return dst;
    }

    private void copyTo(int index, int length, int componentId, ByteBuf dst) {
        int dstIndex = 0;
        int i = componentId;

        while (length > 0) {
            ByteBuf s = components[i];
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

    @Override
    public ByteBuf slice(int index, int length) {
        if (index == 0) {
            if (length == 0) {
                return Unpooled.EMPTY_BUFFER;
            }
        } else if (index < 0 || index > capacity() - length) {
            throw new IndexOutOfBoundsException("Invalid index: " + index
                    + " - Bytes needed: " + (index + length) + ", maximum is "
                    + capacity());
        } else if (length == 0) {
            return Unpooled.EMPTY_BUFFER;
        }

        List<ByteBuf> components = decompose(index, length);
        switch (components.size()) {
        case 0:
            return Unpooled.EMPTY_BUFFER;
        case 1:
            return components.get(0);
        default:
            return new CompositeByteBuf(order(), components);
        }
    }

    @Override
    public boolean hasNioBuffer() {
        return false;
    }
    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        throw new UnsupportedOperationException();
    }

    private ByteBuffer copiedNioBuffer(int index, int length) {
        if (components.length == 1) {
            return toNioBuffer(components[0], index, length);
        }

        ByteBuffer[] buffers = nioBuffers(index, length);
        ByteBuffer merged = ByteBuffer.allocate(length).order(order());
        for (ByteBuffer b: buffers) {
            merged.put(b);
        }
        merged.flip();
        return merged;
    }

    private ByteBuffer[] nioBuffers(int index, int length) {
        int componentId = componentId(index);
        if (index + length > capacity()) {
            throw new IndexOutOfBoundsException("Too many bytes to convert - Needs"
                    + (index + length) + ", maximum is " + capacity());
        }

        List<ByteBuffer> buffers = new ArrayList<ByteBuffer>(components.length);

        int i = componentId;
        while (length > 0) {
            ByteBuf c = components[i];
            int adjustment = indices[i];
            int localLength = Math.min(length, c.capacity() - (index - adjustment));
            buffers.add(toNioBuffer(c, index - adjustment, localLength));
            index += localLength;
            length -= localLength;
            i ++;
        }

        return buffers.toArray(new ByteBuffer[buffers.size()]);
    }

    private static ByteBuffer toNioBuffer(ByteBuf buf, int index, int length) {
        if (buf.hasNioBuffer()) {
            return buf.nioBuffer(index, length);
        } else {
            return buf.copy(index, length).nioBuffer(0, length);
        }
    }

    private int componentId(int index) {
        int lastComponentId = lastAccessedComponentId;
        if (index >= indices[lastComponentId]) {
            if (index < indices[lastComponentId + 1]) {
                return lastComponentId;
            }

            // Search right
            for (int i = lastComponentId + 1; i < components.length; i ++) {
                if (index < indices[i + 1]) {
                    lastAccessedComponentId = i;
                    return i;
                }
            }
        } else {
            // Search left
            for (int i = lastComponentId - 1; i >= 0; i --) {
                if (index >= indices[i]) {
                    lastAccessedComponentId = i;
                    return i;
                }
            }
        }

        throw new IndexOutOfBoundsException("Invalid index: " + index + ", maximum: " + indices.length);
    }

    @Override
    public void discardReadBytes() {
        // Only the bytes between readerIndex and writerIndex will be kept.
        // New readerIndex and writerIndex will become 0 and
        // (previous writerIndex - previous readerIndex) respectively.

        final int localReaderIndex = readerIndex();
        if (localReaderIndex == 0) {
            return;
        }
        int localWriterIndex = writerIndex();

        final int bytesToMove = capacity() - localReaderIndex;
        List<ByteBuf> list = decompose(localReaderIndex, bytesToMove);


        // If the list is empty we need to assign a new one because
        // we get a List that is immutable.
        //
        // See https://github.com/netty/netty/issues/325
        if (list.isEmpty()) {
            list = new ArrayList<ByteBuf>(1);
        }

        // Add a new buffer so that the capacity of this composite buffer does
        // not decrease due to the discarded components.
        // XXX Might create too many components if discarded by small amount.
        final ByteBuf padding = Unpooled.buffer(localReaderIndex).order(order());
        padding.writerIndex(localReaderIndex);
        list.add(padding);

        // Reset the index markers to get the index marker values.
        int localMarkedReaderIndex = localReaderIndex;
        try {
            resetReaderIndex();
            localMarkedReaderIndex = readerIndex();
        } catch (IndexOutOfBoundsException e) {
            // ignore
        }
        int localMarkedWriterIndex = localWriterIndex;
        try {
            resetWriterIndex();
            localMarkedWriterIndex = writerIndex();
        } catch (IndexOutOfBoundsException e) {
            // ignore
        }

        setComponents(list);

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
        return result + ", components=" + components.length + ")";
    }
}
