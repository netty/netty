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
import java.util.List;

public interface CompositeByteBuf extends ByteBuf, Iterable<ByteBuf> {

    void addComponent(ByteBuf buffer);
    void addComponent(int cIndex, ByteBuf buffer);

    void addComponents(ByteBuf... buffers);
    void addComponents(Iterable<ByteBuf> buffers);
    void addComponents(int cIndex, ByteBuf... buffers);
    void addComponents(int cIndex, Iterable<ByteBuf> buffers);

    void removeComponent(int cIndex);
    void removeComponents(int cIndex, int numComponents);

    int numComponents();
    int maxNumComponents();

    ByteBuf component(int cIndex);
    ByteBuf componentAtOffset(int offset);

    void discardReadComponents();
    void consolidate();
    void consolidate(int cIndex, int numComponents);

    int toComponentIndex(int offset);
    int toByteIndex(int cIndex);

    /**
     * Same with {@link #slice(int, int)} except that this method returns a list.
     */
    List<ByteBuf> decompose(int offset, int length);

    /**
     * Exposes this buffer's readable bytes as an NIO {@link ByteBuffer}'s.  The returned buffer
     * shares the content with this buffer, while changing the position and limit of the returned
     * NIO buffer does not affect the indexes and marks of this buffer. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.  Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     *
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     */
    ByteBuffer[] nioBuffers();

    /**
     * Exposes this buffer's bytes as an NIO {@link ByteBuffer}'s for the specified offset and length
     * The returned buffer shares the content with this buffer, while changing the position and limit
     * of the returned NIO buffer does not affect the indexes and marks of this buffer. This method does
     * not modify {@code readerIndex} or {@code writerIndex} of this buffer.  Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     *
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     */
    ByteBuffer[] nioBuffers(int offset, int length);
}
