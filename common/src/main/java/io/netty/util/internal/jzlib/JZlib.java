/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/*
 * This program is based on zlib-1.1.3, so all credit should go authors
 * Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
 * and contributors of zlib.
 */

package io.netty.util.internal.jzlib;

public final class JZlib {

    // wrapper types
    public static final Enum<?> W_NONE = WrapperType.NONE;
    public static final Enum<?> W_ZLIB = WrapperType.ZLIB;
    public static final Enum<?> W_GZIP = WrapperType.GZIP;
    public static final Enum<?> W_ZLIB_OR_NONE = WrapperType.ZLIB_OR_NONE;

    // compression levels
    public static final int Z_NO_COMPRESSION = 0;
    public static final int Z_BEST_SPEED = 1;
    public static final int Z_BEST_COMPRESSION = 9;
    public static final int Z_DEFAULT_COMPRESSION = -1;

    // compression strategy
    public static final int Z_FILTERED = 1;
    public static final int Z_HUFFMAN_ONLY = 2;
    public static final int Z_DEFAULT_STRATEGY = 0;

    // flush method
    public static final int Z_NO_FLUSH = 0;
    public static final int Z_PARTIAL_FLUSH = 1;
    public static final int Z_SYNC_FLUSH = 2;
    public static final int Z_FULL_FLUSH = 3;
    public static final int Z_FINISH = 4;

    // return codes
    public static final int Z_OK = 0;
    public static final int Z_STREAM_END = 1;
    public static final int Z_NEED_DICT = 2;
    public static final int Z_ERRNO = -1;
    public static final int Z_STREAM_ERROR = -2;
    public static final int Z_DATA_ERROR = -3;
    public static final int Z_MEM_ERROR = -4;
    public static final int Z_BUF_ERROR = -5;
    public static final int Z_VERSION_ERROR = -6;

    // internal stuff
    static final int Z_DEFLATED = 8;
    static final int MAX_MEM_LEVEL = 9;
    static final int DEF_MEM_LEVEL = 8;
    static final int MAX_WBITS = 15; // 32K LZ77 window
    static final int DEF_WBITS = MAX_WBITS;
    static final int MAX_BITS = 15;
    static final int PRESET_DICT = 0x20; // preset dictionary flag in zlib header
    static final int MANY = 1440;
    static final int BL_CODES = 19;
    static final int D_CODES = 30;
    static final int LITERALS = 256;
    static final int LENGTH_CODES = 29;
    static final int L_CODES = LITERALS + 1 + LENGTH_CODES;
    static final int HEAP_SIZE = 2 * L_CODES + 1;
    // Bit length codes must not exceed MAX_BL_BITS bits
    static final int MAX_BL_BITS = 7;

    enum WrapperType {
        NONE, ZLIB, GZIP, ZLIB_OR_NONE
    }
}
