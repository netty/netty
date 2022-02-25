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
package io.netty5.handler.codec.http2;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.ssl.ApplicationProtocolConfig;
import io.netty5.handler.ssl.ApplicationProtocolNames;
import io.netty5.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty5.handler.ssl.ClientAuth;
import io.netty5.handler.ssl.OpenSsl;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandshakeCompletionEvent;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.SupportedCipherSuiteFilter;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.util.CharsetUtil;
import io.netty5.util.NetUtil;
import io.netty5.util.ReferenceCountUtil;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class Http2MultiplexTransportTest {
    private static final ChannelHandler DISCARD_HANDLER = new ChannelHandlerAdapter() {

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

    @BeforeEach
    public void setup() {
        eventLoopGroup = new MultithreadEventLoopGroup(NioHandler.newFactory());
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
    public void asyncSettingsAckWithMultiplexCodec() throws Exception {
        asyncSettingsAck0(new Http2MultiplexCodecBuilder(true, DISCARD_HANDLER).build(), null);
    }

    @Test
    @Timeout(value = 10000, unit = MILLISECONDS)
    public void asyncSettingsAckWithMultiplexHandler() throws Exception {
        asyncSettingsAck0(new Http2FrameCodecBuilder(true).build(),
                new Http2MultiplexHandler(DISCARD_HANDLER));
    }

    private void asyncSettingsAck0(final Http2FrameCodec codec, final ChannelHandler multiplexer)
            throws Exception {
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
                ch.pipeline().addLast(new ChannelHandler() {
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
        serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).get();

        Bootstrap bs = new Bootstrap();
        bs.group(eventLoopGroup);
        bs.channel(NioSocketChannel.class);
        bs.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(Http2MultiplexCodecBuilder
                        .forClient(DISCARD_HANDLER).autoAckSettingsFrame(false).build());
                ch.pipeline().addLast(new ChannelHandler() {
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
        clientChannel = bs.connect(serverChannel.localAddress()).get();
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
    public void testFlushNotDiscarded() throws Exception {
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(eventLoopGroup);
            sb.channel(NioServerSocketChannel.class);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(true).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelHandler() {
                        @Override
                        public void channelRead(final ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
                                executorService.schedule(() -> {
                                    ctx.writeAndFlush(new DefaultHttp2HeadersFrame(
                                            new DefaultHttp2Headers(), false)).addListener(future -> {
                                        ctx.write(new DefaultHttp2DataFrame(
                                                Unpooled.copiedBuffer("Hello World",
                                                                      CharsetUtil.US_ASCII), true));
                                        ctx.channel().executor().execute(ctx::flush);
                                    });
                                }, 500, MILLISECONDS);
                            }
                            ReferenceCountUtil.release(msg);
                        }
                    }));
                }
            });
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).get();

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
            clientChannel = bs.connect(serverChannel.localAddress()).get();
            Http2StreamChannelBootstrap h2Bootstrap = new Http2StreamChannelBootstrap(clientChannel);
            h2Bootstrap.handler(new ChannelHandler() {
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
        SelfSignedCertificate ssc = null;
        try {
            ssc = new SelfSignedCertificate();
            final SslContext sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
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
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).get();

            final SslContext clientCtx = SslContextBuilder.forClient()
                    .keyManager(ssc.key(), ssc.cert())
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
                    ch.pipeline().addLast(new ChannelHandler() {
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
                                    h2Bootstrap.handler(new ChannelHandler() {
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
                                    h2Bootstrap.open().addListener(future -> {
                                        if (future.isSuccess()) {
                                            future.getNow().writeAndFlush(new DefaultHttp2HeadersFrame(
                                                    new DefaultHttp2Headers(), false));
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
            clientChannel = bs.connect(serverChannel.localAddress()).get();
            latch.await();
            AssertionError error = errorRef.get();
            if (error != null) {
                throw error;
            }
        } finally {
            if (ssc != null) {
                ssc.delete();
            }
        }
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "See: https://github.com/netty/netty/issues/11542")
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testFireChannelReadAfterHandshakeSuccess_JDK() throws Exception {
        assumeTrue(SslProvider.isAlpnSupported(SslProvider.JDK));
        testFireChannelReadAfterHandshakeSuccess(SslProvider.JDK);
    }

    @Disabled("This fails atm... needs investigation")
    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "See: https://github.com/netty/netty/issues/11542")
    @Timeout(value = 5000L, unit = MILLISECONDS)
    public void testFireChannelReadAfterHandshakeSuccess_OPENSSL() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        assumeTrue(SslProvider.isAlpnSupported(SslProvider.OPENSSL));
        testFireChannelReadAfterHandshakeSuccess(SslProvider.OPENSSL);
    }

    private void testFireChannelReadAfterHandshakeSuccess(SslProvider provider) throws Exception {
        SelfSignedCertificate ssc = null;
        try {
            ssc = new SelfSignedCertificate();
            final SslContext serverCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
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
                            ctx.pipeline().addLast(new Http2MultiplexHandler(new ChannelHandler() {
                                @Override
                                public void channelRead(final ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
                                        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(
                                                new DefaultHttp2Headers(), false))
                                           .addListener(future -> {
                                                   ctx.writeAndFlush(new DefaultHttp2DataFrame(
                                                           Unpooled.copiedBuffer("Hello World", CharsetUtil.US_ASCII),
                                                           true));
                                           });
                                    }
                                    ReferenceCountUtil.release(msg);
                                }
                            }));
                        }
                    });
                }
            });
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).get();

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
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt instanceof SslHandshakeCompletionEvent) {
                                SslHandshakeCompletionEvent handshakeCompletionEvent =
                                        (SslHandshakeCompletionEvent) evt;
                                if (handshakeCompletionEvent.isSuccess()) {
                                    Http2StreamChannelBootstrap h2Bootstrap =
                                            new Http2StreamChannelBootstrap(clientChannel);
                                    h2Bootstrap.handler(new ChannelHandler() {
                                        @Override
                                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                            if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                                                latch.countDown();
                                            }
                                            ReferenceCountUtil.release(msg);
                                        }
                                    });
                                    h2Bootstrap.open().addListener(future -> {
                                        if (future.isSuccess()) {
                                            future.getNow().writeAndFlush(new DefaultHttp2HeadersFrame(
                                                    new DefaultHttp2Headers(), true));
                                        }
                                    });
                                }
                            }
                        }
                    });
                }
            });
            clientChannel = bs.connect(serverChannel.localAddress()).get();

            latch.await();
        } finally {
            if (ssc != null) {
                ssc.delete();
            }
        }
    }
}
