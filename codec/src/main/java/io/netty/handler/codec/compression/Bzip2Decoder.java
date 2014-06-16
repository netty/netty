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
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import static io.netty.handler.codec.compression.Bzip2Constants.*;

/**
 * Uncompresses a {@link ByteBuf} encoded with the Bzip2 format.
 *
 * See <a href="http://en.wikipedia.org/wiki/Bzip2">Bzip2</a>.
 */
public class Bzip2Decoder extends ByteToMessageDecoder {
    /**
     * Current state of stream.
     */
    enum State {
        INIT,
        INIT_BLOCK,
        INIT_BLOCK_PARAMS,
        RECEIVE_HUFFMAN_USED_MAP,
        RECEIVE_HUFFMAN_USED_BITMAPS,
        RECEIVE_SELECTORS_NUMBER,
        RECEIVE_SELECTORS,
        RECEIVE_HUFFMAN_LENGTH,
        DECODE_HUFFMAN_DATA,
        END_BLOCK,
        EOF
    }
    private State currentState = State.INIT;

    /**
     * The decompressor for the current block.
     */
    private Bzip2BlockDecompressor blockDecompressor;

    /**
     * BZip2 Huffman coding stage.
     */
    private Bzip2HuffmanStageDecoder huffmanStageDecoder;

    /**
     * Always: in the range 0 .. 9. The current block size is 100000 * this number.
     */
    private int blockSize;

    /**
     * The CRC of the current block as read from the block header.
     */
    private int blockCRC;

    /**
     * The merged CRC of all blocks decompressed so far.
     */
    private int streamCRC;

    // For bitwise access
    /**
     * A buffer of bits read from the input stream that have not yet been returned.
     */
    private int bitBuffer;

