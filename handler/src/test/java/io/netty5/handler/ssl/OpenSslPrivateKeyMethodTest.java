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
package io.netty5.handler.ssl;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty5.handler.ssl.OpenSslTestUtils.checkShouldUseKeyManagerFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class OpenSslPrivateKeyMethodTest {
    private static final String RFC_CIPHER_NAME = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
    private static EventLoopGroup GROUP;
    private static SelfSignedCertificate CERT;
    private static ExecutorService EXECUTOR;

    static Collection<Object[]> parameters() {
        List<Object[]> dst = new ArrayList<Object[]>();
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                for (int c = 0; c < 2; c++) {
                    dst.add(new Object[] { a == 0, b == 0, c == 0 });
                }
            }
        }
        return dst;
    }

    @BeforeAll
    public static void init() throws Exception {
        checkShouldUseKeyManagerFactory();

        assumeTrue(OpenSsl.isBoringSSL());
        // Check if the cipher is supported at all which may not be the case for various JDK versions and OpenSSL API
        // implementations.
        assumeCipherAvailable(SslProvider.OPENSSL);
        assumeCipherAvailable(SslProvider.JDK);

        GROUP = new MultithreadEventLoopGroup(LocalHandler.newFactory());
        CERT = new SelfSignedCertificate();
        EXECUTOR = Executors.newCachedThreadPool(DelegateThread::new);
    }

    @AfterAll
    public static void destroy() {
        if (OpenSsl.isBoringSSL()) {
            GROUP.shutdownGracefully();
            CERT.delete();
            EXECUTOR.shutdown();
        }
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
        assumeTrue(cipherSupported, "Unsupported cipher: " + RFC_CIPHER_NAME);
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

        return SslContextBuilder.forServer(kmf)
                .sslProvider(SslProvider.OPENSSL)
                .ciphers(ciphers)
                // As this is not a TLSv1.3 cipher we should ensure we talk something else.
                .protocols(SslProtocols.TLS_v1_2)
                .option(OpenSslContextOption.PRIVATE_KEY_METHOD, method)
                .build();
    }

    private SslContext buildClientContext()  throws Exception {
        return SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .ciphers(Collections.singletonList(RFC_CIPHER_NAME))
                // As this is not a TLSv1.3 cipher we should ensure we talk something else.
                .protocols(SslProtocols.TLS_v1_2)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }

    private static Executor delegateExecutor(boolean delegate) {
        return delegate ? EXECUTOR : null;
    }
    private SslContext buildServerContext(OpenSslAsyncPrivateKeyMethod method) throws Exception {
        List<String> ciphers = Collections.singletonList(RFC_CIPHER_NAME);

        final KeyManagerFactory kmf = OpenSslX509KeyManagerFactory.newKeyless(CERT.cert());

        return SslContextBuilder.forServer(kmf)
                .sslProvider(SslProvider.OPENSSL)
                .ciphers(ciphers)
                // As this is not a TLSv1.3 cipher we should ensure we talk something else.
                .protocols(SslProtocols.TLS_v1_2)
                .option(OpenSslContextOption.ASYNC_PRIVATE_KEY_METHOD, method)
                .build();
    }

    private static void assertThread(boolean delegate) {
        if (delegate && OpenSslContext.USE_TASKS) {
            assertEquals(DelegateThread.class, Thread.currentThread().getClass());
        }
    }

    @ParameterizedTest(name = "{index}: delegate = {0}, async = {1}, newThread={2}")
    @MethodSource("parameters")
    public void testPrivateKeyMethod(final boolean delegate, boolean async, boolean newThread) throws Exception {
        final AtomicBoolean signCalled = new AtomicBoolean();
        OpenSslPrivateKeyMethod keyMethod = new OpenSslPrivateKeyMethod() {
            @Override
            public byte[] sign(SSLEngine engine, int signatureAlgorithm, byte[] input) throws Exception {
                signCalled.set(true);
                assertThread(delegate);

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
        };

        final SslContext sslServerContext = async ? buildServerContext(
                new OpenSslPrivateKeyMethodAdapter(keyMethod, newThread)) : buildServerContext(keyMethod);

        final SslContext sslClientContext = buildClientContext();
        try {
            try {
                final Promise<Object> serverPromise = GROUP.next().newPromise();
                final Promise<Object> clientPromise = GROUP.next().newPromise();

                ChannelHandler serverHandler = new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(newSslHandler(sslServerContext, ch.alloc(), delegateExecutor(delegate)));

                        pipeline.addLast(new SimpleChannelInboundHandler<Object>() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                serverPromise.cancel();
                                ctx.fireChannelInactive();
                            }

                            @Override
                            public void messageReceived(ChannelHandlerContext ctx, Object msg) {
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
                            pipeline.addLast(newSslHandler(sslClientContext, ch.alloc(), delegateExecutor(delegate)));

                            pipeline.addLast(new SimpleChannelInboundHandler<Object>() {
                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    clientPromise.cancel();
                                    ctx.fireChannelInactive();
                                }

                                @Override
                                public void messageReceived(ChannelHandlerContext ctx, Object msg) {
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

                        Future<Object> clientFuture = clientPromise.asFuture();
                        Future<Object> serverFuture = serverPromise.asFuture();
                        assertTrue(clientFuture.await(5L, TimeUnit.SECONDS), "client timeout");
                        assertTrue(serverFuture.await(5L, TimeUnit.SECONDS), "server timeout");

                        clientFuture.sync();
                        serverFuture.sync();
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

    @ParameterizedTest(name = "{index}: delegate = {0}")
    @MethodSource("parameters")
    public void testPrivateKeyMethodFailsBecauseOfException(final boolean delegate) throws Exception {
        testPrivateKeyMethodFails(delegate, false);
    }

    @ParameterizedTest(name = "{index}: delegate = {0}")
    @MethodSource("parameters")
    public void testPrivateKeyMethodFailsBecauseOfNull(final boolean delegate) throws Exception {
        testPrivateKeyMethodFails(delegate, true);
    }

    private void testPrivateKeyMethodFails(final boolean delegate, final boolean returnNull) throws Exception {
        final SslContext sslServerContext = buildServerContext(new OpenSslPrivateKeyMethod() {
            @Override
            public byte[] sign(SSLEngine engine, int signatureAlgorithm, byte[] input) throws Exception {
                assertThread(delegate);
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
                sslServerContext, UnpooledByteBufAllocator.DEFAULT, delegateExecutor(delegate));
        SslHandler clientSslHandler = newSslHandler(
                sslClientContext, UnpooledByteBufAllocator.DEFAULT, delegateExecutor(delegate));

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

        return bootstrap.bind(address).get();
    }

    private static Channel client(Channel server, ChannelHandler handler) throws Exception {
        SocketAddress remoteAddress = server.localAddress();

        Bootstrap bootstrap = new Bootstrap()
                .channel(LocalChannel.class)
                .group(GROUP)
                .handler(handler);

        return bootstrap.connect(remoteAddress).get();
    }

    private static final class DelegateThread extends Thread {
        DelegateThread(Runnable target) {
            super(target);
        }
    }

    private static final class OpenSslPrivateKeyMethodAdapter implements OpenSslAsyncPrivateKeyMethod {
        private final OpenSslPrivateKeyMethod keyMethod;
        private final boolean newThread;

        OpenSslPrivateKeyMethodAdapter(OpenSslPrivateKeyMethod keyMethod, boolean newThread) {
            this.keyMethod = keyMethod;
            this.newThread = newThread;
        }

        @Override
        public Future<byte[]> sign(final SSLEngine engine, final int signatureAlgorithm, final byte[] input) {
            final Promise<byte[]> promise = ImmediateEventExecutor.INSTANCE.newPromise();
            try {
                if (newThread) {
                    // Let's run these in an extra thread to ensure that this would also work if the promise is
                    // notified later.
                    new DelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // Let's sleep for some time to ensure we would notify in an async fashion
                                Thread.sleep(ThreadLocalRandom.current().nextLong(100, 500));
                                promise.setSuccess(keyMethod.sign(engine, signatureAlgorithm, input));
                            } catch (Throwable cause) {
                                promise.setFailure(cause);
                            }
                        }
                    }).start();
                } else {
                    promise.setSuccess(keyMethod.sign(engine, signatureAlgorithm, input));
                }
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
            return promise.asFuture();
        }

        @Override
        public Future<byte[]> decrypt(final SSLEngine engine, final byte[] input) {
            final Promise<byte[]> promise = ImmediateEventExecutor.INSTANCE.newPromise();
            try {
                if (newThread) {
                    // Let's run these in an extra thread to ensure that this would also work if the promise is
                    // notified later.
                    new DelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // Let's sleep for some time to ensure we would notify in an async fashion
                                Thread.sleep(ThreadLocalRandom.current().nextLong(100, 500));
                                promise.setSuccess(keyMethod.decrypt(engine, input));
                            } catch (Throwable cause) {
                                promise.setFailure(cause);
                            }
                        }
                    }).start();
                } else {
                    promise.setSuccess(keyMethod.decrypt(engine, input));
                }
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
            return promise.asFuture();
        }
    }
}
