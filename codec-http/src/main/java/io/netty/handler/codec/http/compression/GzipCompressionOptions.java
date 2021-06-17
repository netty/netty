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
package io.netty.handler.codec.http.compression;

/**
 * {@link GzipCompressionOptions} holds {@link #compressionLevel()},
 * {@link #memLevel()} and {@link #windowBits()} for Gzip compression.
 * This class is an extension of {@link DeflateCompressionOptions}
 */
public final class GzipCompressionOptions extends DeflateCompressionOptions {

    /**
     * Default implementation of {@link GzipCompressionOptions} with
     * {@link #compressionLevel()} set to 6, {@link #windowBits()} set to 15
     * and {@link #memLevel()} set to 8.
     */
    public static final GzipCompressionOptions DEFAULT = new GzipCompressionOptions(
            6, 15, 8
    );

    /**
     * Create a new {@link GzipCompressionOptions} Instance
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     * @param windowBits       The base two logarithm of the size of the history buffer.  The
     *                         value should be in the range {@code 9} to {@code 15} inclusive.
     *                         Larger values result in better compression at the expense of
     *                         memory usage.  The default value is {@code 15}.
     * @param memLevel         How much memory should be allocated for the internal compression
     *                         state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                         memory.  Larger values result in better and faster compression
     *                         at the expense of memory usage.  The default value is {@code 8}
     */
    public GzipCompressionOptions(int compressionLevel, int windowBits, int memLevel) {
        super(compressionLevel, windowBits, memLevel);
    }
}
