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
import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalIoHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.codec.http2.headers.Http2Headers;
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
import io.netty5.util.NetUtil;
import io.netty5.util.Resource;

import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.handler.codec.http2.Http2TestUtil.bb;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class Http2MultiplexTransportTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(Http2MultiplexTransportTest.class);
    private static final ChannelHandler DISCARD_HANDLER = new ChannelHandlerAdapter() {

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Resource.dispose(msg);
        }

        @Override
        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
            Resource.dispose(evt);
        }
    };

    private EventLoopGroup eventLoopGroup;
    private Channel clientChannel;
    private Channel serverChannel;
    private Channel serverConnectedChannel;

    private static final class MultiplexInboundStream implements ChannelHandler {
        Future<Void> responseFuture;
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
                Buffer response = ctx.bufferAllocator().copyOf(LARGE_STRING, StandardCharsets.US_ASCII);
                responseFuture = ctx.writeAndFlush(new DefaultHttp2DataFrame(response.send(), true));
            }
            Resource.dispose(msg);
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
        eventLoopGroup = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
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

    @Disabled("This started failing when Http2MultiplexCodecBuilder was removed")
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
                        Resource.dispose(msg);
                    }
                });
            }
        });
        serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).asStage().get();

        Bootstrap bs = new Bootstrap();
        bs.group(eventLoopGroup);
        bs.channel(NioSocketChannel.class);
        bs.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(Http2FrameCodecBuilder
                        .forClient().autoAckSettingsFrame(false).build());
                ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER, new ChannelHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof Http2SettingsFrame) {
                            clientSettingsLatch.countDown();
                        }
                        Resource.dispose(msg);
                    }
                }));
            }
        });
        clientChannel = bs.connect(serverChannel.localAddress()).asStage().get();
        serverConnectedChannelLatch.await();
        serverConnectedChannel = serverConnectedChannelRef.get();

        serverConnectedChannel.writeAndFlush(new DefaultHttp2SettingsFrame(new Http2Settings()
                .maxConcurrentStreams(10))).asStage().sync();

        clientSettingsLatch.await();

        // We expect a timeout here because we want to asynchronously generate the SETTINGS ACK below.
        assertFalse(serverAckOneLatch.await(300, MILLISECONDS));

        // We expect 2 settings frames, the initial settings frame during connection establishment and the setting frame
        // written in this test. We should ack both of these settings frames.
        clientChannel.writeAndFlush(Http2SettingsAckFrame.INSTANCE).asStage().sync();
        clientChannel.writeAndFlush(Http2SettingsAckFrame.INSTANCE).asStage().sync();

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
                                            Http2Headers.newHeaders(), false)).addListener(future -> {
                                        ctx.write(new DefaultHttp2DataFrame(
                                                bb("Hello World").send(), true));
                                        ctx.channel().executor().execute(ctx::flush);
                                    });
                                }, 500, MILLISECONDS);
                            }
                            Resource.dispose(msg);
                        }
                    }));
                }
            });
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).asStage().get();

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
            clientChannel = bs.connect(serverChannel.localAddress()).asStage().get();
            Http2StreamChannelBootstrap h2Bootstrap = new Http2StreamChannelBootstrap(clientChannel);
            h2Bootstrap.handler(new ChannelHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                        latch.countDown();
                    }
                    Resource.dispose(msg);
                }
            });
            Http2StreamChannel streamChannel = h2Bootstrap.open().asStage().get();
            streamChannel.writeAndFlush(new DefaultHttp2HeadersFrame(Http2Headers.newHeaders(), true)).asStage().sync();

            latch.await();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @Timeout(value = 10000L, unit = MILLISECONDS)
    public void testSSLExceptionOpenSslTLSv12() throws Exception {
        testSslException(SslProvider.OPENSSL, false);
    }

    @Test
    @Timeout(value = 10000L, unit = MILLISECONDS)
    public void testSSLExceptionOpenSslTLSv13() throws Exception {
        testSslException(SslProvider.OPENSSL, true);
    }

    @Disabled("JDK SSLEngine does not produce an alert")
    @Test
    @Timeout(value = 10000L, unit = MILLISECONDS)
    public void testSSLExceptionJDKTLSv12() throws Exception {
        testSslException(SslProvider.JDK, false);
    }

    @Disabled("JDK SSLEngine does not produce an alert")
    @Test
    @Timeout(value = 10000L, unit = MILLISECONDS)
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
                    ch.pipeline().addLast(sslCtx.newHandler(ch.bufferAllocator()));
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(true).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
                }
            });
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).asStage().get();

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
                    ch.pipeline().addLast(clientCtx.newHandler(ch.bufferAllocator()));
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(false).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
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
                                        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                            if (cause.getCause() instanceof SSLException) {
                                                latch.countDown();
                                            } else {
                                                LOGGER.debug("Got unexpected exception in h2Boostrap handler", cause);
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
                                                    Http2Headers.newHeaders(), false));
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
                                } else {
                                    LOGGER.debug(
                                            "Got unexpected handshake completion event in client bootstrap handler: {}",
                                            handshakeCompletionEvent);
                                }
                            } else {
                                LOGGER.debug("Got unexpected user event in client bootstrap handler: {}", evt);
                            }
                        }
                    });
                }
            });
            clientChannel = bs.connect(serverChannel.localAddress()).asStage().get();
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
                    ch.pipeline().addLast(serverCtx.newHandler(ch.bufferAllocator()));
                    ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                        @Override
                        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                            ctx.pipeline().addLast(new Http2FrameCodecBuilder(true).build());
                            ctx.pipeline().addLast(new Http2MultiplexHandler(new ChannelHandler() {
                                @Override
                                public void channelRead(final ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
                                        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(
                                                Http2Headers.newHeaders(), false))
                                           .addListener(future -> {
                                                   ctx.writeAndFlush(new DefaultHttp2DataFrame(
                                                           bb("Hello World").send(),
                                                           true));
                                           });
                                    }
                                    Resource.dispose(msg);
                                }
                            }));
                        }
                    });
                }
            });
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).asStage().get();

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
                    ch.pipeline().addLast(clientCtx.newHandler(ch.bufferAllocator()));
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(false).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
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
                                            Resource.dispose(msg);
                                        }
                                    });
                                    h2Bootstrap.open().addListener(future -> {
                                        if (future.isSuccess()) {
                                            future.getNow().writeAndFlush(new DefaultHttp2HeadersFrame(
                                                    Http2Headers.newHeaders(), true));
                                        }
                                    });
                                }
                            }
                        }
                    });
                }
            });
            clientChannel = bs.connect(serverChannel.localAddress()).asStage().get();

            latch.await();
        } finally {
            if (ssc != null) {
                ssc.delete();
            }
        }
    }

    /**
     * When an HTTP/2 server stream channel receives a frame with EOS flag, and when it responds with a EOS
     * flag, then the server side stream will be closed, hence the stream handler will be inactivated. This test
     * verifies that the ChannelFuture of the server response is successful at the time the server stream handler is
     * inactivated.
     */
    @Test
    @Timeout(value = 120000L, unit = MILLISECONDS)
    public void streamHandlerInactivatedResponseFlushed() throws InterruptedException, ExecutionException {
        EventLoopGroup serverEventLoopGroup = null;
        EventLoopGroup clientEventLoopGroup = null;

        try {
            serverEventLoopGroup = new MultithreadEventLoopGroup(1, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "serverloop");
                }
            }, NioIoHandler.newFactory());

            clientEventLoopGroup = new MultithreadEventLoopGroup(1, new ThreadFactory() {
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
                    ch.setOption(ChannelOption.SO_SNDBUF, 1);
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
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).asStage().get();

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

            clientChannel = bs.connect(serverChannel.localAddress()).asStage().get();
            final Http2StreamChannelBootstrap h2Bootstrap = new Http2StreamChannelBootstrap(clientChannel);
            h2Bootstrap.handler(new ChannelHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                        latchClientResponses.countDown();
                    }
                    Resource.dispose(msg);
                }
                @Override
                public boolean isSharable() {
                    return true;
                }
            });

            List<Future<Void>> streamFutures = new ArrayList<>();
            for (int i = 0; i < streams; i ++) {
                Http2StreamChannel stream = h2Bootstrap.open().asStage().get();
                streamFutures.add(stream.writeAndFlush(new DefaultHttp2HeadersFrame(Http2Headers.newHeaders(), true)));
            }
            for (int i = 0; i < streams; i ++) {
                streamFutures.get(i).asStage().sync();
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
            group = new MultithreadEventLoopGroup(LocalIoHandler.newFactory());
            LocalAddress serverAddress = new LocalAddress(getClass().getName());
            ServerBootstrap sb = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(Http2FrameCodecBuilder.forServer().build());
                            pipeline.addLast(new Http2FrameIgnore<Http2SettingsFrame>(Http2SettingsFrame.class));
                            pipeline.addLast(new Http2FrameIgnore<Http2SettingsAckFrame>(Http2SettingsAckFrame.class));
                            pipeline.addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                @Override
                                protected void initChannel(Http2StreamChannel ch) {
                                    ChannelPipeline pipeline = ch.pipeline();
                                    pipeline.addLast(new Http2StreamFrameToHttpObjectCodec(true));
                                    pipeline.addLast(new HttpObjectAggregator<>(16384));
                                    pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                        @Override
                                        protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest msg) {
                                            ctx.writeAndFlush(
                                                    new DefaultFullHttpResponse(
                                                            msg.protocolVersion(), HttpResponseStatus.OK,
                                                            ctx.bufferAllocator().copyOf(
                                                                    "hello", StandardCharsets.US_ASCII)))
                                                    .addListener(ctx, ChannelFutureListeners.CLOSE);
                                        }
                                    });
                                }
                            }));
                        }
                    });
            serverChannel = sb.bind(serverAddress).asStage().get();

            Bootstrap cb = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(Http2FrameCodecBuilder.forClient().build());
                            pipeline.addLast(new Http2FrameIgnore<>(Http2SettingsFrame.class));
                            pipeline.addLast(new Http2FrameIgnore<>(Http2SettingsAckFrame.class));
                            pipeline.addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                @Override
                                protected void initChannel(Http2StreamChannel ch) {
                                    // noop
                                }
                            }));
                        }
                    });

            clientChannel = cb.connect(serverAddress).asStage().get();
            clientStreamChannel = new Http2StreamChannelBootstrap(clientChannel)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new Http2StreamFrameToHttpObjectCodec(false));
                            pipeline.addLast(new HttpObjectAggregator<>(16384));
                            pipeline.addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                @Override
                                protected void messageReceived(ChannelHandlerContext ctx, FullHttpResponse msg) {
                                    clientReceivedResponseLatch.countDown();
                                }
                            });
                        }
                    })
                    .open().asStage().get();

            clientStreamChannel.writeAndFlush(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test/",
                            clientStreamChannel.bufferAllocator().allocate(0))).asStage().get();

            assertTrue(clientReceivedResponseLatch.await(3, SECONDS));

            // The server should NOT send any RST_STREAM frame.
            assertFalse(resetFrameLatch.await(1, SECONDS));
        } finally {
            if (clientStreamChannel != null) {
                clientStreamChannel.close().asStage().get();
            }
            if (clientChannel != null) {
                clientChannel.close().asStage().get();
            }
            if (serverChannel != null) {
                serverChannel.close().asStage().get();
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
        protected void messageReceived(ChannelHandlerContext ctx, T msg) {
        }
    }
}
