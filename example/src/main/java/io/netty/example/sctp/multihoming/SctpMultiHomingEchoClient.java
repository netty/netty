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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.example.sctp.SctpEchoClientHandler;
import io.netty.util.internal.SocketUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * SCTP Echo Client with multi-homing support.
 */
public final class SctpMultiHomingEchoClient {

    private static final String CLIENT_PRIMARY_HOST = System.getProperty("host.primary", "127.0.0.1");
    private static final String CLIENT_SECONDARY_HOST = System.getProperty("host.secondary", "127.0.0.2");

    private static final int CLIENT_PORT = Integer.parseInt(System.getProperty("port.local", "8008"));

    private static final String SERVER_REMOTE_HOST = System.getProperty("host.remote", "127.0.0.1");
    private static final int SERVER_REMOTE_PORT = Integer.parseInt(System.getProperty("port.remote", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure the client.
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSctpChannel.class)
             .option(SctpChannelOption.SCTP_NODELAY, true)
             .handler(new ChannelInitializer<SctpChannel>() {
                 @Override
                 public void initChannel(SctpChannel ch) throws Exception {
                     ch.pipeline().addLast(
//                             new LoggingHandler(LogLevel.INFO),
                             new SctpEchoClientHandler());
                 }
             });

            InetSocketAddress localAddress = SocketUtils.socketAddress(CLIENT_PRIMARY_HOST, CLIENT_PORT);
            InetAddress localSecondaryAddress = SocketUtils.addressByName(CLIENT_SECONDARY_HOST);

            InetSocketAddress remoteAddress = SocketUtils.socketAddress(SERVER_REMOTE_HOST, SERVER_REMOTE_PORT);

            // Bind the client channel.
            ChannelFuture bindFuture = b.bind(localAddress).sync();

            // Get the underlying sctp channel
            SctpChannel channel = (SctpChannel) bindFuture.channel();

            // Bind the secondary address.
            // Please note that, bindAddress in the client channel should be done before connecting if you have not
            // enable Dynamic Address Configuration. See net.sctp.addip_enable kernel param
            channel.bindAddress(localSecondaryAddress).sync();

            // Finish connect
            ChannelFuture connectFuture = channel.connect(remoteAddress).sync();

            // Wait until the connection is closed.
            connectFuture.channel().closeFuture().sync();
        } finally {
            // Shut down the event loop to terminate all threads.
            group.shutdownGracefully();
        }
    }
}
