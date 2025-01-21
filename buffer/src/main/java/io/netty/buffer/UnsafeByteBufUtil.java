/*
 * Copyright 2015 The Netty Project
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
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

import static io.netty.util.internal.MathUtil.isOutOfBounds;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.PlatformDependent.BIG_ENDIAN_NATIVE_ORDER;

/**
 * All operations get and set as {@link ByteOrder#BIG_ENDIAN}.
 */
final class UnsafeByteBufUtil {
    private static final boolean UNALIGNED = PlatformDependent.isUnaligned();
    private static final byte ZERO = 0;
    private static final int MAX_HAND_ROLLED_SET_ZERO_BYTES = 64;

    static byte getByte(long address) {
        return PlatformDependent.getByte(address);
    }

    static short getShort(long address) {
        if (UNALIGNED) {
            short v = PlatformDependent.getShort(address);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Short.reverseBytes(v);
        }
        return (short) (PlatformDependent.getByte(address) << 8 | PlatformDependent.getByte(address + 1) & 0xff);
    }

    static short getShortLE(long address) {
        if (UNALIGNED) {
            short v = PlatformDependent.getShort(address);
            return BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes(v) : v;
        }
        return (short) (PlatformDependent.getByte(address) & 0xff | PlatformDependent.getByte(address + 1) << 8);
    }

    static int getUnsignedMedium(long address) {
        if (UNALIGNED) {
            return (PlatformDependent.getByte(address) & 0xff) << 16 |
                    (BIG_ENDIAN_NATIVE_ORDER ? PlatformDependent.getShort(address + 1)
                                             : Short.reverseBytes(PlatformDependent.getShort(address + 1))) & 0xffff;
        }
        return (PlatformDependent.getByte(address)     & 0xff) << 16 |
               (PlatformDependent.getByte(address + 1) & 0xff) << 8  |
               PlatformDependent.getByte(address + 2)  & 0xff;
    }

    static int getUnsignedMediumLE(long address) {
        if (UNALIGNED) {
            return (PlatformDependent.getByte(address) & 0xff) |
                    ((BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes(PlatformDependent.getShort(address + 1))
                                              : PlatformDependent.getShort(address + 1)) & 0xffff) << 8;
        }
        return PlatformDependent.getByte(address)      & 0xff        |
               (PlatformDependent.getByte(address + 1) & 0xff) << 8  |
               (PlatformDependent.getByte(address + 2) & 0xff) << 16;
    }

    static int getInt(long address) {
        if (UNALIGNED) {
            int v = PlatformDependent.getInt(address);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Integer.reverseBytes(v);
        }
        return PlatformDependent.getByte(address) << 24 |
               (PlatformDependent.getByte(address + 1) & 0xff) << 16 |
               (PlatformDependent.getByte(address + 2) & 0xff) <<  8 |
               PlatformDependent.getByte(address + 3)  & 0xff;
    }

    static int getIntLE(long address) {
        if (UNALIGNED) {
            int v = PlatformDependent.getInt(address);
            return BIG_ENDIAN_NATIVE_ORDER ? Integer.reverseBytes(v) : v;
        }
        return PlatformDependent.getByte(address) & 0xff |
               (PlatformDependent.getByte(address + 1) & 0xff) <<  8 |
               (PlatformDependent.getByte(address + 2) & 0xff) << 16 |
               PlatformDependent.getByte(address + 3) << 24;
    }

    static long getLong(long address) {
        if (UNALIGNED) {
            long v = PlatformDependent.getLong(address);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Long.reverseBytes(v);
        }
        return ((long) PlatformDependent.getByte(address)) << 56 |
               (PlatformDependent.getByte(address + 1) & 0xffL) << 48 |
               (PlatformDependent.getByte(address + 2) & 0xffL) << 40 |
               (PlatformDependent.getByte(address + 3) & 0xffL) << 32 |
               (PlatformDependent.getByte(address + 4) & 0xffL) << 24 |
               (PlatformDependent.getByte(address + 5) & 0xffL) << 16 |
               (PlatformDependent.getByte(address + 6) & 0xffL) <<  8 |
               (PlatformDependent.getByte(address + 7)) & 0xffL;
    }

