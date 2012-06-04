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
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;


/**
 * A derived buffer which hides its parent's tail data beyond a certain index.
 * It is recommended to use {@link ChannelBuffer#slice()} and
 * {@link ChannelBuffer#slice(int, int)} instead of calling the constructor
 * explicitly.
 */
public class TruncatedChannelBuffer extends AbstractChannelBuffer implements WrappedChannelBuffer {

    private final ChannelBuffer buffer;
    private final int length;

    public TruncatedChannelBuffer(ChannelBuffer buffer, int length) {
        if (length > buffer.capacity()) {
            throw new IndexOutOfBoundsException("Length is too large, got "
                    + length + " but can't go higher than " + buffer.capacity());
        }

        this.buffer = buffer;
        this.length = length;
        writerIndex(length);
    }

    @Override
    public ChannelBuffer unwrap() {
        return buffer;
    }

    @Override
    public ChannelBufferFactory factory() {
        return buffer.factory();
    }

    @Override
    public ByteOrder order() {
        return buffer.order();
    }

    @Override
    public boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
    public int capacity() {
        return length;
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
        checkIndex(index);
        return buffer.getByte(index);
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return buffer.getShort(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return buffer.getUnsignedMedium(index);
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return buffer.getInt(index);
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return buffer.getLong(index);
    }

    @Override
    public ChannelBuffer duplicate() {
        ChannelBuffer duplicate = new TruncatedChannelBuffer(buffer, length);
        duplicate.setIndex(readerIndex(), writerIndex());
        return duplicate;
    }

    @Override
    public ChannelBuffer copy(int index, int length) {
        checkIndex(index, length);
        return buffer.copy(index, length);
    }

    @Override
    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
        if (length == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        return buffer.slice(index, length);
    }

    @Override
    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        buffer.getBytes(index, dst);
    }

    @Override
    public void setByte(int index, int value) {
        checkIndex(index);
        buffer.setByte(index, value);
    }

    @Override
    public void setShort(int index, int value) {
        checkIndex(index, 2);
        buffer.setShort(index, value);
    }

    @Override
    public void setMedium(int index, int value) {
        checkIndex(index, 3);
        buffer.setMedium(index, value);
    }

    @Override
    public void setInt(int index, int value) {
        checkIndex(index, 4);
        buffer.setInt(index, value);
    }

    @Override
    public void setLong(int index, long value) {
        checkIndex(index, 8);
        buffer.setLong(index, value);
    }

    @Override
    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index, src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index, src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, ByteBuffer src) {
        checkIndex(index, src.remaining());
        buffer.setBytes(index, src);
    }

    @Override
    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        checkIndex(index, length);
        buffer.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.getBytes(index, out, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index, in, length);
    }

    @Override
    public boolean hasNioBuffer() {
        return buffer.hasNioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        return buffer.nioBuffer(index, length);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= capacity()) {
            throw new IndexOutOfBoundsException("Invalid index of " + index
                    + ", maximum is " + capacity());
        }
    }

    private void checkIndex(int index, int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length is negative: " + length);
        }
        if (index + length > capacity()) {
            throw new IndexOutOfBoundsException("Invalid index of "
                    + (index + length) + ", maximum is " + capacity());
        }
    }
}
