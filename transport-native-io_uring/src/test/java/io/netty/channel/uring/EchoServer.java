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
package io.netty.channel.uring;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public final class EchoServer {

    private EchoServer() { }

    private static final int PORT = Integer.parseInt(System.getProperty("port", "8088"));

    public static void main(String []args) {
        boolean nio = false;
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, nio ? NioIoHandler.newFactory() :
                IoUringIoHandler.newFactory());
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    //.childOption(IoUringChannelOption.POLLIN_FIRST, false)
                    .childOption(IoUringChannelOption.IOSQE_ASYNC, true)
                    .channel(nio ? NioServerSocketChannel.class : IoUringServerSocketChannel.class)
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

    public static final class EchoServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            //System.err.println("" + ((ByteBuf) msg).readableBytes());
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
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
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
