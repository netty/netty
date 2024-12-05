/*
 * Copyright 2018 The Netty Project
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
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The purpose of this unit test is to act as a canary and catch changes in supported cipher suites.
 */
public class CipherSuiteCanaryTest {

    private static EventLoopGroup GROUP;

    private static X509Bundle CERT;

    static Collection<Object[]> parameters() {
       List<Object[]> dst = new ArrayList<Object[]>();
       dst.addAll(expand("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")); // DHE-RSA-AES128-GCM-SHA256
       return dst;
    }

    @BeforeAll
    public static void init() throws Exception {
        GROUP = new DefaultEventLoopGroup();
        CERT = new CertificateBuilder()
                .rsa2048()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
    }

    @AfterAll
    public static void destroy() {
        GROUP.shutdownGracefully();
    }

    private static void assumeCipherAvailable(SslProvider provider, String cipher) throws NoSuchAlgorithmException {
        boolean cipherSupported = false;
        if (provider == SslProvider.JDK) {
            SSLEngine engine = SSLContext.getDefault().createSSLEngine();
            for (String c: engine.getSupportedCipherSuites()) {
               if (cipher.equals(c)) {
                   cipherSupported = true;
                   break;
               }
            }
        } else {
            cipherSupported = OpenSsl.isCipherSuiteAvailable(cipher);
        }
        assumeTrue(cipherSupported, "Unsupported cipher: " + cipher);
    }

    private static SslHandler newSslHandler(SslContext sslCtx, ByteBufAllocator allocator, Executor executor) {
        if (executor == null) {
            return sslCtx.newHandler(allocator);
        } else {
            return sslCtx.newHandler(allocator, executor);
        }
    }

    @ParameterizedTest(
            name = "{index}: serverSslProvider = {0}, clientSslProvider = {1}, rfcCipherName = {2}, delegate = {3}")
    @MethodSource("parameters")
    public void testHandshake(SslProvider serverSslProvider, SslProvider clientSslProvider,
                              String rfcCipherName, boolean delegate) throws Exception {
        // Check if the cipher is supported at all which may not be the case for various JDK versions and OpenSSL API
        // implementations.
        assumeCipherAvailable(serverSslProvider, rfcCipherName);
        assumeCipherAvailable(clientSslProvider, rfcCipherName);

        List<String> ciphers = Collections.singletonList(rfcCipherName);

        PrivateKey privateKey = CERT.getKeyPair().getPrivate();
        X509Certificate[] certChain = CERT.getCertificatePath();
        final SslContext sslServerContext = SslContextBuilder.forServer(privateKey, certChain)
                .sslProvider(serverSslProvider)
                .ciphers(ciphers)
                // As this is not a TLSv1.3 cipher we should ensure we talk something else.
                .protocols(SslProtocols.TLS_v1_2)
                .build();

        final ExecutorService executorService = delegate ? Executors.newCachedThreadPool() : null;

        try {
            final SslContext sslClientContext = SslContextBuilder.forClient()
                    .sslProvider(clientSslProvider)
                    .ciphers(ciphers)
                    // As this is not a TLSv1.3 cipher we should ensure we talk something else.
                    .protocols(SslProtocols.TLS_v1_2)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            try {
                final Promise<Object> serverPromise = GROUP.next().newPromise();
                final Promise<Object> clientPromise = GROUP.next().newPromise();

                ChannelHandler serverHandler = new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(newSslHandler(sslServerContext, ch.alloc(), executorService));

                        pipeline.addLast(new SimpleChannelInboundHandler<Object>() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                serverPromise.cancel(true);
                                ctx.fireChannelInactive();
                            }

                            @Override
                            public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (serverPromise.trySuccess(null)) {
                                    ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {'P', 'O', 'N', 'G'}));
                                }
                                ctx.close();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                if (!serverPromise.tryFailure(cause)) {
                                    ctx.fireExceptionCaught(cause);
                                }
                            }
                        });
                    }
                };

                LocalAddress address = new LocalAddress("test-" + serverSslProvider
                        + '-' + clientSslProvider + '-' + rfcCipherName);

                Channel server = server(address, serverHandler);
                try {
                    ChannelHandler clientHandler = new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(newSslHandler(sslClientContext, ch.alloc(), executorService));

                            pipeline.addLast(new SimpleChannelInboundHandler<Object>() {
                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                    clientPromise.cancel(true);
                                    ctx.fireChannelInactive();
                                }

                                @Override
                                public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    clientPromise.trySuccess(null);
                                    ctx.close();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                        throws Exception {
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

                        assertTrue(clientPromise.await(5L, TimeUnit.SECONDS), "client timeout");
                        assertTrue(serverPromise.await(5L, TimeUnit.SECONDS), "server timeout");

                        clientPromise.sync();
                        serverPromise.sync();
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

            if (executorService != null) {
                executorService.shutdown();
            }
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

    private static List<Object[]> expand(String rfcCipherName) {
        List<Object[]> dst = new ArrayList<Object[]>();
        SslProvider[] sslProviders = SslProvider.values();

        for (int i = 0; i < sslProviders.length; i++) {
            SslProvider serverSslProvider = sslProviders[i];

            for (int j = 0; j < sslProviders.length; j++) {
                SslProvider clientSslProvider = sslProviders[j];

                if ((serverSslProvider != SslProvider.JDK || clientSslProvider != SslProvider.JDK)
                        && !OpenSsl.isAvailable()) {
                    continue;
                }

                dst.add(new Object[]{serverSslProvider, clientSslProvider, rfcCipherName, true});
                dst.add(new Object[]{serverSslProvider, clientSslProvider, rfcCipherName, false});
            }
        }

        if (dst.isEmpty()) {
            throw new IllegalStateException();
        }

        return dst;
    }
}
