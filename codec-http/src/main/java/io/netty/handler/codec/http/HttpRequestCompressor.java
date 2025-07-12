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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.BrotliOptions;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.DeflateOptions;
import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdEncoder;
import io.netty.handler.codec.compression.ZstdOptions;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@code ChannelOutboundHandler} that encodes (compresses) the body of HTTP-Requests using the given encoding.
 * <p>
 * <b>Supported encodings:</b>
 * <ul>
 * <li>gzip (default)</li>
 * <li>deflate</li>
 * <li>snappy</li>
 * <li>br (depends on "com.aayushatharva.brotli4j:brotli4j")</li>
 * <li>zstd (depends on "com.github.luben:zstd-jni")r</li>
 * </ul>
 * <p>
 * <b>Note for zstd:</b>
 * <a href="https://github.com/netty/netty/issues/15340">
 * you should define a threshold that is greater or equal to the configured block size</a>.
 * <p>
 * <b>How-To Use</b>
 * <p>
 * Add the handler after {@code HttpClientCodec} to the pipeline.
 * <p>
 * <b>Example for Netty</b>
 * <pre>
 * Bootstrap b = new Bootstrap();
 * b.handler(new ChannelInitializer&lt;SocketChannel&gt;() {
 *   public void initChannel(SocketChannel ch) throws Exception {
 *     ch.pipeline().addLast(new HttpClientCodec());
 *     // add request compressor
 *     ch.pipeline().addLast(new HttpRequestCompressor("gzip"));
 *   }
 * });
 * </pre>
 */
public class HttpRequestCompressor extends ChannelOutboundHandlerAdapter {

    /**
     * default encoding. used if no preferred encoding is set or it is not available.
     */
    public static final String DEFAULT_ENCODING = "gzip";
    private static final List<String> SUPPORTED_ENCODINGS = Arrays.asList(new String[] {
        "br",
        "zstd",
        "snappy",
        "deflate",
        "gzip"
    });
    private static final InternalLogger log = InternalLoggerFactory.getInstance(HttpRequestCompressor.class);
    private final int contentSizeThreshold;
    private final String encoding;
    private final Supplier<MessageToByteEncoder<ByteBuf>> encoderFactory;

    /**
     * Key of the attribute that will hold the original request that is needed later
     * in order to create a {@link FullHttpRequest}
     */
    private final AttributeKey<HttpRequest> reqAttrKey = AttributeKey.valueOf(HttpRequestCompressor.class, "req");

    /**
     * Key of the buffer that will hold all the payload chunks that we have to compress later
     */
    private final AttributeKey<ByteBuf> bufAttrKey = AttributeKey.valueOf(HttpRequestCompressor.class, "buf");

    /**
     * new instance using the defaults.
     * shortcut for {@code new HttpRequestCompressor(DEFAULT_ENCODING)}
     * @see #DEFAULT_ENCODING
     * @see #HttpRequestCompressor(java.lang.String)
     */
    public HttpRequestCompressor() {
        this(DEFAULT_ENCODING);
    }

    /**
     * new instance using the preferred encoding.
     * shortcut for {@code new HttpRequestCompressor(preferredEncoding, 0)}
     * @param preferredEncoding the preferred encoding.
     * if unavailable, the default encoding {@link #DEFAULT_ENCODING} will be used.
     * @see #HttpRequestCompressor(java.lang.String, int)
     */
    public HttpRequestCompressor(String preferredEncoding) {
        this(preferredEncoding, 0);
    }

    /**
     * new instance using the preferred encoding and the given threshold.
     * shortcut for {@code new HttpRequestCompressor(preferredEncoding, contentSizeThreshold, null)}
     * @param preferredEncoding the preferred encoding.
     * if unavailable, the default encoding {@link #DEFAULT_ENCODING} will be used.
     * @param contentSizeThreshold the size in byte the http body must have before compressing the request
     * @see #HttpRequestCompressor(java.lang.String, int, io.netty.handler.codec.compression.CompressionOptions...)
     */
    public HttpRequestCompressor(String preferredEncoding, int contentSizeThreshold) {
        this(preferredEncoding, contentSizeThreshold, (CompressionOptions[]) null);
    }

