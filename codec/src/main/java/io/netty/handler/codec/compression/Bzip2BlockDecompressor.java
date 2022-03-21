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

import static io.netty.handler.codec.compression.Bzip2Constants.HUFFMAN_DECODE_MAX_CODE_LENGTH;
import static io.netty.handler.codec.compression.Bzip2Constants.HUFFMAN_SYMBOL_RUNA;
import static io.netty.handler.codec.compression.Bzip2Constants.HUFFMAN_SYMBOL_RUNB;
import static io.netty.handler.codec.compression.Bzip2Constants.MAX_BLOCK_LENGTH;

/**
 * Reads and decompresses a single Bzip2 block.<br><br>
 *
 * Block decoding consists of the following stages:<br>
 * 1. Read block header<br>
 * 2. Read Huffman tables<br>
 * 3. Read and decode Huffman encoded data - {@link #decodeHuffmanData(Bzip2HuffmanStageDecoder)}<br>
 * 4. Run-Length Decoding[2] - {@link #decodeHuffmanData(Bzip2HuffmanStageDecoder)}<br>
 * 5. Inverse Move To Front Transform - {@link #decodeHuffmanData(Bzip2HuffmanStageDecoder)}<br>
 * 6. Inverse Burrows Wheeler Transform - {@link #initialiseInverseBWT()}<br>
 * 7. Run-Length Decoding[1] - {@link #read()}<br>
 * 8. Optional Block De-Randomisation - {@link #read()} (through {@link #decodeNextBWTByte()})
 */
final class Bzip2BlockDecompressor {
    /**
     * A reader that provides bit-level reads.
     */
    private final Bzip2BitReader reader;

    /**
     * Calculates the block CRC from the fully decoded bytes of the block.
     */
    private final Crc32 crc = new Crc32();

    /**
     * The CRC of the current block as read from the block header.
     */
    private final int blockCRC;

    /**
     * {@code true} if the current block is randomised, otherwise {@code false}.
     */
    private final boolean blockRandomised;

    /* Huffman Decoding stage */
    /**
     * The end-of-block Huffman symbol. Decoding of the block ends when this is encountered.
     */
    int huffmanEndOfBlockSymbol;

    /**
     * Bitmap, of ranges of 16 bytes, present/not present.
     */
    int huffmanInUse16;

    /**
     * A map from Huffman symbol index to output character. Some types of data (e.g. ASCII text)
     * may contain only a limited number of byte values; Huffman symbols are only allocated to
     * those values that actually occur in the uncompressed data.
     */
    final byte[] huffmanSymbolMap = new byte[256];

    /* Move To Front stage */
    /**
     * Counts of each byte value within the {@link Bzip2BlockDecompressor#huffmanSymbolMap} data.
     * Collected at the Move To Front stage, consumed by the Inverse Burrows Wheeler Transform stage.
     */
    private final int[] bwtByteCounts = new int[256];

    /**
     * The Burrows-Wheeler Transform processed data. Read at the Move To Front stage, consumed by the
     * Inverse Burrows Wheeler Transform stage.
     */
    private final byte[] bwtBlock;

    /**
     * Starting pointer into BWT for after untransform.
     */
    private final int bwtStartPointer;

    /* Inverse Burrows-Wheeler Transform stage */
    /**
     * At each position contains the union of :-
     *   An output character (8 bits)
     *   A pointer from each position to its successor (24 bits, left shifted 8 bits)
     * As the pointer cannot exceed the maximum block size of 900k, 24 bits is more than enough to
     * hold it; Folding the character data into the spare bits while performing the inverse BWT,
     * when both pieces of information are available, saves a large number of memory accesses in
     * the final decoding stages.
     */
    private int[] bwtMergedPointers;

    /**
     * The current merged pointer into the Burrow-Wheeler Transform array.
     */
    private int bwtCurrentMergedPointer;

    /**
     * The actual length in bytes of the current block at the Inverse Burrows Wheeler Transform
     * stage (before final Run-Length Decoding).
     */
    private int bwtBlockLength;

