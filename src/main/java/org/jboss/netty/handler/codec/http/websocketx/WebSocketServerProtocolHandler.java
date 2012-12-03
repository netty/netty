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
package org.jboss.netty.handler.codec.http.websocketx;

import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * Handles WebSocket control frames (Close, Ping, Pong) and data frames (Text and Binary) are passed
 * to the next handler in the pipeline.
 */
public class WebSocketServerProtocolHandler extends SimpleChannelUpstreamHandler implements
    LifeCycleAwareChannelHandler {

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

    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline cp = ctx.getPipeline();
        if (cp.get(WebSocketServerProtocolHandshakeHandler.class) == null) {
            // Add the WebSocketHandshakeHandler before this one.
            ctx.getPipeline().addBefore(ctx.getName(), WebSocketServerProtocolHandshakeHandler.class.getName(),
                    new WebSocketServerProtocolHandshakeHandler(websocketPath, subprotocols, allowExtensions));
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) e.getMessage();
            if (frame instanceof CloseWebSocketFrame) {
                WebSocketServerHandshaker handshaker = getHandshaker(ctx);
                handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
                return;
            } else if (frame instanceof PingWebSocketFrame) {
                ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
                return;
            }
        }
        ctx.sendUpstream(e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (e.getCause() instanceof WebSocketHandshakeException) {
            DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            response.setContent(ChannelBuffers.wrappedBuffer(e.getCause().getMessage().getBytes()));
            ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.getChannel().close();
        }
    }

    static WebSocketServerHandshaker getHandshaker(ChannelHandlerContext ctx) {
        return (WebSocketServerHandshaker) ctx.getAttachment();
    }

    static void setHandshaker(ChannelHandlerContext ctx, WebSocketServerHandshaker handshaker) {
        ctx.setAttachment(handshaker);
    }

    static ChannelHandler forbiddenHttpRequestResponder() {
        return new SimpleChannelHandler() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                if (!(e.getMessage() instanceof WebSocketFrame)) {
                    DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FORBIDDEN);
                    ctx.getChannel().write(response);
                } else {
                    ctx.sendUpstream(e);
                }
            }
        };
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
    }

    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
    }

    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
    }
}
