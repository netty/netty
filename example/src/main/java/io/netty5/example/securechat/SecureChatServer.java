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
package io.netty5.example.securechat;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.example.telnet.TelnetServer;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;

/**
 * Simple SSL chat server modified from {@link TelnetServer}.
 */
public final class SecureChatServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8992"));

    public static void main(String[] args) throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        SslContext sslCtx = SslContextBuilder.forServer(cert.toKeyManagerFactory())
            .build();

        EventLoopGroup bossGroup = new MultithreadEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new SecureChatServerInitializer(sslCtx));

            b.bind(PORT).asStage().get().closeFuture().asStage().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
