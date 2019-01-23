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
package io.netty.example.factorial;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * Sends a sequence of integers to a {@link FactorialServer} to calculate
 * the factorial of the specified integer.
 */
public final class FactorialClient {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "8322"));
    static final int COUNT = Integer.parseInt(System.getProperty("count", "1000"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } else {
            sslCtx = null;
        }

        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new FactorialClientInitializer(sslCtx));

            // Make a new connection.
            ChannelFuture f = b.connect(HOST, PORT).sync();

            // Get the handler instance to retrieve the answer.
            FactorialClientHandler handler =
                (FactorialClientHandler) f.channel().pipeline().last();

            // Print out the answer.
            System.err.format("Factorial of %,d is: %,d", COUNT, handler.getFactorial());
        } finally {
            group.shutdownGracefully();
        }
    }
}
