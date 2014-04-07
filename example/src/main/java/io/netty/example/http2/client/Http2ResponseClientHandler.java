/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http2.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.util.CharsetUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A handler that interprets response messages as text and prints it out to the console.
 */
public class Http2ResponseClientHandler extends SimpleChannelInboundHandler<Http2DataFrame> {
    private final BlockingQueue<ChannelFuture> queue = new LinkedBlockingQueue<ChannelFuture>();

    private ByteBuf data;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Http2DataFrame frame) throws Exception {
        System.out.println("Received frame: " + frame);

        // Copy the data into the buffer.
        int available = frame.content().readableBytes();
        if (data == null) {
            data = ctx.alloc().buffer(available);
            data.writeBytes(frame.content());
        } else {
            // Expand the buffer
            ByteBuf newBuffer = ctx.alloc().buffer(data.readableBytes() + available);
            newBuffer.writeBytes(data);
            newBuffer.writeBytes(frame.content());
            data.release();
            data = newBuffer;
        }

        // If it's the last frame, print the complete message.
        if (frame.isEndOfStream()) {
            byte[] bytes = new byte[data.readableBytes()];
            data.readBytes(bytes);
            System.out.println("Received message: " + new String(bytes, CharsetUtil.UTF_8));

            // Free the data buffer.
            data.release();
            data = null;

            queue.add(ctx.channel().newSucceededFuture());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        queue.add(ctx.channel().newFailedFuture(cause));
        cause.printStackTrace();
        ctx.close();
    }

    public BlockingQueue<ChannelFuture> queue() {
        return queue;
    }
}
