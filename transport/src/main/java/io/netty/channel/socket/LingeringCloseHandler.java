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
package io.netty.channel.socket;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.TimeUnit;

/**
 * Delays full socket closure to allow a peer to read the final flushed write while it is still sending data.
 * Install at the transport end of the pipeline, before protocol handlers (including TLS), so their outbound
 * close processing runs first. Inbound messages are released while waiting for EOF or the configured timeout.
 * Channels without duplex shutdown support close immediately.
 */
public final class LingeringCloseHandler extends ChannelDuplexHandler {
    private final long timeout;
    private final TimeUnit unit;
    private ScheduledFuture<?> deadline;

    public LingeringCloseHandler(long timeout, TimeUnit unit) {
        this.timeout = ObjectUtil.checkPositive(timeout, "timeout");
        this.unit = ObjectUtil.checkNotNull(unit, "unit");
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(ctx.channel() instanceof DuplexChannel) || !ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }
        DuplexChannel channel = (DuplexChannel) ctx.channel();
        if (channel.isInputShutdown()) {
            ctx.close(promise);
            return;
        }
        if (!promise.isVoid()) {
            channel.closeFuture().addListener(f -> promise.trySuccess());
        }
        if (deadline == null) {
            deadline = ctx.executor().schedule(() -> {
                ctx.close();
            }, timeout, unit);
            channel.shutdownOutput().addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    ctx.read();
                } else {
                    ctx.close();
                }
            });
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (deadline == null) {
            ctx.fireChannelRead(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (deadline == null) {
            ctx.fireChannelReadComplete();
        } else {
            ctx.read();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        if (deadline != null && event == ChannelInputShutdownEvent.INSTANCE) {
            ctx.close();
        }
        ctx.fireUserEventTriggered(event);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (deadline != null) {
            deadline.cancel(false);
            if (ctx.channel().isOpen()) {
                ctx.close();
            }
        }
    }
}
