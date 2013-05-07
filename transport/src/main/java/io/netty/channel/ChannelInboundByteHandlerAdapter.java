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
 * Abstract base class for {@link ChannelInboundByteHandler} which should be extended by the user to
 * get notified once more data is ready to get consumed from the inbound {@link ByteBuf}.
 *
 * This implementation is a good starting point for most users.
 */
public abstract class ChannelInboundByteHandlerAdapter
        extends ChannelStateHandlerAdapter implements ChannelInboundByteHandler {

    /**
     * Create a new unpooled {@link ByteBuf} by default. Sub-classes may override this to offer a more
     * optimized implementation.
     */
    @Override
    public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return ChannelHandlerUtil.allocate(ctx);
    }

    @Override
    public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundByteBuffer().discardSomeReadBytes();
    }

    @Override
    public final void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        inboundBufferUpdated(ctx, ctx.inboundByteBuffer());
    }

    /**
     * Callback which will get notifed once the given {@link ByteBuf} received more data to read. What will be done
     * with the data at this point is up to the implementation.
     * Implementations may choose to read it or just let it in the buffer to read it later.
     */
    protected abstract void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception;
}
