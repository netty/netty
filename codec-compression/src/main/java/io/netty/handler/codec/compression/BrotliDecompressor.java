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
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Decompresses a {@link ByteBuf} encoded with the brotli format.
 *
 * See <a href="https://github.com/google/brotli">brotli</a>.
 */
@UnstableApi
public final class BrotliDecompressor implements Decompressor {
    private final ByteBufAllocator allocator;
    private DecoderJNI.Wrapper decoder;
    private ByteBuf unusedInput;

    static {
        try {
            Brotli.ensureAvailability();
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    BrotliDecompressor(Builder builder, ByteBufAllocator allocator) throws DecompressionException {
        this.allocator = allocator;
        try {
            this.decoder = new DecoderJNI.Wrapper(builder.inputBufferSize, builder.maxOutputChunkSize);
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
                    if (decoder.hasOutput()) {
                        return Status.NEED_OUTPUT;
                    }
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
        try {
            if (unusedInput != null) {
                throw new IllegalStateException("Not in state NEED_INPUT");
            }
            addSomeInput(buf);
        } catch (Throwable t) {
            buf.release();
            throw t;
        }
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
        if (decoder.getStatus() == DecoderJNI.Status.NEEDS_MORE_INPUT) {
            assert unusedInput == null : "Expected to be in NEED_INPUT state";
            decoder.push(0);
            if (decoder.getStatus() == DecoderJNI.Status.NEEDS_MORE_INPUT) {
                throw new DecompressionException("Truncated brotli stream");
            }
        }
    }

    @Override
    public ByteBuf takeOutput() throws DecompressionException {
        ByteBuffer nativeBuffer = decoder.pull();
        // nativeBuffer actually wraps brotli's internal buffer so we need to copy its content
        // size limited by maxOutputChunkSize
        ByteBuf copy = allocator.buffer(nativeBuffer.remaining());
        copy.writeBytes(nativeBuffer);
        return copy;
    }

    @Override
    public void close() {
        if (decoder != null) {
            decoder.destroy();
            decoder = null;
        }
        if (unusedInput != null) {
            unusedInput.release();
            unusedInput = null;
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

    @UnstableApi
    public static Builder builder() {
        return new Builder();
    }

    @UnstableApi
    public static final class Builder extends AbstractDecompressorBuilder {
        private int inputBufferSize = 8 * 1024;
        private int maxOutputChunkSize = 64 * 1024;

        Builder() {
        }

        /**
         * Desired size of the input buffer in bytes. Default 8K.
         *
         * @param inputBufferSize desired size of the input buffer in bytes
         * @return This builder
         */
        public Builder inputBufferSize(int inputBufferSize) {
            this.inputBufferSize = ObjectUtil.checkPositive(inputBufferSize, "inputBufferSize");
            return this;
        }

        /**
         * Number of bytes of output to consume at a time. Default 64K.
         *
         * @param maxOutputChunkSize Maximum output chunk size
         * @return This builder
         */
        public Builder maxOutputChunkSize(int maxOutputChunkSize) {
            this.maxOutputChunkSize = ObjectUtil.checkPositive(maxOutputChunkSize, "maxOutputChunkSize");
            return this;
        }

        @Override
        public Decompressor build(ByteBufAllocator allocator) throws DecompressionException {
            return new DefensiveDecompressor(new BrotliDecompressor(this, allocator));
        }
    }
}
