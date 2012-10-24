/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.fileregion;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelStateHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.fileregion.DefaultFileRegion;
import io.netty.handler.fileregion.FileRegionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;

public class FileServer {

    private final int port;
    private final File file;

    public FileServer(int port, File file) {
        this.port = port;
        this.file = file;
    }

    public void run() throws Exception {
        // Configure the server.
        ServerBootstrap b = new ServerBootstrap();
        try {
            b.group(new NioEventLoopGroup(), new NioEventLoopGroup())
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 100)
             .localAddress(new InetSocketAddress(port))
             .childOption(ChannelOption.TCP_NODELAY, true)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ch.pipeline().addLast(
                             new FileRegionHandler(), new FileHandler(), new ChannelInboundByteHandlerAdapter() {
                         @Override
                         public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                             in.clear();
                         }
                     });
                 }
             });

            // Start the server.
            ChannelFuture f = b.bind().sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            b.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            int port = Integer.parseInt(args[0]);
            File file = new File(args[1]);
            new FileServer(port, file).run();

        } else {
            System.err.println("Usage: " + FileServer.class.getName() + " <port> <file>");
            System.exit(1);
        }

    }

    private final class FileHandler extends ChannelStateHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.channel().write(new DefaultFileRegion(new FileInputStream(file).getChannel(),
                    0, file.length())).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
