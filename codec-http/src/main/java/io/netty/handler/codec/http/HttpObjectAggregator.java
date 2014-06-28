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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageAggregator;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * A {@link ChannelHandler} that aggregates an {@link HttpMessage}
 * and its following {@link HttpContent}s into a single {@link FullHttpRequest}
 * or {@link FullHttpResponse} (depending on if it used to handle requests or responses)
 * with no following {@link HttpContent}s.  It is useful when you don't want to take
 * care of HTTP messages whose transfer encoding is 'chunked'.  Insert this
 * handler after {@link HttpObjectDecoder} in the {@link ChannelPipeline}:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * ...
 * p.addLast("encoder", new {@link HttpResponseEncoder}());
 * p.addLast("decoder", new {@link HttpRequestDecoder}());
 * p.addLast("aggregator", <b>new {@link HttpObjectAggregator}(1048576)</b>);
 * ...
 * p.addLast("handler", new HttpRequestHandler());
 * </pre>
 * Be aware that you need to have the {@link HttpResponseEncoder} or {@link HttpRequestEncoder}
 * before the {@link HttpObjectAggregator} in the {@link ChannelPipeline}.
 */
public class HttpObjectAggregator
        extends MessageAggregator<HttpObject, HttpMessage, HttpContent, FullHttpMessage> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpObjectAggregator.class);

    private static final FullHttpResponse CONTINUE = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse TOO_LARGE = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER);

    static {
        TOO_LARGE.headers().set(HttpHeaders.Names.CONTENT_LENGTH, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content.
     *        If the length of the aggregated content exceeds this value,
     *        {@link #handleOversizedMessage(ChannelHandlerContext, HttpMessage)}
     *        will be called.
     */
    public HttpObjectAggregator(int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    protected boolean isStartMessage(HttpObject msg) throws Exception {
        return msg instanceof HttpMessage;
    }

    @Override
    protected boolean isContentMessage(HttpObject msg) throws Exception {
        return msg instanceof HttpContent;
    }

    @Override
    protected boolean isLastContentMessage(HttpContent msg) throws Exception {
        return msg instanceof LastHttpContent;
    }

    @Override
    protected boolean isAggregated(HttpObject msg) throws Exception {
        return msg instanceof FullHttpMessage;
    }

    @Override
    protected boolean hasContentLength(HttpMessage start) throws Exception {
        return HttpHeaders.isContentLengthSet(start);
    }

    @Override
    protected long contentLength(HttpMessage start) throws Exception {
        return HttpHeaders.getContentLength(start);
    }

    @Override
    protected Object newContinueResponse(HttpMessage start) throws Exception {
        if (HttpHeaders.is100ContinueExpected(start)) {
            return CONTINUE;
        } else {
            return null;
        }
    }

    @Override
    protected FullHttpMessage beginAggregation(HttpMessage start, ByteBuf content) throws Exception {
        assert !(start instanceof FullHttpMessage);

        HttpHeaders.removeTransferEncodingChunked(start);

        FullHttpMessage ret;
        if (start instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) start;
            ret = new DefaultFullHttpRequest(req.protocolVersion(),
                    req.method(), req.uri(), content);
        } else if (start instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) start;
            ret = new DefaultFullHttpResponse(
                    res.protocolVersion(), res.status(), content);
        } else {
            throw new Error();
        }

        ret.headers().set(start.headers());
        ret.setDecoderResult(start.decoderResult());
        return ret;
    }

    @Override
    protected void aggregate(FullHttpMessage aggregated, HttpContent content) throws Exception {
        if (content instanceof LastHttpContent) {
            // Merge trailing headers into the message.
            aggregated.headers().add(((LastHttpContent) content).trailingHeaders());
        }
    }

    @Override
    protected void finishAggregation(FullHttpMessage aggregated) throws Exception {
        // Set the 'Content-Length' header.
        aggregated.headers().set(
                HttpHeaders.Names.CONTENT_LENGTH,
                String.valueOf(aggregated.content().readableBytes()));
    }

    @Override
    protected void handleOversizedMessage(final ChannelHandlerContext ctx, HttpMessage oversized) throws Exception {
        if (oversized instanceof HttpRequest) {
            // send back a 413 and close the connection
            ChannelFuture future = ctx.writeAndFlush(TOO_LARGE).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        logger.debug("Failed to send a 413 Request Entity Too Large.", future.cause());
                        ctx.close();
                    }
                }
            });

            // If the client started to send data already, close because it's impossible to recover.
            // If 'Expect: 100-continue' is missing, close becuase it's impossible to recover.
            // If keep-alive is off, no need to leave the connection open.
            if (oversized instanceof FullHttpMessage ||
                    !HttpHeaders.is100ContinueExpected(oversized) || !HttpHeaders.isKeepAlive(oversized)) {
                future.addListener(ChannelFutureListener.CLOSE);
            }

            // If an oversized request was handled properly and the connection is still alive
            // (i.e. rejected 100-continue). the decoder should prepare to handle a new message.
            HttpObjectDecoder decoder = ctx.pipeline().get(HttpObjectDecoder.class);
            if (decoder != null) {
                decoder.reset();
            }
        } else if (oversized instanceof HttpResponse) {
            ctx.close();
            throw new TooLongFrameException("Response entity too large: " + oversized);
        } else {
            throw new IllegalStateException();
        }
    }
}
