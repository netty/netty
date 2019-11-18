/*
 * Copyright 2019 The Netty Project
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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.ObjectUtil;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

/**
 * Send {@link CloseWebSocketFrame} message on channel close, if close frame was not sent before.
 */
final class WebSocketCloseFrameHandler extends ChannelOutboundHandlerAdapter {
    private final WebSocketCloseStatus closeStatus;
    private final long forceCloseTimeoutMillis;
    private ChannelPromise closeSent;

    WebSocketCloseFrameHandler(WebSocketCloseStatus closeStatus, long forceCloseTimeoutMillis) {
        this.closeStatus = ObjectUtil.checkNotNull(closeStatus, "closeStatus");
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }
        if (closeSent == null) {
            write(ctx, new CloseWebSocketFrame(closeStatus), ctx.newPromise());
        }
        flush(ctx);
        applyCloseSentTimeout(ctx);
        closeSent.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                ctx.close(promise);
            }
        });
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (closeSent != null) {
            ReferenceCountUtil.release(msg);
            promise.setFailure(new ClosedChannelException());
            return;
        }
        if (msg instanceof CloseWebSocketFrame) {
            promise = promise.unvoid();
            closeSent = promise;
        }
        super.write(ctx, msg, promise);
    }

    private void applyCloseSentTimeout(ChannelHandlerContext ctx) {
        if (closeSent.isDone() || forceCloseTimeoutMillis < 0) {
            return;
        }

        final ScheduledFuture<?> timeoutTask = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (!closeSent.isDone()) {
                    closeSent.tryFailure(new WebSocketHandshakeException("send close frame timed out"));
                }
            }
        }, forceCloseTimeoutMillis, TimeUnit.MILLISECONDS);

        closeSent.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                timeoutTask.cancel(false);
            }
        });
    }
}
