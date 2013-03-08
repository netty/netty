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
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerUtil.SingleOutboundMessageHandler;
import io.netty.util.Signal;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * Abstract base class which handles messages of a specific type.
 *
 * If your {@link ChannelOutboundMessageHandlerAdapter} handles messages of type {@link ByteBuf} or {@link Object}
 * and you want to add a {@link ByteBuf} to the next buffer in the {@link ChannelPipeline} use
 * {@link ChannelHandlerUtil#addToNextOutboundBuffer(ChannelHandlerContext, Object)}.
 *
 * @param <I>   The type of the messages to handle
 */
public abstract class ChannelOutboundMessageHandlerAdapter<I>
        extends ChannelOperationHandlerAdapter
        implements ChannelOutboundMessageHandler<I>, SingleOutboundMessageHandler<I> {

    /**
     * Thrown by {@link #flush(ChannelHandlerContext, Object)} to abort message processing.
     */
    protected static final Signal ABORT = ChannelHandlerUtil.ABORT;

    private final TypeParameterMatcher msgMatcher;
    private boolean closeOnFailedFlush = true;

    protected ChannelOutboundMessageHandlerAdapter() {
        msgMatcher = TypeParameterMatcher.find(this, ChannelOutboundMessageHandlerAdapter.class, "I");
    }

    protected final boolean isCloseOnFailedFlush() {
        return closeOnFailedFlush;
    }

    protected final void setCloseOnFailedFlush(boolean closeOnFailedFlush) {
        this.closeOnFailedFlush = closeOnFailedFlush;
    }

    @Override
    public MessageBuf<I> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.outboundMessageBuffer().release();
    }

    @Override
    public final void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ChannelHandlerUtil.handleFlush(ctx, promise, isCloseOnFailedFlush(), this);
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msgMatcher.match(msg);
    }

    @Override
    public void beginFlush(ChannelHandlerContext ctx) throws Exception { }

    @Override
    public void endFlush(ChannelHandlerContext ctx) throws Exception { }
}
