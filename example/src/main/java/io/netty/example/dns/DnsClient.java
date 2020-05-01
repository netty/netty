/*
 * Copyright 2020 The Netty Project
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
package io.netty.example.dns;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;
import io.netty.util.internal.SocketUtils;

public class DnsClient {

    private static final String QUERY_DOMAIN = "www.example.com";
    private static final int DNS_SERVER_PORT = 53;
    private static final String DNS_SERVER_HOST = "8.8.8.8";

    private static void handleQueryResp(DatagramDnsResponse msg) {
        if (msg.count(DnsSection.QUESTION) > 0) {
            DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
            System.out.printf("name: %s\n", question.name());
        }
        for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A) {
                //just print the IP after query
                DnsRawRecord raw = (DnsRawRecord) record;
                System.out.println(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress addr = SocketUtils.socketAddress(DNS_SERVER_HOST, DNS_SERVER_PORT);
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioDatagramChannel.class)
             .handler(new ChannelInitializer<DatagramChannel>() {
                 @Override
                 protected void initChannel(DatagramChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new DatagramDnsQueryEncoder())
                     .addLast(new DatagramDnsResponseDecoder())
                     .addLast(new SimpleChannelInboundHandler<DatagramDnsResponse>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsResponse msg)
                                throws Exception {
                            try {
                                handleQueryResp(msg);
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                 }
             });
            Channel ch = b.bind(0).sync().channel();
            DnsQuery query = new DatagramDnsQuery(null, addr, 1).setRecord(
                    DnsSection.QUESTION,
                    new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
            ch.writeAndFlush(query);
            latch.await(10L, TimeUnit.SECONDS);
            ch.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
