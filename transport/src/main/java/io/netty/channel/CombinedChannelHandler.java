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

import io.netty.buffer.Buf;

import java.net.SocketAddress;

/**
 *  Combines a {@link ChannelInboundHandler} and a {@link ChannelOutboundHandler} into one {@link ChannelHandler}.
 *
 */
public class CombinedChannelHandler extends ChannelStateHandlerAdapter implements ChannelInboundHandler,
        ChannelOutboundHandler {

    private ChannelOutboundHandler out;
    private ChannelInboundHandler in;

    protected CombinedChannelHandler() {
        // User will call init in the subclass constructor.
    }

    /**
     * Combine the given {@link ChannelInboundHandler} and {@link ChannelOutboundHandler}.
     */
    public CombinedChannelHandler(
            ChannelInboundHandler inboundHandler, ChannelOutboundHandler outboundHandler) {
        init(inboundHandler, outboundHandler);
    }

    /**
     * Needs to get called before the handler can be added to the {@link ChannelPipeline}.
     * Otherwise it will trigger a {@link IllegalStateException} later.
     *
     */
    protected void init(
            ChannelInboundHandler inboundHandler, ChannelOutboundHandler outboundHandler) {
        if (inboundHandler == null) {
            throw new NullPointerException("inboundHandler");
        }
        if (outboundHandler == null) {
            throw new NullPointerException("outboundHandler");
        }
        if (inboundHandler instanceof ChannelOperationHandler) {
            throw new IllegalArgumentException(
                    "inboundHandler must not implement " +
                    ChannelOperationHandler.class.getSimpleName() + " to get combined.");
        }
        if (outboundHandler instanceof ChannelStateHandler) {
            throw new IllegalArgumentException(
                    "outboundHandler must not implement " +
                    ChannelStateHandler.class.getSimpleName() + " to get combined.");
        }

        if (in != null) {
            throw new IllegalStateException("init() cannot be called more than once.");
        }

        in = inboundHandler;
        out = outboundHandler;
    }

    @Override
    public Buf newInboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        return in.newInboundBuffer(ctx);
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx, Buf buf) throws Exception {
        in.freeInboundBuffer(ctx, buf);
    }

    @Override
    public Buf newOutboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        return out.newOutboundBuffer(ctx);
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx, Buf buf) throws Exception {
        out.freeOutboundBuffer(ctx, buf);
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        if (in == null) {
            throw new IllegalStateException(
                    "not initialized yet - call init() in the constructor of the subclass");
        }

        try {
            in.beforeAdd(ctx);
        } finally {
            out.beforeAdd(ctx);
        }
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        try {
            in.afterAdd(ctx);
        } finally {
            out.afterAdd(ctx);
        }
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        try {
            in.beforeRemove(ctx);
        } finally {
            out.beforeRemove(ctx);
        }
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        try {
            in.afterRemove(ctx);
        } finally {
            out.afterRemove(ctx);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        in.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        in.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        in.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        in.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        in.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        in.userEventTriggered(ctx, evt);
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        in.inboundBufferUpdated(ctx);
    }

    @Override
    public void bind(
            ChannelHandlerContext ctx,
            SocketAddress localAddress, ChannelPromise promise) throws Exception {
        out.bind(ctx, localAddress, promise);
    }

    @Override
    public void connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        out.connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        out.disconnect(ctx, promise);
    }

    @Override
    public void close(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        out.close(ctx, promise);
    }

    @Override
    public void deregister(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        out.deregister(ctx, promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        out.read(ctx);
    }

    @Override
    public void flush(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        out.flush(ctx, promise);
    }

    @Override
    public void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) throws Exception {
        out.sendFile(ctx, region, promise);
    }
}
