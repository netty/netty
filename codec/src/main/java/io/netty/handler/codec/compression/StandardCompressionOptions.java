/*
 * Copyright 2021 The Netty Project
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

import com.aayushatharva.brotli4j.encoder.Encoder;

/**
 * Standard Compression Options for {@link BrotliOptions},
 * {@link GzipOptions} and {@link DeflateOptions}
 */
public final class StandardCompressionOptions {

    private StandardCompressionOptions() {
        // Prevent outside initialization
    }

    /**
     * Default implementation of {@link BrotliOptions} with {@link Encoder.Parameters#setQuality(int)} set to 4
     * and {@link Encoder.Parameters#setMode(Encoder.Mode)} set to {@link Encoder.Mode#TEXT}
     */
    public static BrotliOptions brotli() {
        return BrotliOptions.DEFAULT;
    }

    /**
     * Create a new {@link BrotliOptions}
     *
     * @param parameters {@link Encoder.Parameters} Instance
     * @throws NullPointerException If {@link Encoder.Parameters} is {@code null}
     */
    public static BrotliOptions brotli(Encoder.Parameters parameters) {
        return new BrotliOptions(parameters);
    }

    /**
     * Default implementation of {@link ZstdOptions} with{compressionLevel(int)} set to
     * {@link ZstdConstants#DEFAULT_COMPRESSION_LEVEL},{@link ZstdConstants#DEFAULT_BLOCK_SIZE},
     * {@link ZstdConstants#MAX_BLOCK_SIZE}
     */
    public static ZstdOptions zstd() {
        return ZstdOptions.DEFAULT;
    }

    /**
     * Create a new {@link ZstdOptions}
     *
     * @param  blockSize
     *           is used to calculate the compressionLevel
     * @param  maxEncodeSize
     *           specifies the size of the largest compressed object
     * @param  compressionLevel
     *           specifies the level of the compression
     */
    public static ZstdOptions zstd(int compressionLevel, int blockSize, int maxEncodeSize) {
        return new ZstdOptions(compressionLevel, blockSize, maxEncodeSize);
    }

     /**
     * Default implementation of {@link GzipOptions} with
     * {@code compressionLevel()} set to 6, {@code windowBits()} set to 15 and {@code memLevel()} set to 8.
     */
    public static GzipOptions gzip() {
        return GzipOptions.DEFAULT;
    }

    /**
     * Create a new {@link GzipOptions} Instance
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     *
     * @param windowBits       The base two logarithm of the size of the history buffer.  The
     *                         value should be in the range {@code 9} to {@code 15} inclusive.
     *                         Larger values result in better compression at the expense of
     *                         memory usage.  The default value is {@code 15}.
     *
     * @param memLevel         How much memory should be allocated for the internal compression
     *                         state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                         memory.  Larger values result in better and faster compression
     *                         at the expense of memory usage.  The default value is {@code 8}
     */
    public static GzipOptions gzip(int compressionLevel, int windowBits, int memLevel) {
        return new GzipOptions(compressionLevel, windowBits, memLevel);
    }

    /**
     * Default implementation of {@link DeflateOptions} with
     * {@code compressionLevel} set to 6, {@code windowBits} set to 15 and {@code memLevel} set to 8.
     */
    public static DeflateOptions deflate() {
        return DeflateOptions.DEFAULT;
    }

    /**
     * Create a new {@link DeflateOptions} Instance
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     *
     * @param windowBits       The base two logarithm of the size of the history buffer.  The
     *                         value should be in the range {@code 9} to {@code 15} inclusive.
     *                         Larger values result in better compression at the expense of
     *                         memory usage.  The default value is {@code 15}.
     *
     * @param memLevel         How much memory should be allocated for the internal compression
     *                         state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                         memory.  Larger values result in better and faster compression
     *                         at the expense of memory usage.  The default value is {@code 8}
     */
    public static DeflateOptions deflate(int compressionLevel, int windowBits, int memLevel) {
        return new DeflateOptions(compressionLevel, windowBits, memLevel);
    }
}
