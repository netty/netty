/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.zip.Adler32;
import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.FastLz.*;

/**
 * Compresses a {@link ByteBuf} using the FastLZ algorithm.
 *
 * See <a href="https://github.com/netty/netty/issues/2750">FastLZ format</a>.
 */
public class FastLzFrameEncoder extends MessageToByteEncoder<ByteBuf> {
    /**
     * Compression level.
     */
    private final int level;

    /**
     * Underlying checksum calculator in use.
     */
    private final Checksum checksum;

    /**
     * Creates a FastLZ encoder without checksum calculator and with auto detection of compression level.
     */
    public FastLzFrameEncoder() {
        this(LEVEL_AUTO, null);
    }

    /**
     * Creates a FastLZ encoder with specified compression level and without checksum calculator.
     *
     * @param level supports only these values:
     *        0 - Encoder will choose level automatically depending on the length of the input buffer.
     *        1 - Level 1 is the fastest compression and generally useful for short data.
     *        2 - Level 2 is slightly slower but it gives better compression ratio.
     */
    public FastLzFrameEncoder(int level) {
        this(level, null);
    }

    /**
     * Creates a FastLZ encoder with auto detection of compression
     * level and calculation of checksums as specified.
     *
     * @param validateChecksums
     *        If true, the checksum of each block will be calculated and this value
     *        will be added to the header of block.
     *        By default {@link FastLzFrameEncoder} uses {@link java.util.zip.Adler32}
     *        for checksum calculation.
     */
    public FastLzFrameEncoder(boolean validateChecksums) {
        this(LEVEL_AUTO, validateChecksums ? new Adler32() : null);
    }

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
    public FastLzFrameEncoder(int level, Checksum checksum) {
        super(false);
        if (level != LEVEL_AUTO && level != LEVEL_1 && level != LEVEL_2) {
            throw new IllegalArgumentException(String.format(
                    "level: %d (expected: %d or %d or %d)", level, LEVEL_AUTO, LEVEL_1, LEVEL_2));
        }
        this.level = level;
        this.checksum = checksum;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        final Checksum checksum = this.checksum;

        for (;;) {
            if (!in.isReadable()) {
                return;
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
                final byte[] output = out.array();
                final int outputPtr = out.arrayOffset() + outputOffset + 2;

                if (checksum != null) {
                    final byte[] input;
                    final int inputPtr;
                    if (in.hasArray()) {
                        input = in.array();
                        inputPtr = in.arrayOffset() + idx;
                    } else {
                        input = new byte[length];
                        in.getBytes(idx, input);
                        inputPtr = 0;
                    }

                    checksum.reset();
                    checksum.update(input, inputPtr, length);
                    out.setInt(outputIdx + CHECKSUM_OFFSET, (int) checksum.getValue());

                    System.arraycopy(input, inputPtr, output, outputPtr, length);
                } else {
                    in.getBytes(idx, output, outputPtr, length);
                }
                chunkLength = length;
            } else {
                // try to compress
                final byte[] input;
                final int inputPtr;
                if (in.hasArray()) {
                    input = in.array();
                    inputPtr = in.arrayOffset() + idx;
                } else {
                    input = new byte[length];
                    in.getBytes(idx, input);
                    inputPtr = 0;
                }

                if (checksum != null) {
                    checksum.reset();
                    checksum.update(input, inputPtr, length);
                    out.setInt(outputIdx + CHECKSUM_OFFSET, (int) checksum.getValue());
                }

                final int maxOutputLength = calculateOutputBufferLength(length);
                out.ensureWritable(outputOffset + 4 + maxOutputLength);
                final byte[] output = out.array();
                final int outputPtr = out.arrayOffset() + outputOffset + 4;
                final int compressedLength = compress(input, inputPtr, length, output, outputPtr, level);
                if (compressedLength < length) {
                    blockType = BLOCK_TYPE_COMPRESSED;
                    chunkLength = compressedLength;

                    out.setShort(outputOffset, chunkLength);
                    outputOffset += 2;
                } else {
                    blockType = BLOCK_TYPE_NON_COMPRESSED;
                    System.arraycopy(input, inputPtr, output, outputPtr - 2, length);
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
}
