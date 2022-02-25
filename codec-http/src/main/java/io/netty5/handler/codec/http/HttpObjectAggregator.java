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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.Send;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.handler.codec.MessageAggregatorNew;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import static io.netty5.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty5.handler.codec.http.HttpHeaderNames.EXPECT;
import static io.netty5.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty5.handler.codec.http.HttpResponseStatus.EXPECTATION_FAILED;
import static io.netty5.handler.codec.http.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static io.netty5.handler.codec.http.HttpUtil.getContentLength;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.util.Objects.requireNonNullElse;

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
 * <pre>
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
 * <tbody>
 * <tr>
 * <th>Response Status</th>
 * <th>Condition When Sent</th>
 * </tr>
 * <tr>
 * <td>100 Continue</td>
 * <td>A '100-continue' expectation is received and the 'content-length' doesn't exceed maxContentLength</td>
 * </tr>
 * <tr>
 * <td>417 Expectation Failed</td>
 * <td>A '100-continue' expectation is received and the 'content-length' exceeds maxContentLength</td>
 * </tr>
 * <tr>
 * <td>413 Request Entity Too Large</td>
 * <td>Either the 'content-length' or the bytes received so far exceed maxContentLength</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * @see FullHttpRequest
 * @see FullHttpResponse
 * @see HttpResponseDecoder
 * @see HttpServerCodec
 */
