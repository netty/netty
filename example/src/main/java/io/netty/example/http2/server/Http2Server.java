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

import static io.netty.example.http2.Http2ExampleUtil.parseEndpointConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.example.http2.Http2ExampleUtil.EndpointConfig;
import io.netty.handler.codec.http2.Http2OrHttpChooser.SelectedProtocol;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.util.Arrays;

/**
 * A HTTP/2 Server that responds to requests with a Hello World. Once started, you can test the
 * server with the example client.
 * <p>
 * To server accepts command-line arguments for {@code -port=<port number>} <i>(default: http=8080,
 * https=8443)</i>, and {@code -ssl=<true/false>} <i>(default=false)</i>.
 */
public class Http2Server {

    private final EndpointConfig config;

    public Http2Server(EndpointConfig config) {
        this.config = config;
    }

    public void run() throws Exception {
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);

            // If SSL was selected, configure the SSL context.
            SslContext sslCtx = null;
            if (config.isSsl()) {
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslCtx = SslContext.newServerContext(
                        ssc.certificate(), ssc.privateKey(), null, null,
                        Arrays.asList(
                                SelectedProtocol.HTTP_2.protocolName(),
                                SelectedProtocol.HTTP_1_1.protocolName()),
                        0, 0);
            }

            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new Http2ServerInitializer(sslCtx));

            Channel ch = b.bind(config.port()).sync().channel();
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        EndpointConfig config = parseEndpointConfig(args);
        System.out.println(config);

        System.out.println("HTTP2 server started at port " + config.port() + '.');
        new Http2Server(config).run();
    }
}
