/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.ssl.ocsp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.ocsp.OcspTestUtil.CertAndKey;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.ssl.ocsp.OcspTestUtil.buildCertificate;
import static io.netty.handler.ssl.ocsp.OcspTestUtil.ocspUrlExtension;
import static io.netty.handler.ssl.ocsp.OcspTestUtil.startOcspResponder;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcspServerCertificateValidatorTest {

    /**
     * Full round trip against a local TLS server whose certificate points at a local OCSP responder: a good
     * response must result in a {@link OcspResponse.Status#VALID} verdict.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void connectUsingHttpAndValidateCertificateUsingOcspTest() throws Exception {
        final AtomicBoolean ocspStatus = new AtomicBoolean();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        SslContext serverSslContext = null;
        SslContext clientSslContext = null;
        Channel ocspResponder = null;
        Channel tlsServer = null;
        Channel client = null;

        try {
            final CountDownLatch latch = new CountDownLatch(1);
            CertAndKey caRoot = buildCertificate("CN=TrustedRootCA", true, null);

            // Start a local OCSP responder so the test never leaves the loopback interface.
            ocspResponder = startOcspResponder(eventLoopGroup, caRoot);
            CertAndKey serverCert = buildCertificate("CN=localhost", false, caRoot, ocspUrlExtension(ocspResponder));

            serverSslContext = SslContextBuilder.forServer(serverCert.keyPair.getPrivate(),
                    serverCert.certificate, caRoot.certificate).build();
            final SslContext serverSslCtx = serverSslContext;
            tlsServer = new ServerBootstrap()
                    .group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(serverSslCtx.newHandler(ch.alloc()));
                        }
                    })
                    .bind(NetUtil.LOCALHOST4, 0)
                    .sync().channel();
            final int tlsPort = ((InetSocketAddress) tlsServer.localAddress()).getPort();

            clientSslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            final SslContext clientSslCtx = clientSslContext;

            client = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(clientSslCtx.newHandler(ch.alloc(), "localhost", tlsPort));
                            ch.pipeline().addLast(new OcspServerCertificateValidator(false));
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof OcspValidationEvent) {
                                        OcspValidationEvent event = (OcspValidationEvent) evt;

                                        ocspStatus.set(event.response().status() == OcspResponse.Status.VALID);
                                        ctx.channel().close();
                                        latch.countDown();
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    failure.compareAndSet(null, cause);
                                    ctx.close();
                                    latch.countDown();
                                }
                            });
                        }
                    })
                    .connect(NetUtil.LOCALHOST4, tlsPort)
                    .sync().channel();

            // Wait for maximum of 1 minute for Ocsp validation to happen
            assertTrue(latch.await(1, TimeUnit.MINUTES));
            assertNull(failure.get());
            assertTrue(ocspStatus.get());

            // Wait for Channel to be closed
            client.closeFuture().sync();
        } finally {
            if (client != null) {
                client.close().syncUninterruptibly();
            }
            if (tlsServer != null) {
                tlsServer.close().syncUninterruptibly();
            }
            if (ocspResponder != null) {
                ocspResponder.close().syncUninterruptibly();
            }
            ReferenceCountUtil.release(clientSslContext);
            ReferenceCountUtil.release(serverSslContext);
            eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
