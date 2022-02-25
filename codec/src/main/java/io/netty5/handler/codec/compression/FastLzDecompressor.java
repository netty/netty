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
 * Uncompresses a {@link ByteBuf} encoded by {@link FastLzCompressor} using the FastLZ algorithm.
 *
 * See <a href="https://github.com/netty/netty/issues/2750">FastLZ format</a>.
 */
public final class FastLzDecompressor implements Decompressor {
    /**
     * Current state of decompression.
     */
    private enum State {
        INIT_BLOCK,
        INIT_BLOCK_PARAMS,
        DECOMPRESS_DATA,
        DONE,
        CORRUPTED,
        CLOSED
    }

    private State currentState = State.INIT_BLOCK;

    /**
     * Underlying checksum calculator in use.
     */
    private final ByteBufChecksum checksum;

    /**
     * Length of current received chunk of data.
     */
    private int chunkLength;

    /**
     * Original of current received chunk of data.
     * It is equal to {@link #chunkLength} for non compressed chunks.
     */
    private int originalLength;

    /**
     * Indicates is this chunk compressed or not.
     */
    private boolean isCompressed;

    /**
     * Indicates is this chunk has checksum or not.
     */
    private boolean hasChecksum;

    /**
     * Checksum value of current received chunk of data which has checksum.
     */
    private int currentChecksum;

    private FastLzDecompressor(Checksum checksum) {
        this.checksum = checksum == null ? null : ByteBufChecksum.wrapChecksum(checksum);
    }

    /**
     * Creates the fastest FastLZ decompressor factory without checksum calculation.
     *
     * @return the factory.
     */
    public static Supplier<FastLzDecompressor> newFactory() {
        return newFactory(false);
    }

    /**
     * Creates a FastLZ decompressor factory with calculation of checksums as specified.
     *
     * @param validateChecksums
     *        If true, the checksum field will be validated against the actual
     *        uncompressed data, and if the checksums do not match, a suitable
     *        {@link DecompressionException} will be thrown.
     *        Note, that in this case decoder will use {@link java.util.zip.Adler32}
     *        as a default checksum calculator.
     * @return the factory.
     */
    public static Supplier<FastLzDecompressor> newFactory(boolean validateChecksums) {
        return newFactory(validateChecksums ? new Adler32() : null);
    }

    /**
     * Creates a FastLZ decompressor factory with specified checksum calculator.
     *
     * @param checksum
     *        the {@link Checksum} instance to use to check data for integrity.
     *        You may set {@code null} if you do not want to validate checksum of each block.
     * @return the factory.
     */
    public static Supplier<FastLzDecompressor> newFactory(Checksum checksum) {
        return () -> new FastLzDecompressor(checksum);
    }

    @Override
    public ByteBuf decompress(ByteBuf in, ByteBufAllocator allocator) throws DecompressionException {
        switch (currentState) {
            case CLOSED:
                throw new DecompressionException("Decompressor closed");
            case DONE:
            case CORRUPTED:
                return Unpooled.EMPTY_BUFFER;
            case INIT_BLOCK:
                if (in.readableBytes() < 4) {
                    return null;
                }

                final int magic = in.readUnsignedMedium();
                if (magic != MAGIC_NUMBER) {
                    streamCorrupted("unexpected block identifier");
                }

                final byte options = in.readByte();
                isCompressed = (options & 0x01) == BLOCK_TYPE_COMPRESSED;
                hasChecksum = (options & 0x10) == BLOCK_WITH_CHECKSUM;

                currentState = State.INIT_BLOCK_PARAMS;
                // fall through
            case INIT_BLOCK_PARAMS:
                if (in.readableBytes() < 2 + (isCompressed ? 2 : 0) + (hasChecksum ? 4 : 0)) {
                    return null;
                }
                currentChecksum = hasChecksum ? in.readInt() : 0;
                chunkLength = in.readUnsignedShort();
                originalLength = isCompressed ? in.readUnsignedShort() : chunkLength;

                currentState = State.DECOMPRESS_DATA;
                // fall through
            case DECOMPRESS_DATA:
                final int chunkLength = this.chunkLength;
                if (in.readableBytes() < chunkLength) {
                    return null;
                }

                final int idx = in.readerIndex();
                final int originalLength = this.originalLength;

                ByteBuf output = null;
                try {
                    if (isCompressed) {

                        output = allocator.buffer(originalLength);
                        int outputOffset = output.writerIndex();
                        final int decompressedBytes = FastLz.decompress(in, idx, chunkLength,
                                output, outputOffset, originalLength);
                        if (originalLength != decompressedBytes) {
                            streamCorrupted(String.format(
                                    "stream corrupted: originalLength(%d) and actual length(%d) mismatch",
                                    originalLength, decompressedBytes));
                        }
                        output.writerIndex(output.writerIndex() + decompressedBytes);
                    } else {
                        output = in.retainedSlice(idx, chunkLength);
                    }

                    final ByteBufChecksum checksum = this.checksum;
                    if (hasChecksum && checksum != null) {
                        checksum.reset();
                        checksum.update(output, output.readerIndex(), output.readableBytes());
                        final int checksumResult = (int) checksum.getValue();
                        if (checksumResult != currentChecksum) {
                            streamCorrupted(String.format(
                                    "stream corrupted: mismatching checksum: %d (expected: %d)",
                                    checksumResult, currentChecksum));
                        }
                    }

                    final ByteBuf data;
                    if (output.readableBytes() > 0) {
                        data = output;
                        output = null;
                    } else {
                        data = null;
                    }
                    in.skipBytes(chunkLength);

                    currentState = State.INIT_BLOCK;
                    return data;
                } finally {
                    if (output != null) {
                        output.release();
                    }
                }
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isFinished() {
        return currentState == State.DONE || currentState == State.CORRUPTED || currentState == State.CLOSED;
    }

    @Override
    public boolean isClosed() {
        return currentState == State.CLOSED;
    }

    @Override
    public void close() {
        currentState = State.CLOSED;
    }

    private void streamCorrupted(String message) {
        currentState = State.CORRUPTED;
        throw new DecompressionException(message);
    }
}
