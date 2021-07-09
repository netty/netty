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
     * @see BrotliOptions#DEFAULT
     */
    public static BrotliOptions brotli() {
        return BrotliOptions.DEFAULT;
    }

    /**
     * @see BrotliOptions#BrotliOptions(Encoder.Parameters)
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
     * @see GzipOptions#DEFAULT
     */
    public static GzipOptions gzip() {
        return GzipOptions.DEFAULT;
    }

    /**
     * @see GzipOptions#GzipOptions(int, int, int)
     */
    public static GzipOptions gzip(int compressionLevel, int windowBits, int memLevel) {
        return new GzipOptions(compressionLevel, windowBits, memLevel);
    }

    /**
     * @see DeflateOptions#DEFAULT
     */
    public static DeflateOptions deflate() {
        return DeflateOptions.DEFAULT;
    }

    /**
     * @see DeflateOptions#DeflateOptions(int, int, int)
     */
    public static DeflateOptions deflate(int compressionLevel, int windowBits, int memLevel) {
        return new DeflateOptions(compressionLevel, windowBits, memLevel);
    }
}
