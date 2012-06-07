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


public class ChannelInboundStreamHandlerAdapter
        extends ChannelStateHandlerAdapter implements ChannelInboundHandler<Byte> {

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return ChannelBufferHolders.byteBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        inboundBufferUpdated(ctx, ctx.inboundByteBuffer());
    }

    public void inboundBufferUpdated(ChannelHandlerContext ctx, ChannelBuffer in) throws Exception {
        ctx.nextInboundByteBuffer().writeBytes(in);
        in.discardReadBytes();
    }
}
