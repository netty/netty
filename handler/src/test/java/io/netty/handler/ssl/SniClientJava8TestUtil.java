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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ThrowableUtil;

import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.util.Collections;

/**
 * In extra class to be able to run tests with java7 without trying to load classes that not exists in java7.
 */
final class SniClientJava8TestUtil {

    private SniClientJava8TestUtil() { }

    static void testSniClient(SslProvider sslClientProvider, SslProvider sslServerProvider, final boolean match)
            throws Exception {
        final String sniHost = "sni.netty.io";
        LocalAddress address = new LocalAddress("test");
        EventLoopGroup group = new DefaultEventLoopGroup(1);
        Channel sc = null;
        Channel cc = null;
        try {
            SelfSignedCertificate cert = new SelfSignedCertificate();
            final SslContext sslServerContext = SslContextBuilder.forServer(cert.key(), cert.cert())
                    .sslProvider(sslServerProvider).build();
            final Promise<Void> promise = group.next().newPromise();
            ServerBootstrap sb = new ServerBootstrap();
            sc = sb.group(group).channel(LocalServerChannel.class).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    SslHandler handler = sslServerContext.newHandler(ch.alloc());
                    SSLParameters parameters = handler.engine().getSSLParameters();
                    SNIMatcher matcher = new SNIMatcher(0) {
                        @Override
                        public boolean matches(SNIServerName sniServerName) {
                            return match;
                        }
                    };
                    parameters.setSNIMatchers(Collections.singleton(matcher));
                    handler.engine().setSSLParameters(parameters);

                    ch.pipeline().addFirst(handler);
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt instanceof SslHandshakeCompletionEvent) {
                                SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                                if (match) {
                                    if (event.isSuccess()) {
                                        promise.setSuccess(null);
                                    } else {
                                        promise.setFailure(event.cause());
                                    }
                                } else {
                                    if (event.isSuccess()) {
                                        promise.setFailure(new AssertionError("expected SSLException"));
                                    } else {
                                        Throwable cause = event.cause();
                                        if (cause instanceof SSLException) {
                                            promise.setSuccess(null);
                                        } else {
                                            promise.setFailure(
                                                    new AssertionError("cause not of type SSLException: "
                                                            + ThrowableUtil.stackTraceToString(cause)));
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            }).bind(address).syncUninterruptibly().channel();

            SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .sslProvider(sslClientProvider).build();

            SslHandler sslHandler = new SslHandler(
                    sslContext.newEngine(ByteBufAllocator.DEFAULT, sniHost, -1));
            Bootstrap cb = new Bootstrap();
            cc = cb.group(group).channel(LocalChannel.class).handler(sslHandler)
                    .connect(address).syncUninterruptibly().channel();

            promise.syncUninterruptibly();
            sslHandler.handshakeFuture().syncUninterruptibly();
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
}
