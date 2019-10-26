/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectPool.ObjectCreator;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

final class PooledUnsafeDirectByteBuf extends PooledByteBuf<ByteBuffer> {
    private static final ObjectPool<PooledUnsafeDirectByteBuf> RECYCLER = ObjectPool.newPool(
            new ObjectCreator<PooledUnsafeDirectByteBuf>() {
        @Override
        public PooledUnsafeDirectByteBuf newObject(Handle<PooledUnsafeDirectByteBuf> handle) {
            return new PooledUnsafeDirectByteBuf(handle, 0);
        }
    });

    static PooledUnsafeDirectByteBuf newInstance(int maxCapacity) {
        PooledUnsafeDirectByteBuf buf = RECYCLER.get();
        buf.reuse(maxCapacity);
        return buf;
    }

    private long memoryAddress;

    private PooledUnsafeDirectByteBuf(Handle<PooledUnsafeDirectByteBuf> recyclerHandle, int maxCapacity) {
        super(recyclerHandle, maxCapacity);
    }

    @Override
    void init(PoolChunk<ByteBuffer> chunk, ByteBuffer nioBuffer,
              long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        super.init(chunk, nioBuffer, handle, offset, length, maxLength, cache);
        initMemoryAddress();
    }

    @Override
    void initUnpooled(PoolChunk<ByteBuffer> chunk, int length) {
        super.initUnpooled(chunk, length);
        initMemoryAddress();
    }

    private void initMemoryAddress() {
        memoryAddress = PlatformDependent.directBufferAddress(memory) + offset;
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(ByteBuffer memory) {
        return memory.duplicate();
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    protected byte _getByte(int index) {
        return UnsafeByteBufUtil.getByte(addr(index));
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
    protected int _getUnsignedMedium(int index) {
        return UnsafeByteBufUtil.getUnsignedMedium(addr(index));
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return UnsafeByteBufUtil.getUnsignedMediumLE(addr(index));
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
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, out, length);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        UnsafeByteBufUtil.setByte(addr(index), (byte) value);
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
    protected void _setMedium(int index, int value) {
        UnsafeByteBufUtil.setMedium(addr(index), value);
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        UnsafeByteBufUtil.setMediumLE(addr(index), value);
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
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return UnsafeByteBufUtil.setBytes(this, addr(index), index, in, length);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return UnsafeByteBufUtil.copy(this, addr(index), index, length);
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
        ensureAccessible();
        return memoryAddress;
    }

    private long addr(int index) {
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
