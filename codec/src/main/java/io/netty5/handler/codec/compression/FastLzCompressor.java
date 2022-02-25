/*
 * Copyright 2014 The Netty Project
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
import io.netty5.buffer.Unpooled;

import java.util.function.Supplier;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import static io.netty5.handler.codec.compression.FastLz.*;

/**
 * Compresses a {@link ByteBuf} using the FastLZ algorithm.
 *
 * See <a href="https://github.com/netty/netty/issues/2750">FastLZ format</a>.
 */
public final class FastLzCompressor implements Compressor {
    /**
     * Compression level.
     */
    private final int level;

    /**
     * Underlying checksum calculator in use.
     */
    private final ByteBufChecksum checksum;

    private enum State {
        PROCESSING,
        FINISHED,
        CLOSED
    }

    private State state = State.PROCESSING;

    /**
     * Creates a FastLZ encoder with specified compression level and checksum calculator.
     *
     * @param level supports only these values:
     *        0 - Encoder will choose level automatically depending on the length of the input buffer.
     *        1 - Level 1 is the fastest compression and generally useful for short data.
     *        2 - Level 2 is slightly slower but it gives better compression ratio.
     * @param checksum
     *        the {@link Checksum} instance to use to check data for integrity.
     *        You may set {@code null} if you don't want to validate checksum of each block.
     */
    private FastLzCompressor(int level, Checksum checksum) {
        this.level = level;
        this.checksum = checksum == null ? null : ByteBufChecksum.wrapChecksum(checksum);
    }

    /**
     * Creates a FastLZ compressor factory without checksum calculator and with auto detection of compression level.
     *
     * @return the factory.
     */
    public static Supplier<FastLzCompressor> newFactory() {
        return newFactory(LEVEL_AUTO, null);
    }

    /**
     * Creates a FastLZ compressor factory with specified compression level and without checksum calculator.
     *
     * @param level supports only these values:
     *        0 - Encoder will choose level automatically depending on the length of the input buffer.
     *        1 - Level 1 is the fastest compression and generally useful for short data.
     *        2 - Level 2 is slightly slower but it gives better compression ratio.
     * @return the factory.
     */
    public static Supplier<FastLzCompressor> newFactory(int level) {
        return newFactory(level, null);
    }

    /**
     * Creates a FastLZ compressor factory with auto detection of compression
     * level and calculation of checksums as specified.
     *
     * @param validateChecksums
     *        If true, the checksum of each block will be calculated and this value
     *        will be added to the header of block.
     *        By default {@link FastLzCompressor} uses {@link java.util.zip.Adler32}
     *        for checksum calculation.
     * @return the factory.
     */
    public static Supplier<FastLzCompressor> newFactory(boolean validateChecksums) {
        return newFactory(LEVEL_AUTO, validateChecksums ? new Adler32() : null);
    }

    /**
     * Creates a FastLZ compressor factory with specified compression level and checksum calculator.
     *
     * @param level supports only these values:
     *        0 - Encoder will choose level automatically depending on the length of the input buffer.
     *        1 - Level 1 is the fastest compression and generally useful for short data.
     *        2 - Level 2 is slightly slower but it gives better compression ratio.
     * @param checksum
     *        the {@link Checksum} instance to use to check data for integrity.
     *        You may set {@code null} if you don't want to validate checksum of each block.
     * @return the factory.
     */
    public static Supplier<FastLzCompressor> newFactory(int level, Checksum checksum) {
        if (level != LEVEL_AUTO && level != LEVEL_1 && level != LEVEL_2) {
            throw new IllegalArgumentException(String.format(
                    "level: %d (expected: %d or %d or %d)", level, LEVEL_AUTO, LEVEL_1, LEVEL_2));
        }
        return () -> new FastLzCompressor(level, checksum);
    }

    @Override
    public ByteBuf compress(ByteBuf in, ByteBufAllocator allocator) throws CompressionException {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
                return Unpooled.EMPTY_BUFFER;
            case PROCESSING:
                return compressData(in, allocator);
            default:
                throw new IllegalStateException();
        }
    }

    private ByteBuf compressData(ByteBuf in, ByteBufAllocator allocator) {
        final ByteBufChecksum checksum = this.checksum;
        ByteBuf out = allocator.buffer();
        for (;;) {
            if (!in.isReadable()) {
                return out;
            }
            final int idx = in.readerIndex();
            final int length = Math.min(in.readableBytes(), MAX_CHUNK_LENGTH);

            final int outputIdx = out.writerIndex();
            out.setMedium(outputIdx, MAGIC_NUMBER);
            int outputOffset = outputIdx + CHECKSUM_OFFSET + (checksum != null ? 4 : 0);

            final byte blockType;
            final int chunkLength;
            if (length < MIN_LENGTH_TO_COMPRESSION) {
                blockType = BLOCK_TYPE_NON_COMPRESSED;

                out.ensureWritable(outputOffset + 2 + length);
                final int outputPtr = outputOffset + 2;

                if (checksum != null) {
                    checksum.reset();
                    checksum.update(in, idx, length);
                    out.setInt(outputIdx + CHECKSUM_OFFSET, (int) checksum.getValue());
                }
                out.setBytes(outputPtr, in, idx, length);
                chunkLength = length;
            } else {
                // try to compress
                if (checksum != null) {
                    checksum.reset();
                    checksum.update(in, idx, length);
                    out.setInt(outputIdx + CHECKSUM_OFFSET, (int) checksum.getValue());
                }

                final int maxOutputLength = calculateOutputBufferLength(length);
                out.ensureWritable(outputOffset + 4 + maxOutputLength);
                final int outputPtr = outputOffset + 4;
                final int compressedLength =
                        FastLz.compress(in, in.readerIndex(), length, out, outputPtr, level);

                if (compressedLength < length) {
                    blockType = BLOCK_TYPE_COMPRESSED;
                    chunkLength = compressedLength;

                    out.setShort(outputOffset, chunkLength);
                    outputOffset += 2;
                } else {
                    blockType = BLOCK_TYPE_NON_COMPRESSED;
                    out.setBytes(outputOffset + 2, in, idx, length);
                    chunkLength = length;
                }
            }
            out.setShort(outputOffset, length);

            out.setByte(outputIdx + OPTIONS_OFFSET,
                    blockType | (checksum != null ? BLOCK_WITH_CHECKSUM : BLOCK_WITHOUT_CHECKSUM));
            out.writerIndex(outputOffset + 2 + chunkLength);
            in.skipBytes(length);
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
