/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel;

import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.Attribute;
import io.netty5.util.AttributeKey;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.net.SocketAddress;

import static java.util.Objects.requireNonNull;

/**
 *  Combines the inbound handling of one {@link ChannelHandler} with the outbound handling of
 *  another {@link ChannelHandler}.
 */
public class CombinedChannelDuplexHandler<I extends ChannelHandler, O extends ChannelHandler>
        extends ChannelHandlerAdapter {

    private DelegatingChannelHandlerContext inboundCtx;
    private DelegatingChannelHandlerContext outboundCtx;
    private volatile boolean handlerAdded;

    private I inboundHandler;
    private O outboundHandler;

    /**
     * Creates a new uninitialized instance. A class that extends this handler must invoke
     * {@link #init(ChannelHandler, ChannelHandler)} before adding this handler into a
     * {@link ChannelPipeline}.
     */
    protected CombinedChannelDuplexHandler() {
        ensureNotSharable();
    }

    /**
     * Creates a new instance that combines the specified two handlers into one.
     */
    public CombinedChannelDuplexHandler(I inboundHandler, O outboundHandler) {
        ensureNotSharable();
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

        requireNonNull(inboundHandler, "inboundHandler");
        requireNonNull(outboundHandler, "outboundHandler");
        if (ChannelHandlerMask.isOutbound(inboundHandler.getClass())) {
            throw new IllegalArgumentException(
                    "inboundHandler must not implement any outbound method to get combined.");
        }
        if (ChannelHandlerMask.isInbound(outboundHandler.getClass())) {
            throw new IllegalArgumentException(
                    "outboundHandler must not implement any inbound method to get combined.");
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
     * Removes the inbound {@link ChannelHandler} that was combined in this {@link CombinedChannelDuplexHandler}.
     */
    public final void removeInboundHandler() {
        checkAdded();
        inboundCtx.remove();
    }

    /**
     * Removes the outbound {@link ChannelHandler} that was combined in this {@link CombinedChannelDuplexHandler}.
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
        inboundCtx = new DelegatingChannelHandlerContext(ctx, inboundHandler);

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
    public Future<Void> bind(
            ChannelHandlerContext ctx,
            SocketAddress localAddress) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            return outboundHandler.bind(outboundCtx, localAddress);
        } else {
            return outboundCtx.bind(localAddress);
        }
    }

    @Override
    public Future<Void> connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            return outboundHandler.connect(outboundCtx, remoteAddress, localAddress);
        } else {
            return outboundCtx.connect(remoteAddress, localAddress);
        }
    }

    @Override
    public Future<Void> disconnect(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            return outboundHandler.disconnect(outboundCtx);
        } else {
            return outboundCtx.disconnect();
        }
    }

    @Override
    public Future<Void> close(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            return outboundHandler.close(outboundCtx);
        } else {
            return outboundCtx.close();
        }
    }

    @Override
    public Future<Void> register(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            return outboundHandler.register(outboundCtx);
        } else {
            return outboundCtx.register();
        }
    }

    @Override
    public Future<Void> deregister(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            return outboundHandler.deregister(outboundCtx);
        } else {
            return outboundCtx.deregister();
        }
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.read(outboundCtx);
        } else {
            outboundCtx.read();
        }
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            return outboundHandler.write(outboundCtx, msg);
        } else {
            return outboundCtx.write(msg);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.ctx;
        if (!outboundCtx.removed) {
            outboundHandler.flush(outboundCtx);
        } else {
            outboundCtx.flush();
        }
    }

    private static final class DelegatingChannelHandlerContext implements ChannelHandlerContext {

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
        public Future<Void> bind(SocketAddress localAddress) {
            return ctx.bind(localAddress);
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress) {
            return ctx.connect(remoteAddress);
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return ctx.connect(remoteAddress, localAddress);
        }

        @Override
        public Future<Void> disconnect() {
            return ctx.disconnect();
        }

        @Override
        public Future<Void> close() {
            return ctx.close();
        }

        @Override
        public Future<Void> register() {
            return ctx.register();
        }

        @Override
        public Future<Void> deregister() {
            return ctx.deregister();
        }

        @Override
        public ChannelHandlerContext read() {
            ctx.read();
            return this;
        }

        @Override
        public Future<Void> write(Object msg) {
            return ctx.write(msg);
        }

        @Override
        public ChannelHandlerContext flush() {
            ctx.flush();
            return this;
        }

        @Override
        public Future<Void> writeAndFlush(Object msg) {
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
        public BufferAllocator bufferAllocator() {
            return ctx.bufferAllocator();
        }

        @Override
        public Promise<Void> newPromise() {
            return ctx.newPromise();
        }

        @Override
        public Future<Void> newSucceededFuture() {
            return ctx.newSucceededFuture();
        }

        @Override
        public Future<Void> newFailedFuture(Throwable cause) {
            return ctx.newFailedFuture(cause);
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return ctx.channel().attr(key);
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return ctx.channel().hasAttr(key);
        }

        void remove() {
            EventExecutor executor = executor();
            if (executor.inEventLoop()) {
                remove0();
            } else {
                executor.execute(this::remove0);
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
