/*
 * Copyright 2024 The Netty Project
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
package io.netty5.example.ipfilter;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.ipfilter.IpFilterRuleType;
import io.netty5.handler.ipfilter.IpSubnetFilter;
import io.netty5.handler.ipfilter.IpSubnetFilterRule;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Discards any incoming data from a blacklisteded IP address subnet and accepts the rest.
 */
public final class IpSubnetFilterExample {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8009"));

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, NioIoHandler.newFactory());

        try {
            List<IpSubnetFilterRule> rules = new ArrayList<>();

            // Reject 10.10.10.0/24 and 192.168.0.0/16 ranges but accept the rest
            rules.add(new IpSubnetFilterRule("10.10.10.0", 24, IpFilterRuleType.REJECT));
            rules.add(new IpSubnetFilterRule("192.168.0.0", 16, IpFilterRuleType.REJECT));

            // Share this same Handler instance with multiple ChannelPipeline(s).
            final IpSubnetFilter ipFilter = new IpSubnetFilter(rules);

            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addFirst(ipFilter);

                            p.addLast(new SimpleChannelInboundHandler<Buffer>() {
                                @Override
                                protected void messageReceived(ChannelHandlerContext ctx, Buffer msg) {
                                    System.out.println("Received data from: " + ctx.channel().remoteAddress());
                                }
                            });
                        }
                    });

            // Bind and start to accept incoming connections.
            Channel ch = b.bind(PORT).asStage().get();

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            ch.closeFuture().asStage().await();
        } finally {
            group.shutdownGracefully();
        }
    }
}
