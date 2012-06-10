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
 * A derived buffer which simply forwards all data access requests to its
 * parent.  It is recommended to use {@link ByteBuf#duplicate()} instead
 * of calling the constructor explicitly.
 */
public class DuplicatedByteBuf extends AbstractByteBuf implements WrappedByteBuf {

    private final ByteBuf buffer;

    public DuplicatedByteBuf(ByteBuf buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    private DuplicatedByteBuf(DuplicatedByteBuf buffer) {
        this.buffer = buffer.buffer;
        setIndex(buffer.readerIndex(), buffer.writerIndex());
    }

    @Override
    public ByteBuf unwrap() {
        return buffer;
    }

    @Override
    public ByteBufFactory factory() {
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
        return buffer.capacity();
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
    public ByteBuf duplicate() {
        return new DuplicatedByteBuf(this);
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
    public void getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public void getBytes(int index, ByteBuffer dst) {
        buffer.getBytes(index, dst);
    }

    @Override
    public void setByte(int index, int value) {
        buffer.setByte(index, value);
    }

    @Override
    public void setShort(int index, int value) {
        buffer.setShort(index, value);
    }

    @Override
    public void setMedium(int index, int value) {
        buffer.setMedium(index, value);
    }

    @Override
    public void setInt(int index, int value) {
        buffer.setInt(index, value);
    }

    @Override
    public void setLong(int index, long value) {
        buffer.setLong(index, value);
    }

    @Override
    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, ByteBuf src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
    }

    @Override
    public void setBytes(int index, ByteBuffer src) {
        buffer.setBytes(index, src);
    }

    @Override
    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        buffer.getBytes(index, out, length);
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
}
