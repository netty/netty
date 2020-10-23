/*
 * Copyright 2015 The Netty Project
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
package io.netty.example.sctp.multihoming;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.SctpServerChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.example.sctp.SctpEchoServerHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.SocketUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * SCTP Echo Server with multi-homing support.
 */
public final class SctpMultiHomingEchoServer {

    private static final String SERVER_PRIMARY_HOST = System.getProperty("host.primary", "127.0.0.1");
    private static final String SERVER_SECONDARY_HOST = System.getProperty("host.secondary", "127.0.0.2");

    private static final int SERVER_PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioSctpServerChannel.class)
             .option(ChannelOption.SO_BACKLOG, 100)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SctpChannel>() {
                 @Override
                 public void initChannel(SctpChannel ch) throws Exception {
                     ch.pipeline().addLast(
//                             new LoggingHandler(LogLevel.INFO),
                             new SctpEchoServerHandler());
                 }
             });

            InetSocketAddress localAddress = SocketUtils.socketAddress(SERVER_PRIMARY_HOST, SERVER_PORT);
            InetAddress localSecondaryAddress = SocketUtils.addressByName(SERVER_SECONDARY_HOST);

            // Bind the server to primary address.
            ChannelFuture bindFuture = b.bind(localAddress).sync();

            //Get the underlying sctp channel
            SctpServerChannel channel = (SctpServerChannel) bindFuture.channel();

            //Bind the secondary address
            ChannelFuture connectFuture = channel.bindAddress(localSecondaryAddress).sync();

            // Wait until the connection is closed.
            connectFuture.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
