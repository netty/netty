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

/**
 * A {@link ByteBuf} which is composed out of other {@link ByteBuf}s.
 */
public interface CompositeByteBuf extends ByteBuf, Iterable<ByteBuf> {

    /**
     * Add the given {@link ByteBuf}.
     *
     * Be aware that this method does not increase the {@code writerIndex} of the {@link CompositeByteBuf}.
     * If you need to have it increased you need to handle it by your own.
     *
     * @param buffer    the {@link ByteBuf} to add
     * @return self     this instance
     */
    CompositeByteBuf addComponent(ByteBuf buffer);

    /**
     * Add the given {@link ByteBuf} on the specific index.
     *
     * Be aware that this method does not increase the {@code writerIndex} of the {@link CompositeByteBuf}.
     * If you need to have it increased you need to handle it by your own.
     *
     * @param cIndex
     *          the index on which the {@link ByteBuf} will be added
     * @param buffer
     *          the {@link ByteBuf} to add
     * @return self
     *          this instance
     * @throws IndexOutOfBoundsException
     *          if the index is invalid
     */
    CompositeByteBuf addComponent(int cIndex, ByteBuf buffer);

    /**
     * Add the given {@link ByteBuf}s.
     *
     * Be aware that this method does not increase the {@code writerIndex} of the {@link CompositeByteBuf}.
     * If you need to have it increased you need to handle it by your own.
     *
     * @param buffers   the {@link ByteBuf}s to add
     * @return self     this instance
     */
    CompositeByteBuf addComponents(ByteBuf... buffers);

    /**
     * Add the given {@link ByteBuf}s.
     *
     * Be aware that this method does not increase the {@code writerIndex} of the {@link CompositeByteBuf}.
     * If you need to have it increased you need to handle it by your own.
     *
     * @param buffers   the {@link ByteBuf}s to add
     * @return self     this instance
     */
    CompositeByteBuf addComponents(Iterable<ByteBuf> buffers);

    /**
     * Add the given {@link ByteBuf}s on the specific index
     *
     * Be aware that this method does not increase the {@code writerIndex} of the {@link CompositeByteBuf}.
     * If you need to have it increased you need to handle it by your own.
     *
     * @param cIndex
     *          the index on which the {@link ByteBuf} will be added.
     * @param buffers
     *          the {@link ByteBuf}s to add
     * @return self
     *          this instance
     * @throws IndexOutOfBoundsException
     *          if the index is invalid
     *
     */
    CompositeByteBuf addComponents(int cIndex, ByteBuf... buffers);

    /**
     * Add the given {@link ByteBuf}s on the specific index
     *
     * Be aware that this method does not increase the {@code writerIndex} of the {@link CompositeByteBuf}.
     * If you need to have it increased you need to handle it by your own.
     *
     * @param cIndex
     *          the index on which the {@link ByteBuf} will be added.
     * @param buffers
     *          the {@link ByteBuf}s to add
     * @return self
     *          this instance
     * @throws IndexOutOfBoundsException
     *          if the index is invalid
     */
    CompositeByteBuf addComponents(int cIndex, Iterable<ByteBuf> buffers);

    /**
     * Remove the {@link ByteBuf} from the given index.
     *
     * @param cIndex
     *          the index on from which the {@link ByteBuf} will be remove
     * @return self
     *          this instance
     * @throws IndexOutOfBoundsException
     *          if the index is invalid
     */
    CompositeByteBuf removeComponent(int cIndex);

    /**
     * Remove the number of {@link ByteBuf}s starting from the given index.
     *
     * @param cIndex
     *          the index on which the {@link ByteBuf}s will be started to removed
     * @param numComponents
     *          the number of components to remove
     * @return self
     *          this instance
     * @throws IndexOutOfBoundsException
     *          if the index is invalid
     */
    CompositeByteBuf removeComponents(int cIndex, int numComponents);

    /**
     * Return the current number of {@link ByteBuf}'s that are composed in this instance
     */
    int numComponents();

    /**
     * Return the max number of {@link ByteBuf}'s that are composed in this instance
     */
    int maxNumComponents();

    /**
     * Return the {@link ByteBuf} on the specified index
     *
     * @param cIndex
     *          the index for which the {@link ByteBuf} should be returned
     * @return buf
     *          the {@link ByteBuf} on the specified index
     * @throws IndexOutOfBoundsException
     *          if the index is invalid
     */
    ByteBuf component(int cIndex);

    /**
     * Return the {@link ByteBuf} on the specified index
     *
     * @param offset
     *          the offset for which the {@link ByteBuf} should be returned
     * @return buf
     *          the {@link ByteBuf} on the specified index
     * @throws IndexOutOfBoundsException
     *          if the offset is invalid
     */
    ByteBuf componentAtOffset(int offset);

    /**
     * Discard all {@link ByteBuf}s which are read.
     *
     * @return self    this instance
     */
    CompositeByteBuf discardReadComponents();

    /**
     * Consolidate the composed {@link ByteBuf}s
     *
     * @return self     this instance
     */
    CompositeByteBuf consolidate();

    /**
     * Consolidate the composed {@link ByteBuf}s
     *
     * @param cIndex
     *          the index on which to start to compose
     * @param numComponents
     *          the number of components to compose
     * @return self
     *          this instance
     * @throws IndexOutOfBoundsException
     *          if the offset is invalid
     */
    CompositeByteBuf consolidate(int cIndex, int numComponents);

    /**
     * Return the index for the given offset
     */
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
    CompositeByteBuf ensureWritable(int minWritableBytes);

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
    CompositeByteBuf retain(int increment);

    @Override
    CompositeByteBuf retain();
}
