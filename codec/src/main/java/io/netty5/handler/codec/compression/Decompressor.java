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
 * Decompressor that takes care of decompress some input.
 */
public interface Decompressor extends AutoCloseable {
    /**
     * This method will read from the input {@link ByteBuf} and decompress into a new {@link ByteBuf} that will be
     * allocated (if needed) from the {@link ByteBufAllocator}. If there is not enough readable data in the
     * {@link ByteBuf} to process it will return {@code null}.
     *
     * This method should be called in a loop as long:
     *
     * <li>
     *     <ul>{@link #isFinished()} is {@code false}</ul>
     *     <ul>something was read from the {@code input}</ul>
     *     <ul>something was returned</ul>
     * </li>
     * Otherwise this method should be called again once there is more data in the input buffer.
     *
     * @param input         the {@link ByteBuf} that contains the data to be decompressed.
     * @param allocator     the {@link ByteBufAllocator} that is used to allocate a new buffer (if needed) to write the
     *                      decompressed bytes too.
     * @return              the {@link ByteBuf} that contains the decompressed data. The caller of this method takes
     *                      ownership of the buffer. The return value will be {@code null} if there is not enough data
     *                      readable in the input to make any progress. In this case the user should call it again once
     *                      there is more data ready to be consumed.
     * @throws DecompressionException   thrown if an decompression error was encountered or the decompressor was closed
     *                                  before.
     */
    ByteBuf decompress(ByteBuf input, ByteBufAllocator allocator) throws DecompressionException;

    /**
     * Returns {@code} true if the decompressor was finish. This might be because the decompressor was explicitly closed
     * or the end of the compressed stream was detected.
     *
     * @return {@code true} if the decompressor is done with decompressing the stream.
     */
    boolean isFinished();

    /**
     * Return {@code true} if the decompressor was closed, {@code false} otherwise.
     *
     * @return if {@link #close()} was called.
     */
    boolean isClosed();

    /**
     * Close the decompressor. After this method was called {@link #isFinished()}
     * will return {@code true} as well and it is not allowed to call {@link #decompress(ByteBuf, ByteBufAllocator)}
     * anymore.
     */
    @Override
    void close();
}
