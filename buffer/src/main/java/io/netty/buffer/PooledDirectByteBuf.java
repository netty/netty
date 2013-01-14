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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

final class PooledDirectByteBuf extends PooledByteBuf<ByteBuffer> {

    PooledDirectByteBuf(int maxCapacity) {
        super(maxCapacity);
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
    public byte getByte(int index) {
        checkIndex(index);
        return memory.get(idx(index));
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return memory.getShort(idx(index));
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        index = idx(index);
        return (memory.get(index) & 0xff) << 16 | (memory.get(index + 1) & 0xff) << 8 | memory.get(index + 2) & 0xff;
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return memory.getInt(idx(index));
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return memory.getLong(idx(index));
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex(index, length);
        if (dst instanceof PooledDirectByteBuf) {
            PooledDirectByteBuf bbdst = (PooledDirectByteBuf) dst;
            ByteBuffer data = bbdst.internalNioBuffer();
            dstIndex = bbdst.idx(dstIndex);
            data.clear().position(dstIndex).limit(dstIndex + length);
            getBytes(index, data);
        } else if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        ByteBuffer tmpBuf = internalNioBuffer();
        index = idx(index);
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.get(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex(index);
        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        ByteBuffer tmpBuf = internalNioBuffer();
        index = idx(index);
        tmpBuf.clear().position(index).limit(index + bytesToCopy);
        dst.put(tmpBuf);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex(index, length);
        if (length == 0) {
            return this;
        }

        byte[] tmp = new byte[length];
        ByteBuffer tmpBuf = internalNioBuffer();
        tmpBuf.clear().position(idx(index));
        tmpBuf.get(tmp);
        out.write(tmp);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        checkIndex(index, length);
        if (length == 0) {
            return 0;
        }

        ByteBuffer tmpBuf = internalNioBuffer();
        index = idx(index);
        tmpBuf.clear().position(index).limit(index + length);
        return out.write(tmpBuf);
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        checkIndex(index);
        memory.put(idx(index), (byte) value);
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        checkIndex(index, 2);
        memory.putShort(idx(index), (short) value);
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        checkIndex(index, 3);
        index = idx(index);
        memory.put(index, (byte) (value >>> 16));
        memory.put(index + 1, (byte) (value >>> 8));
        memory.put(index + 2, (byte) value);
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        checkIndex(index, 4);
        memory.putInt(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        checkIndex(index, 8);
        memory.putLong(idx(index), value);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkIndex(index, length);
        if (src instanceof PooledDirectByteBuf) {
            PooledDirectByteBuf bbsrc = (PooledDirectByteBuf) src;
            ByteBuffer data = bbsrc.internalNioBuffer();
            srcIndex = bbsrc.idx(srcIndex);
            data.clear().position(srcIndex).limit(srcIndex + length);
            setBytes(index, data);
        } else if (src.hasArray()) {
            setBytes(index, src.array(), src.arrayOffset() + srcIndex, length);
        } else {
            src.getBytes(srcIndex, this, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        ByteBuffer tmpBuf = internalNioBuffer();
        index = idx(index);
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.put(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        checkIndex(index);
        ByteBuffer tmpBuf = internalNioBuffer();
        if (src == tmpBuf) {
            src = src.duplicate();
        }

        index = idx(index);
        tmpBuf.clear().position(index).limit(index + src.remaining());
        tmpBuf.put(src);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkIndex(index, length);
        byte[] tmp = new byte[length];
        int readBytes = in.read(tmp);
        if (readBytes <= 0) {
            return readBytes;
        }
        ByteBuffer tmpNioBuf = internalNioBuffer();
        tmpNioBuf.clear().position(idx(index));
        tmpNioBuf.put(tmp, 0, readBytes);
        return readBytes;
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        checkIndex(index, length);
        ByteBuffer tmpNioBuf = internalNioBuffer();
        index = idx(index);
        tmpNioBuf.clear().position(index).limit(index + length);
        try {
            return in.read(tmpNioBuf);
        } catch (ClosedChannelException e) {
            return -1;
        }
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        ByteBuf copy = alloc().directBuffer(capacity(), maxCapacity());
        copy.writeBytes(this, index, length);
        return copy;
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        index = idx(index);
        return ((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length)).slice();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
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
}
