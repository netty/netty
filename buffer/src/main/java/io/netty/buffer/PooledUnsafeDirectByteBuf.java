/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.Recycler;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

final class PooledUnsafeDirectByteBuf extends PooledByteBuf<ByteBuffer> {

    private static final Recycler<PooledUnsafeDirectByteBuf> RECYCLER = new Recycler<PooledUnsafeDirectByteBuf>() {
        @Override
        protected PooledUnsafeDirectByteBuf newObject(Handle handle) {
            return new PooledUnsafeDirectByteBuf(handle, 0);
        }
    };

    static PooledUnsafeDirectByteBuf newInstance(int maxCapacity) {
        PooledUnsafeDirectByteBuf buf = RECYCLER.get();
        buf.setRefCnt(1);
        buf.maxCapacity(maxCapacity);
        return buf;
    }

    private long memoryAddress;

    private PooledUnsafeDirectByteBuf(Recycler.Handle recyclerHandle, int maxCapacity) {
        super(recyclerHandle, maxCapacity);
    }

    @Override
    void init(PoolChunk<ByteBuffer> chunk, long handle, int offset, int length, int maxLength) {
        super.init(chunk, handle, offset, length, maxLength);
        initMemoryAddress();
    }

    @Override
    void initUnpooled(PoolChunk<ByteBuffer> chunk, int length) {
        super.initUnpooled(chunk, length);
        initMemoryAddress();
    }

    private void initMemoryAddress() {
        memoryAddress = PlatformDependent.directBufferAddress(memory) + offset;
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(ByteBuffer memory) {
        return memory.duplicate();
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    protected byte _getByte(int index) {
        return UnsafeDirectByteBufUtil._getByte(this, index);
    }

    @Override
    protected short _getShort(int index) {
        return UnsafeDirectByteBufUtil._getShort(this, index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return UnsafeDirectByteBufUtil._getUnsignedMedium(this, index);
    }

    @Override
    protected int _getInt(int index) {
        return UnsafeDirectByteBufUtil._getInt(this, index);
    }

    @Override
    protected long _getLong(int index) {
        return UnsafeDirectByteBufUtil._getLong(this, index);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        UnsafeDirectByteBufUtil.getBytes(this, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        UnsafeDirectByteBufUtil.getBytes(this, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        DirectByteBufUtil.getBytes(this, memory, index, dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        DirectByteBufUtil.readBytes(this, memory, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        UnsafeDirectByteBufUtil.getBytes(this, index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return DirectByteBufUtil.getBytes(this, memory, index, out, length);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length)
            throws IOException {
        return DirectByteBufUtil.readBytes(this, memory, out, length);
    }

    @Override
    protected void _setByte(int index, int value) {
        UnsafeDirectByteBufUtil._setByte(this, index, value);
    }

    @Override
    protected void _setShort(int index, int value) {
        UnsafeDirectByteBufUtil._setShort(this, index, value);
    }

    @Override
    protected void _setMedium(int index, int value) {
        UnsafeDirectByteBufUtil._setMedium(this, index, value);
    }

    @Override
    protected void _setInt(int index, int value) {
        UnsafeDirectByteBufUtil._setInt(this, index, value);
    }

    @Override
    protected void _setLong(int index, long value) {
        UnsafeDirectByteBufUtil._setLong(this, index, value);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        UnsafeDirectByteBufUtil.setBytes(this, index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        UnsafeDirectByteBufUtil.setBytes(this, index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        DirectByteBufUtil.setBytes(this, index, src);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return UnsafeDirectByteBufUtil.setBytes(this, index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return DirectByteBufUtil.setBytes(this, index, in, length);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return UnsafeDirectByteBufUtil.copy(this, index, length);
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return DirectByteBufUtil.nioBuffer(this, memory, index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return DirectByteBufUtil.internalNioBuffer(this, index, length);
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
    public boolean hasMemoryAddress() {
        return true;
    }

    @Override
    public long memoryAddress() {
        return memoryAddress;
    }

    @Override
    protected Recycler<?> recycler() {
        return RECYCLER;
    }

    @Override
    protected SwappedByteBuf newSwappedByteBuf() {
        return new UnsafeDirectSwappedByteBuf(this);
    }
}
