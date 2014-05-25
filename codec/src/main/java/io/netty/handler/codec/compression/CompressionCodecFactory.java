/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

/**
 * Creates a new {@link CompressionEncoder} or a new {@link CompressionDecoder}
 * with specified {@link CompressionFormat}.
 */
public final class CompressionCodecFactory {

    /**
     * Factory method for a new {@link CompressionEncoder}.
     *
     * @param format specified compression format.
     */
    public static CompressionEncoder newEncoder(CompressionFormat format) {
        ObjectUtil.checkNotNull(format, "format");
        switch (format) {
            case ZLIB:
                return ZlibCodecFactory.newZlibEncoder(ZlibWrapper.ZLIB);
            case GZIP:
                return ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP);
            case DEFLATE:
                return ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE);
            case ZLIB_OR_NONE:
                throw new UnsupportedOperationException("In case of " + ZlibWrapper.ZLIB_OR_NONE
                        + " an encoder can not be auto-detected");
            case SNAPPY:
                return new SnappyFrameEncoder();
            case BZIP2:
                return new Bzip2Encoder();
            case LZF:
                return new LzfEncoder();
            case LZ4:
                return new Lz4FrameEncoder();
            case FASTLZ:
                return new FastLzFrameEncoder();
            case LZMA:
                return new LzmaFrameEncoder();
            default:
                throw new IllegalArgumentException("Format " + format + " is not supported");
        }
    }

    /**
     * Factory method for a new {@link CompressionDecoder}.
     *
     * @param format specified compression format.
     */
    public static CompressionDecoder newDecoder(CompressionFormat format) {
        ObjectUtil.checkNotNull(format, "format");
        switch (format) {
            case ZLIB:
                return ZlibCodecFactory.newZlibDecoder(ZlibWrapper.ZLIB);
            case GZIP:
                return ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP);
            case DEFLATE:
                return ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE);
            case ZLIB_OR_NONE:
                return ZlibCodecFactory.newZlibDecoder(ZlibWrapper.ZLIB_OR_NONE);
            case SNAPPY:
                return new SnappyFrameDecoder();
            case BZIP2:
                return new Bzip2Decoder();
            case LZF:
                return new LzfDecoder();
            case LZ4:
                return new Lz4FrameDecoder();
            case FASTLZ:
                return new FastLzFrameDecoder();
            default:
                throw new IllegalArgumentException("Format " + format + " is not supported");
        }
    }

    private CompressionCodecFactory() { }
}
