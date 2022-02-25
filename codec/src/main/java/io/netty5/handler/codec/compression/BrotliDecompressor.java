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

import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;
import io.netty5.util.internal.ObjectUtil;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Uncompresses a {@link ByteBuf} encoded with the brotli format.
 *
 * See <a href="https://github.com/google/brotli">brotli</a>.
 */
public final class BrotliDecompressor implements Decompressor {

    static {
        try {
            Brotli.ensureAvailability();
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    private enum State {
        PROCESSING,
        FINISHED,
        CLOSED
    };

    private State state = State.PROCESSING;

    private final DecoderJNI.Wrapper decoder;

    /**
     * Creates a new factory for {@link BrotliDecompressor}s with a default 8kB input buffer
     *
     * @return the factory.
     */
    public static Supplier<BrotliDecompressor> newFactory() {
        return newFactory(8 * 1024);
    }

    /**
     * Creates a new factory for {@link BrotliDecompressor}s.
     *
     * @param inputBufferSize desired size of the input buffer in bytes
     * @return the factory.
     */
    public static Supplier<BrotliDecompressor> newFactory(int inputBufferSize) {
        ObjectUtil.checkPositive(inputBufferSize, "inputBufferSize");
        return () -> {
            try {
                return new BrotliDecompressor(inputBufferSize);
            } catch (IOException e) {
                throw new DecompressionException(e);
            }
        };
    }

    /**
     * Creates a new BrotliDecoder
     *
     * @param inputBufferSize desired size of the input buffer in bytes
     */
    private BrotliDecompressor(int inputBufferSize) throws IOException {
        decoder = new DecoderJNI.Wrapper(inputBufferSize);
    }

    private ByteBuf pull(ByteBufAllocator alloc) {
        ByteBuffer nativeBuffer = decoder.pull();
        // nativeBuffer actually wraps brotli's internal buffer so we need to copy its content
        ByteBuf copy = alloc.buffer(nativeBuffer.remaining());
        copy.writeBytes(nativeBuffer);
        return copy;
    }

    private static int readBytes(ByteBuf in, ByteBuffer dest) {
        int limit = Math.min(in.readableBytes(), dest.remaining());
        ByteBuffer slice = dest.slice();
        slice.limit(limit);
        in.readBytes(slice);
        dest.position(dest.position() + limit);
        return limit;
    }

    @Override
    public ByteBuf decompress(ByteBuf input, ByteBufAllocator allocator) throws DecompressionException {
        switch (state) {
            case CLOSED:
                throw new DecompressionException("Decompressor closed");
            case FINISHED:
                return Unpooled.EMPTY_BUFFER;
            case PROCESSING:
                for (;;) {
                    switch (decoder.getStatus()) {
                        case DONE:
                            state = State.FINISHED;
                            return null;
                        case OK:
                            decoder.push(0);
                            break;
                        case NEEDS_MORE_INPUT:
                            if (decoder.hasOutput()) {
                                return pull(allocator);
                            }

                            if (!input.isReadable()) {
                                return null;
                            }

                            ByteBuffer decoderInputBuffer = decoder.getInputBuffer();
                            decoderInputBuffer.clear();
                            int readBytes = readBytes(input, decoderInputBuffer);
                            decoder.push(readBytes);
                            break;
                        case NEEDS_MORE_OUTPUT:
                            return pull(allocator);
                        default:
                            state = State.FINISHED;
                            throw new DecompressionException("Brotli stream corrupted");
                    }
                }
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
        if (state != State.FINISHED) {
            state = State.FINISHED;
            decoder.destroy();
        }
    }
}