    /**
     * new instance using the preferred encoding, threshold and compression options.
     * @param preferredEncoding the preferred encoding.
     * if unavailable, the default encoding {@link #DEFAULT_ENCODING} will be used.
     * @param contentSizeThreshold the size in byte the http body must have before compressing the request
     * @param compressionOptions the desired compression options to use.
     * if {@code null} or empty, the defaults will be used.
     * @see StandardCompressionOptions#brotli() default brotli options
     * @see StandardCompressionOptions#gzip() default gzip options
     * @see StandardCompressionOptions#zstd() default zstd options
     * @see StandardCompressionOptions#deflate() default deflate options
     */
    public HttpRequestCompressor(String preferredEncoding, int contentSizeThreshold,
            CompressionOptions... compressionOptions) {
        ObjectUtil.checkNonEmpty(preferredEncoding, "preferredEncoding");
        this.contentSizeThreshold = ObjectUtil.
                checkPositiveOrZero(contentSizeThreshold, "contentSizeThreshold");

        if (!SUPPORTED_ENCODINGS.contains(preferredEncoding)) {
            throw new IllegalArgumentException(
                    String.format("Unsupported encoding %s. Supported encodings are: %s",
                            preferredEncoding, StringUtil.join(",", SUPPORTED_ENCODINGS)));
        }

        GzipOptions gzipOptions = null;
        DeflateOptions deflateOptions = null;
        BrotliOptions brotliOptions = null;
        ZstdOptions zstdOptions = null;
        for (CompressionOptions compressionOption : Optional.ofNullable(compressionOptions)
                .orElseGet(() -> new CompressionOptions[0])) {
            if (compressionOption instanceof BrotliOptions) {
                brotliOptions = (BrotliOptions) compressionOption;
            } else if (compressionOption instanceof GzipOptions) {
                gzipOptions = (GzipOptions) compressionOption;
            } else if (compressionOption instanceof DeflateOptions) {
                deflateOptions = (DeflateOptions) compressionOption;
            } else if (compressionOption instanceof ZstdOptions) {
                zstdOptions = (ZstdOptions) compressionOption;
            } else {
                log.info("ignoring unsupported compression option {}",
                        compressionOption != null ? compressionOption.getClass() : "null");
            }
        }

        if ("br".equals(preferredEncoding) && Brotli.isAvailable()) {
            encoding = preferredEncoding;
            final BrotliOptions opts = Optional.ofNullable(brotliOptions)
                    .orElseGet(StandardCompressionOptions::brotli);
            encoderFactory = () -> new BrotliEncoder(opts.parameters(), false);
        } else if ("zstd".equals(preferredEncoding) && Zstd.isAvailable()) {
            encoding = preferredEncoding;
            final ZstdOptions opts = Optional.ofNullable(zstdOptions)
                    .orElseGet(StandardCompressionOptions::zstd);
            encoderFactory = () -> new ZstdEncoder(opts.compressionLevel(), opts.blockSize(), opts.maxEncodeSize());
        } else if ("snappy".equals(preferredEncoding)) {
            encoding = preferredEncoding;
            encoderFactory = SnappyFrameEncoder::new;
        } else if ("deflate".equals(preferredEncoding)) {
            encoding = preferredEncoding;
            final DeflateOptions opts = Optional.ofNullable(deflateOptions)
                    .orElseGet(StandardCompressionOptions::deflate);
            encoderFactory = () -> ZlibCodecFactory.newZlibEncoder(
                    ZlibWrapper.ZLIB, opts.compressionLevel(), opts.windowBits(), opts.memLevel());
        } else {
            encoding = DEFAULT_ENCODING;
            final GzipOptions opts = Optional.ofNullable(gzipOptions)
                    .orElseGet(StandardCompressionOptions::gzip);
            encoderFactory = () -> ZlibCodecFactory.newZlibEncoder(
                    ZlibWrapper.GZIP, opts.compressionLevel(), opts.windowBits(), opts.memLevel());
        }
        if (!preferredEncoding.equals(encoding)) {
            log.info("preferred encoding {} not available, using {} as default", preferredEncoding, encoding);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof FullHttpRequest) {
            final FullHttpRequest req = (FullHttpRequest) msg;
            if (!isContentEncodingSet(req)
                    && req.content().isReadable()
                    && req.content().readableBytes() >= contentSizeThreshold) {
                handleFullHttpRequest(ctx, promise, req);
            } else {
                super.write(ctx, msg, promise);
            }
        } else if (msg instanceof HttpRequest) {
            final HttpRequest req = (HttpRequest) msg;
            if (!isContentEncodingSet(req)) {
                handleChunkedHttpRequest(ctx, promise, req);
            } else {
                super.write(ctx, msg, promise);
            }
        } else if (msg instanceof HttpContent && !(msg instanceof LastHttpContent)) {
            handleHttpContent(ctx, promise, (HttpContent) msg);
        } else if (msg instanceof ByteBuf) {
            handleHttpContent(ctx, promise, (ByteBuf) msg);
        } else if (msg instanceof LastHttpContent) {
            handleLastHttpContent(ctx, promise, (LastHttpContent) msg);
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private void handleFullHttpRequest(ChannelHandlerContext ctx, ChannelPromise promise, FullHttpRequest req) {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ctx.channel().id(),
                ctx.channel().metadata().hasDisconnect(),
                ctx.channel().config(),
                encoderFactory.get()
        );
        encoderChannel.writeOutbound(req.content().retain());
        encoderChannel.flushOutbound();
        ByteBuf compressedContent = encoderChannel.readOutbound();
        encoderChannel.finishAndReleaseAll();
        DefaultFullHttpRequest compressedRequest = new DefaultFullHttpRequest(
                req.protocolVersion(),
                req.method(),
                req.uri(),
                compressedContent,
                req.headers().copy(),
                req.trailingHeaders()
        );
        setContentEncoding(compressedRequest, encoding);
        HttpUtil.setContentLength(compressedRequest, compressedContent.readableBytes());
        HttpUtil.setTransferEncodingChunked(compressedRequest, false);
        ctx.write(compressedRequest, promise);
    }

    private void handleChunkedHttpRequest(ChannelHandlerContext ctx, ChannelPromise promise, HttpRequest req) {
        // only process requests that are allowed to have content
        if (req.method().equals(HttpMethod.POST)
                || req.method().equals(HttpMethod.PUT)
                || req.method().equals(HttpMethod.PATCH)) {
            if (!ctx.channel().attr(reqAttrKey).compareAndSet(null, ReferenceCountUtil.retain(req))) {
                ReferenceCountUtil.release(req);
                throw new IllegalStateException(
                        "new HttpRequest received while waiting for LastHttpContent of the previous request");
            }

            final ByteBuf buf = allocateBuffer(ctx, req);
            ctx.channel().attr(bufAttrKey).set(buf);
        } else {
            ctx.write(req, promise);
        }
    }

    private void handleHttpContent(ChannelHandlerContext ctx, ChannelPromise promise,
            HttpContent content) throws Exception {
        handleHttpContent(ctx, promise, content.content());
    }

    private void handleHttpContent(ChannelHandlerContext ctx, ChannelPromise promise, ByteBuf content) {
        final ByteBuf buf = ctx.channel().attr(bufAttrKey).get();
        if (buf == null) {
            // received HttpContent without previous HttpRequest ... likely because we skipped it
            ctx.write(content, promise);
        } else {
            buf.writeBytes(content);
        }
    }

    private void handleLastHttpContent(ChannelHandlerContext ctx, ChannelPromise promise, LastHttpContent last) {
        final HttpRequest req = ctx.channel().attr(reqAttrKey).getAndSet(null);
        if (req == null) {
            // received LastHttpContent without previous HttpRequest ... likely because we skipped it
            ctx.write(last, promise);
            return;
        }
        final ByteBuf uncompressedContent = ctx.channel().attr(bufAttrKey).getAndSet(null);
        final DefaultFullHttpRequest uncompressedRequest = new DefaultFullHttpRequest(
                req.protocolVersion(),
                req.method(),
                req.uri(),
                uncompressedContent,
                req.headers(),
                last.trailingHeaders()
        );
        // cleanup
        ReferenceCountUtil.release(req);
        if (uncompressedContent.isReadable()
                && uncompressedContent.readableBytes() >= contentSizeThreshold) {
            // convert uncompressed chunked request to full compressed request
            handleFullHttpRequest(ctx, promise, uncompressedRequest);
        } else {
            // body is empty or size below threshold, just write empty full request
            ctx.write(uncompressedRequest, promise);
        }
    }

    private ByteBuf allocateBuffer(ChannelHandlerContext ctx, HttpRequest req) {
        return HttpUtil.isContentLengthSet(req)
                ? ctx.alloc().directBuffer(HttpUtil.getContentLength(req, 256))
                : ctx.alloc().directBuffer();
    }

    private static boolean isContentEncodingSet(HttpMessage msg) {
        return msg.headers().contains(HttpHeaderNames.CONTENT_ENCODING);
    }

    private static void setContentEncoding(HttpMessage msg, String value) {
        msg.headers().set(HttpHeaderNames.CONTENT_ENCODING, value);
    }
}
