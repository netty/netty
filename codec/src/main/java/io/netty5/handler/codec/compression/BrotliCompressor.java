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

import com.aayushatharva.brotli4j.encoder.Encoder;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.ByteBufUtil;
import io.netty5.buffer.Unpooled;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Compress a {@link ByteBuf} with the brotli format.
 *
 * See <a href="https://github.com/google/brotli">brotli</a>.
 */
public final class BrotliCompressor implements Compressor {
    private final Encoder.Parameters parameters;

    private enum State {
        PROCESSING,
        FINISHED,
        CLOSED
    }

    private State state = State.PROCESSING;

    /**
     * Create a new {@link BrotliCompressor} Instance
     *
     * @param parameters {@link Encoder.Parameters} Instance
     */
    private BrotliCompressor(Encoder.Parameters parameters) {
        this.parameters = requireNonNull(parameters, "Parameters");
    }

    /**
     * Create a new {@link BrotliCompressor} factory
     *
     * @param parameters {@link Encoder.Parameters} Instance
     * @return the factory.
     */
    public static Supplier<BrotliCompressor> newFactory(Encoder.Parameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        return () -> new BrotliCompressor(parameters);
    }

    /**
     * Create a new {@link BrotliCompressor} factory
     *
     * @param brotliOptions {@link BrotliOptions} to use.
     * @return the factory.
     */
    public static Supplier<BrotliCompressor> newFactory(BrotliOptions brotliOptions) {
        return newFactory(brotliOptions.parameters());
    }

    /**
     * Create a new {@link BrotliCompressor} factory
     *
     * with {@link Encoder.Parameters#setQuality(int)} set to 4
     * and {@link Encoder.Parameters#setMode(Encoder.Mode)} set to {@link Encoder.Mode#TEXT}
     *
     * @return the factory.
     */
    public static Supplier<BrotliCompressor> newFactory() {
        return newFactory(BrotliOptions.DEFAULT);
    }

    @Override
    public ByteBuf compress(ByteBuf input, ByteBufAllocator allocator) throws CompressionException {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
                return Unpooled.EMPTY_BUFFER;
            case PROCESSING:
                if (!input.isReadable()) {
                    return Unpooled.EMPTY_BUFFER;
                }
                byte[] uncompressed = ByteBufUtil.getBytes(input, input.readerIndex(), input.readableBytes(), false);
                try {
                    byte[] compressed = Encoder.compress(uncompressed, parameters);
                    input.skipBytes(input.readableBytes());
                    return Unpooled.wrappedBuffer(compressed);
                } catch (IOException e) {
                    state = State.FINISHED;
                    throw new CompressionException(e);
                }
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public ByteBuf finish(ByteBufAllocator allocator) {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
            case PROCESSING:
                state = State.FINISHED;
                return Unpooled.EMPTY_BUFFER;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isFinished() {
        return state != State.PROCESSING;
    }

    @Override
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    @Override
    public void close() {
        state = State.CLOSED;
    }
}
