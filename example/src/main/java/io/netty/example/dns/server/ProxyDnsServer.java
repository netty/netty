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
package io.netty.example.dns.server;

import io.netty.handler.codec.dns.server.DnsServerHandler;
import io.netty.handler.codec.dns.server.DnsAnswerProvider;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.example.dns.client.DnsClient;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import java.net.InetSocketAddress;

/**
 * A demo proxy DNS server that simply delegates to Google Public DNS. To be
 * useful in practice, one would want to implement caching.
 */
public class ProxyDnsServer {

    public static void main(String[] ignored) throws InterruptedException {

        NioEventLoopGroup group = new NioEventLoopGroup(8); // 4 threads

        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new DnsServerHandler(new AnswerProviderImpl(group)));

        Channel channel = b.bind(5753).sync().channel();
        System.out.println("Started demo proxy DNS server");
        channel.closeFuture().await();
    }

    private static class AnswerProviderImpl implements DnsAnswerProvider {

        private final DnsClient client;

        AnswerProviderImpl(EventLoopGroup group) throws InterruptedException {
            client = new DnsClient(group);
        }

        @Override
        public void respond(final DnsQuery query, ChannelHandlerContext ctx,
                final DnsResponseSender responseSender) throws Exception {
            client.query(query.questions(), new DnsClient.Callback() {
                @Override
                public void onResponseReceived(DnsResponse theirResponse) throws Exception {
                    final DnsResponse ourResponse = new DnsResponse(query.header().id(), query.recipient());
                    theirResponse.copyInto(ourResponse);
                    responseSender.withResponse(ourResponse);
                }
            });
        }
    }
}
