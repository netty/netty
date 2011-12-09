/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
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
 * A derived buffer which exposes its parent's sub-region only.  It is
 * recommended to use {@link ChannelBuffer#slice()} and
 * {@link ChannelBuffer#slice(int, int)} instead of calling the constructor
 * explicitly.
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
public class SlicedChannelBuffer extends AbstractChannelBuffer implements WrappedChannelBuffer {

    private final ChannelBuffer buffer;
    private final int adjustment;
    private final int length;

    public SlicedChannelBuffer(ChannelBuffer buffer, int index, int length) {
        if (index < 0 || index > buffer.capacity()) {
            throw new IndexOutOfBoundsException();
        }

        if (index + length > buffer.capacity()) {
            throw new IndexOutOfBoundsException();
        }

        this.buffer = buffer;
        adjustment = index;
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
        return buffer.arrayOffset() + adjustment;
    }

    @Override
    public byte getByte(int index) {
        checkIndex(index);
        return buffer.getByte(index + adjustment);
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return buffer.getShort(index + adjustment);
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return buffer.getUnsignedMedium(index + adjustment);
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return buffer.getInt(index + adjustment);
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return buffer.getLong(index + adjustment);
    }

    @Override
    public ChannelBuffer duplicate() {
        ChannelBuffer duplicate = new SlicedChannelBuffer(buffer, adjustment, length);
        duplicate.setIndex(readerIndex(), writerIndex());
        return duplicate;
    }

    @Override
    public ChannelBuffer copy(int index, int length) {
        checkIndex(index, length);
        return buffer.copy(index + adjustment, length);
    }

    @Override
    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
        if (length == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        return new SlicedChannelBuffer(buffer, index + adjustment, length);
    }

    @Override
    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        buffer.getBytes(index + adjustment, dst);
    }

    @Override
    public void setByte(int index, int value) {
        checkIndex(index);
        buffer.setByte(index + adjustment, value);
    }

    @Override
    public void setShort(int index, int value) {
        checkIndex(index, 2);
        buffer.setShort(index + adjustment, value);
    }

    @Override
    public void setMedium(int index, int value) {
        checkIndex(index, 3);
        buffer.setMedium(index + adjustment, value);
    }

    @Override
    public void setInt(int index, int value) {
        checkIndex(index, 4);
        buffer.setInt(index + adjustment, value);
    }

    @Override
    public void setLong(int index, long value) {
        checkIndex(index, 8);
        buffer.setLong(index + adjustment, value);
    }

    @Override
    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index + adjustment, src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index + adjustment, src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, ByteBuffer src) {
        checkIndex(index, src.remaining());
        buffer.setBytes(index + adjustment, src);
    }

    @Override
    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, out, length);
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.getBytes(index + adjustment, out, length);
    }

    @Override
    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index + adjustment, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index + adjustment, in, length);
    }

    @Override
    public ByteBuffer toByteBuffer(int index, int length) {
        checkIndex(index, length);
        return buffer.toByteBuffer(index + adjustment, length);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= capacity()) {
            throw new IndexOutOfBoundsException();
        }
    }

    private void checkIndex(int startIndex, int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length is negative: " + length);
        }
        if (startIndex < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (startIndex + length > capacity()) {
            throw new IndexOutOfBoundsException();
        }
    }
}
