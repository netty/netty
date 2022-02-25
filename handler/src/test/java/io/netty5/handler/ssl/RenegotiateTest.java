/*
 * Copyright 2015 The Netty Project
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
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.FutureListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class RenegotiateTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testRenegotiateServer() throws Throwable {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(2);
        SelfSignedCertificate cert = new SelfSignedCertificate();
        EventLoopGroup group = new MultithreadEventLoopGroup(LocalHandler.newFactory());
        try {
            final SslContext context = SslContextBuilder.forServer(cert.key(), cert.cert())
                    .sslProvider(serverSslProvider())
                    .protocols(SslProtocols.TLS_v1_2)
                    .build();

            ServerBootstrap sb = new ServerBootstrap();
            sb.group(group).channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            SslHandler handler = context.newHandler(ch.alloc());
                            handler.setHandshakeTimeoutMillis(0);
                            ch.pipeline().addLast(handler);
                            ch.pipeline().addLast(new ChannelHandler() {

                                private boolean renegotiate;

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ReferenceCountUtil.release(msg);
                                }

                                @Override
                                public void userEventTriggered(
                                        final ChannelHandlerContext ctx, Object evt) {
                                    if (!renegotiate && evt instanceof SslHandshakeCompletionEvent) {
                                        SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;

                                        if (event.isSuccess()) {
                                            final SslHandler handler = ctx.pipeline().get(SslHandler.class);

                                            renegotiate = true;
                                            handler.renegotiate().addListener((FutureListener<Channel>) future -> {
                                                if (future.isFailed()) {
                                                    error.compareAndSet(null, future.cause());
                                                    ctx.close();
                                                }
                                                latch.countDown();
                                            });
                                        } else {
                                            error.compareAndSet(null, event.cause());
                                            latch.countDown();

                                            ctx.close();
                                        }
                                    }
                                }
                            });
                        }
                    });
            Channel channel = sb.bind(new LocalAddress("test")).get();

            final SslContext clientContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .sslProvider(SslProvider.JDK)
                    .protocols(SslProtocols.TLS_v1_2)
                    .build();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(LocalChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            SslHandler handler = clientContext.newHandler(ch.alloc());
                            handler.setHandshakeTimeoutMillis(0);
                            ch.pipeline().addLast(handler);
                            ch.pipeline().addLast(new ChannelHandler() {
                                @Override
                                public void userEventTriggered(
                                        ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof SslHandshakeCompletionEvent) {
                                        SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                                        if (!event.isSuccess()) {
                                            error.compareAndSet(null, event.cause());
                                            ctx.close();
                                        }
                                        latch.countDown();
                                    }
                                }
                            });
                        }
                    });

            Channel clientChannel = bootstrap.connect(channel.localAddress()).get();
            latch.await();
            clientChannel.close().syncUninterruptibly();
            channel.close().syncUninterruptibly();
            verifyResult(error);
        } finally  {
            group.shutdownGracefully();
        }
    }

    protected abstract SslProvider serverSslProvider();

    protected void verifyResult(AtomicReference<Throwable> error) throws Throwable {
        Throwable cause = error.get();
        if (cause != null) {
            throw cause;
        }
    }
}
