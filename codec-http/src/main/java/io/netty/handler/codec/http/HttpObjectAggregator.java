/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpHeaders.Names;

import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isContentLengthSet;
import static io.netty.handler.codec.http.HttpHeaders.removeTransferEncodingChunked;

/**
 * A {@link ChannelHandler} that aggregates an {@link HttpMessage}
 * and its following {@link HttpContent}s into a single {@link FullHttpRequest}
 * or {@link FullHttpResponse} (depending on if it used to handle requests or responses)
 * with no following {@link HttpContent}s.  It is useful when you don't want to take
 * care of HTTP messages whose transfer encoding is 'chunked'.  Insert this
 * handler after {@link HttpResponseDecoder} in the {@link ChannelPipeline} if being used to handle
 * responses, or after {@link HttpRequestDecoder} and {@link HttpResponseEncoder} in the
 * {@link ChannelPipeline} if being used to handle requests.
 * <blockquote>
 *  <pre>
 *  {@link ChannelPipeline} p = ...;
 *  ...
 *  p.addLast("decoder", <b>new {@link HttpRequestDecoder}()</b>);
 *  p.addLast("encoder", <b>new {@link HttpResponseEncoder}()</b>);
 *  p.addLast("aggregator", <b>new {@link HttpObjectAggregator}(1048576)</b>);
 *  ...
 *  p.addLast("handler", new HttpRequestHandler());
 *  </pre>
 * </blockquote>
 * <p>
 * For convenience, consider putting a {@link HttpServerCodec} before the {@link HttpObjectAggregator}
 * as it functions as both a {@link HttpRequestDecoder} and a {@link HttpResponseEncoder}.
 * </p>
 * Be aware that {@link HttpObjectAggregator} may end up sending a {@link HttpResponse}:
 * <table border summary="Possible Responses">
 *   <tbody>
 *     <tr>
 *       <th>Response Status</th>
 *       <th>Condition When Sent</th>
 *     </tr>
 *     <tr>
 *       <td>100 Continue</td>
 *       <td>A '100-continue' expectation is received and the 'content-length' doesn't exceed maxContentLength</td>
 *     </tr>
 *     <tr>
 *       <td>417 Expectation Failed</td>
 *       <td>A '100-continue' expectation is received and the 'content-length' exceeds maxContentLength</td>
 *     </tr>
 *     <tr>
 *       <td>413 Request Entity Too Large</td>
 *       <td>Either the 'content-length' or the bytes received so far exceed maxContentLength</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * @see FullHttpRequest
 * @see FullHttpResponse
 * @see HttpResponseDecoder
 * @see HttpServerCodec
 */
