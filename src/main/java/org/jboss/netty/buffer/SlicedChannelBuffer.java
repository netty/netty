/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
 * Derived buffer which exposes its parent's sub-region only.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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

    public ByteOrder order() {
        return buffer.order();
    }

    public int capacity() {
        return length;
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
        return buffer.copy(index + adjustment, length);
    }

    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
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

    public void setByte(int index, byte value) {
        checkIndex(index);
        buffer.setByte(index + adjustment, value);
    }

    public void setShort(int index, short value) {
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

    public String toString(int index, int length, String charsetName) {
        checkIndex(index, length);
        return buffer.toString(index + adjustment, length, charsetName);
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
