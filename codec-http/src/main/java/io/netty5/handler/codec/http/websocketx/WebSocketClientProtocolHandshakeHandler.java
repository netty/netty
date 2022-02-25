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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;

import static io.netty5.util.internal.ObjectUtil.checkPositive;

class WebSocketClientProtocolHandshakeHandler implements ChannelHandler {

    private static final long DEFAULT_HANDSHAKE_TIMEOUT_MS = 10000L;

    private final WebSocketClientHandshaker handshaker;
    private final long handshakeTimeoutMillis;
    private ChannelHandlerContext ctx;
    private Promise<Void> handshakePromise;

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
        ctx.fireChannelActive();
        handshaker.handshake(ctx.channel()).addListener(future -> {
            if (future.isFailed()) {
                handshakePromise.tryFailure(future.cause());
                ctx.fireExceptionCaught(future.cause());
            } else {
                ctx.fireUserEventTriggered(
                        WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_ISSUED);
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

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpResponse)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try (FullHttpResponse response = (FullHttpResponse) msg) {
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ctx.channel(), response);
                handshakePromise.trySuccess(null);
                ctx.fireUserEventTriggered(
                        ClientHandshakeStateEvent.HANDSHAKE_COMPLETE);
                ctx.pipeline().remove(this);
                return;
            }
            throw new IllegalStateException("WebSocketClientHandshaker should have been non finished yet");
        }
    }

    private void applyHandshakeTimeout() {
        final Promise<Void> localHandshakePromise = handshakePromise;
        if (handshakeTimeoutMillis <= 0 || localHandshakePromise.isDone()) {
            return;
        }

        final Future<?> timeoutFuture = ctx.executor().schedule(() -> {
            if (localHandshakePromise.isDone()) {
                return;
            }

            if (localHandshakePromise.tryFailure(new WebSocketClientHandshakeException("handshake timed out"))) {
                ctx.flush()
                   .fireUserEventTriggered(ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT)
                   .close();
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);

        // Cancel the handshake timeout when handshake is finished.
        localHandshakePromise.asFuture().addListener(f -> timeoutFuture.cancel());
    }

    /**
     * This method is visible for testing.
     *
     * @return current handshake future
     */
    Future<Void> getHandshakeFuture() {
        return handshakePromise.asFuture();
    }
}
