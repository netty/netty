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
package io.netty.handler.codec;

import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Queue;

public abstract class MessageToMessageDecoder<I, O> extends ChannelInboundHandlerAdapter<I> {

    @Override
    public ChannelBufferHolder<I> newInboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx)
            throws Exception {
        Queue<I> in = ctx.inboundMessageBuffer();
        boolean notify = false;
        for (;;) {
            try {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }
                if (!isDecodable(msg)) {
                    ctx.nextInboundMessageBuffer().add(msg);
                    notify = true;
                    continue;
                }

                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                O omsg = decode(ctx, imsg);
                if (omsg == null) {
                    // Decoder consumed a message but returned null.
                    // Probably it needs more messages because it's an aggregator.
                    continue;
                }

                if (CodecUtil.unfoldAndAdd(ctx, omsg, true)) {
                    notify = true;
                }
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new DecoderException(t));
                }
            }
        }
        if (notify) {
            ctx.fireInboundBufferUpdated();
        }
    }

    /**
     * Returns {@code true} if and only if the specified message can be decoded by this decoder.
     *
     * @param msg the message
     */
    public boolean isDecodable(Object msg) throws Exception {
        return true;
    }

    public abstract O decode(ChannelHandlerContext ctx, I msg) throws Exception;
}
