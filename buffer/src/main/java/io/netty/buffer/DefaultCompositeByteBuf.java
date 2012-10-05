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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;


/**
 * A virtual buffer which shows multiple buffers as a single merged buffer.  It
 * is recommended to use {@link Unpooled#wrappedBuffer(ByteBuf...)}
 * instead of calling the constructor explicitly.
 */
public class DefaultCompositeByteBuf extends AbstractByteBuf implements CompositeByteBuf {

    private final List<Component> components = new ArrayList<Component>();
    private final int maxNumComponents;
    private final Unsafe unsafe = new CompositeUnsafe();

    private Component lastAccessed;
    private int lastAccessedId;

    public DefaultCompositeByteBuf(int maxNumComponents) {
        super(ByteOrder.BIG_ENDIAN, Integer.MAX_VALUE);
        this.maxNumComponents = maxNumComponents;
    }

    public DefaultCompositeByteBuf(int maxNumComponents, ByteBuf... buffers) {
        super(ByteOrder.BIG_ENDIAN, Integer.MAX_VALUE);

        if (maxNumComponents < 2) {
            throw new IllegalArgumentException(
                    "maxNumComponents: " + maxNumComponents + " (expected: >= 2)");
        }

        this.maxNumComponents = maxNumComponents;

        addComponents(0, buffers);
        setIndex(0, capacity());
    }

    public DefaultCompositeByteBuf(int maxNumComponents, Iterable<ByteBuf> buffers) {
        super(ByteOrder.BIG_ENDIAN, Integer.MAX_VALUE);

        if (maxNumComponents < 2) {
            throw new IllegalArgumentException(
                    "maxNumComponents: " + maxNumComponents + " (expected: >= 2)");
        }

        this.maxNumComponents = maxNumComponents;
        addComponents(0, buffers);
        setIndex(0, capacity());
    }

    @Override
    public void addComponent(ByteBuf buffer) {
        addComponent(components.size(), buffer);
    }

    @Override
    public void addComponents(ByteBuf... buffers) {
        addComponents(components.size(), buffers);
    }

    @Override
    public void addComponents(Iterable<ByteBuf> buffers) {
        addComponents(components.size(), buffers);
    }

    @Override
    public void addComponent(int cIndex, ByteBuf buffer) {
        checkComponentIndex(cIndex);

        if (buffer == null) {
            throw new NullPointerException("buffer");
        }

        if (buffer instanceof Iterable) {
            @SuppressWarnings("unchecked")
            Iterable<ByteBuf> composite = (Iterable<ByteBuf>) buffer;
            addComponents(cIndex, composite);
            return;
        }

        int readableBytes = buffer.readableBytes();
        if (readableBytes == 0) {
            return;
        }

        // Consolidate if the number of components will exceed the allowed maximum by the current
        // operation.
        final int numComponents = components.size();
        if (numComponents >= maxNumComponents) {
            final int capacity = components.get(numComponents - 1).endOffset + readableBytes;

            ByteBuf consolidated = buffer.unsafe().newBuffer(capacity);
            for (int i = 0; i < numComponents; i ++) {
                ByteBuf b = components.get(i).buf;
                consolidated.writeBytes(b);
                b.unsafe().release();
            }
            consolidated.writeBytes(buffer, buffer.readerIndex(), readableBytes);

            Component c = new Component(consolidated);
            c.endOffset = c.length;
            components.clear();
            components.add(c);
            return;
        }

        // No need to consolidate - just add a component to the list.
        Component c = new Component(buffer.order(ByteOrder.BIG_ENDIAN).slice());
        if (cIndex == components.size()) {
            components.add(c);
            if (cIndex == 0) {
                c.endOffset = readableBytes;
            } else {
                Component prev = components.get(cIndex - 1);
                c.offset = prev.endOffset;
                c.endOffset = c.offset + readableBytes;
            }
        } else {
            components.add(cIndex, c);
            updateComponentOffsets(cIndex);
        }
    }

