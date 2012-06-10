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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A factory that creates or pools {@link ByteBuf}s.
 */
public interface ByteBufFactory {

    /**
     * Returns a {@link ByteBuf} with the specified {@code capacity}.
     * This method is identical to {@code getBuffer(getDefaultOrder(), capacity)}.
     *
     * @param capacity the capacity of the returned {@link ByteBuf}
     * @return a {@link ByteBuf} with the specified {@code capacity},
     *         whose {@code readerIndex} and {@code writerIndex} are {@code 0}
     */
    ByteBuf getBuffer(int capacity);

    /**
     * Returns a {@link ByteBuf} with the specified {@code endianness}
     * and {@code capacity}.
     *
     * @param endianness the endianness of the returned {@link ByteBuf}
     * @param capacity   the capacity of the returned {@link ByteBuf}
     * @return a {@link ByteBuf} with the specified {@code endianness} and
     *         {@code capacity}, whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0}
     */
    ByteBuf getBuffer(ByteOrder endianness, int capacity);

    /**
     * Returns a {@link ByteBuf} whose content is equal to the sub-region
     * of the specified {@code array}.  Depending on the factory implementation,
     * the returned buffer could wrap the {@code array} or create a new copy of
     * the {@code array}.
     * This method is identical to {@code getBuffer(getDefaultOrder(), array, offset, length)}.
     *
     * @param array the byte array
     * @param offset the offset of the byte array
     * @param length the length of the byte array
     *
     * @return a {@link ByteBuf} with the specified content,
     *         whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0} and {@code (length - offset)} respectively
     */
    ByteBuf getBuffer(byte[] array, int offset, int length);

    /**
     * Returns a {@link ByteBuf} whose content is equal to the sub-region
     * of the specified {@code array}.  Depending on the factory implementation,
     * the returned buffer could wrap the {@code array} or create a new copy of
     * the {@code array}.
     *
     * @param endianness the endianness of the returned {@link ByteBuf}
     * @param array the byte array
     * @param offset the offset of the byte array
     * @param length the length of the byte array
     *
     * @return a {@link ByteBuf} with the specified content,
     *         whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0} and {@code (length - offset)} respectively
     */
    ByteBuf getBuffer(ByteOrder endianness, byte[] array, int offset, int length);

    /**
     * Returns a {@link ByteBuf} whose content is equal to the sub-region
     * of the specified {@code nioBuffer}.  Depending on the factory
     * implementation, the returned buffer could wrap the {@code nioBuffer} or
     * create a new copy of the {@code nioBuffer}.
     *
     * @param nioBuffer the NIO {@link ByteBuffer}
     *
     * @return a {@link ByteBuf} with the specified content,
     *         whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0} and {@code nioBuffer.remaining()} respectively
     */
    ByteBuf getBuffer(ByteBuffer nioBuffer);

    /**
     * Returns the default endianness of the {@link ByteBuf} which is
     * returned by {@link #getBuffer(int)}.
     *
     * @return the default endianness of the {@link ByteBuf} which is
     *         returned by {@link #getBuffer(int)}
     */
    ByteOrder getDefaultOrder();
}
