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

import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * A NIO {@link ByteBuffer} based buffer.  It is recommended to use {@link Unpooled#directBuffer(int)}
 * and {@link Unpooled#wrappedBuffer(ByteBuffer)} instead of calling the
 * constructor explicitly.
 */
public class UnpooledDirectByteBuf extends AbstractReferenceCountedByteBuf {

    private final ByteBufAllocator alloc;

    private ByteBuffer buffer;
    private ByteBuffer tmpNioBuf;
    private int capacity;
    private boolean doNotFree;

    /**
     * Creates a new direct buffer.
     *
     * @param initialCapacity the initial capacity of the underlying direct buffer
     * @param maxCapacity     the maximum capacity of the underlying direct buffer
     */
    protected UnpooledDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(maxCapacity);
        if (alloc == null) {
            throw new NullPointerException("alloc");
        }
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity);
        }
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity: " + maxCapacity);
        }
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        this.alloc = alloc;
        setByteBuffer(ByteBuffer.allocateDirect(initialCapacity));
    }

    /**
     * Creates a new direct buffer by wrapping the specified initial buffer.
     *
     * @param maxCapacity the maximum capacity of the underlying direct buffer
     */
    protected UnpooledDirectByteBuf(ByteBufAllocator alloc, ByteBuffer initialBuffer, int maxCapacity) {
        super(maxCapacity);
        if (alloc == null) {
            throw new NullPointerException("alloc");
        }
        if (initialBuffer == null) {
            throw new NullPointerException("initialBuffer");
        }
        if (!initialBuffer.isDirect()) {
            throw new IllegalArgumentException("initialBuffer is not a direct buffer.");
        }
        if (initialBuffer.isReadOnly()) {
            throw new IllegalArgumentException("initialBuffer is a read-only buffer.");
        }

        int initialCapacity = initialBuffer.remaining();
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        this.alloc = alloc;
        doNotFree = true;
        setByteBuffer(initialBuffer.slice().order(ByteOrder.BIG_ENDIAN));
        writerIndex(initialCapacity);
    }

    /**
     * Allocate a new direct {@link ByteBuffer} with the given initialCapacity.
     */
    protected ByteBuffer allocateDirect(int initialCapacity) {
        return ByteBuffer.allocateDirect(initialCapacity);
    }

    /**
     * Free a direct {@link ByteBuffer}
     */
    protected void freeDirect(ByteBuffer buffer) {
        PlatformDependent.freeDirectBuffer(buffer);
    }

    private void setByteBuffer(ByteBuffer buffer) {
        ByteBuffer oldBuffer = this.buffer;
        if (oldBuffer != null) {
            if (doNotFree) {
                doNotFree = false;
            } else {
                freeDirect(oldBuffer);
            }
        }

        this.buffer = buffer;
        tmpNioBuf = null;
        capacity = buffer.remaining();
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        ensureAccessible();
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        int readerIndex = readerIndex();
        int writerIndex = writerIndex();

        int oldCapacity = capacity;
        if (newCapacity > oldCapacity) {
            ByteBuffer oldBuffer = buffer;
            ByteBuffer newBuffer = allocateDirect(newCapacity);
            oldBuffer.position(0).limit(oldBuffer.capacity());
            newBuffer.position(0).limit(oldBuffer.capacity());
            newBuffer.put(oldBuffer);
            newBuffer.clear();
            setByteBuffer(newBuffer);
        } else if (newCapacity < oldCapacity) {
            ByteBuffer oldBuffer = buffer;
            ByteBuffer newBuffer = allocateDirect(newCapacity);
            if (readerIndex < newCapacity) {
                if (writerIndex > newCapacity) {
                    writerIndex(writerIndex = newCapacity);
                }
                oldBuffer.position(readerIndex).limit(writerIndex);
                newBuffer.position(readerIndex).limit(writerIndex);
                newBuffer.put(oldBuffer);
                newBuffer.clear();
            } else {
                setIndex(newCapacity, newCapacity);
            }
            setByteBuffer(newBuffer);
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
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        throw new UnsupportedOperationException("direct buffer");
    }

    @Override
    public int arrayOffset() {
        throw new UnsupportedOperationException("direct buffer");
    }

    @Override
    public boolean hasMemoryAddress() {
        return false;
    }

    @Override
    public long memoryAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte getByte(int index) {
        ensureAccessible();
        return _getByte(index);
    }

    @Override
    protected byte _getByte(int index) {
        return buffer.get(index);
    }

    @Override
    public short getShort(int index) {
        ensureAccessible();
        return _getShort(index);
    }

    @Override
    protected short _getShort(int index) {
        return buffer.getShort(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        ensureAccessible();
        return _getUnsignedMedium(index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return (getByte(index) & 0xff) << 16 | (getByte(index + 1) & 0xff) << 8 | getByte(index + 2) & 0xff;
    }

    @Override
    public int getInt(int index) {
        ensureAccessible();
        return _getInt(index);
    }

    @Override
    protected int _getInt(int index) {
        return buffer.getInt(index);
    }

    @Override
    public long getLong(int index) {
        ensureAccessible();
        return _getLong(index);
    }

    @Override
    protected long _getLong(int index) {
        return buffer.getLong(index);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else if (dst.nioBufferCount() > 0) {
            for (ByteBuffer bb: dst.nioBuffers(dstIndex, length)) {
                int bbLen = bb.remaining();
                getBytes(index, bb);
                index += bbLen;
            }
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        getBytes(index, dst, dstIndex, length, false);
        return this;
    }

    private void getBytes(int index, byte[] dst, int dstIndex, int length, boolean internal) {
        checkDstIndex(index, length, dstIndex, dst.length);

        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = internalNioBuffer();
        } else {
            tmpBuf = buffer.duplicate();
        }
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.get(dst, dstIndex, length);
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        getBytes(readerIndex, dst, dstIndex, length, true);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        getBytes(index, dst, false);
        return this;
    }

    private void getBytes(int index, ByteBuffer dst, boolean internal) {
        checkIndex(index, dst.remaining());

        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = internalNioBuffer();
        } else {
            tmpBuf = buffer.duplicate();
        }
        tmpBuf.clear().position(index).limit(index + dst.remaining());
        dst.put(tmpBuf);
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        int length = dst.remaining();
        checkReadableBytes(length);
        getBytes(readerIndex, dst, true);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        ensureAccessible();
        _setByte(index, value);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        buffer.put(index, (byte) value);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        ensureAccessible();
        _setShort(index, value);
        return this;
    }

    @Override
    protected void _setShort(int index, int value) {
        buffer.putShort(index, (short) value);
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        ensureAccessible();
        _setMedium(index, value);
        return this;
    }

    @Override
    protected void _setMedium(int index, int value) {
        setByte(index, (byte) (value >>> 16));
        setByte(index + 1, (byte) (value >>> 8));
        setByte(index + 2, (byte) value);
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        ensureAccessible();
        _setInt(index, value);
        return this;
    }

    @Override
    protected void _setInt(int index, int value) {
        buffer.putInt(index, value);
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        ensureAccessible();
        _setLong(index, value);
        return this;
    }

    @Override
    protected void _setLong(int index, long value) {
        buffer.putLong(index, value);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        if (src.nioBufferCount() > 0) {
            for (ByteBuffer bb: src.nioBuffers(srcIndex, length)) {
                int bbLen = bb.remaining();
                setBytes(index, bb);
                index += bbLen;
            }
        } else {
            src.getBytes(srcIndex, this, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.length);
        ByteBuffer tmpBuf = internalNioBuffer();
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.put(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        ensureAccessible();
        ByteBuffer tmpBuf = internalNioBuffer();
        if (src == tmpBuf) {
            src = src.duplicate();
        }

        tmpBuf.clear().position(index).limit(index + src.remaining());
        tmpBuf.put(src);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        getBytes(index, out, length, false);
        return this;
    }

    private void getBytes(int index, OutputStream out, int length, boolean internal) throws IOException {
        ensureAccessible();
        if (length == 0) {
            return;
        }

        if (buffer.hasArray()) {
            out.write(buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            byte[] tmp = new byte[length];
            ByteBuffer tmpBuf;
            if (internal) {
                tmpBuf = internalNioBuffer();
            } else {
                tmpBuf = buffer.duplicate();
            }
            tmpBuf.clear().position(index);
            tmpBuf.get(tmp);
            out.write(tmp);
        }
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        checkReadableBytes(length);
        getBytes(readerIndex, out, length, true);
        readerIndex += length;
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return getBytes(index, out, length, false);
    }

    private int getBytes(int index, GatheringByteChannel out, int length, boolean internal) throws IOException {
        ensureAccessible();
        if (length == 0) {
            return 0;
        }

        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = internalNioBuffer();
        } else {
            tmpBuf = buffer.duplicate();
        }
        tmpBuf.clear().position(index).limit(index + length);
        return out.write(tmpBuf);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = getBytes(readerIndex, out, length, true);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        ensureAccessible();
        if (buffer.hasArray()) {
            return in.read(buffer.array(), buffer.arrayOffset() + index, length);
        } else {
            byte[] tmp = new byte[length];
            int readBytes = in.read(tmp);
            if (readBytes <= 0) {
                return readBytes;
            }
            ByteBuffer tmpBuf = internalNioBuffer();
            tmpBuf.clear().position(index);
            tmpBuf.put(tmp, 0, readBytes);
            return readBytes;
        }
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        ensureAccessible();
        ByteBuffer tmpBuf = internalNioBuffer();
        tmpBuf.clear().position(index).limit(index + length);
        try {
            return in.read(tmpNioBuf);
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    @Override
    public ByteBuf copy(int index, int length) {
        ensureAccessible();
        ByteBuffer src;
        try {
            src = (ByteBuffer) buffer.duplicate().clear().position(index).limit(index + length);
        } catch (IllegalArgumentException ignored) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need " + (index + length));
        }

        return alloc().directBuffer(length, maxCapacity()).writeBytes(src);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return (ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length);
    }

    private ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = buffer.duplicate();
        }
        return tmpNioBuf;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        return ((ByteBuffer) buffer.duplicate().position(index).limit(index + length)).slice();
    }

    @Override
    protected void deallocate() {
        ByteBuffer buffer = this.buffer;
        if (buffer == null) {
            return;
        }

        this.buffer = null;

        if (!doNotFree) {
            freeDirect(buffer);
        }
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }
}
