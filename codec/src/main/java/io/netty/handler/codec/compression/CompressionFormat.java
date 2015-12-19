/*
 * Copyright 2017 The Netty Project
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

/**
 * Enumeration of possible compression formats.
 */
public enum CompressionFormat {
    /**
     * The ZLIB Compressed Data Format as specified in <a href="https://tools.ietf.org/html/rfc1950">RFC 1950</a>.
     *
     * For more information about {@code zlib} compression format
     * see <a href="http://en.wikipedia.org/wiki/Zlib">zlib</a> wiki-page.
     */
    ZLIB,
    /**
     * The GZIP file format as specified in <a href="https://tools.ietf.org/html/rfc1952">RFC 1952</a>.
     *
     * For more information about {@code gzip} compression format
     * see <a href="http://en.wikipedia.org/wiki/Gzip">gzip</a> wiki-page.
     */
    GZIP,
    /**
     * The DEFLATE Compressed Data Format as specified in <a href="https://tools.ietf.org/html/rfc1951">RFC 1951</a>.
     *
     * For more information about {@code DEFLATE} compression format
     * see <a href="https://en.wikipedia.org/wiki/DEFLATE">DEFLATE</a> wiki-page.
     */
    DEFLATE,
    /**
     * Try {@link #ZLIB} first and then {@link #DEFLATE} if the first attempt fails.
     * Please note that you can specify this wrapper type only when decompressing.
     */
    ZLIB_OR_NONE,
    /**
     * For more information about {@code gzip} compression format
     * see <a href="http://google.github.io/snappy/">Snappy</a> official website.
     */
    SNAPPY,
    /**
     * For more information about {@code bzip2} compression format
     * see <a href="http://en.wikipedia.org/wiki/Bzip2">bzip2</a> wiki-page.
     */
    BZIP2,
    /**
     * For more information about {@code LZF} compression format
     * see <a href="https://github.com/ning/compress/wiki">LZF</a> official repository.
     */
    LZF,
    /**
     * For more information about {@code LZ4} compression format
     * see <a href="http://en.wikipedia.org/wiki/LZ4_%28compression_algorithm%29">LZ4</a> wiki-page.
     */
    LZ4,
    /**
     * For more information about {@code FastLZ} compression format
     * see <a href="http://fastlz.org/">FastLZ</a> wiki-page.
     */
    FASTLZ,
    /**
     * For more information about {@code LZMA} compression format
     * see <a href="http://en.wikipedia.org/wiki/Lempel%E2%80%93Ziv%E2%80%93Markov_chain_algorithm">LZMA</a> wiki-page.
     */
    LZMA
}
