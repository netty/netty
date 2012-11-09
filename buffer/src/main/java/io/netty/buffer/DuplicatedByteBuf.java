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
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;


/**
 * A derived buffer which simply forwards all data access requests to its
 * parent.  It is recommended to use {@link ByteBuf#duplicate()} instead
 * of calling the constructor explicitly.
 */
public class DuplicatedByteBuf extends AbstractWrappedByteBuf {

    private final Unsafe unsafe = new DuplicatedUnsafe();
    final ByteBuf buffer;

    public DuplicatedByteBuf(ByteBuf buffer) {
        super(buffer.order(), buffer.maxCapacity());

        if (buffer instanceof DuplicatedByteBuf) {
            this.buffer = ((DuplicatedByteBuf) buffer).buffer;
        } else {
            this.buffer = buffer;
        }

        setIndex(buffer.readerIndex(), buffer.writerIndex());

        buffer.unsafe().acquire();
    }

    @Override
    public ByteBuf unwrap() {
        return buffer;
    }

    @Override
    public boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
    public int capacity() {
        return buffer.capacity();
    }

    @Override
    public WrappedByteBuf capacity(int newCapacity) {
        buffer.capacity(newCapacity);
        return this;
    }

    @Override
    public boolean hasArray() {
        return buffer.hasArray();
    }

    @Override
    public byte[] array() {
        return buffer.array();
    }

    @Override
    public int arrayOffset() {
        return buffer.arrayOffset();
    }

    @Override
    public byte getByte(int index) {
        return buffer.getByte(index);
    }

    @Override
    public short getShort(int index) {
        return buffer.getShort(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return buffer.getUnsignedMedium(index);
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
    public ByteBuf copy(int index, int length) {
        return buffer.copy(index, length);
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return buffer.slice(index, length);
    }

    @Override
    public WrappedByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public WrappedByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public WrappedByteBuf getBytes(int index, ByteBuffer dst) {
        buffer.getBytes(index, dst);
        return this;
    }

    @Override
    public WrappedByteBuf setByte(int index, int value) {
        buffer.setByte(index, value);
        return this;
    }

    @Override
    public WrappedByteBuf setShort(int index, int value) {
        buffer.setShort(index, value);
        return this;
    }

    @Override
    public WrappedByteBuf setMedium(int index, int value) {
        buffer.setMedium(index, value);
        return this;
    }

    @Override
    public WrappedByteBuf setInt(int index, int value) {
        buffer.setInt(index, value);
        return this;
    }

    @Override
    public WrappedByteBuf setLong(int index, long value) {
        buffer.setLong(index, value);
        return this;
    }

    @Override
    public WrappedByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public WrappedByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public WrappedByteBuf setBytes(int index, ByteBuffer src) {
        buffer.setBytes(index, src);
        return this;
    }

    @Override
    public WrappedByteBuf getBytes(int index, OutputStream out, int length)
            throws IOException {
        buffer.getBytes(index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return buffer.getBytes(index, out, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        return buffer.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        return buffer.setBytes(index, in, length);
    }

    @Override
    public boolean hasNioBuffer() {
        return buffer.hasNioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return buffer.nioBuffer(index, length);
    }

    @Override
    public boolean hasNioBuffers() {
        return buffer.hasNioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int offset, int length) {
        return buffer.nioBuffers(offset, length);
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    private final class DuplicatedUnsafe implements Unsafe {

        @Override
        public ByteBuffer nioBuffer() {
            return buffer.unsafe().nioBuffer();
        }

        @Override
        public ByteBuffer[] nioBuffers() {
            return buffer.unsafe().nioBuffers();
        }

        @Override
        public ByteBuf newBuffer(int initialCapacity) {
            return buffer.unsafe().newBuffer(initialCapacity);
        }

        @Override
        public void discardSomeReadBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void acquire() {
            buffer.unsafe().acquire();
        }

        @Override
        public void release() {
            buffer.unsafe().release();
        }
    }
}
