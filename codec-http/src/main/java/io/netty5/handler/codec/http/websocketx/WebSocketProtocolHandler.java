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


import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToMessageDecoder;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

abstract class WebSocketProtocolHandler extends MessageToMessageDecoder<WebSocketFrame> {

    private final boolean dropPongFrames;
    private final WebSocketCloseStatus closeStatus;
    private final long forceCloseTimeoutMillis;
    private Promise<Void> closeSent;

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
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof PingWebSocketFrame) {
            // We need to `send` the binary data to the pong frame, because the MessageToMessageDecoder
            // is going to close the ping frame, which would otherwise cause the data to be freed.
            try (frame) {
                ctx.writeAndFlush(new PongWebSocketFrame(frame.binaryData().send()));
            }
            readIfNeeded(ctx);
            return;
        }
        if (frame instanceof PongWebSocketFrame && dropPongFrames) {
            readIfNeeded(ctx);
            return;
        }

        ctx.fireChannelRead(frame);
    }

    private static void readIfNeeded(ChannelHandlerContext ctx) {
        if (!ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
    }

    @Override
    public Future<Void> close(final ChannelHandlerContext ctx) {
        if (closeStatus == null || !ctx.channel().isActive()) {
            return ctx.close();
        }
        final Future<Void> future = closeSent == null ?
                write(ctx, new CloseWebSocketFrame(ctx.bufferAllocator(), closeStatus)) : closeSent.asFuture();

        flush(ctx);
        applyCloseSentTimeout(ctx);
        Promise<Void> promise = ctx.newPromise();
        future.addListener(f -> ctx.close().cascadeTo(promise));
        return promise.asFuture();
    }

    @Override
    public Future<Void> write(final ChannelHandlerContext ctx, Object msg) {
        if (closeSent != null) {
            ReferenceCountUtil.release(msg);
            return ctx.newFailedFuture(new ClosedChannelException());
        }
        if (msg instanceof CloseWebSocketFrame) {
            Promise<Void> promise = ctx.newPromise();
            closeSent(promise);
            ctx.write(msg).cascadeTo(closeSent);
            return promise.asFuture();
        }
        return ctx.write(msg);
    }

    void closeSent(Promise<Void> promise) {
        closeSent = promise;
    }

    private void applyCloseSentTimeout(ChannelHandlerContext ctx) {
        if (closeSent.isDone() || forceCloseTimeoutMillis < 0) {
            return;
        }

        Future<?> timeoutTask = ctx.executor().schedule(() -> {
            if (!closeSent.isDone()) {
                closeSent.tryFailure(buildHandshakeException("send close frame timed out"));
            }
        }, forceCloseTimeoutMillis, TimeUnit.MILLISECONDS);

        closeSent.asFuture().addListener(future -> timeoutTask.cancel());
    }

    /**
     * Returns a {@link WebSocketHandshakeException} that depends on which client or server pipeline
     * this handler belongs. Should be overridden in implementation otherwise a default exception is used.
     */
    protected WebSocketHandshakeException buildHandshakeException(String message) {
        return new WebSocketHandshakeException(message);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }
}
