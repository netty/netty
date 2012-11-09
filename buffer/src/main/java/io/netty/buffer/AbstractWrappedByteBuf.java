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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

/**
 * Abstract base class for WrappedByteBuf implementations.
 */
public abstract class AbstractWrappedByteBuf extends AbstractByteBuf implements WrappedByteBuf {

    protected AbstractWrappedByteBuf(ByteOrder endianness, int maxCapacity) {
        super(endianness, maxCapacity);
    }

    @Override
    public WrappedByteBuf capacity(int newCapacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WrappedByteBuf discardReadBytes() {
        return (WrappedByteBuf) super.discardReadBytes();
    }

    @Override
    public WrappedByteBuf readerIndex(int readerIndex) {
        return (WrappedByteBuf) super.readerIndex(readerIndex);
    }

    @Override
    public WrappedByteBuf writerIndex(int writerIndex) {
        return (WrappedByteBuf) super.writerIndex(writerIndex);
    }

    @Override
    public WrappedByteBuf setIndex(int readerIndex, int writerIndex) {
        return (WrappedByteBuf) super.setIndex(readerIndex, writerIndex);
    }

    @Override
    public WrappedByteBuf clear() {
        return (WrappedByteBuf) super.clear();
    }

    @Override
    public WrappedByteBuf markReaderIndex() {
        return (WrappedByteBuf) super.markReaderIndex();
    }

    @Override
    public WrappedByteBuf resetReaderIndex() {
        return (WrappedByteBuf) super.resetReaderIndex();
    }

    @Override
    public WrappedByteBuf markWriterIndex() {
        return (WrappedByteBuf) super.markWriterIndex();
    }

    @Override
    public WrappedByteBuf resetWriterIndex() {
        return (WrappedByteBuf) super.resetWriterIndex();
    }

    @Override
    public WrappedByteBuf ensureWritableBytes(int minWritableBytes) {
        return (WrappedByteBuf) super.ensureWritableBytes(minWritableBytes);
    }

    @Override
    public WrappedByteBuf getBytes(int index, ByteBuf dst) {
        return (WrappedByteBuf) super.getBytes(index, dst);
    }

    @Override
    public WrappedByteBuf getBytes(int index, ByteBuf dst, int length) {
        return (WrappedByteBuf) super.getBytes(index, dst, length);
    }

    @Override
    public WrappedByteBuf getBytes(int index, byte[] dst) {
        return (WrappedByteBuf) super.getBytes(index, dst);
    }

    @Override
    public WrappedByteBuf setBoolean(int index, boolean value) {
        return (WrappedByteBuf) super.setBoolean(index, value);
    }

    @Override
    public WrappedByteBuf setChar(int index, int value) {
        return (WrappedByteBuf) super.setChar(index, value);
    }

    @Override
    public WrappedByteBuf setFloat(int index, float value) {
        return (WrappedByteBuf) super.setFloat(index, value);
    }

    @Override
    public WrappedByteBuf setDouble(int index, double value) {
        return (WrappedByteBuf) super.setDouble(index, value);
    }

    @Override
    public WrappedByteBuf setBytes(int index, ByteBuf src) {
        return (WrappedByteBuf) super.setBytes(index, src);
    }

    @Override
    public WrappedByteBuf setBytes(int index, ByteBuf src, int length) {
        return (WrappedByteBuf) super.setBytes(index, src, length);
    }

    @Override
    public WrappedByteBuf setBytes(int index, byte[] src) {
        return (WrappedByteBuf) super.setBytes(index, src);
    }


    @Override
    public WrappedByteBuf setZero(int index, int length) {
        return (WrappedByteBuf) super.setZero(index, length);
    }

    @Override
    public WrappedByteBuf readBytes(ByteBuf dst) {
        return (WrappedByteBuf) super.readBytes(dst);
    }

    @Override
    public WrappedByteBuf readBytes(ByteBuf dst, int length) {
        return (WrappedByteBuf) super.readBytes(dst, length);
    }

    @Override
    public WrappedByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        return (WrappedByteBuf) super.readBytes(dst, dstIndex, length);
    }

    @Override
    public WrappedByteBuf readBytes(byte[] dst) {
        return (WrappedByteBuf) super.readBytes(dst);
    }

    @Override
    public WrappedByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        return (WrappedByteBuf) super.readBytes(dst, dstIndex, length);
    }

    @Override
    public WrappedByteBuf readBytes(ByteBuffer dst) {
        return (WrappedByteBuf) super.readBytes(dst);
    }

    @Override
    public WrappedByteBuf readBytes(OutputStream out, int length) throws IOException {
        return (WrappedByteBuf) super.readBytes(out, length);
    }

    @Override
    public WrappedByteBuf skipBytes(int length) {
        return (WrappedByteBuf) super.skipBytes(length);
    }

    @Override
    public WrappedByteBuf writeBoolean(boolean value) {
        return (WrappedByteBuf) super.writeBoolean(value);
    }

    @Override
    public WrappedByteBuf writeByte(int value) {
        return (WrappedByteBuf) super.writeByte(value);
    }

    @Override
    public WrappedByteBuf writeShort(int value) {
        return (WrappedByteBuf) super.writeShort(value);
    }

    @Override
    public WrappedByteBuf writeMedium(int value) {
        return (WrappedByteBuf) super.writeMedium(value);
    }

    @Override
    public WrappedByteBuf writeInt(int value) {
        return (WrappedByteBuf) super.writeInt(value);
    }

    @Override
    public WrappedByteBuf writeLong(long value) {
        return (WrappedByteBuf) super.writeLong(value);
    }

    @Override
    public WrappedByteBuf writeChar(int value) {
        return (WrappedByteBuf) super.writeChar(value);
    }

    @Override
    public WrappedByteBuf writeFloat(float value) {
        return (WrappedByteBuf) super.writeFloat(value);
    }

    @Override
    public WrappedByteBuf writeDouble(double value) {
        return (WrappedByteBuf) super.writeDouble(value);
    }

    @Override
    public WrappedByteBuf writeBytes(ByteBuf src) {
        return (WrappedByteBuf) super.writeBytes(src);
    }

    @Override
    public WrappedByteBuf writeBytes(ByteBuf src, int length) {
        return (WrappedByteBuf) super.writeBytes(src, length);
    }

    @Override
    public WrappedByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        return (WrappedByteBuf) super.writeBytes(src, srcIndex, length);
    }

    @Override
    public WrappedByteBuf writeBytes(byte[] src) {
        return (WrappedByteBuf) super.writeBytes(src);
    }

    @Override
    public WrappedByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        return (WrappedByteBuf) super.writeBytes(src, srcIndex, length);
    }

    @Override
    public WrappedByteBuf writeBytes(ByteBuffer src) {
        return (WrappedByteBuf) super.writeBytes(src);
    }

    @Override
    public WrappedByteBuf writeZero(int length) {
        return (WrappedByteBuf) super.writeZero(length);
    }

}
