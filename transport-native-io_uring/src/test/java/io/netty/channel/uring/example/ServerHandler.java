/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.uring.example;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.uring.IoUringBufferRingExhaustedEvent;
import io.netty.util.ReferenceCountUtil;

final class ServerHandler extends ChannelInboundHandlerAdapter {

    private final boolean sink;

    ServerHandler(boolean sink) {
        this.sink = sink;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (sink) {
            ReferenceCountUtil.release(msg);
        } else {
            ctx.write(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (!sink) {
            ctx.flush();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        // Close the connection when an exception is raised.
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        if (evt instanceof IoUringBufferRingExhaustedEvent) {
            System.err.println(evt);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        // Ensure we are not writing to fast by stop reading if we can not flush out data fast enough.
        if (ctx.channel().isWritable()) {
            ctx.channel().config().setAutoRead(true);
        } else {
            ctx.flush();
            if (!ctx.channel().isWritable()) {
                ctx.channel().config().setAutoRead(false);
            }
        }
    }
}
