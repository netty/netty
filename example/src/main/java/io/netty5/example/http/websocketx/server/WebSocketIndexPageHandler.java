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
package io.netty5.example.http.websocketx.server;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpUtil;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.util.CharsetUtil;

import static io.netty5.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty5.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty5.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty5.handler.codec.http.HttpMethod.GET;
import static io.netty5.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty5.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty5.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty5.handler.codec.http.HttpResponseStatus.OK;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Outputs index page content.
 */
public class WebSocketIndexPageHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String websocketPath;

    public WebSocketIndexPageHandler(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // Handle a bad request.
        if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST,
                    ctx.bufferAllocator().allocate(0)));
            return;
        }

        // Allow only GET methods.
        if (!GET.equals(req.method())) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN,
                    ctx.bufferAllocator().allocate(0)));
            return;
        }

        // Send the index page
        if ("/".equals(req.uri()) || "/index.html".equals(req.uri())) {
            String webSocketLocation = getWebSocketLocation(ctx.pipeline(), req, websocketPath);
            Buffer content = WebSocketServerIndexPage.getContent(ctx.bufferAllocator(), webSocketLocation);
            FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);

            res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
            HttpUtil.setContentLength(res, content.readableBytes());

            sendHttpResponse(ctx, req, res);
        } else {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND,
                    ctx.bufferAllocator().allocate(0)));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        if (res.status().code() != 200) {
            res.payload().writeCharSequence(res.status().toString(), CharsetUtil.UTF_8);
            HttpUtil.setContentLength(res, res.payload().readableBytes());
        }

        // Send the response and close the connection if necessary.
        if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
            // Tell the client we're going to close the connection.
            res.headers().set(CONNECTION, CLOSE);
            ctx.writeAndFlush(res).addListener(ctx, ChannelFutureListeners.CLOSE);
        } else {
            if (req.protocolVersion().equals(HTTP_1_0)) {
                res.headers().set(CONNECTION, KEEP_ALIVE);
            }
            ctx.writeAndFlush(res);
        }
    }

    private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String path) {
        String protocol = "ws";
        if (cp.get(SslHandler.class) != null) {
            // SSL in use so use Secure WebSockets
            protocol = "wss";
        }
        return protocol + "://" + req.headers().get(HttpHeaderNames.HOST) + path;
    }
}
