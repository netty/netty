/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

/** Staged TCP teardown after a terminal HTTP rejection (RFC 9112, section 9.6). */
final class HttpServerLingeringCloseHandler extends ChannelDuplexHandler {
    private final long closeTimeoutMillis;
    private ScheduledFuture<?> deadline;
    private boolean shuttingDown;

    static void install(ChannelPipeline pipeline, long closeTimeoutMillis) {
        ChannelHandlerContext ssl = pipeline.context(SslHandler.class);
        if (ssl == null) {
            pipeline.addFirst(DiscardInputHandler.INSTANCE);
        } else {
            // Let TLS consume close_notify while discarding application data before HTTP decoding.
            pipeline.addAfter(ssl.name(), null, DiscardInputHandler.INSTANCE);
        }
        pipeline.addFirst(new HttpServerLingeringCloseHandler(closeTimeoutMillis));
    }

    private HttpServerLingeringCloseHandler(long closeTimeoutMillis) {
        this.closeTimeoutMillis = closeTimeoutMillis;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        SocketChannel socket = (SocketChannel) ctx.channel();
        socket.config().setAllowHalfClosure(true);
        // Bound the complete teardown, including a stalled response write or TLS close_notify.
        deadline = ctx.executor().schedule(() -> ctx.close(), closeTimeoutMillis, TimeUnit.MILLISECONDS);
        ctx.read();
    }

    static void abort(ChannelHandlerContext ctx) {
        ChannelHandlerContext drain = ctx.pipeline().context(HttpServerLingeringCloseHandler.class);
        if (drain == null) {
            ctx.close();
        } else {
            drain.close();
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        // At the transport end of the pipeline, TLS has already processed the outbound close.
        SocketChannel socket = (SocketChannel) ctx.channel();
        if (!socket.isActive() || socket.isInputShutdown()) {
            ctx.close(promise);
            return;
        }
        if (!promise.isVoid()) {
            socket.closeFuture().addListener(f -> promise.trySuccess());
        }
        if (!shuttingDown) {
            shuttingDown = true;
            socket.shutdownOutput().addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    ctx.close();
                }
            });
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt == ChannelInputShutdownEvent.INSTANCE && shuttingDown) {
            ctx.close();
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (deadline != null) {
            deadline.cancel(false);
        }
    }

    @Sharable
    private static final class DiscardInputHandler extends ChannelInboundHandlerAdapter {
        static final DiscardInputHandler INSTANCE = new DiscardInputHandler();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            // An outbound handler may be waiting for read-complete to flush the rejection.
            ctx.fireChannelReadComplete();
            ctx.read();
        }
    }
}
