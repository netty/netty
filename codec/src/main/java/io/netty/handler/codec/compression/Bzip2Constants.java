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

/**
 * Constants for both the {@link Bzip2Encoder} and the {@link Bzip2Decoder}.
 */
final class Bzip2Constants {

    /**
     * Magic number of Bzip2 stream.
     */
    static final int MAGIC_NUMBER = 'B' << 16 | 'Z' << 8 | 'h';

    /**
     * Block header magic number. Equals to BCD (pi).
     */
    static final int BLOCK_HEADER_MAGIC_1 = 0x314159;
    static final int BLOCK_HEADER_MAGIC_2 = 0x265359;

    /**
     * End of stream magic number. Equals to BCD sqrt(pi).
     */
    static final int END_OF_STREAM_MAGIC_1 = 0x177245;
    static final int END_OF_STREAM_MAGIC_2 = 0x385090;

    /**
     * Base block size.
     */
    static final int BASE_BLOCK_SIZE = 100000;

    /**
     * Minimum and maximum size of one block.
     * Must be multiplied by {@link Bzip2Constants#BASE_BLOCK_SIZE}.
     */
    static final int MIN_BLOCK_SIZE = 1;
    static final int MAX_BLOCK_SIZE = 9;

    /**
     * Maximum possible Huffman alphabet size.
     */
    static final int HUFFMAN_MAX_ALPHABET_SIZE = 258;

    /**
     * The longest Huffman code length created by the encoder.
     */
    static final int HUFFMAN_ENCODE_MAX_CODE_LENGTH = 20;

    /**
     * The longest Huffman code length accepted by the decoder.
     */
    static final int HUFFMAN_DECODE_MAX_CODE_LENGTH = 23;

    /**
     * Huffman symbols used for run-length encoding.
     */
    static final int HUFFMAN_SYMBOL_RUNA = 0;
    static final int HUFFMAN_SYMBOL_RUNB = 1;

    /**
     * Huffman symbols range size for Huffman used map.
     */
    static final int HUFFMAN_SYMBOL_RANGE_SIZE = 16;

    /**
     * Maximum length of zero-terminated bit runs of MTF'ed Huffman table.
     */
    static final int HUFFMAN_SELECTOR_LIST_MAX_LENGTH = 6;

    /**
     * Number of symbols decoded after which a new Huffman table is selected.
     */
    static final int HUFFMAN_GROUP_RUN_LENGTH = 50;

    /**
     * Maximum possible number of Huffman table selectors.
     */
    static final int MAX_SELECTORS = 2 + 900000 / HUFFMAN_GROUP_RUN_LENGTH; // 18002

    /**
     * Minimum number of alternative Huffman tables.
     */
    static final int HUFFMAN_MINIMUM_TABLES = 2;

    /**
     * Maximum number of alternative Huffman tables.
     */
    static final int HUFFMAN_MAXIMUM_TABLES = 6;

    private Bzip2Constants() { }
}
