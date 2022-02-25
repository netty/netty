/*
 * Copyright 2015 The Netty Project
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

package io.netty5.example.http2.tiles;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpUtil;
import io.netty5.handler.codec.http2.Http2CodecUtil;

import java.nio.charset.StandardCharsets;

import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty5.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty5.handler.codec.http.HttpResponseStatus.OK;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handles the exceptional case where HTTP 1.x was negotiated under TLS.
 */
public final class FallbackRequestHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private static final byte[] responseBytes = ("<!DOCTYPE html>"
            + "<html><body><h2>To view the example you need a browser that supports HTTP/2 ("
            + Http2CodecUtil.TLS_UPGRADE_PROTOCOL_NAME
            + ")</h2></body></html>").getBytes(StandardCharsets.UTF_8);

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, HttpRequest req) throws Exception {
        if (HttpUtil.is100ContinueExpected(req)) {
            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, ctx.bufferAllocator().allocate(0)));
        }

        Buffer content = ctx.bufferAllocator().allocate(responseBytes.length).writeBytes(responseBytes);

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().setInt(CONTENT_LENGTH, response.payload().readableBytes());

        ctx.write(response).addListener(ctx, ChannelFutureListeners.CLOSE);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