public class HttpObjectAggregator extends MessageToMessageDecoder<HttpObject> {
    public static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;
    private static final FullHttpResponse CONTINUE =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse EXPECTATION_FAILED = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED, Unpooled.EMPTY_BUFFER);

    static {
        HttpHeaders.setContentLength(EXPECTATION_FAILED, 0);
    }

    private final int maxContentLength;
    private AggregatedFullHttpMessage currentMessage;
    private final boolean closeOnExpectationFailed;

    private int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;
    private ChannelHandlerContext ctx;

    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content in bytes.
     *        If the length of the aggregated content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public HttpObjectAggregator(int maxContentLength) {
        this(maxContentLength, false);
    }

    /**
     * Creates a new instance.
     * @param maxContentLength
     *        the maximum length of the aggregated content in bytes.
     *        If the length of the aggregated content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     * @param closeOnExpectationFailed If a 100-continue response is detected but the content length is too large
     * then {@code true} means close the connection. otherwise the connection will remain open and data will be
     * consumed and discarded until the next request is received.
     */
    public HttpObjectAggregator(int maxContentLength, boolean closeOnExpectationFailed) {
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException("maxContentLength must be a positive integer: " + maxContentLength);
        }
        this.maxContentLength = maxContentLength;
        this.closeOnExpectationFailed = closeOnExpectationFailed;
    }
    /**
     * Returns the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}.
     */
    public final int getMaxCumulationBufferComponents() {
        return maxCumulationBufferComponents;
    }

    /**
     * Sets the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}
     * and its minimum allowed value is {@code 2}.
     */
    public final void setMaxCumulationBufferComponents(int maxCumulationBufferComponents) {
        if (maxCumulationBufferComponents < 2) {
            throw new IllegalArgumentException(
                    "maxCumulationBufferComponents: " + maxCumulationBufferComponents +
                    " (expected: >= 2)");
        }

        if (ctx == null) {
            this.maxCumulationBufferComponents = maxCumulationBufferComponents;
        } else {
            throw new IllegalStateException(
                    "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        if (msg instanceof HttpMessage) {
            if (currentMessage != null) {
                currentMessage.release();
                currentMessage = null;
                throw new IllegalStateException("Start of new message received before existing message completed.");
            }
            HttpMessage m = (HttpMessage) msg;

            // Handle the 'Expect: 100-continue' header if necessary.
            if (is100ContinueExpected(m)) {
                if (HttpHeaders.getContentLength(m, 0) > maxContentLength) {
                    final ChannelFuture future = ctx.writeAndFlush(EXPECTATION_FAILED.duplicate().retain());
                    future.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                ctx.fireExceptionCaught(future.cause());
                            }
                        }
                    });
                    if (closeOnExpectationFailed) {
                        future.addListener(ChannelFutureListener.CLOSE);
                    }
                    ctx.pipeline().fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
                    return;
                }
                ctx.writeAndFlush(CONTINUE.duplicate().retain()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            ctx.fireExceptionCaught(future.cause());
                        }
                    }
                });
            }

            if (!m.getDecoderResult().isSuccess()) {
                removeTransferEncodingChunked(m);
                out.add(toFullMessage(m));
                return;
            }
            if (msg instanceof HttpRequest) {
                HttpRequest header = (HttpRequest) msg;
                currentMessage = new AggregatedFullHttpRequest(
                        header, ctx.alloc().compositeBuffer(maxCumulationBufferComponents), null);
            } else if (msg instanceof HttpResponse) {
                HttpResponse header = (HttpResponse) msg;
                currentMessage = new AggregatedFullHttpResponse(
                        header, ctx.alloc().compositeBuffer(maxCumulationBufferComponents), null);
            } else {
                throw new Error();
            }

            // A streamed message - initialize the cumulative buffer, and wait for incoming chunks.
            removeTransferEncodingChunked(currentMessage);
        } else if (msg instanceof HttpContent) {
            if (currentMessage == null) {
                // it is possible that a TooLongFrameException was already thrown but we can still discard data
                // until the begging of the next request/response.
                return;
            }

            // Merge the received chunk into the content of the current message.
            HttpContent chunk = (HttpContent) msg;
            CompositeByteBuf content = (CompositeByteBuf) currentMessage.content();

            if (content.readableBytes() > maxContentLength - chunk.content().readableBytes()) {
                // release current message to prevent leaks
                currentMessage.release();
                currentMessage = null;

                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength +
                        " bytes.");
            }

            // Append the content of the chunk
            if (chunk.content().isReadable()) {
                content.addComponent(true, chunk.content().retain());
            }

            final boolean last;
            if (!chunk.getDecoderResult().isSuccess()) {
                currentMessage.setDecoderResult(
                        DecoderResult.failure(chunk.getDecoderResult().cause()));
                last = true;
            } else {
                last = chunk instanceof LastHttpContent;
            }

            if (last) {
                // Merge trailing headers into the message.
                if (chunk instanceof LastHttpContent) {
                    LastHttpContent trailer = (LastHttpContent) chunk;
                    currentMessage.setTrailingHeaders(trailer.trailingHeaders());
                } else {
                    currentMessage.setTrailingHeaders(new DefaultHttpHeaders());
                }

                // Set the 'Content-Length' header. If one isn't already set.
                // This is important as HEAD responses will use a 'Content-Length' header which
                // does not match the actual body, but the number of bytes that would be
                // transmitted if a GET would have been used.
                //
                // See rfc2616 14.13 Content-Length
                if (!isContentLengthSet(currentMessage)) {
                    currentMessage.headers().set(
                            Names.CONTENT_LENGTH,
                            String.valueOf(content.readableBytes()));
                }
                // Set our currentMessage member variable to null in case adding to out will cause re-entry.
                AggregatedFullHttpMessage currentMessage = this.currentMessage;
                this.currentMessage = null;
                out.add(currentMessage);
            }
        } else {
            throw new Error();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            super.channelInactive(ctx);
        } finally {
            // release current message if it is not null as it may be a left-over
            releaseCurrentMessage();
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            super.handlerRemoved(ctx);
        } finally {
            // release current message if it is not null as it may be a left-over as there is not much more we can do in
            // this case
            releaseCurrentMessage();
        }
    }

    private void releaseCurrentMessage() {
        if (currentMessage != null) {
            currentMessage.release();
            currentMessage = null;
        }
    }

    private static FullHttpMessage toFullMessage(HttpMessage msg) {
        if (msg instanceof FullHttpMessage) {
            return ((FullHttpMessage) msg).retain();
        }

        FullHttpMessage fullMsg;
        if (msg instanceof HttpRequest) {
            fullMsg = new AggregatedFullHttpRequest(
                    (HttpRequest) msg, Unpooled.EMPTY_BUFFER, new DefaultHttpHeaders());
        } else if (msg instanceof HttpResponse) {
            fullMsg = new AggregatedFullHttpResponse(
                    (HttpResponse) msg, Unpooled.EMPTY_BUFFER, new DefaultHttpHeaders());
        } else {
            throw new IllegalStateException();
        }

        return fullMsg;
    }

    private abstract static class AggregatedFullHttpMessage implements ByteBufHolder, FullHttpMessage {
        protected final HttpMessage message;
        private final ByteBuf content;
        private HttpHeaders trailingHeaders;

        AggregatedFullHttpMessage(HttpMessage message, ByteBuf content, HttpHeaders trailingHeaders) {
            this.message = message;
            this.content = content;
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public HttpHeaders trailingHeaders() {
            HttpHeaders trailingHeaders = this.trailingHeaders;
            if (trailingHeaders == null) {
                return HttpHeaders.EMPTY_HEADERS;
            } else {
                return trailingHeaders;
            }
        }

        void setTrailingHeaders(HttpHeaders trailingHeaders) {
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return message.getProtocolVersion();
        }

        @Override
        public FullHttpMessage setProtocolVersion(HttpVersion version) {
            message.setProtocolVersion(version);
            return this;
        }

        @Override
        public HttpHeaders headers() {
            return message.headers();
        }

        @Override
        public DecoderResult getDecoderResult() {
            return message.getDecoderResult();
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            message.setDecoderResult(result);
        }

        @Override
        public ByteBuf content() {
            return content;
        }

        @Override
        public int refCnt() {
            return content.refCnt();
        }

        @Override
        public FullHttpMessage retain() {
            content.retain();
            return this;
        }

        @Override
        public FullHttpMessage retain(int increment) {
            content.retain(increment);
            return this;
        }

        @Override
        public boolean release() {
            return content.release();
        }

        @Override
        public boolean release(int decrement) {
            return content.release(decrement);
        }

        @Override
        public abstract FullHttpMessage copy();

        @Override
        public abstract FullHttpMessage duplicate();
    }

    private static final class AggregatedFullHttpRequest extends AggregatedFullHttpMessage implements FullHttpRequest {

        AggregatedFullHttpRequest(HttpRequest request, ByteBuf content, HttpHeaders trailingHeaders) {
            super(request, content, trailingHeaders);
        }

        @Override
        public FullHttpRequest copy() {
            DefaultFullHttpRequest copy = new DefaultFullHttpRequest(
                    getProtocolVersion(), getMethod(), getUri(), content().copy());
            copy.headers().set(headers());
            copy.trailingHeaders().set(trailingHeaders());
            return copy;
        }

        @Override
        public FullHttpRequest duplicate() {
            DefaultFullHttpRequest duplicate = new DefaultFullHttpRequest(
                    getProtocolVersion(), getMethod(), getUri(), content().duplicate());
            duplicate.headers().set(headers());
            duplicate.trailingHeaders().set(trailingHeaders());
            return duplicate;
        }

        @Override
        public FullHttpRequest retain(int increment) {
            super.retain(increment);
            return this;
        }

        @Override
        public FullHttpRequest retain() {
            super.retain();
            return this;
        }

        @Override
        public FullHttpRequest setMethod(HttpMethod method) {
            ((HttpRequest) message).setMethod(method);
            return this;
        }

        @Override
        public FullHttpRequest setUri(String uri) {
            ((HttpRequest) message).setUri(uri);
            return this;
        }

        @Override
        public HttpMethod getMethod() {
            return ((HttpRequest) message).getMethod();
        }

        @Override
        public String getUri() {
            return ((HttpRequest) message).getUri();
        }

        @Override
        public FullHttpRequest setProtocolVersion(HttpVersion version) {
            super.setProtocolVersion(version);
            return this;
        }

        @Override
        public String toString() {
            return HttpMessageUtil.appendFullRequest(new StringBuilder(256), this).toString();
        }
    }

    private static final class AggregatedFullHttpResponse extends AggregatedFullHttpMessage
            implements FullHttpResponse {

        AggregatedFullHttpResponse(HttpResponse message, ByteBuf content, HttpHeaders trailingHeaders) {
            super(message, content, trailingHeaders);
        }

        @Override
        public FullHttpResponse copy() {
            DefaultFullHttpResponse copy = new DefaultFullHttpResponse(
                    getProtocolVersion(), getStatus(), content().copy());
            copy.headers().set(headers());
            copy.trailingHeaders().set(trailingHeaders());
            return copy;
        }

        @Override
        public FullHttpResponse duplicate() {
            DefaultFullHttpResponse duplicate = new DefaultFullHttpResponse(getProtocolVersion(), getStatus(),
                    content().duplicate());
            duplicate.headers().set(headers());
            duplicate.trailingHeaders().set(trailingHeaders());
            return duplicate;
        }

        @Override
        public FullHttpResponse setStatus(HttpResponseStatus status) {
            ((HttpResponse) message).setStatus(status);
            return this;
        }

        @Override
        public HttpResponseStatus getStatus() {
            return ((HttpResponse) message).getStatus();
        }

        @Override
        public FullHttpResponse setProtocolVersion(HttpVersion version) {
            super.setProtocolVersion(version);
            return this;
        }

        @Override
        public FullHttpResponse retain(int increment) {
            super.retain(increment);
            return this;
        }

        @Override
        public FullHttpResponse retain() {
            super.retain();
            return this;
        }

        @Override
        public String toString() {
            return HttpMessageUtil.appendFullResponse(new StringBuilder(256), this).toString();
        }
    }
}
