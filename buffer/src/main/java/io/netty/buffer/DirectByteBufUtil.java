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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * Helper methods for direct {@link ByteBuf} implementations.
 */
final class DirectByteBufUtil {

    static int _getUnsignedMedium(AbstractByteBuf byteBuf, int index) {
        return (byteBuf._getByte(index) & 0xff) << 16 | (byteBuf._getByte(index + 1) & 0xff) << 8
                | byteBuf._getByte(index + 2) & 0xff;
    }

    static void _setMedium(AbstractByteBuf byteBuf, int index, int value) {
        byteBuf._setByte(index, (byte) (value >>> 16));
        byteBuf._setByte(index + 1, (byte) (value >>> 8));
        byteBuf._setByte(index + 2, (byte) value);
    }

    static ByteBuffer nioBuffer(AbstractByteBuf byteBuf, ByteBuffer buffer, int index, int length) {
        byteBuf.checkIndex(index, length);
        int pos = byteBuf.idx(index);
        return ((ByteBuffer) buffer.duplicate().position(pos).limit(pos + length)).slice();
    }

    static void getBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, int index, ByteBuffer dst) {
        getBytes(byteBuf, buffer, index, dst, false);
    }

    private static void getBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, int index,
                                 ByteBuffer dst, boolean internal) {
        byteBuf.checkIndex(index);
        if (dst == null) {
            throw new NullPointerException("dst");
        }

        int bytesToCopy = Math.min(byteBuf.capacity() - index, dst.remaining());
        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = byteBuf.internalNioBuffer();
        } else {
            tmpBuf = buffer.duplicate();
        }
        int pos = byteBuf.idx(index);
        tmpBuf.clear().position(pos).limit(pos + bytesToCopy);
        dst.put(tmpBuf);
    }

    static void readBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, ByteBuffer dst) {
        int length = dst.remaining();
        byteBuf.checkReadableBytes(length);
        getBytes(byteBuf, buffer, byteBuf.readerIndex, dst, true);
        byteBuf.readerIndex += length;
    }

    static int getBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, int index, GatheringByteChannel out, int length)
            throws IOException {
        return getBytes(byteBuf, buffer, index, out, length, false);
    }

    static int readBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, GatheringByteChannel out, int length)
            throws IOException {
        byteBuf.checkReadableBytes(length);
        int readBytes = getBytes(byteBuf, buffer, byteBuf.readerIndex, out, length, true);
        byteBuf.readerIndex += readBytes;
        return readBytes;
    }

    private static int getBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, int index,
                                GatheringByteChannel out, int length, boolean internal) throws IOException {
        byteBuf.ensureAccessible();
        if (length == 0) {
            return 0;
        }

        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = byteBuf.internalNioBuffer();
        } else {
            tmpBuf = buffer.duplicate();
        }
        int pos = byteBuf.idx(index);
        tmpBuf.clear().position(pos).limit(pos + length);
        return out.write(tmpBuf);
    }

    static void getBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, int index, byte[] dst, int dstIndex, int length) {
        getBytes(byteBuf, buffer, index, dst, dstIndex, length, false);
    }

    private static void getBytes(AbstractByteBuf byteBuf, ByteBuffer buffer,
                                 int index, byte[] dst, int dstIndex, int length, boolean internal) {
        byteBuf.checkDstIndex(index, length, dstIndex, dst.length);

        if (dstIndex < 0 || dstIndex > dst.length - length) {
            throw new IndexOutOfBoundsException(String.format(
                    "dstIndex: %d, length: %d (expected: range(0, %d))", dstIndex, length, dst.length));
        }

        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = byteBuf.internalNioBuffer();
        } else {
            tmpBuf = buffer.duplicate();
        }
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.get(dst, dstIndex, length);
    }

    static void readBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, byte[] dst, int dstIndex, int length) {
        byteBuf.checkReadableBytes(length);
        getBytes(byteBuf, buffer, byteBuf.readerIndex, dst, dstIndex, length, true);
        byteBuf.readerIndex += length;
    }

    static void getBytes(AbstractByteBuf byteBuf, int index, ByteBuf dst, int dstIndex, int length) {
        byteBuf.checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasArray()) {
            byteBuf.getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else if (dst.nioBufferCount() > 0) {
            for (ByteBuffer bb: dst.nioBuffers(dstIndex, length)) {
                int bbLen = bb.remaining();
                byteBuf.getBytes(index, bb);
                index += bbLen;
            }
        } else {
            dst.setBytes(dstIndex, byteBuf, index, length);
        }
    }

    static void getBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, int index, OutputStream out, int length)
            throws IOException {
        getBytes(byteBuf, buffer, index, out, length, false);
    }

    private static void getBytes(AbstractByteBuf byteBuf, ByteBuffer buffer, int index,
                          OutputStream out, int length, boolean internal) throws IOException {
        byteBuf.ensureAccessible();
        if (length == 0) {
            return;
        }
        byte[] tmp = new byte[length];
        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = byteBuf.internalNioBuffer();
        } else {
            tmpBuf = buffer.duplicate();
        }
        tmpBuf.clear().position(index);
        tmpBuf.get(tmp);
        out.write(tmp);
    }

    static void readBytes(
            AbstractByteBuf byteBuf, ByteBuffer buffer, OutputStream out, int length) throws IOException {
        byteBuf.checkReadableBytes(length);
        getBytes(byteBuf, buffer, byteBuf.readerIndex, out, length, true);
        byteBuf.readerIndex += length;
    }

    static void setBytes(AbstractByteBuf byteBuf, int index, ByteBuffer src) {
        byteBuf.ensureAccessible();
        ByteBuffer tmpBuf = byteBuf.internalNioBuffer();
        if (src == tmpBuf) {
            src = src.duplicate();
        }
        int pos = byteBuf.idx(index);
        tmpBuf.clear().position(pos).limit(pos + src.remaining());
        tmpBuf.put(src);
    }

    static int setBytes(AbstractByteBuf byteBuf, int index, ScatteringByteChannel in, int length) throws IOException {
        byteBuf.ensureAccessible();
        ByteBuffer tmpBuf = byteBuf.internalNioBuffer();
        int pos = byteBuf.idx(index);
        tmpBuf.clear().position(pos).limit(pos + length);
        try {
            return in.read(tmpBuf);
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    static int setBytes(AbstractByteBuf byteBuf, int index, InputStream in, int length) throws IOException {
        byteBuf.ensureAccessible();
        byte[] tmp = new byte[length];
        int readBytes = in.read(tmp);
        if (readBytes <= 0) {
            return readBytes;
        }
        ByteBuffer tmpBuf = byteBuf.internalNioBuffer();
        tmpBuf.clear().position(index);
        tmpBuf.put(tmp, 0, readBytes);
        return readBytes;
    }

    static void setBytes(AbstractByteBuf byteBuf, int index, ByteBuf src, int srcIndex, int length) {
        byteBuf.checkSrcIndex(index, length, srcIndex, src.capacity());
        if (src.nioBufferCount() > 0) {
            for (ByteBuffer bb: src.nioBuffers(srcIndex, length)) {
                int bbLen = bb.remaining();
                byteBuf.setBytes(index, bb);
                index += bbLen;
            }
        } else {
            src.getBytes(srcIndex, byteBuf, index, length);
        }
    }

    static void setBytes(AbstractByteBuf byteBuf, int index, byte[] src, int srcIndex, int length) {
        byteBuf.checkSrcIndex(index, length, srcIndex, src.length);
        ByteBuffer tmpBuf = byteBuf.internalNioBuffer();
        int pos = byteBuf.idx(index);
        tmpBuf.clear().position(pos).limit(pos + length);
        tmpBuf.put(src, srcIndex, length);
    }

    static ByteBuf copy(AbstractByteBuf byteBuf, int index, int length) {
        byteBuf.checkIndex(index, length);
        ByteBuf copy = byteBuf.alloc().directBuffer(length, byteBuf.maxCapacity());
        copy.writeBytes(byteBuf, index, length);
        return copy;
    }

    static ByteBuffer internalNioBuffer(AbstractByteBuf byteBuf, int index, int length) {
        byteBuf.checkIndex(index, length);
        int pos = byteBuf.idx(index);
        return (ByteBuffer) byteBuf.internalNioBuffer().clear().position(pos).limit(pos + length);
    }

    private DirectByteBufUtil() { }
}
