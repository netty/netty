/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.dns.server;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DnsDecoderException;
import io.netty.handler.codec.dns.server.DnsAnswerProvider.DnsResponseSender;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQueryDecoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseEncoder;

/**
 * Handles some of the plumbing of writing a DNS server. To use, simply
 * implement DnsAnswerProvider and pass it to the constructor.
 *
 * <pre>
 *        NioEventLoopGroup group = new NioEventLoopGroup(4); // 4 threads
 *
 *        Bootstrap b = new Bootstrap();
 *        b.group(group)
 *                .channel(NioDatagramChannel.class)
 *                .option(ChannelOption.SO_BROADCAST, true)
 *                .handler(new DnsServerHandler(new AnswerProviderImpl()));
 *
 *        Channel channel = b.bind(5753).sync().channel();
 *        channel.closeFuture().await();
 * </pre>
 */
@ChannelHandler.Sharable
public class DnsServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final DnsAnswerProvider answerer;
    private final DnsResponseEncoder encoder = new DnsResponseEncoder();
    private final DnsQueryDecoder decoder = new DnsQueryDecoder();

    public DnsServerHandler(DnsAnswerProvider answerer) {
        super(DatagramPacket.class);
        if (answerer == null) {
            throw new NullPointerException("answerer");
        }
        this.answerer = answerer;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        DnsQuery query = decoder.decode(ctx, packet);
        try {
            answerer.respond(query, ctx, new Responder(ctx, this));
        } catch (DnsDecoderException ex) {
            DnsResponse resp = new DnsResponse(query.header().id(), query.recipient());
            resp.header().setResponseCode(ex.code())
                    .setZ(query.header().z())
                    .setOpcode(query.header().opcode());
            for (DnsQuestion question : query) {
                resp.addQuestion(question);
            }
        }
    }

    private static class Responder implements DnsResponseSender {

        private final ChannelHandlerContext ctx;
        private final DnsServerHandler handler;

        public Responder(ChannelHandlerContext ctx, DnsServerHandler handler) {
            this.ctx = ctx;
            this.handler = handler;
        }

        @Override
        public void withResponse(DnsResponse response) throws Exception {
            try {
                ctx.writeAndFlush(handler.encoder.encode(ctx, response));
            } catch (Exception e) {
                handler.exceptionCaught(ctx, e);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        answerer.exceptionCaught(ctx, cause);
    }
}
