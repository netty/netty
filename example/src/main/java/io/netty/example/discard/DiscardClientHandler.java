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
package io.netty.example.discard;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles a client-side channel.
 */
public class DiscardClientHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            DiscardClientHandler.class.getName());

    private final byte[] content;
    private ChannelHandlerContext ctx;

    public DiscardClientHandler(int messageSize) {
        if (messageSize <= 0) {
            throw new IllegalArgumentException(
                    "messageSize: " + messageSize);
        }
        content = new byte[messageSize];
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx)
            throws Exception {
        this.ctx = ctx;
        // Send the initial messages.
        generateTraffic();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in)
            throws Exception {
        // Server is supposed to send nothing, but if it sends something, discard it.
        in.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
            Throwable cause) throws Exception {
        // Close the connection when an exception is raised.
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                cause);
        ctx.close();
    }

    long counter;

    private void generateTraffic() {
        // Fill the outbound buffer up to 64KiB
        ByteBuf out = ctx.nextOutboundByteBuffer();
        while (out.readableBytes() < 65536) {
            out.writeBytes(content);
        }

        // Flush the outbound buffer to the socket.
        // Once flushed, generate the same amount of traffic again.
        ctx.flush().addListener(trafficGenerator);
    }

    private final ChannelFutureListener trafficGenerator = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                generateTraffic();
            }
        }
    };
}
