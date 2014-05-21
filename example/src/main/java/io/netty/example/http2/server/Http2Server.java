/*
 * Copyright 2014 The Netty Project
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

package io.netty.example.http2.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2OrHttpChooser.SelectedProtocol;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.util.Arrays;

/**
 * A HTTP/2 Server that responds to requests with a Hello World.
 * <p>
 * Once started, you can test the server with the example client.
 */
public class Http2Server {

    private final SslContext sslCtx;
    private final int port;

    public Http2Server(SslContext sslCtx, int port) {
        this.sslCtx = sslCtx;
        this.port = port;
    }

    public void run() throws Exception {
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new Http2ServerInitializer(sslCtx));

            Channel ch = b.bind(port).sync().channel();
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8443;
        }

        System.out.println("HTTP2 server started at port " + port + '.');

        // Configure SSL context.
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext sslCtx = SslContext.newServerContext(
                ssc.certificate(), ssc.privateKey(), null, null,
                Arrays.asList(
                        SelectedProtocol.HTTP_2.protocolName(),
                        SelectedProtocol.HTTP_1_1.protocolName()),
                0, 0);

        new Http2Server(sslCtx, port).run();
    }
}
