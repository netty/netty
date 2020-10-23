/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.ssl.OpenSslTestUtils.checkShouldUseKeyManagerFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class OpenSslPrivateKeyMethodTest {
    private static final String RFC_CIPHER_NAME = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
    private static EventLoopGroup GROUP;
    private static SelfSignedCertificate CERT;
    private static ExecutorService EXECUTOR;

    @Parameters(name = "{index}: delegate = {0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> dst = new ArrayList<Object[]>();
        dst.add(new Object[] { true });
        dst.add(new Object[] { false });
        return dst;
    }

    @BeforeClass
    public static void init() throws Exception {
        checkShouldUseKeyManagerFactory();

        Assume.assumeTrue(OpenSsl.isBoringSSL());
        // Check if the cipher is supported at all which may not be the case for various JDK versions and OpenSSL API
        // implementations.
        assumeCipherAvailable(SslProvider.OPENSSL);
        assumeCipherAvailable(SslProvider.JDK);

        GROUP = new DefaultEventLoopGroup();
        CERT = new SelfSignedCertificate();
        EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new DelegateThread(r);
            }
        });
    }

    @AfterClass
    public static void destroy() {
        if (OpenSsl.isBoringSSL()) {
            GROUP.shutdownGracefully();
            CERT.delete();
            EXECUTOR.shutdown();
        }
    }

    private final boolean delegate;

    public OpenSslPrivateKeyMethodTest(boolean delegate) {
        this.delegate = delegate;
    }

    private static void assumeCipherAvailable(SslProvider provider) throws NoSuchAlgorithmException {
        boolean cipherSupported = false;
        if (provider == SslProvider.JDK) {
            SSLEngine engine = SSLContext.getDefault().createSSLEngine();
            for (String c: engine.getSupportedCipherSuites()) {
               if (RFC_CIPHER_NAME.equals(c)) {
                   cipherSupported = true;
                   break;
               }
            }
        } else {
            cipherSupported = OpenSsl.isCipherSuiteAvailable(RFC_CIPHER_NAME);
        }
        Assume.assumeTrue("Unsupported cipher: " + RFC_CIPHER_NAME, cipherSupported);
    }

    private static SslHandler newSslHandler(SslContext sslCtx, ByteBufAllocator allocator, Executor executor) {
        if (executor == null) {
            return sslCtx.newHandler(allocator);
        } else {
            return sslCtx.newHandler(allocator, executor);
        }
    }

    private SslContext buildServerContext(OpenSslPrivateKeyMethod method) throws Exception {
        List<String> ciphers = Collections.singletonList(RFC_CIPHER_NAME);

        final KeyManagerFactory kmf = OpenSslX509KeyManagerFactory.newKeyless(CERT.cert());

        final SslContext sslServerContext = SslContextBuilder.forServer(kmf)
                .sslProvider(SslProvider.OPENSSL)
                .ciphers(ciphers)
                // As this is not a TLSv1.3 cipher we should ensure we talk something else.
                .protocols(SslUtils.PROTOCOL_TLS_V1_2)
                .build();

        ((OpenSslContext) sslServerContext).setPrivateKeyMethod(method);
        return sslServerContext;
    }

    private SslContext buildClientContext()  throws Exception {
        return SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .ciphers(Collections.singletonList(RFC_CIPHER_NAME))
                // As this is not a TLSv1.3 cipher we should ensure we talk something else.
                .protocols(SslUtils.PROTOCOL_TLS_V1_2)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }

    private Executor delegateExecutor() {
       return delegate ? EXECUTOR : null;
    }

    private void assertThread() {
        if (delegate && OpenSslContext.USE_TASKS) {
            assertEquals(DelegateThread.class, Thread.currentThread().getClass());
        } else {
            assertNotEquals(DelegateThread.class, Thread.currentThread().getClass());
        }
    }

    @Test
    public void testPrivateKeyMethod() throws Exception {
        final AtomicBoolean signCalled = new AtomicBoolean();
        final SslContext sslServerContext = buildServerContext(new OpenSslPrivateKeyMethod() {
            @Override
            public byte[] sign(SSLEngine engine, int signatureAlgorithm, byte[] input) throws Exception {
                signCalled.set(true);
                assertThread();

                assertEquals(CERT.cert().getPublicKey(),
                        engine.getSession().getLocalCertificates()[0].getPublicKey());

                // Delegate signing to Java implementation.
                final Signature signature;
                // Depending on the Java version it will pick one or the other.
                if (signatureAlgorithm == OpenSslPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA256) {
                    signature = Signature.getInstance("SHA256withRSA");
                } else if (signatureAlgorithm == OpenSslPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA256) {
                    signature = Signature.getInstance("RSASSA-PSS");
                    signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                            32, 1));
                } else {
                    throw new AssertionError("Unexpected signature algorithm " + signatureAlgorithm);
                }
                signature.initSign(CERT.key());
                signature.update(input);
                return signature.sign();
            }

            @Override
            public byte[] decrypt(SSLEngine engine, byte[] input) {
                throw new UnsupportedOperationException();
            }
        });

        final SslContext sslClientContext = buildClientContext();
        try {
            try {
                final Promise<Object> serverPromise = GROUP.next().newPromise();
                final Promise<Object> clientPromise = GROUP.next().newPromise();

                ChannelHandler serverHandler = new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(newSslHandler(sslServerContext, ch.alloc(), delegateExecutor()));

                        pipeline.addLast(new SimpleChannelInboundHandler<Object>() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                serverPromise.cancel(true);
                                ctx.fireChannelInactive();
                            }

                            @Override
                            public void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                if (serverPromise.trySuccess(null)) {
                                    ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {'P', 'O', 'N', 'G'}));
                                }
                                ctx.close();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                if (!serverPromise.tryFailure(cause)) {
                                    ctx.fireExceptionCaught(cause);
                                }
                            }
                        });
                    }
                };

                LocalAddress address = new LocalAddress("test-" + SslProvider.OPENSSL
                        + '-' + SslProvider.JDK + '-' + RFC_CIPHER_NAME + '-' + delegate);

                Channel server = server(address, serverHandler);
                try {
                    ChannelHandler clientHandler = new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(newSslHandler(sslClientContext, ch.alloc(), delegateExecutor()));

                            pipeline.addLast(new SimpleChannelInboundHandler<Object>() {
                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    clientPromise.cancel(true);
                                    ctx.fireChannelInactive();
                                }

                                @Override
                                public void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    clientPromise.trySuccess(null);
                                    ctx.close();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    if (!clientPromise.tryFailure(cause)) {
                                        ctx.fireExceptionCaught(cause);
                                    }
                                }
                            });
                        }
                    };

                    Channel client = client(server, clientHandler);
                    try {
                        client.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {'P', 'I', 'N', 'G'}))
                              .syncUninterruptibly();

                        assertTrue("client timeout", clientPromise.await(5L, TimeUnit.SECONDS));
                        assertTrue("server timeout", serverPromise.await(5L, TimeUnit.SECONDS));

                        clientPromise.sync();
                        serverPromise.sync();
                        assertTrue(signCalled.get());
                    } finally {
                        client.close().sync();
                    }
                } finally {
                    server.close().sync();
                }
            } finally {
                ReferenceCountUtil.release(sslClientContext);
            }
        } finally {
            ReferenceCountUtil.release(sslServerContext);
        }
    }

    @Test
    public void testPrivateKeyMethodFailsBecauseOfException() throws Exception {
        testPrivateKeyMethodFails(false);
    }

    @Test
    public void testPrivateKeyMethodFailsBecauseOfNull() throws Exception {
        testPrivateKeyMethodFails(true);
    }
    private void testPrivateKeyMethodFails(final boolean returnNull) throws Exception {
        final SslContext sslServerContext = buildServerContext(new OpenSslPrivateKeyMethod() {
            @Override
            public byte[] sign(SSLEngine engine, int signatureAlgorithm, byte[] input) throws Exception {
                assertThread();
                if (returnNull) {
                    return null;
                }
                throw new SignatureException();
            }

            @Override
            public byte[] decrypt(SSLEngine engine, byte[] input) {
                throw new UnsupportedOperationException();
            }
        });
        final SslContext sslClientContext = buildClientContext();

        SslHandler serverSslHandler = newSslHandler(
                sslServerContext, UnpooledByteBufAllocator.DEFAULT, delegateExecutor());
        SslHandler clientSslHandler = newSslHandler(
                sslClientContext, UnpooledByteBufAllocator.DEFAULT, delegateExecutor());

        try {
            try {
                LocalAddress address = new LocalAddress("test-" + SslProvider.OPENSSL
                        + '-' + SslProvider.JDK + '-' + RFC_CIPHER_NAME + '-' + delegate);

                Channel server = server(address, serverSslHandler);
                try {
                    Channel client = client(server, clientSslHandler);
                    try {
                        Throwable clientCause = clientSslHandler.handshakeFuture().await().cause();
                        Throwable serverCause = serverSslHandler.handshakeFuture().await().cause();
                        assertNotNull(clientCause);
                        assertThat(serverCause, Matchers.instanceOf(SSLHandshakeException.class));
                    } finally {
                        client.close().sync();
                    }
                } finally {
                    server.close().sync();
                }
            } finally {
                ReferenceCountUtil.release(sslClientContext);
            }
        } finally {
            ReferenceCountUtil.release(sslServerContext);
        }
    }

    private static Channel server(LocalAddress address, ChannelHandler handler) throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(GROUP)
                .childHandler(handler);

        return bootstrap.bind(address).sync().channel();
    }

    private static Channel client(Channel server, ChannelHandler handler) throws Exception {
        SocketAddress remoteAddress = server.localAddress();

        Bootstrap bootstrap = new Bootstrap()
                .channel(LocalChannel.class)
                .group(GROUP)
                .handler(handler);

        return bootstrap.connect(remoteAddress).sync().channel();
    }

    private static final class DelegateThread extends Thread {
        DelegateThread(Runnable target) {
            super(target);
        }
    }
}
