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
package io.netty.handler.codec.http;

import static io.netty.handler.codec.http.HttpHeaderValues.BR;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.X_DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.X_GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.SNAPPY;
import static io.netty.handler.codec.http.HttpHeaderValues.ZSTD;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliDecoder;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdDecoder;

/**
 * Decompresses an {@link HttpMessage} and an {@link HttpContent} compressed in
 * {@code gzip} or {@code deflate} encoding.  For more information on how this
 * handler modifies the message, please refer to {@link HttpContentDecoder}.
 */
public class HttpContentDecompressor extends HttpContentDecoder {

    private final boolean strict;
    private final int maxAllocation;

    /**
     * Create a new {@link HttpContentDecompressor} in non-strict mode.
     * @deprecated
     *            Use {@link HttpContentDecompressor#HttpContentDecompressor(int)}.
     */
    @Deprecated
    public HttpContentDecompressor() {
        this(false, 0);
    }

    /**
     * Create a new {@link HttpContentDecompressor} in non-strict mode.
     * @param maxAllocation
     *            Maximum size of the decompression buffer. Must be &gt;= 0. If zero, maximum size is not limited.
     */
    public HttpContentDecompressor(int maxAllocation) {
        this(false, maxAllocation);
    }

    /**
     * Create a new {@link HttpContentDecompressor}.
     *
     * @param strict    if {@code true} use strict handling of deflate if used, otherwise handle it in a
     *                  more lenient fashion.
     * @deprecated
     *            Use {@link HttpContentDecompressor#HttpContentDecompressor(boolean, int)}.
     */
    @Deprecated
    public HttpContentDecompressor(boolean strict) {
        this(strict, 0);
    }

    /**
     * Create a new {@link HttpContentDecompressor}.
     *
     * @param strict    if {@code true} use strict handling of deflate if used, otherwise handle it in a
     *                  more lenient fashion.
     * @param maxAllocation
     *             Maximum size of the decompression buffer. Must be &gt;= 0. If zero, maximum size is not limited.
     */
    public HttpContentDecompressor(boolean strict, int maxAllocation) {
        this.strict = strict;
        this.maxAllocation = checkPositiveOrZero(maxAllocation, "maxAllocation");
    }

    @Override
    protected EmbeddedChannel newContentDecoder(String contentEncoding) throws Exception {
        if (GZIP.contentEqualsIgnoreCase(contentEncoding) ||
            X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP, maxAllocation));
        }
        if (DEFLATE.contentEqualsIgnoreCase(contentEncoding) ||
            X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            final ZlibWrapper wrapper = strict ? ZlibWrapper.ZLIB : ZlibWrapper.ZLIB_OR_NONE;
            // To be strict, 'deflate' means ZLIB, but some servers were not implemented correctly.
            return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), ZlibCodecFactory.newZlibDecoder(wrapper, maxAllocation));
        }
        if (Brotli.isAvailable() && BR.contentEqualsIgnoreCase(contentEncoding)) {
            return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
              ctx.channel().config(), new BrotliDecoder());
        }

        if (SNAPPY.contentEqualsIgnoreCase(contentEncoding)) {
            return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), new SnappyFrameDecoder());
        }

        if (Zstd.isAvailable() && ZSTD.contentEqualsIgnoreCase(contentEncoding)) {
            return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), new ZstdDecoder());
        }

        // 'identity' or unsupported
        return null;
    }
}
