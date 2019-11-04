/*
 * Copyright 2017 The Netty Project
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ResourcesUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.EmptyArrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ParameterizedSslHandlerTest {

    @Parameterized.Parameters(name = "{index}: clientProvider={0}, {index}: serverProvider={1}")
    public static Collection<Object[]> data() {
        List<SslProvider> providers = new ArrayList<SslProvider>(3);
        if (OpenSsl.isAvailable()) {
            providers.add(SslProvider.OPENSSL);
            providers.add(SslProvider.OPENSSL_REFCNT);
        }
        providers.add(SslProvider.JDK);

        List<Object[]> params = new ArrayList<Object[]>();

        for (SslProvider cp: providers) {
            for (SslProvider sp: providers) {
                params.add(new Object[] { cp, sp });
            }
        }
        return params;
    }

    private final SslProvider clientProvider;
    private final SslProvider serverProvider;

    public ParameterizedSslHandlerTest(SslProvider clientProvider, SslProvider serverProvider) {
        this.clientProvider = clientProvider;
        this.serverProvider = serverProvider;
    }

    @Test(timeout = 480000)
    public void testCompositeBufSizeEstimationGuaranteesSynchronousWrite()
            throws CertificateException, SSLException, ExecutionException, InterruptedException {
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
                            final SslHandler handler = letHandlerCreateServerEngine
                                    ? sslServerCtx.newHandler(ch.alloc())
                                    : new SslHandler(sslServerCtx.newEngine(ch.alloc()));
                            if (serverDisableWrapSize) {
                                handler.setWrapDataSize(-1);
                            }
                            ch.pipeline().addLast(handler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                private boolean sentData;
                                private Throwable writeCause;

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
                                            ctx.writeAndFlush(content).addListener(new ChannelFutureListener() {
                                                @Override
                                                public void operationComplete(ChannelFuture future) throws Exception {
                                                    writeCause = future.cause();
                                                    if (writeCause == null) {
                                                        sentData = true;
                                                    }
                                                }
                                            });
                                        } else {
                                            donePromise.tryFailure(sslEvt.cause());
                                        }
                                    }
                                    ctx.fireUserEventTriggered(evt);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    donePromise.tryFailure(new IllegalStateException("server exception sentData: " +
                                            sentData + " writeCause: " + writeCause, cause));
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    donePromise.tryFailure(new IllegalStateException("server closed sentData: " +
                                            sentData + " writeCause: " + writeCause));
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
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof SslHandshakeCompletionEvent) {
                                        SslHandshakeCompletionEvent sslEvt = (SslHandshakeCompletionEvent) evt;
                                        if (!sslEvt.isSuccess()) {
                                            donePromise.tryFailure(sslEvt.cause());
                                        }
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
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

    @Test(timeout = 30000)
    public void testAlertProducedAndSend() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        final SslContext sslServerCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
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
                                        promise.trySuccess(null);
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

    @Test(timeout = 30000)
    public void testCloseNotify() throws Exception {
        testCloseNotify(5000, false);
    }

    @Test(timeout = 30000)
    public void testCloseNotifyReceivedTimeout() throws Exception {
        testCloseNotify(100, true);
    }

    @Test(timeout = 30000)
    public void testCloseNotifyNotWaitForResponse() throws Exception {
        testCloseNotify(0, false);
    }

    private void testCloseNotify(final long closeNotifyReadTimeout, final boolean timeout) throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        final SslContext sslServerCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                                         .sslProvider(serverProvider)
                                                         // Use TLSv1.2 as we depend on the fact that the handshake
                                                         // is done in an extra round trip in the test which
                                                         // is not true in TLSv1.3
                                                         .protocols(SslUtils.PROTOCOL_TLS_V1_2)
                                                         .build();

        final SslContext sslClientCtx = SslContextBuilder.forClient()
                                                         .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                                         .sslProvider(clientProvider)
                                                         // Use TLSv1.2 as we depend on the fact that the handshake
                                                         // is done in an extra round trip in the test which
                                                         // is not true in TLSv1.3
                                                         .protocols(SslUtils.PROTOCOL_TLS_V1_2)
                                                         .build();

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
}

