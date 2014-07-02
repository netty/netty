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


import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;



/**
 * Read-only ByteBuf which wraps a read-only direct ByteBuffer and use unsafe for best performance.
 */
final class ReadOnlyUnsafeDirectByteBuf extends ReadOnlyByteBufferBuf {
    private static final boolean NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private final long memoryAddress;

    ReadOnlyUnsafeDirectByteBuf(ByteBufAllocator allocator, ByteBuffer buffer) {
        super(allocator, buffer);
        memoryAddress = PlatformDependent.directBufferAddress(buffer);
    }

    @Override
    protected byte _getByte(int index) {
        return PlatformDependent.getByte(addr(index));
    }

    @Override
    protected short _getShort(int index) {
        short v = PlatformDependent.getShort(addr(index));
        return NATIVE_ORDER? v : Short.reverseBytes(v);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        long addr = addr(index);
        return (PlatformDependent.getByte(addr) & 0xff) << 16 |
                (PlatformDependent.getByte(addr + 1) & 0xff) << 8 |
                PlatformDependent.getByte(addr + 2) & 0xff;
    }

    @Override
    protected int _getInt(int index) {
        int v = PlatformDependent.getInt(addr(index));
        return NATIVE_ORDER? v : Integer.reverseBytes(v);
    }

    @Override
    protected long _getLong(int index) {
        long v = PlatformDependent.getLong(addr(index));
        return NATIVE_ORDER? v : Long.reverseBytes(v);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex(index, length);
        if (dst == null) {
            throw new NullPointerException("dst");
        }
        if (dstIndex < 0 || dstIndex > dst.capacity() - length) {
            throw new IndexOutOfBoundsException("dstIndex: " + dstIndex);
        }

        if (dst.hasMemoryAddress()) {
            PlatformDependent.copyMemory(addr(index), dst.memoryAddress() + dstIndex, length);
        } else if (dst.hasArray()) {
            PlatformDependent.copyMemory(addr(index), dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        if (dst == null) {
            throw new NullPointerException("dst");
        }
        if (dstIndex < 0 || dstIndex > dst.length - length) {
            throw new IndexOutOfBoundsException(String.format(
                    "dstIndex: %d, length: %d (expected: range(0, %d))", dstIndex, length, dst.length));
        }

        if (length != 0) {
            PlatformDependent.copyMemory(addr(index), dst, dstIndex, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex(index);
        if (dst == null) {
            throw new NullPointerException("dst");
        }

        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        ByteBuffer tmpBuf = internalNioBuffer();
        tmpBuf.clear().position(index).limit(index + bytesToCopy);
        dst.put(tmpBuf);
        return this;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        ByteBuf copy = alloc().directBuffer(length, maxCapacity());
        if (length != 0) {
            if (copy.hasMemoryAddress()) {
                PlatformDependent.copyMemory(addr(index), copy.memoryAddress(), length);
                copy.setIndex(0, length);
            } else {
                copy.writeBytes(this, index, length);
            }
        }
        return copy;
    }

    private long addr(int index) {
        return memoryAddress + index;
    }
}