    @Override
    public void addComponents(int cIndex, ByteBuf... buffers) {
        checkComponentIndex(cIndex);

        if (buffers == null) {
            throw new NullPointerException("buffers");
        }

        ByteBuf lastBuf = null;
        int cnt = 0;
        int readableBytes = 0;
        for (ByteBuf b: buffers) {
            if (b == null) {
                break;
            }
            lastBuf = b;
            cnt ++;
            readableBytes += b.readableBytes();
        }

        if (readableBytes == 0) {
            return;
        }

        // Consolidate if the number of components will exceed the maximum by this operation.
        final int numComponents = components.size();
        if (numComponents + cnt > maxNumComponents) {
            final ByteBuf consolidated;
            if (numComponents != 0) {
                final int capacity = components.get(numComponents - 1).endOffset + readableBytes;
                consolidated = lastBuf.unsafe().newBuffer(capacity);
                for (int i = 0; i < cIndex; i ++) {
                    ByteBuf b = components.get(i).buf;
                    consolidated.writeBytes(b);
                    b.unsafe().release();
                }

                for (ByteBuf b: buffers) {
                    if (b == null) {
                        break;
                    }
                    consolidated.writeBytes(b, b.readerIndex(), b.readableBytes());
                }

                for (int i = cIndex; i < numComponents; i ++) {
                    ByteBuf b = components.get(i).buf;
                    consolidated.writeBytes(b);
                    b.unsafe().release();
                }
            } else {
                consolidated = lastBuf.unsafe().newBuffer(readableBytes);
                for (ByteBuf b: buffers) {
                    if (b == null) {
                        break;
                    }
                    consolidated.writeBytes(b, b.readerIndex(), b.readableBytes());
                }
            }

            Component c = new Component(consolidated);
            c.endOffset = c.length;
            components.clear();
            components.add(c);
            updateComponentOffsets(0);
            return;
        }

        // No need for consolidation
        for (ByteBuf b: buffers) {
            if (b == null) {
                break;
            }

            if (b.readable()) {
                addComponent(cIndex ++, b);
            }
        }
    }

    @Override
    public void addComponents(int cIndex, Iterable<ByteBuf> buffers) {
        if (buffers == null) {
            throw new NullPointerException("buffers");
        }

        if (buffers instanceof DefaultCompositeByteBuf) {
            List<Component> list = ((DefaultCompositeByteBuf) buffers).components;
            ByteBuf[] array = new ByteBuf[list.size()];
            for (int i = 0; i < array.length; i ++) {
                array[i] = list.get(i).buf;
            }
            addComponents(cIndex, array);
            return;
        }

        if (buffers instanceof List) {
            List<ByteBuf> list = (List<ByteBuf>) buffers;
            ByteBuf[] array = new ByteBuf[list.size()];
            for (int i = 0; i < array.length; i ++) {
                array[i] = list.get(i);
            }
            addComponents(cIndex, array);
            return;
        }

        if (buffers instanceof Collection) {
            Collection<ByteBuf> col = (Collection<ByteBuf>) buffers;
            ByteBuf[] array = new ByteBuf[col.size()];
            int i = 0;
            for (ByteBuf b: col) {
                array[i ++] = b;
            }
            addComponents(cIndex, array);
            return;
        }

        List<ByteBuf> list = new ArrayList<ByteBuf>();
        for (ByteBuf b: buffers) {
            list.add(b);
        }
        addComponents(cIndex, list.toArray(new ByteBuf[list.size()]));
    }

    private void checkComponentIndex(int cIndex) {
        if (cIndex < 0 || cIndex > components.size()) {
            throw new IndexOutOfBoundsException(String.format(
                    "cIndex: %d (expected: >= 0 && <= numComponents(%d))",
                    cIndex, components.size()));
        }
    }

    private void checkComponentIndex(int cIndex, int numComponents) {
        if (cIndex < 0 || cIndex + numComponents > components.size()) {
            throw new IndexOutOfBoundsException(String.format(
                    "cIndex: %d, numComponents: %d " +
                    "(expected: cIndex >= 0 && cIndex + numComponents <= totalNumComponents(%d))",
                    cIndex, numComponents, components.size()));
        }
    }

    private void updateComponentOffsets(int cIndex) {

        Component c = components.get(cIndex);
        lastAccessed = c;
        lastAccessedId = cIndex;

        if (cIndex == 0) {
            c.offset = 0;
            c.endOffset = c.length;
            cIndex ++;
        }

        for (int i = cIndex; i < components.size(); i ++) {
            Component prev = components.get(i - 1);
            Component cur = components.get(i);
            cur.offset = prev.endOffset;
            cur.endOffset = cur.offset + cur.length;
        }
    }

    @Override
    public void removeComponent(int cIndex) {
        checkComponentIndex(cIndex);
        components.remove(cIndex);
        updateComponentOffsets(cIndex);
    }

    @Override
    public void removeComponents(int cIndex, int numComponents) {
        checkComponentIndex(cIndex, numComponents);
        components.subList(cIndex, cIndex + numComponents).clear();
        updateComponentOffsets(cIndex);
    }

