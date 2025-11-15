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
import io.netty.handler.codec.compression.SnappyOptions;
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
     * default encoding. used if no preferred encoding is set.
     */
    public static final String DEFAULT_ENCODING = "gzip";

    /**
     * default threshold. used if no custom threshold is set.
     */
    public static final int DEFAULT_THRESHOLD = 0;

    private static final List<String> SUPPORTED_ENCODINGS = Arrays.asList(new String[] {
        "br",
        "zstd",
        "snappy",
        "deflate",
        "gzip",
    });

    private final int contentSizeThreshold;

    private final String encoding;

    private final Supplier<MessageToByteEncoder<ByteBuf>> encoderFactory;

    /**
     * Key of the attribute that will hold the original request that is needed later
     */
    private final AttributeKey<HttpRequest> reqAttrKey = AttributeKey.valueOf(HttpRequestCompressor.class, "req");

    /**
     * Key of the buffer that will hold all the payload chunks that we have to compress later
     */
    private final AttributeKey<ByteBuf> bufAttrKey = AttributeKey.valueOf(HttpRequestCompressor.class, "buf");

    /**
     * Key of the encoder-channel that is used to compress the chunks
     */
    private final AttributeKey<EmbeddedChannel> encoderChannelAttrKey =
            AttributeKey.valueOf(HttpRequestCompressor.class, "encoderChannel");

    /**
     * Create a new instance using the defaults.
     * shortcut for {@code new HttpRequestCompressor(DEFAULT_ENCODING)}
     * @see #DEFAULT_ENCODING
     * @see #HttpRequestCompressor(java.lang.String)
     */
    public HttpRequestCompressor() {
        this(DEFAULT_ENCODING);
    }

    /**
     * Create a new instance using the preferred encoding.
     * shortcut for {@code new HttpRequestCompressor(preferredEncoding, DEFAULT_THRESHOLD)}
     * @param preferredEncoding the preferred encoding.
     * if unavailable, the default encoding {@link #DEFAULT_ENCODING} will be used.
     * @see #HttpRequestCompressor(java.lang.String, int)
     */
    public HttpRequestCompressor(String preferredEncoding) {
        this(preferredEncoding, DEFAULT_THRESHOLD);
    }

    /**
     * Create a new instance using the preferred encoding and the given threshold.
     * shortcut for {@code new HttpRequestCompressor(preferredEncoding, contentSizeThreshold, null)}
     * @param preferredEncoding the preferred encoding.
     * if unavailable, the default encoding {@link #DEFAULT_ENCODING} will be used.
     * @param contentSizeThreshold the size in byte the http body must have before compressing the request
     * @see #HttpRequestCompressor(java.lang.String, int, CompressionOptions)
     */
    public HttpRequestCompressor(String preferredEncoding, int contentSizeThreshold) {
        this(preferredEncoding, contentSizeThreshold, null);
    }

    /**
     * Create a new instance using the preferred encoding, threshold and compression options.
     * @param preferredEncoding the preferred encoding.
     * if unavailable, the default encoding {@link #DEFAULT_ENCODING} will be used.
     * @param contentSizeThreshold the size in byte the http body must have before compressing the request
     * @param compressionOptions the desired compression options to use.
     * if {@code null}, the defaults for the given encoding will be used.
     * @see StandardCompressionOptions#brotli() default brotli options
     * @see StandardCompressionOptions#gzip() default gzip options
     * @see StandardCompressionOptions#zstd() default zstd options
     * @see StandardCompressionOptions#deflate() default deflate options
     */
    public HttpRequestCompressor(String preferredEncoding, int contentSizeThreshold,
            CompressionOptions compressionOptions) {
        ObjectUtil.checkNonEmpty(preferredEncoding, "preferredEncoding");
        this.contentSizeThreshold = ObjectUtil.
                checkPositiveOrZero(contentSizeThreshold, "contentSizeThreshold");

        if (!SUPPORTED_ENCODINGS.contains(preferredEncoding)) {
            throw new IllegalArgumentException(
                    String.format("Unsupported encoding %s. Supported encodings are: %s",
                            preferredEncoding, StringUtil.join(",", SUPPORTED_ENCODINGS)));
        }

        final String optsName = "compressionOptions";
        if ("br".equals(preferredEncoding) && Brotli.isAvailable()) {
            final BrotliOptions opts = Optional.ofNullable(
                    requireType(BrotliOptions.class, compressionOptions, optsName))
                    .orElseGet(StandardCompressionOptions::brotli);
            encoding = preferredEncoding;
            encoderFactory = () -> new BrotliEncoder(opts.parameters(), false);
        } else if ("zstd".equals(preferredEncoding) && Zstd.isAvailable()) {
            final ZstdOptions opts = Optional.ofNullable(
                    requireType(ZstdOptions.class, compressionOptions, optsName))
                    .orElseGet(StandardCompressionOptions::zstd);
            encoding = preferredEncoding;
            encoderFactory = () -> new ZstdEncoder(opts.compressionLevel(), opts.blockSize(), opts.maxEncodeSize());
        } else if ("snappy".equals(preferredEncoding)) {
            requireType(SnappyOptions.class, compressionOptions, optsName);
            encoding = preferredEncoding;
            encoderFactory = SnappyFrameEncoder::new;
        } else if ("deflate".equals(preferredEncoding)) {
            final DeflateOptions opts = Optional.ofNullable(
                    requireType(DeflateOptions.class, compressionOptions, optsName))
                    .orElseGet(StandardCompressionOptions::deflate);
            encoding = preferredEncoding;
            encoderFactory = () -> ZlibCodecFactory.newZlibEncoder(
                    ZlibWrapper.ZLIB, opts.compressionLevel(), opts.windowBits(), opts.memLevel());
        } else {
            final GzipOptions opts = Optional.ofNullable(
                    requireType(GzipOptions.class, compressionOptions, optsName))
                    .orElseGet(StandardCompressionOptions::gzip);
            encoding = DEFAULT_ENCODING;
            encoderFactory = () -> ZlibCodecFactory.newZlibEncoder(
                    ZlibWrapper.GZIP, opts.compressionLevel(), opts.windowBits(), opts.memLevel());
        }
        if (!preferredEncoding.equals(encoding)) {
            throw new IllegalArgumentException(String.format("preferred encoding %s not available", preferredEncoding));
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

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanup(ctx);
        super.handlerRemoved(ctx);
    }

    private void handleFullHttpRequest(ChannelHandlerContext ctx, ChannelPromise promise, FullHttpRequest req) {
        ByteBuf compressedContent = allocateBuffer(ctx, req);
        EmbeddedChannel encoderChannel = createEncoderChannel(ctx);
        finishCompress(encoderChannel, req.content().retain(), compressedContent);
        FullHttpRequest compressedRequest = req.replace(compressedContent);
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
            EmbeddedChannel encoderChannel = ctx.channel().attr(encoderChannelAttrKey).get();
            if (encoderChannel == null) {
                buf.writeBytes(content);
                // buffered content exceeds threshold, start streaming and send stored HttpRequest
                if (buf.isReadable() && buf.readableBytes() >= contentSizeThreshold) {
                    encoderChannel = createEncoderChannel(ctx);
                    ctx.channel().attr(encoderChannelAttrKey).set(encoderChannel);

                    final HttpRequest req = ctx.channel().attr(reqAttrKey).getAndSet(null);
                    setContentEncoding(req, encoding);
                    HttpUtil.setTransferEncodingChunked(req, true);
                    ctx.write(req, ctx.voidPromise());

                    // call retain here as it will call release after its written to the channel
                    encoderChannel.writeOutbound(buf.retain());
                    buf.clear();
                    writeAllOutput(encoderChannel, ctx, promise);
                }
            } else {
                // encoderChannel is present, this means we can now directly stream new content
                encoderChannel.writeOutbound(content);
                writeAllOutput(encoderChannel, ctx, promise);
            }
        }
    }

    private void handleLastHttpContent(ChannelHandlerContext ctx, ChannelPromise promise, LastHttpContent last) {
        final HttpRequest req = ctx.channel().attr(reqAttrKey).getAndSet(null);
        final EmbeddedChannel encoderChannel = ctx.channel().attr(encoderChannelAttrKey).getAndSet(null);
        if (req == null && encoderChannel == null) {
            // received LastHttpContent without previous HttpRequest ... likely because we skipped it
            ctx.write(last, promise);
        } else if (req != null && encoderChannel == null) {
            // never received content or content size is below threshold, send everything at once
            final ByteBuf uncompressedContent = ctx.channel().attr(bufAttrKey).getAndSet(null);
            uncompressedContent.writeBytes(last.content());
            final DefaultFullHttpRequest uncompressedRequest = new DefaultFullHttpRequest(
                    req.protocolVersion(),
                    req.method(),
                    req.uri(),
                    uncompressedContent,
                    req.headers().copy(),
                    last.trailingHeaders()
            );
            // cleanup
            ReferenceCountUtil.release(req);
            if (uncompressedContent.isReadable()
                    && uncompressedContent.readableBytes() >= contentSizeThreshold) {
                // convert uncompressed request to compressed request
                handleFullHttpRequest(ctx, promise, uncompressedRequest);
            } else {
                // body is empty or size still below threshold, just write it as uncompressed full request
                HttpUtil.setContentLength(uncompressedRequest, uncompressedContent.readableBytes());
                HttpUtil.setTransferEncodingChunked(uncompressedRequest, false);
                ctx.write(uncompressedRequest, promise);
            }
        } else if (req == null && encoderChannel != null) {
            // some content was already sent, send the last
            final ByteBuf compressedContent = ctx.channel().attr(bufAttrKey).getAndSet(null);
            finishCompress(encoderChannel, last.content(), compressedContent);
            LastHttpContent compressedRequest = last.replace(compressedContent);
            ctx.write(compressedRequest, promise);
        }
    }

    private ByteBuf allocateBuffer(ChannelHandlerContext ctx, HttpRequest req) {
        return req != null && HttpUtil.isContentLengthSet(req)
                ? ctx.alloc().directBuffer(HttpUtil.getContentLength(req, 256))
                : ctx.alloc().directBuffer();
    }

    private EmbeddedChannel createEncoderChannel(ChannelHandlerContext ctx) {
        return new EmbeddedChannel(
                ctx.channel().id(),
                ctx.channel().metadata().hasDisconnect(),
                ctx.channel().config(),
                encoderFactory.get()
        );
    }

    /**
     * finishes the compression of the remaining content and closes the channel
     * afterwards.
     *
     * @param encoderChannel the encoder channel to use
     * @param in the remaining content to compress
     * @param out the destination of the compressed content
     */
    private void finishCompress(EmbeddedChannel encoderChannel, ByteBuf in, ByteBuf out) {
        encoderChannel.writeOutbound(in);
        encoderChannel.flushOutbound();
        readAllOutput(encoderChannel, out);
        if (encoderChannel.finish()) {
            readAllOutput(encoderChannel, out);
        }
        encoderChannel.releaseOutbound();
        encoderChannel.close();
    }

    /**
     * reads all output of the given channel into the given buffer {@code dst}
     *
     * @param channel the channel to use
     * @param dst the destination of the output
     */
    private void readAllOutput(EmbeddedChannel channel, ByteBuf dst) {
        ByteBuf out;
        while ((out = channel.readOutbound()) != null) {
            if (out.isReadable()) {
                dst.writeBytes(out);
            } else {
                out.release();
            }
        }
    }

    /**
     * writes all output of the given channel to the given context {@code ctx}
     *
     * @param channel the channel to use
     * @param ctx the context to write to
     * @param promise the promise for the write operation
     */
    private void writeAllOutput(EmbeddedChannel channel, ChannelHandlerContext ctx, ChannelPromise promise) {
        ByteBuf out;
        while ((out = channel.readOutbound()) != null) {
            if (out.isReadable()) {
                ctx.write(out, promise);
            } else {
                out.release();
            }
        }
    }

    private void cleanup(ChannelHandlerContext ctx) throws Exception {
        Exception err1 = withExceptionCaught(() -> {
            ByteBuf buf = ctx.channel().attr(bufAttrKey).getAndSet(null);
            if (buf != null) {
                buf.release();
            }
        });
        Exception err2 = withExceptionCaught(() -> {
            HttpRequest req = ctx.channel().attr(reqAttrKey).getAndSet(null);
            if (req != null) {
                ReferenceCountUtil.release(req);
            }
        });
        Exception err3 = withExceptionCaught(() -> {
            EmbeddedChannel encoderChannel = ctx.channel().attr(encoderChannelAttrKey).getAndSet(null);
            if (encoderChannel != null) {
                encoderChannel.finishAndReleaseAll();
            }
        });
        if (err1 != null) {
            throw err1;
        } else if (err2 != null) {
            throw err2;
        } else if (err3 != null) {
            throw err3;
        }
    }

    /**
     * runs the given action and returns any caught exception
     *
     * @param action the action to execute
     * @return the exception caught or {@code null} if none occured
     */
    private static Exception withExceptionCaught(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Exception cause) {
            return cause;
        } catch (Throwable cause) {
            return new Exception(cause);
        }
    }

    private static boolean isContentEncodingSet(HttpMessage msg) {
        return msg.headers().contains(HttpHeaderNames.CONTENT_ENCODING);
    }

    private static void setContentEncoding(HttpMessage msg, String value) {
        msg.headers().set(HttpHeaderNames.CONTENT_ENCODING, value);
    }

    /**
     * checks if {@code value} is an instance of {@code T}.
     *
     * @param <T> the resulting type
     * @param requiredType class of the required type
     * @param value the value to check
     * @param name the name of the parameter
     * @return {@code value} casted as type {@code T}, or {@code null}
     * @throws IllegalArgumentException if {@code value != null} and not an
     * instance of {@code T}
     */
    private static <T> T requireType(Class<T> requiredType, Object value, String name) throws IllegalArgumentException {
        if (value == null || requiredType.isInstance(value)) {
            return requiredType.cast(value);
        } else {
            throw new IllegalArgumentException(
                    String.format("Param %s must be of type %s but got %s",
                            name, requiredType.getName(), value.getClass().getName()));
        }
    }
}
