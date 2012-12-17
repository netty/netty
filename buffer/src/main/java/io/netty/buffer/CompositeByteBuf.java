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
import java.util.List;

public interface CompositeByteBuf extends ByteBuf, Iterable<ByteBuf> {

    CompositeByteBuf addComponent(ByteBuf buffer);
    CompositeByteBuf addComponent(int cIndex, ByteBuf buffer);

    CompositeByteBuf addComponents(ByteBuf... buffers);
    CompositeByteBuf addComponents(Iterable<ByteBuf> buffers);
    CompositeByteBuf addComponents(int cIndex, ByteBuf... buffers);
    CompositeByteBuf addComponents(int cIndex, Iterable<ByteBuf> buffers);

    CompositeByteBuf removeComponent(int cIndex);
    CompositeByteBuf removeComponents(int cIndex, int numComponents);

    int numComponents();
    int maxNumComponents();

    ByteBuf component(int cIndex);
    ByteBuf componentAtOffset(int offset);

    CompositeByteBuf discardReadComponents();
    CompositeByteBuf consolidate();
    CompositeByteBuf consolidate(int cIndex, int numComponents);

    int toComponentIndex(int offset);
    int toByteIndex(int cIndex);

    /**
     * Same with {@link #slice(int, int)} except that this method returns a list.
     */
    List<ByteBuf> decompose(int offset, int length);

    @Override
    CompositeByteBuf capacity(int newCapacity);

    @Override
    CompositeByteBuf readerIndex(int readerIndex);

    @Override
    CompositeByteBuf writerIndex(int writerIndex);

    @Override
    CompositeByteBuf setIndex(int readerIndex, int writerIndex);

    @Override
    CompositeByteBuf clear();

    @Override
    CompositeByteBuf markReaderIndex();

    @Override
    CompositeByteBuf resetReaderIndex();

    @Override
    CompositeByteBuf markWriterIndex();

    @Override
    CompositeByteBuf resetWriterIndex();

    @Override
    CompositeByteBuf discardReadBytes();

    @Override
    CompositeByteBuf discardSomeReadBytes();

    @Override
    CompositeByteBuf ensureWritableBytes(int minWritableBytes);

    @Override
    CompositeByteBuf getBytes(int index, ByteBuf dst);

    @Override
    CompositeByteBuf getBytes(int index, ByteBuf dst, int length);

    @Override
    CompositeByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length);

    @Override
    CompositeByteBuf getBytes(int index, byte[] dst);

    @Override
    CompositeByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);

    @Override
    CompositeByteBuf getBytes(int index, ByteBuffer dst);

    @Override
    CompositeByteBuf getBytes(int index, OutputStream out, int length) throws IOException;

    @Override
    CompositeByteBuf setBoolean(int index, boolean value);

    @Override
    CompositeByteBuf setByte(int index, int value);

    @Override
    CompositeByteBuf setShort(int index, int value);

    @Override
    CompositeByteBuf setMedium(int index, int value);

    @Override
    CompositeByteBuf setInt(int index, int value);

    @Override
    CompositeByteBuf setLong(int index, long value);

    @Override
    CompositeByteBuf setChar(int index, int value);

    @Override
    CompositeByteBuf setFloat(int index, float value);

    @Override
    CompositeByteBuf setDouble(int index, double value);

    @Override
    CompositeByteBuf setBytes(int index, ByteBuf src);

    @Override
    CompositeByteBuf setBytes(int index, ByteBuf src, int length);

    @Override
    CompositeByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length);

    @Override
    CompositeByteBuf setBytes(int index, byte[] src);

    @Override
    CompositeByteBuf setBytes(int index, byte[] src, int srcIndex, int length);

    @Override
    CompositeByteBuf setBytes(int index, ByteBuffer src);

    @Override
    CompositeByteBuf setZero(int index, int length);

    @Override
    CompositeByteBuf readBytes(ByteBuf dst);

    @Override
    CompositeByteBuf readBytes(ByteBuf dst, int length);

    @Override
    CompositeByteBuf readBytes(ByteBuf dst, int dstIndex, int length);

    @Override
    CompositeByteBuf readBytes(byte[] dst);

    @Override
    CompositeByteBuf readBytes(byte[] dst, int dstIndex, int length);

    @Override
    CompositeByteBuf readBytes(ByteBuffer dst);

    @Override
    CompositeByteBuf readBytes(OutputStream out, int length) throws IOException;

    @Override
    CompositeByteBuf skipBytes(int length);

    @Override
    CompositeByteBuf writeBoolean(boolean value);

    @Override
    CompositeByteBuf writeByte(int value);

    @Override
    CompositeByteBuf writeShort(int value);

    @Override
    CompositeByteBuf writeMedium(int value);

    @Override
    CompositeByteBuf writeInt(int value);

    @Override
    CompositeByteBuf writeLong(long value);

    @Override
    CompositeByteBuf writeChar(int value);

    @Override
    CompositeByteBuf writeFloat(float value);

    @Override
    CompositeByteBuf writeDouble(double value);

    @Override
    CompositeByteBuf writeBytes(ByteBuf src);

    @Override
    CompositeByteBuf writeBytes(ByteBuf src, int length);

    @Override
    CompositeByteBuf writeBytes(ByteBuf src, int srcIndex, int length);

    @Override
    CompositeByteBuf writeBytes(byte[] src);

    @Override
    CompositeByteBuf writeBytes(byte[] src, int srcIndex, int length);

    @Override
    CompositeByteBuf writeBytes(ByteBuffer src);

    @Override
    CompositeByteBuf writeZero(int length);

    @Override
    CompositeByteBuf suspendIntermediaryDeallocations();

    @Override
    CompositeByteBuf resumeIntermediaryDeallocations();
}
