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
package io.netty5.example.discard;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.example.util.ServerUtil;
import io.netty5.handler.ssl.SslContext;

/**
 * Keeps sending random data to the specified address.
 */
public final class DiscardClient {

    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "8009"));
    static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx = ServerUtil.buildSslContext();

        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.bufferAllocator(), HOST, PORT));
                     }
                     p.addLast(new DiscardClientHandler());
                 }
             });

            // Make the connection attempt.
            Channel channel = b.connect(HOST, PORT).asStage().get();

            // Wait until the connection is closed.
            channel.closeFuture().asStage().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
