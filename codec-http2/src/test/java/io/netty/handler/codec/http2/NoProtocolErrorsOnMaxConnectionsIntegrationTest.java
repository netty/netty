/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.cert.CertificateException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.Test;


public class NoProtocolErrorsOnMaxConnectionsIntegrationTest {
    private static final int NUM_CONNECTIONS = 3;
    private static final int MAX_CONNECTIONS = 2;
    private static final String ADDRESS_NAME = "NoProtocolErrorsOnMaxConnections";
    static int protocolErrors;

    private static EventLoopGroup serverGroup;
    private static EventLoopGroup clientGroup;
    private static ChannelFuture serverFuture;

    @Test
    public void testServerClientCommunication() throws Exception {
        serverGroup = new NioEventLoopGroup();
        clientGroup = new NioEventLoopGroup();

        serverFuture = startServer();

        // Wait for the server to start
        serverFuture.sync();

        final CountDownLatch latch = new CountDownLatch(NUM_CONNECTIONS);

        ExecutorService executorService = Executors.newFixedThreadPool(NUM_CONNECTIONS);

        // Start multiple clients all on the same stream
        for (int i = 0; i < NUM_CONNECTIONS; i++) {
            executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Bootstrap b = new Bootstrap();
                            b.group(clientGroup)
                            .channel(LocalChannel.class)
                            // .channel(LocalServerChannel.class)
                            .handler(new ChannelInitializer<LocalChannel>() {
                                @Override
                                protected void initChannel(LocalChannel ch) {
                                    ch.pipeline().addLast(new ClientHandler(latch));
                                }
                            });

                            b.connect(serverFuture.channel().localAddress());

                            ChannelFuture clientFuture = b.connect(new LocalAddress(ADDRESS_NAME)).sync();

                            final FullHttpRequest request = new DefaultFullHttpRequest(
                                HttpVersion.HTTP_1_0,
                                HttpMethod.GET,
                                "/"
                            );
                            request.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3);
                            request.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");
                            request.headers().set(HttpHeaderNames.HOST, "server");
                            request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
                            request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
                            clientFuture.channel().writeAndFlush(request).sync();
                            clientFuture.channel().closeFuture().sync();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
        }

        latch.await(); // Wait for all clients to complete

        assertEquals(0, protocolErrors);
    }

    public static final class ServerUtil {
        public static SslContext buildSslContext() throws CertificateException, SSLException {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            return SslContextBuilder
                    .forServer(ssc.certificate(), ssc.privateKey())
                    .build();
        }
    }

    private ChannelFuture startServer() throws Exception {
        final SslContext sslCtx = ServerUtil.buildSslContext();

        ServerBootstrap b = new ServerBootstrap();

        b.group(serverGroup, clientGroup)
                .channel(LocalServerChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel ch) {
                        ch.pipeline()
                            .addLast(new StringDecoder())
                            .addLast(sslCtx.newHandler(ch.alloc()))
                            .addLast(new Http2OnlyHandler("None"))
                            .addLast(new ServerHandler())
                            .addLast("http-aggregator", new HttpObjectAggregator(1024));
                    }
                });

        return b.bind(new LocalAddress(ADDRESS_NAME)).sync();
    }

    private static class ServerHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            // Process the received message from the client
            ctx.writeAndFlush("Hello, client!");
        }
    }

    public class Http2OnlyHandler extends ApplicationProtocolNegotiationHandler {
        protected Http2OnlyHandler(String fallbackProtocol) {
            super(fallbackProtocol);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
            String protocol = sslHandler.applicationProtocol();
            configurePipeline(ctx, protocol != null ? protocol : ApplicationProtocolNames.HTTP_2);
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
                InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(connection)
                    .propagateSettings(true).validateHttpHeaders(false)
                    .maxContentLength(1024).build();
                HttpToHttp2ConnectionHandler connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                    .initialSettings(new Http2Settings().maxConcurrentStreams(MAX_CONNECTIONS)).frameListener(listener)
                    .connection(connection).build();

                ctx.pipeline().addLast(connectionHandler);
                return;
            }

            protocolErrors++;
            throw new IllegalStateException("Unsupported protocol: " + protocol);
        }
       }

    private static class ClientHandler extends SimpleChannelInboundHandler<String> {
        final CountDownLatch latch;

        ClientHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            // No action needed for client
            this.latch.countDown();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // Send a message to the server upon channel activation
            ctx.write("Hello, server!");

            ctx.write("Hello, server!");
            ctx.writeAndFlush("Hello, server!");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            latch.countDown(); // Release the latch when the client connection is closed
        }
    }
}
