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
package io.netty5.handler.codec.http;

import static io.netty5.handler.codec.http.HttpHeaderValues.BR;
import static io.netty5.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty5.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty5.handler.codec.http.HttpHeaderValues.X_DEFLATE;
import static io.netty5.handler.codec.http.HttpHeaderValues.X_GZIP;

import io.netty5.handler.codec.compression.Brotli;
import io.netty5.handler.codec.compression.BrotliDecompressor;
import io.netty5.handler.codec.compression.Decompressor;
import io.netty5.handler.codec.compression.ZlibDecompressor;
import io.netty5.handler.codec.compression.ZlibWrapper;

/**
 * Decompresses an {@link HttpMessage} and an {@link HttpContent} compressed in
 * {@code gzip} or {@code deflate} encoding.  For more information on how this
 * handler modifies the message, please refer to {@link HttpContentDecoder}.
 */
public class HttpContentDecompressor extends HttpContentDecoder {

    private final boolean strict;

    /**
     * Create a new {@link HttpContentDecompressor} in non-strict mode.
     */
    public HttpContentDecompressor() {
        this(false);
    }

    /**
     * Create a new {@link HttpContentDecompressor}.
     *
     * @param strict    if {@code true} use strict handling of deflate if used, otherwise handle it in a
     *                  more lenient fashion.
     */
    public HttpContentDecompressor(boolean strict) {
        this.strict = strict;
    }

    @Override
    protected Decompressor newContentDecoder(String contentEncoding) throws Exception {
        if (GZIP.contentEqualsIgnoreCase(contentEncoding) ||
            X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            return ZlibDecompressor.newFactory(ZlibWrapper.GZIP).get();
        }
        if (DEFLATE.contentEqualsIgnoreCase(contentEncoding) ||
            X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            final ZlibWrapper wrapper = strict ? ZlibWrapper.ZLIB : ZlibWrapper.ZLIB_OR_NONE;
            ZlibDecompressor.newFactory(wrapper).get();
        }
        if (Brotli.isAvailable() && BR.contentEqualsIgnoreCase(contentEncoding)) {
            return BrotliDecompressor.newFactory().get();
        }

        // 'identity' or unsupported
        return null;
    }
}
