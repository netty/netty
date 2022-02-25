/*
 * Copyright 2019 The Netty Project
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
package io.netty5.testsuite.svm;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public final class HttpNativeServer {

    /**
     * Main entry point (not instantiable)
     */
    private HttpNativeServer() {
    }

    public static void main(String[] args) throws Exception {
        // Configure the server.
        EventLoopGroup bossGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        EventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioHandler.newFactory());
        // Control status.
        boolean serverStartSucess = false;
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new HttpNativeServerInitializer());

            Channel channel = b.bind(0).get();
            System.err.println("Server started, will shutdown now.");
            channel.close().sync();
            serverStartSucess = true;
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        // return the right system exit code to signal success
        System.exit(serverStartSucess ? 0 : 1);
    }
}
