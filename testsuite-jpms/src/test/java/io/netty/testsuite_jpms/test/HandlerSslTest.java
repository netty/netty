/*
 * Copyright 2024 The Netty Project
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

package io.netty.testsuite_jpms.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.ssl.SslContextBuilder.forServer;
import static io.netty.handler.ssl.SslContextBuilder.forClient;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HandlerSslTest {

    private static X509Bundle signedCert;
    private Channel clientChannel;
    private Channel serverChannel;
    private Channel serverConnectedChannel;
    private Throwable clientException;
    private Throwable serverException;
    private final CountDownLatch serverLatch = new CountDownLatch(1);
    private final CountDownLatch clientLatch = new CountDownLatch(1);

    @BeforeAll
    public static void setup() throws Exception {
        signedCert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
    }

    @Test
    public void testSimpleJdkProvider() throws Exception {
        mySetupClientHostnameValidation(
                SslProvider.JDK,
                false);
        assertTrue(clientLatch.await(10, TimeUnit.SECONDS));

        rethrowIfNotNull(clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        rethrowIfNotNull(serverException);
    }

    @Test
    public void testOpenSslProvider() throws Exception {
        mySetupClientHostnameValidation(
                SslProvider.OPENSSL,
                false);
        assertTrue(clientLatch.await(10, TimeUnit.SECONDS));

        rethrowIfNotNull(clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        rethrowIfNotNull(serverException);
    }

    private Future<Void> mySetupClientHostnameValidation(SslProvider sslProvider,
                                                         final boolean failureExpected)
            throws Exception {

        final String expectedHost = "localhost";
        SslContext serverSslCtx = forServer(signedCert.getKeyPair().getPrivate(), signedCert.getCertificatePath())
                .sslProvider(sslProvider)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build();

        SslContext clientSslCtx = forClient()
                .sslProvider(sslProvider)
                .trustManager(signedCert.toTrustManagerFactory())
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build();

        serverConnectedChannel = null;
        ServerBootstrap sb = new ServerBootstrap();
        Bootstrap cb = new Bootstrap();

        sb.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()),
                new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()));
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();

                SslHandler handler = serverSslCtx.newHandler(ch.alloc());
                p.addLast(handler);
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            if (failureExpected) {
                                serverException = new IllegalStateException("handshake complete. expected failure");
                            }
                            serverLatch.countDown();
                        } else if (evt instanceof SslHandshakeCompletionEvent) {
                            serverException = ((SslHandshakeCompletionEvent) evt).cause();
                            serverLatch.countDown();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        if (cause.getCause() instanceof SSLHandshakeException) {
                            serverException = cause.getCause();
                            serverLatch.countDown();
                        } else {
                            serverException = cause;
                            ctx.fireExceptionCaught(cause);
                        }
                    }
                });
                serverConnectedChannel = ch;
            }
        });

        final Promise<Void> clientWritePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        cb.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()));
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                InetSocketAddress remoteAddress = (InetSocketAddress) serverChannel.localAddress();

                SslHandler sslHandler = clientSslCtx.newHandler(ch.alloc(), expectedHost, 0);

                SSLParameters parameters = sslHandler.engine().getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslHandler.engine().setSSLParameters(parameters);
                p.addLast(sslHandler);
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        // Only write if there is a failure expected. We don't actually care about the write going
                        // through we just want to verify the local failure condition. This way we don't have to worry
                        // about verifying the payload and releasing the content on the server side.
                        if (failureExpected) {
                            ChannelFuture f = ctx.write(ctx.alloc().buffer(1).writeByte(1));
                            PromiseNotifier.cascade(f, clientWritePromise);
                        }
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            if (failureExpected) {
                                clientException = new IllegalStateException("handshake complete. expected failure");
                            }
                            clientLatch.countDown();
                        } else if (evt instanceof SslHandshakeCompletionEvent) {
                            clientException = ((SslHandshakeCompletionEvent) evt).cause();
                            clientLatch.countDown();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        if (cause.getCause() instanceof SSLHandshakeException) {
                            clientException = cause.getCause();
                            clientLatch.countDown();
                        } else {
                            ctx.fireExceptionCaught(cause);
                        }
                    }
                });
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(expectedHost, 0)).sync().channel();
        final int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(expectedHost, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
        return clientWritePromise;
    }

    protected static void rethrowIfNotNull(Throwable error) {
        if (error != null) {
            throw new AssertionFailedError("Expected no error", error);
        }
    }
}
