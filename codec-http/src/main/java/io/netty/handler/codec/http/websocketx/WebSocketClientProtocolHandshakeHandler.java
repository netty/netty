/*
 * Copyright 2013 The Netty Project
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
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.*;

class WebSocketClientProtocolHandshakeHandler extends ChannelInboundHandlerAdapter {
    private static final long DEFAULT_HANDSHAKE_TIMEOUT_MS = 10000L;

    private final WebSocketClientHandshaker handshaker;
    private final long handshakeTimeoutMillis;
    private ChannelHandlerContext ctx;
    private ChannelPromise handshakePromise;

    WebSocketClientProtocolHandshakeHandler(WebSocketClientHandshaker handshaker) {
        this(handshaker, DEFAULT_HANDSHAKE_TIMEOUT_MS);
    }

    WebSocketClientProtocolHandshakeHandler(WebSocketClientHandshaker handshaker, long handshakeTimeoutMillis) {
        this.handshaker = handshaker;
        this.handshakeTimeoutMillis = checkPositive(handshakeTimeoutMillis, "handshakeTimeoutMillis");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        handshakePromise = ctx.newPromise();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        handshaker.handshake(ctx.channel()).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    handshakePromise.tryFailure(future.cause());
                    ctx.fireExceptionCaught(future.cause());
                } else {
                    ctx.fireUserEventTriggered(
                            WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_ISSUED);
                }
            }
        });
        applyHandshakeTimeout();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!handshakePromise.isDone()) {
            handshakePromise.tryFailure(new WebSocketClientHandshakeException("channel closed with handshake " +
                                                                              "in progress"));
        }

        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpResponse)) {
            ctx.fireChannelRead(msg);
            return;
        }

        FullHttpResponse response = (FullHttpResponse) msg;
        try {
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ctx.channel(), response);
                handshakePromise.trySuccess();
                ctx.fireUserEventTriggered(
                        WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE);
                ctx.pipeline().remove(this);
                return;
            }
            throw new IllegalStateException("WebSocketClientHandshaker should have been non finished yet");
        } finally {
            response.release();
        }
    }

    private void applyHandshakeTimeout() {
        final ChannelPromise localHandshakePromise = handshakePromise;
        if (handshakeTimeoutMillis <= 0 || localHandshakePromise.isDone()) {
            return;
        }

        final Future<?> timeoutFuture = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (localHandshakePromise.isDone()) {
                    return;
                }

                if (localHandshakePromise.tryFailure(new WebSocketClientHandshakeException("handshake timed out"))) {
                    ctx.flush()
                       .fireUserEventTriggered(ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT)
                       .close();
                }
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);

        // Cancel the handshake timeout when handshake is finished.
        localHandshakePromise.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> f) throws Exception {
                timeoutFuture.cancel(false);
            }
        });
    }

    /**
     * This method is visible for testing.
     *
     * @return current handshake future
     */
    ChannelFuture getHandshakeFuture() {
        return handshakePromise;
    }
}
