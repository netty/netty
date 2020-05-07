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
package io.netty.example.dns.doh;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;


import java.net.URL;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public final class DoHClient {

    private static final String QUERY_DOMAIN = "www.example.com";

    private DoHClient() {
    }

    public static void main(String[] args) throws Exception {
        URL url = new URL("https://dns.google/dns-query");
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            final SslContext sslContext = SslContextBuilder.forClient()
                    .protocols("TLSv1.2", "TLSv1.3")
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2,
                            ApplicationProtocolNames.HTTP_1_1))
                    .build();

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new Initializer(sslContext, url));

            final Channel ch = b.connect(url.getHost(), url.getDefaultPort()).sync().channel();

            Thread.sleep(1000); // Wait for TLS Handshake to finish

            int randomID = new Random().nextInt(60000 - 1000) + 1000;
            DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY);
            query.setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
            ch.writeAndFlush(query).sync();

            ch.closeFuture().await(10, TimeUnit.SECONDS);
        } finally {
            group.shutdownGracefully();
        }
    }
}
