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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.compression.AbstractBackpressureDecompressionHandler;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliDecompressor;
import io.netty.handler.codec.compression.Decompressor;
import io.netty.handler.codec.compression.SnappyFrameDecompressor;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdDecompressor;
import io.netty.util.internal.ObjectUtil;

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
public final class HttpDecompressionHandler extends AbstractBackpressureDecompressionHandler {
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

    private boolean endOfOutput;
    private LastHttpContent lastHttpContent;

    HttpDecompressionHandler(Builder builder) {
        super(builder);
        this.decompressionDecider = builder.decompressionDecider;
    }

    public static HttpDecompressionHandler create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
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
                lastHttpContent = null;
                endOfOutput = false;
                beginDecompression(ctx, decompressorBuilder);

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
                    fireFramingMessage(ctx, stripContent(message));
                } else {
                    fireFramingMessage(ctx, message);
                    return;
                }
            }
        }

        if (!messageCompressed) {
            // no need to estimate size
            fireFramingMessage(ctx, msg);
            return;
        }

        HttpContent content = (HttpContent) msg;

        if (content.content().isReadable()) {
            channelReadBytes(ctx, content.content());

            if (content instanceof LastHttpContent) {
                channelRead(ctx, stripData((LastHttpContent) content));
            }
        } else {
            content.content().release();

            if (content instanceof LastHttpContent) {
                messageCompressed = false;
                lastHttpContent = (LastHttpContent) content;
                channelReadEndOfInput(ctx);
                if (endOfOutput) {
                    fireEndOfOutput(ctx);
                }
            }
        }
    }

    @Override
    protected void fireEndOfOutput(ChannelHandlerContext ctx) {
        if (lastHttpContent != null) {
            fireFramingMessage(ctx, lastHttpContent);
            lastHttpContent = null;
        } else {
            endOfOutput = true;
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
    protected Object wrapOutputBuffer(ByteBuf output) {
        return new DefaultHttpContent(output);
    }

    public static final class Builder extends AbstractBackpressureDecompressionHandler.Builder {
        DecompressionDecider decompressionDecider = DecompressionDecider.DEFAULT;

        public Builder decompressionDecider(DecompressionDecider decompressionDecider) {
            this.decompressionDecider = ObjectUtil.checkNotNull(decompressionDecider, "decompressionDecider");
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
