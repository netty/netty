/*
 * Copyright 2026 The Netty Project
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
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;

import static io.netty.handler.codec.compression.Bzip2Constants.HUFFMAN_SYMBOL_RUNB;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Bzip2BlockDecompressorTest {

    /**
     * Regression test for an integer overflow in {@link Bzip2BlockDecompressor#decodeHuffmanData}.
     *
     * <p>{@code repeatCount} accumulates RUNA/RUNB run lengths and doubles on every symbol with no
     * ceiling. The guard that protects the output block, {@code bwtBlockLength + repeatCount >
     * streamBlockSize}, adds before comparing, so once a run drives {@code repeatCount} close to
     * {@link Integer#MAX_VALUE} the sum wraps negative and the guard is bypassed, letting the decoder
     * attempt to write a run far larger than the block it was validating against.
     *
     * <p>This test drives {@link Bzip2BlockDecompressor} directly, bypassing the full bzip2 header
     * parsing, by building a minimal one-table Huffman alphabet of 4 symbols (RUNA, RUNB, one literal,
     * and end-of-block) and feeding it: two literals (so {@code bwtBlockLength == 2}), 30 consecutive
     * RUNB symbols (so {@code repeatCount == 2^31 - 2 == 2147483646}, still positive), then
     * end-of-block. {@code 2 + 2147483646} overflows to {@code Integer.MIN_VALUE}, which is not
     * greater than the tiny declared block size used here, so the unfixed guard lets execution reach
     * the write loop instead of raising {@link DecompressionException}.
     */
    @Test
    public void testHuffmanRunLengthOverflowDoesNotBypassBlockSizeCheck() {
        final int totalTables = 1;
        final int alphabetSize = 4; // RUNA=0, RUNB=1, literal=2, EOB=3
        final int literalSymbol = 2;
        final int endOfBlockSymbol = 3;
        final int streamBlockSize = 5;

        final BitWriter writer = new BitWriter();
        writer.writeBits(2, literalSymbol);
        writer.writeBits(2, literalSymbol);
        for (int i = 0; i < 30; i++) {
            writer.writeBits(2, HUFFMAN_SYMBOL_RUNB);
        }
        writer.writeBits(2, endOfBlockSymbol);
        // Trailing padding so hasReadableBits() lookahead never runs dry before EOB is decoded.
        for (int i = 0; i < 8; i++) {
            writer.writeBits(8, 0);
        }

        ByteBuf input = writer.finish();
        final Bzip2BitReader reader = new Bzip2BitReader();
        reader.setByteBuf(input);

        final Bzip2HuffmanStageDecoder huffmanDecoder =
            new Bzip2HuffmanStageDecoder(reader, totalTables, alphabetSize);
        Arrays.fill(huffmanDecoder.tableCodeLengths[0], 0, alphabetSize, (byte) 2);
        huffmanDecoder.selectors = new byte[] { 0 };
        huffmanDecoder.createHuffmanDecodingTables();

        final Bzip2BlockDecompressor decompressor =
            new Bzip2BlockDecompressor(streamBlockSize, 0, false, 0, reader);
        decompressor.huffmanEndOfBlockSymbol = endOfBlockSymbol;

        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() {
                decompressor.decodeHuffmanData(huffmanDecoder);
            }
        }, "repeatCount overflow must not bypass the block size check");

        input.release();
    }

    /**
     * Minimal MSB-first bit packer mirroring {@link Bzip2BitReader}'s unpacking order, used to build
     * synthetic Huffman-coded symbol streams for {@link #testHuffmanRunLengthOverflowDoesNotBypassBlockSizeCheck()}.
     */
    private static final class BitWriter {
        private final ByteBuf buf = Unpooled.buffer();
        private long bitBuf;
        private int bitCount;

        void writeBits(int count, int value) {
            bitBuf = bitBuf << count | value & ((1L << count) - 1);
            bitCount += count;
            while (bitCount >= 8) {
                bitCount -= 8;
                buf.writeByte((int) (bitBuf >>> bitCount) & 0xFF);
            }
        }

        ByteBuf finish() {
            if (bitCount > 0) {
                buf.writeByte((int) (bitBuf << 8 - bitCount) & 0xFF);
                bitCount = 0;
            }
            return buf;
        }
    }
}
