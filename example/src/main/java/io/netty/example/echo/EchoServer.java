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
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // Configure the server.
        //创建两个EventLoopGroup对象
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);//创建boos线程组，用于服务端接受客户端的连接
        EventLoopGroup workerGroup = new NioEventLoopGroup();//创建worker线程组，用于进行SocketChannel的数据读写，处理业务逻辑
        //创建Handler
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            //创建ServerBootstrap对象
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)//设置EventLoopGroup
             .channel(NioServerSocketChannel.class)//设置要被实例化的NioServerSocketChannel类
             .option(ChannelOption.SO_BACKLOG, 100) //设置NioServerSocketChannel的可设置项
             .handler(new LoggingHandler(LogLevel.INFO))//设置NioServerSocketChannel的处理器
             .childHandler(new ChannelInitializer<SocketChannel>() {//设置处理连入的Client的SocketChannel的处理器
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(serverHandler);
                 }
             });

            // Start the server.
            //绑定端口，并同步等待成功，即启动服务端
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            //监听服务端关闭，并阻塞等待
            //这里并不是关闭服务器，而是“监听”服务端关闭
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            //优雅关闭两个EventLoopGroup对象
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