    /**
     * The number of output bytes that have been decoded up to the Inverse Burrows Wheeler Transform stage.
     */
    private int bwtBytesDecoded;

    /* Run-Length Encoding and Random Perturbation stage */
    /**
     * The most recently RLE decoded byte.
     */
    private int rleLastDecodedByte = -1;

    /**
     * The number of previous identical output bytes decoded. After 4 identical bytes, the next byte
     * decoded is an RLE repeat count.
     */
    private int rleAccumulator;

    /**
     * The RLE repeat count of the current decoded byte. When this reaches zero, a new byte is decoded.
     */
    private int rleRepeat;

    /**
     * If the current block is randomised, the position within the RNUMS randomisation array.
     */
    private int randomIndex;

    /**
     * If the current block is randomised, the remaining count at the current RNUMS position.
     */
    private int randomCount = Bzip2Rand.rNums(0) - 1;

    /**
     * Table for Move To Front transformations.
     */
    private final Bzip2MoveToFrontTable symbolMTF = new Bzip2MoveToFrontTable();

    // This variables is used to save current state if we haven't got enough readable bits
    private int repeatCount;
    private int repeatIncrement = 1;
    private int mtfValue;

    Bzip2BlockDecompressor(final int blockSize, final int blockCRC, final boolean blockRandomised,
                           final int bwtStartPointer, final Bzip2BitReader reader) {

        bwtBlock = new byte[blockSize];

        this.blockCRC = blockCRC;
        this.blockRandomised = blockRandomised;
        this.bwtStartPointer = bwtStartPointer;

        this.reader = reader;
    }

    /**
     * Reads the Huffman encoded data from the input stream, performs Run-Length Decoding and
     * applies the Move To Front transform to reconstruct the Burrows-Wheeler Transform array.
     */
    boolean decodeHuffmanData(final Bzip2HuffmanStageDecoder huffmanDecoder) {
        final Bzip2BitReader reader = this.reader;
        final byte[] bwtBlock = this.bwtBlock;
        final byte[] huffmanSymbolMap = this.huffmanSymbolMap;
        final int streamBlockSize = this.bwtBlock.length;
        final int huffmanEndOfBlockSymbol = this.huffmanEndOfBlockSymbol;
        final int[] bwtByteCounts = this.bwtByteCounts;
        final Bzip2MoveToFrontTable symbolMTF = this.symbolMTF;

        int bwtBlockLength = this.bwtBlockLength;
        int repeatCount = this.repeatCount;
        int repeatIncrement = this.repeatIncrement;
        int mtfValue = this.mtfValue;

        for (;;) {
            if (!reader.hasReadableBits(HUFFMAN_DECODE_MAX_CODE_LENGTH)) {
                this.bwtBlockLength = bwtBlockLength;
                this.repeatCount = repeatCount;
                this.repeatIncrement = repeatIncrement;
                this.mtfValue = mtfValue;
                return false;
            }
            final int nextSymbol = huffmanDecoder.nextSymbol();

            if (nextSymbol == HUFFMAN_SYMBOL_RUNA) {
                repeatCount += repeatIncrement;
                repeatIncrement <<= 1;
            } else if (nextSymbol == HUFFMAN_SYMBOL_RUNB) {
                repeatCount += repeatIncrement << 1;
                repeatIncrement <<= 1;
            } else {
                if (repeatCount > 0) {
                    if (bwtBlockLength + repeatCount > streamBlockSize) {
                        throw new DecompressionException("block exceeds declared block size");
                    }
                    final byte nextByte = huffmanSymbolMap[mtfValue];
                    bwtByteCounts[nextByte & 0xff] += repeatCount;
                    while (--repeatCount >= 0) {
                        bwtBlock[bwtBlockLength++] = nextByte;
                    }

                    repeatCount = 0;
                    repeatIncrement = 1;
                }

                if (nextSymbol == huffmanEndOfBlockSymbol) {
                    break;
                }

                if (bwtBlockLength >= streamBlockSize) {
                    throw new DecompressionException("block exceeds declared block size");
                }

                mtfValue = symbolMTF.indexToFront(nextSymbol - 1) & 0xff;

                final byte nextByte = huffmanSymbolMap[mtfValue];
                bwtByteCounts[nextByte & 0xff]++;
                bwtBlock[bwtBlockLength++] = nextByte;
            }
        }
        if (bwtBlockLength > MAX_BLOCK_LENGTH) {
            throw new DecompressionException("block length exceeds max block length: "
                    + bwtBlockLength + " > " + MAX_BLOCK_LENGTH);
        }

        this.bwtBlockLength = bwtBlockLength;
        initialiseInverseBWT();
        return true;
    }

