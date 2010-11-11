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
import java.nio.ReadOnlyBufferException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * A derived buffer which forbids any write requests to its parent.  It is
 * recommended to use {@link ChannelBuffers#unmodifiableBuffer(ChannelBuffer)}
 * instead of calling the constructor explicitly.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2206 $, $Date: 2010-03-03 14:35:01 +0900 (Wed, 03 Mar 2010) $
 *
 */
public class ReadOnlyChannelBuffer extends AbstractChannelBuffer implements WrappedChannelBuffer {

    private final ChannelBuffer buffer;

    public ReadOnlyChannelBuffer(ChannelBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    private ReadOnlyChannelBuffer(ReadOnlyChannelBuffer buffer) {
        this.buffer = buffer.buffer;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
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

    public boolean hasArray() {
        return false;
    }

    public byte[] array() {
        throw new ReadOnlyBufferException();
    }

    public int arrayOffset() {
        throw new ReadOnlyBufferException();
    }

    @Override
    public void discardReadBytes() {
        throw new ReadOnlyBufferException();
    }

    public void setByte(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    public void setBytes(int index, ByteBuffer src) {
        throw new ReadOnlyBufferException();
    }

    public void setShort(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    public void setMedium(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    public void setInt(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    public void setLong(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    public int setBytes(int index, InputStream in, int length)
            throws IOException {
        throw new ReadOnlyBufferException();
    }

    public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {
        throw new ReadOnlyBufferException();
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return buffer.getBytes(index, out, length);
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        buffer.getBytes(index, out, length);
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
    }

    public void getBytes(int index, ByteBuffer dst) {
        buffer.getBytes(index, dst);
    }

    public ChannelBuffer duplicate() {
        return new ReadOnlyChannelBuffer(this);
    }

    public ChannelBuffer copy(int index, int length) {
        return buffer.copy(index, length);
    }

    public ChannelBuffer slice(int index, int length) {
        return new ReadOnlyChannelBuffer(buffer.slice(index, length));
    }

    public byte getByte(int index) {
        return buffer.getByte(index);
    }

    public short getShort(int index) {
        return buffer.getShort(index);
    }

    public int getUnsignedMedium(int index) {
        return buffer.getUnsignedMedium(index);
    }

    public int getInt(int index) {
        return buffer.getInt(index);
    }

    public long getLong(int index) {
        return buffer.getLong(index);
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        return buffer.toByteBuffer(index, length).asReadOnlyBuffer();
    }

    @Override
    public ByteBuffer[] toByteBuffers(int index, int length) {
        ByteBuffer[] bufs = buffer.toByteBuffers(index, length);
        for (int i = 0; i < bufs.length; i ++) {
            bufs[i] = bufs[i].asReadOnlyBuffer();
        }
        return bufs;
    }

    public int capacity() {
        return buffer.capacity();
    }
}
