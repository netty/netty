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

final class Lz4Constants {
    /**
     * Magic number of LZ4 block.
     */
    static final long MAGIC_NUMBER = (long) 'L' << 56 |
                                     (long) 'Z' << 48 |
                                     (long) '4' << 40 |
                                     (long) 'B' << 32 |
                                            'l' << 24 |
                                            'o' << 16 |
                                            'c' << 8  |
                                            'k';

    /**
     * Full length of LZ4 block header.
     */
    static final int HEADER_LENGTH = 8 +  // magic number
                                     1 +  // token
                                     4 +  // compressed length
                                     4 +  // decompressed length
                                     4;   // checksum

    /**
     * Offsets of header's parts.
     */
    static final int TOKEN_OFFSET = 8;
    static final int COMPRESSED_LENGTH_OFFSET = TOKEN_OFFSET + 1;
    static final int DECOMPRESSED_LENGTH_OFFSET = COMPRESSED_LENGTH_OFFSET + 4;
    static final int CHECKSUM_OFFSET = DECOMPRESSED_LENGTH_OFFSET + 4;

    /**
     * Base value for compression level.
     */
    static final int COMPRESSION_LEVEL_BASE = 10;

    /**
     * LZ4 block sizes.
     */
    static final int MIN_BLOCK_SIZE = 64;
    static final int MAX_BLOCK_SIZE = 1 << COMPRESSION_LEVEL_BASE + 0x0F;   //  32 M
    static final int DEFAULT_BLOCK_SIZE = 1 << 16;  // 64 KB

    /**
     * LZ4 block types.
     */
    static final int BLOCK_TYPE_NON_COMPRESSED = 0x10;
    static final int BLOCK_TYPE_COMPRESSED = 0x20;

    /**
     * Default seed value for xxhash.
     */
    static final int DEFAULT_SEED = 0x9747b28c;

    private Lz4Constants() { }
}
