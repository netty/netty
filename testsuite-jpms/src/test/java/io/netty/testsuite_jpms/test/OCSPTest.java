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
package io.netty.testsuite_jpms.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.ocsp.OcspResponse;
import io.netty.handler.ssl.ocsp.OcspServerCertificateValidator;
import io.netty.handler.ssl.ocsp.OcspValidationEvent;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class OCSPTest {

    @Test
    public void testSimpleQueryTest() throws Exception {
        final AtomicBoolean ocspStatus = new AtomicBoolean();
        EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(sslContext.newHandler(ch.alloc(), "netty.io", 443));
                            pipeline.addLast(new OcspServerCertificateValidator(false));
                            pipeline.addLast(new SimpleChannelInboundHandler<>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    // NOOP
                                }

                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof OcspValidationEvent) {
                                        OcspValidationEvent event = (OcspValidationEvent) evt;

                                        ocspStatus.set(event.response().status() == OcspResponse.Status.VALID);
                                        ctx.channel().close();
                                        latch.countDown();
                                    }
                                }
                            });
                        }
                    });

            ChannelFuture channelFuture = bootstrap.connect("netty.io", 443);
            channelFuture.sync();

            // Wait for maximum of 1 minute for Ocsp validation to happen
            latch.await(1, TimeUnit.MINUTES);
            assertTrue(ocspStatus.get());

            // Wait for Channel to be closed
            channelFuture.channel().closeFuture().sync();
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }

}
