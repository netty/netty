/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.handler.codec.compression.BackpressureDecompressionHandler;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliDecompressor;
import io.netty.handler.codec.compression.Decompressor;
import io.netty.handler.codec.compression.SnappyFrameDecompressor;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdDecompressor;

import static io.netty.handler.codec.http.HttpHeaderValues.BR;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.SNAPPY;
import static io.netty.handler.codec.http.HttpHeaderValues.X_DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.X_GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.ZSTD;

/**
 * Decompresses an {@link HttpMessage} and an {@link HttpContent} compressed in
 * {@code gzip} or {@code deflate} encoding.  For more information on how this
 * handler modifies the message, please refer to {@link BackpressureHttpContentDecoder}.
 */
public class HttpDecompressionHandler extends BackpressureHttpContentDecoder {
    protected Decompressor.AbstractDecompressorBuilder newDecompressorBuilder(String contentEncoding) {
        if (GZIP.contentEqualsIgnoreCase(contentEncoding) ||
                X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            return ZlibCodecFactory.decompressorBuilder()
                    .wrapper(ZlibWrapper.GZIP);
        }
        if (DEFLATE.contentEqualsIgnoreCase(contentEncoding) ||
                X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            return ZlibCodecFactory.decompressorBuilder()
                    .wrapper(ZlibWrapper.ZLIB_OR_NONE);
        }
        if (Brotli.isAvailable() && BR.contentEqualsIgnoreCase(contentEncoding)) {
            return BrotliDecompressor.builder();
        }

        if (SNAPPY.contentEqualsIgnoreCase(contentEncoding)) {
            return SnappyFrameDecompressor.builder();
        }

        if (Zstd.isAvailable() && ZSTD.contentEqualsIgnoreCase(contentEncoding)) {
            return ZstdDecompressor.builder();
        }

        // 'identity' or unsupported
        return null;
    }

    @Override
    protected final ChannelDuplexHandler newContentDecoder(String contentEncoding) throws Exception {
        Decompressor.AbstractDecompressorBuilder builder = newDecompressorBuilder(contentEncoding);
        if (builder != null) {
            return BackpressureDecompressionHandler.create(builder);
        } else {
            return null;
        }
    }
}