    static long getLongLE(long address) {
        if (UNALIGNED) {
            long v = PlatformDependent.getLong(address);
            return BIG_ENDIAN_NATIVE_ORDER ? Long.reverseBytes(v) : v;
        }
        return (PlatformDependent.getByte(address))    & 0xffL        |
               (PlatformDependent.getByte(address + 1) & 0xffL) <<  8 |
               (PlatformDependent.getByte(address + 2) & 0xffL) << 16 |
               (PlatformDependent.getByte(address + 3) & 0xffL) << 24 |
               (PlatformDependent.getByte(address + 4) & 0xffL) << 32 |
               (PlatformDependent.getByte(address + 5) & 0xffL) << 40 |
               (PlatformDependent.getByte(address + 6) & 0xffL) << 48 |
               ((long) PlatformDependent.getByte(address + 7))  << 56;
    }

    static void setByte(long address, int value) {
        PlatformDependent.putByte(address, (byte) value);
    }

    static void setShort(long address, int value) {
        if (UNALIGNED) {
            PlatformDependent.putShort(
                    address, BIG_ENDIAN_NATIVE_ORDER ? (short) value : Short.reverseBytes((short) value));
        } else {
            PlatformDependent.putByte(address, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 1, (byte) value);
        }
    }

    static void setShortLE(long address, int value) {
        if (UNALIGNED) {
            PlatformDependent.putShort(
                address, BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes((short) value) : (short) value);
        } else {
            PlatformDependent.putByte(address, (byte) value);
            PlatformDependent.putByte(address + 1, (byte) (value >>> 8));
        }
    }

    static void setMedium(long address, int value) {
        PlatformDependent.putByte(address, (byte) (value >>> 16));
        if (UNALIGNED) {
            PlatformDependent.putShort(address + 1, BIG_ENDIAN_NATIVE_ORDER ? (short) value
                                                                            : Short.reverseBytes((short) value));
        } else {
            PlatformDependent.putByte(address + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 2, (byte) value);
        }
    }

    static void setMediumLE(long address, int value) {
        PlatformDependent.putByte(address, (byte) value);
        if (UNALIGNED) {
            PlatformDependent.putShort(address + 1, BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes((short) (value >>> 8))
                                                                            : (short) (value >>> 8));
        } else {
            PlatformDependent.putByte(address + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 2, (byte) (value >>> 16));
        }
    }

    static void setInt(long address, int value) {
        if (UNALIGNED) {
            PlatformDependent.putInt(address, BIG_ENDIAN_NATIVE_ORDER ? value : Integer.reverseBytes(value));
        } else {
            PlatformDependent.putByte(address, (byte) (value >>> 24));
            PlatformDependent.putByte(address + 1, (byte) (value >>> 16));
            PlatformDependent.putByte(address + 2, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 3, (byte) value);
        }
    }

    static void setIntLE(long address, int value) {
        if (UNALIGNED) {
            PlatformDependent.putInt(address, BIG_ENDIAN_NATIVE_ORDER ? Integer.reverseBytes(value) : value);
        } else {
            PlatformDependent.putByte(address, (byte) value);
            PlatformDependent.putByte(address + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 2, (byte) (value >>> 16));
            PlatformDependent.putByte(address + 3, (byte) (value >>> 24));
        }
    }

    static void setLong(long address, long value) {
        if (UNALIGNED) {
            PlatformDependent.putLong(address, BIG_ENDIAN_NATIVE_ORDER ? value : Long.reverseBytes(value));
        } else {
            PlatformDependent.putByte(address, (byte) (value >>> 56));
            PlatformDependent.putByte(address + 1, (byte) (value >>> 48));
            PlatformDependent.putByte(address + 2, (byte) (value >>> 40));
            PlatformDependent.putByte(address + 3, (byte) (value >>> 32));
            PlatformDependent.putByte(address + 4, (byte) (value >>> 24));
            PlatformDependent.putByte(address + 5, (byte) (value >>> 16));
            PlatformDependent.putByte(address + 6, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 7, (byte) value);
        }
    }

