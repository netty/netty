/*
 * Copyright 2015 The Netty Project
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

package io.netty.example.http2.tiles;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpHeaderValues;

import java.util.concurrent.TimeUnit;

/**
 * Handles the requests for the tiled image using HTTP 1.x as a protocol.
 */
public final class Http1RequestHandler extends Http2RequestHandler {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (HttpUtil.is100ContinueExpected(request)) {
            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
        }
        super.channelRead0(ctx, request);
    }

    @Override
    protected void sendResponse(final ChannelHandlerContext ctx, String streamId, int latency,
            final FullHttpResponse response, final FullHttpRequest request) {
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (isKeepAlive(request)) {
                    response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    ctx.writeAndFlush(response);
                } else {
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }, latency, TimeUnit.MILLISECONDS);
    }
}
