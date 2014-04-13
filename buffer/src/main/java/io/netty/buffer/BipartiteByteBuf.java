/*
 * Copyright 2014 The Netty Project
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
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import io.netty.util.ResourceLeak;
import io.netty.util.internal.EmptyArrays;

public class BipartiteByteBuf extends AbstractReferenceCountedByteBuf {
    private int part1Len;
    private int part2Len;
    private ByteBuf part1;
    private ByteBuf part2;
    private boolean freed;
    private final ByteBufAllocator alloc;
    private final boolean directByDefault;
    private final ResourceLeak leak;
    private static final ByteBuffer FULL_BYTEBUFFER = (ByteBuffer) ByteBuffer.allocate(1).position(1);

    public BipartiteByteBuf(ByteBufAllocator alloc, boolean directByDefault, ByteBuf part1, ByteBuf part2) {
        super(Integer.MAX_VALUE);

        if (alloc == null) {
            throw new NullPointerException("alloc");
        }

        if (part1 == null) {
            throw new NullPointerException("part1");
        }

        if (part2 == null) {
            throw new NullPointerException("part2");
        }

        this.alloc = alloc;
        this.directByDefault = directByDefault;
        leak = leakDetector.open(this);

        part1(part1);
        part2(part2);
    }

    private void part1(ByteBuf buffer) {
        part1 = buffer.order(ByteOrder.BIG_ENDIAN).slice();
        part1Len = buffer.readableBytes();
    }

    public ByteBuf part1() {
        return part1.duplicate();
    }

    private void part2(ByteBuf buffer) {
        part2 = buffer.order(ByteOrder.BIG_ENDIAN).slice();
        part2Len = buffer.readableBytes();
    }

    public ByteBuf part2() {
        return part2.duplicate();
    }

    @Override
    protected void deallocate() {
        if (!freed) {
            part1.release();
            part2.release();
            freed = true;
        }
    }

    @Override
    protected byte _getByte(int index) {
        checkIndex(index);
        if (index < part1Len) {
            return part1.getByte(index);
        } else {
            return part2.getByte(index - part1Len);
        }
    }

    @Override
    protected short _getShort(int index) {
        assert order() == ByteOrder.BIG_ENDIAN;

        checkIndex(index, 2);
        if (index + 1 < part1Len) {
            return part1.getShort(index);
        } else if (index >= part1Len) {
            return part2.getShort(index - part1Len);
        } else {
            // one byte in part1, one byte in part2
            return (short) ((part1.getByte(index) & 0xff) << 8 | part2.getByte(0) & 0xff);
        }
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        assert order() == ByteOrder.BIG_ENDIAN;

        checkIndex(index, 3);
        if (index + 2 < part1Len) {
            return part1.getUnsignedMedium(index);
        } else if (index >= part1Len) {
            return part2.getUnsignedMedium(index - part1Len);
        } else {
            return (_getShort(index) & 0xffff) << 8 | _getByte(index + 2) & 0xff;
        }
    }

    @Override
    protected int _getInt(int index) {
        assert order() == ByteOrder.BIG_ENDIAN;

        checkIndex(index, 4);
        if (index + 3 < part1Len) {
            return part1.getInt(index);
        } else if (index >= part1Len) {
            return part2.getInt(index - part1Len);
        } else {
            return (_getShort(index) & 0xffff) << 16 | _getShort(index + 2) & 0xffff;
        }
    }

    @Override
    protected long _getLong(int index) {
        assert order() == ByteOrder.BIG_ENDIAN;

        checkIndex(index, 8);
        if (index + 7 < part1Len) {
            return part1.getLong(index);
        } else if (index >= part1Len) {
            return part2.getLong(index - part1Len);
        } else {
            return (_getInt(index) & 0xffffffffL) << 32 | _getInt(index + 4) & 0xffffffffL;
        }
    }

    @Override
    protected void _setByte(int index, int value) {
        checkIndex(index);
        if (index < part1Len) {
            part1.setByte(index, value);
        } else {
            part2.setByte(index - part1Len, value);
        }
    }

    @Override
    protected void _setShort(int index, int value) {
        assert order() == ByteOrder.BIG_ENDIAN;

        checkIndex(index, 2);
        if (index + 1 < part1Len) {
            part1.setShort(index, value);
        } else if (index >= part1Len) {
            part2.setShort(index - part1Len, value);
        } else {
            part1.setByte(index, value >>> 8 & 0xff);
            part2.setByte(0, value & 0xff);
        }
    }

    @Override
    protected void _setMedium(int index, int value) {
        assert order() == ByteOrder.BIG_ENDIAN;

        checkIndex(index, 3);
        if (index + 2 < part1Len) {
            part1.setMedium(index, value);
        } else if (index >= part1Len) {
            part2.setMedium(index - part1Len, value);
        } else {
            _setShort(index, value >>> 8 & 0xffff);
            _setByte(index + 2, value & 0xff);
        }
    }

    @Override
    protected void _setInt(int index, int value) {
        assert order() == ByteOrder.BIG_ENDIAN;

        checkIndex(index, 4);
        if (index + 3 < part1Len) {
            part1.setInt(index, value);
        } else if (index >= part1Len) {
            part2.setInt(index - part1Len, value);
        } else {
            _setShort(index, value >>> 16 & 0xffff);
            _setShort(index + 2, value & 0xffff);
        }
    }

    @Override
    protected void _setLong(int index, long value) {
        assert order() == ByteOrder.BIG_ENDIAN;

        checkIndex(index, 8);
        if (index + 7 < part1Len) {
            part1.setLong(index, value);
        } else if (index >= part1Len) {
            part2.setLong(index - part1Len, value);
        } else {
            _setInt(index, (int) (value >>> 32));
            _setInt(index + 4, (int) value);
        }
    }

    @Override
    public int capacity() {
        return part1Len + part2Len;
    }

    @Override
    public BipartiteByteBuf capacity(int newCapacity) {
        final int capacity = capacity();
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        if (newCapacity > capacity) {
            final int paddingLen = newCapacity - capacity;
            ByteBuf padding = allocBuffer(paddingLen);
            padding.setIndex(0, paddingLen);

            BipartiteByteBuf newPart2 = Unpooled.bipartiteBuffer(part2, padding);
            newPart2.setIndex(0, part2Len + paddingLen);
            part2(newPart2);
        } else if (newCapacity < capacity) {
            throw new IllegalArgumentException("Downsizing is not supported. (newCapacity: " + newCapacity +
                    ", capacity: " + capacity + ')');
        }

        return this;
    }

    @Override
    public ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }

    @Override
    public boolean isDirect() {
        return part1.isDirect() && part2.isDirect();
    }

    @Override
    public BipartiteByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (index < part1Len) {
            final int part1Read = Math.min(part1Len - index, length);
            part1.getBytes(index, dst, dstIndex, part1Read);
            length -= part1Read;

            if (length > 0) {
                part2.getBytes(0, dst, dstIndex + part1Read, length);
            }
        } else {
            part2.getBytes(index - part1Len, dst, dstIndex, length);
        }
        return this;
    }

    @Override
    public BipartiteByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        if (index < part1Len) {
            final int part1Read = Math.min(part1Len - index, length);
            part1.getBytes(index, dst, dstIndex, part1Read);
            length -= part1Read;

            if (length > 0) {
                part2.getBytes(0, dst, dstIndex + part1Read, length);
            }
        } else {
            part2.getBytes(index - part1Len, dst, dstIndex, length);
        }
        return this;
    }

    @Override
    public BipartiteByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex(index, length);
        if (index < part1Len) {
            final int part1Read = Math.min(part1Len - index, length);
            part1.getBytes(index, out, part1Read);
            length -= part1Read;

            if (length > 0) {
                part2.getBytes(0, out, length);
            }
        } else {
            part2.getBytes(index - part1Len, out, length);
        }
        return this;
    }

    @Override
    public BipartiteByteBuf getBytes(int index, ByteBuffer dst) {
        int length = dst.remaining();
        checkIndex(index, length);
        if (index < part1Len) {
            final int limit = dst.limit();
            try {
                final int part1Read = Math.min(part1Len - index, length);
                dst.limit(dst.position() + part1Read);
                part1.getBytes(index, dst);
                length -= part1Read;

                if (length > 0) {
                    dst.limit(limit);
                    part2.getBytes(0, dst);
                }
            } finally {
                dst.limit(limit);
            }
        } else {
            part2.getBytes(index - part1Len, dst);
        }
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        if (nioBufferCount() == 1) {
            return out.write(internalNioBuffer(index, length));
        } else {
            long writtenBytes = out.write(nioBuffers(index, length));
            return (int) Math.min(writtenBytes, Integer.MAX_VALUE);
        }
    }

    @Override
    public BipartiteByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        if (index < part1Len) {
            final int part1Write = Math.min(part1Len - index, length);
            part1.setBytes(index, src, srcIndex, part1Write);
            length -= part1Write;

            if (length > 0) {
                part2.setBytes(0, src, srcIndex + part1Write, length);
            }
        } else {
            part2.setBytes(index - part1Len, src, srcIndex, length);
        }
        return this;
    }

    @Override
    public BipartiteByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.length);
        if (index < part1Len) {
            final int part1Write = Math.min(part1Len - index, length);
            part1.setBytes(index, src, srcIndex, part1Write);
            length -= part1Write;

            if (length > 0) {
                part2.setBytes(0, src, srcIndex + part1Write, length);
            }
        } else {
            part2.setBytes(index - part1Len, src, srcIndex, length);
        }
        return this;
    }

    @Override
    public BipartiteByteBuf setBytes(int index, ByteBuffer src) {
        int length = src.remaining();
        checkIndex(index, length);
        if (index < part1Len) {
            final int limit = src.limit();
            try {
                final int part1Write = Math.min(part1Len - index, length);
                src.limit(src.position() + part1Write);
                part1.setBytes(index, src);
                length -= part1Write;

                if (length > 0) {
                    src.limit(limit);
                    part2.setBytes(0, src);
                }
            } finally {
                src.limit(limit);
            }
        } else {
            part2.setBytes(index - part1Len, src);
        }
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkIndex(index, length);
        if (length == 0) {
            return in.read(EmptyArrays.EMPTY_BYTES);
        }

        int writtenBytes = 0;
        do {
            int localWrittenBytes;
            if (index < part1Len) {
                final int part1Write = Math.min(part1Len - index, length);
                localWrittenBytes = part1.setBytes(index, in, part1Write);
            } else {
                localWrittenBytes = part2.setBytes(index - part1Len, in, length);
            }

            if (localWrittenBytes == 0) {
                return writtenBytes;
            }

            if (localWrittenBytes < 0) {
                if (writtenBytes == 0) {
                    return -1;
                } else {
                    return writtenBytes;
                }
            }

            index += localWrittenBytes;
            writtenBytes += localWrittenBytes;
            length -= localWrittenBytes;
        } while (length > 0);
        return writtenBytes;
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        checkIndex(index, length);
        // see https://github.com/netty/netty/issues/1644
        if (length == 0) {
            return in.read(FULL_BYTEBUFFER);
        }
        int writtenBytes = 0;
        do {
            int localWrittenBytes;
            if (index < part1Len) {
                final int part1Write = Math.min(part1Len - index, length);
                localWrittenBytes = part1.setBytes(index, in, part1Write);
            } else {
                localWrittenBytes = part2.setBytes(index - part1Len, in, length);
            }

            if (localWrittenBytes == 0) {
                return writtenBytes;
            }

            if (localWrittenBytes < 0) {
                if (writtenBytes == 0) {
                    return -1;
                } else {
                    return writtenBytes;
                }
            }

            index += localWrittenBytes;
            writtenBytes += localWrittenBytes;
            length -= localWrittenBytes;
        } while (length > 0);
        return writtenBytes;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);

        ByteBuf dst = Unpooled.buffer(length);
        getBytes(index, dst, length);
        dst.writerIndex(length);

        return dst;
    }

    @Override
    public int nioBufferCount() {
        return part1.nioBufferCount() + part2.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        ByteBuffer merged = ByteBuffer.allocate(length).order(order());
        ByteBuffer[] buffers = nioBuffers(index, length);

        for (ByteBuffer buffer : buffers) {
            merged.put(buffer);
        }
        merged.flip();

        return merged;
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        checkIndex(index, length);
        if (length == 0) {
            return EmptyArrays.EMPTY_BYTE_BUFFERS;
        }

        if (index < part1Len) {
            if (part1.nioBufferCount() == 0) {
                throw new UnsupportedOperationException();
            }
            final int part1Read = Math.min(part1Len - index, length);
            final ByteBuffer[] part1Buffers = part1.nioBuffers(index, part1Read);
            length -= part1Read;

            if (length > 0) {
                if (part2.nioBufferCount() == 0) {
                    throw new UnsupportedOperationException();
                }
                final ByteBuffer[] part2Buffers = part2.nioBuffers(0, length);

                // TODO: Benchmark with System.arraycopy, but arrays will usually be one element
                // merge part1Buffers and part2Buffers into buffers array
                ByteBuffer[] buffers = new ByteBuffer[part1Buffers.length + part2Buffers.length];
                int i = 0;
                for (ByteBuffer buffer : part1Buffers) {
                    buffers[i++] = buffer;
                }
                for (ByteBuffer buffer : part2Buffers) {
                    buffers[i++] = buffer;
                }
                return buffers;
            } else {
                return part1Buffers;
            }
        } else {
            if (part2.nioBufferCount() == 0) {
                throw new UnsupportedOperationException();
            }
            return part2.nioBuffers(index - part1Len, length);
        }
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
    public boolean hasMemoryAddress() {
        return false;
    }

    @Override
    public long memoryAddress() {
        throw new UnsupportedOperationException();
    }

    private ByteBuf allocBuffer(int capacity) {
        if (directByDefault) {
            return alloc().directBuffer(capacity);
        }
        return alloc().heapBuffer(capacity);
    }

    @Override
    public BipartiteByteBuf touch() {
        if (leak != null) {
            leak.record();
        }
        return this;
    }

    @Override
    public BipartiteByteBuf touch(Object hint) {
        if (leak != null) {
            leak.record(hint);
        }
        return this;
    }

    @Override
    public String toString() {
        String result = super.toString();
        result = result.substring(0, result.length() - 1);
        result += String.format(", part1=%s, part2=%s, part1Len=%d, part2Len=%d, directByDefault=%b)",
                part1.toString(), part2.toString(), part1Len, part2Len, directByDefault);
        return result;
    }

    @Override
    public BipartiteByteBuf setByte(int index, int value) {
        return (BipartiteByteBuf) super.setByte(index, value);
    }

    @Override
    public BipartiteByteBuf setShort(int index, int value) {
        return (BipartiteByteBuf) super.setShort(index, value);
    }

    @Override
    public BipartiteByteBuf setMedium(int index, int value) {
        return (BipartiteByteBuf) super.setMedium(index, value);
    }

    @Override
    public BipartiteByteBuf setInt(int index, int value) {
        return (BipartiteByteBuf) super.setInt(index, value);
    }

    @Override
    public BipartiteByteBuf setLong(int index, long value) {
        return (BipartiteByteBuf) super.setLong(index, value);
    }

    @Override
    public BipartiteByteBuf readerIndex(int readerIndex) {
        return (BipartiteByteBuf) super.readerIndex(readerIndex);
    }

    @Override
    public BipartiteByteBuf writerIndex(int writerIndex) {
        return (BipartiteByteBuf) super.writerIndex(writerIndex);
    }

    @Override
    public BipartiteByteBuf setIndex(int readerIndex, int writerIndex) {
        return (BipartiteByteBuf) super.setIndex(readerIndex, writerIndex);
    }

    @Override
    public BipartiteByteBuf clear() {
        return (BipartiteByteBuf) super.clear();
    }

    @Override
    public BipartiteByteBuf markReaderIndex() {
        return (BipartiteByteBuf) super.markReaderIndex();
    }

    @Override
    public BipartiteByteBuf resetReaderIndex() {
        return (BipartiteByteBuf) super.resetReaderIndex();
    }

    @Override
    public BipartiteByteBuf markWriterIndex() {
        return (BipartiteByteBuf) super.markWriterIndex();
    }

    @Override
    public BipartiteByteBuf resetWriterIndex() {
        return (BipartiteByteBuf) super.resetWriterIndex();
    }

    @Override
    public BipartiteByteBuf ensureWritable(int minWritableBytes) {
        return (BipartiteByteBuf) super.ensureWritable(minWritableBytes);
    }

    @Override
    public BipartiteByteBuf getBytes(int index, ByteBuf dst) {
        return (BipartiteByteBuf) super.getBytes(index, dst);
    }

    @Override
    public BipartiteByteBuf getBytes(int index, ByteBuf dst, int length) {
        return (BipartiteByteBuf) super.getBytes(index, dst, length);
    }

    @Override
    public BipartiteByteBuf getBytes(int index, byte[] dst) {
        return (BipartiteByteBuf) super.getBytes(index, dst);
    }

    @Override
    public BipartiteByteBuf setBoolean(int index, boolean value) {
        return (BipartiteByteBuf) super.setBoolean(index, value);
    }

    @Override
    public BipartiteByteBuf setChar(int index, int value) {
        return (BipartiteByteBuf) super.setChar(index, value);
    }

    @Override
    public BipartiteByteBuf setFloat(int index, float value) {
        return (BipartiteByteBuf) super.setFloat(index, value);
    }

    @Override
    public BipartiteByteBuf setDouble(int index, double value) {
        return (BipartiteByteBuf) super.setDouble(index, value);
    }

    @Override
    public BipartiteByteBuf setBytes(int index, ByteBuf src) {
        return (BipartiteByteBuf) super.setBytes(index, src);
    }

    @Override
    public BipartiteByteBuf setBytes(int index, ByteBuf src, int length) {
        return (BipartiteByteBuf) super.setBytes(index, src, length);
    }

    @Override
    public BipartiteByteBuf setBytes(int index, byte[] src) {
        return (BipartiteByteBuf) super.setBytes(index, src);
    }

    @Override
    public BipartiteByteBuf setZero(int index, int length) {
        return (BipartiteByteBuf) super.setZero(index, length);
    }

    @Override
    public BipartiteByteBuf readBytes(ByteBuf dst) {
        return (BipartiteByteBuf) super.readBytes(dst);
    }

    @Override
    public BipartiteByteBuf readBytes(ByteBuf dst, int length) {
        return (BipartiteByteBuf) super.readBytes(dst, length);
    }

    @Override
    public BipartiteByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        return (BipartiteByteBuf) super.readBytes(dst, dstIndex, length);
    }

    @Override
    public BipartiteByteBuf readBytes(byte[] dst) {
        return (BipartiteByteBuf) super.readBytes(dst);
    }

    @Override
    public BipartiteByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        return (BipartiteByteBuf) super.readBytes(dst, dstIndex, length);
    }

    @Override
    public BipartiteByteBuf readBytes(ByteBuffer dst) {
        return (BipartiteByteBuf) super.readBytes(dst);
    }

    @Override
    public BipartiteByteBuf readBytes(OutputStream out, int length) throws IOException {
        return (BipartiteByteBuf) super.readBytes(out, length);
    }

    @Override
    public BipartiteByteBuf skipBytes(int length) {
        return (BipartiteByteBuf) super.skipBytes(length);
    }

    @Override
    public BipartiteByteBuf writeBoolean(boolean value) {
        return (BipartiteByteBuf) super.writeBoolean(value);
    }

    @Override
    public BipartiteByteBuf writeByte(int value) {
        return (BipartiteByteBuf) super.writeByte(value);
    }

    @Override
    public BipartiteByteBuf writeShort(int value) {
        return (BipartiteByteBuf) super.writeShort(value);
    }

    @Override
    public BipartiteByteBuf writeMedium(int value) {
        return (BipartiteByteBuf) super.writeMedium(value);
    }

    @Override
    public BipartiteByteBuf writeInt(int value) {
        return (BipartiteByteBuf) super.writeInt(value);
    }

    @Override
    public BipartiteByteBuf writeLong(long value) {
        return (BipartiteByteBuf) super.writeLong(value);
    }

    @Override
    public BipartiteByteBuf writeChar(int value) {
        return (BipartiteByteBuf) super.writeChar(value);
    }

    @Override
    public BipartiteByteBuf writeFloat(float value) {
        return (BipartiteByteBuf) super.writeFloat(value);
    }

    @Override
    public BipartiteByteBuf writeDouble(double value) {
        return (BipartiteByteBuf) super.writeDouble(value);
    }

    @Override
    public BipartiteByteBuf writeBytes(ByteBuf src) {
        return (BipartiteByteBuf) super.writeBytes(src);
    }

    @Override
    public BipartiteByteBuf writeBytes(ByteBuf src, int length) {
        return (BipartiteByteBuf) super.writeBytes(src, length);
    }

    @Override
    public BipartiteByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        return (BipartiteByteBuf) super.writeBytes(src, srcIndex, length);
    }

    @Override
    public BipartiteByteBuf writeBytes(byte[] src) {
        return (BipartiteByteBuf) super.writeBytes(src);
    }

    @Override
    public BipartiteByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        return (BipartiteByteBuf) super.writeBytes(src, srcIndex, length);
    }

    @Override
    public BipartiteByteBuf writeBytes(ByteBuffer src) {
        return (BipartiteByteBuf) super.writeBytes(src);
    }

    @Override
    public BipartiteByteBuf writeZero(int length) {
        return (BipartiteByteBuf) super.writeZero(length);
    }

    @Override
    public BipartiteByteBuf retain(int increment) {
        return (BipartiteByteBuf) super.retain(increment);
    }

    @Override
    public BipartiteByteBuf retain() {
        return (BipartiteByteBuf) super.retain();
    }
}
