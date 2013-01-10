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

import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

final class PooledUnsafeDirectByteBuf extends PooledByteBuf<ByteBuffer> {

    private static final Field ADDRESS_FIELD;
    private static final Unsafe UNSAFE;
    private static final boolean NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    static {
        Unsafe unsafe;
        try {
            Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
            singleoneInstanceField.setAccessible(true);
            unsafe = (Unsafe) singleoneInstanceField.get(null);
        } catch (Throwable t) {
            throw new Error(t);
        }
        UNSAFE = unsafe;

        Field addressField;
        try {
            addressField = Buffer.class.getDeclaredField("address");
            addressField.setAccessible(true);
            if (addressField.getLong(ByteBuffer.allocate(1)) != 0) {
                throw new Error("heap buffer address must be 0");
            }
            ByteBuffer directBuf = ByteBuffer.allocateDirect(1);
            if (addressField.getLong(directBuf) == 0) {
                throw new Error("direct buffer address must be non-zero");
            }
            UnpooledDirectByteBuf.freeDirect(directBuf);
        } catch (Throwable t) {
            throw new Error(t);
        }
        ADDRESS_FIELD = addressField;
    }

    private long memoryAddress;

    PooledUnsafeDirectByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    void init(PoolChunk<ByteBuffer> chunk, long handle, int offset, int length, int maxLength) {
        super.init(chunk, handle, offset, length, maxLength);
        initiMemoryAddress();
    }

    @Override
    void initUnpooled(PoolChunk<ByteBuffer> chunk, int length) {
        super.initUnpooled(chunk, length);
        initiMemoryAddress();
    }

    private void initiMemoryAddress() {
        ByteBuffer memory = this.memory;
        try {
            memoryAddress = ADDRESS_FIELD.getLong(memory) + offset;
        } catch (Exception e) {
            throw new Error("failed to get the address of a direct buffer", e);
        }
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
        return UNSAFE.getByte(addr(index));
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        if (NATIVE_ORDER) {
            return UNSAFE.getShort(addr(index));
        }
        return Short.reverseBytes(UNSAFE.getShort(addr(index)));
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        long addr = addr(index);
        return (UNSAFE.getByte(addr) & 0xff) << 16 |(UNSAFE.getByte(addr + 1) & 0xff) << 8 |
                UNSAFE.getByte(addr + 2) & 0xff;
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        if (NATIVE_ORDER) {
            return UNSAFE.getInt(addr(index));
        }
        return Integer.reverseBytes(UNSAFE.getInt(addr(index)));
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        if (NATIVE_ORDER) {
            return UNSAFE.getLong(addr(index));
        }
        return Long.reverseBytes(UNSAFE.getLong(addr(index)));
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex(index, length);
        if (dst instanceof PooledUnsafeDirectByteBuf) {
            PooledUnsafeDirectByteBuf bbdst = (PooledUnsafeDirectByteBuf) dst;
            UNSAFE.copyMemory(addr(index), bbdst.addr(dstIndex), length);
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
        UNSAFE.putByte(addr(index), (byte) value);
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        checkIndex(index, 2);
        UNSAFE.putShort(addr(index), NATIVE_ORDER? (short) value : Short.reverseBytes((short) value));
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        checkIndex(index, 3);
        long addr = addr(index);
        UNSAFE.putByte(addr, (byte) (value >>> 16));
        UNSAFE.putByte(addr + 1, (byte) (value >>> 8));
        UNSAFE.putByte(addr + 2, (byte) value);
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        checkIndex(index, 4);
        UNSAFE.putInt(addr(index), NATIVE_ORDER? value : Integer.reverseBytes(value));
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        checkIndex(index, 8);
        UNSAFE.putLong(addr(index), NATIVE_ORDER? value : Long.reverseBytes(value));
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkIndex(index, length);
        if (src instanceof PooledUnsafeDirectByteBuf) {
            PooledUnsafeDirectByteBuf bbsrc = (PooledUnsafeDirectByteBuf) src;
            UNSAFE.copyMemory(bbsrc.addr(srcIndex), addr(index), length);
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
        PooledUnsafeDirectByteBuf copy = (PooledUnsafeDirectByteBuf) alloc().directBuffer(capacity(), maxCapacity());
        UNSAFE.copyMemory(addr(index), copy.addr(index), length);
        copy.setIndex(index, index + length);
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

    private long addr(int index) {
        return memoryAddress + index;
    }
}
