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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.CNameDnsRecord;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.Ipv4AddressRecord;
import io.netty.handler.codec.dns.NameServerDnsRecord;
import java.net.InetSocketAddress;

/**
 * A demo DNS server that can answer one query, for exaple.netty.io.
 */
public class DnsServer {

    public static void main(String[] ignored) throws InterruptedException {

        NioEventLoopGroup group = new NioEventLoopGroup(4); // 4 threads

        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new DnsServerHandler(new AnswerProviderImpl()));

        Channel channel = b.bind(5753).sync().channel();
        System.out.println("Started demo DNS server");
        channel.closeFuture().await();
        group.shutdownGracefully();
    }

    private static class AnswerProviderImpl implements DnsAnswerProvider {

        @Override
        public void respond(DnsQuery query, ChannelHandlerContext ctx,
                DnsResponseSender callback) throws Exception {
            // Find the destination we need to reply to
            InetSocketAddress remoteAddress = query.recipient();
            // Create a new response
            DnsResponse resp = new DnsResponse(query.header().id(), remoteAddress);
            // Set the headers
            resp.header().setResponseCode(DnsResponseCode.NOERROR)
                    .setOpcode(query.header().opcode())
                    .setRecursionAvailable(false)
                    .setRecursionDesired(query.header().isRecursionDesired())
                    .setZ(query.header().z());
            // Iterate all the questions
            boolean found = false;
            for (DnsQuestion q : query) {
                // The one question we can answer
                if ("git.timboudreau.com".equals(q.name())) {
                    found = true;
                    resp.addQuestion(q);
                    resp.addAnswer(new CNameDnsRecord("example.netty.io", 300, "netty.io"));
                    resp.addAnswer(new Ipv4AddressRecord("timboudreau.com", 300, "104.28.9.44"));
                    resp.addAuthorityResource(new NameServerDnsRecord("netty.io",
                            "theo.ns.cloudflare.com", 8185));
                    resp.addAdditionalResource(new Ipv4AddressRecord("theo.ns.cloudflare.com",
                            170923, "173.245.58.147"));
                    resp.header().setAuthoritativeAnswer(true);
                }
            }
            if (!found) {
                resp.header().setResponseCode(DnsResponseCode.NOTZONE);
                resp.addQuestions(query);
                resp.header().setAuthoritativeAnswer(false);
            }
            callback.withResponse(resp);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
        }
    }
}