public class HttpObjectAggregator<C extends HttpContent<C>>
        extends MessageAggregatorNew<HttpObject, HttpMessage, HttpContent<C>, FullHttpMessage<?>> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpObjectAggregator.class);

    private final boolean closeOnExpectationFailed;

    /**
     * Creates a new instance.
     *
     * @param maxContentLength the maximum length of the aggregated content in bytes.
     * If the length of the aggregated content exceeds this value,
     * {@link #handleOversizedMessage(ChannelHandlerContext, Object)} will be called.
     */
    public HttpObjectAggregator(int maxContentLength) {
        this(maxContentLength, false);
    }

    /**
     * Creates a new instance.
     *
     * @param maxContentLength the maximum length of the aggregated content in bytes.
     * If the length of the aggregated content exceeds this value,
     * {@link #handleOversizedMessage(ChannelHandlerContext, Object)} will be called.
     * @param closeOnExpectationFailed If a 100-continue response is detected but the content length is too large
     * then {@code true} means close the connection. otherwise the connection will remain open and data will be
     * consumed and discarded until the next request is received.
     */
    public HttpObjectAggregator(int maxContentLength, boolean closeOnExpectationFailed) {
        super(maxContentLength);
        this.closeOnExpectationFailed = closeOnExpectationFailed;
    }

    @Override
    protected HttpMessage tryStartMessage(Object msg) {
        return msg instanceof HttpMessage ? (HttpMessage) msg : null;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected HttpContent<C> tryContentMessage(Object msg) {
        return msg instanceof HttpContent ? (HttpContent<C>) msg : null;
    }

    @Override
    protected boolean isAggregated(Object msg) throws Exception {
        return msg instanceof FullHttpMessage;
    }

    @Override
    protected int lengthForContent(HttpContent<C> msg) {
        return msg.payload().readableBytes();
    }

    @Override
    protected int lengthForAggregation(FullHttpMessage<?> msg) {
        return msg.payload().readableBytes();
    }

    @Override
    protected boolean isLastContentMessage(HttpContent<C> msg) throws Exception {
        return msg instanceof LastHttpContent;
    }

    @Override
    protected boolean isContentLengthInvalid(HttpMessage start, int maxContentLength) {
        try {
            return getContentLength(start, -1L) > maxContentLength;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    private static FullHttpResponse continueResponse(HttpMessage start, int maxContentLength,
                                                     ChannelPipeline pipeline) {
        if (HttpUtil.isUnsupportedExpectation(start)) {
            // if the request contains an unsupported expectation, we return 417
            pipeline.fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
            return newErrorResponse(EXPECTATION_FAILED, pipeline.channel().bufferAllocator(), true, false);
        } else if (HttpUtil.is100ContinueExpected(start)) {
            // if the request contains 100-continue but the content-length is too large, we return 413
            if (getContentLength(start, -1L) <= maxContentLength) {
                return newErrorResponse(CONTINUE, pipeline.channel().bufferAllocator(), false, false);
            }
            pipeline.fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
            return newErrorResponse(REQUEST_ENTITY_TOO_LARGE, pipeline.channel().bufferAllocator(), true, false);
        }

        return null;
    }

    @Override
    protected Object newContinueResponse(HttpMessage start, int maxContentLength, ChannelPipeline pipeline) {
        FullHttpResponse response = continueResponse(start, maxContentLength, pipeline);
        // we're going to respond based on the request expectation so there's no
        // need to propagate the expectation further.
        if (response != null) {
            start.headers().remove(EXPECT);
        }
        return response;
    }

    @Override
    protected boolean closeAfterContinueResponse(Object msg) {
        return closeOnExpectationFailed && ignoreContentAfterContinueResponse(msg);
    }

    @Override
    protected boolean ignoreContentAfterContinueResponse(Object msg) {
        if (msg instanceof HttpResponse) {
            final HttpResponse httpResponse = (HttpResponse) msg;
            return httpResponse.status().codeClass().equals(HttpStatusClass.CLIENT_ERROR);
        }
        return false;
    }

    @Override
    protected FullHttpMessage<?> beginAggregation(BufferAllocator allocator, HttpMessage start) throws Exception {
        assert !(start instanceof FullHttpMessage);

        HttpUtil.setTransferEncodingChunked(start, false);

        final CompositeBuffer content = CompositeBuffer.compose(allocator);
        FullHttpMessage<?> ret;
        if (start instanceof HttpRequest) {
            ret = new AggregatedFullHttpRequest((HttpRequest) start, content, null);
        } else if (start instanceof HttpResponse) {
            ret = new AggregatedFullHttpResponse((HttpResponse) start, content, null);
        } else {
            throw new Error();
        }
        return ret;
    }

    @Override
    protected void aggregate(BufferAllocator allocator, FullHttpMessage<?> aggregated,
                             HttpContent<C> content) throws Exception {
        final CompositeBuffer payload = (CompositeBuffer) aggregated.payload();
        payload.extendWith(content.payload().send());
        if (content instanceof LastHttpContent) {
            // Merge trailing headers into the message.
            ((AggregatedFullHttpMessage<?>) aggregated).setTrailingHeaders(((LastHttpContent<?>) content)
                    .trailingHeaders());
        }
    }

    @Override
    protected void finishAggregation(BufferAllocator allocator, FullHttpMessage<?> aggregated) throws Exception {
        // Set the 'Content-Length' header. If one isn't already set.
        // This is important as HEAD responses will use a 'Content-Length' header which
        // does not match the actual body, but the number of bytes that would be
        // transmitted if a GET would have been used.
        //
        // See rfc2616 14.13 Content-Length
        if (!HttpUtil.isContentLengthSet(aggregated)) {
            aggregated.headers()
                    .set(CONTENT_LENGTH, String.valueOf(aggregated.payload().readableBytes()));
        }
    }

    @Override
    protected void handleOversizedMessage(final ChannelHandlerContext ctx, Object oversized) throws Exception {
        if (oversized instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) oversized;
            // send back a 413 and close the connection

            // If the client started to send data already, close because it's impossible to recover.
            // If keep-alive is off and 'Expect: 100-continue' is missing, no need to leave the connection open.
            if (oversized instanceof FullHttpMessage ||
                    !HttpUtil.is100ContinueExpected(request) && !HttpUtil.isKeepAlive(request)) {
                Future<Void> future = ctx.writeAndFlush(newErrorResponse(REQUEST_ENTITY_TOO_LARGE,
                        ctx.bufferAllocator(), true, true));
                future.addListener(f -> {
                    if (f.isFailed()) {
                        logger.debug("Failed to send a 413 Request Entity Too Large.", f.cause());
                    }
                    ctx.close();
                });
            } else {
                ctx.writeAndFlush(newErrorResponse(REQUEST_ENTITY_TOO_LARGE,
                                ctx.bufferAllocator(), true, false))
                        .addListener(future -> {
                            if (future.isFailed()) {
                                logger.debug("Failed to send a 413 Request Entity Too Large.", future.cause());
                                ctx.close();
                            }
                        });
            }
        } else if (oversized instanceof HttpResponse) {
            throw new ResponseTooLargeException("Response entity too large: " + oversized);
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        if (cause instanceof ResponseTooLargeException) {
            ctx.close();
        }
    }

    private static FullHttpResponse newErrorResponse(HttpResponseStatus status, BufferAllocator allocator,
                                                     boolean emptyContent, boolean closeConnection) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status, allocator.allocate(0));
        if (emptyContent) {
            resp.headers().set(CONTENT_LENGTH, 0);
        }
        if (closeConnection) {
            resp.headers().set(CONNECTION, HttpHeaderValues.CLOSE);
        }
        return resp;
    }

    private static final class ResponseTooLargeException extends TooLongHttpContentException {
        ResponseTooLargeException(String message) {
            super(message);
        }
    }

    private abstract static class AggregatedFullHttpMessage<R extends FullHttpMessage<R>>
            implements FullHttpMessage<R> {
        protected final HttpMessage message;
        private final Buffer payload;
        private HttpHeaders trailingHeaders;

        AggregatedFullHttpMessage(HttpMessage message, Buffer payload, HttpHeaders trailingHeaders) {
            this.message = message;
            this.payload = payload;
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public void close() {
            payload.close();
        }

        @Override
        public boolean isAccessible() {
            return payload.isAccessible();
        }

        @Override
        public Buffer payload() {
            return payload;
        }

        @Override
        public HttpHeaders trailingHeaders() {
            HttpHeaders trailingHeaders = this.trailingHeaders;
            return requireNonNullElse(trailingHeaders, EmptyHttpHeaders.INSTANCE);
        }

        void setTrailingHeaders(HttpHeaders trailingHeaders) {
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return message.protocolVersion();
        }

        @Override
        public HttpVersion protocolVersion() {
            return message.protocolVersion();
        }

        @Override
        public FullHttpMessage<R> setProtocolVersion(HttpVersion version) {
            message.setProtocolVersion(version);
            return this;
        }

        @Override
        public HttpHeaders headers() {
            return message.headers();
        }

        @Override
        public DecoderResult decoderResult() {
            return message.decoderResult();
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            message.setDecoderResult(result);
        }
    }

    private static final class AggregatedFullHttpRequest extends AggregatedFullHttpMessage<FullHttpRequest>
            implements FullHttpRequest {

        AggregatedFullHttpRequest(HttpRequest request, Buffer content, HttpHeaders trailingHeaders) {
            super(request, content, trailingHeaders);
        }

        @Override
        public Send<FullHttpRequest> send() {
            return payload().send().map(FullHttpRequest.class,
                    p -> new AggregatedFullHttpRequest(this, p, trailingHeaders()));
        }

        @Override
        public FullHttpRequest touch(Object hint) {
            payload().touch(hint);
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
        public HttpMethod method() {
            return ((HttpRequest) message).method();
        }

        @Override
        public String uri() {
            return ((HttpRequest) message).uri();
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

    private static final class AggregatedFullHttpResponse extends AggregatedFullHttpMessage<FullHttpResponse>
            implements FullHttpResponse {

        AggregatedFullHttpResponse(HttpResponse message, Buffer content, HttpHeaders trailingHeaders) {
            super(message, content, trailingHeaders);
        }

        @Override
        public Send<FullHttpResponse> send() {
            return payload().send().map(FullHttpResponse.class,
                    p -> new AggregatedFullHttpResponse(this, p, trailingHeaders()));
        }

        @Override
        public FullHttpResponse touch(Object hint) {
            payload().touch(hint);
            return this;
        }

        @Override
        public FullHttpResponse setStatus(HttpResponseStatus status) {
            ((HttpResponse) message).setStatus(status);
            return this;
        }

        @Override
        public HttpResponseStatus status() {
            return ((HttpResponse) message).status();
        }

        @Override
        public FullHttpResponse setProtocolVersion(HttpVersion version) {
            super.setProtocolVersion(version);
            return this;
        }

        @Override
        public String toString() {
            return HttpMessageUtil.appendFullResponse(new StringBuilder(256), this).toString();
        }
    }
}
