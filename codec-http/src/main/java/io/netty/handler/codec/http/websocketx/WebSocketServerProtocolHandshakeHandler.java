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
package io.netty.handler.codec.http.websocketx;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.SslHandler;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

/**
 * Handles the HTTP handshake (the HTTP Upgrade request)
 */
public class WebSocketServerProtocolHandshakeHandler extends ChannelInboundMessageHandlerAdapter<HttpRequest> {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(WebSocketServerProtocolHandshakeHandler.class);
    private final String websocketPath;
    private final String subprotocols;
    private final boolean allowExtensions;

    public WebSocketServerProtocolHandshakeHandler(String websocketPath, String subprotocols,
            boolean allowExtensions) {
        this.websocketPath = websocketPath;
        this.subprotocols = subprotocols;
        this.allowExtensions = allowExtensions;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, HttpRequest req) throws Exception {
        if (req.getMethod() != GET) {
            sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(ctx.pipeline(), req, websocketPath), subprotocols, allowExtensions);
        final WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            final ChannelFuture handshakeFuture = handshaker.handshake(ctx.channel(), req);
            handshakeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        ctx.fireExceptionCaught(future.cause());
                    }
                }
            });
            WebSocketServerProtocolHandler.setHandshaker(ctx, handshaker);
            ctx.pipeline().replace(this, "WS403Responder",
                WebSocketServerProtocolHandler.forbiddenHttpRequestResponder());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception Caught", cause);
        ctx.close();
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        ChannelFuture f = ctx.channel().write(res);
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String path) {
        String protocol = "ws";
        if (cp.get(SslHandler.class) != null) {
            // SSL in use so use Secure WebSockets
            protocol = "wss";
        }
        return protocol + "://" + req.getHeader(HttpHeaders.Names.HOST) + path;
    }

}
