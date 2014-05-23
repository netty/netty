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
package io.netty.example.worldclock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Sends a list of continent/city pairs to a {@link WorldClockServer} to
 * get the local times of the specified cities.
 */
public final class WorldClockClient {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "8463"));
    static final List<String> CITIES = Arrays.asList(System.getProperty(
            "cities", "Asia/Seoul,Europe/Berlin,America/Los_Angeles").split(","));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            sslCtx = SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE);
        } else {
            sslCtx = null;
        }

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new WorldClockClientInitializer(sslCtx));

            // Make a new connection.
            Channel ch = b.connect(HOST, PORT).sync().channel();

            // Get the handler instance to initiate the request.
            WorldClockClientHandler handler = ch.pipeline().get(WorldClockClientHandler.class);

            // Request and get the response.
            List<String> response = handler.getLocalTimes(CITIES);

            // Close the connection.
            ch.close();

            // Print the response at last but not least.
            for (int i = 0; i < CITIES.size(); i ++) {
                System.out.format("%28s: %s%n", CITIES.get(i), response.get(i));
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}
