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

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AttributeKey;

/**
 * Handles WebSocket control frames (Close, Ping, Pong) and data frames (Text and Binary) are passed
 * to the next handler in the pipeline.
 */
public class WebSocketServerProtocolHandler extends ChannelInboundMessageHandlerAdapter<WebSocketFrame> {

    private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER_ATTR_KEY =
            new AttributeKey<WebSocketServerHandshaker>(WebSocketServerHandshaker.class.getName());

    private final String websocketPath;
    private final String subprotocols;
    private final boolean allowExtensions;

    public WebSocketServerProtocolHandler(String websocketPath) {
        this(websocketPath, null, false);
    }

    public WebSocketServerProtocolHandler(String websocketPath, String subprotocols) {
        this(websocketPath, subprotocols, false);
    }

    public WebSocketServerProtocolHandler(String websocketPath, String subprotocols, boolean allowExtensions) {
        this.websocketPath = websocketPath;
        this.subprotocols = subprotocols;
        this.allowExtensions = allowExtensions;
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) {
        ChannelPipeline cp = ctx.pipeline();
        if (cp.get(WebSocketServerProtocolHandshakeHandler.class) == null) {
            // Add the WebSocketHandshakeHandler before this one.
            ctx.pipeline().addBefore(ctx.name(), WebSocketServerProtocolHandshakeHandler.class.getName(),
                    new WebSocketServerProtocolHandshakeHandler(websocketPath, subprotocols, allowExtensions));
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof CloseWebSocketFrame) {
            WebSocketServerHandshaker handshaker = getHandshaker(ctx);
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame);
            return;
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.getBinaryData()));
            return;
        }

        ctx.nextInboundMessageBuffer().add(frame);
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof WebSocketHandshakeException) {
            DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            response.setContent(Unpooled.wrappedBuffer(cause.getMessage().getBytes()));
            ctx.channel().write(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.close();
        }
    }

    static WebSocketServerHandshaker getHandshaker(ChannelHandlerContext ctx) {
        return ctx.attr(HANDSHAKER_ATTR_KEY).get();
    }

    static void setHandshaker(ChannelHandlerContext ctx, WebSocketServerHandshaker handshaker) {
        ctx.attr(HANDSHAKER_ATTR_KEY).set(handshaker);
    }

    static ChannelHandler forbiddenHttpRequestResponder() {
        return new ChannelInboundMessageHandlerAdapter<Object>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (!(msg instanceof WebSocketFrame)) {
                    DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FORBIDDEN);
                    ctx.channel().write(response);
                } else {
                    ctx.nextInboundMessageBuffer().add(msg);
                    ctx.fireInboundBufferUpdated();
                }
            }
        };
    }

}
