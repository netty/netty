/*
 * Copyright 2020 The Netty Project
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
package io.netty5.example.dns.udp;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.ByteBufUtil;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.nio.NioDatagramChannel;
import io.netty5.handler.codec.dns.DatagramDnsQuery;
import io.netty5.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty5.handler.codec.dns.DatagramDnsResponse;
import io.netty5.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty5.handler.codec.dns.DefaultDnsQuestion;
import io.netty5.handler.codec.dns.DnsQuery;
import io.netty5.handler.codec.dns.DnsQuestion;
import io.netty5.handler.codec.dns.DnsRawRecord;
import io.netty5.handler.codec.dns.DnsRecord;
import io.netty5.handler.codec.dns.DnsRecordType;
import io.netty5.handler.codec.dns.DnsSection;
import io.netty5.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class DnsClient {

    private static final String QUERY_DOMAIN = "www.example.com";
    private static final int DNS_SERVER_PORT = 53;
    private static final String DNS_SERVER_HOST = "8.8.8.8";

    private DnsClient() { }

    private static void handleQueryResp(DatagramDnsResponse msg) {
        if (msg.count(DnsSection.QUESTION) > 0) {
            DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
            System.out.printf("name: %s%n", question.name());
        }
        for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A) {
                // Just print the IP after query
                DnsRawRecord raw = (DnsRawRecord) record;
                System.out.println(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress addr = new InetSocketAddress(DNS_SERVER_HOST, DNS_SERVER_PORT);
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioDatagramChannel.class)
             .handler(new ChannelInitializer<DatagramChannel>() {
                 @Override
                 protected void initChannel(DatagramChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new DatagramDnsQueryEncoder())
                     .addLast(new DatagramDnsResponseDecoder())
                     .addLast(new SimpleChannelInboundHandler<DatagramDnsResponse>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, DatagramDnsResponse msg) {
                            try {
                                handleQueryResp(msg);
                            } finally {
                                ctx.close();
                            }
                        }
                    });
                 }
             });
            final Channel ch = b.bind(0).get();
            DnsQuery query = new DatagramDnsQuery(null, addr, 1).setRecord(
                    DnsSection.QUESTION,
                    new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
            ch.writeAndFlush(query).sync();
            boolean success = ch.closeFuture().await(10, TimeUnit.SECONDS);
            if (!success) {
                System.err.println("dns query timeout!");
                ch.close().sync();
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}
