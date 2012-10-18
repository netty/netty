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

/**
 * The common interface for buffer wrappers and derived buffers.  Most users won't
 * need to use this interface.  It is used internally in most cases.
 */
public interface WrappedByteBuf extends ByteBuf {
    /**
     * Returns this buffer's parent that this buffer is wrapping.
     */
    ByteBuf unwrap();

    @Override
    WrappedByteBuf capacity(int newCapacity);

    @Override
    WrappedByteBuf readerIndex(int readerIndex);

    @Override
    WrappedByteBuf writerIndex(int writerIndex);

    @Override
    WrappedByteBuf setIndex(int readerIndex, int writerIndex);

    @Override
    WrappedByteBuf clear();

    @Override
    WrappedByteBuf markReaderIndex();

    @Override
    WrappedByteBuf resetReaderIndex();

    @Override
    WrappedByteBuf markWriterIndex();

    @Override
    WrappedByteBuf resetWriterIndex();

    @Override
    WrappedByteBuf discardReadBytes();

    @Override
    WrappedByteBuf ensureWritableBytes(int minWritableBytes);

    @Override
    WrappedByteBuf getBytes(int index, ByteBuf dst);

    @Override
    WrappedByteBuf getBytes(int index, ByteBuf dst, int length);

    @Override
    WrappedByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length);

    @Override
    WrappedByteBuf getBytes(int index, byte[] dst);

    @Override
    WrappedByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);

    @Override
    WrappedByteBuf getBytes(int index, ByteBuffer dst);

    @Override
    WrappedByteBuf getBytes(int index, OutputStream out, int length) throws IOException;

    @Override
    WrappedByteBuf setBoolean(int index, boolean value);

    @Override
    WrappedByteBuf setByte(int index, int value);

    @Override
    WrappedByteBuf setShort(int index, int value);

    @Override
    WrappedByteBuf setMedium(int index, int value);

    @Override
    WrappedByteBuf setInt(int index, int value);

    @Override
    WrappedByteBuf setLong(int index, long value);

    @Override
    WrappedByteBuf setChar(int index, int value);

    @Override
    WrappedByteBuf setFloat(int index, float value);

    @Override
    WrappedByteBuf setDouble(int index, double value);

    @Override
    WrappedByteBuf setBytes(int index, ByteBuf src);

    @Override
    WrappedByteBuf setBytes(int index, ByteBuf src, int length);

    @Override
    WrappedByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length);

    @Override
    WrappedByteBuf setBytes(int index, byte[] src);

    @Override
    WrappedByteBuf setBytes(int index, byte[] src, int srcIndex, int length);

    @Override
    WrappedByteBuf setBytes(int index, ByteBuffer src);

    @Override
    WrappedByteBuf setZero(int index, int length);

    @Override
    WrappedByteBuf readBytes(ByteBuf dst);

    @Override
    WrappedByteBuf readBytes(ByteBuf dst, int length);

    @Override
    WrappedByteBuf readBytes(ByteBuf dst, int dstIndex, int length);

    @Override
    WrappedByteBuf readBytes(byte[] dst);

    @Override
    WrappedByteBuf readBytes(byte[] dst, int dstIndex, int length);

    @Override
    WrappedByteBuf readBytes(ByteBuffer dst);

    @Override
    WrappedByteBuf readBytes(OutputStream out, int length) throws IOException;

    @Override
    WrappedByteBuf skipBytes(int length);

    @Override
    WrappedByteBuf writeBoolean(boolean value);

    @Override
    WrappedByteBuf writeByte(int value);

    @Override
    WrappedByteBuf writeShort(int value);

    @Override
    WrappedByteBuf writeMedium(int value);

    @Override
    WrappedByteBuf writeInt(int value);

    @Override
    WrappedByteBuf writeLong(long value);

    @Override
    WrappedByteBuf writeChar(int value);

    @Override
    WrappedByteBuf writeFloat(float value);

    @Override
    WrappedByteBuf writeDouble(double value);

    @Override
    WrappedByteBuf writeBytes(ByteBuf src);

    @Override
    WrappedByteBuf writeBytes(ByteBuf src, int length);

    @Override
    WrappedByteBuf writeBytes(ByteBuf src, int srcIndex, int length);

    @Override
    WrappedByteBuf writeBytes(byte[] src);

    @Override
    WrappedByteBuf writeBytes(byte[] src, int srcIndex, int length);

    @Override
    WrappedByteBuf writeBytes(ByteBuffer src);

    @Override
    WrappedByteBuf writeZero(int length);
}
