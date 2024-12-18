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
package io.netty5.example.portunification;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;

/**
 * Serves two protocols (HTTP and Factorial) using only one port, enabling
 * either SSL or GZIP dynamically on demand.
 * <p>
 * Because SSL and GZIP are enabled on demand, 5 combinations per protocol
 * are possible: none, SSL only, GZIP only, SSL + GZIP, and GZIP + SSL.
 */
public final class PortUnificationServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));

    public static void main(String[] args) throws Exception {
        // Configure SSL context
        X509Bundle cert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        final SslContext sslCtx = SslContextBuilder.forServer(cert.toKeyManagerFactory())
            .build();

        EventLoopGroup bossGroup = new MultithreadEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new PortUnificationServerHandler(sslCtx));
                }
            });

            // Bind and start to accept incoming connections.
            b.bind(PORT).asStage().get().closeFuture().asStage().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
