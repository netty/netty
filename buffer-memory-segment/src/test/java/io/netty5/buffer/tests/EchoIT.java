/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.tests;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EchoIT {
    // In this test we have a server and a client, where the server echos back anything it receives,
    // and our client sends a single message to the server, and then verifies that it receives it back.

    @Test
    void echoServerMustReplyWithSameData() throws Exception {
        EventLoopGroup bossGroup = new MultithreadEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap server = new ServerBootstrap();
            server.group(bossGroup, workerGroup)
                  .channel(NioServerSocketChannel.class)
                  .childOption(ChannelOption.BUFFER_ALLOCATOR, DefaultBufferAllocators.preferredAllocator())
                  .option(ChannelOption.SO_BACKLOG, 100)
                  .handler(new LoggingHandler(LogLevel.INFO))
                  .childHandler(new ChannelInitializer<SocketChannel>() {
                      @Override
                      public void initChannel(SocketChannel ch) throws Exception {
                          ChannelPipeline p = ch.pipeline();
                          p.addLast(new LoggingHandler(LogLevel.INFO));
                          p.addLast(serverHandler);
                      }
                  });

            // Start the server.
            var bind = server.bind("localhost", 0).asStage().sync().getNow();
            InetSocketAddress serverAddress = (InetSocketAddress) bind.localAddress();

            // Configure the client.
            EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
            try {
                Bootstrap b = new Bootstrap();
                b.group(group)
                 .channel(NioSocketChannel.class)
                 .option(ChannelOption.TCP_NODELAY, true)
                 .handler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     public void initChannel(SocketChannel ch) throws Exception {
                         ChannelPipeline p = ch.pipeline();
                         p.addLast(new LoggingHandler(LogLevel.INFO));
                         p.addLast(new EchoClientHandler());
                     }
                 });

                // Start the client.
                var channel = b.connect(serverAddress).asStage().sync().getNow();

                // Wait until the connection is closed.
                channel.closeFuture().asStage().sync();
            } finally {
                // Shut down the event loop to terminate all threads.
                group.shutdownGracefully();
            }

            // Shut down the server.
            bind.close().asStage().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    static class EchoServerHandler implements ChannelHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Buffer buf = (Buffer) msg;
            ctx.write(buf);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Close the connection when an exception is raised.
            cause.printStackTrace();
            ctx.close();
        }
    }

    static class EchoClientHandler implements ChannelHandler {
        private static final int SIZE = 256;
        private final Buffer firstMessage;

        /**
         * Creates a client-side handler.
         */
        EchoClientHandler() {
            firstMessage = BufferAllocator.onHeapUnpooled().allocate(SIZE);
            for (int i = 0; i < SIZE; i++) {
                firstMessage.writeByte((byte) i);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(firstMessage);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Buffer buf = (Buffer) msg;
            assertEquals(SIZE, buf.readableBytes());
            for (int i = 0; i < SIZE; i++) {
                assertEquals((byte) i, buf.readByte());
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.close();
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Close the connection when an exception is raised.
            ctx.close();
            throw new RuntimeException(cause);
        }
    }
}
