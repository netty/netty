/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.net.ssl.SSLException;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http2.Http2FrameCodecBuilder.forClient;
import static io.netty.handler.codec.http2.Http2FrameCodecBuilder.forServer;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class Http2MultiplexTransportTest {
    private static final ChannelHandler DISCARD_HANDLER = new ChannelInboundHandlerAdapter() {

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            ReferenceCountUtil.release(evt);
        }
    };

    private EventLoopGroup eventLoopGroup;
    private Channel clientChannel;
    private Channel serverChannel;
    private Channel serverConnectedChannel;

    private static final class MultiplexInboundStream extends ChannelInboundHandlerAdapter {
        ChannelFuture responseFuture;
        final AtomicInteger handlerInactivatedFlushed;
        final AtomicInteger handleInactivatedNotFlushed;
        final CountDownLatch latchHandlerInactive;
        static final String LARGE_STRING = generateLargeString(10240);

        MultiplexInboundStream(AtomicInteger handleInactivatedFlushed,
                               AtomicInteger handleInactivatedNotFlushed, CountDownLatch latchHandlerInactive) {
            this.handlerInactivatedFlushed = handleInactivatedFlushed;
            this.handleInactivatedNotFlushed = handleInactivatedNotFlushed;
            this.latchHandlerInactive = latchHandlerInactive;
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
                ByteBuf response = Unpooled.copiedBuffer(LARGE_STRING, CharsetUtil.US_ASCII);
                responseFuture = ctx.writeAndFlush(new DefaultHttp2DataFrame(response, true));
            }
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (responseFuture.isSuccess()) {
                handlerInactivatedFlushed.incrementAndGet();
            } else {
                handleInactivatedNotFlushed.incrementAndGet();
            }
            latchHandlerInactive.countDown();
            ctx.fireChannelInactive();
        }

        private static String generateLargeString(int sizeInBytes) {
            StringBuilder sb = new StringBuilder(sizeInBytes);
            for (int i = 0; i < sizeInBytes; i++) {
                sb.append('X');
            }
            return sb.toString();
        }
    }

    @BeforeEach
    public void setup() {
        eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    }

    @AfterEach
    public void teardown() {
        if (clientChannel != null) {
            clientChannel.close();
        }
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close();
        }
        eventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS);
    }

    @Test
    @Timeout(value = 10000, unit = MILLISECONDS)
    public void asyncSettingsAckWithMultiplexCodec() throws InterruptedException {
        asyncSettingsAck0(new Http2MultiplexCodecBuilder(true, DISCARD_HANDLER).build(), null);
    }

    @Test
    @Timeout(value = 10000, unit = MILLISECONDS)
    public void asyncSettingsAckWithMultiplexHandler() throws InterruptedException {
        asyncSettingsAck0(new Http2FrameCodecBuilder(true).build(),
                new Http2MultiplexHandler(DISCARD_HANDLER));
    }

    private void asyncSettingsAck0(final Http2FrameCodec codec, final ChannelHandler multiplexer)
            throws InterruptedException {
        // The client expects 2 settings frames. One from the connection setup and one from this test.
        final CountDownLatch serverAckOneLatch = new CountDownLatch(1);
        final CountDownLatch serverAckAllLatch = new CountDownLatch(2);
        final CountDownLatch clientSettingsLatch = new CountDownLatch(2);
        final CountDownLatch serverConnectedChannelLatch = new CountDownLatch(1);
        final AtomicReference<Channel> serverConnectedChannelRef = new AtomicReference<Channel>();
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(eventLoopGroup);
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(codec);
                if (multiplexer != null) {
                    ch.pipeline().addLast(multiplexer);
                }
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        serverConnectedChannelRef.set(ctx.channel());
                        serverConnectedChannelLatch.countDown();
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof Http2SettingsAckFrame) {
                            serverAckOneLatch.countDown();
                            serverAckAllLatch.countDown();
                        }
                        ReferenceCountUtil.release(msg);
                    }
                });
            }
        });
        serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).awaitUninterruptibly().channel();

        Bootstrap bs = new Bootstrap();
        bs.group(eventLoopGroup);
        bs.channel(NioSocketChannel.class);
        bs.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(Http2MultiplexCodecBuilder
                        .forClient(DISCARD_HANDLER).autoAckSettingsFrame(false).build());
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof Http2SettingsFrame) {
                            clientSettingsLatch.countDown();
                        }
                        ReferenceCountUtil.release(msg);
                    }
                });
            }
        });
        clientChannel = bs.connect(serverChannel.localAddress()).awaitUninterruptibly().channel();
        serverConnectedChannelLatch.await();
        serverConnectedChannel = serverConnectedChannelRef.get();

        serverConnectedChannel.writeAndFlush(new DefaultHttp2SettingsFrame(new Http2Settings()
                .maxConcurrentStreams(10))).sync();

        clientSettingsLatch.await();

        // We expect a timeout here because we want to asynchronously generate the SETTINGS ACK below.
        assertFalse(serverAckOneLatch.await(300, MILLISECONDS));

        // We expect 2 settings frames, the initial settings frame during connection establishment and the setting frame
        // written in this test. We should ack both of these settings frames.
        clientChannel.writeAndFlush(Http2SettingsAckFrame.INSTANCE).sync();
        clientChannel.writeAndFlush(Http2SettingsAckFrame.INSTANCE).sync();

        serverAckAllLatch.await();
    }

    @Test
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testFlushNotDiscarded()
            throws InterruptedException {
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(eventLoopGroup);
            sb.channel(NioServerSocketChannel.class);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(true).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(final ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
                                executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(
                                                new DefaultHttp2Headers(), false)).addListener(
                                                        new ChannelFutureListener() {
                                            @Override
                                            public void operationComplete(ChannelFuture future) {
                                                ctx.write(new DefaultHttp2DataFrame(
                                                        Unpooled.copiedBuffer("Hello World", CharsetUtil.US_ASCII),
                                                        true));
                                                ctx.channel().eventLoop().execute(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        ctx.flush();
                                                    }
                                                });
                                            }
                                        });
                                    }
                                }, 500, MILLISECONDS);
                            }
                            ReferenceCountUtil.release(msg);
                        }
                    }));
                }
            });
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).syncUninterruptibly().channel();

            final CountDownLatch latch = new CountDownLatch(1);
            Bootstrap bs = new Bootstrap();
            bs.group(eventLoopGroup);
            bs.channel(NioSocketChannel.class);
            bs.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(false).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
                }
            });
            clientChannel = bs.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
            Http2StreamChannelBootstrap h2Bootstrap = new Http2StreamChannelBootstrap(clientChannel);
            h2Bootstrap.handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                        latch.countDown();
                    }
                    ReferenceCountUtil.release(msg);
                }
            });
            Http2StreamChannel streamChannel = h2Bootstrap.open().syncUninterruptibly().getNow();
            streamChannel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), true))
                    .syncUninterruptibly();

            latch.await();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testSSLExceptionOpenSslTLSv12() throws Exception {
        testSslException(SslProvider.OPENSSL, false);
    }

    @Test
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testSSLExceptionOpenSslTLSv13() throws Exception {
        testSslException(SslProvider.OPENSSL, true);
    }

    @Disabled("JDK SSLEngine does not produce an alert")
    @Test
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testSSLExceptionJDKTLSv12() throws Exception {
        testSslException(SslProvider.JDK, false);
    }

    @Disabled("JDK SSLEngine does not produce an alert")
    @Test
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testSSLExceptionJDKTLSv13() throws Exception {
        testSslException(SslProvider.JDK, true);
    }

    private void testSslException(SslProvider provider, final boolean tlsv13) throws Exception {
        assumeTrue(SslProvider.isAlpnSupported(provider));
        if (tlsv13) {
            assumeTrue(SslProvider.isTlsv13Supported(provider));
        }
        final String protocol = tlsv13 ? "TLSv1.3" : "TLSv1.2";
        X509Bundle cert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        final SslContext sslCtx = SslContextBuilder.forServer(cert.getKeyPair().getPrivate(), cert.getCertificatePath())
                .trustManager(new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        throw new CertificateExpiredException();
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        throw new CertificateExpiredException();
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }).sslProvider(provider)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .protocols(protocol)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1)).clientAuth(ClientAuth.REQUIRE)
                .build();

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(eventLoopGroup);
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast(new Http2FrameCodecBuilder(true).build());
                ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
            }
        });
        serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).syncUninterruptibly().channel();

        final SslContext clientCtx = SslContextBuilder.forClient()
                .keyManager(cert.getKeyPair().getPrivate(), cert.getCertificatePath())
                .sslProvider(provider)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .protocols(protocol)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<AssertionError> errorRef = new AtomicReference<AssertionError>();
        Bootstrap bs = new Bootstrap();
        bs.group(eventLoopGroup);
        bs.channel(NioSocketChannel.class);
        bs.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(clientCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast(new Http2FrameCodecBuilder(false).build());
                ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            SslHandshakeCompletionEvent handshakeCompletionEvent =
                                    (SslHandshakeCompletionEvent) evt;
                            if (handshakeCompletionEvent.isSuccess()) {
                                // In case of TLSv1.3 we should succeed the handshake. The alert for
                                // the mTLS failure will be send in the next round-trip.
                                if (!tlsv13) {
                                    errorRef.set(new AssertionError("TLSv1.3 expected"));
                                }

                                Http2StreamChannelBootstrap h2Bootstrap =
                                        new Http2StreamChannelBootstrap(ctx.channel());
                                h2Bootstrap.handler(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        if (cause.getCause() instanceof SSLException) {
                                            latch.countDown();
                                        }
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) {
                                        latch.countDown();
                                    }
                                });
                                h2Bootstrap.open().addListener(new FutureListener<Channel>() {
                                    @Override
                                    public void operationComplete(Future<Channel> future) {
                                        if (future.isSuccess()) {
                                            future.getNow().writeAndFlush(new DefaultHttp2HeadersFrame(
                                                    new DefaultHttp2Headers(), false));
                                        }
                                    }
                                });

                            } else if (handshakeCompletionEvent.cause() instanceof SSLException) {
                                // In case of TLSv1.2 we should never see the handshake succeed as the alert for
                                // the mTLS failure will be send in the same round-trip.
                                if (tlsv13) {
                                    errorRef.set(new AssertionError("TLSv1.2 expected"));
                                }
                                latch.countDown();
                                latch.countDown();
                            }
                        }
                    }
                });
            }
        });
        clientChannel = bs.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
        latch.await();
        AssertionError error = errorRef.get();
        if (error != null) {
            throw error;
        }
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "See: https://github.com/netty/netty/issues/11542")
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testFireChannelReadAfterHandshakeSuccess_JDK() throws Exception {
        assumeTrue(SslProvider.isAlpnSupported(SslProvider.JDK));
        testFireChannelReadAfterHandshakeSuccess(SslProvider.JDK);
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "See: https://github.com/netty/netty/issues/11542")
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testFireChannelReadAfterHandshakeSuccess_OPENSSL() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        assumeTrue(SslProvider.isAlpnSupported(SslProvider.OPENSSL));
        testFireChannelReadAfterHandshakeSuccess(SslProvider.OPENSSL);
    }

    private void testFireChannelReadAfterHandshakeSuccess(SslProvider provider) throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        final SslContext serverCtx = SslContextBuilder.forServer(cert.getKeyPair().getPrivate(),
                        cert.getCertificatePath())
                .sslProvider(provider)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(eventLoopGroup);
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(serverCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                        ctx.pipeline().addLast(new Http2FrameCodecBuilder(true).build());
                        ctx.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(final ChannelHandlerContext ctx, Object msg) {
                                if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
                                    ctx.writeAndFlush(new DefaultHttp2HeadersFrame(
                                                    new DefaultHttp2Headers(), false))
                                            .addListener(new ChannelFutureListener() {
                                                @Override
                                                public void operationComplete(ChannelFuture future) {
                                                    ctx.writeAndFlush(new DefaultHttp2DataFrame(
                                                            Unpooled.copiedBuffer("Hello World", CharsetUtil.US_ASCII),
                                                            true));
                                                }
                                            });
                                }
                                ReferenceCountUtil.release(msg);
                            }
                        }));
                    }
                });
            }
        });
        serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).sync().channel();

        final SslContext clientCtx = SslContextBuilder.forClient()
                .sslProvider(provider)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();

        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap bs = new Bootstrap();
        bs.group(eventLoopGroup);
        bs.channel(NioSocketChannel.class);
        bs.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(clientCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast(new Http2FrameCodecBuilder(false).build());
                ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            SslHandshakeCompletionEvent handshakeCompletionEvent =
                                    (SslHandshakeCompletionEvent) evt;
                            if (handshakeCompletionEvent.isSuccess()) {
                                Http2StreamChannelBootstrap h2Bootstrap =
                                        new Http2StreamChannelBootstrap(ctx.channel());
                                h2Bootstrap.handler(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                                            latch.countDown();
                                        }
                                        ReferenceCountUtil.release(msg);
                                    }
                                });
                                h2Bootstrap.open().addListener(new FutureListener<Channel>() {
                                    @Override
                                    public void operationComplete(Future<Channel> future) {
                                        if (future.isSuccess()) {
                                            future.getNow().writeAndFlush(new DefaultHttp2HeadersFrame(
                                                    new DefaultHttp2Headers(), true));
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
        clientChannel = bs.connect(serverChannel.localAddress()).sync().channel();

        latch.await();
    }

    /**
     * When an HTTP/2 server stream channel receives a frame with EOS flag, and when it responds with a EOS
     * flag, then the server side stream will be closed, hence the stream handler will be inactivated. This test
     * verifies that the ChannelFuture of the server response is successful at the time the server stream handler is
     * inactivated.
     */
    @Test
    @Timeout(value = 120000L, unit = MILLISECONDS)
    public void streamHandlerInactivatedResponseFlushed() throws InterruptedException {
        EventLoopGroup serverEventLoopGroup = null;
        EventLoopGroup clientEventLoopGroup = null;

        try {
            serverEventLoopGroup = new MultiThreadIoEventLoopGroup(1, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "serverloop");
                }
            }, NioIoHandler.newFactory());

            clientEventLoopGroup = new MultiThreadIoEventLoopGroup(1, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "clientloop");
                }
            }, NioIoHandler.newFactory());

            final int streams = 10;
            final CountDownLatch latchClientResponses = new CountDownLatch(streams);
            final CountDownLatch latchHandlerInactive = new CountDownLatch(streams);

            final AtomicInteger handlerInactivatedFlushed = new AtomicInteger();
            final AtomicInteger handleInactivatedNotFlushed = new AtomicInteger();
            final ServerBootstrap sb = new ServerBootstrap();

            sb.group(serverEventLoopGroup);
            sb.channel(NioServerSocketChannel.class);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    // using a short sndbuf size will trigger writability events
                    ch.config().setOption(ChannelOption.SO_SNDBUF, 1);
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(true).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().remove(this);
                            ch.pipeline().addLast(new MultiplexInboundStream(handlerInactivatedFlushed,
                                    handleInactivatedNotFlushed, latchHandlerInactive));
                        }
                    }));
                }
            });
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).syncUninterruptibly().channel();

            final Bootstrap bs = new Bootstrap();

            bs.group(clientEventLoopGroup);
            bs.channel(NioSocketChannel.class);
            bs.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(false).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
                }
            });

            clientChannel = bs.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
            final Http2StreamChannelBootstrap h2Bootstrap = new Http2StreamChannelBootstrap(clientChannel);
            h2Bootstrap.handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                        latchClientResponses.countDown();
                    }
                    ReferenceCountUtil.release(msg);
                }
                @Override
                public boolean isSharable() {
                    return true;
                }
            });

            List<ChannelFuture> streamFutures = new ArrayList<ChannelFuture>();
            for (int i = 0; i < streams; i ++) {
                Http2StreamChannel stream = h2Bootstrap.open().syncUninterruptibly().getNow();
                streamFutures.add(stream.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), true)));
            }
            for (int i = 0; i < streams; i ++) {
                streamFutures.get(i).syncUninterruptibly();
            }

            assertTrue(latchHandlerInactive.await(120000, MILLISECONDS));
            assertTrue(latchClientResponses.await(120000, MILLISECONDS));
            assertEquals(0, handleInactivatedNotFlushed.get());
            assertEquals(streams, handlerInactivatedFlushed.get());
        } finally {
            if (serverEventLoopGroup != null) {
                serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS);
            }
            if (clientEventLoopGroup != null) {
                clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS);
            }
        }
    }

    @Test
    public void testServerCloseShouldNotSendResetIfClientSentEOS() throws Exception {
        EventLoopGroup group = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        Channel clientStreamChannel = null;
        try {
            final CountDownLatch clientReceivedResponseLatch = new CountDownLatch(1);
            final CountDownLatch resetFrameLatch = new CountDownLatch(1);
            group = new DefaultEventLoop();
            LocalAddress serverAddress = new LocalAddress(getClass().getName());
            ServerBootstrap sb = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(forServer().build());
                            pipeline.addLast(new Http2FrameIgnore<Http2SettingsFrame>(Http2SettingsFrame.class));
                            pipeline.addLast(new Http2FrameIgnore<Http2SettingsAckFrame>(Http2SettingsAckFrame.class));
                            pipeline.addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                @Override
                                protected void initChannel(Http2StreamChannel ch) {
                                    ChannelPipeline pipeline = ch.pipeline();
                                    pipeline.addLast(new Http2StreamFrameToHttpObjectCodec(true, true));
                                    pipeline.addLast(new HttpObjectAggregator(16384));
                                    pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
                                            ctx.writeAndFlush(
                                                    new DefaultFullHttpResponse(
                                                            msg.protocolVersion(), HttpResponseStatus.OK,
                                                            Unpooled.copiedBuffer("hello", CharsetUtil.US_ASCII)))
                                                    .addListeners(ChannelFutureListener.CLOSE);
                                        }
                                    });
                                }
                            }));
                        }
                    });
            serverChannel = sb.bind(serverAddress).sync().channel();

            Bootstrap cb = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(forClient().build());
                            pipeline.addLast(new Http2FrameIgnore<Http2SettingsFrame>(Http2SettingsFrame.class));
                            pipeline.addLast(new Http2FrameIgnore<Http2SettingsAckFrame>(Http2SettingsAckFrame.class));
                            pipeline.addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                @Override
                                protected void initChannel(Http2StreamChannel ch) {
                                    // noop
                                }
                            }));
                        }
                    });

            clientChannel = cb.connect(serverAddress).sync().channel();
            clientStreamChannel = new Http2StreamChannelBootstrap(clientChannel)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new Http2StreamFrameToHttpObjectCodec(false, true));
                            pipeline.addLast(new HttpObjectAggregator(16384));
                            pipeline.addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                                    clientReceivedResponseLatch.countDown();
                                }
                            });
                        }
                    })
                    .open().sync().get();

            clientStreamChannel.writeAndFlush(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test/")).sync();

            assertTrue(clientReceivedResponseLatch.await(3, SECONDS));

            // The server should NOT send any RST_STREAM frame.
            assertFalse(resetFrameLatch.await(1, SECONDS));
        } finally {
            if (clientStreamChannel != null) {
                clientStreamChannel.close().syncUninterruptibly();
            }
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (group != null) {
                group.shutdownGracefully(0, 3, SECONDS);
            }
        }
    }

    private static final class Http2FrameIgnore<T extends Http2Frame> extends SimpleChannelInboundHandler<T> {
        Http2FrameIgnore(Class<? extends T> inboundMessageType) {
            super(inboundMessageType);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, T msg) {
        }
    }
}
