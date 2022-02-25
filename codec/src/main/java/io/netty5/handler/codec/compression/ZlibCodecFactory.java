/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.compression;

import io.netty5.channel.ChannelHandler;

/**
 * Creates a new ZLIB encoder / decoder.
 */
public final class ZlibCodecFactory {
    private static final boolean supportsWindowSizeAndMemLevel = true;

    /**
     * Returns {@code true} if specify a custom window size and mem level is supported.
     */
    public static boolean isSupportingWindowSizeAndMemLevel() {
        return supportsWindowSizeAndMemLevel;
    }

    public static ChannelHandler newZlibEncoder(int compressionLevel) {
        return new CompressionHandler(ZlibCompressor.newFactory(compressionLevel));
    }

    public static ChannelHandler newZlibEncoder(ZlibWrapper wrapper) {
        return new CompressionHandler(ZlibCompressor.newFactory(wrapper));
    }

    public static ChannelHandler newZlibEncoder(ZlibWrapper wrapper, int compressionLevel) {
        return new CompressionHandler(ZlibCompressor.newFactory(wrapper, compressionLevel));
    }

    public static ChannelHandler newZlibEncoder(ZlibWrapper wrapper, int compressionLevel,
                                                int windowBits, int memLevel) {
        return new CompressionHandler(ZlibCompressor.newFactory(wrapper, compressionLevel));
    }

    public static ChannelHandler newZlibEncoder(byte[] dictionary) {
        return new CompressionHandler(ZlibCompressor.newFactory(dictionary));
    }

    public static ChannelHandler newZlibEncoder(int compressionLevel, byte[] dictionary) {
        return new CompressionHandler(ZlibCompressor.newFactory(compressionLevel, dictionary));
    }

    public static ChannelHandler newZlibEncoder(int compressionLevel, int windowBits, int memLevel, byte[] dictionary) {
        return new CompressionHandler(ZlibCompressor.newFactory(compressionLevel, dictionary));
    }

    public static ChannelHandler newZlibDecoder() {
        return new DecompressionHandler(ZlibDecompressor.newFactory(true));
    }

    public static ChannelHandler newZlibDecoder(ZlibWrapper wrapper) {
        return new DecompressionHandler(ZlibDecompressor.newFactory(wrapper, true));
    }

    public static ChannelHandler newZlibDecoder(byte[] dictionary) {
        return new DecompressionHandler(ZlibDecompressor.newFactory(dictionary));
    }

    private ZlibCodecFactory() {
        // Unused
    }
}
