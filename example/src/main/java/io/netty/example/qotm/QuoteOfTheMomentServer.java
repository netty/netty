/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.qotm;

import io.netty.channel.Channel;
import io.netty.channel.ChannelBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.SelectorEventLoop;

import java.net.InetSocketAddress;

/**
 * A UDP server that responds to the QOTM (quote of the moment) request to a
 * {@link QuoteOfTheMomentClient}.
 *
 * Inspired by <a href="http://java.sun.com/docs/books/tutorial/networking/datagrams/clientServer.html">the official Java tutorial</a>.
 */
public class QuoteOfTheMomentServer {

    private final int port;

    public QuoteOfTheMomentServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        ChannelBootstrap b = new ChannelBootstrap();
        try {
            b.eventLoop(new SelectorEventLoop())
             .channel(new NioDatagramChannel())
             .localAddress(new InetSocketAddress(port))
             .option(ChannelOption.SO_BROADCAST, true)
             .initializer(new ChannelInitializer() {
                @Override
                public void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new QuoteOfTheMomentServerHandler());
                }
             });

            b.bind().sync().channel().closeFuture().await();
        } finally {
            b.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8080;
        }
        new QuoteOfTheMomentServer(port).run();
    }
}
