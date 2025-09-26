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

import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Decompresses a {@link ByteBuf} encoded with the brotli format.
 *
 * See <a href="https://github.com/google/brotli">brotli</a>.
 */
public final class BrotliDecompressor implements Decompressor {
    private final ByteBufAllocator allocator;
    private final DecoderJNI.Wrapper decoder;
    private ByteBuf unusedInput;

    BrotliDecompressor(Builder builder) throws DecompressionException {
        this.allocator = builder.allocator;
        try {
            this.decoder = new DecoderJNI.Wrapper(builder.inputBufferSize);
        } catch (IOException ioe) {
            throw new DecompressionException(ioe);
        }
    }

    @Override
    public Status status() throws DecompressionException {
        while (true) {
            switch (decoder.getStatus()) {
                case ERROR:
                    throw new DecompressionException("Brotli error status");
                case DONE:
                    return Status.COMPLETE;
                case NEEDS_MORE_INPUT:
                    if (unusedInput == null) {
                        return Status.NEED_INPUT;
                    }
                    addSomeInput(unusedInput);
                    if (!unusedInput.isReadable()) {
                        unusedInput.release();
                        unusedInput = null;
                    }
                    break;
                case OK:
                    decoder.push(0);
                    break;
                case NEEDS_MORE_OUTPUT:
                    return Status.NEED_OUTPUT;
                default:
                    throw new AssertionError("Unknown status: " + decoder.getStatus());
            }
        }
    }

    @Override
    public void addInput(ByteBuf buf) throws DecompressionException {
        if (unusedInput != null) {
            throw new IllegalStateException("Not in state NEED_INPUT");
        }
        addSomeInput(buf);
        if (buf.isReadable()) {
            this.unusedInput = buf;
        } else {
            buf.release();
        }
    }

    private void addSomeInput(ByteBuf buf) {
        ByteBuffer decoderInputBuffer = decoder.getInputBuffer();
        decoderInputBuffer.clear();
        int readBytes = readBytes(buf, decoderInputBuffer);
        decoder.push(readBytes);
    }

    @Override
    public void endOfInput() throws DecompressionException {
    }

    @Override
    public ByteBuf takeOutput() throws DecompressionException {
        ByteBuffer nativeBuffer = decoder.pull();
        // nativeBuffer actually wraps brotli's internal buffer so we need to copy its content
        ByteBuf copy = allocator.buffer(nativeBuffer.remaining());
        copy.writeBytes(nativeBuffer);
        return copy;
    }

    @Override
    public void close() {
        decoder.destroy();
        if (unusedInput != null) {
            unusedInput.release();
        }
    }

    private static int readBytes(ByteBuf in, ByteBuffer dest) {
        int limit = Math.min(in.readableBytes(), dest.remaining());
        ByteBuffer slice = dest.slice();
        slice.limit(limit);
        in.readBytes(slice);
        dest.position(dest.position() + limit);
        return limit;
    }

    public static Builder builder(ByteBufAllocator allocator) {
        return new Builder(allocator);
    }

    public static final class Builder extends AbstractDecompressorBuilder {
        private int inputBufferSize = 8 * 1024;

        Builder(ByteBufAllocator allocator) {
            super(allocator);
        }

        /**
         * Desired size of the input buffer in bytes. Default 8K.
         *
         * @param inputBufferSize desired size of the input buffer in bytes
         * @return This builder
         */
        public Builder inputBufferSize(int inputBufferSize) {
            this.inputBufferSize = inputBufferSize;
            return this;
        }

        @Override
        public Decompressor build() throws DecompressionException {
            return new DefensiveDecompressor(new BrotliDecompressor(this));
        }
    }
}