    static void setLongLE(long address, long value) {
        if (UNALIGNED) {
            PlatformDependent.putLong(address, BIG_ENDIAN_NATIVE_ORDER ? Long.reverseBytes(value) : value);
        } else {
            PlatformDependent.putByte(address, (byte) value);
            PlatformDependent.putByte(address + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 2, (byte) (value >>> 16));
            PlatformDependent.putByte(address + 3, (byte) (value >>> 24));
            PlatformDependent.putByte(address + 4, (byte) (value >>> 32));
            PlatformDependent.putByte(address + 5, (byte) (value >>> 40));
            PlatformDependent.putByte(address + 6, (byte) (value >>> 48));
            PlatformDependent.putByte(address + 7, (byte) (value >>> 56));
        }
    }

    static byte getByte(byte[] array, int index) {
        return PlatformDependent.getByte(array, index);
    }

    static short getShort(byte[] array, int index) {
        if (UNALIGNED) {
            short v = PlatformDependent.getShort(array, index);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Short.reverseBytes(v);
        }
        return (short) (PlatformDependent.getByte(array, index) << 8 |
                       PlatformDependent.getByte(array, index + 1) & 0xff);
    }

    static short getShortLE(byte[] array, int index) {
        if (UNALIGNED) {
            short v = PlatformDependent.getShort(array, index);
            return BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes(v) : v;
        }
        return (short) (PlatformDependent.getByte(array, index) & 0xff |
                       PlatformDependent.getByte(array, index + 1) << 8);
    }

    static int getUnsignedMedium(byte[] array, int index) {
        if (UNALIGNED) {
            return (PlatformDependent.getByte(array, index) & 0xff) << 16 |
                    (BIG_ENDIAN_NATIVE_ORDER ? PlatformDependent.getShort(array, index + 1)
                                             : Short.reverseBytes(PlatformDependent.getShort(array, index + 1)))
                            & 0xffff;
        }
        return (PlatformDependent.getByte(array, index) & 0xff) << 16 |
               (PlatformDependent.getByte(array, index + 1) & 0xff) <<  8 |
               PlatformDependent.getByte(array, index + 2) & 0xff;
    }

    static int getUnsignedMediumLE(byte[] array, int index) {
        if (UNALIGNED) {
            return (PlatformDependent.getByte(array, index) & 0xff) |
                    ((BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes(PlatformDependent.getShort(array, index + 1))
                                              : PlatformDependent.getShort(array, index + 1)) & 0xffff) << 8;
        }
        return PlatformDependent.getByte(array, index) & 0xff |
               (PlatformDependent.getByte(array, index + 1) & 0xff) <<  8 |
               (PlatformDependent.getByte(array, index + 2) & 0xff) << 16;
    }

    static int getInt(byte[] array, int index) {
        if (UNALIGNED) {
            int v = PlatformDependent.getInt(array, index);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Integer.reverseBytes(v);
        }
        return PlatformDependent.getByte(array, index) << 24 |
               (PlatformDependent.getByte(array, index + 1) & 0xff) << 16 |
               (PlatformDependent.getByte(array, index + 2) & 0xff) <<  8 |
               PlatformDependent.getByte(array, index + 3) & 0xff;
    }

    static int getIntLE(byte[] array, int index) {
        if (UNALIGNED) {
            int v = PlatformDependent.getInt(array, index);
            return BIG_ENDIAN_NATIVE_ORDER ? Integer.reverseBytes(v) : v;
        }
        return PlatformDependent.getByte(array, index)      & 0xff        |
               (PlatformDependent.getByte(array, index + 1) & 0xff) <<  8 |
               (PlatformDependent.getByte(array, index + 2) & 0xff) << 16 |
               PlatformDependent.getByte(array,  index + 3) << 24;
    }

