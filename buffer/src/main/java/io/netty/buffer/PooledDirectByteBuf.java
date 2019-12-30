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

import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectPool.ObjectCreator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

final class PooledDirectByteBuf extends PooledByteBuf<ByteBuffer> {

    private static final ObjectPool<PooledDirectByteBuf> RECYCLER = ObjectPool.newPool(
            new ObjectCreator<PooledDirectByteBuf>() {
        @Override
        public PooledDirectByteBuf newObject(Handle<PooledDirectByteBuf> handle) {
            return new PooledDirectByteBuf(handle, 0);
        }
    });

    static PooledDirectByteBuf newInstance(int maxCapacity) {
        PooledDirectByteBuf buf = RECYCLER.get();
        buf.reuse(maxCapacity);
        return buf;
    }

    private PooledDirectByteBuf(Handle<PooledDirectByteBuf> recyclerHandle, int maxCapacity) {
        super(recyclerHandle, maxCapacity);
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
        return memory.get(idx(index));
    }

    @Override
    protected short _getShort(int index) {
        return memory.getShort(idx(index));
    }

    @Override
    protected short _getShortLE(int index) {
        return ByteBufUtil.swapShort(_getShort(index));
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        index = idx(index);
        return (memory.get(index) & 0xff)     << 16 |
               (memory.get(index + 1) & 0xff) << 8  |
               memory.get(index + 2) & 0xff;
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        index = idx(index);
        return memory.get(index)      & 0xff        |
               (memory.get(index + 1) & 0xff) << 8  |
               (memory.get(index + 2) & 0xff) << 16;
    }

    @Override
    protected int _getInt(int index) {
        return memory.getInt(idx(index));
    }

    @Override
    protected int _getIntLE(int index) {
        return ByteBufUtil.swapInt(_getInt(index));
    }

    @Override
    protected long _getLong(int index) {
        return memory.getLong(idx(index));
    }

    @Override
    protected long _getLongLE(int index) {
        return ByteBufUtil.swapLong(_getLong(index));
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
        checkDstIndex(index, length, dstIndex, dst.length);
        _internalNioBuffer(index, length, true).get(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        checkDstIndex(length, dstIndex, dst.length);
        _internalNioBuffer(readerIndex, length, false).get(dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        dst.put(duplicateInternalNioBuffer(index, dst.remaining()));
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        int length = dst.remaining();
        checkReadableBytes(length);
        dst.put(_internalNioBuffer(readerIndex, length, false));
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        getBytes(index, out, length, false);
        return this;
    }

    private void getBytes(int index, OutputStream out, int length, boolean internal) throws IOException {
        checkIndex(index, length);
        if (length == 0) {
            return;
        }
        ByteBufUtil.readBytes(alloc(), internal ? internalNioBuffer() : memory.duplicate(), idx(index), length, out);
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        checkReadableBytes(length);
        getBytes(readerIndex, out, length, true);
        readerIndex += length;
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        memory.put(idx(index), (byte) value);
    }

    @Override
    protected void _setShort(int index, int value) {
        memory.putShort(idx(index), (short) value);
    }

    @Override
    protected void _setShortLE(int index, int value) {
        _setShort(index, ByteBufUtil.swapShort((short) value));
    }

    @Override
    protected void _setMedium(int index, int value) {
        index = idx(index);
        memory.put(index, (byte) (value >>> 16));
        memory.put(index + 1, (byte) (value >>> 8));
        memory.put(index + 2, (byte) value);
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        index = idx(index);
        memory.put(index, (byte) value);
        memory.put(index + 1, (byte) (value >>> 8));
        memory.put(index + 2, (byte) (value >>> 16));
    }

    @Override
    protected void _setInt(int index, int value) {
        memory.putInt(idx(index), value);
    }

    @Override
    protected void _setIntLE(int index, int value) {
        _setInt(index, ByteBufUtil.swapInt(value));
    }

    @Override
    protected void _setLong(int index, long value) {
        memory.putLong(idx(index), value);
    }

    @Override
    protected void _setLongLE(int index, long value) {
        _setLong(index, ByteBufUtil.swapLong(value));
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        if (src.hasArray()) {
            setBytes(index, src.array(), src.arrayOffset() + srcIndex, length);
        } else if (src.nioBufferCount() > 0) {
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
        _internalNioBuffer(index, length, false).put(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        int length = src.remaining();
        checkIndex(index, length);
        ByteBuffer tmpBuf = internalNioBuffer();
        if (src == tmpBuf) {
            src = src.duplicate();
        }

        index = idx(index);
        tmpBuf.limit(index + length).position(index);
        tmpBuf.put(src);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkIndex(index, length);
        byte[] tmp = ByteBufUtil.threadLocalTempArray(length);
        int readBytes = in.read(tmp, 0, length);
        if (readBytes <= 0) {
            return readBytes;
        }
        ByteBuffer tmpBuf = internalNioBuffer();
        tmpBuf.position(idx(index));
        tmpBuf.put(tmp, 0, readBytes);
        return readBytes;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        ByteBuf copy = alloc().directBuffer(length, maxCapacity());
        return copy.writeBytes(this, index, length);
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
}
