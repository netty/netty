/*
 * Copyright 2012 The Netty Project
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
package io.netty5.example.proxy;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;

public final class HexDumpProxy {

    static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8443"));
    static final String REMOTE_HOST = System.getProperty("remoteHost", "www.google.com");
    static final int REMOTE_PORT = Integer.parseInt(System.getProperty("remotePort", "443"));

    public static void main(String[] args) throws Exception {
        System.err.println("Proxying *:" + LOCAL_PORT + " to " + REMOTE_HOST + ':' + REMOTE_PORT + " ...");

        // Configure the bootstrap.
        EventLoopGroup bossGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        EventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new HexDumpProxyInitializer(REMOTE_HOST, REMOTE_PORT))
             .childOption(ChannelOption.AUTO_READ, false)
             .bind(LOCAL_PORT).get().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
