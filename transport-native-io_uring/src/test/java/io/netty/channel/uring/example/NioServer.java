/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NioServer {
    private static final int PORT = Integer.parseInt(System.getProperty("port", "9081"));

    public static void main(String []args) {
        System.setProperty("io.netty.native.deleteLibAfterLoading", "false");
        IoHandlerFactory ioHandlerFactory = NioIoHandler.newFactory();
        Class<? extends ServerChannel> serverChannelClass = NioServerSocketChannel.class;
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);
        final ServerHandler serverHandler = new ServerHandler(true);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.RECVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(64 * 1024))
                    .channel(serverChannelClass)
                    .childHandler(serverHandler);

            // Start the server.
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // Shut down all event loops to terminate all threads.
            group.shutdownGracefully();
        }
    }
}
