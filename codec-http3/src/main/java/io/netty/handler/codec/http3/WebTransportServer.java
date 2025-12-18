/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * WebTransport 服务器，用于启动 WebTransport 服务。
 */
public class WebTransportServer {

    private final int port;
    private final SslContext sslContext;
    private final WebTransportObserver observer;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public WebTransportServer(int port) throws Exception {
        this(port, null);
    }

    public WebTransportServer(int port, WebTransportObserver observer) throws Exception {
        this(port, createDefaultSslContext(), observer);
    }

    public WebTransportServer(int port, SslContext sslContext, WebTransportObserver observer) {
        this.port = port;
        this.sslContext = sslContext;
        this.observer = observer != null ? observer : NoopWebTransportObserver.INSTANCE;
    }

    private static SslContext createDefaultSslContext() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
    }

    public ChannelFuture start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 100)
             .childHandler(new WebTransportChannelInitializer(sslContext, observer));
            
            // 绑定端口并启动服务器
            ChannelFuture f = b.bind(port).sync();
            System.out.println("WebTransport server started on port " + port);
            return f;
        } catch (InterruptedException e) {
            shutdown();
            throw e;
        }
    }

    public void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8443;
        WebTransportServer server = new WebTransportServer(port);
        server.start().channel().closeFuture().sync();
        server.shutdown();
    }
}