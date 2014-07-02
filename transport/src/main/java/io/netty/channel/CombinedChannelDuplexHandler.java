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

import java.net.SocketAddress;

/**
 *  Combines a {@link ChannelInboundHandler} and a {@link ChannelOutboundHandler} into one {@link ChannelHandler}.
 *
 */
public class CombinedChannelDuplexHandler<I extends ChannelInboundHandler, O extends ChannelOutboundHandler>
        extends ChannelDuplexHandler {

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

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (inboundHandler == null) {
            throw new IllegalStateException(
                    "init() must be invoked before being added to a " + ChannelPipeline.class.getSimpleName() +
                            " if " +  CombinedChannelDuplexHandler.class.getSimpleName() +
                            " was constructed with the default constructor.");
        }
        try {
            inboundHandler.handlerAdded(ctx);
        } finally {
            outboundHandler.handlerAdded(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            inboundHandler.handlerRemoved(ctx);
        } finally {
            outboundHandler.handlerRemoved(ctx);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        inboundHandler.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        inboundHandler.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        inboundHandler.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        inboundHandler.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        inboundHandler.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        inboundHandler.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        inboundHandler.channelRead(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        inboundHandler.channelReadComplete(ctx);
    }

    @Override
    public void bind(
            ChannelHandlerContext ctx,
            SocketAddress localAddress, ChannelPromise promise) throws Exception {
        outboundHandler.bind(ctx, localAddress, promise);
    }

    @Override
    public void connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        outboundHandler.connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        outboundHandler.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        outboundHandler.close(ctx, promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        outboundHandler.deregister(ctx, promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        outboundHandler.read(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        outboundHandler.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        outboundHandler.flush(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        inboundHandler.channelWritabilityChanged(ctx);
    }
}
