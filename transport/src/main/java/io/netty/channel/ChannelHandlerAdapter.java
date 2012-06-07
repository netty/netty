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

public abstract class ChannelHandlerAdapter<I, O> extends ChannelStateHandlerAdapter
        implements ChannelOperationHandler, ChannelInboundHandler<I>, ChannelOutboundHandler<O> {

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        ChannelInboundHandlerAdapter.inboundBufferUpdated0(ctx);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.bind(localAddress, future);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.connect(remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        ctx.disconnect(future);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        ctx.close(future);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        ctx.deregister(future);
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        ChannelOutboundHandlerAdapter.flush0(ctx, future);
    }
}
