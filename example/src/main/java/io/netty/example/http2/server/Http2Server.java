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

package io.netty.example.http2.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * A HTTP/2 Server that responds to requests with a Hello World.
 * <p>
 * Once started, you can test the server with the example client.
 */
public class Http2Server {

    private final int port;

    public Http2Server(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new Http2ServerInitializer());

            Channel ch = b.bind(port).sync().channel();
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        checkForNpnSupport();
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8443;
        }

        System.out.println("HTTP2 server started at port " + port + '.');

        new Http2Server(port).run();
    }

    public static void checkForNpnSupport() {
        try {
            Class.forName("sun.security.ssl.NextProtoNegoExtension");
        } catch (ClassNotFoundException ignored) {
            System.err.println();
            System.err.println("Could not locate Next Protocol Negotiation (NPN) implementation.");
            System.err.println("The NPN jar should have been made available when building the examples with maven.");
            System.err.println("Please check that your JDK is among those supported by Jetty-NPN:");
            System.err.println("http://wiki.eclipse.org/Jetty/Feature/NPN#Versions");
            System.err.println();
            throw new IllegalStateException("Could not locate NPN implementation. See console err for details.");
        }
    }
}
