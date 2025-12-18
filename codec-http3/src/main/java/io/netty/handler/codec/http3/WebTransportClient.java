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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.util.concurrent.TimeUnit;

/**
 * WebTransport客户端，用于连接WebTransport服务器。
 */
public class WebTransportClient {

    private final String host;
    private final int port;
    private final SslContext sslContext;
    private final WebTransportObserver observer;

    private Channel channel;
    private WebTransportSession session;

    public WebTransportClient(String host, int port) throws Exception {
        this(host, port, null);
    }

    public WebTransportClient(String host, int port, WebTransportObserver observer) throws Exception {
        this.host = host;
        this.port = port;
        this.observer = observer != null ? observer : NoopWebTransportObserver.INSTANCE;
        this.sslContext = createSslContext();
    }

    /**
     * 连接到WebTransport服务器。
     * @return 连接成功的ChannelFuture
     */
    public ChannelFuture connect() {
        System.out.println("WebTransportClient.connect() called");
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .option(ChannelOption.TCP_NODELAY, true)
             .handler(new WebTransportChannelInitializer(sslContext, observer, false));

            System.out.println("Connecting to WebTransport server at " + host + ":" + port);
            ChannelFuture future = b.connect(host, port);
            future.addListener(f -> {
                if (f.isSuccess()) {
                    System.out.println("WebTransport client connected successfully");
                    channel = future.channel();
                    // 发送 HTTP CONNECT 请求以建立 WebTransport 会话
                    sendConnectRequest(channel);
                    // 立即尝试获取会话
                    session = WebTransportSessionProvider.getSession(channel);
                    if (session != null) {
                        System.out.println("WebTransport session established successfully");
                    } else {
                        System.err.println("Failed to establish WebTransport session immediately");
                    }
                } else {
                    System.err.println("WebTransport client connection failed: " + f.cause().getMessage());
                    group.shutdownGracefully();
                }
            });
            return future;
        } catch (Exception e) {
            group.shutdownGracefully();
            throw new RuntimeException(e);
        }
    }

    /**
     * 发送 HTTP CONNECT 请求以建立 WebTransport 会话。
     * @param channel 客户端通道
     */
    private void sendConnectRequest(Channel channel) {
        System.out.println("Sending HTTP CONNECT request...");
        // 创建 HTTP CONNECT 请求
        io.netty.handler.codec.http.DefaultFullHttpRequest request = 
                new io.netty.handler.codec.http.DefaultFullHttpRequest(
                        io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                        io.netty.handler.codec.http.HttpMethod.CONNECT,
                        "/webtransport");
        // 添加 WebTransport 协议所需的头部
        request.headers().add("sec-web-transport-version", "webtransport-http3-draft02");
        // 添加必要的 HTTP 头部
        request.headers().add("Host", host + ":" + port);
        request.headers().add("User-Agent", "Netty WebTransport Client");
        // 发送请求
        channel.writeAndFlush(request);
        System.out.println("HTTP CONNECT request sent");
        System.out.println("Request method: " + request.method());
        System.out.println("Request URI: " + request.uri());
        System.out.println("Request headers: " + request.headers());
    }

    /**
     * 获取当前WebTransport会话。
     * @return WebTransportSession实例
     */
    public WebTransportSession getSession() {
        return session;
    }

    /**
     * 关闭客户端连接。
     */
    public void close() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
    }

    private SslContext createSslContext() throws Exception {
        return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }
}