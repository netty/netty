/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;

/**
 *  Combines a {@link ChannelInboundHandler} and a {@link ChannelOutboundHandler} into one {@link ChannelHandler}.
 */
public class CombinedChannelDuplexHandler<I extends ChannelInboundHandler, O extends ChannelOutboundHandler>
        extends ChannelDuplexHandler {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CombinedChannelDuplexHandler.class);

    private DelegatingChannelHandlerContext inboundCtx;
    private DelegatingChannelHandlerContext outboundCtx;
    private volatile boolean handlerAdded;

    private I inboundHandler;
    private O outboundHandler;

    /**
     * Creates a new uninitialized instance. A class that extends this handler must invoke
     * {@link #init(ChannelInboundHandler, ChannelOutboundHandler)} before adding this handler into a
     * {@link ChannelPipeline}.
     */
    protected CombinedChannelDuplexHandler() { }

    /**
     * Creates a new instance that combines the specified two handlers into one.
     */
    public CombinedChannelDuplexHandler(I inboundHandler, O outboundHandler) {
        init(inboundHandler, outboundHandler);
    }

    /**
     * Initialized this handler with the specified handlers.
     *
     * @throws IllegalStateException if this handler was not constructed via the default constructor or
     *                               if this handler does not implement all required handler interfaces
     * @throws IllegalArgumentException if the specified handlers cannot be combined into one due to a conflict
     *                                  in the type hierarchy
     */
    protected final void init(I inboundHandler, O outboundHandler) {
        validate(inboundHandler, outboundHandler);
        this.inboundHandler = inboundHandler;
        this.outboundHandler = outboundHandler;
    }

    private void validate(I inboundHandler, O outboundHandler) {
        if (this.inboundHandler != null) {
            throw new IllegalStateException(
                    "init() can not be invoked if " + CombinedChannelDuplexHandler.class.getSimpleName() +
                            " was constructed with non-default constructor.");
        }

        if (inboundHandler == null) {
            throw new NullPointerException("inboundHandler");
        }
        if (outboundHandler == null) {
            throw new NullPointerException("outboundHandler");
        }
        if (inboundHandler instanceof ChannelOutboundHandler) {
            throw new IllegalArgumentException(
                    "inboundHandler must not implement " +
                    ChannelOutboundHandler.class.getSimpleName() + " to get combined.");
        }
        if (outboundHandler instanceof ChannelInboundHandler) {
            throw new IllegalArgumentException(
                    "outboundHandler must not implement " +
                    ChannelInboundHandler.class.getSimpleName() + " to get combined.");
        }
    }

    protected final I inboundHandler() {
        return inboundHandler;
    }

    protected final O outboundHandler() {
        return outboundHandler;
    }

    private void checkAdded() {
        if (!handlerAdded) {
            throw new IllegalStateException("handler not added to pipeline yet");
        }
    }

    /**
     * Removes the {@link ChannelInboundHandler} that was combined in this {@link CombinedChannelDuplexHandler}.
     */
    public final void removeInboundHandler() {
        checkAdded();
        inboundCtx.remove();
    }

    /**
     * Removes the {@link ChannelOutboundHandler} that was combined in this {@link CombinedChannelDuplexHandler}.
     */
    public final void removeOutboundHandler() {
        checkAdded();
        outboundCtx.remove();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (inboundHandler == null) {
            throw new IllegalStateException(
                    "init() must be invoked before being added to a " + ChannelPipeline.class.getSimpleName() +
                            " if " +  CombinedChannelDuplexHandler.class.getSimpleName() +
                            " was constructed with the default constructor.");
        }

        outboundCtx = new DelegatingChannelHandlerContext(ctx, outboundHandler);
        inboundCtx = new DelegatingChannelHandlerContext(ctx, inboundHandler) {
            @SuppressWarnings("deprecation")
            @Override
            public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
                if (!outboundCtx.removed) {
                    try {
                        // We directly delegate to the ChannelOutboundHandler as this may override exceptionCaught(...)
                        // as well
                        outboundHandler.exceptionCaught(outboundCtx, cause);
                    } catch (Throwable error) {
                        if (logger.isDebugEnabled()) {
                            logger.debug(
                                    "An exception {}" +
                                    "was thrown by a user handler's exceptionCaught() " +
                                    "method while handling the following exception:",
                                    ThrowableUtil.stackTraceToString(error), cause);
                        } else if (logger.isWarnEnabled()) {
                            logger.warn(
                                    "An exception '{}' [enable DEBUG level for full stacktrace] " +
                                    "was thrown by a user handler's exceptionCaught() " +
                                    "method while handling the following exception:", error, cause);
                        }
                    }
                } else {
                    super.fireExceptionCaught(cause);
                }
                return this;
            }
        };

        // The inboundCtx and outboundCtx were created and set now it's safe to call removeInboundHandler() and
        // removeOutboundHandler().
        handlerAdded = true;

        try {
            inboundHandler.handlerAdded(inboundCtx);
        } finally {
            outboundHandler.handlerAdded(outboundCtx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            inboundCtx.remove();
        } finally {
            outboundCtx.remove();
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.channelRegistered(inboundCtx);
        } else {
            inboundCtx.fireChannelRegistered();
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.channelUnregistered(inboundCtx);
        } else {
            inboundCtx.fireChannelUnregistered();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.channelActive(inboundCtx);
        } else {
            inboundCtx.fireChannelActive();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.channelInactive(inboundCtx);
        } else {
            inboundCtx.fireChannelInactive();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.exceptionCaught(inboundCtx, cause);
        } else {
            inboundCtx.fireExceptionCaught(cause);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.userEventTriggered(inboundCtx, evt);
        } else {
            inboundCtx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.channelRead(inboundCtx, msg);
        } else {
            inboundCtx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.channelReadComplete(inboundCtx);
        } else {
            inboundCtx.fireChannelReadComplete();
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.ctx;
        if (!inboundCtx.removed) {
            inboundHandler.channelWritabilityChanged(inboundCtx);
        } else {
            inboundCtx.fireChannelWritabilityChanged();
        }
    }

    @Override
    public void bind(
            ChannelHandlerContext ctx,
            SocketAddress localAddress, ChannelPromise promise) throws Exception {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.bind(outboundCtx, localAddress, promise);
        } else {
            outboundCtx.bind(localAddress, promise);
        }
    }

    @Override
    public void connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.connect(outboundCtx, remoteAddress, localAddress, promise);
        } else {
            outboundCtx.connect(localAddress, promise);
        }
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.disconnect(outboundCtx, promise);
        } else {
            outboundCtx.disconnect(promise);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.close(outboundCtx, promise);
        } else {
            outboundCtx.close(promise);
        }
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.deregister(outboundCtx, promise);
        } else {
            outboundCtx.deregister(promise);
        }
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.read(outboundCtx);
        } else {
            outboundCtx.read();
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.write(outboundCtx, msg, promise);
        } else {
            outboundCtx.write(msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.flush(outboundCtx);
        } else {
            outboundCtx.flush();
        }
    }

    private static class DelegatingChannelHandlerContext implements ChannelHandlerContext {

        private final ChannelHandlerContext ctx;
        private final ChannelHandler handler;
        boolean removed;

        DelegatingChannelHandlerContext(ChannelHandlerContext ctx, ChannelHandler handler) {
            this.ctx = ctx;
            this.handler = handler;
        }

        @Override
        public Channel channel() {
            return ctx.channel();
        }

        @Override
        public EventExecutor executor() {
            return ctx.executor();
        }

        @Override
        public String name() {
            return ctx.name();
        }

        @Override
        public ChannelHandler handler() {
            return ctx.handler();
        }

        @Override
        public boolean isRemoved() {
            return removed || ctx.isRemoved();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            ctx.fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            ctx.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            ctx.fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            ctx.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            ctx.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object event) {
            ctx.fireUserEventTriggered(event);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            ctx.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            ctx.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            ctx.fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            return ctx.bind(localAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            return ctx.connect(remoteAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return ctx.connect(remoteAddress, localAddress);
        }

        @Override
        public ChannelFuture disconnect() {
            return ctx.disconnect();
        }

        @Override
        public ChannelFuture close() {
            return ctx.close();
        }

        @Override
        public ChannelFuture deregister() {
            return ctx.deregister();
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            return ctx.bind(localAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            return ctx.connect(remoteAddress, promise);
        }

        @Override
        public ChannelFuture connect(
                SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            return ctx.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            return ctx.disconnect(promise);
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            return ctx.close(promise);
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return ctx.deregister(promise);
        }

        @Override
        public ChannelHandlerContext read() {
            ctx.read();
            return this;
        }

        @Override
        public ChannelFuture write(Object msg) {
            return ctx.write(msg);
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return ctx.write(msg, promise);
        }

        @Override
        public ChannelHandlerContext flush() {
            ctx.flush();
            return this;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return ctx.writeAndFlush(msg, promise);
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return ctx.writeAndFlush(msg);
        }

        @Override
        public ChannelPipeline pipeline() {
            return ctx.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return ctx.alloc();
        }

        @Override
        public ChannelPromise newPromise() {
            return ctx.newPromise();
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return ctx.newProgressivePromise();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return ctx.newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return ctx.newFailedFuture(cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            return ctx.voidPromise();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return ctx.attr(key);
        }

        final void remove() {
            EventExecutor executor = executor();
            if (executor.inEventLoop()) {
                remove0();
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        remove0();
                    }
                });
            }
        }

        private void remove0() {
            if (!removed) {
                removed = true;
                try {
                    handler.handlerRemoved(this);
                } catch (Throwable cause) {
                    fireExceptionCaught(new ChannelPipelineException(
                            handler.getClass().getName() + ".handlerRemoved() has thrown an exception.", cause));
                }
            }
        }
    }
}
