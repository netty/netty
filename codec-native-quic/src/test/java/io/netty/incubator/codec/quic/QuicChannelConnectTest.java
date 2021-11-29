/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.TrustManagerFactoryWrapper;
import io.netty.util.DomainWildcardMappingBuilder;
import io.netty.util.concurrent.Future;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class QuicChannelConnectTest extends AbstractQuicTest {

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testConnectAndQLog() throws Throwable {
        Path path = Files.createTempFile("qlog", ".quic");
        assertTrue(path.toFile().delete());
        testQLog(path, p -> {
            try {
                // Some log should have been written at some point.
                while (Files.readAllLines(p).isEmpty()) {
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testConnectAndQLogDir() throws Throwable {
        Path path = Files.createTempDirectory("qlogdir-");
        testQLog(path, p -> {
            try {
                for (;;) {
                    File[] files = path.toFile().listFiles();
                    if (files != null && files.length == 1) {
                        if (!Files.readAllLines(files[0].toPath()).isEmpty()) {
                            return;
                        }
                    }
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private void testQLog(Path path, Consumer<Path> consumer) throws Throwable {
        QuicChannelValidationHandler serverValidationHandler = new QuicChannelValidationHandler();
        QuicChannelValidationHandler clientValidationHandler = new QuicChannelValidationHandler();
        Channel server = QuicTestUtils.newServer(serverValidationHandler,
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        try {
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientValidationHandler)
                    .option(QuicChannelOption.QLOG,
                            new QLogConfiguration(path.toString(), "testTitle", "test"))
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get();

            stream.writeAndFlush(Unpooled.directBuffer().writeZero(10)).sync();
            stream.close().sync();
            quicChannel.close().sync();
            quicChannel.closeFuture().sync();
            consumer.accept(path);

            serverValidationHandler.assertState();
            clientValidationHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testKeylogEnabled() throws Throwable {
        testKeylog(true);
    }

    @Test
    public void testKeylogDisabled() throws Throwable {
        testKeylog(false);
    }

    private static void testKeylog(boolean enable) throws Throwable {
        TestLogBackAppender.clearLogs();
        QuicChannelValidationHandler serverValidationHandler = new QuicChannelValidationHandler();
        QuicChannelValidationHandler clientValidationHandler = new QuicChannelValidationHandler();
        Channel server = QuicTestUtils.newServer(serverValidationHandler,
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(
                QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .applicationProtocols(QuicTestUtils.PROTOS).keylog(enable).build()));

        try {
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientValidationHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();

            quicChannel.close().sync();
            quicChannel.closeFuture().sync();
            assertTrue(enable ? TestLogBackAppender.getLogs().size() > 0 : TestLogBackAppender.getLogs().size() == 0);
            serverValidationHandler.assertState();
            clientValidationHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testAddressValidation() throws Throwable {
        // Bind to something so we can use the port to connect too and so can ensure we really timeout.
        DatagramSocket socket = new DatagramSocket();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder().localConnectionIdLength(10));
        try {
            ChannelStateVerifyHandler verifyHandler = new ChannelStateVerifyHandler();
            Future<QuicChannel> future = QuicChannel.newBootstrap(channel)
                    .handler(verifyHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(socket.getLocalSocketAddress())
                    .connectionAddress(QuicConnectionAddress.random(20))
                    .connect();
            Throwable cause = future.await().cause();
            assertThat(cause, CoreMatchers.instanceOf(IllegalArgumentException.class));
            verifyHandler.assertState();
        } finally {
            socket.close();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testConnectWithCustomIdLength() throws Throwable {
        testConnectWithCustomIdLength(10);
    }

    @Test
    public void testConnectWithCustomIdLengthOfZero() throws Throwable {
        testConnectWithCustomIdLength(0);
    }

    private static void testConnectWithCustomIdLength(int idLength) throws Throwable {
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        ChannelStateVerifyHandler serverQuicStreamHandler = new ChannelStateVerifyHandler();
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder()
                        .localConnectionIdLength(idLength),
                InsecureQuicTokenHandler.INSTANCE, serverQuicChannelHandler, serverQuicStreamHandler);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder()
                .localConnectionIdLength(idLength));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            assertTrue(quicChannel.close().await().isSuccess());
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
        } finally {
            serverQuicChannelHandler.assertState();
            serverQuicStreamHandler.assertState();

            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testConnectTimeout() throws Throwable {
        // Bind to something so we can use the port to connect too and so can ensure we really timeout.
        DatagramSocket socket = new DatagramSocket();
        Channel channel = QuicTestUtils.newClient();
        try {
            ChannelStateVerifyHandler verifyHandler = new ChannelStateVerifyHandler();
            Future<QuicChannel> future = QuicChannel.newBootstrap(channel)
                    .handler(verifyHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10)
                    .remoteAddress(socket.getLocalSocketAddress())
                    .connect();
            Throwable cause = future.await().cause();
            assertThat(cause, CoreMatchers.instanceOf(ConnectTimeoutException.class));
            verifyHandler.assertState();
        } finally {
            socket.close();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testConnectAlreadyConnected() throws Throwable {
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        ChannelStateVerifyHandler serverQuicStreamHandler = new ChannelStateVerifyHandler();

        Channel server = QuicTestUtils.newServer(serverQuicChannelHandler, serverQuicStreamHandler);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();

            // Try to connect again
            ChannelFuture connectFuture = quicChannel.connect(QuicConnectionAddress.random());
            Throwable cause = connectFuture.await().cause();
            assertThat(cause, CoreMatchers.instanceOf(AlreadyConnectedException.class));
            assertTrue(quicChannel.close().await().isSuccess());
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
            serverQuicChannelHandler.assertState();
            serverQuicStreamHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testConnectWithoutTokenValidation() throws Throwable {
        int numBytes = 8;
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        CountDownLatch serverLatch = new CountDownLatch(1);
        CountDownLatch clientLatch = new CountDownLatch(1);

        // Disable token validation
        Channel server = QuicTestUtils.newServer(NoValidationQuicTokenHandler.INSTANCE,
                serverQuicChannelHandler, new BytesCountingHandler(serverLatch, numBytes));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            QuicConnectionAddress localAddress = (QuicConnectionAddress) quicChannel.localAddress();
            QuicConnectionAddress remoteAddress = (QuicConnectionAddress) quicChannel.remoteAddress();
            assertNotNull(localAddress);
            assertNotNull(remoteAddress);

            QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new BytesCountingHandler(clientLatch, numBytes)).get();
            stream.writeAndFlush(Unpooled.directBuffer().writeZero(numBytes)).sync();
            clientLatch.await();

            assertEquals(QuicTestUtils.PROTOS[0],
                    // Just do the cast as getApplicationProtocol() only exists in SSLEngine itself since Java9+ and
                    // we may run on an earlier version
                    ((QuicheQuicSslEngine) quicChannel.sslEngine()).getApplicationProtocol());
            stream.close().sync();
            quicChannel.close().sync();
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());

            clientQuicChannelHandler.assertState();
            serverQuicChannelHandler.assertState();

            assertEquals(serverQuicChannelHandler.localAddress(), remoteAddress);
            assertEquals(serverQuicChannelHandler.remoteAddress(), localAddress);

            // Check if we also can access these after the channel was closed.
            assertNotNull(quicChannel.localAddress());
            assertNotNull(quicChannel.remoteAddress());
        } finally {
            serverLatch.await();

            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testConnectAndGetAddressesAfterClose() throws Throwable {
        AtomicReference<QuicChannel> acceptedRef = new AtomicReference<>();
        AtomicReference<QuicConnectionEvent> serverEventRef = new AtomicReference<>();
        Channel server = QuicTestUtils.newServer(
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        acceptedRef.set((QuicChannel) ctx.channel());
                        super.channelActive(ctx);
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof QuicConnectionEvent) {
                            serverEventRef.set((QuicConnectionEvent) evt);
                        }
                        super.userEventTriggered(ctx, evt);
                    }
                },
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        try {
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            quicChannel.close().sync();
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());

            // Check if we also can access these after the channel was closed.
            assertNotNull(quicChannel.localAddress());
            assertNotNull(quicChannel.remoteAddress());

            assertNull(serverEventRef.get().oldAddress());
            assertEquals(channel.localAddress(), serverEventRef.get().newAddress());

            QuicChannel accepted;
            while ((accepted = acceptedRef.get()) == null) {
                Thread.sleep(50);
            }
            // Check if we also can access these after the channel was closed.
            assertNotNull(accepted.localAddress());
            assertNotNull(accepted.remoteAddress());
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testConnectAndStreamPriority() throws Throwable {
        int numBytes = 8;
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        CountDownLatch serverLatch = new CountDownLatch(1);
        CountDownLatch clientLatch = new CountDownLatch(1);

        Channel server = QuicTestUtils.newServer(serverQuicChannelHandler,
                new BytesCountingHandler(serverLatch, numBytes));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new BytesCountingHandler(clientLatch, numBytes)).get();
            assertNull(stream.priority());
            QuicStreamPriority priority = new QuicStreamPriority(0, false);
            stream.updatePriority(priority).sync();
            assertEquals(priority, stream.priority());

            stream.writeAndFlush(Unpooled.directBuffer().writeZero(numBytes)).sync();
            clientLatch.await();

            stream.close().sync();
            quicChannel.close().sync();
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
        } finally {
            serverLatch.await();
            serverQuicChannelHandler.assertState();

            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testExtendedTrustManagerFailureOnTheClient() throws Throwable {
        testTrustManagerFailureOnTheClient(true);
    }

    @Test
    public void testTrustManagerFailureOnTheClient() throws Throwable {
        testTrustManagerFailureOnTheClient(false);
    }

    private void testTrustManagerFailureOnTheClient(boolean extended) throws Throwable {
        final X509TrustManager trustManager;
        if (extended) {
            trustManager = new TestX509ExtendedTrustManager() {

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                        throws CertificateException {
                    throw new CertificateException();
                }
            };
        } else {
            trustManager = new TestX509TrustManager() {

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    throw new CertificateException();
                }
            };
        }
        Channel server = QuicTestUtils.newServer(new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(QuicSslContextBuilder.forClient()
                .trustManager(new TrustManagerFactoryWrapper(trustManager))
                .applicationProtocols(QuicTestUtils.PROTOS).build()));
        try {
            Throwable cause = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .await().cause();
            assertThat(cause, Matchers.instanceOf(SSLException.class));
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testALPNProtocolMissmatch() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch eventLatch = new CountDownLatch(1);

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(QuicSslContextBuilder.forServer(
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                        .applicationProtocols("my-protocol").build()),
                InsecureQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            if (((SslHandshakeCompletionEvent) evt).cause() instanceof SSLHandshakeException) {
                                eventLatch.countDown();
                                return;
                            }
                        }
                        ctx.fireUserEventTriggered(evt);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        if (cause instanceof SSLHandshakeException) {
                            latch.countDown();
                        } else {
                            ctx.fireExceptionCaught(cause);
                        }
                    }
                },
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols("protocol").build()));
        try {
            Throwable cause = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .await().cause();
            assertThat(cause, Matchers.instanceOf(ClosedChannelException.class));
            latch.await();
            eventLatch.await();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testConnectSuccessWhenTrustManagerBuildFromSameCert() throws Throwable {
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(QuicSslContextBuilder.forServer(
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                        .applicationProtocols(QuicTestUtils.PROTOS).clientAuth(ClientAuth.NONE).build()),
                InsecureQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(QuicSslContextBuilder.forClient()
                .trustManager(QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                .applicationProtocols(QuicTestUtils.PROTOS).build()));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            assertTrue(quicChannel.close().await().isSuccess());
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testConnectMutualAuthSuccess() throws Throwable {
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(QuicSslContextBuilder.forServer(
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate()).trustManager(
                InsecureTrustManagerFactory.INSTANCE)
                        .applicationProtocols(QuicTestUtils.PROTOS).clientAuth(ClientAuth.REQUIRE).build()),
                InsecureQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).keyManager(
                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                .applicationProtocols(QuicTestUtils.PROTOS).build()));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            assertTrue(quicChannel.close().await().isSuccess());
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testConnectMutualAuthFailsIfClientNotSendCertificate() throws Throwable {
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(QuicSslContextBuilder.forServer(
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .applicationProtocols(QuicTestUtils.PROTOS).clientAuth(ClientAuth.REQUIRE).build()),
                InsecureQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicTestUtils.PROTOS).build()));
        try {
            Throwable cause = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .await().cause();
            assertThat(cause, Matchers.instanceOf(SSLException.class));
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testSniMatch() throws Throwable {
        QuicSslContext defaultServerSslContext = QuicSslContextBuilder.forServer(
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                .applicationProtocols("default-protocol").build();

        QuicSslContext sniServerSslContext = QuicSslContextBuilder.forServer(
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                .applicationProtocols("sni-protocol").build();

        CountDownLatch sniEventLatch = new CountDownLatch(1);
        CountDownLatch sslEventLatch = new CountDownLatch(1);
        String hostname = "quic.netty.io";
        QuicSslContext serverSslContext = QuicSslContextBuilder.buildForServerWithSni(
                        new DomainWildcardMappingBuilder<>(defaultServerSslContext)
                                .add(hostname, sniServerSslContext).build());

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(serverSslContext),
                InsecureQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof SniCompletionEvent) {
                            if (hostname.equals(((SniCompletionEvent) evt).hostname())) {
                                sniEventLatch.countDown();
                            }
                        } else if (evt instanceof SslHandshakeCompletionEvent) {
                            if (((SslHandshakeCompletionEvent) evt).isSuccess()) {
                                sslEventLatch.countDown();
                            }
                        }
                        super.userEventTriggered(ctx, evt);
                    }
                },
                new ChannelInboundHandlerAdapter());

        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols("sni-protocol").build();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder()
                .sslEngineProvider(c -> clientSslContext.newEngine(c.alloc(), hostname, 8080)));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();

            quicChannel.close().sync();
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
            sniEventLatch.await();
            sslEventLatch.await();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testSniFallbackToDefault() throws Throwable {
        testSniFallbackToDefault(true);
    }

    @Test
    public void testNoSniFallbackToDefault() throws Throwable {
        testSniFallbackToDefault(false);
    }

    private void testSniFallbackToDefault(boolean sendSni) throws Throwable {
        QuicSslContext defaultServerSslContext = QuicSslContextBuilder.forServer(
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                .applicationProtocols("default-protocol").build();

        QuicSslContext sniServerSslContext = QuicSslContextBuilder.forServer(
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                .applicationProtocols("sni-protocol").build();

        QuicSslContext serverSslContext = QuicSslContextBuilder.buildForServerWithSni(
                new DomainWildcardMappingBuilder<>(defaultServerSslContext)
                        .add("quic.netty.io", sniServerSslContext).build());

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(serverSslContext),
                InsecureQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());

        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols("default-protocol").build();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder()
                .sslEngineProvider(c -> {
                    if (sendSni) {
                        return clientSslContext.newEngine(c.alloc(), "netty.io", 8080);
                    } else {
                        return clientSslContext.newEngine(c.alloc());
                    }
                }));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();

            quicChannel.close().sync();
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    @Timeout(5)
    public void testSessionReusedOnClientSide() throws Exception {
        CountDownLatch serverSslCompletionEventLatch = new CountDownLatch(2);
        Channel server = QuicTestUtils.newServer(
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        ((QuicChannel) ctx.channel()).createStream(QuicStreamType.BIDIRECTIONAL,
                                new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ctx.writeAndFlush(ctx.alloc().directBuffer(10).writeZero(10))
                                        .addListener(f -> ctx.close());
                            }
                        });
                        ctx.fireChannelActive();
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            serverSslCompletionEventLatch.countDown();
                        }
                    }
                },
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols(QuicTestUtils.PROTOS).build();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder().sslEngineProvider(c ->
                clientSslContext.newEngine(c.alloc(), "localhost", 9999)));
        try {
            CountDownLatch clientSslCompletionEventLatch = new CountDownLatch(2);

            QuicChannelBootstrap bootstrap = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public boolean isSharable() {
                            return true;
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt instanceof SslHandshakeCompletionEvent) {
                                clientSslCompletionEventLatch.countDown();
                            }
                        }
                    })
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address);

            CountDownLatch latch1 = new CountDownLatch(1);
            QuicChannel quicChannel1 = bootstrap
                    .streamHandler(new BytesCountingHandler(latch1, 10))
                    .connect()
                    .get();
            latch1.await();
            assertSessionReused(quicChannel1, false);

            CountDownLatch latch2 = new CountDownLatch(1);
            QuicChannel quicChannel2 = bootstrap
                    .streamHandler(new BytesCountingHandler(latch2, 10))
                    .connect()
                    .get();

            latch2.await();

            // Ensure the session is reused.
            assertSessionReused(quicChannel2, true);

            quicChannel1.close().sync();
            quicChannel2.close().sync();

            serverSslCompletionEventLatch.await();
            clientSslCompletionEventLatch.await();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    private static void assertSessionReused(QuicChannel channel, boolean reused) throws Exception {
        QuicheQuicSslEngine engine =  (QuicheQuicSslEngine) channel.sslEngine();
        while (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            // Let's wait a bit and re-check if the handshake is done.
            Thread.sleep(50);
        }
        assertEquals(reused, engine.isSessionReused());
    }

    private static final class BytesCountingHandler extends ChannelInboundHandlerAdapter {
        private final CountDownLatch latch;
        private final int numBytes;
        private int bytes;

        BytesCountingHandler(CountDownLatch latch, int numBytes) {
            this.latch = latch;
            this.numBytes = numBytes;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buffer = (ByteBuf) msg;
            bytes += buffer.readableBytes();
            ctx.writeAndFlush(buffer);
            if (bytes == numBytes) {
                latch.countDown();
            }
        }
    }

    private static final class ChannelStateVerifyHandler extends QuicChannelValidationHandler {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
            fail();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
            fail();
        }
    }

    private static final class ChannelActiveVerifyHandler extends QuicChannelValidationHandler {
        private final BlockingQueue<Integer> states = new LinkedBlockingQueue<>();
        private volatile QuicConnectionAddress localAddress;
        private volatile QuicConnectionAddress remoteAddress;

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.fireChannelRegistered();
            states.add(0);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            ctx.fireChannelUnregistered();
            states.add(3);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            localAddress = (QuicConnectionAddress) ctx.channel().localAddress();
            remoteAddress = (QuicConnectionAddress) ctx.channel().remoteAddress();
            ctx.fireChannelActive();
            states.add(1);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
            states.add(2);
        }

        void assertState() throws Throwable {
            // Check that we receive the different events in the correct order.
            for (long i = 0; i < 4; i++) {
                assertEquals(i, (int) states.take());
            }
            assertNull(states.poll());
            super.assertState();
        }

        QuicConnectionAddress localAddress() {
            return localAddress;
        }

        QuicConnectionAddress remoteAddress() {
            return remoteAddress;
        }
    }

    private abstract static class TestX509ExtendedTrustManager extends X509ExtendedTrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            // NOOP
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            // NOOP
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            // NOOP
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            // NOOP
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // NOOP
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // NOOP
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private abstract static class TestX509TrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // NOOP
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // NOOP
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
