/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpUtil.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static io.netty.util.internal.ObjectUtil.*;

/**
 * Handles the HTTP handshake (the HTTP Upgrade request) for {@link WebSocketServerProtocolHandler}.
 */
class WebSocketServerProtocolHandshakeHandler extends ChannelInboundHandlerAdapter {

    private final WebSocketServerProtocolConfig serverConfig;
    private ChannelHandlerContext ctx;
    private ChannelPromise handshakePromise;
    private boolean isWebSocketPath;

    WebSocketServerProtocolHandshakeHandler(WebSocketServerProtocolConfig serverConfig) {
        this.serverConfig = checkNotNull(serverConfig, "serverConfig");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        handshakePromise = ctx.newPromise();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        final HttpObject httpObject = (HttpObject) msg;

        if (httpObject instanceof HttpRequest) {
            final HttpRequest req = (HttpRequest) httpObject;
            isWebSocketPath = isWebSocketPath(req);
            if (!isWebSocketPath) {
                ctx.fireChannelRead(msg);
                return;
            }

            try {
                if (!GET.equals(req.method())) {
                    sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN, ctx.alloc().buffer(0)));
                    return;
                }

                final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                        getWebSocketLocation(ctx.pipeline(), req, serverConfig.websocketPath()),
                        serverConfig.subprotocols(), serverConfig.decoderConfig());
                final WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
                final ChannelPromise localHandshakePromise = handshakePromise;
                if (handshaker == null) {
                    WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                } else {
                    // Ensure we set the handshaker and replace this handler before we
                    // trigger the actual handshake. Otherwise we may receive websocket bytes in this handler
                    // before we had a chance to replace it.
                    //
                    // See https://github.com/netty/netty/issues/9471.
                    WebSocketServerProtocolHandler.setHandshaker(ctx.channel(), handshaker);
                    ctx.pipeline().remove(this);

                    final ChannelFuture handshakeFuture = handshaker.handshake(ctx.channel(), req);
                    handshakeFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) {
                            if (!future.isSuccess()) {
                                localHandshakePromise.tryFailure(future.cause());
                                ctx.fireExceptionCaught(future.cause());
                            } else {
                                localHandshakePromise.trySuccess();
                                // Kept for compatibility
                                ctx.fireUserEventTriggered(
                                        WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE);
                                ctx.fireUserEventTriggered(
                                        new WebSocketServerProtocolHandler.HandshakeComplete(
                                                req.uri(), req.headers(), handshaker.selectedSubprotocol()));
                            }
                        }
                    });
                    applyHandshakeTimeout();
                }
            } finally {
                ReferenceCountUtil.release(req);
            }
        } else if (!isWebSocketPath) {
            ctx.fireChannelRead(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    private boolean isWebSocketPath(HttpRequest req) {
        String websocketPath = serverConfig.websocketPath();
        String uri = req.uri();
        boolean checkStartUri = uri.startsWith(websocketPath);
        boolean checkNextUri = "/".equals(websocketPath) || checkNextUri(uri, websocketPath);
        return serverConfig.checkStartsWith() ? (checkStartUri && checkNextUri) : uri.equals(websocketPath);
    }

    private boolean checkNextUri(String uri, String websocketPath) {
        int len = websocketPath.length();
        if (uri.length() > len) {
            char nextUri = uri.charAt(len);
            return nextUri == '/' || nextUri == '?';
        }
        return true;
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String path) {
        String protocol = "ws";
        if (cp.get(SslHandler.class) != null) {
            // SSL in use so use Secure WebSockets
            protocol = "wss";
        }
        String host = req.headers().get(HttpHeaderNames.HOST);
        return protocol + "://" + host + path;
    }

    private void applyHandshakeTimeout() {
        final ChannelPromise localHandshakePromise = handshakePromise;
        final long handshakeTimeoutMillis = serverConfig.handshakeTimeoutMillis();
        if (handshakeTimeoutMillis <= 0 || localHandshakePromise.isDone()) {
            return;
        }

        final Future<?> timeoutFuture = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (!localHandshakePromise.isDone() &&
                    localHandshakePromise.tryFailure(new WebSocketServerHandshakeException("handshake timed out"))) {
                    ctx.flush()
                       .fireUserEventTriggered(ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT)
                       .close();
                }
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);

        // Cancel the handshake timeout when handshake is finished.
        localHandshakePromise.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> f) {
                timeoutFuture.cancel(false);
            }
        });
    }
}
