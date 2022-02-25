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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.CodecException;
import io.netty5.handler.codec.MessageToMessageDecoder;
import io.netty5.handler.codec.compression.Decompressor;

import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.extractOrCopy;
import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.intoByteBuf;

/**
 * Decodes the content of the received {@link HttpRequest} and {@link HttpContent}.
 * The original content is replaced with the new content decoded by the
 * {@link Decompressor}, which is created by {@link #newContentDecoder(String)}.
 * Once decoding is finished, the value of the <tt>'Content-Encoding'</tt>
 * header is set to the target content encoding, as returned by {@link #getTargetContentEncoding(String)}.
 * Also, the <tt>'Content-Length'</tt> header is updated to the length of the
 * decoded content.  If the content encoding of the original is not supported
 * by the decoder, {@link #newContentDecoder(String)} should return {@code null}
 * so that no decoding occurs (i.e. pass-through).
 * <p>
 * Please note that this is an abstract class.  You have to extend this class
 * and implement {@link #newContentDecoder(String)} properly to make this class
 * functional.  For example, refer to the source code of {@link HttpContentDecompressor}.
 * <p>
 * This handler must be placed after {@link HttpObjectDecoder} in the pipeline
 * so that this handler can intercept HTTP requests after {@link HttpObjectDecoder}
 * converts {@link Buffer}s into HTTP requests.
 */
public abstract class HttpContentDecoder extends MessageToMessageDecoder<HttpObject> {

    static final String IDENTITY = HttpHeaderValues.IDENTITY.toString();

