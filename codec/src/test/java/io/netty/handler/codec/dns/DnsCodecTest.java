/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class DnsCodecTest {

    @Test
    public void sendQuery() throws Exception {
        byte[] dns = { 8, 8, 8, 8 }; // Google public dns
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(dns), 53);
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioDatagramChannel.class).option(ChannelOption.SO_BROADCAST, true)
                    .handler(new Initializer());
            Channel ch = b.connect(address).sync().channel();
            DnsQuery query = new DnsQuery(15305);
            query.addQuestion(new Question("206.226.125.74.in-addr.arpa", DnsEntry.TYPE_PTR)); // Google IP
            Assert.assertEquals("Invalid question count, expected 1.", 1, query.getHeader().questionCount());
            Assert.assertEquals("Invalid answer count, expected 0.", 0, query.getHeader().answerCount());
            Assert.assertEquals("Invalid authority resource record count, expected 0.", 0, query.getHeader()
                    .authorityResourceCount());
            Assert.assertEquals("Invalid additional resource record count, expected 0.", 0, query.getHeader()
                    .additionalResourceCount());
            Assert.assertEquals("Invalid type, should be TYPE_QUERY (0)", DnsHeader.TYPE_QUERY, query.getHeader()
                    .getType());
            ch.writeAndFlush(query).sync();
            if (!ch.closeFuture().await(5000)) {
                System.err.println("DNS request timed out.");
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    class Initializer extends ChannelInitializer<NioDatagramChannel> {

        @Override
        protected void initChannel(NioDatagramChannel ch) throws Exception {
            ch.pipeline().addLast("decoder", new DnsResponseDecoder()).addLast("encoder", new DnsQueryEncoder())
                    .addLast("handler", new Handler());
        }
    }

    class Handler extends SimpleChannelInboundHandler<DnsResponse> {

        @Override
        public void channelRead0(ChannelHandlerContext ctx, DnsResponse response) throws Exception {
            try {
                DnsResponseHeader header = response.getHeader();
                Assert.assertEquals("Invalid response code, expected TYPE_RESPONSE (1).", DnsHeader.TYPE_RESPONSE,
                        header.getType());
                Assert.assertFalse("Server response was truncated.", header.isTruncated());
                Assert.assertTrue("Inconsistency between recursion" + "desirability and availability.",
                        header.isRecursionDesired() == header.isRecursionAvailable());
                Assert.assertEquals("Invalid ID returned from server.", 15305, response.getHeader().getId());
                Assert.assertEquals("Question count in response not 1.", 1, response.getHeader().questionCount());
                Assert.assertTrue("Server didn't send any resources.", response.getHeader().answerCount()
                        + response.getHeader().authorityResourceCount()
                        + response.getHeader().additionalResourceCount() > 0);
                List<Resource> answers = response.getAnswers();
                for (Resource answer : answers) {
                    if (answer.type() == DnsEntry.TYPE_PTR) {
                        System.out.println(DnsResponseDecoder.readName(answer.content()));
                    }
                }
            } finally {
                ctx.close();
            }
        }
    }
}
