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
/*
 * Adaptation of http://bjoern.hoehrmann.de/utf-8/decoder/dfa/
 *
 * Copyright (c) 2008-2009 Bjoern Hoehrmann <bjoern@hoehrmann.de>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.netty.buffer;

import io.netty.util.CharsetUtil;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;


/**
 * A skeletal implementation of a buffer.
 */
public abstract class AbstractByteBuf extends ByteBuf {

    private static final int UTF8_ACCEPT = 0;
    private static final int UTF8_REJECT = 12;
    private static final char REPLACEMENT = '\ufffd';

    private static final byte[] TYPES = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8,
            8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8,
            8, 8, 8, 8, 8, 8 };

    private static final byte[] STATES = { 0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12,
            12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
            12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 36,
            12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12,
            12, 12, 12, 12, 12, 12 };

    static final ResourceLeakDetector<ByteBuf> leakDetector = new ResourceLeakDetector<ByteBuf>(ByteBuf.class);

    int readerIndex;
    private int writerIndex;
    private int markedReaderIndex;
    private int markedWriterIndex;

    private int maxCapacity;

    private SwappedByteBuf swappedBuf;

    protected AbstractByteBuf(int maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity: " + maxCapacity + " (expected: >= 0)");
        }
        this.maxCapacity = maxCapacity;
    }

    @Override
    public int maxCapacity() {
        return maxCapacity;
    }

    protected final void maxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    @Override
    public int readerIndex() {
        return readerIndex;
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        if (readerIndex < 0 || readerIndex > writerIndex) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex: %d (expected: 0 <= readerIndex <= writerIndex(%d))", readerIndex, writerIndex));
        }
        this.readerIndex = readerIndex;
        return this;
    }

    @Override
    public int writerIndex() {
        return writerIndex;
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        if (writerIndex < readerIndex || writerIndex > capacity()) {
            throw new IndexOutOfBoundsException(String.format(
                    "writerIndex: %d (expected: readerIndex(%d) <= writerIndex <= capacity(%d))",
                    writerIndex, readerIndex, capacity()));
        }
        this.writerIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        if (readerIndex < 0 || readerIndex > writerIndex || writerIndex > capacity()) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex: %d, writerIndex: %d (expected: 0 <= readerIndex <= writerIndex <= capacity(%d))",
                    readerIndex, writerIndex, capacity()));
        }
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf clear() {
        readerIndex = writerIndex = 0;
        return this;
    }

    @Override
    public boolean isReadable() {
        return writerIndex > readerIndex;
    }

    @Override
    public boolean isReadable(int numBytes) {
        return writerIndex - readerIndex >= numBytes;
    }

    @Override
    public boolean isWritable() {
        return capacity() > writerIndex;
    }

    @Override
    public boolean isWritable(int numBytes) {
        return capacity() - writerIndex >= numBytes;
    }

    @Override
    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    @Override
    public int writableBytes() {
        return capacity() - writerIndex;
    }

    @Override
    public int maxWritableBytes() {
        return maxCapacity() - writerIndex;
    }

    @Override
    public ByteBuf markReaderIndex() {
        markedReaderIndex = readerIndex;
        return this;
    }

    @Override
    public ByteBuf resetReaderIndex() {
        readerIndex(markedReaderIndex);
        return this;
    }

    @Override
    public ByteBuf markWriterIndex() {
        markedWriterIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf resetWriterIndex() {
        writerIndex = markedWriterIndex;
        return this;
    }

    @Override
    public ByteBuf discardReadBytes() {
        ensureAccessible();
        if (readerIndex == 0) {
            return this;
        }

        if (readerIndex != writerIndex) {
            setBytes(0, this, readerIndex, writerIndex - readerIndex);
            writerIndex -= readerIndex;
            adjustMarkers(readerIndex);
            readerIndex = 0;
        } else {
            adjustMarkers(readerIndex);
            writerIndex = readerIndex = 0;
        }
        return this;
    }

    @Override
    public ByteBuf discardSomeReadBytes() {
        ensureAccessible();
        if (readerIndex == 0) {
            return this;
        }

        if (readerIndex == writerIndex) {
            adjustMarkers(readerIndex);
            writerIndex = readerIndex = 0;
            return this;
        }

        if (readerIndex >= capacity() >>> 1) {
            setBytes(0, this, readerIndex, writerIndex - readerIndex);
            writerIndex -= readerIndex;
            adjustMarkers(readerIndex);
            readerIndex = 0;
        }
        return this;
    }

    protected final void adjustMarkers(int decrement) {
        int markedReaderIndex = this.markedReaderIndex;
        if (markedReaderIndex <= decrement) {
            this.markedReaderIndex = 0;
            int markedWriterIndex = this.markedWriterIndex;
            if (markedWriterIndex <= decrement) {
                this.markedWriterIndex = 0;
            } else {
                this.markedWriterIndex = markedWriterIndex - decrement;
            }
        } else {
            this.markedReaderIndex = markedReaderIndex - decrement;
            markedWriterIndex -= decrement;
        }
    }

    @Override
    public ByteBuf ensureWritable(int minWritableBytes) {
        if (minWritableBytes < 0) {
            throw new IllegalArgumentException(String.format(
                    "minWritableBytes: %d (expected: >= 0)", minWritableBytes));
        }

        if (minWritableBytes <= writableBytes()) {
            return this;
        }

        if (minWritableBytes > maxCapacity - writerIndex) {
            throw new IndexOutOfBoundsException(String.format(
                    "writerIndex(%d) + minWritableBytes(%d) exceeds maxCapacity(%d): %s",
                    writerIndex, minWritableBytes, maxCapacity, this));
        }

        // Normalize the current capacity to the power of 2.
        int newCapacity = calculateNewCapacity(writerIndex + minWritableBytes);

        // Adjust to the new capacity.
        capacity(newCapacity);
        return this;
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        if (minWritableBytes < 0) {
            throw new IllegalArgumentException(String.format(
                    "minWritableBytes: %d (expected: >= 0)", minWritableBytes));
        }

        if (minWritableBytes <= writableBytes()) {
            return 0;
        }

        if (minWritableBytes > maxCapacity - writerIndex) {
            if (force) {
                if (capacity() == maxCapacity()) {
                    return 1;
                }

                capacity(maxCapacity());
                return 3;
            }
        }

        // Normalize the current capacity to the power of 2.
        int newCapacity = calculateNewCapacity(writerIndex + minWritableBytes);

        // Adjust to the new capacity.
        capacity(newCapacity);
        return 2;
    }

    private int calculateNewCapacity(int minNewCapacity) {
        final int maxCapacity = this.maxCapacity;
        final int threshold = 1048576 * 4; // 4 MiB page

        if (minNewCapacity == threshold) {
            return threshold;
        }

        // If over threshold, do not double but just increase by threshold.
        if (minNewCapacity > threshold) {
            int newCapacity = minNewCapacity / threshold * threshold;
            if (newCapacity > maxCapacity - threshold) {
                newCapacity = maxCapacity;
            } else {
                newCapacity += threshold;
            }
            return newCapacity;
        }

        // Not over threshold. Double up to 4 MiB, starting from 64.
        int newCapacity = 64;
        while (newCapacity < minNewCapacity) {
            newCapacity <<= 1;
        }

        return Math.min(newCapacity, maxCapacity);
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }
        if (endianness == order()) {
            return this;
        }

        SwappedByteBuf swappedBuf = this.swappedBuf;
        if (swappedBuf == null) {
            this.swappedBuf = swappedBuf = new SwappedByteBuf(this);
        }
        return swappedBuf;
    }

    @Override
    public byte getByte(int index) {
        checkIndex(index);
        return _getByte(index);
    }

    protected abstract byte _getByte(int index);

    @Override
    public boolean getBoolean(int index) {
        return getByte(index) != 0;
    }

    @Override
    public short getUnsignedByte(int index) {
        return (short) (getByte(index) & 0xFF);
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return _getShort(index);
    }

    protected abstract short _getShort(int index);

    @Override
    public int getUnsignedShort(int index) {
        return getShort(index) & 0xFFFF;
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return _getUnsignedMedium(index);
    }

    protected abstract int _getUnsignedMedium(int index);

    @Override
    public int getMedium(int index) {
        int value = getUnsignedMedium(index);
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return _getInt(index);
    }

    protected abstract int _getInt(int index);

    @Override
    public long getUnsignedInt(int index) {
        return getInt(index) & 0xFFFFFFFFL;
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return _getLong(index);
    }

    protected abstract long _getLong(int index);

    @Override
    public char getChar(int index) {
        return (char) getShort(index);
    }

    @Override
    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    @Override
    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        getBytes(index, dst, 0, dst.length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        getBytes(index, dst, dst.writableBytes());
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        getBytes(index, dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        checkIndex(index);
        _setByte(index, value);
        return this;
    }

    protected abstract void _setByte(int index, int value);

    @Override
    public ByteBuf setBoolean(int index, boolean value) {
        setByte(index, value ? 1 : 0);
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        checkIndex(index, 2);
        _setShort(index, value);
        return this;
    }

    protected abstract void _setShort(int index, int value);

    @Override
    public ByteBuf setChar(int index, int value) {
        setShort(index, value);
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        checkIndex(index, 3);
        _setMedium(index, value);
        return this;
    }

    protected abstract void _setMedium(int index, int value);

    @Override
    public ByteBuf setInt(int index, int value) {
        checkIndex(index, 4);
        _setInt(index, value);
        return this;
    }

    protected abstract void _setInt(int index, int value);

    @Override
    public ByteBuf setFloat(int index, float value) {
        setInt(index, Float.floatToRawIntBits(value));
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        checkIndex(index, 8);
        _setLong(index, value);
        return this;
    }

    protected abstract void _setLong(int index, long value);

    @Override
    public ByteBuf setDouble(int index, double value) {
        setLong(index, Double.doubleToRawLongBits(value));
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        setBytes(index, src, 0, src.length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        setBytes(index, src, src.readableBytes());
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        checkIndex(index, length);
        if (src == null) {
            throw new NullPointerException("src");
        }
        if (length > src.readableBytes()) {
            throw new IndexOutOfBoundsException(String.format(
                    "length(%d) exceeds src.readableBytes(%d) where src is: %s", length, src.readableBytes(), src));
        }

        setBytes(index, src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
        return this;
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        if (length == 0) {
            return this;
        }

        checkIndex(index, length);

        int nLong = length >>> 3;
        int nBytes = length & 7;
        for (int i = nLong; i > 0; i --) {
            setLong(index, 0);
            index += 8;
        }
        if (nBytes == 4) {
            setInt(index, 0);
        } else if (nBytes < 4) {
            for (int i = nBytes; i > 0; i --) {
                setByte(index, (byte) 0);
                index ++;
            }
        } else {
            setInt(index, 0);
            index += 4;
            for (int i = nBytes - 4; i > 0; i --) {
                setByte(index, (byte) 0);
                index ++;
            }
        }
        return this;
    }

    @Override
    public byte readByte() {
        checkReadableBytes(1);
        int i = readerIndex;
        byte b = getByte(i);
        readerIndex = i + 1;
        return b;
    }

    @Override
    public boolean readBoolean() {
        return readByte() != 0;
    }

    @Override
    public short readUnsignedByte() {
        return (short) (readByte() & 0xFF);
    }

    @Override
    public short readShort() {
        checkReadableBytes(2);
        short v = _getShort(readerIndex);
        readerIndex += 2;
        return v;
    }

    @Override
    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    @Override
    public int readMedium() {
        int value = readUnsignedMedium();
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public int readUnsignedMedium() {
        checkReadableBytes(3);
        int v = _getUnsignedMedium(readerIndex);
        readerIndex += 3;
        return v;
    }

    @Override
    public int readInt() {
        checkReadableBytes(4);
        int v = _getInt(readerIndex);
        readerIndex += 4;
        return v;
    }

    @Override
    public long readUnsignedInt() {
        return readInt() & 0xFFFFFFFFL;
    }

    @Override
    public long readLong() {
        checkReadableBytes(8);
        long v = _getLong(readerIndex);
        readerIndex += 8;
        return v;
    }

    @Override
    public char readChar() {
        return (char) readShort();
    }

    @Override
    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public ByteBuf readBytes(int length) {
        checkReadableBytes(length);
        if (length == 0) {
            return Unpooled.EMPTY_BUFFER;
        }

        // Use an unpooled heap buffer because there's no way to mandate a user to free the returned buffer.
        ByteBuf buf = Unpooled.buffer(length, maxCapacity);
        buf.writeBytes(this, readerIndex, length);
        readerIndex += length;
        return buf;
    }

    @Override
    public ByteBuf readSlice(int length) {
        ByteBuf slice = slice(readerIndex, length);
        readerIndex += length;
        return slice;
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        readBytes(dst, 0, dst.length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        readBytes(dst, dst.writableBytes());
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        if (length > dst.writableBytes()) {
            throw new IndexOutOfBoundsException(String.format(
                    "length(%d) exceeds dst.writableBytes(%d) where dst is: %s", length, dst.writableBytes(), dst));
        }
        readBytes(dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        checkReadableBytes(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        int length = dst.remaining();
        checkReadableBytes(length);
        getBytes(readerIndex, dst);
        readerIndex += length;
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length)
            throws IOException {
        checkReadableBytes(length);
        int readBytes = getBytes(readerIndex, out, length);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        checkReadableBytes(length);
        getBytes(readerIndex, out, length);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf skipBytes(int length) {
        checkReadableBytes(length);

        int newReaderIndex = readerIndex + length;
        if (newReaderIndex > writerIndex) {
            throw new IndexOutOfBoundsException(String.format(
                    "length: %d (expected: readerIndex(%d) + length <= writerIndex(%d))",
                    length, readerIndex, writerIndex));
        }
        readerIndex = newReaderIndex;
        return this;
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
        return this;
    }

    @Override
    public ByteBuf writeByte(int value) {
        ensureWritable(1);
        setByte(writerIndex++, value);
        return this;
    }

    @Override
    public ByteBuf writeShort(int value) {
        ensureWritable(2);
        _setShort(writerIndex, value);
        writerIndex += 2;
        return this;
    }

    @Override
    public ByteBuf writeMedium(int value) {
        ensureWritable(3);
        _setMedium(writerIndex, value);
        writerIndex += 3;
        return this;
    }

    @Override
    public ByteBuf writeInt(int value) {
        ensureWritable(4);
        _setInt(writerIndex, value);
        writerIndex += 4;
        return this;
    }

    @Override
    public ByteBuf writeLong(long value) {
        ensureWritable(8);
        _setLong(writerIndex, value);
        writerIndex += 8;
        return this;
    }

    @Override
    public ByteBuf writeChar(int value) {
        writeShort(value);
        return this;
    }

    @Override
    public ByteBuf writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
        return this;
    }

    @Override
    public ByteBuf writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        ensureWritable(length);
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        writeBytes(src, 0, src.length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        writeBytes(src, src.readableBytes());
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        if (length > src.readableBytes()) {
            throw new IndexOutOfBoundsException(String.format(
                    "length(%d) exceeds src.readableBytes(%d) where src is: %s", length, src.readableBytes(), src));
        }
        writeBytes(src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        ensureWritable(length);
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        int length = src.remaining();
        ensureWritable(length);
        setBytes(writerIndex, src);
        writerIndex += length;
        return this;
    }

    @Override
    public int writeBytes(InputStream in, int length)
            throws IOException {
        ensureWritable(length);
        int writtenBytes = setBytes(writerIndex, in, length);
        if (writtenBytes > 0) {
            writerIndex += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        ensureWritable(length);
        int writtenBytes = setBytes(writerIndex, in, length);
        if (writtenBytes > 0) {
            writerIndex += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public ByteBuf writeZero(int length) {
        if (length == 0) {
            return this;
        }

        ensureWritable(length);
        checkIndex(writerIndex, length);

        int nLong = length >>> 3;
        int nBytes = length & 7;
        for (int i = nLong; i > 0; i --) {
            writeLong(0);
        }
        if (nBytes == 4) {
            writeInt(0);
        } else if (nBytes < 4) {
            for (int i = nBytes; i > 0; i --) {
                writeByte((byte) 0);
            }
        } else {
            writeInt(0);
            for (int i = nBytes - 4; i > 0; i --) {
                writeByte((byte) 0);
            }
        }
        return this;
    }

    @Override
    public ByteBuf copy() {
        return copy(readerIndex, readableBytes());
    }

    @Override
    public ByteBuf duplicate() {
        return new DuplicatedByteBuf(this);
    }

    @Override
    public ByteBuf slice() {
        return slice(readerIndex, readableBytes());
    }

    @Override
    public ByteBuf slice(int index, int length) {
        if (length == 0) {
            return Unpooled.EMPTY_BUFFER;
        }

        return new SlicedByteBuf(this, index, length);
    }

    @Override
    public ByteBuffer nioBuffer() {
        return nioBuffer(readerIndex, readableBytes());
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return nioBuffers(readerIndex, readableBytes());
    }

    @Override
    public String toString(Charset charset) {
        return toString(readerIndex, readableBytes(), charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        if (length == 0) {
            return "";
        }
        // TODO: Optimize index / length check
        checkIndex(index);
        checkReadableBytes(length);

        if (charset.equals(CharsetUtil.US_ASCII)) {
            return decodeUsAscii(index, length);
        }
        if (charset.equals(CharsetUtil.UTF_8)) {
            return decodeUtf8(index, length);
        }
        ByteBuffer nioBuffer;
        if (nioBufferCount() == 1) {
            nioBuffer = nioBuffer(index, length);
        } else {
            nioBuffer = ByteBuffer.allocate(length);
            getBytes(index, nioBuffer);
            nioBuffer.flip();
        }

        return ByteBufUtil.decodeString(nioBuffer, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return ByteBufUtil.indexOf(this, fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return bytesBefore(readerIndex(), readableBytes(), value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        checkReadableBytes(length);
        return bytesBefore(readerIndex(), length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        int endIndex = indexOf(index, index + length, value);
        if (endIndex < 0) {
            return -1;
        }
        return endIndex - index;
    }

    @Override
    public int forEachByte(ByteBufProcessor processor) {
        int index = readerIndex;
        int length = writerIndex - index;
        return forEachByteAsc0(index, length, processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteBufProcessor processor) {
        checkIndex(index, length);
        return forEachByteAsc0(index, length, processor);
    }

    private int forEachByteAsc0(int index, int length, ByteBufProcessor processor) {
        if (processor == null) {
            throw new NullPointerException("processor");
        }

        if (length == 0) {
            return -1;
        }

        final int endIndex = index + length;
        int i = index;
        try {
            do {
                if (processor.process(_getByte(i))) {
                    i ++;
                } else {
                    return i;
                }
            } while (i < endIndex);
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        return -1;
    }

    @Override
    public int forEachByteDesc(ByteBufProcessor processor) {
        int index = readerIndex;
        int length = writerIndex - index;
        return forEachByteDesc0(index, length, processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteBufProcessor processor) {
        checkIndex(index, length);
        return forEachByteDesc0(index, length, processor);
    }

    private int forEachByteDesc0(int index, int length, ByteBufProcessor processor) {
        if (processor == null) {
            throw new NullPointerException("processor");
        }

        if (length == 0) {
            return -1;
        }

        int i = index + length - 1;
        try {
            do {
                if (processor.process(_getByte(i))) {
                    i --;
                } else {
                    return i;
                }
            } while (i >= index);
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        return -1;
    }

    @Override
    public int hashCode() {
        return ByteBufUtil.hashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof ByteBuf) {
            return ByteBufUtil.equals(this, (ByteBuf) o);
        }
        return false;
    }

    @Override
    public int compareTo(ByteBuf that) {
        return ByteBufUtil.compare(this, that);
    }

    @Override
    public String toString() {
        if (refCnt() == 0) {
            return getClass().getSimpleName() + "(freed)";
        }

        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(ridx: ");
        buf.append(readerIndex);
        buf.append(", widx: ");
        buf.append(writerIndex);
        buf.append(", cap: ");
        buf.append(capacity());
        if (maxCapacity != Integer.MAX_VALUE) {
            buf.append('/');
            buf.append(maxCapacity);
        }

        ByteBuf unwrapped = unwrap();
        if (unwrapped != null) {
            buf.append(", unwrapped: ");
            buf.append(unwrapped);
        }
        buf.append(')');
        return buf.toString();
    }

    protected final void checkIndex(int index) {
        ensureAccessible();
        if (index < 0 || index >= capacity()) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d (expected: range(0, %d))", index, capacity()));
        }
    }

    protected final void checkIndex(int index, int fieldLength) {
        ensureAccessible();
        if (fieldLength < 0) {
            throw new IllegalArgumentException("length: " + fieldLength + " (expected: >= 0)");
        }
        if (index < 0 || index > capacity() - fieldLength) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d, length: %d (expected: range(0, %d))", index, fieldLength, capacity()));
        }
    }

    protected final void checkSrcIndex(int index, int length, int srcIndex, int srcCapacity) {
        checkIndex(index, length);
        if (srcIndex < 0 || srcIndex > srcCapacity - length) {
            throw new IndexOutOfBoundsException(String.format(
                    "srcIndex: %d, length: %d (expected: range(0, %d))", srcIndex, length, srcCapacity));
        }
    }

    protected final void checkDstIndex(int index, int length, int dstIndex, int dstCapacity) {
        checkIndex(index, length);
        if (dstIndex < 0 || dstIndex > dstCapacity - length) {
            throw new IndexOutOfBoundsException(String.format(
                    "dstIndex: %d, length: %d (expected: range(0, %d))", dstIndex, length, dstCapacity));
        }
    }

    /**
     * Throws an {@link IndexOutOfBoundsException} if the current
     * {@linkplain #readableBytes() readable bytes} of this buffer is less
     * than the specified value.
     */
    protected final void checkReadableBytes(int minimumReadableBytes) {
        ensureAccessible();
        if (minimumReadableBytes < 0) {
            throw new IllegalArgumentException("minimumReadableBytes: " + minimumReadableBytes + " (expected: >= 0)");
        }
        if (readerIndex > writerIndex - minimumReadableBytes) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex(%d) + length(%d) exceeds writerIndex(%d): %s",
                    readerIndex, minimumReadableBytes, writerIndex, this));
        }
    }

    /**
     * Should be called by every method that tries to access the buffers content to check
     * if the buffer was released before.
     */
    protected final void ensureAccessible() {
        if (refCnt() == 0) {
            throw new IllegalReferenceCountException(0);
        }
    }

    @Override
    public ByteBuf writeCharSequence(CharSequence seq, Charset charset) {
        writerIndex +=  setCharSequence0(writerIndex, seq, charset);
        return this;
    }

    @Override
    public ByteBuf setCharSequence(int index, CharSequence seq, Charset charset) {
        setCharSequence0(index, seq, charset);
        return this;
    }

    private int setCharSequence0(int index, CharSequence seq, Charset charset) {
        // check if we can write the seq in an optimized way
        if (charset.equals(CharsetUtil.US_ASCII)) {
            int length = seq.length();
            checkIndex(index, length);
            return encodeUsAscii(seq, index);
        }
        if (charset.equals(CharsetUtil.UTF_8)) {
            int length = seq.length() * 4;
            checkIndex(index, length);
            return encodeUtf8(seq, index, length);
        }
        if (nioBufferCount() == 1) {
            return ByteBufUtil.encodeCharBuffer(this, index, CharBuffer.wrap(seq), charset);
        } else {
            byte[] bytes = seq.toString().getBytes(charset);
            setBytes(index, bytes);
            return bytes.length;
        }
    }

    private int encodeUsAscii(CharSequence str, int index) {
        int length = str.length();
        for (int i = 0; i < length; i++) {
            _setByte(index++, (byte) str.charAt(i));
        }
        return length;
    }

    private String decodeUsAscii(int index, int length) {
        // TODO: Maybe we want to have some pre-allocated char[] in a ThreadLocal
        //       and only create a new one of the needed is bigger then the stored
        //       this would safe us one allocation and also the collection of this.
        char[] chars = new char[length];
        for (int i = 0;  i < length; i++) {
            chars[i] = (char) _getByte(index++);
        }
        return new String(chars);
    }

    // Based on:
    // http://bjoern.hoehrmann.de/utf-8/decoder/dfa/
    private String decodeUtf8(int index, int length) {
        long end = index + length;
        int out = 0;
        // TODO: Maybe we want to have some pre-allocated char[] in a ThreadLocal
        //       and only create a new one of the needed is bigger then the stored
        //       this would safe us one allocation and also the collection of this.
        char[] chars = new char[length];
        int codep = 0;
        int state = UTF8_ACCEPT;
        int prevState = state;
        while (index < end) {
            byte b = _getByte(index++);
            byte type = TYPES[b & 0xFF];

            codep = state != UTF8_ACCEPT ? b & 0x3f | codep << 6 : 0xff >> type & b;

            state = STATES[state + type];

            if (state == UTF8_ACCEPT) {
                chars[out++] = (char) codep;
            } else if (state == UTF8_REJECT) {
                // Try to replace invalid sequences
                chars[out++] = REPLACEMENT;
                state = UTF8_ACCEPT;
                if (prevState != UTF8_ACCEPT) {
                    index--;
                }
            }
            prevState = state;
        }
        return new String(chars, 0, out);
    }

    // Based on:
    //  * https://github.com/nitsanw/javanetperf/blob/psylobsaw/src/psy/lob/saw/CustomUtf8Encoder.java
    //  * http://psy-lob-saw.blogspot.co.uk/2012/12/encode-utf-8-string-to-bytebuffer-faster.html
    private int encodeUtf8(CharSequence seq, int index, int length) {
        int startIndex = index;
        int dstLimit = index + length;
        int sourceOffset = 0;
        int sourceLength = seq.length();
        int dlASCII = index + Math.min(sourceLength, dstLimit - index);
        // handle ascii encoded strings in an optimised loop
        int c;
        while (index < dlASCII && (c = seq.charAt(sourceOffset)) < 128) {
            _setByte(index++, (byte) c);
            sourceOffset++;
        }

        try {
            while (sourceOffset < sourceLength) {
                c = seq.charAt(sourceOffset);
                if (c < 128) {
                    if (index >= length) {
                        CoderResult.OVERFLOW.throwException();
                    }
                    _setByte(index++, (byte) c);
                } else if (c < 2048) {
                    if (length - index < 2) {
                        CoderResult.OVERFLOW.throwException();
                    }
                    _setByte(index++, (byte) (0xC0 | (c >> 6)));
                    _setByte(index++, (byte) (0x80 | (c & 0x3F)));
                } else if (isSurrogate(c)) {
                    int uc = parseUtf8((char) c, seq, sourceOffset, sourceLength);
                    if (uc == -1) {
                        return index - startIndex;
                    }
                    if (length - index < 4) {
                        CoderResult.OVERFLOW.throwException();
                    }
                    _setByte(index++, (byte) (0xF0 | uc >> 18));
                    _setByte(index++, (byte) (0x80 | uc >> 12 & 0x3F));
                    _setByte(index++, (byte) (0x80 | uc >> 6 & 0x3F));
                    _setByte(index++, (byte) (0x80 | uc & 0x3F));
                    sourceOffset++;
                } else {
                    if (length - index < 3) {
                        CoderResult.OVERFLOW.throwException();
                    }
                    _setByte(index++, (byte) (0xE0 | c >> 12));
                    _setByte(index++, (byte) (0x80 | c >> 6 & 0x3F));
                    _setByte(index++, (byte) (0x80 | c & 0x3F));
                }
                sourceOffset++;
            }
            return index - startIndex;
        } catch (CharacterCodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int parseUtf8(char c, CharSequence seq, int offset, int length) {
        if (Character.isHighSurrogate(c)) {
            if (length - offset < 2) {
                return -1;
            }
            char c2 = seq.charAt(offset + 1);
            if (Character.isLowSurrogate(c2)) {
                return Character.toCodePoint(c, c2);
            }
            // replace char
            return REPLACEMENT;
        }
        if (Character.isLowSurrogate(c)) {
            // replace char
            return REPLACEMENT;
        }
        return c;
    }

    private static boolean isSurrogate(int c) {
        return Character.MIN_SURROGATE <= c && c <= Character.MAX_SURROGATE;
    }
}
