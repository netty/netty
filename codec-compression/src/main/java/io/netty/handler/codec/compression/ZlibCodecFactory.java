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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Creates a new {@link ZlibEncoder} and a new {@link ZlibDecoder}.
 */
public final class ZlibCodecFactory {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ZlibCodecFactory.class);

    private static final int DEFAULT_JDK_WINDOW_SIZE = 15;
    private static final int DEFAULT_JDK_MEM_LEVEL = 8;

    /**
     * Returns {@code true} if specify a custom window size and mem level is supported.
     */
    public static boolean isSupportingWindowSizeAndMemLevel() {
        return false;
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

    /**
     * Create a new decoder instance.
     *
     * @deprecated Use {@link ZlibCodecFactory#newZlibDecoder(int)}.
     */
    @Deprecated
    public static ZlibDecoder newZlibDecoder() {
        return newZlibDecoder(0);
    }

    /**
     * Create a new decoder instance with specified maximum buffer allocation.
     *
     * @param maxAllocation
     *           Maximum size of the decompression buffer. Must be &gt;= 0.
     *           If zero, maximum size is not limited by decoder.
     */
    public static ZlibDecoder newZlibDecoder(int maxAllocation) {
        return new JdkZlibDecoder(true, maxAllocation);
    }

    /**
     * Create a new decoder instance with the specified wrapper.
     *
     * @deprecated Use {@link ZlibCodecFactory#newZlibDecoder(ZlibWrapper, int)}.
     */
    @Deprecated
    public static ZlibDecoder newZlibDecoder(ZlibWrapper wrapper) {
        return newZlibDecoder(wrapper, 0);
    }

    /**
     * Create a new decoder instance with the specified wrapper and maximum buffer allocation.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is not limited by decoder.
     */
    public static ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
        return new JdkZlibDecoder(wrapper, true, maxAllocation);
    }

    /**
     * Create a new decoder instance with the specified preset dictionary. The wrapper
     * is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @deprecated Use {@link ZlibCodecFactory#newZlibDecoder(byte[], int)}.
     */
    @Deprecated
    public static ZlibDecoder newZlibDecoder(byte[] dictionary) {
        return newZlibDecoder(dictionary, 0);
    }

    /**
     * Create a new decoder instance with the specified preset dictionary and maximum buffer allocation.
     * The wrapper is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is not limited by decoder.
     */
    public static ZlibDecoder newZlibDecoder(byte[] dictionary, int maxAllocation) {
        return new JdkZlibDecoder(dictionary, maxAllocation);
    }

    private ZlibCodecFactory() {
        // Unused
    }
}
