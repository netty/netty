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

import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;

/**
 * Helper methods for direct {@link ByteBuf} implementations that use {@link sun.misc.Unsafe}
 */
final class UnsafeDirectByteBufUtil {
    private static final boolean NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    static byte _getByte(ByteBuf byteBuf, int index) {
        return PlatformDependent.getByte(addr(byteBuf, index));
    }

    static short _getShort(ByteBuf byteBuf, int index) {
        return _getShort(byteBuf, index, NATIVE_ORDER);
    }

    static short _getShort(ByteBuf byteBuf, int index, boolean nativeByteOrder) {
        short v = PlatformDependent.getShort(addr(byteBuf, index));
        return nativeByteOrder? v : Short.reverseBytes(v);
    }

    static int _getUnsignedMedium(ByteBuf byteBuf, int index) {
        long addr = addr(byteBuf, index);
        return (PlatformDependent.getByte(addr) & 0xff) << 16 |
                (PlatformDependent.getByte(addr + 1) & 0xff) << 8 |
                PlatformDependent.getByte(addr + 2) & 0xff;
    }

    static int _getInt(ByteBuf byteBuf, int index) {
        return _getInt(byteBuf, index, NATIVE_ORDER);
    }

    static int _getInt(ByteBuf byteBuf, int index, boolean nativeByteOrder) {
        int v = PlatformDependent.getInt(addr(byteBuf, index));
        return nativeByteOrder? v : Integer.reverseBytes(v);
    }

    static long _getLong(ByteBuf byteBuf, int index) {
        return _getLong(byteBuf, index, NATIVE_ORDER);
    }

    static long _getLong(ByteBuf byteBuf, int index, boolean nativeByteOrder) {
        long v = PlatformDependent.getLong(addr(byteBuf, index));
        return nativeByteOrder? v : Long.reverseBytes(v);
    }

    static void getBytes(AbstractByteBuf byteBuf, int index, ByteBuf dst, int dstIndex, int length) {
        byteBuf.checkIndex(index, length);
        if (dst == null) {
            throw new NullPointerException("dst");
        }
        if (dstIndex < 0 || dstIndex > dst.capacity() - length) {
            throw new IndexOutOfBoundsException("dstIndex: " + dstIndex);
        }

        if (dst.hasMemoryAddress()) {
            PlatformDependent.copyMemory(addr(byteBuf, index), dst.memoryAddress() + dstIndex, length);
        } else if (dst.hasArray()) {
            PlatformDependent.copyMemory(addr(byteBuf, index), dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, byteBuf, index, length);
        }
    }

    static void getBytes(AbstractByteBuf byteBuf, int index, byte[] dst, int dstIndex, int length) {
        byteBuf.checkIndex(index, length);
        if (dst == null) {
            throw new NullPointerException("dst");
        }
        if (dstIndex < 0 || dstIndex > dst.length - length) {
            throw new IndexOutOfBoundsException(String.format(
                    "dstIndex: %d, length: %d (expected: range(0, %d))", dstIndex, length, dst.length));
        }

        if (length != 0) {
            PlatformDependent.copyMemory(addr(byteBuf, index), dst, dstIndex, length);
        }
    }

    static ByteBuf copy(AbstractByteBuf byteBuf, int index, int length) {
        byteBuf.checkIndex(index, length);
        ByteBuf copy = byteBuf.alloc().directBuffer(length, byteBuf.maxCapacity());
        if (length != 0) {
            if (copy.hasMemoryAddress()) {
                PlatformDependent.copyMemory(addr(byteBuf, index), copy.memoryAddress(), length);
                copy.setIndex(0, length);
            } else {
                copy.writeBytes(byteBuf, index, length);
            }
        }
        return copy;
    }

    static int setBytes(AbstractByteBuf byteBuf, int index, InputStream in, int length) throws IOException {
        byteBuf.checkIndex(index, length);
        byte[] tmp = new byte[length];
        int readBytes = in.read(tmp);
        if (readBytes > 0) {
            PlatformDependent.copyMemory(tmp, 0, addr(byteBuf, index), readBytes);
        }
        return readBytes;
    }

    static void _setByte(ByteBuf byteBuf, int index, int value) {
        PlatformDependent.putByte(addr(byteBuf, index), (byte) value);
    }

    static void _setShort(ByteBuf byteBuf, int index, int value) {
        _setShort(byteBuf, index, value, NATIVE_ORDER);
    }

    static void _setShort(ByteBuf byteBuf, int index, int value, boolean nativeByteOrder) {
        PlatformDependent.putShort(addr(byteBuf, index),
                nativeByteOrder ? (short) value : Short.reverseBytes((short) value));
    }

    static void _setMedium(ByteBuf byteBuf, int index, int value) {
        long addr = addr(byteBuf, index);
        PlatformDependent.putByte(addr, (byte) (value >>> 16));
        PlatformDependent.putByte(addr + 1, (byte) (value >>> 8));
        PlatformDependent.putByte(addr + 2, (byte) value);
    }

    static void _setInt(ByteBuf byteBuf, int index, int value) {
        _setInt(byteBuf, index, value, NATIVE_ORDER);
    }

    static void _setInt(ByteBuf byteBuf, int index, int value, boolean nativeByteOrder) {
        PlatformDependent.putInt(addr(byteBuf, index), nativeByteOrder ? value : Integer.reverseBytes(value));
    }

    static void _setLong(ByteBuf byteBuf, int index, long value) {
        _setLong(byteBuf, index, value, NATIVE_ORDER);
    }

    static void _setLong(ByteBuf byteBuf, int index, long value, boolean nativeByteOrder) {
        PlatformDependent.putLong(addr(byteBuf, index), nativeByteOrder ? value : Long.reverseBytes(value));
    }

    static void setBytes(AbstractByteBuf byteBuf, int index, ByteBuf src, int srcIndex, int length) {
        byteBuf.checkIndex(index, length);
        if (src == null) {
            throw new NullPointerException("src");
        }
        if (srcIndex < 0 || srcIndex > src.capacity() - length) {
            throw new IndexOutOfBoundsException("srcIndex: " + srcIndex);
        }

        if (length != 0) {
            if (src.hasMemoryAddress()) {
                PlatformDependent.copyMemory(src.memoryAddress() + srcIndex, addr(byteBuf, index), length);
            } else if (src.hasArray()) {
                PlatformDependent.copyMemory(src.array(), src.arrayOffset() + srcIndex, addr(byteBuf, index), length);
            } else {
                src.getBytes(srcIndex, byteBuf, index, length);
            }
        }
    }

    static void setBytes(AbstractByteBuf byteBuf, int index, byte[] src, int srcIndex, int length) {
        byteBuf.checkIndex(index, length);
        if (length != 0) {
            PlatformDependent.copyMemory(src, srcIndex, addr(byteBuf, index), length);
        }
    }

    static void getBytes(AbstractByteBuf byteBuf, int index, OutputStream out, int length) throws IOException {
        byteBuf.ensureAccessible();
        if (length != 0) {
            byte[] tmp = new byte[length];
            PlatformDependent.copyMemory(addr(byteBuf, index), tmp, 0, length);
            out.write(tmp);
        }
    }

    static long addr(ByteBuf buf, int index) {
        return buf.memoryAddress() + index;
    }

    private UnsafeDirectByteBufUtil() { }
}
