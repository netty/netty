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

import sun.misc.Cleaner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * A NIO {@link ByteBuffer} based buffer.  It is recommended to use {@link Unpooled#directBuffer(int)}
 * and {@link Unpooled#wrappedBuffer(ByteBuffer)} instead of calling the
 * constructor explicitly.
 */
@SuppressWarnings("restriction")
public class DirectByteBuf extends AbstractByteBuf {

    private static final Field CLEANER_FIELD;

    static {
        ByteBuffer direct = ByteBuffer.allocateDirect(1);
        Field cleanerField;
        try {
            cleanerField = direct.getClass().getDeclaredField("cleaner");
            cleanerField.setAccessible(true);
            Cleaner cleaner = (Cleaner) cleanerField.get(direct);
            cleaner.clean();
        } catch (Throwable t) {
            cleanerField = null;
        }
        CLEANER_FIELD = cleanerField;
    }

    private static void freeDirect(ByteBuffer buffer) {
        Cleaner cleaner;
        try {
            cleaner = (Cleaner) CLEANER_FIELD.get(buffer);
            cleaner.clean();
        } catch (Throwable t) {
            // Nothing we can do here.
        }
    }

    private final Unsafe unsafe = new DirectUnsafe();

    private boolean doNotFree;
    private ByteBuffer buffer;
    private ByteBuffer tmpBuf;
    private int capacity;

    /**
     * Creates a new direct buffer.
     *
     * @param initialCapacity the initial capacity of the underlying direct buffer
     * @param maxCapacity     the maximum capacity of the underlying direct buffer
     */
    public DirectByteBuf(int initialCapacity, int maxCapacity) {
        super(ByteOrder.BIG_ENDIAN, maxCapacity);
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity);
        }
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity: " + maxCapacity);
        }
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        setByteBuffer(ByteBuffer.allocateDirect(initialCapacity));
    }

    /**
     * Creates a new direct buffer by wrapping the specified initial buffer.
     *
     * @param maxCapacity the maximum capacity of the underlying direct buffer
     */
    public DirectByteBuf(ByteBuffer initialBuffer, int maxCapacity) {
        super(ByteOrder.BIG_ENDIAN, maxCapacity);

        if (initialBuffer == null) {
            throw new NullPointerException("initialBuffer");
        }
        if (!initialBuffer.isDirect()) {
            throw new IllegalArgumentException("initialBuffer is not a direct buffer.");
        }
        if (initialBuffer.isReadOnly()) {
            throw new IllegalArgumentException("initialBuffer is a read-only buffer.");
        }

        int initialCapacity = initialBuffer.remaining();
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        doNotFree = true;
        setByteBuffer(initialBuffer.slice().order(ByteOrder.BIG_ENDIAN));
        writerIndex(initialCapacity);
    }

    private void setByteBuffer(ByteBuffer buffer) {
        ByteBuffer oldBuffer = this.buffer;
        if (oldBuffer != null) {
            if (doNotFree) {
                doNotFree = false;
            } else {
                freeDirect(oldBuffer);
            }
        }

        this.buffer = buffer;
        tmpBuf = buffer.duplicate();
        capacity = buffer.remaining();
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }

        int readerIndex = readerIndex();
        int writerIndex = writerIndex();

        int oldCapacity = capacity;
        if (newCapacity > oldCapacity) {
            ByteBuffer oldBuffer = buffer;
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
            oldBuffer.position(readerIndex).limit(writerIndex);
            newBuffer.position(readerIndex).limit(writerIndex);
            newBuffer.put(oldBuffer);
            newBuffer.clear();
            setByteBuffer(newBuffer);
        } else if (newCapacity < oldCapacity) {
            ByteBuffer oldBuffer = buffer;
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
            if (readerIndex < newCapacity) {
                if (writerIndex > newCapacity) {
                    writerIndex(writerIndex = newCapacity);
                }
                oldBuffer.position(readerIndex).limit(writerIndex);
                newBuffer.position(readerIndex).limit(writerIndex);
                newBuffer.put(oldBuffer);
                newBuffer.clear();
            } else {
                setIndex(newCapacity, newCapacity);
            }
            setByteBuffer(newBuffer);
        }
        return this;
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
    public byte getByte(int index) {
        return buffer.get(index);
    }

    @Override
    public short getShort(int index) {
        return buffer.getShort(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return (getByte(index) & 0xff) << 16 | (getByte(index + 1) & 0xff) << 8 | getByte(index + 2) & 0xff;
    }

    @Override
    public int getInt(int index) {
        return buffer.getInt(index);
    }

    @Override
    public long getLong(int index) {
        return buffer.getLong(index);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        if (dst instanceof DirectByteBuf) {
            DirectByteBuf bbdst = (DirectByteBuf) dst;
            ByteBuffer data = bbdst.tmpBuf;
            data.clear().position(dstIndex).limit(dstIndex + length);
            getBytes(index, data);
        } else if (buffer.hasArray()) {
            dst.setBytes(dstIndex, buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        try {
            tmpBuf.clear().position(index).limit(index + length);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need " +
                    (index + length) + ", maximum is " + buffer.limit());
        }
        tmpBuf.get(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        try {
            tmpBuf.clear().position(index).limit(index + bytesToCopy);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need " +
                    (index + bytesToCopy) + ", maximum is " + buffer.limit());
        }
        dst.put(tmpBuf);
        return this;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        buffer.put(index, (byte) value);
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        buffer.putShort(index, (short) value);
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        setByte(index, (byte) (value >>> 16));
        setByte(index + 1, (byte) (value >>> 8));
        setByte(index + 2, (byte) value);
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        buffer.putInt(index, value);
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        buffer.putLong(index, value);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        if (src instanceof DirectByteBuf) {
            DirectByteBuf bbsrc = (DirectByteBuf) src;
            ByteBuffer data = bbsrc.tmpBuf;

            data.clear().position(srcIndex).limit(srcIndex + length);
            setBytes(index, data);
        } else if (buffer.hasArray()) {
            src.getBytes(srcIndex, buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            src.getBytes(srcIndex, this, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.put(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        if (src == tmpBuf) {
            src = src.duplicate();
        }

        tmpBuf.clear().position(index).limit(index + src.remaining());
        tmpBuf.put(src);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        if (length == 0) {
            return this;
        }

        if (buffer.hasArray()) {
            out.write(buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            byte[] tmp = new byte[length];
            tmpBuf.clear().position(index);
            tmpBuf.get(tmp);
            out.write(tmp);
        }
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        if (length == 0) {
            return 0;
        }

        tmpBuf.clear().position(index).limit(index + length);
        return out.write(tmpBuf);
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {

        if (buffer.hasArray()) {
            return in.read(buffer.array(), buffer.arrayOffset() + index, length);
        } else {
            byte[] tmp = new byte[length];
            int readBytes = in.read(tmp);
            tmpBuf.clear().position(index);
            tmpBuf.put(tmp);
            return readBytes;
        }
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        tmpBuf.clear().position(index).limit(index + length);
        try {
            return in.read(tmpBuf);
        } catch (ClosedChannelException e) {
            return -1;
        }
    }

    @Override
    public boolean hasNioBuffer() {
        return true;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        if (index == 0 && length == capacity()) {
            return buffer.duplicate();
        } else {
            return ((ByteBuffer) tmpBuf.clear().position(index).limit(index + length)).slice();
        }
    }

    @Override
    public boolean hasNioBuffers() {
        return false;
    }

    @Override
    public ByteBuffer[] nioBuffers(int offset, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        ByteBuffer src;
        try {
            src = (ByteBuffer) tmpBuf.clear().position(index).limit(index + length);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need " + (index + length));
        }

        ByteBuffer dst =
                src.isDirect()? ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
        dst.put(src);
        dst.order(order());
        dst.clear();
        return new DirectByteBuf(dst, maxCapacity());
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    private class DirectUnsafe implements Unsafe {
        @Override
        public ByteBuffer nioBuffer() {
            return tmpBuf;
        }

        @Override
        public ByteBuffer[] nioBuffers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf newBuffer(int initialCapacity) {
            return new DirectByteBuf(initialCapacity, Math.max(initialCapacity, maxCapacity()));
        }

        @Override
        public void discardSomeReadBytes() {
            final int readerIndex = readerIndex();
            if (readerIndex == writerIndex()) {
                discardReadBytes();
                return;
            }

            if (readerIndex > 0 && readerIndex >= capacity >>> 1) {
                discardReadBytes();
            }
        }

        @Override
        public void acquire() {
            if (refCnt <= 0) {
                throw new IllegalStateException();
            }
            refCnt ++;
        }

        @Override
        public void release() {
            if (refCnt <= 0) {
                throw new IllegalStateException();
            }
            refCnt --;
            if (refCnt == 0) {
                if (doNotFree) {
                    doNotFree = false;
                } else {
                    freeDirect(buffer);
                }

                buffer = null;
                tmpBuf = null;
            }
        }
    }
}
