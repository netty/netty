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
package io.netty.example.objectecho;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.example.echo.EchoClient;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

/**
 * Modification of {@link EchoClient} which utilizes Java object serialization.
 */
public class ObjectEchoClient {

    private final String host;
    private final int port;
    private final int firstMessageSize;

    public ObjectEchoClient(String host, int port, int firstMessageSize) {
        this.host = host;
        this.port = port;
        this.firstMessageSize = firstMessageSize;
    }

    public void run() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(
                            new ObjectEncoder(),
                            new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                            new ObjectEchoClientHandler(firstMessageSize));
                }
             });

            // Start the connection attempt.
            b.connect(host, port).sync().channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        // Print usage if no argument is specified.
        if (args.length < 2 || args.length > 3) {
            System.err.println(
                    "Usage: " + ObjectEchoClient.class.getSimpleName() +
                    " <host> <port> [<first message size>]");
            return;
        }

        // Parse options.
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final int firstMessageSize;

        if (args.length == 3) {
            firstMessageSize = Integer.parseInt(args[2]);
        } else {
            firstMessageSize = 256;
        }

        new ObjectEchoClient(host, port, firstMessageSize).run();
    }
}
