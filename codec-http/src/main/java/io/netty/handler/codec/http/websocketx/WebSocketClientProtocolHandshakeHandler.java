/*
 * Copyright 2013 The Netty Project
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.ThrowableUtil;

import java.util.concurrent.TimeUnit;

class WebSocketClientProtocolHandshakeHandler extends ChannelInboundHandlerAdapter {
    private static final WebSocketHandshakeException HANDSHAKE_TIMED_OUT_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new WebSocketHandshakeException("handshake timed out"),
            WebSocketClientProtocolHandshakeHandler.class,
            "channelActive(...)");

    private final WebSocketClientHandshaker handshaker;
    private volatile long handshakeTimeoutMillis;
    private volatile ChannelHandlerContext ctx;
    private final Promise<Channel> handshakePromise = new LazyChannelPromise();

    WebSocketClientProtocolHandshakeHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
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
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpResponse)) {
            ctx.fireChannelRead(msg);
            return;
        }

        FullHttpResponse response = (FullHttpResponse) msg;
        try {
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ctx.channel(), response);
                handshakePromise.trySuccess(ctx.channel());
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

    public WebSocketClientProtocolHandshakeHandler handshakeTimeoutMillis(long handshakeTimeoutMillis) {
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        return this;
    }

    public void applyHandshakeTimeout() {
        final Promise<Channel> localHandshakePromise = handshakePromise;
        final long handshakeTimeoutMillis = this.handshakeTimeoutMillis;
        if (handshakeTimeoutMillis <= 0 || localHandshakePromise.isDone()) {
            return;
        }

        final ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (localHandshakePromise.isDone()) {
                    return;
                }

                if (localHandshakePromise.tryFailure(HANDSHAKE_TIMED_OUT_EXCEPTION)) {
                    ctx.flush()
                       .fireUserEventTriggered(ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT)
                       .close();
                }
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);

        // Cancel the handshake timeout when handshake is finished.
        localHandshakePromise.addListener(new FutureListener<Channel>() {
            @Override
            public void operationComplete(Future<Channel> f) throws Exception {
                timeoutFuture.cancel(false);
            }
        });
    }

    private final class LazyChannelPromise extends DefaultPromise<Channel> {

        @Override
        protected EventExecutor executor() {
            if (ctx == null) {
                throw new IllegalStateException();
            }
            return ctx.executor();
        }

        @Override
        protected void checkDeadLock() {
            if (ctx == null) {
                // If ctx is null the handlerAdded(...) callback was not called, in this case the checkDeadLock()
                // method was called from another Thread then the one that is used by ctx.executor(). We need to
                // guard against this as a user can see a race if handshakeFuture().sync() is called but the
                // handlerAdded(..) method was not yet as it is called from the EventExecutor of the
                // ChannelHandlerContext. If we not guard against this super.checkDeadLock() would cause an
                // IllegalStateException when trying to call executor().
                return;
            }
            super.checkDeadLock();
        }
    }
}
