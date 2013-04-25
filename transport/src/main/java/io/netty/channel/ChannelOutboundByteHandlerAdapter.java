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

import io.netty.buffer.ByteBuf;

/**
 * Abstract base class which handles outgoing bytes.
 */
public abstract class ChannelOutboundByteHandlerAdapter
        extends ChannelOperationHandlerAdapter implements ChannelOutboundByteHandler {
    @Override
    public ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return ChannelHandlerUtil.allocate(ctx);
    }

    @Override
    public void discardOutboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        ctx.outboundByteBuffer().discardSomeReadBytes();
    }

    /**
     * This method merely delegates the flush request to {@link #flush(ChannelHandlerContext, ByteBuf, ChannelPromise)}.
     */
    @Override
    public final void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        flush(ctx, ctx.outboundByteBuffer(), promise);
    }

    /**
     * Invoked when a flush request has been issued.
     *
     * @param ctx the current context
     * @param in this handler's outbound buffer
     * @param promise the promise associate with the current flush request
     */
    protected abstract void flush(ChannelHandlerContext ctx, ByteBuf in, ChannelPromise promise) throws Exception;
}