    @Override
    public Iterator<ByteBuf> iterator() {
        List<ByteBuf> list = new ArrayList<ByteBuf>(components.size());
        for (Component c: components) {
            list.add(c.buf);
        }
        return list.iterator();
    }

    @Override
    public List<ByteBuf> decompose(int offset, int length) {
        if (length == 0) {
            return Collections.emptyList();
        }

        if (offset + length > capacity()) {
            throw new IndexOutOfBoundsException("Too many bytes to decompose - Need "
                    + (offset + length) + ", capacity is " + capacity());
        }

        int componentId = toComponentIndex(offset);
        List<ByteBuf> slice = new ArrayList<ByteBuf>(components.size());

        // The first component
        Component firstC = components.get(componentId);
        ByteBuf first = firstC.buf.duplicate();
        first.readerIndex(offset - firstC.offset);

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
                buf = components.get(componentId).buf.duplicate();
            }
        } while (bytesToSlice > 0);

        // Slice all components because only readable bytes are interesting.
        for (int i = 0; i < slice.size(); i ++) {
            slice.set(i, slice.get(i).slice());
        }

        return slice;
    }

    @Override
    public boolean isDirect() {
        if (components.size() == 1) {
            return components.get(0).buf.isDirect();
        }
        return false;
    }

    @Override
    public boolean hasArray() {
        if (components.size() == 1) {
            return components.get(0).buf.hasArray();
        }
        return false;
    }

    @Override
    public byte[] array() {
        if (components.size() == 1) {
            return components.get(0).buf.array();
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public int arrayOffset() {
        if (components.size() == 1) {
            return components.get(0).buf.arrayOffset();
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public int capacity() {
        if (components.isEmpty()) {
            return 0;
        }
        return components.get(components.size() - 1).endOffset;
    }

    @Override
    public void capacity(int newCapacity) {
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        int oldCapacity = capacity();
        if (newCapacity > oldCapacity) {
            final int paddingLength = newCapacity - oldCapacity;
            ByteBuf padding;
            if (components.isEmpty()) {
                padding = new HeapByteBuf(paddingLength, paddingLength);
            } else {
                Component last = components.get(components.size() - 1);
                padding = last.buf.unsafe().newBuffer(paddingLength);
            }
            padding.setIndex(0, paddingLength);
            addComponent(padding);
        } else if (newCapacity < oldCapacity) {
            int bytesToTrim = oldCapacity - newCapacity;
            for (ListIterator<Component> i = components.listIterator(components.size()); i.hasPrevious();) {
                Component c = i.previous();
                if (bytesToTrim >= c.length) {
                    bytesToTrim -= c.length;
                    i.remove();
                    continue;
                }

                // Replace the last component with the trimmed slice.
                Component newC = new Component(c.buf.slice(0, c.length - bytesToTrim));
                newC.offset = c.offset;
                newC.endOffset = newC.offset + newC.length;
                c.buf.unsafe().release();
                i.set(newC);
                break;
            }

            if (readerIndex() > newCapacity) {
                setIndex(newCapacity, newCapacity);
            } else if (writerIndex() > newCapacity) {
                writerIndex(newCapacity);
            }
        }
    }

    @Override
    public int numComponents() {
        return components.size();
    }

    @Override
    public int maxNumComponents() {
        return maxNumComponents;
    }

    @Override
    public int toComponentIndex(int offset) {
        if (offset < 0 || offset >= capacity()) {
            throw new IndexOutOfBoundsException(String.format(
                    "offset: %d (expected: >= 0 && < capacity(%d))", offset, capacity()));
        }

        Component c = lastAccessed;
        if (c == null) {
            lastAccessed = c = components.get(0);
        }
        if (offset >= c.offset) {
            if (offset < c.endOffset) {
                return lastAccessedId;
            }

            // Search right
            for (int i = lastAccessedId + 1; i < components.size(); i ++) {
                c = components.get(i);
                if (offset < c.endOffset) {
                    lastAccessedId = i;
                    lastAccessed = c;
                    return i;
                }
            }
        } else {
            // Search left
            for (int i = lastAccessedId - 1; i >= 0; i --) {
                c = components.get(i);
                if (offset >= c.offset) {
                    lastAccessedId = i;
                    lastAccessed = c;
                    return i;
                }
            }
        }

        throw new IllegalStateException("should not reach here - concurrent modification?");
    }

    @Override
    public int toByteIndex(int cIndex) {
        checkComponentIndex(cIndex);
        return components.get(cIndex).offset;
    }

    @Override
    public byte getByte(int index) {
        Component c = findComponent(index);
        return c.buf.getByte(index - c.offset);
    }

    @Override
    public short getShort(int index) {
        Component c = findComponent(index);
        if (index + 2 <= c.endOffset) {
            return c.buf.getShort(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (short) ((getByte(index) & 0xff) << 8 | getByte(index + 1) & 0xff);
        } else {
            return (short) (getByte(index) & 0xff | (getByte(index + 1) & 0xff) << 8);
        }
    }

    @Override
    public int getUnsignedMedium(int index) {
        Component c = findComponent(index);
        if (index + 3 <= c.endOffset) {
            return c.buf.getUnsignedMedium(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getShort(index) & 0xffff) << 8 | getByte(index + 2) & 0xff;
        } else {
            return getShort(index) & 0xFFFF | (getByte(index + 2) & 0xFF) << 16;
        }
    }

    @Override
    public int getInt(int index) {
        Component c = findComponent(index);
        if (index + 4 <= c.endOffset) {
            return c.buf.getInt(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getShort(index) & 0xffff) << 16 | getShort(index + 2) & 0xffff;
        } else {
            return getShort(index) & 0xFFFF | (getShort(index + 2) & 0xFFFF) << 16;
        }
    }

    @Override
    public long getLong(int index) {
        Component c = findComponent(index);
        if (index + 8 <= c.endOffset) {
            return c.buf.getLong(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (getInt(index) & 0xffffffffL) << 32 | getInt(index + 4) & 0xffffffffL;
        } else {
            return getInt(index) & 0xFFFFFFFFL | (getInt(index + 4) & 0xFFFFFFFFL) << 32;
        }
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        if (index > capacity() - length || dstIndex > dst.length - length) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Needs "
                    + (index + length) + ", maximum is " + capacity() + " or "
                    + dst.length);
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("index must be >= 0");
        }
        if (length == 0) {
            return;
        }
        int i = toComponentIndex(index);

        while (length > 0) {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
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
        int limit = dst.limit();
        int length = dst.remaining();

        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be read - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("index must be >= 0");
        }
        if (length == 0) {
            return;
        }
        int i = toComponentIndex(index);
        try {
            while (length > 0) {
                Component c = components.get(i);
                ByteBuf s = c.buf;
                int adjustment = c.offset;
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
        if (index > capacity() - length || dstIndex > dst.capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be read - Needs "
                    + (index + length) + " or " + (dstIndex + length) + ", maximum is "
                    + capacity() + " or " + dst.capacity());
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("index must be >= 0");
        }
        if (length == 0) {
            return;
        }
        int i = toComponentIndex(index);
        while (length > 0) {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
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
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be read - needs "
                    + (index + length) + ", maximum of " + capacity());
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("index must be >= 0");
        }
        if (length == 0) {
            return;
        }

        int i = toComponentIndex(index);
        while (length > 0) {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            s.getBytes(index - adjustment, out, localLength);
            index += localLength;
            length -= localLength;
            i ++;
        }
    }

    @Override
    public void setByte(int index, int value) {
        Component c = findComponent(index);
        c.buf.setByte(index - c.offset, value);
    }

    @Override
    public void setShort(int index, int value) {
        Component c = findComponent(index);
        if (index + 2 <= c.endOffset) {
            c.buf.setShort(index - c.offset, value);
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
        Component c = findComponent(index);
        if (index + 3 <= c.endOffset) {
            c.buf.setMedium(index - c.offset, value);
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
        Component c = findComponent(index);
        if (index + 4 <= c.endOffset) {
            c.buf.setInt(index - c.offset, value);
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
        Component c = findComponent(index);
        if (index + 8 <= c.endOffset) {
            c.buf.setLong(index - c.offset, value);
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
        int componentId = toComponentIndex(index);
        if (index > capacity() - length || srcIndex > src.length - length) {
            throw new IndexOutOfBoundsException("Too many bytes to read - needs "
                    + (index + length) + " or " + (srcIndex + length) + ", maximum is "
                    + capacity() + " or " + src.length);
        }

        int i = componentId;
        while (length > 0) {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
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
        int componentId = toComponentIndex(index);
        int limit = src.limit();
        int length = src.remaining();
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be written - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        int i = componentId;
        try {
            while (length > 0) {
                Component c = components.get(i);
                ByteBuf s = c.buf;
                int adjustment = c.offset;
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
        int componentId = toComponentIndex(index);
        if (index > capacity() - length || srcIndex > src.capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to be written - Needs "
                    + (index + length) + " or " + (srcIndex + length) + ", maximum is "
                    + capacity() + " or " + src.capacity());
        }

        int i = componentId;
        while (length > 0) {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
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
        int componentId = toComponentIndex(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to write - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        int i = componentId;
        int readBytes = 0;

        do {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
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
        int componentId = toComponentIndex(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to write - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        int i = componentId;
        int readBytes = 0;
        do {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            int localReadBytes = s.setBytes(index - adjustment, in, localLength);

            if (localReadBytes == 0) {
                break;
            }

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
    public ByteBuf copy(int index, int length) {
        int componentId = toComponentIndex(index);
        if (index > capacity() - length) {
            throw new IndexOutOfBoundsException("Too many bytes to copy - Needs "
                    + (index + length) + ", maximum is " + capacity());
        }

        ByteBuf dst = unsafe().newBuffer(length);
        copyTo(index, length, componentId, dst);
        return dst;
    }

    private void copyTo(int index, int length, int componentId, ByteBuf dst) {
        int dstIndex = 0;
        int i = componentId;

        while (length > 0) {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
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
    public ByteBuf component(int cIndex) {
        checkComponentIndex(cIndex);
        return components.get(cIndex).buf;
    }

    @Override
    public ByteBuf componentAtOffset(int offset) {
        return findComponent(offset).buf;
    }

    private Component findComponent(int offset) {
        if (offset < 0 || offset >= capacity()) {
            throw new IndexOutOfBoundsException(String.format(
                    "offset: %d (expected: >= 0 && < capacity(%d))", offset, capacity()));
        }

        Component c = lastAccessed;
        if (c == null) {
            lastAccessed = c = components.get(0);
        }

        if (offset >= c.offset) {
            if (offset < c.endOffset) {
                return c;
            }

            // Search right
            for (int i = lastAccessedId + 1; i < components.size(); i ++) {
                c = components.get(i);
                if (offset < c.endOffset) {
                    lastAccessedId = i;
                    lastAccessed = c;
                    return c;
                }
            }
        } else {
            // Search left
            for (int i = lastAccessedId - 1; i >= 0; i --) {
                c = components.get(i);
                if (offset >= c.offset) {
                    lastAccessedId = i;
                    lastAccessed = c;
                    return c;
                }
            }
        }

        throw new IllegalStateException("should not reach here - concurrent modification?");
    }

    @Override
    public boolean hasNioBuffer() {
        if (components.size() == 1) {
            return components.get(0).buf.hasNioBuffer();
        }
        return false;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        if (components.size() == 1) {
            return components.get(0).buf.nioBuffer(index, length);
        }
        throw new UnsupportedOperationException();
    }

    private ByteBuffer copiedNioBuffer(int index, int length) {
        if (components.size() == 1) {
            return toNioBuffer(components.get(0).buf, index, length);
        }

        ByteBuffer[] buffers = nioBuffers(index, length);
        ByteBuffer merged = ByteBuffer.allocate(length).order(order());
        for (ByteBuffer b: buffers) {
            merged.put(b);
        }
        merged.flip();
        return merged;
    }

    @Override
    public boolean hasNioBuffers() {
        return true;
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        if (index + length > capacity()) {
            throw new IndexOutOfBoundsException("Too many bytes to convert - Needs"
                    + (index + length) + ", maximum is " + capacity());
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("index must be >= 0");
        }
        if (length == 0) {
            return new ByteBuffer[0];
        }
        int componentId = toComponentIndex(index);

        List<ByteBuffer> buffers = new ArrayList<ByteBuffer>(components.size());

        int i = componentId;
        while (length > 0) {
            Component c = components.get(i);
            ByteBuf s = c.buf;
            int adjustment = c.offset;
            int localLength = Math.min(length, s.capacity() - (index - adjustment));
            buffers.add(toNioBuffer(s, index - adjustment, localLength));
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

    @Override
    public void consolidate() {
        final int numComponents = numComponents();
        if (numComponents <= 1) {
            return;
        }

        final Component last = components.get(numComponents - 1);
        final int capacity = last.endOffset;
        final ByteBuf consolidated = last.buf.unsafe().newBuffer(capacity);

        for (int i = 0; i < numComponents; i ++) {
            ByteBuf b = components.get(i).buf;
            consolidated.writeBytes(b);
            b.unsafe().release();
        }

        components.clear();
        components.add(new Component(consolidated));
        updateComponentOffsets(0);
    }

    @Override
    public void consolidate(int cIndex, int numComponents) {
        checkComponentIndex(cIndex, numComponents);
        if (numComponents <= 1) {
            return;
        }

        final int endCIndex = cIndex + numComponents;
        final Component last = components.get(endCIndex - 1);
        final int capacity = last.endOffset - components.get(cIndex).offset;
        final ByteBuf consolidated = last.buf.unsafe().newBuffer(capacity);

        for (int i = cIndex; i < endCIndex; i ++) {
            ByteBuf b = components.get(i).buf;
            consolidated.writeBytes(b);
            b.unsafe().release();
        }

        components.subList(cIndex + 1, endCIndex).clear();
        components.set(cIndex, new Component(consolidated));
        updateComponentOffsets(cIndex);
    }

    @Override
    public void discardReadComponents() {
        final int readerIndex = readerIndex();
        if (readerIndex == 0) {
            return;
        }

        // Discard everything if (readerIndex = writerIndex = capacity).
        int writerIndex = writerIndex();
        if (readerIndex == writerIndex && writerIndex == capacity()) {
            for (Component c: components) {
                c.buf.unsafe().release();
            }
            components.clear();
            setIndex(0, 0);
            adjustMarkers(readerIndex);
            return;
        }

        // Remove read components.
        int firstComponentId = toComponentIndex(readerIndex);
        for (int i = 0; i < firstComponentId; i ++) {
            components.get(i).buf.unsafe().release();
        }
        components.subList(0, firstComponentId).clear();

        // Update indexes and markers.
        Component first = components.get(0);
        updateComponentOffsets(0);
        setIndex(readerIndex - first.offset, writerIndex - first.offset);
        adjustMarkers(first.offset);
    }

    @Override
    public void discardReadBytes() {
        final int readerIndex = readerIndex();
        if (readerIndex == 0) {
            return;
        }

        // Discard everything if (readerIndex = writerIndex = capacity).
        int writerIndex = writerIndex();
        if (readerIndex == writerIndex && writerIndex == capacity()) {
            for (Component c: components) {
                c.buf.unsafe().release();
            }
            components.clear();
            setIndex(0, 0);
            adjustMarkers(readerIndex);
            return;
        }

        // Remove read components.
        int firstComponentId = toComponentIndex(readerIndex);
        for (int i = 0; i < firstComponentId; i ++) {
            components.get(i).buf.unsafe().release();
        }
        components.subList(0, firstComponentId).clear();

        // Remove or replace the first readable component with a new slice.
        Component c = components.get(0);
        int adjustment = readerIndex - c.offset;
        if (adjustment == c.length) {
            // new slice would be empty, so remove instead
            components.remove(0);
        } else {
            Component newC = new Component(c.buf.slice(adjustment, c.length - adjustment));
            c.buf.unsafe().release();
            components.set(0, newC);
        }

        // Update indexes and markers.
        updateComponentOffsets(0);
        setIndex(0, writerIndex - readerIndex);
        adjustMarkers(readerIndex);
    }

    @Override
    public String toString() {
        String result = super.toString();
        result = result.substring(0, result.length() - 1);
        return result + ", components=" + components.size() + ")";
    }

    private static final class Component {
        final ByteBuf buf;
        final int length;
        int offset;
        int endOffset;

        Component(ByteBuf buf) {
            this.buf = buf;
            length = buf.readableBytes();
        }
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    private final class CompositeUnsafe implements Unsafe {
        @Override
        public ByteBuffer nioBuffer() {
            if (components.size() == 1) {
                return components.get(0).buf.unsafe().nioBuffer();
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuffer[] nioBuffers() {
            ByteBuffer[] nioBuffers = new ByteBuffer[components.size()];
            int index = 0;
            for (Component component : components) {
                nioBuffers[index++] = component.buf.unsafe().nioBuffer();
            }
            return nioBuffers;
        }

        @Override
        public ByteBuf newBuffer(int initialCapacity) {
            CompositeByteBuf buf = new DefaultCompositeByteBuf(maxNumComponents);
            buf.addComponent(new HeapByteBuf(new byte[initialCapacity], initialCapacity));
            return buf;
        }

        @Override
        public void discardSomeReadBytes() {
            discardReadComponents();
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
                for (Component c: components) {
                    c.buf.unsafe().release();
                }

                components.clear();
                lastAccessed = null;
            }
        }
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return nioBuffers(readerIndex(), readableBytes());
    }
}
