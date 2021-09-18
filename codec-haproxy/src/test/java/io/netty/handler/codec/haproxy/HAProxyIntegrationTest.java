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

package io.netty.handler.codec.haproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HAProxyIntegrationTest {

    @Test
    public void testBasicCase() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<HAProxyMessage> msgHolder = new AtomicReference<HAProxyMessage>();
        LocalAddress localAddress = new LocalAddress("HAProxyIntegrationTest");

        EventLoopGroup group = new DefaultEventLoopGroup();
        ServerBootstrap sb = new ServerBootstrap();
        sb.channel(LocalServerChannel.class)
          .group(group)
          .childHandler(new ChannelInitializer() {
              @Override
              protected void initChannel(Channel ch) throws Exception {
                  ch.pipeline().addLast(new HAProxyMessageDecoder());
                  ch.pipeline().addLast(new SimpleChannelInboundHandler<HAProxyMessage>() {
                      @Override
                      protected void channelRead0(ChannelHandlerContext ctx, HAProxyMessage msg) throws Exception {
                          msgHolder.set(msg.retain());
                          latch.countDown();
                      }
                  });
              }
          });
        Channel serverChannel = sb.bind(localAddress).sync().channel();

        Bootstrap b = new Bootstrap();
        Channel clientChannel = b.channel(LocalChannel.class)
                                 .handler(HAProxyMessageEncoder.INSTANCE)
                                 .group(group)
                                 .connect(localAddress).sync().channel();

        try {
            HAProxyMessage message = new HAProxyMessage(
                    HAProxyProtocolVersion.V1, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP4,
                    "192.168.0.1", "192.168.0.11", 56324, 443);
            clientChannel.writeAndFlush(message).sync();

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            HAProxyMessage readMessage = msgHolder.get();

            assertEquals(message.protocolVersion(), readMessage.protocolVersion());
            assertEquals(message.command(), readMessage.command());
            assertEquals(message.proxiedProtocol(), readMessage.proxiedProtocol());
            assertEquals(message.sourceAddress(), readMessage.sourceAddress());
            assertEquals(message.destinationAddress(), readMessage.destinationAddress());
            assertEquals(message.sourcePort(), readMessage.sourcePort());
            assertEquals(message.destinationPort(), readMessage.destinationPort());

            readMessage.release();
        } finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }
}
