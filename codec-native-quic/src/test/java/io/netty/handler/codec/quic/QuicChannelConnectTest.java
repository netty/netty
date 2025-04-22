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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.TrustManagerFactoryWrapper;
import io.netty.util.DomainWildcardMappingBuilder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.AssertionFailedError;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class QuicChannelConnectTest extends AbstractQuicTest {

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testConnectAndQLog(Executor executor) throws Throwable {
        Path path = Files.createTempFile("qlog", ".quic");
        assertTrue(path.toFile().delete());
        testQLog(executor, path, p -> {
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

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testConnectAndQLogDir(Executor executor) throws Throwable {
        Path path = Files.createTempDirectory("qlogdir-");
        testQLog(executor, path, p -> {
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

    private void testQLog(Executor executor, Path path, Consumer<Path> consumer) throws Throwable {
        QuicChannelValidationHandler serverValidationHandler = new QuicChannelValidationHandler();
        QuicChannelValidationHandler clientValidationHandler = new QuicChannelValidationHandler();
        Channel server = QuicTestUtils.newServer(executor, serverValidationHandler,
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(executor);
        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testKeylogEnabled(Executor executor) throws Throwable {
        testKeylog(executor, true);
        assertNotEquals(0, TestLogBackAppender.getLogs().size());
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testKeylogDisabled(Executor executor) throws Throwable {
        testKeylog(executor, false);
        assertEquals(0, TestLogBackAppender.getLogs().size());
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testCustomKeylog(Executor executor) throws Throwable {
        AtomicBoolean called = new AtomicBoolean();
        testKeylog(executor, (BoringSSLKeylog) (engine, log) -> {
            called.set(true);
        });
        assertTrue(called.get());
    }

    private static void testKeylog(Executor sslTaskExecutor, Object keylog) throws Throwable {
        TestLogBackAppender.clearLogs();
        QuicChannelValidationHandler serverValidationHandler = new QuicChannelValidationHandler();
        QuicChannelValidationHandler clientValidationHandler = new QuicChannelValidationHandler();
        Channel server = QuicTestUtils.newServer(sslTaskExecutor, serverValidationHandler,
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        QuicSslContextBuilder ctxClientBuilder = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicTestUtils.PROTOS);
        if (keylog instanceof Boolean) {
            ctxClientBuilder.keylog((Boolean) keylog);
        } else {
            ctxClientBuilder.keylog((BoringSSLKeylog) keylog);
        }

        QuicSslContext context = ctxClientBuilder.build();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(sslTaskExecutor, context));

        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientValidationHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();

            quicChannel.close().sync();
            quicChannel.closeFuture().sync();
            serverValidationHandler.assertState();
            clientValidationHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(sslTaskExecutor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testAddressValidation(Executor executor) throws Throwable {
        // Bind to something so we can use the port to connect too and so can ensure we really timeout.
        DatagramSocket socket = new DatagramSocket();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor)
                .localConnectionIdLength(10));
        try {
            ChannelStateVerifyHandler verifyHandler = new ChannelStateVerifyHandler();
            Future<QuicChannel> future = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectWithCustomIdLength(Executor executor) throws Throwable {
        testConnectWithCustomIdLength(executor, 10, 5);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectWithCustomIdLengthOfZero(Executor executor) throws Throwable {
        testConnectWithCustomIdLength(executor, 0, 0);
    }

    private static void testConnectWithCustomIdLength(Executor executor, int clientIdLength, int serverIdLength)
            throws Throwable {
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        ChannelStateVerifyHandler serverQuicStreamHandler = new ChannelStateVerifyHandler();
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor)
                        .localConnectionIdLength(serverIdLength),
                TestQuicTokenHandler.INSTANCE, serverQuicChannelHandler, serverQuicStreamHandler);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor)
                .localConnectionIdLength(clientIdLength));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            assertTrue(quicChannel.close().await().isSuccess());
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
            assertEquals(clientIdLength, clientQuicChannelHandler.localAddress().id().remaining());
            assertEquals(serverIdLength, clientQuicChannelHandler.remoteAddress().id().remaining());
        } finally {
            serverQuicChannelHandler.assertState();
            assertEquals(serverIdLength, serverQuicChannelHandler.localAddress().id().remaining());
            assertEquals(clientIdLength, serverQuicChannelHandler.remoteAddress().id().remaining());
            serverQuicStreamHandler.assertState();

            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
            shutdown(executor);
        }
    }

    private void testConnectWithDroppedPackets(Executor executor, int numDroppedPackets,
                                               QuicConnectionIdGenerator connectionIdGenerator) throws Throwable {
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor)
                        .connectionIdAddressGenerator(connectionIdGenerator),
                NoQuicTokenHandler.INSTANCE,
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                },
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        // Server closes the stream whenever the client sends a FIN.
                        if (evt instanceof ChannelInputShutdownEvent) {
                            ctx.close();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }
                });

        // Have the server drop the few first numDroppedPackets incoming packets.
        server.pipeline().addFirst(
                new ChannelInboundHandlerAdapter() {
                    private int counter;

                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (counter++ < numDroppedPackets) {
                            System.out.println("Server dropping incoming packet #" + counter);
                            ReferenceCountUtil.release(msg);
                        } else {
                            ctx.fireChannelRead(msg);
                        }
                    }
                });

        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor));
        ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .remoteAddress(address)
                    .connect()
                    .get();

            QuicStreamChannel quicStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get();

            ByteBuf payload = Unpooled.wrappedBuffer("HELLO!".getBytes(StandardCharsets.US_ASCII));
            quicStream.writeAndFlush(payload).sync();
            quicStream.shutdownOutput().sync();
            assertTrue(quicStream.closeFuture().await().isSuccess());

            ChannelFuture closeFuture = channel.close().await();
            assertTrue(closeFuture.isSuccess());
        } finally {
            clientQuicChannelHandler.assertState();
            channel.close().sync();
            server.close().sync();
            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(3)
    public void testConnectWithNoDroppedPacketsAndRandomConnectionIdGenerator(Executor executor) throws Throwable {
        testConnectWithDroppedPackets(executor, 0, QuicConnectionIdGenerator.randomGenerator());
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(5)
    public void testConnectWithDroppedPacketsAndRandomConnectionIdGenerator(Executor executor) throws Throwable {
        testConnectWithDroppedPackets(executor, 2, QuicConnectionIdGenerator.randomGenerator());
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(3)
    public void testConnectWithNoDroppedPacketsAndSignConnectionIdGenerator(Executor executor) throws Throwable {
        testConnectWithDroppedPackets(executor, 0, QuicConnectionIdGenerator.signGenerator());
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(5)
    public void testConnectWithDroppedPacketsAndSignConnectionIdGenerator(Executor executor) throws Throwable {
        testConnectWithDroppedPackets(executor, 2, QuicConnectionIdGenerator.signGenerator());
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(5)
    public void testTimedOut(Executor executor) throws Throwable {
        final AtomicBoolean dropPackets = new AtomicBoolean();
        final BlockingQueue<QuicStreamChannel> acceptedStreams = new LinkedBlockingQueue<>();
        Channel server = QuicTestUtils.newServer(
                QuicTestUtils.newQuicServerBuilder(executor).maxIdleTimeout(1, TimeUnit.MILLISECONDS),
                NoQuicTokenHandler.INSTANCE,
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                },
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        acceptedStreams.add((QuicStreamChannel) ctx.channel());
                        dropPackets.set(true);
                        ctx.fireChannelActive();
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        // Server closes the stream whenever the client sends a FIN.
                        if (evt instanceof ChannelInputShutdownEvent) {
                            ctx.close();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }
                });

        // Have the server drop packets once we tell it to do so.
        server.pipeline().addFirst(
                new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (dropPackets.get()) {
                            ReferenceCountUtil.release(msg);
                        } else {
                            ctx.fireChannelRead(msg);
                        }
                    }

                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                        if (dropPackets.get()) {
                            ReferenceCountUtil.release(msg);
                            promise.setSuccess();
                        } else {
                            ctx.write(msg, promise);
                        }
                    }
                });

        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor));
        ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .remoteAddress(address)
                    .connect()
                    .get();

            QuicStreamChannel quicStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get();

            ByteBuf payload = Unpooled.wrappedBuffer("HELLO!".getBytes(StandardCharsets.US_ASCII));
            quicStream.writeAndFlush(payload).sync();
            assertTrue(quicStream.closeFuture().await().isSuccess());

            QuicStreamChannel accepted = acceptedStreams.take();
            accepted.closeFuture().sync();
            assertTrue(accepted.parent().isTimedOut());

            ChannelFuture closeFuture = channel.close().await();
            assertTrue(closeFuture.isSuccess());
        } finally {
            clientQuicChannelHandler.assertState();
            channel.close().sync();
            server.close().sync();
            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectTimeout(Executor executor) throws Throwable {
        // Bind to something so we can use the port to connect too and so can ensure we really timeout.
        DatagramSocket socket = new DatagramSocket();
        Channel channel = QuicTestUtils.newClient(executor);
        try {
            ChannelStateVerifyHandler verifyHandler = new ChannelStateVerifyHandler();
            Future<QuicChannel> future = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectFailsInParentPipeline(Executor executor) throws Throwable {
        // Bind to something so we can use the port to connect too and so can ensure we really timeout.
        DatagramSocket socket = new DatagramSocket();
        Channel channel = QuicTestUtils.newClient(executor);
        final Exception exception = new UnsupportedOperationException();
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                                ChannelPromise promise) {
                promise.setFailure(exception);
            }
        });
        try {
            ChannelStateVerifyHandler verifyHandler = new ChannelStateVerifyHandler();
            Future<QuicChannel> future = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(verifyHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(socket.getLocalSocketAddress())
                    .connect();
            Throwable cause = future.await().cause();
            assertSame(cause, exception);
            verifyHandler.assertState();
        } finally {
            socket.close();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectAlreadyConnected(Executor executor) throws Throwable {
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        ChannelStateVerifyHandler serverQuicStreamHandler = new ChannelStateVerifyHandler();

        Channel server = QuicTestUtils.newServer(executor, serverQuicChannelHandler, serverQuicStreamHandler);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(executor);
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectWithTokenValidation(Executor executor) throws Throwable {
        int numBytes = 8;
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        CountDownLatch serverLatch = new CountDownLatch(1);
        CountDownLatch clientLatch = new CountDownLatch(1);

        // Disable token validation
        Channel server = QuicTestUtils.newServer(executor, new QuicTokenHandler() {
                    @Override
                    public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
                        out.writeInt(0)
                                .writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes());
                        return true;
                    }

                    @Override
                    public int validateToken(ByteBuf token, InetSocketAddress address) {
                        // Use readInt() so we adjust the readerIndex of the token.
                        assertEquals(0, token.readInt());
                        return token.readerIndex();
                    }

                    @Override
                    public int maxTokenLength() {
                        return 96;
                    }
                },
                serverQuicChannelHandler, new BytesCountingHandler(serverLatch, numBytes));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(executor);
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            QuicheQuicSslEngine quicheQuicSslEngine = (QuicheQuicSslEngine) quicChannel.sslEngine();
            assertNotNull(quicheQuicSslEngine);
            assertEquals(QuicTestUtils.PROTOS[0],
                    // Just do the cast as getApplicationProtocol() only exists in SSLEngine itself since Java9+ and
                    // we may run on an earlier version
                    quicheQuicSslEngine.getApplicationProtocol());
            stream.close().sync();
            quicChannel.close().sync();
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());

            clientQuicChannelHandler.assertState();
            serverQuicChannelHandler.assertState();

            assertEquals(serverQuicChannelHandler.localAddress(), remoteAddress);
            assertEquals(serverQuicChannelHandler.remoteAddress(), localAddress);
        } finally {
            serverLatch.await();

            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectWithoutTokenValidation(Executor executor) throws Throwable {
        int numBytes = 8;
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        CountDownLatch serverLatch = new CountDownLatch(1);
        CountDownLatch clientLatch = new CountDownLatch(1);

        // Disable token validation
        Channel server = QuicTestUtils.newServer(executor, NoQuicTokenHandler.INSTANCE,
                serverQuicChannelHandler, new BytesCountingHandler(serverLatch, numBytes));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(executor);
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            QuicheQuicSslEngine quicheQuicSslEngine = (QuicheQuicSslEngine) quicChannel.sslEngine();
            assertNotNull(quicheQuicSslEngine);
            assertEquals(QuicTestUtils.PROTOS[0],
                    // Just do the cast as getApplicationProtocol() only exists in SSLEngine itself since Java9+ and
                    // we may run on an earlier version
                    quicheQuicSslEngine.getApplicationProtocol());
            stream.close().sync();
            quicChannel.close().sync();
            ChannelFuture closeFuture = quicChannel.closeFuture().await();
            assertTrue(closeFuture.isSuccess());

            clientQuicChannelHandler.assertState();
            serverQuicChannelHandler.assertState();

            assertEquals(serverQuicChannelHandler.localAddress(), remoteAddress);
            assertEquals(serverQuicChannelHandler.remoteAddress(), localAddress);
        } finally {
            serverLatch.await();

            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testKeyTypeChange(Executor executor) throws Throwable {
        final CountDownLatch readLatch = new CountDownLatch(1);
        Map<String, String> serverKeyTypes = new HashMap<>();
        serverKeyTypes.put("RSA", "RSA");

        Set<String> clientKeyTypes = new HashSet<>();
        clientKeyTypes.add("RSA");

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor,
                        QuicSslContextBuilder.forServer(
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                                .applicationProtocols(QuicTestUtils.PROTOS)
                                .option(BoringSSLContextOption.SERVER_KEY_TYPES, serverKeyTypes)
                                .earlyData(true)
                                .build()),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                }, new ByteToMessageDecoder() {
                    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
                        if (msg.readableBytes() < 4) {
                            return;
                        }
                        assertEquals(5, msg.readInt());
                        readLatch.countDown();
                        ctx.close();
                    }
                });

        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicTestUtils.PROTOS)
                .option(BoringSSLContextOption.CLIENT_KEY_TYPES, clientKeyTypes)
                .earlyData(true)
                .build();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor, sslContext)
                .sslEngineProvider(q -> sslContext.newEngine(q.alloc(), "localhost", 9999)));

        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).addListener(f -> {
                        Channel stream = (Channel) f.getNow();
                        stream.writeAndFlush(stream.alloc().buffer().writeInt(5));
            }).await().addListener(f -> {
                assertTrue(f.isSuccess());
            });

            readLatch.await();
        } finally {
            server.close().sync();
            channel.close().sync();
            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testKeyTypeChangeFail(Executor executor) throws Throwable {
        Map<String, String> serverKeyTypes = new HashMap<>();
        serverKeyTypes.put("ECDHE_ECDSA", "EdDSA");

        Set<String> clientKeyTypes = new HashSet<>();
        clientKeyTypes.add("EdDSA");

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor,
                        QuicSslContextBuilder.forServer(
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                                .applicationProtocols(QuicTestUtils.PROTOS)
                                .option(BoringSSLContextOption.SERVER_KEY_TYPES, serverKeyTypes)
                                .earlyData(true)
                                .build()),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());

        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicTestUtils.PROTOS)
                .option(BoringSSLContextOption.CLIENT_KEY_TYPES, clientKeyTypes)
                .earlyData(true)
                .build();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor, sslContext)
                .sslEngineProvider(q -> sslContext.newEngine(q.alloc(), "localhost", 9999)));

        try {
            assertThrows(ExecutionException.class, () -> {
                QuicTestUtils.newQuicChannelBootstrap(channel)
                                .streamHandler(new ChannelInboundHandlerAdapter())
                                .remoteAddress(address)
                                .connect()
                                .get();
            });
        } finally {
            server.close().sync();
            channel.close().sync();
            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectWith0RTT(Executor executor) throws Throwable {
        final CountDownLatch readLatch = new CountDownLatch(1);
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor,
                        QuicSslContextBuilder.forServer(
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                                .applicationProtocols(QuicTestUtils.PROTOS)
                                .earlyData(true)
                                .build()),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                }, new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf buffer = (ByteBuf) msg;
                        try {
                            assertEquals(4, buffer.readableBytes());
                            assertEquals(1, buffer.readInt());
                            readLatch.countDown();
                            ctx.close();
                            ctx.channel().parent().close();
                        } finally {
                            buffer.release();
                        }
                    }
                });
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicTestUtils.PROTOS)
                .earlyData(true)
                .build();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor, sslContext)
                .sslEngineProvider(q -> sslContext.newEngine(q.alloc(), "localhost", 9999)));
        final CountDownLatch activeLatch = new CountDownLatch(1);
        final CountDownLatch eventLatch = new CountDownLatch(1);
        final CountDownLatch streamLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt instanceof SslEarlyDataReadyEvent) {
                                errorRef.set(new AssertionFailedError("Shouldn't be called on the first connection"));
                            }
                            ctx.fireUserEventTriggered(evt);
                        }
                    })
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();

            QuicClientSessionCache cache = ((QuicheQuicSslContext) sslContext).getSessionCache();

            // Let's spin until the session shows up in the cache. This is needed as this might happen a bit after
            // the connection is already established.
            // See https://commondatastorage.googleapis.com/chromium-boringssl-docs/ssl.h.html#SSL_CTX_sess_set_new_cb
            while (!cache.hasSession("localhost", 9999)) {
                // Check again in 100ms.
                Thread.sleep(100);
            }

            quicChannel.close().sync();

            if (errorRef.get() != null) {
                throw errorRef.get();
            }

            quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            activeLatch.countDown();
                            ctx.fireChannelActive();
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt instanceof SslEarlyDataReadyEvent) {
                                eventLatch.countDown();
                                ((QuicChannel) ctx.channel()).createStream(QuicStreamType.BIDIRECTIONAL,
                                        new ChannelInboundHandlerAdapter()).addListener(f -> {
                                    try {
                                        // This should succeed as we have the transport params cached as part of
                                        // the session.
                                        assertTrue(f.isSuccess());
                                        Channel stream = (Channel) f.getNow();

                                        // Let's write some data as part of the client hello.
                                        stream.writeAndFlush(stream.alloc().buffer().writeInt(1));
                                    } catch (Throwable error) {
                                        errorRef.set(error);
                                    } finally {
                                        streamLatch.countDown();
                                    }
                                });
                            }
                            ctx.fireUserEventTriggered(evt);
                        }
                    })
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();

            awaitAndCheckError(activeLatch, errorRef);
            awaitAndCheckError(eventLatch, errorRef);
            awaitAndCheckError(streamLatch, errorRef);

            quicChannel.closeFuture().sync();
            readLatch.await();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    private static void awaitAndCheckError(CountDownLatch latch, AtomicReference<Throwable> errorRef) throws Throwable {
        while (!latch.await(500, TimeUnit.MILLISECONDS)) {
            if (errorRef.get() != null) {
                throw errorRef.get();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectAndStreamPriority(Executor executor) throws Throwable {
        int numBytes = 8;
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        CountDownLatch serverLatch = new CountDownLatch(1);
        CountDownLatch clientLatch = new CountDownLatch(1);

        Channel server = QuicTestUtils.newServer(executor, serverQuicChannelHandler,
                new BytesCountingHandler(serverLatch, numBytes));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(executor);
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectMultipleTimes(Executor executor) throws Throwable {
        Channel server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, QuicTestUtils.NOOP_HANDLER);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(executor);
        try {
            QuicChannelBootstrap cb = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(QuicTestUtils.NOOP_HANDLER)
                    .streamHandler(QuicTestUtils.NOOP_HANDLER)
                    .remoteAddress(address);
            List<QuicChannel> channels = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                channels.add(cb
                        .connect()
                        .get());
            }

            for (QuicChannel ch : channels) {
                ch.close().sync();
            }
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testExtendedTrustManagerFailureOnTheClient(Executor executor) throws Throwable {
        testTrustManagerFailureOnTheClient(executor, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testTrustManagerFailureOnTheClient(Executor executor) throws Throwable {
        testTrustManagerFailureOnTheClient(executor, false);
    }

    private void testTrustManagerFailureOnTheClient(Executor executor, boolean extended) throws Throwable {
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
        Channel server = QuicTestUtils.newServer(executor, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor,
                QuicSslContextBuilder.forClient()
                        .trustManager(new TrustManagerFactoryWrapper(trustManager))
                        .applicationProtocols(QuicTestUtils.PROTOS).build()));
        try {
            Throwable cause = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testALPNProtocolMissmatch(Executor executor) throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch eventLatch = new CountDownLatch(1);

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor,
                        QuicSslContextBuilder.forServer(
                                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                                .applicationProtocols("my-protocol").build()),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {

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
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor,
                QuicSslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols("protocol").build()));
        AtomicReference<QuicConnectionCloseEvent> closeEventRef = new AtomicReference<>();
        try {
            Throwable cause = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt instanceof QuicConnectionCloseEvent) {
                                closeEventRef.set((QuicConnectionCloseEvent) evt);
                            }
                            super.userEventTriggered(ctx, evt);
                        }
                    })
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .await().cause();
            assertThat(cause, Matchers.instanceOf(ClosedChannelException.class));
            latch.await();
            eventLatch.await();
            QuicConnectionCloseEvent closeEvent = closeEventRef.get();
            assertNotNull(closeEvent);
            assertTrue(closeEvent.isTlsError());
            // 120 is the ALPN error.
            // See https://datatracker.ietf.org/doc/html/rfc8446#section-6
            assertEquals(120, QuicConnectionCloseEvent.extractTlsError(closeEvent.error()));
            assertEquals(closeEvent, ((QuicClosedChannelException) cause).event());
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectSuccessWhenTrustManagerBuildFromSameCert(Executor executor) throws Throwable {
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor,
                        QuicSslContextBuilder.forServer(
                                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                                .applicationProtocols(QuicTestUtils.PROTOS).clientAuth(ClientAuth.NONE).build()),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor,
                QuicSslContextBuilder.forClient()
                        .trustManager(QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                        .applicationProtocols(QuicTestUtils.PROTOS).build()));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectMutualAuthRequiredSuccess(Executor executor) throws Throwable {
        testConnectMutualAuthSuccess(executor, MutalAuthTestMode.REQUIRED);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectMutualAuthOptionalWithCertSuccess(Executor executor) throws Throwable {
        testConnectMutualAuthSuccess(executor, MutalAuthTestMode.OPTIONAL_CERT);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectMutualAuthOptionalWithoutKeyManagerSuccess(Executor executor) throws Throwable {
        testConnectMutualAuthSuccess(executor, MutalAuthTestMode.OPTIONAL_NO_KEYMANAGER);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectMutualAuthOptionalWithoutKeyInKeyManagerSuccess(Executor executor) throws Throwable {
        testConnectMutualAuthSuccess(executor, MutalAuthTestMode.OPTIONAL_NO_KEY_IN_KEYMANAGER);
    }

    private void testConnectMutualAuthSuccess(Executor executor, MutalAuthTestMode mode) throws Throwable {
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor,
                        QuicSslContextBuilder.forServer(
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate()).trustManager(
                                        InsecureTrustManagerFactory.INSTANCE)
                                .applicationProtocols(QuicTestUtils.PROTOS)
                                .clientAuth(mode == MutalAuthTestMode.REQUIRED ?
                                        ClientAuth.REQUIRE : ClientAuth.OPTIONAL).build()),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        QuicSslContextBuilder clientSslCtxBuilder = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicTestUtils.PROTOS);
        switch (mode) {
            case OPTIONAL_CERT:
            case REQUIRED:
                clientSslCtxBuilder.keyManager(
                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate());
                break;
            case OPTIONAL_NO_KEY_IN_KEYMANAGER:
                clientSslCtxBuilder.keyManager(new X509ExtendedKeyManager() {
                    @Override
                    public String[] getClientAliases(String keyType, Principal[] issuers) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    @Nullable
                    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                        return null;
                    }

                    @Override
                    public String[] getServerAliases(String keyType, Principal[] issuers) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public X509Certificate[] getCertificateChain(String alias) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public PrivateKey getPrivateKey(String alias) {
                        throw new UnsupportedOperationException();
                    }
                }, null);
                break;
            case OPTIONAL_NO_KEYMANAGER:
                break;
            default:
                throw new IllegalStateException();
        }

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor,
                clientSslCtxBuilder.build()));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    private enum MutalAuthTestMode {
        REQUIRED,
        OPTIONAL_CERT,
        OPTIONAL_NO_KEYMANAGER,
        OPTIONAL_NO_KEY_IN_KEYMANAGER
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectMutualAuthFailsIfClientNotSendCertificate(Executor executor) throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor,
                        QuicSslContextBuilder.forServer(
                                QuicTestUtils.SELF_SIGNED_CERTIFICATE.privateKey(), null,
                                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate())
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .applicationProtocols(QuicTestUtils.PROTOS).clientAuth(ClientAuth.REQUIRE).build()),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        causeRef.compareAndSet(null, cause);
                        latch.countDown();
                        ctx.close();
                    }
                },
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor,
                QuicSslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .applicationProtocols(QuicTestUtils.PROTOS).build()));
        QuicChannel client = null;
        try {
            client  = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            cause.printStackTrace();
                        }
                    })
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            latch.await();

            assertThat(causeRef.get(), Matchers.instanceOf(SSLHandshakeException.class));
        } finally {
            server.close().sync();

            if (client != null) {
                client.close().sync();
            }
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testSniMatch(Executor executor) throws Throwable {
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

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor, serverSslContext),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {
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

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor)
                .sslEngineProvider(c -> clientSslContext.newEngine(c.alloc(), hostname, 8080)));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testSniFallbackToDefault(Executor executor) throws Throwable {
        testSniFallbackToDefault(executor, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testNoSniFallbackToDefault(Executor executor) throws Throwable {
        testSniFallbackToDefault(executor, false);
    }

    private void testSniFallbackToDefault(Executor executor, boolean sendSni) throws Throwable {
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

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor, serverSslContext),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter(),
                new ChannelInboundHandlerAdapter());

        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols("default-protocol").build();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor)
                .sslEngineProvider(c -> {
                    if (sendSni) {
                        return clientSslContext.newEngine(c.alloc(), "netty.io", 8080);
                    } else {
                        return clientSslContext.newEngine(c.alloc());
                    }
                }));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectKeyless(Executor executor) throws Throwable {
        testConnectKeyless0(executor, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testConnectKeylessSignFailure(Executor executor) throws Throwable {
        testConnectKeyless0(executor, true);
    }

    public void testConnectKeyless0(Executor executor, boolean fail) throws Throwable {
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        AtomicBoolean signCalled = new AtomicBoolean();
        BoringSSLAsyncPrivateKeyMethod keyMethod = new BoringSSLAsyncPrivateKeyMethod() {
            @Override
            public Future<byte[]> sign(SSLEngine engine, int signatureAlgorithm, byte[] input) {
                signCalled.set(true);

                assertEquals(QuicTestUtils.SELF_SIGNED_CERTIFICATE.cert().getPublicKey(),
                        engine.getSession().getLocalCertificates()[0].getPublicKey());

                try {
                    if (fail) {
                        return ImmediateEventExecutor.INSTANCE.newFailedFuture(new SignatureException());
                    }
                    // Delegate signing to Java implementation.
                    final Signature signature;
                    // Depending on the Java version it will pick one or the other.
                    if (signatureAlgorithm == BoringSSLAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA256) {
                        signature = Signature.getInstance("SHA256withRSA");
                    } else if (signatureAlgorithm == BoringSSLAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA256) {
                        signature = Signature.getInstance("RSASSA-PSS");
                        signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                                32, 1));
                    } else {
                        throw new AssertionError("Unexpected signature algorithm " + signatureAlgorithm);
                    }
                    signature.initSign(QuicTestUtils.SELF_SIGNED_CERTIFICATE.key());
                    signature.update(input);
                    return ImmediateEventExecutor.INSTANCE.newSucceededFuture(signature.sign());
                } catch (Throwable cause) {
                    return ImmediateEventExecutor.INSTANCE.newFailedFuture(cause);
                }
            }

            @Override
            public Future<byte[]> decrypt(SSLEngine engine, byte[] input) {
                throw new UnsupportedOperationException();
            }
        };

        BoringSSLKeylessManagerFactory factory = BoringSSLKeylessManagerFactory.newKeyless(
                keyMethod, QuicTestUtils.SELF_SIGNED_CERTIFICATE.certificate());
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor,
                        QuicSslContextBuilder.forServer(factory, null)
                                .applicationProtocols(QuicTestUtils.PROTOS).clientAuth(ClientAuth.NONE).build()),
                TestQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        causeRef.set(cause);
                    }
                } ,
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor,
                QuicSslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .applicationProtocols(QuicTestUtils.PROTOS).build()));
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            Future<QuicChannel> connectFuture = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientQuicChannelHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect().await();
            if (fail) {
                assertThat(connectFuture.cause(), Matchers.instanceOf(ClosedChannelException.class));
                assertThat(causeRef.get(), Matchers.instanceOf(SSLHandshakeException.class));
            } else {
                QuicChannel quicChannel = connectFuture.get();
                assertTrue(quicChannel.close().await().isSuccess());
                ChannelFuture closeFuture = quicChannel.closeFuture().await();
                assertTrue(closeFuture.isSuccess());
                clientQuicChannelHandler.assertState();
                assertNull(causeRef.get());
            }
            assertTrue(signCalled.get());
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testSessionTickets(Executor executor) throws Throwable {
        testSessionReuse(executor, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(5)
    public void testSessionReusedOnClientSide(Executor executor) throws Exception {
        testSessionReuse(executor, false);
    }

    private static void testSessionReuse(Executor executor, boolean ticketKey) throws Exception {
        QuicSslContext sslServerCtx = QuicSslContextBuilder.forServer(
                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.key(), null,
                        QuicTestUtils.SELF_SIGNED_CERTIFICATE.cert())
                .applicationProtocols(QuicTestUtils.PROTOS)
                .build();
        QuicSslContext sslClientCtx = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols(QuicTestUtils.PROTOS).build();

        if (ticketKey) {

            SslSessionTicketKey key = new SslSessionTicketKey(new byte[SslSessionTicketKey.NAME_SIZE],
                    new byte[SslSessionTicketKey.HMAC_KEY_SIZE], new byte[SslSessionTicketKey.AES_KEY_SIZE]);
            sslClientCtx.sessionContext().setTicketKeys(key);
            sslServerCtx.sessionContext().setTicketKeys(key);
        }
        CountDownLatch serverSslCompletionEventLatch = new CountDownLatch(2);
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor, sslServerCtx),
                TestQuicTokenHandler.INSTANCE,
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

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor).sslEngineProvider(c ->
                sslClientCtx.newEngine(c.alloc(), "localhost", 9999)));
        try {
            CountDownLatch clientSslCompletionEventLatch = new CountDownLatch(2);

            QuicChannelBootstrap bootstrap = QuicTestUtils.newQuicChannelBootstrap(channel)
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

            shutdown(executor);
        }
    }

    private static void assertSessionReused(QuicChannel channel, boolean reused) throws Exception {
        QuicheQuicSslEngine engine =  (QuicheQuicSslEngine) channel.sslEngine();
        assertNotNull(engine);
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
            super.channelActive(ctx);
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
