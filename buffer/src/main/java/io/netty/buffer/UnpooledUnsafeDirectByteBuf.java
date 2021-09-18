/*
 * Copyright 2012 The Netty Project
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

import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * A NIO {@link ByteBuffer} based buffer. It is recommended to use
 * {@link UnpooledByteBufAllocator#directBuffer(int, int)}, {@link Unpooled#directBuffer(int)} and
 * {@link Unpooled#wrappedBuffer(ByteBuffer)} instead of calling the constructor explicitly.}
 */
public class UnpooledUnsafeDirectByteBuf extends UnpooledDirectByteBuf {

    long memoryAddress;

    /**
     * Creates a new direct buffer.
     *
     * @param initialCapacity the initial capacity of the underlying direct buffer
     * @param maxCapacity     the maximum capacity of the underlying direct buffer
     */
    public UnpooledUnsafeDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(alloc, initialCapacity, maxCapacity);
    }

    /**
     * Creates a new direct buffer by wrapping the specified initial buffer.
     *
     * @param maxCapacity the maximum capacity of the underlying direct buffer
     */
    protected UnpooledUnsafeDirectByteBuf(ByteBufAllocator alloc, ByteBuffer initialBuffer, int maxCapacity) {
        // We never try to free the buffer if it was provided by the end-user as we don't know if this is a duplicate or
        // a slice. This is done to prevent an IllegalArgumentException when using Java9 as Unsafe.invokeCleaner(...)
        // will check if the given buffer is either a duplicate or slice and in this case throw an
        // IllegalArgumentException.
        //
        // See https://hg.openjdk.java.net/jdk9/hs-demo/jdk/file/0d2ab72ba600/src/jdk.unsupported/share/classes/
        // sun/misc/Unsafe.java#l1250
        //
        // We also call slice() explicitly here to preserve behaviour with previous netty releases.
        super(alloc, initialBuffer, maxCapacity, /* doFree = */ false, /* slice = */ true);
    }

    UnpooledUnsafeDirectByteBuf(ByteBufAllocator alloc, ByteBuffer initialBuffer, int maxCapacity, boolean doFree) {
        super(alloc, initialBuffer, maxCapacity, doFree, false);
    }

    @Override
    final void setByteBuffer(ByteBuffer buffer, boolean tryFree) {
        super.setByteBuffer(buffer, tryFree);
        memoryAddress = PlatformDependent.directBufferAddress(buffer);
    }

    @Override
    public boolean hasMemoryAddress() {
        return true;
    }

    @Override
    public long memoryAddress() {
        ensureAccessible();
        return memoryAddress;
    }

    @Override
    public byte getByte(int index) {
        checkIndex(index);
        return _getByte(index);
    }

    @Override
    protected byte _getByte(int index) {
        return UnsafeByteBufUtil.getByte(addr(index));
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return _getShort(index);
    }

    @Override
    protected short _getShort(int index) {
        return UnsafeByteBufUtil.getShort(addr(index));
    }

    @Override
    protected short _getShortLE(int index) {
        return UnsafeByteBufUtil.getShortLE(addr(index));
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return _getUnsignedMedium(index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return UnsafeByteBufUtil.getUnsignedMedium(addr(index));
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return UnsafeByteBufUtil.getUnsignedMediumLE(addr(index));
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return _getInt(index);
    }

    @Override
    protected int _getInt(int index) {
        return UnsafeByteBufUtil.getInt(addr(index));
    }

    @Override
    protected int _getIntLE(int index) {
        return UnsafeByteBufUtil.getIntLE(addr(index));
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return _getLong(index);
    }

    @Override
    protected long _getLong(int index) {
        return UnsafeByteBufUtil.getLong(addr(index));
    }

    @Override
    protected long _getLongLE(int index) {
        return UnsafeByteBufUtil.getLongLE(addr(index));
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst, dstIndex, length);
        return this;
    }

    @Override
    void getBytes(int index, byte[] dst, int dstIndex, int length, boolean internal) {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst, dstIndex, length);
    }

    @Override
    void getBytes(int index, ByteBuffer dst, boolean internal) {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst);
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        checkIndex(index);
        _setByte(index, value);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        UnsafeByteBufUtil.setByte(addr(index), value);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        checkIndex(index, 2);
        _setShort(index, value);
        return this;
    }

    @Override
    protected void _setShort(int index, int value) {
        UnsafeByteBufUtil.setShort(addr(index), value);
    }

    @Override
    protected void _setShortLE(int index, int value) {
        UnsafeByteBufUtil.setShortLE(addr(index), value);
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        checkIndex(index, 3);
        _setMedium(index, value);
        return this;
    }

    @Override
    protected void _setMedium(int index, int value) {
        UnsafeByteBufUtil.setMedium(addr(index), value);
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        UnsafeByteBufUtil.setMediumLE(addr(index), value);
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        checkIndex(index, 4);
        _setInt(index, value);
        return this;
    }

    @Override
    protected void _setInt(int index, int value) {
        UnsafeByteBufUtil.setInt(addr(index), value);
    }

    @Override
    protected void _setIntLE(int index, int value) {
        UnsafeByteBufUtil.setIntLE(addr(index), value);
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        checkIndex(index, 8);
        _setLong(index, value);
        return this;
    }

    @Override
    protected void _setLong(int index, long value) {
        UnsafeByteBufUtil.setLong(addr(index), value);
    }

    @Override
    protected void _setLongLE(int index, long value) {
        UnsafeByteBufUtil.setLongLE(addr(index), value);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        UnsafeByteBufUtil.setBytes(this, addr(index), index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        UnsafeByteBufUtil.setBytes(this, addr(index), index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        UnsafeByteBufUtil.setBytes(this, addr(index), index, src);
        return this;
    }

    @Override
    void getBytes(int index, OutputStream out, int length, boolean internal) throws IOException {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, out, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return UnsafeByteBufUtil.setBytes(this, addr(index), index, in, length);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return UnsafeByteBufUtil.copy(this, addr(index), index, length);
    }

    final long addr(int index) {
        return memoryAddress + index;
    }

    @Override
    protected SwappedByteBuf newSwappedByteBuf() {
        if (PlatformDependent.isUnaligned()) {
            // Only use if unaligned access is supported otherwise there is no gain.
            return new UnsafeDirectSwappedByteBuf(this);
        }
        return super.newSwappedByteBuf();
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        checkIndex(index, length);
        UnsafeByteBufUtil.setZero(addr(index), length);
        return this;
    }

    @Override
    public ByteBuf writeZero(int length) {
        ensureWritable(length);
        int wIndex = writerIndex;
        UnsafeByteBufUtil.setZero(addr(wIndex), length);
        writerIndex = wIndex + length;
        return this;
    }
}
