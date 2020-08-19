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
import java.util.concurrent.TimeUnit;

public final class DoHClient {

    private static final String QUERY_DOMAIN = "www.google.com";

    private DoHClient() {
    }

    public static void main(String[] args) throws Exception {
        URL url = new URL("https://cloudflare-dns.com/dns-query");
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            final SslContext sslContext = SslContextBuilder.forClient()
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

            Channel channel = b.connect(url.getHost(), url.getDefaultPort()).sync().channel();

            // Wait for application protocol negotiation to finish
            channel.pipeline().get(ALPNHandler.class).promise().sync();

            // RFC 8484 recommends ID 0 [https://tools.ietf.org/html/rfc8484#section-4.1]
            DnsQuery dnsQueryA = new DefaultDnsQuery(0, DnsOpCode.QUERY);
            dnsQueryA.setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
            channel.writeAndFlush(dnsQueryA).sync();

            DnsQuery queryAAAA = new DefaultDnsQuery(0, DnsOpCode.QUERY);
            queryAAAA.setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.AAAA));
            channel.writeAndFlush(queryAAAA).sync();
            channel.closeFuture().await(10, TimeUnit.SECONDS);
        } finally {
            group.shutdownGracefully();
        }
    }
}
