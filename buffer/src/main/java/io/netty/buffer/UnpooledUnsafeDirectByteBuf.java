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
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * A NIO {@link ByteBuffer} based buffer.  It is recommended to use {@link Unpooled#directBuffer(int)}
 * and {@link Unpooled#wrappedBuffer(ByteBuffer)} instead of calling the
 * constructor explicitly.
 */
public class UnpooledUnsafeDirectByteBuf extends AbstractReferenceCountedByteBuf {
    private final ByteBufAllocator alloc;

    private long memoryAddress;
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
    protected UnpooledUnsafeDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
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
        setByteBuffer(allocateDirect(initialCapacity));
    }

    /**
     * Creates a new direct buffer by wrapping the specified initial buffer.
     *
     * @param maxCapacity the maximum capacity of the underlying direct buffer
     */
    protected UnpooledUnsafeDirectByteBuf(ByteBufAllocator alloc, ByteBuffer initialBuffer, int maxCapacity) {
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
        memoryAddress = PlatformDependent.directBufferAddress(buffer);
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
        return true;
    }

    @Override
    public long memoryAddress() {
        return memoryAddress;
    }

    @Override
    protected byte _getByte(int index) {
        return UnsafeDirectByteBufUtil._getByte(this, index);
    }

    @Override
    protected short _getShort(int index) {
        return UnsafeDirectByteBufUtil._getShort(this, index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return UnsafeDirectByteBufUtil._getUnsignedMedium(this, index);
    }

    @Override
    protected int _getInt(int index) {
        return UnsafeDirectByteBufUtil._getInt(this, index);
    }

    @Override
    protected long _getLong(int index) {
        return UnsafeDirectByteBufUtil._getLong(this, index);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        UnsafeDirectByteBufUtil.getBytes(this, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        UnsafeDirectByteBufUtil.getBytes(this, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        DirectByteBufUtil.getBytes(this, buffer, index, dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        DirectByteBufUtil.readBytes(this, buffer, dst);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        UnsafeDirectByteBufUtil._setByte(this, index, value);
    }

    @Override
    protected void _setShort(int index, int value) {
        UnsafeDirectByteBufUtil._setShort(this, index, value);
    }

    @Override
    protected void _setMedium(int index, int value) {
        UnsafeDirectByteBufUtil._setMedium(this, index, value);
    }

    @Override
    protected void _setInt(int index, int value) {
        UnsafeDirectByteBufUtil._setInt(this, index, value);
    }

    @Override
    protected void _setLong(int index, long value) {
        UnsafeDirectByteBufUtil._setLong(this, index, value);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        UnsafeDirectByteBufUtil.setBytes(this, index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        UnsafeDirectByteBufUtil.setBytes(this, index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        DirectByteBufUtil.setBytes(this, index, src);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        UnsafeDirectByteBufUtil.getBytes(this, index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return DirectByteBufUtil.getBytes(this, buffer, index, out, length);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return DirectByteBufUtil.readBytes(this, buffer, out, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return UnsafeDirectByteBufUtil.setBytes(this, index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return DirectByteBufUtil.setBytes(this, index, in, length);
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
        return UnsafeDirectByteBufUtil.copy(this, index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return DirectByteBufUtil.internalNioBuffer(this, index, length);
    }

    @Override
    ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = buffer.duplicate();
        }
        return tmpNioBuf;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return DirectByteBufUtil.nioBuffer(this, buffer, index, length);
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

    @Override
    protected SwappedByteBuf newSwappedByteBuf() {
        return new UnsafeDirectSwappedByteBuf(this);
    }
}
