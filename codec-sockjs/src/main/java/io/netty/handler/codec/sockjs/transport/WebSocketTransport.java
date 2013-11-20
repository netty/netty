/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.transport;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.sockjs.transport.Transports.badRequestResponse;
import static io.netty.handler.codec.sockjs.transport.Transports.internalServerErrorResponse;
import static io.netty.handler.codec.sockjs.transport.Transports.methodNotAllowedResponse;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.handler.CorsInboundHandler;
import io.netty.handler.codec.sockjs.handler.CorsOutboundHandler;
import io.netty.handler.codec.sockjs.handler.SessionHandler.Events;
import io.netty.handler.codec.sockjs.handler.SockJsHandler;
import io.netty.handler.codec.sockjs.util.JsonUtil;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import org.codehaus.jackson.JsonParseException;

/**
 * WebSocketTransport is responsible for the WebSocket handshake and
 * also for receiving WebSocket frames.
 */
public class WebSocketTransport extends SimpleChannelInboundHandler<Object> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketTransport.class);
    private static final AttributeKey<HttpRequest> REQUEST_KEY = AttributeKey.valueOf("request.key");
    private final SockJsConfig config;
    private WebSocketServerHandshaker handshaker;

    public WebSocketTransport(final SockJsConfig config) {
        this.config = config;
    }

    @Override
    protected void messageReceived(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private static boolean checkRequestHeaders(final ChannelHandlerContext ctx, final HttpRequest req) {
        if (req.getMethod() != GET) {
            logger.debug("Request was not of type GET, was {}", req.getMethod());
            ctx.writeAndFlush(methodNotAllowedResponse(req.getProtocolVersion()))
            .addListener(ChannelFutureListener.CLOSE);
            return false;
        }

        final String upgradeHeader = req.headers().get(HttpHeaders.Names.UPGRADE);
        if (upgradeHeader == null || !"websocket".equals(upgradeHeader.toLowerCase())) {
            logger.debug("Upgrade header was not 'websocket' was: {}", upgradeHeader);
            ctx.writeAndFlush(badRequestResponse(req.getProtocolVersion(), "Can \"Upgrade\" only to \"WebSocket\"."))
            .addListener(ChannelFutureListener.CLOSE);
            return false;
        }

        String connectHeader = req.headers().get(HttpHeaders.Names.CONNECTION);
        if (connectHeader != null && "keep-alive, upgrade".equals(connectHeader.toLowerCase())) {
            logger.debug("Connection header was not 'keep-alive, upgrade' was: {}", connectHeader);
            req.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.UPGRADE);
            connectHeader = HttpHeaders.Values.UPGRADE;
        }
        if (connectHeader == null || !"upgrade".equals(connectHeader.toLowerCase())) {
            logger.debug("Connection header was not 'upgrade' was: {}", connectHeader);
            ctx.writeAndFlush(badRequestResponse(req.getProtocolVersion(), "\"Connection\" must be \"Upgrade\"."))
            .addListener(ChannelFutureListener.CLOSE);
            return false;
        }
        return true;
    }

    private void handleHttpRequest(final ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (!checkRequestHeaders(ctx, req)) {
            return;
        }
        ctx.attr(REQUEST_KEY).set(req);

        if (WebSocketHAProxyHandshaker.isHAProxyReqeust(req)) {
            final String wsUrl = getWebSocketLocation(config.isTls(), req);
            final WebSocketHAProxyHandshaker haHandshaker = new WebSocketHAProxyHandshaker(wsUrl, null, 65365);
            final ChannelFuture handshakeFuture = haHandshaker.handshake(ctx.channel(), req);
            handshakeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        final ChannelPipeline pipeline = future.channel().pipeline();
                        pipeline.remove(SockJsHandler.class);
                        pipeline.remove(CorsInboundHandler.class);
                        pipeline.remove(CorsOutboundHandler.class);
                        pipeline.replace(WebSocketTransport.class, "websocket-ha-proxy",
                                new WebSocketHAProxyTransport(haHandshaker));
                        pipeline.addLast(new WebSocketSendHandler());
                    }
                }
            });
            return;
        }
        final String wsUrl = getWebSocketLocation(config.isTls(), req, Transports.Type.WEBSOCKET.path());
        final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(wsUrl, null, false);
        handshaker = wsFactory.newHandshaker(req);

        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            final ChannelFuture handshakeFuture = handshaker.handshake(ctx.channel(), req);
            handshakeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.pipeline().remove(SockJsHandler.class);
                        ctx.pipeline().remove(CorsInboundHandler.class);
                        ctx.pipeline().remove(CorsOutboundHandler.class);
                        ctx.pipeline().addLast(new WebSocketSendHandler());
                    } else {
                        logger.error("Handshake error", future.cause());
                    }
                }
            });
            ctx.fireChannelRead(req);
        }
    }

    private static String getWebSocketLocation(final boolean tls, final FullHttpRequest req) {
        final String protocol = tls ? "wss://" : "ws://";
        return protocol + req.headers().get(HttpHeaders.Names.HOST) + req.getUri();
    }

    private static String getWebSocketLocation(final boolean tls, final FullHttpRequest req, final String path) {
        final String protocol = tls ? "wss://" : "ws://";
        return protocol + req.headers().get(HttpHeaders.Names.HOST) + path;
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final WebSocketFrame wsFrame) throws Exception {
        if (wsFrame instanceof CloseWebSocketFrame) {
            wsFrame.retain();
            logger.debug("CloseWebSocketFrame received");
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) wsFrame);
            ctx.fireUserEventTriggered(Events.CLOSE_SESSION);
            return;
        }
        if (wsFrame instanceof PingWebSocketFrame) {
            logger.debug("PingWebSocketFrame received");
            wsFrame.content().retain();
            ctx.channel().writeAndFlush(new PongWebSocketFrame(wsFrame.content()));
            return;
        }
        if (!(wsFrame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported",
                    wsFrame.getClass().getName()));
        }
        final String[] messages = JsonUtil.decode((TextWebSocketFrame) wsFrame);
        for (String message : messages) {
            ctx.fireChannelRead(message);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof JsonParseException) {
            logger.trace("Failed to part JSON", cause);
            ctx.close();
        } else if (cause instanceof WebSocketHandshakeException) {
            final HttpRequest request = ctx.attr(REQUEST_KEY).get();
            logger.error("Failed with ws handshake for request: " + request, cause);
            ctx.writeAndFlush(internalServerErrorResponse(request.getProtocolVersion(), cause.getMessage()))
            .addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

}
