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
package org.jboss.netty.buffer;

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
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2206 $, $Date: 2010-03-03 14:35:01 +0900 (Wed, 03 Mar 2010) $
 *
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

    public ChannelBuffer unwrap() {
        return buffer;
    }

    public ChannelBufferFactory factory() {
        return buffer.factory();
    }

    public ByteOrder order() {
        return buffer.order();
    }

    public boolean isDirect() {
        return buffer.isDirect();
    }

    public int capacity() {
        return length;
    }

    public boolean hasArray() {
        return buffer.hasArray();
    }

    public byte[] array() {
        return buffer.array();
    }

    public int arrayOffset() {
        return buffer.arrayOffset() + adjustment;
    }

    public byte getByte(int index) {
        checkIndex(index);
        return buffer.getByte(index + adjustment);
    }

    public short getShort(int index) {
        checkIndex(index, 2);
        return buffer.getShort(index + adjustment);
    }

    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return buffer.getUnsignedMedium(index + adjustment);
    }

    public int getInt(int index) {
        checkIndex(index, 4);
        return buffer.getInt(index + adjustment);
    }

    public long getLong(int index) {
        checkIndex(index, 8);
        return buffer.getLong(index + adjustment);
    }

    public ChannelBuffer duplicate() {
        ChannelBuffer duplicate = new SlicedChannelBuffer(buffer, adjustment, length);
        duplicate.setIndex(readerIndex(), writerIndex());
        return duplicate;
    }

    public ChannelBuffer copy(int index, int length) {
        checkIndex(index, length);
        return buffer.copy(index + adjustment, length);
    }

    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
        if (length == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        return new SlicedChannelBuffer(buffer, index + adjustment, length);
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, dst, dstIndex, length);
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, dst, dstIndex, length);
    }

    public void getBytes(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        buffer.getBytes(index + adjustment, dst);
    }

    public void setByte(int index, int value) {
        checkIndex(index);
        buffer.setByte(index + adjustment, value);
    }

    public void setShort(int index, int value) {
        checkIndex(index, 2);
        buffer.setShort(index + adjustment, value);
    }

    public void setMedium(int index, int value) {
        checkIndex(index, 3);
        buffer.setMedium(index + adjustment, value);
    }

    public void setInt(int index, int value) {
        checkIndex(index, 4);
        buffer.setInt(index + adjustment, value);
    }

    public void setLong(int index, long value) {
        checkIndex(index, 8);
        buffer.setLong(index + adjustment, value);
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index + adjustment, src, srcIndex, length);
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index + adjustment, src, srcIndex, length);
    }

    public void setBytes(int index, ByteBuffer src) {
        checkIndex(index, src.remaining());
        buffer.setBytes(index + adjustment, src);
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        checkIndex(index, length);
        buffer.getBytes(index + adjustment, out, length);
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.getBytes(index + adjustment, out, length);
    }

    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index + adjustment, in, length);
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index + adjustment, in, length);
    }

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
