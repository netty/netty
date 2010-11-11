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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A factory that creates or pools {@link ChannelBuffer}s.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface ChannelBufferFactory {

    /**
     * Returns a {@link ChannelBuffer} with the specified {@code capacity}.
     * This method is identical to {@code getBuffer(getDefaultOrder(), capacity)}.
     *
     * @param capacity the capacity of the returned {@link ChannelBuffer}
     * @return a {@link ChannelBuffer} with the specified {@code capacity},
     *         whose {@code readerIndex} and {@code writerIndex} are {@code 0}
     */
    ChannelBuffer getBuffer(int capacity);

    /**
     * Returns a {@link ChannelBuffer} with the specified {@code endianness}
     * and {@code capacity}.
     *
     * @param endianness the endianness of the returned {@link ChannelBuffer}
     * @param capacity   the capacity of the returned {@link ChannelBuffer}
     * @return a {@link ChannelBuffer} with the specified {@code endianness} and
     *         {@code capacity}, whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0}
     */
    ChannelBuffer getBuffer(ByteOrder endianness, int capacity);

    /**
     * Returns a {@link ChannelBuffer} whose content is equal to the sub-region
     * of the specified {@code array}.  Depending on the factory implementation,
     * the returned buffer could wrap the {@code array} or create a new copy of
     * the {@code array}.
     * This method is identical to {@code getBuffer(getDefaultOrder(), array, offset, length)}.
     *
     * @param array the byte array
     * @param offset the offset of the byte array
     * @param length the length of the byte array
     *
     * @return a {@link ChannelBuffer} with the specified content,
     *         whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0} and {@code (length - offset)} respectively
     */
    ChannelBuffer getBuffer(byte[] array, int offset, int length);

    /**
     * Returns a {@link ChannelBuffer} whose content is equal to the sub-region
     * of the specified {@code array}.  Depending on the factory implementation,
     * the returned buffer could wrap the {@code array} or create a new copy of
     * the {@code array}.
     *
     * @param endianness the endianness of the returned {@link ChannelBuffer}
     * @param array the byte array
     * @param offset the offset of the byte array
     * @param length the length of the byte array
     *
     * @return a {@link ChannelBuffer} with the specified content,
     *         whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0} and {@code (length - offset)} respectively
     */
    ChannelBuffer getBuffer(ByteOrder endianness, byte[] array, int offset, int length);

    /**
     * Returns a {@link ChannelBuffer} whose content is equal to the sub-region
     * of the specified {@code nioBuffer}.  Depending on the factory
     * implementation, the returned buffer could wrap the {@code nioBuffer} or
     * create a new copy of the {@code nioBuffer}.
     *
     * @param nioBuffer the NIO {@link ByteBuffer}
     *
     * @return a {@link ChannelBuffer} with the specified content,
     *         whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0} and {@code nioBuffer.remaining()} respectively
     */
    ChannelBuffer getBuffer(ByteBuffer nioBuffer);

    /**
     * Returns the default endianness of the {@link ChannelBuffer} which is
     * returned by {@link #getBuffer(int)}.
     *
     * @return the default endianness of the {@link ChannelBuffer} which is
     *         returned by {@link #getBuffer(int)}
     */
    ByteOrder getDefaultOrder();
}
