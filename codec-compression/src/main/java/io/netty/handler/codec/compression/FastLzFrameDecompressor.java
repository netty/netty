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

import java.util.zip.Adler32;
import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.FastLz.BLOCK_TYPE_COMPRESSED;
import static io.netty.handler.codec.compression.FastLz.BLOCK_WITH_CHECKSUM;
import static io.netty.handler.codec.compression.FastLz.MAGIC_NUMBER;
import static io.netty.handler.codec.compression.FastLz.decompress;

/**
 * Uncompresses a {@link ByteBuf} encoded by {@link FastLzFrameEncoder} using the FastLZ algorithm.
 *
 * See <a href="https://github.com/netty/netty/issues/2750">FastLZ format</a>.
 */
public final class FastLzFrameDecompressor extends InputBufferingDecompressor {
    /**
     * Current state of decompression.
     */
    private enum State {
        INIT_BLOCK,
        INIT_BLOCK_PARAMS,
        DECOMPRESS_DATA,
        COMPLETE
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

    FastLzFrameDecompressor(Builder builder, ByteBufAllocator allocator) {
        super(allocator);
        this.checksum = builder.checksum == null ? null : ByteBufChecksum.wrapChecksum(builder.checksum);
    }

    @Override
    void processInput(ByteBuf buf) throws DecompressionException {
        switch (currentState) {
            case INIT_BLOCK:
                if (buf.readableBytes() < 4) {
                    break;
                }

                final int magic = buf.readUnsignedMedium();
                if (magic != MAGIC_NUMBER) {
                    throw new DecompressionException("unexpected block identifier");
                }

                final byte options = buf.readByte();
                isCompressed = (options & 0x01) == BLOCK_TYPE_COMPRESSED;
                hasChecksum = (options & 0x10) == BLOCK_WITH_CHECKSUM;

                currentState = State.INIT_BLOCK_PARAMS;
                // fall through
            case INIT_BLOCK_PARAMS:
                if (buf.readableBytes() < 2 + (isCompressed ? 2 : 0) + (hasChecksum ? 4 : 0)) {
                    break;
                }
                currentChecksum = hasChecksum ? buf.readInt() : 0;
                chunkLength = buf.readUnsignedShort();
                originalLength = isCompressed ? buf.readUnsignedShort() : chunkLength;

                currentState = State.DECOMPRESS_DATA;
                // fall through
            case DECOMPRESS_DATA:
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public Status status() throws DecompressionException {
        switch (currentState) {
            case INIT_BLOCK:
            case INIT_BLOCK_PARAMS:
                return Status.NEED_INPUT;
            case DECOMPRESS_DATA:
                if (available() < chunkLength) {
                    return Status.NEED_INPUT;
                } else {
                    return Status.NEED_OUTPUT;
                }
            case COMPLETE:
                return Status.COMPLETE;
            default:
                throw new AssertionError("Unknown state: " + currentState);
        }
    }

    @Override
    public void endOfInput() throws DecompressionException {
        if (currentState != State.INIT_BLOCK) {
            throw new DecompressionException("Unexpected end of input");
        }
        currentState = State.COMPLETE;
    }

    @Override
    ByteBuf processOutput(ByteBuf in) throws DecompressionException {
        final int chunkLength = this.chunkLength;
        if (in.readableBytes() < chunkLength) {
            throw new IllegalStateException("Not in state NEED_OUTPUT");
        }

        final int idx = in.readerIndex();
        final int originalLength = this.originalLength;

        ByteBuf output = null;

        try {
            if (isCompressed) {
                output = allocator.buffer(originalLength);
                int outputOffset = output.writerIndex();
                final int decompressedBytes = decompress(in, idx, chunkLength,
                        output, outputOffset, originalLength);
                if (originalLength != decompressedBytes) {
                    throw new DecompressionException(String.format(
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
                    throw new DecompressionException(String.format(
                            "stream corrupted: mismatching checksum: %d (expected: %d)",
                            checksumResult, currentChecksum));
                }
            }

            in.skipBytes(chunkLength);

            currentState = State.INIT_BLOCK;
            ByteBuf b = output;
            output = null;
            return b;
        } finally {
            if (output != null) {
                output.release();
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDecompressorBuilder {
        private Checksum checksum;

        Builder() {
        }

        /**
         * A checksum to use to validate each block. Defaults to no checksum validation.
         *
         * @param checksum The checksum to use for validation
         * @return This builder
         */
        public Builder checksum(Checksum checksum) {
            this.checksum = checksum;
            return this;
        }

        /**
         * Enable validation using the default checksum, Adler32.
         *
         * @return This builder
         */
        public Builder defaultChecksum() {
            return checksum(new Adler32());
        }

        @Override
        public Decompressor build(ByteBufAllocator allocator) throws DecompressionException {
            return new DefensiveDecompressor(new FastLzFrameDecompressor(this, allocator));
        }
    }
}
