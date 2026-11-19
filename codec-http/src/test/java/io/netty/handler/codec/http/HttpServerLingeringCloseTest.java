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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HttpServerLingeringCloseTest {
    @Test
    void closeTimeoutDefaultsToFiveSecondsAndRejectsNegativeValues() {
        HttpObjectAggregator aggregator = new HttpObjectAggregator(4);
        assertThat(aggregator.closeTimeoutMillis()).isEqualTo(5000);
        assertThatIllegalArgumentException().isThrownBy(() -> aggregator.setCloseTimeoutMillis(-1));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void tcpPeerCanFinishSendingAfterReading413(boolean autoRead) throws Exception {
        try (Socket peer = new Socket()) {
            checkDelivery(peer, false, 60000, autoRead);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 10000 })
    void tlsPeerReceives413AndOrderlyShutdown(long closeNotifyReadTimeoutMillis) throws Exception {
        X509Bundle cert = new CertificateBuilder().subject("CN=localhost")
                .setIsCertificateAuthority(true).buildSelfSigned();
        SslContext serverTls = SslContextBuilder.forServer(cert.toKeyManagerFactory())
                .sslProvider(SslProvider.JDK).build();
        SSLContext clientTls = SSLContext.getInstance("TLS");
        clientTls.init(null, new TrustManager[] { cert.toTrustManager() }, null);
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        CompletableFuture<SslHandler> accepted = new CompletableFuture<SslHandler>();
        CompletableFuture<Void> outputShutdown = new CompletableFuture<Void>();
        try (Socket peer = clientTls.getSocketFactory().createSocket()) {
            Channel server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            SslHandler ssl = serverTls.newHandler(ch.alloc());
                            ssl.setCloseNotifyReadTimeoutMillis(closeNotifyReadTimeoutMillis);
                            ch.pipeline().addLast(ssl, new HttpServerCodec(), new HttpObjectAggregator(4),
                                    new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                            if (evt == ChannelOutputShutdownEvent.INSTANCE) {
                                                outputShutdown.complete(null);
                                            }
                                            ctx.fireUserEventTriggered(evt);
                                        }
                                    });
                            accepted.complete(ssl);
                        }
                    }).bind("127.0.0.1", 0).sync().channel();
            peer.connect(server.localAddress());
            peer.setSoTimeout(10000);
            sendRequest(peer, false);
            assertThat(readHeaders(peer)).startsWith("HTTP/1.1 413 ");
            // The rejection is on the wire. Send another application-data record before reading close_notify.
            peer.getOutputStream().write(new byte[1024]);
            if (closeNotifyReadTimeoutMillis == 0) {
                outputShutdown.get(10, TimeUnit.SECONDS);
            }
            assertThat(peer.getInputStream().read()).isEqualTo(-1);
            peer.close();
            SslHandler ssl = accepted.get(10, TimeUnit.SECONDS);
            assertThat(ssl.sslCloseFuture().await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(ssl.sslCloseFuture().isSuccess()).isTrue();
            assertThat(ssl.sslCloseFuture().getNow().closeFuture().await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void rejectedExpectationClosesByDeadlineEvenIfPeerKeepsOutputOpen() throws Exception {
        try (Socket peer = new Socket()) {
            checkDelivery(peer, true, 2000, true);
        }
    }

    @Test
    void deadlineClosesSocketWhileRejectionWriteIsPending() throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        CompletableFuture<Channel> writing = new CompletableFuture<Channel>();
        try (Socket peer = new Socket()) {
            Channel server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            HttpObjectAggregator aggregator = new HttpObjectAggregator(4);
                            aggregator.setCloseTimeoutMillis(200);
                            ch.pipeline().addLast(new HttpServerCodec(), new ChannelOutboundHandlerAdapter() {
                                @Override
                                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                                    ReferenceCountUtil.release(msg);
                                    ch.closeFuture().addListener(f -> promise.tryFailure(new ClosedChannelException()));
                                    writing.complete(ch);
                                }
                            }, aggregator);
                        }
                    }).bind("127.0.0.1", 0).sync().channel();
            peer.connect(server.localAddress());
            peer.getOutputStream().write(("PUT / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n" +
                    "Content-Length: 5\r\n\r\n").getBytes(CharsetUtil.US_ASCII));
            assertThat(writing.get(10, TimeUnit.SECONDS).closeFuture().await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void zeroTimeoutClosesAfterResponseWhilePeerOutputIsOpen() throws Exception {
        try (Socket peer = new Socket()) {
            checkDelivery(peer, false, 0, true);
        }
    }

    private static void checkDelivery(Socket peer, boolean rejectExpectation, long timeoutMillis,
                                      boolean autoRead) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        CompletableFuture<Channel> accepted = new CompletableFuture<Channel>();
        try {
            Channel server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.AUTO_READ, autoRead)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            HttpObjectAggregator aggregator = new HttpObjectAggregator(4, rejectExpectation);
                            aggregator.setCloseTimeoutMillis(timeoutMillis);
                            ch.pipeline().addLast(new FlushConsolidationHandler(), new HttpServerCodec(), aggregator);
                            accepted.complete(ch);
                        }
                    }).bind("127.0.0.1", 0).sync().channel();
            peer.connect(server.localAddress());
            peer.setSoTimeout(10000);
            Channel child = accepted.get(10, TimeUnit.SECONDS);
            if (!autoRead) {
                child.read();
            }
            sendRequest(peer, rejectExpectation);
            assertThat(readHeaders(peer)).startsWith("HTTP/1.1 413 ");
            assertThat(peer.getInputStream().read()).isEqualTo(-1);
            if (rejectExpectation || timeoutMillis == 0) {
                assertThat(child.closeFuture().await(3, TimeUnit.SECONDS)).isTrue();
                return;
            }
            assertThat(child.isActive()).isTrue();
            peer.getOutputStream().write(new byte[1024]);
            peer.shutdownOutput();
            // The peer's FIN must complete the close well before the 60-second deadline.
            assertThat(child.closeFuture().await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    private static void sendRequest(Socket peer, boolean expectContinue) throws Exception {
        String request = "PUT / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n" +
                (expectContinue ? "Expect: 100-continue\r\n" : "") + "Content-Length: 65536\r\n\r\nabcde";
        peer.getOutputStream().write(request.getBytes(CharsetUtil.US_ASCII));
    }

    private static String readHeaders(Socket peer) throws Exception {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        int next;
        while ((next = peer.getInputStream().read()) != -1) {
            response.write(next);
            if (next == '\n' && response.toString("US-ASCII").endsWith("\r\n\r\n")) {
                return response.toString("US-ASCII");
            }
        }
        throw new AssertionError("EOF before the end of response headers: " + response.toString("US-ASCII"));
    }
}
