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
package io.netty.example.udt.echo.rendevous;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.transport.udt.UdtMessage;
import io.netty.transport.udt.nio.NioUdtProvider;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;

/**
 * Handler implementation for the echo peer. It initiates the ping-pong traffic
 * between the echo peers by sending the first message to the other peer on
 * activation.
 */
public class MsgEchoPeerHandler extends
        ChannelInboundMessageHandlerAdapter<UdtMessage> {

    private static final Logger log = LoggerFactory
            .getLogger(MsgEchoPeerHandler.class.getName());

    private final UdtMessage message;

    public MsgEchoPeerHandler(final int messageSize) {
        final ByteBuf byteBuf = Unpooled.buffer(messageSize);
        for (int i = 0; i < byteBuf.capacity(); i++) {
            byteBuf.writeByte((byte) i);
        }
        message = new UdtMessage(byteBuf);
    }

    final Meter meter = Metrics.newMeter(MsgEchoPeerHandler.class, "rate",
            "bytes", TimeUnit.SECONDS);

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        log.info("ECHO active {}", NioUdtProvider.socketUDT(ctx.channel())
                .toStringOptions());
        final MessageBuf<Object> out = ctx.nextOutboundMessageBuffer();
        out.add(message);
        ctx.flush();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
            final Throwable cause) {
        log.error("close the connection when an exception is raised", cause);
        ctx.close();
    }

    @Override
    protected void messageReceived(final ChannelHandlerContext ctx,
            final UdtMessage message) throws Exception {
        final ByteBuf byteBuf = message.data();
        meter.mark(byteBuf.readableBytes());
        final MessageBuf<Object> out = ctx.nextOutboundMessageBuffer();
        out.add(message);
        ctx.flush();
    }

}
