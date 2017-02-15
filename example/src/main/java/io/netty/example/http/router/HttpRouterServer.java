/*
 * Copyright 2015 The Netty Project
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
package io.netty.example.http.router;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.router.Router;

public class HttpRouterServer {
    public static final int PORT = 8000;

    public static void main(String[] args) throws Exception {
        // This is an example router, it will be used at HttpRouterServerHandler.
        //
        // For simplicity of this example, route targets are just simple strings.
        // But you can make them classes, and at HttpRouterServerHandler once you
        // get a target class, you can create an instance of it and dispatch the
        // request to the instance etc.
        Router<String> router = new Router<String>()
            .GET("/",             "Index page")
            .GET("/articles/:id", "Article show page")
            .notFound("404 Not Found");
        System.out.println(router);

        NioEventLoopGroup bossGroup   = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .childOption(ChannelOption.TCP_NODELAY, java.lang.Boolean.TRUE)
             .childOption(ChannelOption.SO_KEEPALIVE, java.lang.Boolean.TRUE)
             .channel(NioServerSocketChannel.class)
             .childHandler(new HttpRouterServerInitializer(router));

            Channel ch = b.bind(PORT).sync().channel();
            System.out.println("Server started: http://127.0.0.1:" + PORT + '/');

            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
