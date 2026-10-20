/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.UnstableApi;

/**
 * Shared API for various decompression algorithms. A decompressor reports its current status using {@link #status()}.
 * Status documentation lists which operations are permitted for which status. Callers must observe the current
 * status before invoking an operation that is only valid for that status.
 * <p>
 * All methods may throw exceptions. If an exception is thrown, no further operations are permitted, except for
 * {@link #close()}.
 * <p>
 * This API is still in progress as part of the decompression migration tracked by
 * <a href="https://github.com/netty/netty/issues/16743">#16743</a>.
 */
@UnstableApi
public interface Decompressor extends AutoCloseable {
    /**
     * Get the current status. Like the other operations on this class, calling this method is not permitted if the
     * decompressor has failed or was closed.
     *
     * @return The current status
     */
    Status status() throws DecompressionException;

    /**
     * Add a new input buffer. Only permitted for {@link Status#NEED_INPUT}.
     *
     * @param buf The input buffer. Buffer ownership transfers to the decompressor (also on exception).
     */
    void addInput(ByteBuf buf) throws DecompressionException;

    /**
     * Notify the decompressor that the end of input has been reached. Some implementations may flush remaining data or
     * throw an exception if the input is truncated, but most implementations do nothing. Only permitted for
     * {@link Status#NEED_INPUT}.
     */
    void endOfInput() throws DecompressionException;

    /**
     * Take a decompressed buffer from this decompressor. Only permitted for {@link Status#NEED_OUTPUT}. Buffer
     * ownership transfers to the caller.
     *
     * @return The decompressed buffer. May be empty.
     */
    ByteBuf takeOutput() throws DecompressionException;

    /**
     * Close this decompressor, cleaning up any associated resources. <b>This method is idempotent.</b>
     */
    @Override
    void close() throws DecompressionException;

    /**
     * Current status of the decompressor. The status indicates which method may be called to make progress on
     * decompression.
     */
    @UnstableApi
    enum Status {
        /**
         * More input is required before decompression can proceed. Only calls to {@link #addInput} and
         * {@link #endOfInput()} are permitted.
         */
        NEED_INPUT,
        /**
         * Output must be consumed before more input can be received. Only calls to {@link #takeOutput()} are
         * permitted.
         */
        NEED_OUTPUT,
        /**
         * All data has been processed, and the format indicates that no more input may arrive. No further
         * decompression operations are permitted, except {@link Decompressor#close()} to release resources.
         */
        COMPLETE,
    }

    @UnstableApi
    abstract class AbstractDecompressorBuilder {

        protected AbstractDecompressorBuilder() {
        }

        public abstract Decompressor build(ByteBufAllocator allocator) throws DecompressionException;
    }
}