    /**
     * The number of bits currently buffered in {@link #bitBuffer}.
     */
    private int bitCount;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }

        for (;;) {
            switch (currentState) {
                case INIT:
                    if (in.readableBytes() < 4) {
                        return;
                    }
                    int magicNumber = in.readUnsignedMedium();
                    if (magicNumber != MAGIC_NUMBER) {
                        throw new DecompressionException("Unexpected stream identifier contents. Mismatched bzip2 " +
                                "protocol version?");
                    }
                    int blockSize = in.readByte() - '0';
                    if (blockSize < MIN_BLOCK_SIZE || blockSize > MAX_BLOCK_SIZE) {
                        throw new DecompressionException("block size is invalid");
                    }
                    this.blockSize = blockSize * BASE_BLOCK_SIZE;

                    streamCRC = 0;
                    currentState = State.INIT_BLOCK;
                case INIT_BLOCK:
                    if (in.readableBytes() < 10) {
                        return;
                    }
                    // Get the block magic bytes.
                    final long magic = (long) readBits(in, 24) << 24 | readBits(in, 24);
                    if (magic == END_OF_STREAM_MAGIC) {
                        // End of stream was reached. Check the combined CRC.
                        final int storedCombinedCRC = readInt(in);
                        if (storedCombinedCRC != streamCRC) {
                            throw new DecompressionException("stream CRC error");
                        }
                        currentState = State.EOF;
                        break;
                    }
                    if (magic != COMPRESSED_MAGIC) {
                        throw new DecompressionException("bad block header");
                    }
                    blockCRC = readInt(in);
                    currentState = State.INIT_BLOCK_PARAMS;
                case INIT_BLOCK_PARAMS:
                    if (in.readableBytes() < 4) {
                        return;
                    }
                    final boolean blockRandomised = readBoolean(in);
                    final int bwtStartPointer = readBits(in, 24);

                    blockDecompressor = new Bzip2BlockDecompressor(this.blockSize, blockCRC,
                                                                    blockRandomised, bwtStartPointer);
                    currentState = State.RECEIVE_HUFFMAN_USED_MAP;
                case RECEIVE_HUFFMAN_USED_MAP:
                    if (in.readableBytes() < 2) {
                        return;
                    }
                    blockDecompressor.huffmanInUse16 = readBits(in, 16);
                    currentState = State.RECEIVE_HUFFMAN_USED_BITMAPS;
                case RECEIVE_HUFFMAN_USED_BITMAPS:
                    Bzip2BlockDecompressor blockDecompressor = this.blockDecompressor;
                    final int inUse16 = blockDecompressor.huffmanInUse16;
                    final int bitNumber = Integer.bitCount(inUse16);
                    final byte[] huffmanSymbolMap = blockDecompressor.huffmanSymbolMap;

                    if (in.readableBytes() < bitNumber * 16 / 8 + 1) {
                        return;
                    }

                    int huffmanSymbolCount = 0;
                    if (bitNumber > 0) {
                        for (int i = 0; i < 16; i++) {
                            if ((inUse16 & ((1 << 15) >>> i)) != 0) {
                                for (int j = 0, k = i << 4; j < 16; j++, k++) {
                                    if (readBoolean(in)) {
                                        huffmanSymbolMap[huffmanSymbolCount++] = (byte) k;
                                    }
                                }
                            }
                        }
                    }
                    blockDecompressor.huffmanEndOfBlockSymbol = huffmanSymbolCount + 1;

                    int totalTables = readBits(in, 3);
                    if (totalTables < HUFFMAN_MINIMUM_TABLES || totalTables > HUFFMAN_MAXIMUM_TABLES) {
                        throw new DecompressionException("incorrect huffman groups number");
                    }
                    int alphaSize = huffmanSymbolCount + 2;
                    if (alphaSize > HUFFMAN_MAX_ALPHABET_SIZE) {
                        throw new DecompressionException("incorrect alphabet size");
                    }
                    huffmanStageDecoder = new Bzip2HuffmanStageDecoder(this, totalTables, alphaSize);
                    currentState = State.RECEIVE_SELECTORS_NUMBER;
                case RECEIVE_SELECTORS_NUMBER:
                    if (in.readableBytes() < 2) {
                        return;
                    }
                    int totalSelectors = readBits(in, 15);
                    if (totalSelectors < 1 || totalSelectors > MAX_SELECTORS) {
                        throw new DecompressionException("incorrect selectors number");
                    }
                    huffmanStageDecoder.selectors = new byte[totalSelectors];

                    currentState = State.RECEIVE_SELECTORS;
                case RECEIVE_SELECTORS:
                    Bzip2HuffmanStageDecoder huffmanStageDecoder = this.huffmanStageDecoder;
                    byte[] selectors = huffmanStageDecoder.selectors;
                    totalSelectors = selectors.length;
                    final Bzip2MoveToFrontTable tableMtf = huffmanStageDecoder.tableMTF;

                    int currSelector;
                    // Get zero-terminated bit runs (0..62) of MTF'ed Huffman table. length = 1..6
                    for (currSelector = huffmanStageDecoder.currentSelector;
                                currSelector < totalSelectors; currSelector++) {
                        if (!in.isReadable()) {
                            // Save state if end of current ByteBuf was reached
                            huffmanStageDecoder.currentSelector = currSelector;
                            return;
                        }
                        int index = 0;
                        while (readBoolean(in)) {
                            index++;
                        }
                        selectors[currSelector] = tableMtf.indexToFront(index);
                    }

                    currentState = State.RECEIVE_HUFFMAN_LENGTH;
                case RECEIVE_HUFFMAN_LENGTH:
                    huffmanStageDecoder = this.huffmanStageDecoder;
                    totalTables = huffmanStageDecoder.totalTables;
                    final byte[][] codeLength = huffmanStageDecoder.tableCodeLengths;
                    alphaSize = huffmanStageDecoder.alphabetSize;

                    /* Now the coding tables */
                    int currGroup;
                    int currLength = huffmanStageDecoder.currentLength;
                    int currAlpha = 0;
                    boolean modifyLength = huffmanStageDecoder.modifyLength;
                    boolean saveStateAndReturn = false;
                    loop: for (currGroup = huffmanStageDecoder.currentGroup; currGroup < totalTables; currGroup++) {
                        // start_huffman_length
                        if (!in.isReadable()) {
                            saveStateAndReturn = true;
                            break;
                        }
                        if (currLength < 0) {
                            currLength = readBits(in, 5);
                        }
                        for (currAlpha = huffmanStageDecoder.currentAlpha; currAlpha < alphaSize; currAlpha++) {
                            // delta_bit_length: 1..40
                            if (!hasBit(in)) {
                                saveStateAndReturn = true;
                                break loop;
                            }
                            while (modifyLength || readBoolean(in)) {  // 0=>next symbol; 1=>alter length
                                if (!hasBit(in)) {
                                    modifyLength = true;
                                    saveStateAndReturn = true;
                                    break loop;
                                }
                                currLength += readBoolean(in) ? -1 : 1; // 1=>decrement length;  0=>increment length
                                modifyLength = false;
                                if (!hasBit(in)) {
                                    saveStateAndReturn = true;
                                    break loop;
                                }
                            }
                            codeLength[currGroup][currAlpha] = (byte) currLength;
                        }
                        currLength = -1;
                        currAlpha = huffmanStageDecoder.currentAlpha = 0;
                        modifyLength = false;
                    }
                    if (saveStateAndReturn) {
                        // Save state if end of current ByteBuf was reached
                        huffmanStageDecoder.currentGroup = currGroup;
                        huffmanStageDecoder.currentLength = currLength;
                        huffmanStageDecoder.currentAlpha = currAlpha;
                        huffmanStageDecoder.modifyLength = modifyLength;
                        return;
                    }

                    // Finally create the Huffman tables
                    huffmanStageDecoder.createHuffmanDecodingTables();
                    currentState = State.DECODE_HUFFMAN_DATA;
                case DECODE_HUFFMAN_DATA:
                    blockDecompressor = this.blockDecompressor;
                    final boolean decoded = blockDecompressor.decodeHuffmanData(this.huffmanStageDecoder, in);
                    if (!decoded) {
                        return;
                    }
                    int blockLength = blockDecompressor.blockLength();
                    ByteBuf uncompressed = ctx.alloc().buffer(blockLength);
                    int uncByte;
                    while ((uncByte = blockDecompressor.read()) >= 0) {
                        uncompressed.writeByte(uncByte);
                    }

                    int currentBlockCRC = blockDecompressor.checkCRC();
                    streamCRC = (streamCRC << 1 | streamCRC >>> 31) ^ currentBlockCRC;

                    out.add(uncompressed);
                    currentState = State.INIT_BLOCK;
                    break;
                case EOF:
                    in.skipBytes(in.readableBytes());
                    return;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream
     * has been reached.
     */
    public boolean isClosed() {
        return currentState == State.EOF;
    }

    int readBits(ByteBuf in, final int n) {
        int bitCount = this.bitCount;
        int bitBuffer = this.bitBuffer;

        if (bitCount < n) {
            do {
                int uByte = in.readUnsignedByte();
                bitBuffer = bitBuffer << 8 | uByte;
                bitCount += 8;
            } while (bitCount < n);

            this.bitBuffer = bitBuffer;
        }

        this.bitCount = bitCount -= n;
        return (bitBuffer >>> bitCount) & ((1 << n) - 1);
    }

    private boolean readBoolean(ByteBuf in) {
        return readBits(in, 1) != 0;
    }

    private int readInt(ByteBuf in) {
        return readBits(in, 16) << 16 | readBits(in, 16);
    }

    private boolean hasBit(ByteBuf in) {
        return bitCount > 0 || in.isReadable();
    }
}
