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

import io.netty5.channel.internal.DelegatingChannelHandlerContext;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;

import java.net.SocketAddress;

import static java.util.Objects.requireNonNull;

/**
 *  Combines the inbound handling of one {@link ChannelHandler} with the outbound handling of
 *  another {@link ChannelHandler}.
 */
public class CombinedChannelDuplexHandler<I extends ChannelHandler, O extends ChannelHandler>
        extends ChannelHandlerAdapter {

    private CombinedChannelHandlerContext inboundCtx;
    private CombinedChannelHandlerContext outboundCtx;
    private volatile boolean handlerAdded;

    private I inboundHandler;
    private O outboundHandler;

    /**
     * Creates a new uninitialized instance. A class that extends this handler must invoke
     * {@link #init(ChannelHandler, ChannelHandler)} before adding this handler into a
     * {@link ChannelPipeline}.
     */
    protected CombinedChannelDuplexHandler() {
    }

    /**
     * Creates a new instance that combines the specified two handlers into one.
     */
    public CombinedChannelDuplexHandler(I inboundHandler, O outboundHandler) {
        init(inboundHandler, outboundHandler);
    }

    @Override
    public final boolean isSharable() {
        // Can't be sharable as we keep state.
        return false;
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

        outboundCtx = new CombinedChannelHandlerContext(ctx, outboundHandler);
        inboundCtx = new CombinedChannelHandlerContext(ctx, inboundHandler);

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
        assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelRegistered(inboundCtx);
        } else {
            inboundCtx.fireChannelRegistered();
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelUnregistered(inboundCtx);
        } else {
            inboundCtx.fireChannelUnregistered();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelActive(inboundCtx);
        } else {
            inboundCtx.fireChannelActive();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelInactive(inboundCtx);
        } else {
            inboundCtx.fireChannelInactive();
        }
    }

    @Override
    public void channelShutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) throws Exception {
        assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelShutdown(inboundCtx, direction);
        } else {
            inboundCtx.fireChannelInactive();
        }
    }

    @Override
    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelExceptionCaught(inboundCtx, cause);
        } else {
            inboundCtx.fireChannelExceptionCaught(cause);
        }
    }

    @Override
    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
            assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelInboundEvent(inboundCtx, evt);
        } else {
            inboundCtx.fireChannelInboundEvent(evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelRead(inboundCtx, msg);
        } else {
            inboundCtx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.delegatingCtx();
        if (!inboundCtx.removed) {
            inboundHandler.channelReadComplete(inboundCtx);
        } else {
            inboundCtx.fireChannelReadComplete();
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        assert ctx == inboundCtx.delegatingCtx();
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
        assert ctx == outboundCtx.delegatingCtx();
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
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            return outboundHandler.connect(outboundCtx, remoteAddress, localAddress);
        } else {
            return outboundCtx.connect(remoteAddress, localAddress);
        }
    }

    @Override
    public Future<Void> disconnect(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            return outboundHandler.disconnect(outboundCtx);
        } else {
            return outboundCtx.disconnect();
        }
    }

    @Override
    public Future<Void> close(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            return outboundHandler.close(outboundCtx);
        } else {
            return outboundCtx.close();
        }
    }

    @Override
    public Future<Void> shutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            return outboundHandler.shutdown(outboundCtx, direction);
        } else {
            return outboundCtx.shutdown(direction);
        }
    }

    @Override
    public Future<Void> register(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            return outboundHandler.register(outboundCtx);
        } else {
            return outboundCtx.register();
        }
    }

    @Override
    public Future<Void> deregister(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            return outboundHandler.deregister(outboundCtx);
        } else {
            return outboundCtx.deregister();
        }
    }

    @Override
    public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            outboundHandler.read(outboundCtx, readBufferAllocator);
        } else {
            outboundCtx.read(readBufferAllocator);
        }
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            return outboundHandler.write(outboundCtx, msg);
        } else {
            return outboundCtx.write(msg);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            outboundHandler.flush(outboundCtx);
        } else {
            outboundCtx.flush();
        }
    }

    @Override
    public Future<Void> sendOutboundEvent(ChannelHandlerContext ctx, Object event) {
        assert ctx == outboundCtx.delegatingCtx();
        if (!outboundCtx.removed) {
            return outboundHandler.sendOutboundEvent(outboundCtx, event);
        } else {
            return outboundCtx.sendOutboundEvent(event);
        }
    }

    @Override
    public long pendingOutboundBytes(ChannelHandlerContext ctx) {
        if (!outboundCtx.removed) {
            return outboundCtx.handler().pendingOutboundBytes(outboundCtx);
        }
        return 0;
    }

    private static final class CombinedChannelHandlerContext extends DelegatingChannelHandlerContext {

        private final ChannelHandler handler;
        boolean removed;

        CombinedChannelHandlerContext(ChannelHandlerContext ctx, ChannelHandler handler) {
            super(ctx);
            this.handler = handler;
        }

        @Override
        public boolean isRemoved() {
            return delegatingCtx().isRemoved() || removed;
        }

        @Override
        public ChannelHandler handler() {
            return handler;
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
                    this.fireChannelExceptionCaught(new ChannelPipelineException(
                            handler.getClass().getName() + ".handlerRemoved() has thrown an exception.", cause));
                }
            }
        }
    }
}
