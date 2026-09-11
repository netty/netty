/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.BrotliOptions;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.DeflateOptions;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.ZstdEncoder;
import io.netty.handler.codec.compression.ZstdOptions;

import static io.netty.handler.codec.http.HttpHeaderValues.BR;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.SNAPPY;
import static io.netty.handler.codec.http.HttpHeaderValues.ZSTD;

/**
 * Builds the {@link EmbeddedChannel} that compresses an HTTP/2 response body for a
 * {@link NegotiatedEncoding}.
 * <p>
 * This mirrors the {@code content-encoding} branches of the deprecated
 * {@code CompressorHttp2ConnectionEncoder} ({@code newContentCompressor}/{@code newCompressionChannel}),
 * but sources the compression parameters from the negotiated {@link CompressionOptions} instead of a
 * fixed, per-encoder configuration.
 */
final class Http2CompressionChannelFactory {

    private Http2CompressionChannelFactory() {
    }

    /**
     * Creates a new {@link EmbeddedChannel} that compresses data according to {@code negotiated}.
     *
     * @param ctx the context whose channel the returned {@link EmbeddedChannel} is modeled after
     * @param negotiated the negotiated encoding and its matching compression options
     * @return a new {@link EmbeddedChannel} that compresses written data, or {@code null} if
     *         {@code negotiated}'s content encoding is not recognized
     * @throws Http2Exception if the compressor could not be created for a recognized content encoding
     */
    static EmbeddedChannel newCompressionChannel(ChannelHandlerContext ctx, NegotiatedEncoding negotiated)
            throws Http2Exception {
        CharSequence contentEncoding = negotiated.contentEncoding;
        CompressionOptions options = negotiated.options;
        if (GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            return newZlibCompressionChannel(ctx, ZlibWrapper.GZIP, (DeflateOptions) options);
        }
        if (DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            return newZlibCompressionChannel(ctx, ZlibWrapper.ZLIB, (DeflateOptions) options);
        }
        if (BR.contentEqualsIgnoreCase(contentEncoding)) {
            return newBrotliCompressionChannel(ctx, (BrotliOptions) options);
        }
        if (ZSTD.contentEqualsIgnoreCase(contentEncoding)) {
            return newZstdCompressionChannel(ctx, (ZstdOptions) options);
        }
        if (SNAPPY.contentEqualsIgnoreCase(contentEncoding)) {
            return newSnappyCompressionChannel(ctx);
        }
        // Unrecognized content-encoding: nothing to compress with.
        return null;
    }

    private static EmbeddedChannel newZlibCompressionChannel(ChannelHandlerContext ctx, ZlibWrapper wrapper,
            DeflateOptions options) {
        Channel channel = ctx.channel();
        return EmbeddedChannel.builder()
                .channelId(channel.id())
                .hasDisconnect(channel.metadata().hasDisconnect())
                .config(channel.config())
                .handlers(ZlibCodecFactory.newZlibEncoder(wrapper, options.compressionLevel(), options.windowBits(),
                        options.memLevel()))
                .build();
    }

    private static EmbeddedChannel newBrotliCompressionChannel(ChannelHandlerContext ctx, BrotliOptions options) {
        Channel channel = ctx.channel();
        return EmbeddedChannel.builder()
                .channelId(channel.id())
                .hasDisconnect(channel.metadata().hasDisconnect())
                .config(channel.config())
                .handlers(new BrotliEncoder(options.parameters()))
                .build();
    }

    private static EmbeddedChannel newZstdCompressionChannel(ChannelHandlerContext ctx, ZstdOptions options) {
        Channel channel = ctx.channel();
        return EmbeddedChannel.builder()
                .channelId(channel.id())
                .hasDisconnect(channel.metadata().hasDisconnect())
                .config(channel.config())
                .handlers(new ZstdEncoder(options.compressionLevel(), options.blockSize(), options.maxEncodeSize()))
                .build();
    }

    private static EmbeddedChannel newSnappyCompressionChannel(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        return EmbeddedChannel.builder()
                .channelId(channel.id())
                .hasDisconnect(channel.metadata().hasDisconnect())
                .config(channel.config())
                .handlers(new SnappyFrameEncoder())
                .build();
    }
}
