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


import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseNotifier;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.TimeUnit;

abstract class WebSocketProtocolHandler extends MessageToMessageDecoder<WebSocketFrame>
        implements ChannelOutboundHandler {

    private final boolean dropPongFrames;
    private final WebSocketCloseStatus closeStatus;
    private final long forceCloseTimeoutMillis;
    private ChannelPromise closeSent;

    /**
     * Creates a new {@link WebSocketProtocolHandler} that will <i>drop</i> {@link PongWebSocketFrame}s.
     */
    WebSocketProtocolHandler() {
        this(true);
    }

    /**
     * Creates a new {@link WebSocketProtocolHandler}, given a parameter that determines whether or not to drop {@link
     * PongWebSocketFrame}s.
     *
     * @param dropPongFrames
     *            {@code true} if {@link PongWebSocketFrame}s should be dropped
     */
    WebSocketProtocolHandler(boolean dropPongFrames) {
        this(dropPongFrames, null, 0L);
    }

    WebSocketProtocolHandler(boolean dropPongFrames,
                             WebSocketCloseStatus closeStatus,
                             long forceCloseTimeoutMillis) {
        this.dropPongFrames = dropPongFrames;
        this.closeStatus = closeStatus;
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
        if (frame instanceof PingWebSocketFrame) {
            frame.content().retain();
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content()));
            readIfNeeded(ctx);
            return;
        }
        if (frame instanceof PongWebSocketFrame && dropPongFrames) {
            readIfNeeded(ctx);
            return;
        }

        out.add(frame.retain());
    }

    private static void readIfNeeded(ChannelHandlerContext ctx) {
        if (!ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        if (closeStatus == null || !ctx.channel().isActive()) {
            ctx.close(promise);
        } else {
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
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (closeSent != null) {
            ReferenceCountUtil.release(msg);
            promise.setFailure(new ClosedChannelException());
        } else if (msg instanceof CloseWebSocketFrame) {
            closeSent = promise.unvoid();
            ctx.write(msg).addListener(new ChannelPromiseNotifier(false, closeSent));
        } else {
            ctx.write(msg, promise);
        }
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

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
                     ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise)
            throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }
}
