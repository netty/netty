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

import io.netty.util.ResourceLeak;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

final class AdvancedLeakAwareByteBuf extends WrappedByteBuf {

    private static final String PROP_ACQUIRE_AND_RELEASE_ONLY = "io.netty.leakDetection.acquireAndReleaseOnly";
    private static final boolean ACQUIRE_AND_RELEASE_ONLY;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AdvancedLeakAwareByteBuf.class);

    static {
        ACQUIRE_AND_RELEASE_ONLY = SystemPropertyUtil.getBoolean(PROP_ACQUIRE_AND_RELEASE_ONLY, false);

        if (logger.isDebugEnabled()) {
            logger.debug("-D{}: {}", PROP_ACQUIRE_AND_RELEASE_ONLY, ACQUIRE_AND_RELEASE_ONLY);
        }
    }

    private final ResourceLeak leak;

    AdvancedLeakAwareByteBuf(ByteBuf buf, ResourceLeak leak) {
        super(buf);
        this.leak = leak;
    }

    @Override
    public boolean release() {
        boolean deallocated =  super.release();
        if (deallocated) {
            leak.close();
        } else {
            leak.record();
        }
        return deallocated;
    }

    @Override
    public boolean release(int decrement) {
        boolean deallocated = super.release(decrement);
        if (deallocated) {
            leak.close();
        } else {
            leak.record();
        }
        return deallocated;
    }

    private void recordLeakNonRefCountingOperation() {
        if (!ACQUIRE_AND_RELEASE_ONLY) {
            leak.record();
        }
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        recordLeakNonRefCountingOperation();
        if (order() == endianness) {
            return this;
        } else {
            return new AdvancedLeakAwareByteBuf(super.order(endianness), leak);
        }
    }

    @Override
    public ByteBuf slice() {
        recordLeakNonRefCountingOperation();
        return new AdvancedLeakAwareByteBuf(super.slice(), leak);
    }

    @Override
    public ByteBuf slice(int index, int length) {
        recordLeakNonRefCountingOperation();
        return new AdvancedLeakAwareByteBuf(super.slice(index, length), leak);
    }

    @Override
    public ByteBuf duplicate() {
        recordLeakNonRefCountingOperation();
        return new AdvancedLeakAwareByteBuf(super.duplicate(), leak);
    }

    @Override
    public ByteBuf readSlice(int length) {
        recordLeakNonRefCountingOperation();
        return new AdvancedLeakAwareByteBuf(super.readSlice(length), leak);
    }

    @Override
    public ByteBuf discardReadBytes() {
        recordLeakNonRefCountingOperation();
        return super.discardReadBytes();
    }

    @Override
    public ByteBuf discardSomeReadBytes() {
        recordLeakNonRefCountingOperation();
        return super.discardSomeReadBytes();
    }

    @Override
    public ByteBuf ensureWritable(int minWritableBytes) {
        recordLeakNonRefCountingOperation();
        return super.ensureWritable(minWritableBytes);
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        recordLeakNonRefCountingOperation();
        return super.ensureWritable(minWritableBytes, force);
    }

    @Override
    public boolean getBoolean(int index) {
        recordLeakNonRefCountingOperation();
        return super.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        recordLeakNonRefCountingOperation();
        return super.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        recordLeakNonRefCountingOperation();
        return super.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        recordLeakNonRefCountingOperation();
        return super.getShort(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        recordLeakNonRefCountingOperation();
        return super.getUnsignedShort(index);
    }

    @Override
    public int getMedium(int index) {
        recordLeakNonRefCountingOperation();
        return super.getMedium(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        recordLeakNonRefCountingOperation();
        return super.getUnsignedMedium(index);
    }

    @Override
    public int getInt(int index) {
        recordLeakNonRefCountingOperation();
        return super.getInt(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        recordLeakNonRefCountingOperation();
        return super.getUnsignedInt(index);
    }

    @Override
    public long getLong(int index) {
        recordLeakNonRefCountingOperation();
        return super.getLong(index);
    }

    @Override
    public char getChar(int index) {
        recordLeakNonRefCountingOperation();
        return super.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        recordLeakNonRefCountingOperation();
        return super.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        recordLeakNonRefCountingOperation();
        return super.getDouble(index);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        recordLeakNonRefCountingOperation();
        return super.getBytes(index, dst);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        recordLeakNonRefCountingOperation();
        return super.getBytes(index, dst, length);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        recordLeakNonRefCountingOperation();
        return super.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        recordLeakNonRefCountingOperation();
        return super.getBytes(index, dst);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        recordLeakNonRefCountingOperation();
        return super.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        recordLeakNonRefCountingOperation();
        return super.getBytes(index, dst);
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        recordLeakNonRefCountingOperation();
        return super.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        recordLeakNonRefCountingOperation();
        return super.getBytes(index, out, length);
    }

    @Override
    public ByteBuf setBoolean(int index, boolean value) {
        recordLeakNonRefCountingOperation();
        return super.setBoolean(index, value);
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        recordLeakNonRefCountingOperation();
        return super.setByte(index, value);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        recordLeakNonRefCountingOperation();
        return super.setShort(index, value);
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        recordLeakNonRefCountingOperation();
        return super.setMedium(index, value);
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        recordLeakNonRefCountingOperation();
        return super.setInt(index, value);
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        recordLeakNonRefCountingOperation();
        return super.setLong(index, value);
    }

    @Override
    public ByteBuf setChar(int index, int value) {
        recordLeakNonRefCountingOperation();
        return super.setChar(index, value);
    }

    @Override
    public ByteBuf setFloat(int index, float value) {
        recordLeakNonRefCountingOperation();
        return super.setFloat(index, value);
    }

    @Override
    public ByteBuf setDouble(int index, double value) {
        recordLeakNonRefCountingOperation();
        return super.setDouble(index, value);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        recordLeakNonRefCountingOperation();
        return super.setBytes(index, src);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        recordLeakNonRefCountingOperation();
        return super.setBytes(index, src, length);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        recordLeakNonRefCountingOperation();
        return super.setBytes(index, src, srcIndex, length);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        recordLeakNonRefCountingOperation();
        return super.setBytes(index, src);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        recordLeakNonRefCountingOperation();
        return super.setBytes(index, src, srcIndex, length);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        recordLeakNonRefCountingOperation();
        return super.setBytes(index, src);
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        recordLeakNonRefCountingOperation();
        return super.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        recordLeakNonRefCountingOperation();
        return super.setBytes(index, in, length);
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        recordLeakNonRefCountingOperation();
        return super.setZero(index, length);
    }

    @Override
    public boolean readBoolean() {
        recordLeakNonRefCountingOperation();
        return super.readBoolean();
    }

    @Override
    public byte readByte() {
        recordLeakNonRefCountingOperation();
        return super.readByte();
    }

    @Override
    public short readUnsignedByte() {
        recordLeakNonRefCountingOperation();
        return super.readUnsignedByte();
    }

    @Override
    public short readShort() {
        recordLeakNonRefCountingOperation();
        return super.readShort();
    }

    @Override
    public int readUnsignedShort() {
        recordLeakNonRefCountingOperation();
        return super.readUnsignedShort();
    }

    @Override
    public int readMedium() {
        recordLeakNonRefCountingOperation();
        return super.readMedium();
    }

    @Override
    public int readUnsignedMedium() {
        recordLeakNonRefCountingOperation();
        return super.readUnsignedMedium();
    }

    @Override
    public int readInt() {
        recordLeakNonRefCountingOperation();
        return super.readInt();
    }

    @Override
    public long readUnsignedInt() {
        recordLeakNonRefCountingOperation();
        return super.readUnsignedInt();
    }

    @Override
    public long readLong() {
        recordLeakNonRefCountingOperation();
        return super.readLong();
    }

    @Override
    public char readChar() {
        recordLeakNonRefCountingOperation();
        return super.readChar();
    }

    @Override
    public float readFloat() {
        recordLeakNonRefCountingOperation();
        return super.readFloat();
    }

    @Override
    public double readDouble() {
        recordLeakNonRefCountingOperation();
        return super.readDouble();
    }

    @Override
    public ByteBuf readBytes(int length) {
        recordLeakNonRefCountingOperation();
        return super.readBytes(length);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        recordLeakNonRefCountingOperation();
        return super.readBytes(dst);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        recordLeakNonRefCountingOperation();
        return super.readBytes(dst, length);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        recordLeakNonRefCountingOperation();
        return super.readBytes(dst, dstIndex, length);
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        recordLeakNonRefCountingOperation();
        return super.readBytes(dst);
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        recordLeakNonRefCountingOperation();
        return super.readBytes(dst, dstIndex, length);
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        recordLeakNonRefCountingOperation();
        return super.readBytes(dst);
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        recordLeakNonRefCountingOperation();
        return super.readBytes(out, length);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        recordLeakNonRefCountingOperation();
        return super.readBytes(out, length);
    }

    @Override
    public ByteBuf skipBytes(int length) {
        recordLeakNonRefCountingOperation();
        return super.skipBytes(length);
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        recordLeakNonRefCountingOperation();
        return super.writeBoolean(value);
    }

    @Override
    public ByteBuf writeByte(int value) {
        recordLeakNonRefCountingOperation();
        return super.writeByte(value);
    }

    @Override
    public ByteBuf writeShort(int value) {
        recordLeakNonRefCountingOperation();
        return super.writeShort(value);
    }

    @Override
    public ByteBuf writeMedium(int value) {
        recordLeakNonRefCountingOperation();
        return super.writeMedium(value);
    }

    @Override
    public ByteBuf writeInt(int value) {
        recordLeakNonRefCountingOperation();
        return super.writeInt(value);
    }

    @Override
    public ByteBuf writeLong(long value) {
        recordLeakNonRefCountingOperation();
        return super.writeLong(value);
    }

    @Override
    public ByteBuf writeChar(int value) {
        recordLeakNonRefCountingOperation();
        return super.writeChar(value);
    }

    @Override
    public ByteBuf writeFloat(float value) {
        recordLeakNonRefCountingOperation();
        return super.writeFloat(value);
    }

    @Override
    public ByteBuf writeDouble(double value) {
        recordLeakNonRefCountingOperation();
        return super.writeDouble(value);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        recordLeakNonRefCountingOperation();
        return super.writeBytes(src);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        recordLeakNonRefCountingOperation();
        return super.writeBytes(src, length);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        recordLeakNonRefCountingOperation();
        return super.writeBytes(src, srcIndex, length);
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        recordLeakNonRefCountingOperation();
        return super.writeBytes(src);
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        recordLeakNonRefCountingOperation();
        return super.writeBytes(src, srcIndex, length);
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        recordLeakNonRefCountingOperation();
        return super.writeBytes(src);
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        recordLeakNonRefCountingOperation();
        return super.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        recordLeakNonRefCountingOperation();
        return super.writeBytes(in, length);
    }

    @Override
    public ByteBuf writeZero(int length) {
        recordLeakNonRefCountingOperation();
        return super.writeZero(length);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        recordLeakNonRefCountingOperation();
        return super.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        recordLeakNonRefCountingOperation();
        return super.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        recordLeakNonRefCountingOperation();
        return super.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        recordLeakNonRefCountingOperation();
        return super.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteBufProcessor processor) {
        recordLeakNonRefCountingOperation();
        return super.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteBufProcessor processor) {
        recordLeakNonRefCountingOperation();
        return super.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteBufProcessor processor) {
        recordLeakNonRefCountingOperation();
        return super.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteBufProcessor processor) {
        recordLeakNonRefCountingOperation();
        return super.forEachByteDesc(index, length, processor);
    }

    @Override
    public ByteBuf copy() {
        recordLeakNonRefCountingOperation();
        return super.copy();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        recordLeakNonRefCountingOperation();
        return super.copy(index, length);
    }

    @Override
    public int nioBufferCount() {
        recordLeakNonRefCountingOperation();
        return super.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        recordLeakNonRefCountingOperation();
        return super.nioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        recordLeakNonRefCountingOperation();
        return super.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        recordLeakNonRefCountingOperation();
        return super.nioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        recordLeakNonRefCountingOperation();
        return super.nioBuffers(index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        recordLeakNonRefCountingOperation();
        return super.internalNioBuffer(index, length);
    }

    @Override
    public String toString(Charset charset) {
        recordLeakNonRefCountingOperation();
        return super.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        recordLeakNonRefCountingOperation();
        return super.toString(index, length, charset);
    }

    @Override
    public ByteBuf retain() {
        leak.record();
        return super.retain();
    }

    @Override
    public ByteBuf retain(int increment) {
        leak.record();
        return super.retain(increment);
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        recordLeakNonRefCountingOperation();
        return super.capacity(newCapacity);
    }
}
