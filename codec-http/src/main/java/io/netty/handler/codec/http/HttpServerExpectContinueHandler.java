/*
 * Copyright 2017 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Sends a <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html#sec8.2.3">100 CONTINUE</a>
 * {@link HttpResponse} to {@link HttpRequest}s which contain a 'expect: 100-continue' header. It
 * should only be used for applications which do <b>not</b> install the {@link HttpObjectAggregator}.
 * <p>
 * By default it accepts all expectations.
 * <p>
 * Since {@link HttpServerExpectContinueHandler} expects {@link HttpRequest}s it should be added after {@link
 * HttpServerCodec} but before any other handlers that might send a {@link HttpResponse}. <blockquote>
 * <pre>
 *  {@link io.netty.channel.ChannelPipeline} p = ...;
 *  ...
 *  p.addLast("serverCodec", new {@link HttpServerCodec}());
 *  p.addLast("respondExpectContinue", <b>new {@link HttpServerExpectContinueHandler}()</b>);
 *  ...
 *  p.addLast("handler", new HttpRequestHandler());
 *  </pre>
 * </blockquote>
 */
public class HttpServerExpectContinueHandler extends ChannelInboundHandlerAdapter {

    private static final FullHttpResponse EXPECTATION_FAILED = new DefaultFullHttpResponse(
            HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED, Unpooled.EMPTY_BUFFER);

    private static final FullHttpResponse ACCEPT = new DefaultFullHttpResponse(
            HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER);

    static {
        EXPECTATION_FAILED.headers().set(CONTENT_LENGTH, 0);
        ACCEPT.headers().set(CONTENT_LENGTH, 0);
    }

    /**
     * Produces a {@link HttpResponse} for {@link HttpRequest}s which define an expectation. Returns {@code null} if the
     * request should be rejected. See {@link #rejectResponse(HttpRequest)}.
     */
    protected HttpResponse acceptMessage(@SuppressWarnings("unused") HttpRequest request) {
        return ACCEPT.retainedDuplicate();
    }

    /**
     * Returns the appropriate 4XX {@link HttpResponse} for the given {@link HttpRequest}.
     */
    protected HttpResponse rejectResponse(@SuppressWarnings("unused") HttpRequest request) {
        return EXPECTATION_FAILED.retainedDuplicate();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            if (HttpUtil.is100ContinueExpected(req)) {
                HttpResponse accept = acceptMessage(req);

                if (accept == null) {
                    // the expectation failed so we refuse the request.
                    HttpResponse rejection = rejectResponse(req);
                    ReferenceCountUtil.release(msg);
                    ctx.writeAndFlush(rejection).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                    return;
                }

                ctx.writeAndFlush(accept).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                req.headers().remove(HttpHeaderNames.EXPECT);
            }
        }
        super.channelRead(ctx, msg);
    }
}
