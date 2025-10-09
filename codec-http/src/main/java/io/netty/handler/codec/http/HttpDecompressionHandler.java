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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliDecompressor;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.Decompressor;
import io.netty.handler.codec.compression.SnappyFrameDecompressor;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdDecompressor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.RecyclableArrayList;

import static io.netty.handler.codec.http.HttpHeaderValues.BR;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.SNAPPY;
import static io.netty.handler.codec.http.HttpHeaderValues.X_DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.X_GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.ZSTD;

/**
 * Decompresses an {@link HttpMessage} and an {@link HttpContent} compressed in
 * {@code gzip} or {@code deflate} encoding.
 * <p>
 * This class acts as a replacement for {@link HttpContentDecompressor}, with two advantages: It does not use an
 * {@link EmbeddedChannel}, improving performance, and it correctly handles backpressure, so that decompressors will
 * not produce unrestricted amounts of output data before downstream handlers signal that they are ready to receive
 * this data.
 */
public class HttpDecompressionHandler extends ChannelDuplexHandler {
    private final DecompressionDecider decompressionDecider;

    /**
     * Whether the current input, as seen by {@link #channelRead}, is part of a compressed message, i.e. hasn't been
     * finished by a {@link LastHttpContent} yet. Note that this is not exactly equal to the decompressed state.
     * {@code messageCompressed} can be {@code true} even when decompression is done, when the decompressed format
     * contained a delimiter that signifies an end of input, but we've not received the {@link LastHttpContent} yet.
     * Similarly, this flag can be {@code false} when end of input has been reached but there is still some compressed
     * data in the output buffer.
     */
    private boolean messageCompressed;
    /**
     * The decompressor for the current message.
     */
    private Decompressor decompressor;

    private LastHttpContent lastHttpContent;

    private RecyclableArrayList heldBack;

    private boolean reading;
    private final int messagesPerRead;

    private long downstreamMessageCount;
    private long downstreamMessageTarget;
    private long readStartMessageCount;

    private boolean discardRemainingContent;

    HttpDecompressionHandler(Builder builder) {
        this.decompressionDecider = builder.decompressionDecider;
        this.messagesPerRead = builder.messagesPerRead;
    }

    public static HttpDecompressionHandler create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private Decompressor.Status decompressorStatus(ChannelHandlerContext ctx) {
        assert decompressor != null;
        try {
            return decompressor.status();
        } catch (Exception e) {
            handleDecompressorException(ctx, e);
            return Decompressor.Status.COMPLETE;
        }
    }

    private void handleDecompressorException(ChannelHandlerContext ctx, Exception e) {
        try {
            decompressor.close();
        } catch (Exception f) {
            e.addSuppressed(f);
        }
        decompressor = null;
        if (messageCompressed) {
            discardRemainingContent = true;
        }
        ctx.fireExceptionCaught(e);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!reading) {
            reading = true;
            readStartMessageCount = downstreamMessageCount;
        }

        if (heldBack != null) {
            heldBack.add(msg);
            return;
        }

        if (!messageCompressed &&
                msg instanceof HttpMessage &&
                !(msg instanceof HttpResponse && ((HttpResponse) msg).status().code() == 100)) {
            final HttpMessage message = (HttpMessage) msg;
            final HttpHeaders headers = message.headers();

            // Determine the content encoding.
            String contentEncoding = headers.get(HttpHeaderNames.CONTENT_ENCODING);
            if (contentEncoding != null) {
                contentEncoding = contentEncoding.trim();
            } else {
                String transferEncoding = headers.get(HttpHeaderNames.TRANSFER_ENCODING);
                if (transferEncoding != null) {
                    int idx = transferEncoding.indexOf(',');
                    if (idx != -1) {
                        contentEncoding = transferEncoding.substring(0, idx).trim();
                    } else {
                        contentEncoding = transferEncoding.trim();
                    }
                } else {
                    contentEncoding = HttpContentDecoder.IDENTITY;
                }
            }

            Decompressor.AbstractDecompressorBuilder decompressorBuilder =
                    decompressionDecider.newDecompressorBuilder(contentEncoding);
            if (decompressorBuilder != null) {
                messageCompressed = true;
                decompressor = decompressorBuilder.build(ctx.alloc());
                downstreamMessageTarget = downstreamMessageCount + messagesPerRead;
                lastHttpContent = null;
                discardRemainingContent = false;
                downstreamMessageCount++; // the HttpMessage

                // Remove content-length header:
                // the correct value can be set only after all chunks are processed/decoded.
                // If buffering is not an issue, add HttpObjectAggregator down the chain, it will set the header.
                // Otherwise, rely on LastHttpContent message.
                if (headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
                    headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                    headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }

                // set new content encoding,
                CharSequence targetContentEncoding = decompressionDecider.getTargetContentEncoding(contentEncoding);
                if (HttpHeaderValues.IDENTITY.contentEquals(targetContentEncoding)) {
                    // Do NOT set the 'Content-Encoding' header if the target encoding is 'identity'
                    // as per: https://tools.ietf.org/html/rfc2616#section-14.11
                    headers.remove(HttpHeaderNames.CONTENT_ENCODING);
                } else {
                    headers.set(HttpHeaderNames.CONTENT_ENCODING, targetContentEncoding);
                }

                if (message instanceof HttpContent) {
                    ctx.fireChannelRead(stripContent(message));
                } else {
                    ctx.fireChannelRead(message);
                    return;
                }
            }
        }

