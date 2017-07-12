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
package io.netty.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import java.util.concurrent.CountDownLatch;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ChannelFactoriesRegistryTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelFactoriesRegistryTest.class);

    @Test
    public void testLocalChannel() throws Exception {
        final LocalAddress TEST_ADDRESS = new LocalAddress("test.id");

        EventLoopGroup group1 = new DefaultEventLoopGroup(2);
        EventLoopGroup group2 = new DefaultEventLoopGroup(2);
        try {
            Bootstrap cb = new Bootstrap();
            ServerBootstrap sb = new ServerBootstrap();

            cb.group(group1)
                .handler(new TestHandler());

            sb.group(group2)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestHandler());
                    }
                });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(TEST_ADDRESS).sync().channel();

                final CountDownLatch latch = new CountDownLatch(1);
                // Connect to the server
                cc = cb.connect(sc.localAddress()).sync().channel();
                final Channel ccCpy = cc;
                cc.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        // Send a message event up the pipeline.
                        ccCpy.pipeline().fireChannelRead("Hello, World");
                        latch.countDown();
                    }
                });
                assertTrue(latch.await(5, SECONDS));

                // Close the channel
                closeChannel(cc);
                closeChannel(sc);
                sc.closeFuture().sync();
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        } finally {
            Future<?> group1Future = group1.shutdownGracefully(0, 0, SECONDS);
            Future<?> group2Future = group2.shutdownGracefully(0, 0, SECONDS);

            group1Future.await();
            group2Future.await();
        }
    }

    @Test
    public void testLocalChannelWithGroupSubClass() throws Exception {
        final LocalAddress TEST_ADDRESS = new LocalAddress("test.id");

        EventLoopGroup group1 = new DefaultEventLoopGroup(2) {
        };
        EventLoopGroup group2 = new DefaultEventLoopGroup(2) {
        };
        try {
            Bootstrap cb = new Bootstrap();
            ServerBootstrap sb = new ServerBootstrap();

            cb.group(group1)
                .handler(new TestHandler());

            sb.group(group2)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestHandler());
                    }
                });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(TEST_ADDRESS).sync().channel();

                final CountDownLatch latch = new CountDownLatch(1);
                // Connect to the server
                cc = cb.connect(sc.localAddress()).sync().channel();
                final Channel ccCpy = cc;
                cc.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        // Send a message event up the pipeline.
                        ccCpy.pipeline().fireChannelRead("Hello, World");
                        latch.countDown();
                    }
                });
                assertTrue(latch.await(5, SECONDS));

                // Close the channel
                closeChannel(cc);
                closeChannel(sc);
                sc.closeFuture().sync();
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        } finally {
            Future<?> group1Future = group1.shutdownGracefully(0, 0, SECONDS);
            Future<?> group2Future = group2.shutdownGracefully(0, 0, SECONDS);

            group1Future.await();
            group2Future.await();
        }
    }

    @Test
    public void testNioChannel() throws Exception {

        EventLoopGroup group1 = new NioEventLoopGroup(2);
        EventLoopGroup group2 = new NioEventLoopGroup(2);
        try {
            Bootstrap cb = new Bootstrap();
            ServerBootstrap sb = new ServerBootstrap();

            cb.group(group1)
                .handler(new TestHandler());

            sb.group(group2)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    public void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestHandler());
                    }
                });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(0).sync().channel();

                final CountDownLatch latch = new CountDownLatch(1);
                // Connect to the server
                cc = cb.connect(sc.localAddress()).sync().channel();
                final Channel ccCpy = cc;
                cc.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        // Send a message event up the pipeline.
                        ccCpy.pipeline().fireChannelRead("Hello, World");
                        latch.countDown();
                    }
                });
                assertTrue(latch.await(5, SECONDS));

                // Close the channel
                closeChannel(cc);
                closeChannel(sc);
                sc.closeFuture().sync();
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        } finally {
            Future<?> group1Future = group1.shutdownGracefully(0, 0, SECONDS);
            Future<?> group2Future = group2.shutdownGracefully(0, 0, SECONDS);

            group1Future.await();
            group2Future.await();
        }
    }

    static class TestHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            logger.info(String.format("Received message: %s", msg));
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    private static void closeChannel(Channel cc) {
        if (cc != null) {
            cc.close().syncUninterruptibly();
        }
    }
}