    static long getLong(byte[] array, int index) {
        if (UNALIGNED) {
            long v = PlatformDependent.getLong(array, index);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Long.reverseBytes(v);
        }
        return ((long) PlatformDependent.getByte(array, index)) << 56 |
               (PlatformDependent.getByte(array, index + 1) & 0xffL) << 48 |
               (PlatformDependent.getByte(array, index + 2) & 0xffL) << 40 |
               (PlatformDependent.getByte(array, index + 3) & 0xffL) << 32 |
               (PlatformDependent.getByte(array, index + 4) & 0xffL) << 24 |
               (PlatformDependent.getByte(array, index + 5) & 0xffL) << 16 |
               (PlatformDependent.getByte(array, index + 6) & 0xffL) <<  8 |
               (PlatformDependent.getByte(array, index + 7)) & 0xffL;
    }

    static long getLongLE(byte[] array, int index) {
        if (UNALIGNED) {
            long v = PlatformDependent.getLong(array, index);
            return BIG_ENDIAN_NATIVE_ORDER ? Long.reverseBytes(v) : v;
        }
        return PlatformDependent.getByte(array, index)      & 0xffL        |
               (PlatformDependent.getByte(array, index + 1) & 0xffL) <<  8 |
               (PlatformDependent.getByte(array, index + 2) & 0xffL) << 16 |
               (PlatformDependent.getByte(array, index + 3) & 0xffL) << 24 |
               (PlatformDependent.getByte(array, index + 4) & 0xffL) << 32 |
               (PlatformDependent.getByte(array, index + 5) & 0xffL) << 40 |
               (PlatformDependent.getByte(array, index + 6) & 0xffL) << 48 |
               ((long) PlatformDependent.getByte(array,  index + 7)) << 56;
    }

    static void setByte(byte[] array, int index, int value) {
        PlatformDependent.putByte(array, index, (byte) value);
    }

    static void setShort(byte[] array, int index, int value) {
        if (UNALIGNED) {
            PlatformDependent.putShort(array, index,
                                       BIG_ENDIAN_NATIVE_ORDER ? (short) value : Short.reverseBytes((short) value));
        } else {
            PlatformDependent.putByte(array, index, (byte) (value >>> 8));
            PlatformDependent.putByte(array, index + 1, (byte) value);
        }
    }

    static void setShortLE(byte[] array, int index, int value) {
        if (UNALIGNED) {
            PlatformDependent.putShort(array, index,
                                       BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes((short) value) : (short) value);
        } else {
            PlatformDependent.putByte(array, index, (byte) value);
            PlatformDependent.putByte(array, index + 1, (byte) (value >>> 8));
        }
    }