        if (!messageCompressed) {
            ctx.fireChannelRead(msg);
            downstreamMessageCount++;
            return;
        }

        HttpContent content = (HttpContent) msg;

        if (decompressor == null) {
            if (content.content().isReadable() && !discardRemainingContent) {
                content.release();
                throw new DecompressionException("Additional input after compressed data");
            }
            content.release();
        } else {
            assert decompressor.status() == Decompressor.Status.NEED_INPUT : "heldBack should be set";
            if (content.content().isReadable()) {
                boolean failed = false;
                try {
                    decompressor.addInput(content.content());
                } catch (Exception e) {
                    handleDecompressorException(ctx, e);
                    failed = true;
                }
                if (!failed) {
                    forwardOutput(ctx);
                }
            } else {
                content.release();
            }
        }

        if (content instanceof LastHttpContent) {
            LastHttpContent last = stripData((LastHttpContent) content);
            if (decompressor == null) {
                // done
                messageCompressed = false;
                ctx.fireChannelRead(last);
            } else if (decompressorStatus(ctx) == Decompressor.Status.NEED_INPUT) {
                decompressor.endOfInput();
                messageCompressed = false;
                lastHttpContent = last;
                forwardOutput(ctx);
            } else {
                assert heldBack != null : "should have been set by forwardOutput";
                heldBack.add(last);
            }
        }
    }

    private static HttpMessage stripContent(HttpMessage message) {
        HttpMessage copy;
        if (message instanceof HttpRequest) {
            HttpRequest r = (HttpRequest) message; // HttpRequest or FullHttpRequest
            copy = new DefaultHttpRequest(r.protocolVersion(), r.method(), r.uri());
        } else if (message instanceof HttpResponse) {
            HttpResponse r = (HttpResponse) message; // HttpResponse or FullHttpResponse
            copy = new DefaultHttpResponse(r.protocolVersion(), r.status());
        } else {
            throw new CodecException("Object of class " + message.getClass().getName() +
                    " is not an HttpRequest or HttpResponse");
        }
        copy.headers().set(message.headers());
        copy.setDecoderResult(message.decoderResult());
        return copy;
    }

    private static LastHttpContent stripData(LastHttpContent content) {
        HttpHeaders trailingHeaders = content.trailingHeaders();
        if (trailingHeaders == null || trailingHeaders.isEmpty()) {
            return LastHttpContent.EMPTY_LAST_CONTENT;
        } else {
            return new ComposedLastHttpContent(trailingHeaders, DecoderResult.SUCCESS);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        do {
            if (readStartMessageCount == downstreamMessageCount) {
                // we didn't forward any messages, so we need to ask upstream for more
                reading = false;
                if (!ctx.channel().config().isAutoRead()) {
                    ctx.read();
                }
                return;
            }

            // accept that we might not have hit the target.
            downstreamMessageTarget = downstreamMessageCount;

            ctx.fireChannelReadComplete();
            reading = false;

        } while (fulfillDemandOutsideRead(ctx));
    }

    /**
     * @return {@code true} if {@link #channelReadComplete(ChannelHandlerContext)} should be called next. This is to
     * avoid recursion
     */
    private boolean fulfillDemandOutsideRead(ChannelHandlerContext ctx) throws Exception {
        assert !reading;

        if (decompressor == null) {
            return false;
        }
        boolean autoRead = ctx.channel().config().isAutoRead();
        if (downstreamMessageTarget <= downstreamMessageCount && !autoRead) {
            return false;
        }

        RecyclableArrayList heldBack = this.heldBack;
        if (heldBack == null) {
            if (!autoRead) {
                ctx.read();
            }
            return false;
        }

        reading = true;
        readStartMessageCount = downstreamMessageCount;
        forwardOutput(ctx);
        if (decompressor == null || decompressorStatus(ctx) != Decompressor.Status.NEED_OUTPUT) {
            this.heldBack = null;
            if (heldBack.isEmpty() && readStartMessageCount == downstreamMessageCount) {
                heldBack.recycle();
                return false;
            } else {
                // this sets reading = true
                for (Object msg : heldBack) {
                    channelRead(ctx, msg);
                }
                heldBack.recycle();
                return true; // channelReadComplete(ctx)
            }
        } else {
            if (readStartMessageCount != downstreamMessageCount) {
                return true; // channelReadComplete(ctx)
            }
            ctx.read();
            return false;
        }
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (decompressor == null && !messageCompressed) {
            ctx.read();
            return;
        }

        downstreamMessageTarget = downstreamMessageCount + messagesPerRead;
        if (!reading) {
            if (fulfillDemandOutsideRead(ctx)) {
                channelReadComplete(ctx);
            }
        }
    }

    private void forwardOutput(ChannelHandlerContext ctx) {
        while (true) {
            Decompressor.Status status = decompressorStatus(ctx);
            switch (status) {
                case NEED_OUTPUT:
                    if (downstreamMessageTarget <= downstreamMessageCount && !ctx.channel().config().isAutoRead()) {
                        if (heldBack == null) {
                            heldBack = RecyclableArrayList.newInstance();
                        }
                        return;
                    }
                    downstreamMessageCount++;
                    ByteBuf output;
                    try {
                        output = decompressor.takeOutput();
                    } catch (Exception e) {
                        handleDecompressorException(ctx, e);
                        return;
                    }
                    ctx.fireChannelRead(new DefaultHttpContent(output));
                    break;
                case NEED_INPUT:
                    return;
                case COMPLETE:
                    if (decompressor != null) {
                        try {
                            decompressor.close();
                        } catch (Exception e) {
                            ctx.fireExceptionCaught(e);
                        }
                    }
                    decompressor = null;
                    downstreamMessageCount++;
                    if (lastHttpContent != null) {
                        ctx.fireChannelRead(lastHttpContent);
                        lastHttpContent = null;
                    }
                    return;
                default:
                    throw new AssertionError("Unknown status: " + status);
            }
        }
    }

    public static final class Builder {
        DecompressionDecider decompressionDecider = DecompressionDecider.DEFAULT;
        int messagesPerRead = 64;

        public Builder decompressionDecider(DecompressionDecider decompressionDecider) {
            this.decompressionDecider = ObjectUtil.checkNotNull(decompressionDecider, "decompressionDecider");
            return this;
        }

        public Builder messagesPerRead(int messagesPerRead) {
            this.messagesPerRead = ObjectUtil.checkPositive(messagesPerRead, "messagesPerRead");
            return this;
        }

        public HttpDecompressionHandler build() {
            return new HttpDecompressionHandler(this);
        }
    }

    public interface DecompressionDecider {
        DecompressionDecider DEFAULT = contentEncoding -> {
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
        };

        /**
         * Returns the expected content encoding of the decoded content.
         * This getMethod returns {@code "identity"} by default, which is the case for
         * most decoders.
         *
         * @param contentEncoding the value of the {@code "Content-Encoding"} header
         * @return the expected content encoding of the new content
         */
        default String getTargetContentEncoding(String contentEncoding) throws Exception {
            return HttpContentDecoder.IDENTITY;
        }

        Decompressor.AbstractDecompressorBuilder newDecompressorBuilder(String contentEncoding) throws Exception;
    }
}
