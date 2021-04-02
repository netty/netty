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
package io.netty.handler.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public abstract class RenegotiateTest {

    @Test(timeout = 30000)
    public void testRenegotiateServer() throws Throwable {
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(2);
        SelfSignedCertificate cert = new SelfSignedCertificate();
        EventLoopGroup group = new LocalEventLoopGroup();
        try {
            final SslContext context = SslContextBuilder.forServer(cert.key(), cert.cert())
                    .sslProvider(serverSslProvider())
                    .protocols(SslUtils.PROTOCOL_TLS_V1_2)
                    .build();

            ServerBootstrap sb = new ServerBootstrap();
            sb.group(group).channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            SslHandler handler = context.newHandler(ch.alloc());
                            handler.setHandshakeTimeoutMillis(0);
                            ch.pipeline().addLast(handler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                private boolean renegotiate;

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    ReferenceCountUtil.release(msg);
                                }

                                @Override
                                public void userEventTriggered(
                                        final ChannelHandlerContext ctx, Object evt) throws Exception {
                                    if (!renegotiate && evt instanceof SslHandshakeCompletionEvent) {
                                        SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;

                                        if (event.isSuccess()) {
                                            final SslHandler handler = ctx.pipeline().get(SslHandler.class);

                                            renegotiate = true;
                                            handler.renegotiate().addListener(new FutureListener<Channel>() {
                                                @Override
                                                public void operationComplete(Future<Channel> future) throws Exception {
                                                    if (!future.isSuccess()) {
                                                        error.compareAndSet(null, future.cause());
                                                        ctx.close();
                                                    }
                                                    latch.countDown();
                                                }
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
            Channel channel = sb.bind(new LocalAddress("test")).syncUninterruptibly().channel();

            final SslContext clientContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .sslProvider(SslProvider.JDK)
                    .protocols(SslUtils.PROTOCOL_TLS_V1_2)
                    .build();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(LocalChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            SslHandler handler = clientContext.newHandler(ch.alloc());
                            handler.setHandshakeTimeoutMillis(0);
                            ch.pipeline().addLast(handler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void userEventTriggered(
                                        ChannelHandlerContext ctx, Object evt) throws Exception {
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

            Channel clientChannel = bootstrap.connect(channel.localAddress()).syncUninterruptibly().channel();
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
