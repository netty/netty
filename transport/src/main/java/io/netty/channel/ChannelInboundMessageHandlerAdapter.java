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

import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;

public abstract class ChannelInboundMessageHandlerAdapter<I>
        extends ChannelInboundHandlerAdapter implements ChannelInboundMessageHandler<I> {

    private final Class<?>[] acceptedMsgTypes;

    protected ChannelInboundMessageHandlerAdapter(Class<?>... acceptedMsgTypes) {
        this.acceptedMsgTypes = ChannelHandlerUtil.acceptedMessageTypes(acceptedMsgTypes);
    }

    @Override
    public MessageBuf<I> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        if (!beginMessageReceived(ctx)) {
            return;
        }

        boolean unsupportedFound = false;

        try {
            MessageBuf<I> in = ctx.inboundMessageBuffer();
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }
                try {
                    if (!isSupported(msg)) {
                        ChannelHandlerUtil.addToNextInboundBuffer(ctx, msg);
                        unsupportedFound = true;
                        continue;
                    }
                    if (unsupportedFound) {
                        // the last message were unsupported, but now we received one that is supported.
                        // So reset the flag and notify the next context
                        unsupportedFound = false;
                        ctx.fireInboundBufferUpdated();
                    }
                    messageReceived(ctx, (I) msg);
                } catch (Throwable t) {
                    exceptionCaught(ctx, t);
                }
            }
        } finally {
            if (unsupportedFound) {
                ctx.fireInboundBufferUpdated();
            }
        }

        endMessageReceived(ctx);
    }

    /**
     * Returns {@code true} if and only if the specified message can be handled by this handler.
     *
     * @param msg the message
     */
    public boolean isSupported(Object msg) throws Exception {
        return ChannelHandlerUtil.acceptMessage(acceptedMsgTypes, msg);
    }

    /**
     * Will get notified once {@link #inboundBufferUpdated(ChannelHandlerContext)} was called.
     *
     * If this method returns {@code false} no further processing of the {@link MessageBuf}
     * will be done until the next call of {@link #inboundBufferUpdated(ChannelHandlerContext)}.
     *
     * This will return {@code true} by default, and may get overriden by sub-classes for
     * special handling.
     */
    public boolean beginMessageReceived(ChannelHandlerContext ctx) throws Exception {
        return true;
    }

    public abstract void messageReceived(ChannelHandlerContext ctx, I msg) throws Exception;
    public void endMessageReceived(ChannelHandlerContext ctx) throws Exception { }
}
