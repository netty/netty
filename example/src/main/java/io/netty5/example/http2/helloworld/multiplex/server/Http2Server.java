/*
 * Copyright 2016 The Netty Project
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

package io.netty5.example.http2.helloworld.multiplex.server;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.codec.http2.Http2SecurityUtil;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.ApplicationProtocolConfig;
import io.netty5.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty5.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty5.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty5.handler.ssl.ApplicationProtocolNames;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.SupportedCipherSuiteFilter;
import io.netty5.handler.ssl.util.SelfSignedCertificate;

import static io.netty5.handler.ssl.SslProvider.JDK;
import static io.netty5.handler.ssl.SslProvider.OPENSSL;
import static io.netty5.handler.ssl.SslProvider.isAlpnSupported;

/**
 * An HTTP/2 Server that responds to requests with a Hello World. Once started, you can test the
 * server with the example client.
 *
 * <p>This example is making use of the "multiplexing" http2 API, where streams are mapped to child
 * Channels. This API is very experimental and incomplete.
 */
public final class Http2Server {

    static final boolean SSL = System.getProperty("ssl") != null;

    static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SslProvider provider = isAlpnSupported(OPENSSL) ? OPENSSL : JDK;
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(provider)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                    Protocol.ALPN,
                    // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                    SelectorFailureBehavior.NO_ADVERTISE,
                    // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                    SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1))
                .build();
        } else {
            sslCtx = null;
        }
        // Configure the server.
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(group)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new Http2ServerInitializer(sslCtx));

            Channel ch = b.bind(PORT).get();

            System.err.println("Open your HTTP/2-enabled web browser and navigate to " +
                    (SSL? "https" : "http") + "://127.0.0.1:" + PORT + '/');

            ch.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