    static void setMedium(byte[] array, int index, int value) {
        PlatformDependent.putByte(array, index, (byte) (value >>> 16));
        if (UNALIGNED) {
                PlatformDependent.putShort(array, index + 1,
                                           BIG_ENDIAN_NATIVE_ORDER ? (short) value
                                                                   : Short.reverseBytes((short) value));
        } else {
            PlatformDependent.putByte(array, index + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(array, index + 2, (byte) value);
        }
    }

    static void setMediumLE(byte[] array, int index, int value) {
        PlatformDependent.putByte(array, index, (byte) value);
        if (UNALIGNED) {
            PlatformDependent.putShort(array, index + 1,
                                       BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes((short) (value >>> 8))
                                                               : (short) (value >>> 8));
        } else {
            PlatformDependent.putByte(array, index + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(array, index + 2, (byte) (value >>> 16));
        }
    }

    static void setInt(byte[] array, int index, int value) {
        if (UNALIGNED) {
            PlatformDependent.putInt(array, index, BIG_ENDIAN_NATIVE_ORDER ? value : Integer.reverseBytes(value));
        } else {
            PlatformDependent.putByte(array, index, (byte) (value >>> 24));
            PlatformDependent.putByte(array, index + 1, (byte) (value >>> 16));
            PlatformDependent.putByte(array, index + 2, (byte) (value >>> 8));
            PlatformDependent.putByte(array, index + 3, (byte) value);
        }
    }

    static void setIntLE(byte[] array, int index, int value) {
        if (UNALIGNED) {
            PlatformDependent.putInt(array, index, BIG_ENDIAN_NATIVE_ORDER ? Integer.reverseBytes(value) : value);
        } else {
            PlatformDependent.putByte(array, index, (byte) value);
            PlatformDependent.putByte(array, index + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(array, index + 2, (byte) (value >>> 16));
            PlatformDependent.putByte(array, index + 3, (byte) (value >>> 24));
        }
    }

    static void setLong(byte[] array, int index, long value) {
        if (UNALIGNED) {
            PlatformDependent.putLong(array, index, BIG_ENDIAN_NATIVE_ORDER ? value : Long.reverseBytes(value));
        } else {
            PlatformDependent.putByte(array, index, (byte) (value >>> 56));
            PlatformDependent.putByte(array, index + 1, (byte) (value >>> 48));
            PlatformDependent.putByte(array, index + 2, (byte) (value >>> 40));
            PlatformDependent.putByte(array, index + 3, (byte) (value >>> 32));
            PlatformDependent.putByte(array, index + 4, (byte) (value >>> 24));
            PlatformDependent.putByte(array, index + 5, (byte) (value >>> 16));
            PlatformDependent.putByte(array, index + 6, (byte) (value >>> 8));
            PlatformDependent.putByte(array, index + 7, (byte) value);
        }
    }

    static void setLongLE(byte[] array, int index, long value) {
        if (UNALIGNED) {
            PlatformDependent.putLong(array, index, BIG_ENDIAN_NATIVE_ORDER ? Long.reverseBytes(value) : value);
        } else {
            PlatformDependent.putByte(array, index, (byte) value);
            PlatformDependent.putByte(array, index + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(array, index + 2, (byte) (value >>> 16));
            PlatformDependent.putByte(array, index + 3, (byte) (value >>> 24));
            PlatformDependent.putByte(array, index + 4, (byte) (value >>> 32));
            PlatformDependent.putByte(array, index + 5, (byte) (value >>> 40));
            PlatformDependent.putByte(array, index + 6, (byte) (value >>> 48));
            PlatformDependent.putByte(array, index + 7, (byte) (value >>> 56));
        }
    }

    private static void batchSetZero(byte[] data, int index, int length) {
        int longBatches = length / 8;
        for (int i = 0; i < longBatches; i++) {
            PlatformDependent.putLong(data, index, ZERO);
            index += 8;
        }
        final int remaining = length % 8;
        for (int i = 0; i < remaining; i++) {
            PlatformDependent.putByte(data, index + i, ZERO);
        }
    }

    static void setZero(byte[] array, int index, int length) {
        if (length == 0) {
            return;
        }
        // fast-path for small writes to avoid thread-state change JDK's handling
        if (UNALIGNED && length <= MAX_HAND_ROLLED_SET_ZERO_BYTES) {
            batchSetZero(array, index, length);
        } else {
            PlatformDependent.setMemory(array, index, length, ZERO);
        }
    }

    static ByteBuf copy(AbstractByteBuf buf, long addr, int index, int length) {
        buf.checkIndex(index, length);
        ByteBuf copy = buf.alloc().directBuffer(length, buf.maxCapacity());
        if (length != 0) {
            if (copy.hasMemoryAddress()) {
                PlatformDependent.copyMemory(addr, copy.memoryAddress(), length);
                copy.setIndex(0, length);
            } else {
                copy.writeBytes(buf, index, length);
            }
        }
        return copy;
    }

    static int setBytes(AbstractByteBuf buf, long addr, int index, InputStream in, int length) throws IOException {
        buf.checkIndex(index, length);
        ByteBuf tmpBuf = buf.alloc().heapBuffer(length);
        try {
            byte[] tmp = tmpBuf.array();
            int offset = tmpBuf.arrayOffset();
            int readBytes = in.read(tmp, offset, length);
            if (readBytes > 0) {
                PlatformDependent.copyMemory(tmp, offset, addr, readBytes);
            }
            return readBytes;
        } finally {
            tmpBuf.release();
        }
    }

    static void getBytes(AbstractByteBuf buf, long addr, int index, ByteBuf dst, int dstIndex, int length) {
        buf.checkIndex(index, length);
        checkNotNull(dst, "dst");
        if (isOutOfBounds(dstIndex, length, dst.capacity())) {
            throw new IndexOutOfBoundsException("dstIndex: " + dstIndex);
        }

        if (dst.hasMemoryAddress()) {
            PlatformDependent.copyMemory(addr, dst.memoryAddress() + dstIndex, length);
        } else if (dst.hasArray()) {
            PlatformDependent.copyMemory(addr, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, buf, index, length);
        }
    }

    static void getBytes(AbstractByteBuf buf, long addr, int index, byte[] dst, int dstIndex, int length) {
        buf.checkIndex(index, length);
        checkNotNull(dst, "dst");
        if (isOutOfBounds(dstIndex, length, dst.length)) {
            throw new IndexOutOfBoundsException("dstIndex: " + dstIndex);
        }
        if (length != 0) {
            PlatformDependent.copyMemory(addr, dst, dstIndex, length);
        }
    }

    static void getBytes(AbstractByteBuf buf, long addr, int index, ByteBuffer dst) {
        buf.checkIndex(index, dst.remaining());
        if (dst.remaining() == 0) {
            return;
        }

        if (dst.isDirect()) {
            if (dst.isReadOnly()) {
                // We need to check if dst is ready-only so we not write something in it by using Unsafe.
                throw new ReadOnlyBufferException();
            }
            // Copy to direct memory
            long dstAddress = PlatformDependent.directBufferAddress(dst);
            PlatformDependent.copyMemory(addr, dstAddress + dst.position(), dst.remaining());
            dst.position(dst.position() + dst.remaining());
        } else if (dst.hasArray()) {
            // Copy to array
            PlatformDependent.copyMemory(addr, dst.array(), dst.arrayOffset() + dst.position(), dst.remaining());
            dst.position(dst.position() + dst.remaining());
        } else  {
            dst.put(buf.nioBuffer());
        }
    }

    static void setBytes(AbstractByteBuf buf, long addr, int index, ByteBuf src, int srcIndex, int length) {
        buf.checkIndex(index, length);
        checkNotNull(src, "src");
        if (isOutOfBounds(srcIndex, length, src.capacity())) {
            throw new IndexOutOfBoundsException("srcIndex: " + srcIndex);
        }

        if (length != 0) {
            if (src.hasMemoryAddress()) {
                PlatformDependent.copyMemory(src.memoryAddress() + srcIndex, addr, length);
            } else if (src.hasArray()) {
                PlatformDependent.copyMemory(src.array(), src.arrayOffset() + srcIndex, addr, length);
            } else {
                src.getBytes(srcIndex, buf, index, length);
            }
        }
    }

    static void setBytes(AbstractByteBuf buf, long addr, int index, byte[] src, int srcIndex, int length) {
        buf.checkIndex(index, length);
        // we need to check not null for src as it may cause the JVM crash
        // See https://github.com/netty/netty/issues/10791
        checkNotNull(src, "src");
        if (isOutOfBounds(srcIndex, length, src.length)) {
            throw new IndexOutOfBoundsException("srcIndex: " + srcIndex);
        }

        if (length != 0) {
            PlatformDependent.copyMemory(src, srcIndex, addr, length);
        }
    }

    static void setBytes(AbstractByteBuf buf, long addr, int index, ByteBuffer src) {
        final int length = src.remaining();
        if (length == 0) {
            return;
        }

        if (src.isDirect()) {
            buf.checkIndex(index, length);
            // Copy from direct memory
            long srcAddress = PlatformDependent.directBufferAddress(src);
            PlatformDependent.copyMemory(srcAddress + src.position(), addr, length);
            src.position(src.position() + length);
        } else if (src.hasArray()) {
            buf.checkIndex(index, length);
            // Copy from array
            PlatformDependent.copyMemory(src.array(), src.arrayOffset() + src.position(), addr, length);
            src.position(src.position() + length);
        } else {
            if (length < 8) {
                setSingleBytes(buf, addr, index, src, length);
            } else {
                //no need to checkIndex: internalNioBuffer is already taking care of it
                assert buf.nioBufferCount() == 1;
                final ByteBuffer internalBuffer = buf.internalNioBuffer(index, length);
                internalBuffer.put(src);
            }
        }
    }

    private static void setSingleBytes(final AbstractByteBuf buf, final long addr, final int index,
                                       final ByteBuffer src, final int length) {
        buf.checkIndex(index, length);
        final int srcPosition = src.position();
        final int srcLimit = src.limit();
        long dstAddr = addr;
        for (int srcIndex = srcPosition; srcIndex < srcLimit; srcIndex++) {
            final byte value = src.get(srcIndex);
            PlatformDependent.putByte(dstAddr, value);
            dstAddr++;
        }
        src.position(srcLimit);
    }

    static void getBytes(AbstractByteBuf buf, long addr, int index, OutputStream out, int length) throws IOException {
        buf.checkIndex(index, length);
        if (length != 0) {
            int len = Math.min(length, ByteBufUtil.WRITE_CHUNK_SIZE);
            if (len <= ByteBufUtil.MAX_TL_ARRAY_LEN || !buf.alloc().isDirectBufferPooled()) {
                getBytes(addr, ByteBufUtil.threadLocalTempArray(len), 0, len, out, length);
            } else {
                // if direct buffers are pooled chances are good that heap buffers are pooled as well.
                ByteBuf tmpBuf = buf.alloc().heapBuffer(len);
                try {
                    byte[] tmp = tmpBuf.array();
                    int offset = tmpBuf.arrayOffset();
                    getBytes(addr, tmp, offset, len, out, length);
                } finally {
                    tmpBuf.release();
                }
            }
        }
    }

    private static void getBytes(long inAddr, byte[] in, int inOffset, int inLen, OutputStream out, int outLen)
            throws IOException {
        do {
            int len = Math.min(inLen, outLen);
            PlatformDependent.copyMemory(inAddr, in, inOffset, len);
            out.write(in, inOffset, len);
            outLen -= len;
            inAddr += len;
        } while (outLen > 0);
    }

    private static void batchSetZero(long addr, int length) {
        int longBatches = length / 8;
        for (int i = 0; i < longBatches; i++) {
            PlatformDependent.putLong(addr, ZERO);
            addr += 8;
        }
        final int remaining = length % 8;
        for (int i = 0; i < remaining; i++) {
            PlatformDependent.putByte(addr + i, ZERO);
        }
    }

    static void setZero(long addr, int length) {
        if (length == 0) {
            return;
        }
        // fast-path for small writes to avoid thread-state change JDK's handling
        if (length <= MAX_HAND_ROLLED_SET_ZERO_BYTES) {
            if (!UNALIGNED) {
                // write bytes until the address is aligned
                int bytesToGetAligned = zeroTillAligned(addr, length);
                addr += bytesToGetAligned;
                length -= bytesToGetAligned;
                if (length == 0) {
                    return;
                }
                assert addr % 8 == 0;
            }
            batchSetZero(addr, length);
        } else {
            PlatformDependent.setMemory(addr, length, ZERO);
        }
    }

    private static int zeroTillAligned(long addr, int length) {
        // write bytes until the address is aligned
        int bytesToGetAligned = Math.min((int) (addr % 8), length);
        for (int i = 0; i < bytesToGetAligned; i++) {
            PlatformDependent.putByte(addr + i, ZERO);
        }
        return bytesToGetAligned;
    }

    static UnpooledUnsafeDirectByteBuf newUnsafeDirectByteBuf(
            ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        if (PlatformDependent.useDirectBufferNoCleaner()) {
            return new UnpooledUnsafeNoCleanerDirectByteBuf(alloc, initialCapacity, maxCapacity);
        }
        return new UnpooledUnsafeDirectByteBuf(alloc, initialCapacity, maxCapacity);
    }

    private UnsafeByteBufUtil() { }
}
