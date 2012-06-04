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

import java.util.Queue;

public class ChannelInboundMessageHandlerAdapter<I> extends
        ChannelInboundHandlerAdapter<I> {

    @Override
    public ChannelBufferHolder<I> newInboundBuffer(
            ChannelInboundHandlerContext<I> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<I> ctx)
            throws Exception {
        Queue<I> in = ctx.inbound().messageBuffer();
        for (;;) {
            I msg = in.poll();
            if (msg == null) {
                break;
            }
            try {
                messageReceived(ctx, msg);
                ctx.fireInboundBufferUpdated();
            } catch (Throwable t) {
                ctx.fireExceptionCaught(t);
            }
        }
    }

    public void messageReceived(ChannelInboundHandlerContext<I> ctx, I msg) throws Exception {
        ctx.nextInboundMessageBuffer().add(msg);
    }
}
