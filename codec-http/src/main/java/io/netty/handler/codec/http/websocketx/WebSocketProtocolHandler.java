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


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelShutdownType;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.TimeUnit;

abstract class WebSocketProtocolHandler extends MessageToMessageDecoder<WebSocketFrame>
        implements ChannelOutboundHandler {

    private final boolean dropPongFrames;
    private final WebSocketCloseStatus closeStatus;
    private final long forceCloseTimeoutMillis;
    private Promise<Void> closeSent;
    private Future<?> forceCloseTimeoutTask;

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
        this(dropPongFrames, null, WebSocketServerProtocolConfig.DEFAULT_FORCE_CLOSE_TIMEOUT_MILLIS);
    }

    WebSocketProtocolHandler(boolean dropPongFrames,
                             WebSocketCloseStatus closeStatus,
                             long forceCloseTimeoutMillis) {
        super(WebSocketFrame.class);
        this.dropPongFrames = dropPongFrames;
        this.closeStatus = closeStatus;
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
        if (frame instanceof PingWebSocketFrame) {
            frame.content().retain();
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content()));
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
    public void close(final ChannelHandlerContext ctx, final CompletionHandler<Void> handler) {
        if (closeStatus == null || !ctx.channel().isActive()) {
            ctx.close(handler);
        } else {
            if (closeSent == null) {
                write(ctx, new CloseWebSocketFrame(closeStatus, ctx.alloc()), CompletionHandler.ignore());
            }
            flush(ctx);
            applyCloseSentTimeout(ctx);
            closeSent.addListener(future -> ctx.close(handler));
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
        if (closeSent != null) {
            ReferenceCountUtil.release(msg);
            handler.failure(new ClosedChannelException());
        } else if (msg instanceof CloseWebSocketFrame) {
            closeSent(handler.toPromise(ctx.executor()));
            ctx.write(msg).addListener(new PromiseNotifier<>(false, closeSent));
        } else {
            ctx.write(msg, handler);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
        // Give the flush a chance to complete synchronously before arming the deadline, so a write that
        // finishes immediately (e.g. EmbeddedChannel, or a fast socket) never races the force-close timer.
        if (closeSent != null) {
            applyCloseSentTimeout(ctx);
        }
    }

    /**
     * Records the {@link Promise} used to write the outgoing close frame. Every code path that initiates
     * the close handshake must call this, and must subsequently give
     * {@link #applyCloseSentTimeout(ChannelHandlerContext)} a chance to run (directly, or indirectly via
     * {@link #flush(ChannelHandlerContext)}) so the channel is guaranteed to close even if the outbound write
     * never completes (e.g. the peer stops reading and the socket send buffer fills up).
     */
    void closeSent(Promise<Void> promise) {
        if (closeSent != null) {
            // Already sending (or already sent) a close frame, e.g. a peer that sends more than one CLOSE
            // frame. Keep the original promise (and the deadline already armed for it) authoritative, and
            // just cascade its outcome onto the new one instead of losing track of the original.
            closeSent.addListener(new PromiseNotifier<>(false, promise));
            return;
        }
        closeSent = promise;
    }

    void applyCloseSentTimeout(ChannelHandlerContext ctx) {
        if (forceCloseTimeoutTask != null || closeSent.isDone() || forceCloseTimeoutMillis < 0) {
            return;
        }

        final Future<?> timeoutTask = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (!closeSent.isDone()) {
                    closeSent.tryFailure(buildHandshakeException("send close frame timed out"));
                    // Do not rely on some other listener eventually closing the channel once closeSent
                    // completes (e.g. write(CloseWebSocketFrame) without a subsequent close() call attaches
                    // none): close it here so the deadline is enforced unconditionally.
                    ctx.close();
                }
            }
        }, forceCloseTimeoutMillis, TimeUnit.MILLISECONDS);
        forceCloseTimeoutTask = timeoutTask;

        closeSent.addListener(future -> timeoutTask.cancel(false));
    }

    /**
     * Returns a {@link WebSocketHandshakeException} that depends on which client or server pipeline
     * this handler belongs. Should be overridden in implementation otherwise a default exception is used.
     */
    protected WebSocketHandshakeException buildHandshakeException(String message) {
        return new WebSocketHandshakeException(message);
    }

    @Override
    public void register(ChannelHandlerContext ctx, CompletionHandler<Void> handler) {
        ctx.register(handler);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
                     CompletionHandler<Void> handler) {
        ctx.bind(localAddress, handler);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, CompletionHandler<Void> handler) {
        ctx.connect(remoteAddress, localAddress, handler);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, CompletionHandler<Void> handler) {
        ctx.disconnect(handler);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, CompletionHandler<Void> handler) {
        ctx.deregister(handler);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }

    @Override
    public void shutdown(ChannelHandlerContext ctx,
                         ChannelShutdownType type, CompletionHandler<Void> handler) {
        ctx.shutdown(type, handler);
    }
}
