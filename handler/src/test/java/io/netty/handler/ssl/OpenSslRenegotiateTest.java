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
package io.netty.handler.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenSslRenegotiateTest extends RenegotiateTest {

    @BeforeAll
    public static void checkOpenSsl() {
        OpenSsl.ensureAvailability();
    }

    @Override
    protected SslProvider serverSslProvider() {
        return SslProvider.OPENSSL;
    }

    protected void verifyResult(AtomicReference<Throwable> error) throws Throwable {
        Throwable cause = error.get();
        // Renegotiation is not supported by the OpenSslEngine.
        assertInstanceOf(SSLException.class, cause);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testClientInitiatedRenegotiationRejected() throws Throwable {
        final AtomicInteger serverHandshakes = new AtomicInteger();
        final AtomicReference<Throwable> renegotiationError = new AtomicReference<Throwable>();
        final CountDownLatch renegotiationDone = new CountDownLatch(1);
        final CountDownLatch serverClosed = new CountDownLatch(1);
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());
        final SslContext context = SslContextBuilder.forServer(cert.key(), cert.cert())
                .sslProvider(SslProvider.OPENSSL)
                .protocols(SslProtocols.TLS_v1_2)
                .build();
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(group).channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            SslHandler handler = context.newHandler(ch.alloc());
                            handler.setHandshakeTimeoutMillis(0);
                            ch.pipeline().addLast(handler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ReferenceCountUtil.release(msg);
                                }

                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof SslHandshakeCompletionEvent
                                            && ((SslHandshakeCompletionEvent) evt).isSuccess()) {
                                        serverHandshakes.incrementAndGet();
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    ctx.close();
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    serverClosed.countDown();
                                }
                            });
                        }
                    });
            Channel channel = sb.bind(new LocalAddress(getClass().getName() + ".client")).syncUninterruptibly()
                    .channel();

            final SslContext clientContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .sslProvider(SslProvider.JDK)
                    .protocols(SslProtocols.TLS_v1_2)
                    .build();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(LocalChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            SslHandler handler = clientContext.newHandler(ch.alloc());
                            handler.setHandshakeTimeoutMillis(0);
                            ch.pipeline().addLast(handler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                private boolean renegotiate;

                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (!renegotiate && evt instanceof SslHandshakeCompletionEvent
                                            && ((SslHandshakeCompletionEvent) evt).isSuccess()) {
                                        renegotiate = true;
                                        ctx.pipeline().get(SslHandler.class).renegotiate().addListener(future -> {
                                            renegotiationError.set(future.cause());
                                            renegotiationDone.countDown();
                                        });
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    ctx.close();
                                }
                            });
                        }
                    });

            Channel clientChannel = bootstrap.connect(channel.localAddress()).syncUninterruptibly().channel();
            assertTrue(renegotiationDone.await(20, TimeUnit.SECONDS));
            assertTrue(serverClosed.await(20, TimeUnit.SECONDS));
            clientChannel.close().syncUninterruptibly();
            channel.close().syncUninterruptibly();

            // The server must refuse the second handshake and drop the connection.
            assertNotNull(renegotiationError.get());
            assertEquals(1, serverHandshakes.get());
        } finally  {
            group.shutdownGracefully();
            ReferenceCountUtil.release(context);
        }
    }
}
