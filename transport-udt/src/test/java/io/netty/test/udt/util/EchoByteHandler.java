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

package io.netty.test.udt.util;

import com.yammer.metrics.core.Meter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Handler implementation for the echo client. It initiates the ping-pong
 * traffic between the echo client and server by sending the first message to
 * the server on activation.
 */
public class EchoByteHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(EchoByteHandler.class);

    private final ByteBuf message;

    private final Meter meter;

    public Meter meter() {
        return meter;
    }

    public EchoByteHandler(final Meter meter, final int messageSize) {

        this.meter = meter;

        message = Unpooled.buffer(messageSize);

        for (int i = 0; i < message.capacity(); i++) {
            message.writeByte((byte) i);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {

        log.info("ECHO active {}", NioUdtProvider.socketUDT(ctx.channel()).toStringOptions());

        ctx.writeAndFlush(message);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        if (meter != null) {
            meter.mark(buf.readableBytes());
        }
        ctx.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
            final Throwable e) {

        log.error("exception : {}", e.getMessage());

        ctx.close();
    }
}
