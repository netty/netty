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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUringBufferRingConfig;
import io.netty.channel.uring.IoUringChannelOption;
import io.netty.channel.uring.IoUringFixedBufferRingAllocator;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringIoHandlerConfig;
import io.netty.channel.uring.IoUringServerSocketChannel;

public class EchoServer {
    private static final int PORT = Integer.parseInt(System.getProperty("port", "8081"));

    public static void main(String []args) {
        boolean useNio = false;
        IoHandlerFactory ioHandlerFactory;
        Class<? extends ServerChannel> serverChannelClass;
        if (useNio) {
            ioHandlerFactory = NioIoHandler.newFactory();
            serverChannelClass = NioServerSocketChannel.class;
        } else {
            ioHandlerFactory = IoUringIoHandler.newFactory(
                    new IoUringIoHandlerConfig()
                            .setBufferRingConfig(
                                    new IoUringBufferRingConfig((short) 0, (short) 64,
                                            false, new IoUringFixedBufferRingAllocator(64 * 1024))));
            serverChannelClass = IoUringServerSocketChannel.class;
        }
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(IoUringChannelOption.IO_URING_BUFFER_GROUP_ID, (short) 0)
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

    private static final class EchoServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ctx.write(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Close the connection when an exception is raised.
            ctx.close();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            // Ensure we are not writing to fast by stop reading if we can not flush out data fast enough.
            if (ctx.channel().isWritable()) {
                ctx.channel().config().setAutoRead(true);
            } else {
                ctx.flush();
                if (!ctx.channel().isWritable()) {
                    ctx.channel().config().setAutoRead(false);
                }
            }
        }
    }
}
