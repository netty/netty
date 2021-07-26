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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.FastLz.*;

/**
 * Uncompresses a {@link ByteBuf} encoded by {@link FastLzFrameEncoder} using the FastLZ algorithm.
 *
 * See <a href="https://github.com/netty/netty/issues/2750">FastLZ format</a>.
 */
public class FastLzFrameDecoder extends ByteToMessageDecoder {
    /**
     * Current state of decompression.
     */
    private enum State {
        INIT_BLOCK,
        INIT_BLOCK_PARAMS,
        DECOMPRESS_DATA,
        CORRUPTED
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

    /**
     * Creates the fastest FastLZ decoder without checksum calculation.
     */
    public FastLzFrameDecoder() {
        this(false);
    }

    /**
     * Creates a FastLZ decoder with calculation of checksums as specified.
     *
     * @param validateChecksums
     *        If true, the checksum field will be validated against the actual
     *        uncompressed data, and if the checksums do not match, a suitable
     *        {@link DecompressionException} will be thrown.
     *        Note, that in this case decoder will use {@link java.util.zip.Adler32}
     *        as a default checksum calculator.
     */
    public FastLzFrameDecoder(boolean validateChecksums) {
        this(validateChecksums ? new Adler32() : null);
    }

    /**
     * Creates a FastLZ decoder with specified checksum calculator.
     *
     * @param checksum
     *        the {@link Checksum} instance to use to check data for integrity.
     *        You may set {@code null} if you do not want to validate checksum of each block.
     */
    public FastLzFrameDecoder(Checksum checksum) {
        this.checksum = checksum == null ? null : ByteBufChecksum.wrapChecksum(checksum);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            switch (currentState) {
            case INIT_BLOCK:
                if (in.readableBytes() < 4) {
                    break;
                }

                final int magic = in.readUnsignedMedium();
                if (magic != MAGIC_NUMBER) {
                    throw new DecompressionException("unexpected block identifier");
                }

                final byte options = in.readByte();
                isCompressed = (options & 0x01) == BLOCK_TYPE_COMPRESSED;
                hasChecksum = (options & 0x10) == BLOCK_WITH_CHECKSUM;

                currentState = State.INIT_BLOCK_PARAMS;
                // fall through
            case INIT_BLOCK_PARAMS:
                if (in.readableBytes() < 2 + (isCompressed ? 2 : 0) + (hasChecksum ? 4 : 0)) {
                    break;
                }
                currentChecksum = hasChecksum ? in.readInt() : 0;
                chunkLength = in.readUnsignedShort();
                originalLength = isCompressed ? in.readUnsignedShort() : chunkLength;

                currentState = State.DECOMPRESS_DATA;
                // fall through
            case DECOMPRESS_DATA:
                final int chunkLength = this.chunkLength;
                if (in.readableBytes() < chunkLength) {
                    break;
                }

                final int idx = in.readerIndex();
                final int originalLength = this.originalLength;

                ByteBuf output = null;

                try {
                    if (isCompressed) {

                        output = ctx.alloc().buffer(originalLength);
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

                    if (output.readableBytes() > 0) {
                        out.add(output);
                    } else {
                        output.release();
                    }
                    output = null;
                    in.skipBytes(chunkLength);

                    currentState = State.INIT_BLOCK;
                } finally {
                    if (output != null) {
                        output.release();
                    }
                }
                break;
            case CORRUPTED:
                in.skipBytes(in.readableBytes());
                break;
            default:
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            currentState = State.CORRUPTED;
            throw e;
        }
    }
}
