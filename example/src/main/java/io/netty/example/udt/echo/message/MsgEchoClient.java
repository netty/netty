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
package io.netty.example.udt.echo.message;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.example.udt.util.UtilConsoleReporter;
import io.netty.example.udt.util.UtilThreadFactory;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * UDT Message Flow client
 * <p>
 * Sends one message when a connection is open and echoes back any received data
 * to the server. Simply put, the echo client initiates the ping-pong traffic
 * between the echo client and server by sending the first message to the
 * server.
 */
public class MsgEchoClient {

    private static final Logger log = Logger.getLogger(MsgEchoClient.class.getName());

    private final String host;
    private final int port;
    private final int messageSize;

    public MsgEchoClient(final String host, final int port,
            final int messageSize) {
        this.host = host;
        this.port = port;
        this.messageSize = messageSize;
    }

    public void run() throws Exception {
        // Configure the client.
        final ThreadFactory connectFactory = new UtilThreadFactory("connect");
        final NioEventLoopGroup connectGroup = new NioEventLoopGroup(1,
                connectFactory, NioUdtProvider.MESSAGE_PROVIDER);
        try {
            final Bootstrap boot = new Bootstrap();
            boot.group(connectGroup)
                    .channelFactory(NioUdtProvider.MESSAGE_CONNECTOR)
                    .handler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        public void initChannel(final UdtChannel ch)
                                throws Exception {
                            ch.pipeline().addLast(
                                    new LoggingHandler(LogLevel.INFO),
                                    new MsgEchoClientHandler(messageSize));
                        }
                    });
            // Start the client.
            final ChannelFuture f = boot.connect(host, port).sync();
            // Wait until the connection is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down the event loop to terminate all threads.
            connectGroup.shutdownGracefully();
        }
    }

    public static void main(final String[] args) throws Exception {
        log.info("init");

        // client is reporting metrics
        UtilConsoleReporter.enable(3, TimeUnit.SECONDS);

        final String host = "localhost";

        final int port = 1234;
        final int messageSize = 64 * 1024;

        new MsgEchoClient(host, port, messageSize).run();

        log.info("done");
    }

}
