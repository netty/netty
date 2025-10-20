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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * Special decompressor implementation that buffers input so that it can be processed piecemeal, similar to
 * {@link io.netty.handler.codec.ByteToMessageDecoder}.
 */
abstract class InputBufferingDecompressor implements Decompressor {
    protected final ByteBufAllocator allocator;
    private ByteBuf cumulation;

    InputBufferingDecompressor(ByteBufAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public final void addInput(ByteBuf buf) throws DecompressionException {
        if (!buf.isReadable()) {
            buf.release();
            return;
        }
        if (this.cumulation != null) {
            buf = ByteToMessageDecoder.MERGE_CUMULATOR.cumulate(allocator, this.cumulation, buf);
            this.cumulation = null;
        }
        try {
            processInput(buf);
        } catch (Throwable t) {
            buf.release();
            throw t;
        }
        if (buf.isReadable()) {
            this.cumulation = buf;
        } else {
            buf.release();
        }
    }

    @Override
    public final ByteBuf takeOutput() throws DecompressionException {
        ByteBuf buf = cumulation == null ? Unpooled.EMPTY_BUFFER : cumulation;
        ByteBuf output = processOutput(buf);
        if (status() == Status.NEED_INPUT && buf.isReadable()) {
            try {
                processInput(buf);
            } catch (Throwable t) {
                output.release();
                throw t;
            }
        }
        if (this.cumulation != null && !this.cumulation.isReadable()) {
            this.cumulation.release();
            this.cumulation = null;
        }
        return output;
    }

    /**
     * Process some input. The input buffer ownership <i>does not</i> transfer to this method: If there's still data
     * unread after this method finishes, it will be buffered.
     *
     * @param buf The input buffer
     */
    abstract void processInput(ByteBuf buf) throws DecompressionException;

    /**
     * Produce some output. The input buffer parameter may be read from to consume some more data, but note that this
     * method <i>must</i> return a buffer even if the input is too short.
     *
     * @param buf The input buffer
     */
    abstract ByteBuf processOutput(ByteBuf buf) throws DecompressionException;

    /**
     * Number of buffered bytes.
     *
     * @return Number of buffered bytes
     */
    final int available() {
        return cumulation == null ? 0 : cumulation.readableBytes();
    }

    @Override
    public void close() {
        if (this.cumulation != null) {
            this.cumulation.release();
        }
    }
}
