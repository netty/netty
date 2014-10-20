/*
 * Copyright 2009 Red Hat, Inc.
 * 
 * Red Hat licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http.rest;

import java.io.IOException;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.rest.RestConfiguration;
import io.netty.handler.codec.rest.RestHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * Simple REST Server example
 */
public class RestServerExample {
    static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));
    static final int PORTSIGN = Integer.parseInt(System.getProperty("portsign", "8081"));

    /**
     * @param args
     * @throws InterruptedException 
     * @throws IOException 
     */
    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup groupBoss = new NioEventLoopGroup();
        EventLoopGroup groupWorker = new NioEventLoopGroup();
        try {
            final RestConfiguration configuration = RestExampleCommon.getTestConfigurationWithSignature();
            final RestConfiguration configurationNoSign = RestExampleCommon.getTestConfigurationNoSignature();
            // HttpDataFactory not used so set to In Memory only
            RestHandler.initialize(-1, null);
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.group(groupBoss, groupWorker);
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            bootstrap.option(ChannelOption.SO_REUSEADDR, true);
            bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
            bootstrap.childOption(ChannelOption.SO_REUSEADDR, true);
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);
            bootstrap.childOption(ChannelOption.SO_RCVBUF, 1048576);
            bootstrap.childOption(ChannelOption.SO_SNDBUF, 1048576);
            bootstrap.handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(65536));
                    p.addLast(new ChunkedWriteHandler());
                    p.addLast(new RestHandler(configurationNoSign));
                }
            });
            // Bind and start to accept incoming connections for Unsigned version.
            ChannelFuture f = bootstrap.bind(PORT).sync();

            ServerBootstrap bootstrapSign = new ServerBootstrap();
            bootstrapSign.channel(NioServerSocketChannel.class);
            bootstrapSign.group(groupBoss, groupWorker);
            bootstrapSign.option(ChannelOption.TCP_NODELAY, true);
            bootstrapSign.option(ChannelOption.SO_REUSEADDR, true);
            bootstrapSign.childOption(ChannelOption.TCP_NODELAY, true);
            bootstrapSign.childOption(ChannelOption.SO_REUSEADDR, true);
            bootstrapSign.childOption(ChannelOption.SO_KEEPALIVE, true);
            bootstrapSign.childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);
            bootstrapSign.childOption(ChannelOption.SO_RCVBUF, 1048576);
            bootstrapSign.childOption(ChannelOption.SO_SNDBUF, 1048576);
            bootstrapSign.handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(65536));
                    p.addLast(new ChunkedWriteHandler());
                    p.addLast(new RestHandler(configuration));
                }
            });
            // Bind and start to accept incoming connections for Signed version.
            ChannelFuture fsign = bootstrapSign.bind(PORTSIGN).sync();

            System.out.println("Rest server initialized on ports: Unisgned=" + PORT + " Signed=" + PORTSIGN);
            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync();
            fsign.channel().closeFuture().sync();
        } finally {
            groupBoss.shutdownGracefully();
            groupWorker.shutdownGracefully();
        }
    }

}