    /**
     * Set up the Inverse Burrows-Wheeler Transform merged pointer array.
     */
    private void initialiseInverseBWT() {
        final int bwtStartPointer = this.bwtStartPointer;
        final byte[] bwtBlock  = this.bwtBlock;
        final int[] bwtMergedPointers = new int[bwtBlockLength];
        final int[] characterBase = new int[256];

        if (bwtStartPointer < 0 || bwtStartPointer >= bwtBlockLength) {
            throw new DecompressionException("start pointer invalid");
        }

        // Cumulative character counts
        System.arraycopy(bwtByteCounts, 0, characterBase, 1, 255);
        for (int i = 2; i <= 255; i++) {
            characterBase[i] += characterBase[i - 1];
        }

        // Merged-Array Inverse Burrows-Wheeler Transform
        // Combining the output characters and forward pointers into a single array here, where we
        // have already read both of the corresponding values, cuts down on memory accesses in the
        // final walk through the array
        for (int i = 0; i < bwtBlockLength; i++) {
            int value = bwtBlock[i] & 0xff;
            bwtMergedPointers[characterBase[value]++] = (i << 8) + value;
        }

        this.bwtMergedPointers = bwtMergedPointers;
        bwtCurrentMergedPointer = bwtMergedPointers[bwtStartPointer];
    }

    /**
     * Decodes a byte from the final Run-Length Encoding stage, pulling a new byte from the
     * Burrows-Wheeler Transform stage when required.
     * @return The decoded byte, or -1 if there are no more bytes
     */
    public int read() {
        while (rleRepeat < 1) {
            if (bwtBytesDecoded == bwtBlockLength) {
                return -1;
            }

            int nextByte = decodeNextBWTByte();
            if (nextByte != rleLastDecodedByte) {
                // New byte, restart accumulation
                rleLastDecodedByte = nextByte;
                rleRepeat = 1;
                rleAccumulator = 1;
                crc.updateCRC(nextByte);
            } else {
                if (++rleAccumulator == 4) {
                    // Accumulation complete, start repetition
                    int rleRepeat = decodeNextBWTByte() + 1;
                    this.rleRepeat = rleRepeat;
                    rleAccumulator = 0;
                    crc.updateCRC(nextByte, rleRepeat);
                } else {
                    rleRepeat = 1;
                    crc.updateCRC(nextByte);
                }
            }
        }
        rleRepeat--;

        return rleLastDecodedByte;
    }

    /**
     * Decodes a byte from the Burrows-Wheeler Transform stage. If the block has randomisation
     * applied, reverses the randomisation.
     * @return The decoded byte
     */
    private int decodeNextBWTByte() {
        int mergedPointer = bwtCurrentMergedPointer;
        int nextDecodedByte =  mergedPointer & 0xff;
        bwtCurrentMergedPointer = bwtMergedPointers[mergedPointer >>> 8];

        if (blockRandomised) {
            if (--randomCount == 0) {
                nextDecodedByte ^= 1;
                randomIndex = (randomIndex + 1) % 512;
                randomCount = Bzip2Rand.rNums(randomIndex);
            }
        }
        bwtBytesDecoded++;

        return nextDecodedByte;
    }

    public int blockLength() {
        return bwtBlockLength;
    }

    /**
     * Verify and return the block CRC. This method may only be called
     * after all of the block's bytes have been read.
     * @return The block CRC
     */
    int checkCRC() {
        final int computedBlockCRC = crc.getCRC();
        if (blockCRC != computedBlockCRC) {
            throw new DecompressionException("block CRC error");
        }
        return computedBlockCRC;
    }
}
