/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.sockjs.transports;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.sockjs.transports.Transports.badRequestResponse;
import static io.netty.handler.codec.sockjs.transports.Transports.internalServerErrorResponse;
import static io.netty.handler.codec.sockjs.transports.Transports.methodNotAllowedResponse;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
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
import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.SessionContext;
import io.netty.handler.codec.sockjs.SockJSService;
import io.netty.handler.codec.sockjs.handlers.CorsInboundHandler;
import io.netty.handler.codec.sockjs.handlers.CorsOutboundHandler;
import io.netty.handler.codec.sockjs.handlers.SockJSHandler;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * WebSocketTransport is responsible for the WebSocket handshake and
 * also for receiving WebSocket frames.
 */
public class RawWebSocketTransport extends SimpleChannelInboundHandler<Object> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(RawWebSocketTransport.class);
    private static final AttributeKey<HttpRequest> REQUEST_KEY = new AttributeKey<HttpRequest>("raw.ws.request.key");
    private final Config config;
    private final SockJSService service;
    private WebSocketServerHandshaker handshaker;

    public RawWebSocketTransport(final Config config, final SockJSService service) {
        this.config = config;
        this.service = service;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private boolean checkRequestHeaders(final ChannelHandlerContext ctx, final HttpRequest req) {
        if (req.getMethod() != GET) {
            ctx.writeAndFlush(methodNotAllowedResponse(req.getProtocolVersion()))
            .addListener(ChannelFutureListener.CLOSE);
            return false;
        }

        final String upgradeHeader = req.headers().get(HttpHeaders.Names.UPGRADE);
        if (upgradeHeader == null || !upgradeHeader.toLowerCase().equals("websocket")) {
            ctx.writeAndFlush(badRequestResponse(req.getProtocolVersion(), "Can \"Upgrade\" only to \"WebSocket\"."))
            .addListener(ChannelFutureListener.CLOSE);
            return false;
        }

        String connectHeader = req.headers().get(HttpHeaders.Names.CONNECTION);
        if (connectHeader != null && connectHeader.toLowerCase().equals("keep-alive, upgrade")) {
            req.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.UPGRADE);
            connectHeader = HttpHeaders.Values.UPGRADE.toString();
        }
        if (connectHeader == null || !connectHeader.toLowerCase().equals("upgrade")) {
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
        final String wsUrl = getWebSocketLocation(config.isTls(), req, Transports.Types.WEBSOCKET.path());
        final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(wsUrl,
                config.websocketProtocolCSV(), false);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            final ChannelFuture handshakeFuture = handshaker.handshake(ctx.channel(), req);
            handshakeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.pipeline().remove(SockJSHandler.class);
                        ctx.pipeline().remove(CorsInboundHandler.class);
                        ctx.pipeline().remove(CorsOutboundHandler.class);
                        ctx.pipeline().addLast(new RawWebSocketSendHandler());
                        service.onOpen(new SessionContext() {
                            @Override
                            public void send(String message) {
                                ctx.writeAndFlush(new TextWebSocketFrame(message));
                            }
                            @Override
                            public void close() {
                                ctx.close();
                            }
                            @Override
                            public ChannelHandlerContext getContext() {
                                return ctx;
                            }
                        });
                    }
                }
            });
        }
    }

    private static String getWebSocketLocation(final boolean tls, final FullHttpRequest req, final String path) {
        final String protocol = tls ? "wss://" : "ws://";
        return protocol + req.headers().get(HttpHeaders.Names.HOST) + path;
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final WebSocketFrame wsFrame) throws Exception {
        if (wsFrame instanceof CloseWebSocketFrame) {
            wsFrame.retain();
            service.onClose();
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) wsFrame);
            ctx.close();
            return;
        }
        if (wsFrame instanceof PingWebSocketFrame) {
            wsFrame.content().retain();
            ctx.channel().writeAndFlush(new PongWebSocketFrame(wsFrame.content()));
            return;
        }
        if (!(wsFrame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported",
                    wsFrame.getClass().getName()));
        }
        final String message = ((TextWebSocketFrame) wsFrame).text();
        service.onMessage(message);
    }

    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof WebSocketHandshakeException) {
            final HttpRequest request = ctx.attr(REQUEST_KEY).get();
            logger.error("Failed with ws handshake for request: " + request, cause);
            ctx.writeAndFlush(internalServerErrorResponse(request.getProtocolVersion(), cause.getMessage()))
            .addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

}
