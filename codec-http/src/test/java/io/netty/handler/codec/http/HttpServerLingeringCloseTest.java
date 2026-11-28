/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleUserEventChannelHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HttpServerLingeringCloseTest {
    private final MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final HttpObjectAggregator aggregator = new HttpObjectAggregator(4, true);
    private final CompletableFuture<Void> outputShutdown = new CompletableFuture<Void>();

    @AfterEach
    void tearDown() throws InterruptedException {
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void tcpPeerCanFinishSendingAfterReading413(boolean autoRead) throws Exception {
        aggregator.setCloseTimeoutMillis(60000);
        try (Socket peer = new Socket()) {
            Channel child = connect(peer, autoRead, new FlushConsolidationHandler());
            sendRequest(peer, autoRead ? "Connection: close\r\n" : "");
            assertThat(readResponse(peer, 413).read()).isEqualTo(-1);
            assertThat(child.isActive()).isTrue();
            peer.getOutputStream().write(new byte[1024]);
            peer.shutdownOutput();
            // The peer's FIN must complete the close well before the 60-second deadline.
            child.closeFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @CsvSource({ "100-continue, 413", "unsupported, 417" })
    void rejectedExpectationClosesByDeadlineWhilePeerOutputIsOpen(String expectation, int status) throws Exception {
        aggregator.setCloseTimeoutMillis(200);
        try (Socket peer = new Socket()) {
            Channel child = connect(peer, true, new FlushConsolidationHandler());
            sendRequest(peer, "Expect: " + expectation + "\r\n");
            assertThat(readResponse(peer, status).read()).isEqualTo(-1);
            child.closeFuture().get(3, TimeUnit.SECONDS);
            assertThat(outputShutdown).isDone();
        }
    }

    @Test
    void zeroTimeoutClosesAfterResponseWhilePeerOutputIsOpen() throws Exception {
        aggregator.setCloseTimeoutMillis(0);
        try (Socket peer = new Socket()) {
            Channel child = connect(peer, true, new FlushConsolidationHandler());
            sendRequest(peer, "Connection: close\r\n");
            assertThat(readResponse(peer, 413).read()).isEqualTo(-1);
            child.closeFuture().get(3, TimeUnit.SECONDS);
            assertThat(outputShutdown).isNotDone();
        }
    }

    @Test
    void deadlineClosesSocketWhileRejectionWriteIsPending() throws Exception {
        aggregator.setCloseTimeoutMillis(200);
        CompletableFuture<Void> flushAttempted = new CompletableFuture<Void>();
        try (Socket peer = new Socket()) {
            Channel child = connect(peer, true, new ChannelOutboundHandlerAdapter() {
                @Override
                public void flush(ChannelHandlerContext ctx) {
                    // Leave the rejection queued in the transport until the deadline closes the channel.
                    flushAttempted.complete(null);
                }
            });
            sendRequest(peer, "Connection: close\r\n");
            flushAttempted.get(10, TimeUnit.SECONDS);
            child.closeFuture().get(3, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 10000 })
    void tlsPeerReceives413AndOrderlyShutdown(long closeNotifyReadTimeoutMillis) throws Exception {
        X509Bundle cert = new CertificateBuilder().subject("CN=localhost").setIsCertificateAuthority(true)
                .buildSelfSigned();
        SslHandler ssl = SslContextBuilder.forServer(cert.toKeyManagerFactory()).sslProvider(SslProvider.JDK)
                .build().newHandler(ByteBufAllocator.DEFAULT);
        ssl.setCloseNotifyReadTimeoutMillis(closeNotifyReadTimeoutMillis);
        SSLContext clientTls = SSLContext.getInstance("TLS");
        clientTls.init(null, cert.toTrustManagerFactory().getTrustManagers(), null);
        Channel child;
        try (Socket peer = clientTls.getSocketFactory().createSocket()) {
            child = connect(peer, true, ssl);
            sendRequest(peer, "Connection: close\r\n");
            BufferedReader response = readResponse(peer, 413);
            // Send another TLS application-data record before reading close_notify.
            peer.getOutputStream().write(new byte[1024]);
            if (closeNotifyReadTimeoutMillis == 0) {
                outputShutdown.get(10, TimeUnit.SECONDS);
            }
            assertThat(response.read()).isEqualTo(-1);
        }
        ssl.sslCloseFuture().get(10, TimeUnit.SECONDS);
        child.closeFuture().get(3, TimeUnit.SECONDS);
    }

    private Channel connect(Socket peer, boolean autoRead, ChannelHandler... handlers) throws Exception {
        CompletableFuture<Channel> accepted = new CompletableFuture<Channel>();
        Channel server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.AUTO_READ, autoRead).childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(handlers).addLast(new HttpServerCodec(), aggregator,
                                new SimpleUserEventChannelHandler<ChannelOutputShutdownEvent>() {
                                    @Override
                                    protected void eventReceived(ChannelHandlerContext ctx,
                                                                 ChannelOutputShutdownEvent evt) {
                                        outputShutdown.complete(null);
                                    }
                                });
                        accepted.complete(ch);
                    }
                }).bind("127.0.0.1", 0).sync().channel();
        peer.connect(server.localAddress());
        peer.setSoTimeout(10000);
        Channel child = accepted.get(10, TimeUnit.SECONDS);
        if (!autoRead) {
            child.read();
        }
        return child;
    }

    private static void sendRequest(Socket peer, String headers) throws IOException {
        String request = "PUT / HTTP/1.1\r\nHost: localhost\r\n" + headers + "Content-Length: 65536\r\n\r\nabcde";
        peer.getOutputStream().write(request.getBytes(CharsetUtil.US_ASCII));
    }

    private static BufferedReader readResponse(Socket peer, int status) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(peer.getInputStream(), CharsetUtil.US_ASCII));
        assertThat(reader.readLine()).startsWith("HTTP/1.1 " + status + ' ');
        assertThat(reader.lines().filter(String::isEmpty).findFirst()).isPresent();
        return reader;
    }
}
