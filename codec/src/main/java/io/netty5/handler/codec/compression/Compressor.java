/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.compression;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;

/**
 * Compressor that takes care of compress some input.
 */
public interface Compressor extends AutoCloseable {
    /**
     * This method will read from the input {@link ByteBuf} and compress into a new {@link ByteBuf} that will be
     * allocated (if needed) from the {@link ByteBufAllocator}. This method is expected to consume all data from the
     * input but <strong>not</strong> take ownership. The caller is responsible to release the input buffer after
     * this method returns.
     *
     * @param input         the {@link ByteBuf} that contains the data to be compressed.
     * @param allocator     the {@link ByteBufAllocator} that is used to allocate a new buffer (if needed) to write the
     *                      compressed bytes too.
     * @return              the {@link ByteBuf} that contains the compressed data. The caller of this method takes
     *                      ownership of the buffer. The return value will <strong>never</strong> be {@code null}.
     * @throws CompressionException   thrown if an compression error was encountered or the compressor was closed
     * already.
     */
    ByteBuf compress(ByteBuf input, ByteBufAllocator allocator) throws CompressionException;

    /**
     * By calling this method we signal that the compression stream is marked as finish. The returned {@link ByteBuf}
     * might contain a "trailer" which marks the end of the stream.
     *
     * @return  the {@link ByteBuf} which represent the end of the compression stream, which might be empty if the
     *          compressor don't need a trailer to signal the end. The caller of this method takes
     *          ownership of the buffer. The return value will <strong>never</strong> be {@code null}.
     * @throws CompressionException   thrown if an compression error was encountered or the compressor was closed
     * already.
     */
    ByteBuf finish(ByteBufAllocator allocator) throws CompressionException;

    /**
     * Returns {@code} true if the compressor was finished or closed. This might happen because someone explicit called
     * {@link #finish(ByteBufAllocator)} / {@link #close()} or the compressor implementation did decide to close itself
     * due a compression error which can't be recovered. After {@link #isFinished()} returns {@code true} the
     * {@link #compress(ByteBuf, ByteBufAllocator)} method will just return an empty buffer without consuming anything
     * from its input buffer.
     *
     * @return  {@code true }if the compressor was marked as finished, {@code false} otherwise.
     */
    boolean isFinished();

    /**
     * Return {@code true} if the decompressor was closed, {@code false} otherwise.
     *
     * @return {@code true} if the decompressor was closed, {@code false} otherwise.
     */
    boolean isClosed();

    /**#
     * Close the compressor. After this method was called {@link #isFinished()}
     * will return {@code true} as well and it is not allowed to call {@link #compress(ByteBuf, ByteBufAllocator)} or
     * {@link #finish(ByteBufAllocator)} anymore
-     */
    @Override
    void close();
}
