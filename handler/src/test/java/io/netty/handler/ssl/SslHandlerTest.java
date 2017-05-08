/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.EmptyArrays;
import org.junit.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class SslHandlerTest {

    @Test
    public void testTruncatedPacket() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);

        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        // Push the first part of a 5-byte handshake message.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{22, 3, 1, 0, 5}));

        // Should decode nothing yet.
        assertThat(ch.readInbound(), is(nullValue()));

        try {
            // Push the second part of the 5-byte handshake message.
            ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{2, 0, 0, 1, 0}));
            fail();
        } catch (DecoderException e) {
            // Be sure we cleanup the channel and release any pending messages that may have been generated because
            // of an alert.
            // See https://github.com/netty/netty/issues/6057.
            ch.finishAndReleaseAll();

            // The pushed message is invalid, so it should raise an exception if it decoded the message correctly.
            assertThat(e.getCause(), is(instanceOf(SSLProtocolException.class)));
        }
    }

    @Test(expected = UnsupportedMessageTypeException.class)
    public void testNonByteBufNotPassThrough() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);

        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        try {
            ch.writeOutbound(new Object());
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testReleaseSslEngine() throws Exception {
        assumeTrue(OpenSsl.isAvailable());

        SelfSignedCertificate cert = new SelfSignedCertificate();
        try {
            SslContext sslContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .sslProvider(SslProvider.OPENSSL)
                .build();
            try {
                SSLEngine sslEngine = sslContext.newEngine(ByteBufAllocator.DEFAULT);
                EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(sslEngine));

                assertEquals(1, ((ReferenceCounted) sslContext).refCnt());
                assertEquals(1, ((ReferenceCounted) sslEngine).refCnt());

                assertTrue(ch.finishAndReleaseAll());
                ch.close().syncUninterruptibly();

                assertEquals(1, ((ReferenceCounted) sslContext).refCnt());
                assertEquals(0, ((ReferenceCounted) sslEngine).refCnt());
            } finally {
                ReferenceCountUtil.release(sslContext);
            }
        } finally {
            cert.delete();
        }
    }

    private static final class TlsReadTest extends ChannelOutboundHandlerAdapter {
        private volatile boolean readIssued;

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            readIssued = true;
            super.read(ctx);
        }

        public void test(final boolean dropChannelActive) throws Exception {
          SSLEngine engine = SSLContext.getDefault().createSSLEngine();
          engine.setUseClientMode(true);

          EmbeddedChannel ch = new EmbeddedChannel(
              this,
              new SslHandler(engine),
              new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                  if (!dropChannelActive) {
                    ctx.fireChannelActive();
                  }
                }
              }
          );
          ch.config().setAutoRead(false);
          assertFalse(ch.config().isAutoRead());

          assertTrue(ch.writeOutbound(Unpooled.EMPTY_BUFFER));
          assertTrue(readIssued);
          assertTrue(ch.finishAndReleaseAll());
       }
    }

    @Test
    public void testIssueReadAfterActiveWriteFlush() throws Exception {
        // the handshake is initiated by channelActive
        new TlsReadTest().test(false);
    }

    @Test
    public void testIssueReadAfterWriteFlushActive() throws Exception {
        // the handshake is initiated by flush
        new TlsReadTest().test(true);
    }

    @Test(timeout = 30000)
    public void testRemoval() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        try {
            final Promise<Void> clientPromise = group.next().newPromise();
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(newHandler(SslContextBuilder.forClient().trustManager(
                            InsecureTrustManagerFactory.INSTANCE).build(), clientPromise));

            SelfSignedCertificate ssc = new SelfSignedCertificate();
            final Promise<Void> serverPromise = group.next().newPromise();
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(group, group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(newHandler(SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build(),
                            serverPromise));
            sc = serverBootstrap.bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            cc = bootstrap.connect(sc.localAddress()).syncUninterruptibly().channel();

            serverPromise.syncUninterruptibly();
            clientPromise.syncUninterruptibly();
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
        }
    }

    private static ChannelHandler newHandler(final SslContext sslCtx, final Promise<Void> promise) {
        return new ChannelInitializer() {
            @Override
            protected void initChannel(final Channel ch) {
                final SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
                sslHandler.setHandshakeTimeoutMillis(1000);
                ch.pipeline().addFirst(sslHandler);
                sslHandler.handshakeFuture().addListener(new FutureListener<Channel>() {
                    @Override
                    public void operationComplete(final Future<Channel> future) {
                        ch.pipeline().remove(sslHandler);

                        // Schedule the close so removal has time to propagate exception if any.
                        ch.eventLoop().execute(new Runnable() {
                            @Override
                            public void run() {
                                ch.close();
                            }
                        });
                    }
                });

                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        if (cause instanceof CodecException) {
                            cause = cause.getCause();
                        }
                        if (cause instanceof IllegalReferenceCountException) {
                            promise.setFailure(cause);
                        }
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        promise.trySuccess(null);
                    }
                });
            }
        };
    }

    @Test(timeout = 30000)
    public void testAlertProducedAndSendJdk() throws Exception {
        testAlertProducedAndSend(SslProvider.JDK);
    }

    @Test(timeout = 30000)
    public void testAlertProducedAndSendOpenSsl() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        testAlertProducedAndSend(SslProvider.OPENSSL);
        testAlertProducedAndSend(SslProvider.OPENSSL_REFCNT);
    }

    private void testAlertProducedAndSend(SslProvider provider) throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        final SslContext sslServerCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(provider)
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
                .keyManager(new File(getClass().getResource("test.crt").getFile()),
                        new File(getClass().getResource("test_unencrypted.pem").getFile()))
                .sslProvider(provider).build();

        NioEventLoopGroup group = new NioEventLoopGroup();
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
                            ch.pipeline().addLast(sslServerCtx.newHandler(ch.alloc()));
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    // Just trigger a close
                                    ctx.close();
                                }
                            });
                        }
                    }).bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(sslClientCtx.newHandler(ch.alloc()));
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    if (cause.getCause() instanceof SSLException) {
                                        // We received the alert and so produce an SSLException.
                                        promise.setSuccess(null);
                                    }
                                }
                            });
                        }
                    }).connect(sc.localAddress()).syncUninterruptibly().channel();

            promise.syncUninterruptibly();
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();

            ReferenceCountUtil.release(sslServerCtx);
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test
    public void testCloseFutureNotified() throws Exception {
        SslHandler handler = new SslHandler(SSLContext.getDefault().createSSLEngine());
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // Closing the Channel will also produce a close_notify so it is expected to return true.
        assertTrue(ch.finishAndReleaseAll());

        assertTrue(handler.handshakeFuture().cause() instanceof ClosedChannelException);
        assertTrue(handler.sslCloseFuture().cause() instanceof ClosedChannelException);
    }

    @Test(timeout = 5000)
    public void testEventsFired() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        final BlockingQueue<SslCompletionEvent> events = new LinkedBlockingQueue<SslCompletionEvent>();
        EmbeddedChannel channel = new EmbeddedChannel(new SslHandler(engine), new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof SslCompletionEvent) {
                    events.add((SslCompletionEvent) evt);
                }
            }
        });
        assertTrue(events.isEmpty());
        assertTrue(channel.finishAndReleaseAll());

        SslCompletionEvent evt = events.take();
        assertTrue(evt instanceof SslHandshakeCompletionEvent);
        assertTrue(evt.cause() instanceof ClosedChannelException);

        evt = events.take();
        assertTrue(evt instanceof SslCloseCompletionEvent);
        assertTrue(evt.cause() instanceof ClosedChannelException);
        assertTrue(events.isEmpty());
    }

    @Test(timeout = 30000)
    public void testCloseNotifyReceivedJdk() throws Exception {
        testCloseNotify(SslProvider.JDK, 5000, false);
    }

    @Test(timeout = 30000)
    public void testCloseNotifyReceivedOpenSsl() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        testCloseNotify(SslProvider.OPENSSL, 5000, false);
        testCloseNotify(SslProvider.OPENSSL_REFCNT, 5000, false);
    }

    @Test(timeout = 30000)
    public void testCloseNotifyReceivedJdkTimeout() throws Exception {
        testCloseNotify(SslProvider.JDK, 100, true);
    }

    @Test(timeout = 30000)
    public void testCloseNotifyReceivedOpenSslTimeout() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        testCloseNotify(SslProvider.OPENSSL, 100, true);
        testCloseNotify(SslProvider.OPENSSL_REFCNT, 100, true);
    }

    @Test(timeout = 30000)
    public void testCloseNotifyNotWaitForResponseJdk() throws Exception {
        testCloseNotify(SslProvider.JDK, 0, false);
    }

    @Test(timeout = 30000)
    public void testCloseNotifyNotWaitForResponseOpenSsl() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        testCloseNotify(SslProvider.OPENSSL, 0, false);
        testCloseNotify(SslProvider.OPENSSL_REFCNT, 0, false);
    }

    private static void testCloseNotify(SslProvider provider, final long closeNotifyReadTimeout, final boolean timeout)
            throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        final SslContext sslServerCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(provider)
                .build();

        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(provider).build();

        EventLoopGroup group = new NioEventLoopGroup();
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
                            SslHandler handler = sslServerCtx.newHandler(ch.alloc());
                            handler.setCloseNotifyReadTimeoutMillis(closeNotifyReadTimeout);
                            handler.sslCloseFuture().addListener(
                                    new PromiseNotifier<Channel, Future<Channel>>(serverPromise));
                            handler.handshakeFuture().addListener(new FutureListener<Channel>() {
                                @Override
                                public void operationComplete(Future<Channel> future) {
                                    if (!future.isSuccess()) {
                                        // Something bad happened during handshake fail the promise!
                                        serverPromise.tryFailure(future.cause());
                                    }
                                }
                            });
                            ch.pipeline().addLast(handler);
                        }
                    }).bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            final AtomicBoolean closeSent = new AtomicBoolean();
                            if (timeout) {
                                ch.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        if (closeSent.get()) {
                                            // Drop data on the floor so we will get a timeout while waiting for the
                                            // close_notify.
                                            ReferenceCountUtil.release(msg);
                                        } else {
                                            super.channelRead(ctx, msg);
                                        }
                                    }
                                });
                            }

                            SslHandler handler = sslClientCtx.newHandler(ch.alloc());
                            handler.setCloseNotifyReadTimeoutMillis(closeNotifyReadTimeout);
                            handler.sslCloseFuture().addListener(
                                    new PromiseNotifier<Channel, Future<Channel>>(clientPromise));
                            handler.handshakeFuture().addListener(new FutureListener<Channel>() {
                                @Override
                                public void operationComplete(Future<Channel> future) {
                                    if (future.isSuccess()) {
                                        closeSent.compareAndSet(false, true);
                                        future.getNow().close();
                                    } else {
                                        // Something bad happened during handshake fail the promise!
                                        clientPromise.tryFailure(future.cause());
                                    }
                                }
                            });
                            ch.pipeline().addLast(handler);
                        }
                    }).connect(sc.localAddress()).syncUninterruptibly().channel();

            serverPromise.awaitUninterruptibly();
            clientPromise.awaitUninterruptibly();

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
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();

            ReferenceCountUtil.release(sslServerCtx);
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test(timeout = 300000)
    public void testCompositeBufSizeEstimationGuaranteesSynchronousWrite()
            throws CertificateException, SSLException, ExecutionException, InterruptedException {
        SslProvider[] providers = SslProvider.values();
        for (int i = 0; i < providers.length; ++i) {
            SslProvider serverProvider = providers[i];
            if (isSupported(serverProvider)) {
                for (int j = 0; j < providers.length; ++j) {
                    SslProvider clientProvider = providers[j];
                    if (isSupported(clientProvider)) {
                        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                                true, true);
                        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                                true, false);
                        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                                false, true);
                        compositeBufSizeEstimationGuaranteesSynchronousWrite(serverProvider, clientProvider,
                                false, false);
                    }
                }
            }
        }
    }

    private static void compositeBufSizeEstimationGuaranteesSynchronousWrite(
            SslProvider serverProvider, SslProvider clientProvider,
            final boolean letHandlerCreateServerEngine, final boolean letHandlerCreateClientEngine)
            throws CertificateException, SSLException, ExecutionException, InterruptedException {
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        final SslContext sslServerCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(serverProvider)
                .build();

        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(clientProvider).build();

        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        try {
            final Promise<Void> donePromise = group.next().newPromise();
            // The goal is to provide the SSLEngine with many ByteBuf components to ensure that the overhead for wrap
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
                            if (letHandlerCreateServerEngine) {
                                ch.pipeline().addLast(sslServerCtx.newHandler(ch.alloc()));
                            } else {
                                ch.pipeline().addLast(new SslHandler(sslServerCtx.newEngine(ch.alloc())));
                            }
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof SslHandshakeCompletionEvent) {
                                        SslHandshakeCompletionEvent sslEvt = (SslHandshakeCompletionEvent) evt;
                                        if (sslEvt.isSuccess()) {
                                            CompositeByteBuf content = ctx.alloc().compositeDirectBuffer(numComponents);
                                            for (int i = 0; i < numComponents; ++i) {
                                                ByteBuf buf = ctx.alloc().directBuffer(singleComponentSize);
                                                buf.writerIndex(buf.writerIndex() + singleComponentSize);
                                                content.addComponent(true, buf);
                                            }
                                            ctx.writeAndFlush(content);
                                        } else {
                                            donePromise.tryFailure(sslEvt.cause());
                                        }
                                    }
                                    ctx.fireUserEventTriggered(evt);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    donePromise.tryFailure(cause);
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    donePromise.tryFailure(new IllegalStateException("server closed"));
                                }
                            });
                        }
                    }).bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            if (letHandlerCreateClientEngine) {
                                ch.pipeline().addLast(sslClientCtx.newHandler(ch.alloc()));
                            } else {
                                ch.pipeline().addLast(new SslHandler(sslClientCtx.newEngine(ch.alloc())));
                            }
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                private int bytesSeen;
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof ByteBuf) {
                                        bytesSeen += ((ByteBuf) msg).readableBytes();
                                        if (bytesSeen == expectedBytes) {
                                            donePromise.trySuccess(null);
                                        }
                                    }
                                    ReferenceCountUtil.release(msg);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    donePromise.tryFailure(cause);
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    donePromise.tryFailure(new IllegalStateException("client closed"));
                                }
                            });
                        }
                    }).connect(sc.localAddress()).syncUninterruptibly().channel();

            donePromise.get();
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();

            ReferenceCountUtil.release(sslServerCtx);
            ReferenceCountUtil.release(sslClientCtx);
            ssc.delete();
        }
    }

    private static boolean isSupported(SslProvider provider) {
        switch (provider) {
            case OPENSSL:
            case OPENSSL_REFCNT:
                return OpenSsl.isAvailable();
            default:
                return true;
        }
    }
}
