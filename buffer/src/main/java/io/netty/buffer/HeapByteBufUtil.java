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
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * Helper methods for heap {@link ByteBuf} implementations.
 */
final class HeapByteBufUtil {

    static void getBytes(AbstractByteBuf byteBuf, int index, ByteBuf dst, int dstIndex, int length) {
        byteBuf.checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasMemoryAddress()) {
            PlatformDependent.copyMemory(byteBuf.array(), byteBuf.idx(index), dst.memoryAddress() + dstIndex, length);
        } else if (dst.hasArray()) {
            byteBuf.getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, byteBuf.array(), byteBuf.idx(index), length);
        }
    }

    static void getBytes(AbstractByteBuf byteBuf, int index, byte[] dst, int dstIndex, int length) {
        byteBuf.checkDstIndex(index, length, dstIndex, dst.length);
        System.arraycopy(byteBuf.array(), byteBuf.idx(index), dst, dstIndex, length);
    }

    static void getBytes(AbstractByteBuf byteBuf, int index, ByteBuffer dst) {
        byteBuf.checkIndex(index);
        dst.put(byteBuf.array(), byteBuf.idx(index), Math.min(byteBuf.capacity() - index, dst.remaining()));
    }

    static void getBytes(AbstractByteBuf byteBuf, int index, OutputStream out, int length) throws IOException {
        byteBuf.ensureAccessible();
        out.write(byteBuf.array(), byteBuf.idx(index), length);
    }

    static int getBytes(AbstractByteBuf byteBuf, int index, GatheringByteChannel out, int length) throws IOException {
        return getBytes(byteBuf, index, out, length, false);
    }

    private static int getBytes(AbstractByteBuf byteBuf, int index, GatheringByteChannel out,
                                int length, boolean internal) throws IOException {
        byteBuf.ensureAccessible();
        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = byteBuf.internalNioBuffer();
        } else {
            tmpBuf = ByteBuffer.wrap(byteBuf.array());
        }
        int pos = byteBuf.idx(index);
        return out.write((ByteBuffer) tmpBuf.clear().position(pos).limit(pos + length));
    }

    static int readBytes(AbstractByteBuf byteBuf, GatheringByteChannel out, int length) throws IOException {
        byteBuf.checkReadableBytes(length);
        int readBytes = getBytes(byteBuf, byteBuf.readerIndex, out, length, true);
        byteBuf.readerIndex += readBytes;
        return readBytes;
    }

    static void setBytes(AbstractByteBuf byteBuf, int index, ByteBuf src, int srcIndex, int length) {
        byteBuf.checkSrcIndex(index, length, srcIndex, src.capacity());
        if (src.hasMemoryAddress()) {
            PlatformDependent.copyMemory(src.memoryAddress() + srcIndex, byteBuf.array(), byteBuf.idx(index), length);
        } else  if (src.hasArray()) {
            byteBuf.setBytes(index, src.array(), src.arrayOffset() + srcIndex, length);
        } else {
            src.getBytes(srcIndex, byteBuf.array(), byteBuf.idx(index), length);
        }
    }

    static void setBytes(AbstractByteBuf byteBuf, int index, byte[] src, int srcIndex, int length) {
        byteBuf.checkSrcIndex(index, length, srcIndex, src.length);
        System.arraycopy(src, srcIndex, byteBuf.array(), byteBuf.idx(index), length);
    }

    static void setBytes(AbstractByteBuf byteBuf, int index, ByteBuffer src) {
        byteBuf.ensureAccessible();
        src.get(byteBuf.array(), byteBuf.idx(index), src.remaining());
    }

    static int setBytes(AbstractByteBuf byteBuf, int index, InputStream in, int length) throws IOException {
        byteBuf.ensureAccessible();
        return in.read(byteBuf.array(), byteBuf.idx(index), length);
    }

    static int setBytes(AbstractByteBuf byteBuf, int index, ScatteringByteChannel in, int length) throws IOException {
        byteBuf.ensureAccessible();
        try {
            int pos = byteBuf.idx(index);
            return in.read((ByteBuffer) byteBuf.internalNioBuffer().clear().position(pos).limit(pos + length));
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    static ByteBuffer nioBuffer(AbstractByteBuf byteBuf, int index, int length) {
        byteBuf.ensureAccessible();
        int pos = byteBuf.idx(index);
        return ByteBuffer.wrap(byteBuf.array(), pos, length).slice();
    }

    static ByteBuffer internalNioBuffer(AbstractByteBuf byteBuf, int index, int length) {
        byteBuf.checkIndex(index, length);
        int pos = byteBuf.idx(index);
        return (ByteBuffer) byteBuf.internalNioBuffer().clear().position(pos).limit(pos + length);
    }

    static byte _getByte(byte[] array, int idx) {
        return array[idx];
    }

    static short _getShort(byte[] array, int idx) {
        return (short) (array[idx] << 8 | array[idx + 1] & 0xFF);
    }

    static int _getUnsignedMedium(byte[] array, int idx) {
        return  (array[idx]     & 0xff) << 16 |
                (array[idx + 1] & 0xff) <<  8 |
                array[idx + 2] & 0xff;
    }

    static int _getInt(byte[] array, int idx) {
        return  (array[idx]     & 0xff) << 24 |
                (array[idx + 1] & 0xff) << 16 |
                (array[idx + 2] & 0xff) <<  8 |
                array[idx + 3] & 0xff;
    }

    static long _getLong(byte[] array, int idx) {
        return  ((long) array[idx]     & 0xff) << 56 |
                ((long) array[idx + 1] & 0xff) << 48 |
                ((long) array[idx + 2] & 0xff) << 40 |
                ((long) array[idx + 3] & 0xff) << 32 |
                ((long) array[idx + 4] & 0xff) << 24 |
                ((long) array[idx + 5] & 0xff) << 16 |
                ((long) array[idx + 6] & 0xff) <<  8 |
                (long) array[idx + 7] & 0xff;
    }

    static void _setByte(byte[] array, int idx, int value) {
        array[idx] = (byte) value;
    }

    static void _setShort(byte[] array, int idx, int value) {
        array[idx]     = (byte) (value >>> 8);
        array[idx + 1] = (byte) value;
    }

    static void _setMedium(byte[] array, int idx, int value) {
        array[idx]     = (byte) (value >>> 16);
        array[idx + 1] = (byte) (value >>> 8);
        array[idx + 2] = (byte) value;
    }

    static void _setInt(byte[] array, int idx, int value) {
        array[idx]     = (byte) (value >>> 24);
        array[idx + 1] = (byte) (value >>> 16);
        array[idx + 2] = (byte) (value >>> 8);
        array[idx + 3] = (byte) value;
    }

    static void _setLong(byte[] array, int idx, long value) {
        array[idx]     = (byte) (value >>> 56);
        array[idx + 1] = (byte) (value >>> 48);
        array[idx + 2] = (byte) (value >>> 40);
        array[idx + 3] = (byte) (value >>> 32);
        array[idx + 4] = (byte) (value >>> 24);
        array[idx + 5] = (byte) (value >>> 16);
        array[idx + 6] = (byte) (value >>> 8);
        array[idx + 7] = (byte) value;
    }

    static ByteBuf copy(AbstractByteBuf byteBuf, int index, int length) {
        byteBuf.checkIndex(index, length);
        ByteBuf buffer = byteBuf.alloc().heapBuffer(length);
        byteBuf.getBytes(index, buffer, length);
        return buffer;
    }

    private HeapByteBufUtil() { }
}
