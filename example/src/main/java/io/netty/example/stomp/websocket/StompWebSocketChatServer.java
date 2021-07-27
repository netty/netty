/*
 * Copyright 2020 The Netty Project
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
package io.netty.example.stomp.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class StompWebSocketChatServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));

    public void start(final int port) throws Exception {
        MultithreadEventLoopGroup boosGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        MultithreadEventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(boosGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new StompWebSocketChatServerInitializer("/chat"));
            bootstrap.bind(port).addListener(new GenericFutureListener<Future<Object>>() {
                @Override
                public void operationComplete(Future<Object> future) {
                    if (future.isSuccess()) {
                        System.out.println("Open your web browser and navigate to http://127.0.0.1:" + PORT + '/');
                    } else {
                        System.out.println("Cannot start server, follows exception " + future.cause());
                    }
                }
            }).get().closeFuture().sync();
        } finally {
            boosGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new StompWebSocketChatServer().start(PORT);
    }
}
