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

public abstract class ChannelInboundHandlerAdapter<I> extends ChannelStateHandlerAdapter
        implements ChannelInboundHandler<I> {

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        inboundBufferUpdated0(ctx);
    }

    static <I> void inboundBufferUpdated0(ChannelHandlerContext ctx) {
        if (ctx.hasInboundMessageBuffer()) {
            Queue<I> in = ctx.inboundMessageBuffer();
            Queue<Object> nextIn = ctx.nextInboundMessageBuffer();
            for (;;) {
                I msg = in.poll();
                if (msg == null) {
                    break;
                }
                nextIn.add(msg);
            }
        } else {
            ChannelBuffer in = ctx.inboundByteBuffer();
            ChannelBuffer nextIn = ctx.nextInboundByteBuffer();
            nextIn.writeBytes(in);
            in.discardReadBytes();
        }
        ctx.fireInboundBufferUpdated();
    }
}
