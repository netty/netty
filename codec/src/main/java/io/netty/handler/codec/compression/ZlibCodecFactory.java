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
package io.netty.handler.codec.compression;

/**
 * Creates a new {@link ZlibEncoder} and a new {@link ZlibDecoder}.
 */
public final class ZlibCodecFactory {
    private static final boolean supportsWindowSizeAndMemLevel = true;

    /**
     * Returns {@code true} if specify a custom window size and mem level is supported.
     */
    public static boolean isSupportingWindowSizeAndMemLevel() {
        return supportsWindowSizeAndMemLevel;
    }

    public static ZlibEncoder newZlibEncoder(int compressionLevel) {
        return new JdkZlibEncoder(compressionLevel);
    }

    public static ZlibEncoder newZlibEncoder(ZlibWrapper wrapper) {
        return new JdkZlibEncoder(wrapper);
    }

    public static ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel) {
        return new JdkZlibEncoder(wrapper, compressionLevel);
    }

    public static ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel) {
        return new JdkZlibEncoder(wrapper, compressionLevel);
    }

    public static ZlibEncoder newZlibEncoder(byte[] dictionary) {
        return new JdkZlibEncoder(dictionary);
    }

    public static ZlibEncoder newZlibEncoder(int compressionLevel, byte[] dictionary) {
        return new JdkZlibEncoder(compressionLevel, dictionary);
    }

    public static ZlibEncoder newZlibEncoder(int compressionLevel, int windowBits, int memLevel, byte[] dictionary) {
        return new JdkZlibEncoder(compressionLevel, dictionary);
    }

    public static ZlibDecoder newZlibDecoder() {
        return new JdkZlibDecoder(true);
    }

    public static ZlibDecoder newZlibDecoder(ZlibWrapper wrapper) {
        return new JdkZlibDecoder(wrapper, true);
    }

    public static ZlibDecoder newZlibDecoder(byte[] dictionary) {
        return new JdkZlibDecoder(dictionary);
    }

    private ZlibCodecFactory() {
        // Unused
    }
}
