/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.RecyclableArrayList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.Collections;

/**
 * {@link ByteBuf} implementation which allows to wrap an array of {@link ByteBuf} in a read-only mode.
 * This is useful to write an array of {@link ByteBuf}s.
 */
final class FixedCompositeByteBuf extends AbstractReferenceCountedByteBuf {
    private static final ByteBuf[] EMPTY = { Unpooled.EMPTY_BUFFER };
    private final int nioBufferCount;
    private final int capacity;
    private final ByteBufAllocator allocator;
    private final ByteOrder order;
    private final ByteBuf[] buffers;
    private final boolean direct;

    FixedCompositeByteBuf(ByteBufAllocator allocator, ByteBuf... buffers) {
        super(AbstractByteBufAllocator.DEFAULT_MAX_CAPACITY);
        if (buffers.length == 0) {
            this.buffers = EMPTY;
            order = ByteOrder.BIG_ENDIAN;
            nioBufferCount = 1;
            capacity = 0;
            direct = Unpooled.EMPTY_BUFFER.isDirect();
        } else {
            ByteBuf b = buffers[0];
            this.buffers = buffers;
            boolean direct = true;
            int nioBufferCount = b.nioBufferCount();
            int capacity = b.readableBytes();
            order = b.order();
            for (int i = 1; i < buffers.length; i++) {
                b = buffers[i];
                if (buffers[i].order() != order) {
                    throw new IllegalArgumentException("All ByteBufs need to have same ByteOrder");
                }
                nioBufferCount += b.nioBufferCount();
                capacity += b.readableBytes();
                if (!b.isDirect()) {
                    direct = false;
                }
            }
            this.nioBufferCount = nioBufferCount;
            this.capacity = capacity;
            this.direct = direct;
        }
        setIndex(0, capacity());
        this.allocator = allocator;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public boolean isWritable(int size) {
        return false;
    }

    @Override
    public ByteBuf discardReadBytes() {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setByte(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setShort(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setShortLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setMedium(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setInt(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setIntLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setLong(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setLongLE(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int setBytes(int index, InputStream in, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int maxCapacity() {
        return capacity;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBufAllocator alloc() {
        return allocator;
    }

    @Override
    public ByteOrder order() {
        return order;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }

    @Override
    public boolean isDirect() {
        return direct;
    }

    private Component findComponent(int index) {
        int readable = 0;
        for (int i = 0 ; i < buffers.length; i++) {
            Component comp = null;
            ByteBuf b = buffers[i];
            if (b instanceof Component) {
                comp = (Component) b;
                b = comp.buf;
            }
            readable += b.readableBytes();
            if (index < readable) {
                if (comp == null) {
                    // Create a new component and store it in the array so it not create a new object
                    // on the next access.
                    comp = new Component(i, readable - b.readableBytes(), b);
                    buffers[i] = comp;
                }
                return comp;
            }
        }
        throw new IllegalStateException();
    }

    /**
     * Return the {@link ByteBuf} stored at the given index of the array.
     */
    private ByteBuf buffer(int i) {
        ByteBuf b = buffers[i];
        return b instanceof Component ? ((Component) b).buf : b;
    }

    @Override
    public byte getByte(int index) {
        return _getByte(index);
    }

    @Override
    protected byte _getByte(int index) {
        Component c = findComponent(index);
        return c.buf.getByte(index - c.offset);
    }

    @Override
    protected short _getShort(int index) {
        Component c = findComponent(index);
        if (index + 2 <= c.endOffset) {
            return c.buf.getShort(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (short) ((_getByte(index) & 0xff) << 8 | _getByte(index + 1) & 0xff);
        } else {
            return (short) (_getByte(index) & 0xff | (_getByte(index + 1) & 0xff) << 8);
        }
    }

    @Override
    protected short _getShortLE(int index) {
        Component c = findComponent(index);
        if (index + 2 <= c.endOffset) {
            return c.buf.getShortLE(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (short) (_getByte(index) & 0xff | (_getByte(index + 1) & 0xff) << 8);
        } else {
            return (short) ((_getByte(index) & 0xff) << 8 | _getByte(index + 1) & 0xff);
        }
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        Component c = findComponent(index);
        if (index + 3 <= c.endOffset) {
            return c.buf.getUnsignedMedium(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (_getShort(index) & 0xffff) << 8 | _getByte(index + 2) & 0xff;
        } else {
            return _getShort(index) & 0xFFFF | (_getByte(index + 2) & 0xFF) << 16;
        }
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        Component c = findComponent(index);
        if (index + 3 <= c.endOffset) {
            return c.buf.getUnsignedMediumLE(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return _getShortLE(index) & 0xffff | (_getByte(index + 2) & 0xff) << 16;
        } else {
            return (_getShortLE(index) & 0xffff) << 8 | _getByte(index + 2) & 0xff;
        }
    }

    @Override
    protected int _getInt(int index) {
        Component c = findComponent(index);
        if (index + 4 <= c.endOffset) {
            return c.buf.getInt(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (_getShort(index) & 0xffff) << 16 | _getShort(index + 2) & 0xffff;
        } else {
            return _getShort(index) & 0xFFFF | (_getShort(index + 2) & 0xFFFF) << 16;
        }
    }

    @Override
    protected int _getIntLE(int index) {
        Component c = findComponent(index);
        if (index + 4 <= c.endOffset) {
            return c.buf.getIntLE(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return _getShortLE(index) & 0xFFFF | (_getShortLE(index + 2) & 0xFFFF) << 16;
        } else {
            return (_getShortLE(index) & 0xffff) << 16 | _getShortLE(index + 2) & 0xffff;
        }
    }

    @Override
    protected long _getLong(int index) {
        Component c = findComponent(index);
        if (index + 8 <= c.endOffset) {
            return c.buf.getLong(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return (_getInt(index) & 0xffffffffL) << 32 | _getInt(index + 4) & 0xffffffffL;
        } else {
            return _getInt(index) & 0xFFFFFFFFL | (_getInt(index + 4) & 0xFFFFFFFFL) << 32;
        }
    }

    @Override
    protected long _getLongLE(int index) {
        Component c = findComponent(index);
        if (index + 8 <= c.endOffset) {
            return c.buf.getLongLE(index - c.offset);
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            return _getIntLE(index) & 0xffffffffL | (_getIntLE(index + 4) & 0xffffffffL) << 32;
        } else {
            return (_getIntLE(index) & 0xffffffffL) << 32 | _getIntLE(index + 4) & 0xffffffffL;
        }
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        if (length == 0) {
            return this;
        }

        Component c = findComponent(index);
        int i = c.index;
        int adjustment = c.offset;
        ByteBuf s = c.buf;
        for (;;) {
            int localLength = Math.min(length, s.readableBytes() - (index - adjustment));
            s.getBytes(index - adjustment, dst, dstIndex, localLength);
            index += localLength;
            dstIndex += localLength;
            length -= localLength;
            adjustment += s.readableBytes();
            if (length <= 0) {
                break;
            }
            s = buffer(++i);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        int limit = dst.limit();
        int length = dst.remaining();

        checkIndex(index, length);
        if (length == 0) {
            return this;
        }

        try {
            Component c = findComponent(index);
            int i = c.index;
            int adjustment = c.offset;
            ByteBuf s = c.buf;
            for (;;) {
                int localLength = Math.min(length, s.readableBytes() - (index - adjustment));
                dst.limit(dst.position() + localLength);
                s.getBytes(index - adjustment, dst);
                index += localLength;
                length -= localLength;
                adjustment += s.readableBytes();
                if (length <= 0) {
                    break;
                }
                s = buffer(++i);
            }
        } finally {
            dst.limit(limit);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (length == 0) {
            return this;
        }

        Component c = findComponent(index);
        int i = c.index;
        int adjustment = c.offset;
        ByteBuf s = c.buf;
        for (;;) {
            int localLength = Math.min(length, s.readableBytes() - (index - adjustment));
            s.getBytes(index - adjustment, dst, dstIndex, localLength);
            index += localLength;
            dstIndex += localLength;
            length -= localLength;
            adjustment += s.readableBytes();
            if (length <= 0) {
                break;
            }
            s = buffer(++i);
        }
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        int count = nioBufferCount();
        if (count == 1) {
            return out.write(internalNioBuffer(index, length));
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
    public int getBytes(int index, FileChannel out, long position, int length)
            throws IOException {
        int count = nioBufferCount();
        if (count == 1) {
            return out.write(internalNioBuffer(index, length), position);
        } else {
            long writtenBytes = 0;
            for (ByteBuffer buf : nioBuffers(index, length)) {
                writtenBytes += out.write(buf, position + writtenBytes);
            }
            if (writtenBytes > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            } else {
                return (int) writtenBytes;
            }
        }
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex(index, length);
        if (length == 0) {
            return this;
        }

        Component c = findComponent(index);
        int i = c.index;
        int adjustment = c.offset;
        ByteBuf s = c.buf;
        for (;;) {
            int localLength = Math.min(length, s.readableBytes() - (index - adjustment));
            s.getBytes(index - adjustment, out, localLength);
            index += localLength;
            length -= localLength;
            adjustment += s.readableBytes();
            if (length <= 0) {
                break;
            }
            s = buffer(++i);
        }
        return this;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        boolean release = true;
        ByteBuf buf = alloc().buffer(length);
        try {
            buf.writeBytes(this, index, length);
            release = false;
            return buf;
        } finally {
            if (release) {
                buf.release();
            }
        }
    }

    @Override
    public int nioBufferCount() {
        return nioBufferCount;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        if (buffers.length == 1) {
            ByteBuf buf = buffer(0);
            if (buf.nioBufferCount() == 1) {
                return buf.nioBuffer(index, length);
            }
        }
        ByteBuffer merged = ByteBuffer.allocate(length).order(order());
        ByteBuffer[] buffers = nioBuffers(index, length);

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < buffers.length; i++) {
            merged.put(buffers[i]);
        }

        merged.flip();
        return merged;
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        if (buffers.length == 1) {
            return buffer(0).internalNioBuffer(index, length);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        checkIndex(index, length);
        if (length == 0) {
            return EmptyArrays.EMPTY_BYTE_BUFFERS;
        }

        RecyclableArrayList array = RecyclableArrayList.newInstance(buffers.length);
        try {
            Component c = findComponent(index);
            int i = c.index;
            int adjustment = c.offset;
            ByteBuf s = c.buf;
            for (;;) {
                int localLength = Math.min(length, s.readableBytes() - (index - adjustment));
                switch (s.nioBufferCount()) {
                    case 0:
                        throw new UnsupportedOperationException();
                    case 1:
                        array.add(s.nioBuffer(index - adjustment, localLength));
                        break;
                    default:
                        Collections.addAll(array, s.nioBuffers(index - adjustment, localLength));
                }

                index += localLength;
                length -= localLength;
                adjustment += s.readableBytes();
                if (length <= 0) {
                    break;
                }
                s = buffer(++i);
            }

            return array.toArray(new ByteBuffer[0]);
        } finally {
            array.recycle();
        }
    }

    @Override
    public boolean hasArray() {
        switch (buffers.length) {
            case 0:
                return true;
            case 1:
                return buffer(0).hasArray();
            default:
                return false;
        }
    }

    @Override
    public byte[] array() {
        switch (buffers.length) {
            case 0:
                return EmptyArrays.EMPTY_BYTES;
            case 1:
                return buffer(0).array();
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public int arrayOffset() {
        switch (buffers.length) {
            case 0:
                return 0;
            case 1:
                return buffer(0).arrayOffset();
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean hasMemoryAddress() {
        switch (buffers.length) {
            case 0:
                return Unpooled.EMPTY_BUFFER.hasMemoryAddress();
            case 1:
                return buffer(0).hasMemoryAddress();
            default:
                return false;
        }
    }

    @Override
    public long memoryAddress() {
        switch (buffers.length) {
            case 0:
                return Unpooled.EMPTY_BUFFER.memoryAddress();
            case 1:
                return buffer(0).memoryAddress();
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    protected void deallocate() {
        for (int i = 0; i < buffers.length; i++) {
             buffer(i).release();
        }
    }

    @Override
    public String toString() {
        String result = super.toString();
        result = result.substring(0, result.length() - 1);
        return result + ", components=" + buffers.length + ')';
    }

    private static final class Component extends WrappedByteBuf {
        private final int index;
        private final int offset;
        private final int endOffset;

        Component(int index, int offset, ByteBuf buf) {
            super(buf);
            this.index = index;
            this.offset = offset;
            endOffset = offset + buf.readableBytes();
        }
    }
}