    protected ChannelHandlerContext ctx;
    private Decompressor decompressor;
    private boolean continueResponse;
    private boolean needRead = true;

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpResponse && ((HttpResponse) msg).status().code() == 100) {

            if (!(msg instanceof LastHttpContent)) {
                    continueResponse = true;
            }
            // 100-continue response must be passed through.
            fireChannelRead(ctx, msg);
            return;
        }

        if (continueResponse) {
            if (msg instanceof LastHttpContent) {
                continueResponse = false;
            }
            // 100-continue response must be passed through.
            fireChannelRead(ctx, msg);
            return;
        }

        if (msg instanceof HttpMessage) {
            cleanup();
            final HttpMessage message = (HttpMessage) msg;
            final HttpHeaders headers = message.headers();

            // Determine the content encoding.
            String contentEncoding = headers.get(HttpHeaderNames.CONTENT_ENCODING);
            if (contentEncoding != null) {
                contentEncoding = contentEncoding.trim();
            } else {
                String transferEncoding = headers.get(HttpHeaderNames.TRANSFER_ENCODING);
                if (transferEncoding != null) {
                    int idx = transferEncoding.indexOf(",");
                    if (idx != -1) {
                        contentEncoding = transferEncoding.substring(0, idx).trim();
                    } else {
                        contentEncoding = transferEncoding.trim();
                    }
                } else {
                    contentEncoding = IDENTITY;
                }
            }
            decompressor = newContentDecoder(contentEncoding);

            if (decompressor == null) {
                fireChannelRead(ctx, message);
                return;
            }

            // Remove content-length header:
            // the correct value can be set only after all chunks are processed/decoded.
            // If buffering is not an issue, add HttpObjectAggregator down the chain, it will set the header.
            // Otherwise, rely on LastHttpContent message.
            if (headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
                headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
            // Either it is already chunked or EOF terminated.
            // See https://github.com/netty/netty/issues/5892

            // set new content encoding,
            CharSequence targetContentEncoding = getTargetContentEncoding(contentEncoding);
            if (HttpHeaderValues.IDENTITY.contentEquals(targetContentEncoding)) {
                // Do NOT set the 'Content-Encoding' header if the target encoding is 'identity'
                // as per: https://tools.ietf.org/html/rfc2616#section-14.11
                headers.remove(HttpHeaderNames.CONTENT_ENCODING);
            } else {
                headers.set(HttpHeaderNames.CONTENT_ENCODING, targetContentEncoding);
            }

            if (message instanceof HttpContent) {
                // If message is a full request or response object (headers + data), don't copy data part into out.
                // Output headers only; data part will be decoded below.
                // Note: "copy" object must not be an instance of LastHttpContent class,
                // as this would (erroneously) indicate the end of the HttpMessage to other handlers.
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
                fireChannelRead(ctx, copy);
            } else {
                fireChannelRead(ctx, message);
            }
        }

        if (msg instanceof HttpContent) {
            final HttpContent<?> c = (HttpContent<?>) msg;
            if (decompressor == null) {
                fireChannelRead(ctx, c);
            } else {
                decodeContent(ctx, c);
            }
        }
    }

    private void decodeContent(ChannelHandlerContext ctx, HttpContent<?> c) {
        final Buffer payload = c.payload();
        ByteBuf content = intoByteBuf(payload);

        assert decompressor != null;

        while (!decompressor.isFinished()) {
            int idx = content.readerIndex();
            ByteBuf decompressed = decompressor.decompress(content, ctx.alloc());
            if (decompressed != null) {
                if (!decompressed.isReadable()) {
                    decompressed.release();
                    return;
                }
                ctx.fireChannelRead(new DefaultHttpContent(extractOrCopy(ctx.bufferAllocator(), decompressed)));
            } else if (idx == content.readerIndex()) {
                break;
            }
        }

        if (c instanceof LastHttpContent) {
            decompressor.close();
            decompressor = null;

            LastHttpContent<?> last = (LastHttpContent<?>) c;
            // Generate an additional chunk if the decoder produced
            // the last product on closure,
            HttpHeaders headers = last.trailingHeaders();
            if (headers.isEmpty()) {
                fireChannelRead(ctx, new EmptyLastHttpContent(ctx.bufferAllocator()));
            } else {
                fireChannelRead(ctx, new DefaultLastHttpContent(ctx.bufferAllocator().allocate(0), headers));
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        boolean needRead = this.needRead;
        this.needRead = true;

        try {
            ctx.fireChannelReadComplete();
        } finally {
            if (needRead && !ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }
    }

    /**
     * Returns a new {@link Decompressor} that decodes the HTTP message
     * content encoded in the specified <tt>contentEncoding</tt>.
     *
     * @param contentEncoding the value of the {@code "Content-Encoding"} header
     * @return a new {@link Decompressor} if the specified encoding is supported.
     *         {@code null} otherwise (alternatively, you can throw an exception
     *         to block unknown encoding).
     */
    protected abstract Decompressor newContentDecoder(String contentEncoding) throws Exception;

    /**
     * Returns the expected content encoding of the decoded content.
     * This getMethod returns {@code "identity"} by default, which is the case for
     * most decoders.
     *
     * @param contentEncoding the value of the {@code "Content-Encoding"} header
     * @return the expected content encoding of the new content
     */
    protected String getTargetContentEncoding(
            @SuppressWarnings("UnusedParameters") String contentEncoding) throws Exception {
        return IDENTITY;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanupSafely(ctx);
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanupSafely(ctx);
        super.channelInactive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        super.handlerAdded(ctx);
    }

    private void cleanup() {
        if (decompressor != null) {
            // Clean-up the previous decompressor if not cleaned up correctly.
            decompressor.close();
            decompressor = null;
        }
    }

    private void cleanupSafely(ChannelHandlerContext ctx) {
        try {
            cleanup();
        } catch (Throwable cause) {
            // If cleanup throws any error we need to propagate it through the pipeline
            // so we don't fail to propagate pipeline events.
            ctx.fireExceptionCaught(cause);
        }
    }

    private void fireChannelRead(ChannelHandlerContext ctx, Object msg) {
        needRead = false;
        ctx.fireChannelRead(msg);
    }
}
