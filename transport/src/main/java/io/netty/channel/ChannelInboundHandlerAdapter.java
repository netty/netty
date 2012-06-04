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

import io.netty.buffer.ChannelBuffer;

import java.util.Queue;

public abstract class ChannelInboundHandlerAdapter<I> extends AbstractChannelHandler
        implements ChannelInboundHandler<I> {

    @Override
    public void channelRegistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<I> ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelInboundHandlerContext<I> ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<I> ctx) throws Exception {
        inboundBufferUpdated0(ctx);
    }

    static <I> void inboundBufferUpdated0(ChannelInboundHandlerContext<I> ctx) {
        if (ctx.inbound().isBypass()) {
            ctx.fireInboundBufferUpdated();
            return;
        }

        if (ctx.inbound().hasMessageBuffer()) {
            Queue<I> in = ctx.inbound().messageBuffer();
            Queue<Object> nextIn = ctx.nextInboundMessageBuffer();
            for (;;) {
                I msg = in.poll();
                if (msg == null) {
                    break;
                }
                nextIn.add(msg);
            }
        } else {
            ChannelBuffer in = ctx.inbound().byteBuffer();
            ChannelBuffer nextIn = ctx.nextInboundByteBuffer();
            nextIn.writeBytes(in);
            in.discardReadBytes();
        }
        ctx.fireInboundBufferUpdated();
    }
}
