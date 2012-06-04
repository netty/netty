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
package io.netty.example.proxy;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundStreamHandlerAdapter;

public class HexDumpProxyBackendHandler extends ChannelInboundStreamHandlerAdapter {

    private final Channel inboundChannel;

    public HexDumpProxyBackendHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        ChannelBuffer in = ctx.inbound().byteBuffer();
        ChannelBuffer out = inboundChannel.outboundByteBuffer();
        out.discardReadBytes();
        out.writeBytes(in);
        in.clear();
        inboundChannel.flush();
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        HexDumpProxyFrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<Byte> ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        HexDumpProxyFrontendHandler.closeOnFlush(ctx.channel());
    }
}