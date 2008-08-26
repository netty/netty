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
 * Derived buffer which hides its parent's tail data beyond a certain index.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class TruncatedChannelBuffer extends AbstractChannelBuffer implements WrappedChannelBuffer {

    private final ChannelBuffer buffer;
    private final int length;

    public TruncatedChannelBuffer(ChannelBuffer buffer, int length) {
        if (length > buffer.capacity()) {
            throw new IndexOutOfBoundsException();
        }

        this.buffer = buffer;
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
        return buffer.getByte(index);
    }

    public short getShort(int index) {
        checkIndex(index, 2);
        return buffer.getShort(index);
    }

    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return buffer.getUnsignedMedium(index);
    }

    public int getInt(int index) {
        checkIndex(index, 4);
        return buffer.getInt(index);
    }

    public long getLong(int index) {
        checkIndex(index, 8);
        return buffer.getLong(index);
    }

    public ChannelBuffer duplicate() {
        ChannelBuffer duplicate = new TruncatedChannelBuffer(buffer, length);
        duplicate.setIndex(readerIndex(), writerIndex());
        return duplicate;
    }

    public ChannelBuffer copy(int index, int length) {
        return buffer.copy(index, length);
    }

    public ChannelBuffer slice(int index, int length) {
        checkIndex(index, length);
        return buffer.slice(index, length);
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        buffer.getBytes(index, dst);
    }

    public void setByte(int index, byte value) {
        checkIndex(index);
        buffer.setByte(index, value);
    }

    public void setShort(int index, short value) {
        checkIndex(index, 2);
        buffer.setShort(index, value);
    }

    public void setMedium(int index, int value) {
        checkIndex(index, 3);
        buffer.setMedium(index, value);
    }

    public void setInt(int index, int value) {
        checkIndex(index, 4);
        buffer.setInt(index, value);
    }

    public void setLong(int index, long value) {
        checkIndex(index, 8);
        buffer.setLong(index, value);
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index, src, srcIndex, length);
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        checkIndex(index, length);
        buffer.setBytes(index, src, srcIndex, length);
    }

    public void setBytes(int index, ByteBuffer src) {
        checkIndex(index, src.remaining());
        buffer.setBytes(index, src);
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        checkIndex(index, length);
        buffer.getBytes(index, out, length);
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.getBytes(index, out, length);
    }

    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index, in, length);
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        checkIndex(index, length);
        return buffer.setBytes(index, in, length);
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        checkIndex(index, length);
        return buffer.toByteBuffer(index, length);
    }

    public String toString(int index, int length, String charsetName) {
        checkIndex(index, length);
        return buffer.toString(index, length, charsetName);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= capacity()) {
            throw new IndexOutOfBoundsException();
        }
    }

    private void checkIndex(int index, int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length is negative: " + length);
        }
        if (index + length > capacity()) {
            throw new IndexOutOfBoundsException();
        }
    }
}
