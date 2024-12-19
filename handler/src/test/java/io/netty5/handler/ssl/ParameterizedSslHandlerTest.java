/*
 * Copyright 2017 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.CompositeBuffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalIoHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import io.netty5.util.Resource;
import io.netty5.util.Send;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.EmptyArrays;
import io.netty5.util.internal.ResourcesUtil;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty5.buffer.BufferUtil.writeAscii;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterizedSslHandlerTest {

    private static final String PARAMETERIZED_NAME = "{index}: clientProvider={0}, {index}: serverProvider={1}";
    private static final X509Bundle CERT;

    static {
        try {
            CERT = new CertificateBuilder()
                    .subject("cn=localhost")
                    .setIsCertificateAuthority(true)
                    .buildSelfSigned();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Collection<Object[]> data() {
        List<SslProvider> providers = new ArrayList<>(3);
        if (OpenSsl.isAvailable()) {
            providers.add(SslProvider.OPENSSL);
            providers.add(SslProvider.OPENSSL_REFCNT);
        }
        providers.add(SslProvider.JDK);

        List<Object[]> params = new ArrayList<>();

        for (SslProvider cp: providers) {
            for (SslProvider sp: providers) {
                params.add(new Object[] { cp, sp });
            }
        }
        return params;
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 48000, unit = TimeUnit.MILLISECONDS)
    public void testCompositeBufSizeEstimationGuaranteesSynchronousWrite(
            SslProvider clientProvider, SslProvider serverProvider)
            throws Exception {
        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                true, true, true);
        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                true, true, false);
        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                true, false, true);
        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                true, false, false);
        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                false, true, true);
        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                false, true, false);
        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                false, false, true);
        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                false, false, false);
    }

    private static void compositeBufSizeEstimationGuaranteesSynchronousWrite(
            SslProvider serverProvider, SslProvider clientProvider,
            final boolean serverDisableWrapSize,
            final boolean letHandlerCreateServerEngine, final boolean letHandlerCreateClientEngine)
            throws Exception {
        final SslContext sslServerCtx = SslContextBuilder.forServer(CERT.getKeyPair().getPrivate(),
                        CERT.getCertificatePath())
                .sslProvider(serverProvider)
                .build();

        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(clientProvider).build();

        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        Channel sc = null;
        Channel cc = null;
        try {
            final Promise<Void> donePromise = group.next().newPromise();
            // The goal is to provide the SSLEngine with many Buffer components to ensure that the overhead for wrap
            // is correctly accounted for on each component.
            final int numComponents = 150;
            // This is the TLS packet size. The goal is to divide the maximum amount of application data that can fit
            // into a single TLS packet into many components to ensure the overhead is correctly taken into account.
            final int desiredBytes = 16384;
            final int singleComponentSize = desiredBytes / numComponents;
            final int expectedBytes = numComponents * singleComponentSize;

            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            final SslHandler handler = letHandlerCreateServerEngine
                                    ? sslServerCtx.newHandler(ch.bufferAllocator())
                                    : new SslHandler(sslServerCtx.newEngine(ch.bufferAllocator()));
                            if (serverDisableWrapSize) {
                                handler.setWrapDataSize(-1);
                            }
                            ch.pipeline().addLast(handler);
                            ch.pipeline().addLast(new ChannelHandler() {
                                private boolean sentData;
                                private Throwable writeCause;

                                @Override
                                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof SslHandshakeCompletionEvent) {
                                        SslHandshakeCompletionEvent sslEvt = (SslHandshakeCompletionEvent) evt;
                                        if (sslEvt.isSuccess()) {
                                            List<Send<Buffer>> components = new ArrayList<>(numComponents);
                                            for (int i = 0; i < numComponents; ++i) {
                                                Buffer buf = ctx.bufferAllocator().allocate(singleComponentSize);
                                                buf.skipWritableBytes(singleComponentSize);
                                                components.add(buf.send());
                                            }
                                            CompositeBuffer content = ctx.bufferAllocator().compose(components);
                                            ctx.writeAndFlush(content).addListener(future -> {
                                                writeCause = future.cause();
                                                if (writeCause == null) {
                                                    sentData = true;
                                                }
                                            });
                                        } else {
                                            donePromise.tryFailure(sslEvt.cause());
                                        }
                                    }
                                    ctx.fireChannelInboundEvent(evt);
                                }

                                @Override
                                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    donePromise.tryFailure(new IllegalStateException(
                                            "server exception sentData: " + sentData + " writeCause: " + writeCause,
                                            cause));
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    donePromise.tryFailure(new IllegalStateException(
                                            "server closed sentData: " + sentData + " writeCause: " + writeCause));
                                }
                            });
                        }
                    }).bind(new InetSocketAddress(0)).asStage().get();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            if (letHandlerCreateClientEngine) {
                                ch.pipeline().addLast(sslClientCtx.newHandler(ch.bufferAllocator()));
                            } else {
                                ch.pipeline().addLast(new SslHandler(sslClientCtx.newEngine(ch.bufferAllocator())));
                            }
                            ch.pipeline().addLast(new ChannelHandler() {
                                private int bytesSeen;

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof Buffer) {
                                        bytesSeen += ((Buffer) msg).readableBytes();
                                        if (bytesSeen == expectedBytes) {
                                            donePromise.trySuccess(null);
                                        }
                                    }
                                    Resource.dispose(msg);
                                }

                                @Override
                                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof SslHandshakeCompletionEvent) {
                                        SslHandshakeCompletionEvent sslEvt = (SslHandshakeCompletionEvent) evt;
                                        if (!sslEvt.isSuccess()) {
                                            donePromise.tryFailure(sslEvt.cause());
                                        }
                                    }
                                }

                                @Override
                                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    donePromise.tryFailure(new IllegalStateException("client exception. bytesSeen: " +
                                                                                     bytesSeen, cause));
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    donePromise.tryFailure(new IllegalStateException("client closed. bytesSeen: " +
                                                                                     bytesSeen));
                                }
                            });
                        }
                    }).connect(sc.localAddress()).asStage().get();

            donePromise.asFuture().asStage().sync();
        } finally {
            if (cc != null) {
                cc.close().asStage().sync();
            }
            if (sc != null) {
                sc.close().asStage().sync();
            }
            group.shutdownGracefully();

            Resource.dispose(sslServerCtx);
            Resource.dispose(sslClientCtx);
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testAlertProducedAndSend(SslProvider clientProvider, SslProvider serverProvider) throws Exception {
        final SslContext sslServerCtx = SslContextBuilder.forServer(CERT.getKeyPair().getPrivate(),
                        CERT.getCertificatePath())
                .sslProvider(serverProvider)
                .trustManager(new SimpleTrustManagerFactory() {
                    @Override
                    protected void engineInit(KeyStore keyStore) { }
                    @Override
                    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) { }

                    @Override
                    protected TrustManager[] engineGetTrustManagers() {
                        return new TrustManager[] { new X509TrustManager() {

                            @Override
                            public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                                    throws CertificateException {
                                // Fail verification which should produce an alert that is send back to the client.
                                throw new CertificateException();
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
                                // NOOP
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return EmptyArrays.EMPTY_X509_CERTIFICATES;
                            }
                        } };
                    }
                }).clientAuth(ClientAuth.REQUIRE).build();

        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .keyManager(ResourcesUtil.getFile(getClass(),  "test.crt"),
                        ResourcesUtil.getFile(getClass(), "test_unencrypted.pem"))
                .sslProvider(clientProvider).build();

        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        Channel sc = null;
        Channel cc = null;
        try {
            final Promise<Void> promise = group.next().newPromise();
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(sslServerCtx.newHandler(ch.bufferAllocator()));
                            ch.pipeline().addLast(new ChannelHandler() {
                                @Override
                                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    // Just trigger a close
                                    ctx.close();
                                }
                            });
                        }
                    }).bind(new InetSocketAddress(0)).asStage().get();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(sslClientCtx.newHandler(ch.bufferAllocator()));
                            ch.pipeline().addLast(new ChannelHandler() {
                                @Override
                                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    if (cause.getCause() instanceof SSLException) {
                                        // We received the alert and so produce an SSLException.
                                        promise.trySuccess(null);
                                    }
                                }
                            });
                        }
                    }).connect(sc.localAddress()).asStage().get();

            promise.asFuture().asStage().sync();
        } finally {
            if (cc != null) {
                cc.close().asStage().sync();
            }
            if (sc != null) {
                sc.close().asStage().sync();
            }
            group.shutdownGracefully();

            Resource.dispose(sslServerCtx);
            Resource.dispose(sslClientCtx);
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testCloseNotify(SslProvider clientProvider, SslProvider serverProvider) throws Exception {
        testCloseNotify(clientProvider, serverProvider, 5000, false);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testCloseNotifyReceivedTimeout(SslProvider clientProvider, SslProvider serverProvider)
            throws Exception {
        testCloseNotify(clientProvider, serverProvider, 100, true);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testCloseNotifyNotWaitForResponse(SslProvider clientProvider, SslProvider serverProvider)
            throws Exception {
        testCloseNotify(clientProvider, serverProvider, 0, false);
    }

    private static void testCloseNotify(SslProvider clientProvider, SslProvider serverProvider,
                                        final long closeNotifyReadTimeout, final boolean timeout) throws Exception {
        final SslContext sslServerCtx = SslContextBuilder.forServer(CERT.getKeyPair().getPrivate(),
                        CERT.getCertificatePath())
                                                         .sslProvider(serverProvider)
                                                         // Use TLSv1.2 as we depend on the fact that the handshake
                                                         // is done in an extra round trip in the test which
                                                         // is not true in TLSv1.3
                                                         .protocols(SslProtocols.TLS_v1_2)
                                                         .build();

        final SslContext sslClientCtx = SslContextBuilder.forClient()
                                                         .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                                         .sslProvider(clientProvider)
                                                         // Use TLSv1.2 as we depend on the fact that the handshake
                                                         // is done in an extra round trip in the test which
                                                         // is not true in TLSv1.3
                                                         .protocols(SslProtocols.TLS_v1_2)
                                                         .build();

        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        Channel sc = null;
        Channel cc = null;
        try {
            final Promise<Channel> clientPromise = group.next().newPromise();
            final Promise<Channel> serverPromise = group.next().newPromise();

            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            SslHandler handler = sslServerCtx.newHandler(ch.bufferAllocator());
                            handler.setCloseNotifyReadTimeoutMillis(closeNotifyReadTimeout);
                            handler.sslCloseFuture().cascadeTo(serverPromise);

                            handler.handshakeFuture().addListener(future -> {
                                if (future.isFailed()) {
                                    // Something bad happened during handshake fail the promise!
                                    serverPromise.tryFailure(future.cause());
                                }
                            });
                            ch.pipeline().addLast(handler);
                        }
                    }).bind(new InetSocketAddress(0)).asStage().get();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            final AtomicBoolean closeSent = new AtomicBoolean();
                            if (timeout) {
                                ch.pipeline().addFirst(new ChannelHandler() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        if (closeSent.get()) {
                                            // Drop data on the floor so we will get a timeout while waiting for the
                                            // close_notify.
                                            Resource.dispose(msg);
                                        } else {
                                            ctx.fireChannelRead(msg);
                                        }
                                    }
                                });
                            }

                            SslHandler handler = sslClientCtx.newHandler(ch.bufferAllocator());
                            handler.setCloseNotifyReadTimeoutMillis(closeNotifyReadTimeout);
                            handler.sslCloseFuture().cascadeTo(clientPromise);
                            handler.handshakeFuture().addListener(future -> {
                                if (future.isSuccess()) {
                                    closeSent.compareAndSet(false, true);
                                    future.getNow().close();
                                } else {
                                    // Something bad happened during handshake fail the promise!
                                    clientPromise.tryFailure(future.cause());
                                }
                            });
                            ch.pipeline().addLast(handler);
                        }
                    }).connect(sc.localAddress()).asStage().get();

            serverPromise.asFuture().asStage().await();
            clientPromise.asFuture().asStage().await();

            // Server always received the close_notify as the client triggers the close sequence.
            assertTrue(serverPromise.isSuccess());

            // Depending on if we wait for the response or not the promise will be failed or not.
            if (closeNotifyReadTimeout > 0 && !timeout) {
                assertTrue(clientPromise.isSuccess());
            } else {
                assertFalse(clientPromise.isSuccess());
            }
        } finally {
            if (cc != null) {
                cc.close().asStage().sync();
            }
            if (sc != null) {
                sc.close().asStage().sync();
            }
            group.shutdownGracefully();

            Resource.dispose(sslServerCtx);
            Resource.dispose(sslClientCtx);
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void reentryOnHandshakeCompleteNioChannel(SslProvider clientProvider, SslProvider serverProvider)
            throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        try {
            Class<? extends ServerChannel> serverClass = NioServerSocketChannel.class;
            Class<? extends Channel> clientClass = NioSocketChannel.class;
            SocketAddress bindAddress = new InetSocketAddress(0);
            reentryOnHandshakeComplete(clientProvider, serverProvider, group, bindAddress,
                    serverClass, clientClass, false, false);
            reentryOnHandshakeComplete(clientProvider, serverProvider, group, bindAddress,
                    serverClass, clientClass, false, true);
            reentryOnHandshakeComplete(clientProvider, serverProvider, group, bindAddress,
                    serverClass, clientClass, true, false);
            reentryOnHandshakeComplete(clientProvider, serverProvider, group, bindAddress,
                    serverClass, clientClass, true, true);
        } finally {
            group.shutdownGracefully();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void reentryOnHandshakeCompleteLocalChannel(SslProvider clientProvider, SslProvider serverProvider)
            throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(LocalIoHandler.newFactory());
        try {
            Class<? extends ServerChannel> serverClass = LocalServerChannel.class;
            Class<? extends Channel> clientClass = LocalChannel.class;
            SocketAddress bindAddress = new LocalAddress(getClass());
            reentryOnHandshakeComplete(clientProvider, serverProvider, group, bindAddress,
                    serverClass, clientClass, false, false);
            reentryOnHandshakeComplete(clientProvider, serverProvider, group, bindAddress,
                    serverClass, clientClass, false, true);
            reentryOnHandshakeComplete(clientProvider, serverProvider, group, bindAddress,
                    serverClass, clientClass, true, false);
            reentryOnHandshakeComplete(clientProvider, serverProvider, group, bindAddress,
                    serverClass, clientClass, true, true);
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void reentryOnHandshakeComplete(SslProvider clientProvider, SslProvider serverProvider,
                                                   EventLoopGroup group, SocketAddress bindAddress,
                                                   Class<? extends ServerChannel> serverClass,
                                                   Class<? extends Channel> clientClass, boolean serverAutoRead,
                                                   boolean clientAutoRead) throws Exception {
        final SslContext sslServerCtx = SslContextBuilder.forServer(CERT.getKeyPair().getPrivate(),
                        CERT.getCertificatePath())
                .sslProvider(serverProvider)
                .build();

        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(clientProvider)
                .build();

        Channel sc = null;
        Channel cc = null;
        try {
            final String expectedContent = "HelloWorld";
            final CountDownLatch serverLatch = new CountDownLatch(1);
            final CountDownLatch clientLatch = new CountDownLatch(1);
            final StringBuilder serverQueue = new StringBuilder(expectedContent.length());
            final StringBuilder clientQueue = new StringBuilder(expectedContent.length());

            sc = new ServerBootstrap()
                    .group(group)
                    .channel(serverClass)
                    .childOption(ChannelOption.AUTO_READ, serverAutoRead)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(
                                    disableHandshakeTimeout(sslServerCtx.newHandler(ch.bufferAllocator())));
                            ch.pipeline().addLast(new ReentryWriteSslHandshakeHandler(expectedContent, serverQueue,
                                                                                      serverLatch));
                        }
                    }).bind(bindAddress).asStage().get();

            cc = new Bootstrap()
                    .group(group)
                    .channel(clientClass)
                    .option(ChannelOption.AUTO_READ, clientAutoRead)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(
                                    disableHandshakeTimeout(sslClientCtx.newHandler(ch.bufferAllocator())));
                            ch.pipeline().addLast(new ReentryWriteSslHandshakeHandler(expectedContent, clientQueue,
                                                                                      clientLatch));
                        }
                    }).connect(sc.localAddress()).asStage().get();

            serverLatch.await();
            assertEquals(expectedContent, serverQueue.toString());
            clientLatch.await();
            assertEquals(expectedContent, clientQueue.toString());
        } finally {
            if (cc != null) {
                cc.close().asStage().sync();
            }
            if (sc != null) {
                sc.close().asStage().sync();
            }

            Resource.dispose(sslServerCtx);
            Resource.dispose(sslClientCtx);
        }
    }

    private static SslHandler disableHandshakeTimeout(SslHandler handler) {
        handler.setHandshakeTimeoutMillis(0);
        return handler;
    }

    private static final class ReentryWriteSslHandshakeHandler extends SimpleChannelInboundHandler<Buffer> {
        private static final Logger LOGGER = LoggerFactory.getLogger(ReentryWriteSslHandshakeHandler.class);
        private final String toWrite;
        private final StringBuilder readQueue;
        private final CountDownLatch doneLatch;

        ReentryWriteSslHandshakeHandler(String toWrite, StringBuilder readQueue, CountDownLatch doneLatch) {
            this.toWrite = toWrite;
            this.readQueue = readQueue;
            this.doneLatch = doneLatch;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // Write toWrite in two chunks, first here then we get SslHandshakeCompletionEvent (which is re-entry).
            ctx.writeAndFlush(writeAscii(ctx.bufferAllocator(), toWrite.substring(0, toWrite.length() / 2)));
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, Buffer msg) {
            readQueue.append(msg.toString(StandardCharsets.US_ASCII));
            if (readQueue.length() >= toWrite.length()) {
                doneLatch.countDown();
            }
        }

        @Override
        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof SslHandshakeCompletionEvent) {
                SslHandshakeCompletionEvent sslEvt = (SslHandshakeCompletionEvent) evt;
                if (sslEvt.isSuccess()) {
                    // this is the re-entry write, it should be ordered after the subsequent write.
                    ctx.writeAndFlush(writeAscii(ctx.bufferAllocator(), toWrite.substring(toWrite.length() / 2)));
                } else {
                    appendError(sslEvt.cause());
                }
            }
            ctx.fireChannelInboundEvent(evt);
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            appendError(cause);
            ctx.fireChannelExceptionCaught(cause);
        }

        private void appendError(Throwable cause) {
            LOGGER.error("Caught possible write failure in ParameterizedSslHandlerTest.", cause);
            readQueue.append("failed to write '").append(toWrite).append("': ");

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                cause.printStackTrace(new PrintStream(out));
                readQueue.append(out.toString(StandardCharsets.US_ASCII));
            } catch (IOException ignore) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        }
    }
}

