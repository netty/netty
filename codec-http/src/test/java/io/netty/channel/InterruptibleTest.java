/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;

/**
 * This set of unit tests shows some aspects of {@link Interruptible#interrupt()} by making use of
 * HTTP Pipelining.
 */
public class InterruptibleTest {

    private static final int RANDOM_PORT = 0;

    private static final int NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD = 1000;

    private static EventLoopGroup GROUP;

    @BeforeClass
    public static void init() {
        GROUP = new NioEventLoopGroup();
    }

    @AfterClass
    public static void destroy() {
        GROUP.shutdownGracefully();
    }

    /**
     * This unit test shows a naive way of trying to implement flow-control in Netty by overriding
     * the {@link ChannelOutboundHandler#read(ChannelHandlerContext)} method with the intent to
     * suppress read operations.
     *
     * It will not work because an attacker can keep our receive buffers filled.
     */
    @Test(timeout = 5000L)
    public void testAttemptDisablingRead() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);

        Channel server = newServer(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast(new ChannelDuplexHandler() {
                    private boolean reading = true;

                    @Override
                    public void read(ChannelHandlerContext ctx) throws Exception {
                        if (reading) {
                            ctx.read();
                        }
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ReferenceCountUtil.release(msg);

                        if (msg instanceof HttpRequest) {
                            counter.incrementAndGet();
                            latch.countDown();

                            //
                            // This is a futile attempt because deep down in the guts of the Channel
                            // implementation there is a loop which will consume all available bytes
                            // and the only way to break out of it is to disable auto reading or when
                            // all bytes have been consumed.
                            //
                            // If you wanted to implement flow-control this way you're vulnerable to
                            // things like HTTP pipelining because it's easy to squeeze 100s if not
                            // a thousand(s) HTTP requests (speaking in terms of bytes) into the socket's
                            // receive buffer and Netty will happily decode them into higher level POJOs.
                            //
                            reading = false;
                        }
                    }
                });
            }
        });

        try {
            InetSocketAddress address = (InetSocketAddress) server.localAddress();
            Channel client = newClient(address, new ChannelInboundHandlerAdapter() {
            });

            try {
                ByteBuf request = newRequest(NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);
                client.writeAndFlush(request).sync();

                boolean complete = latch.await(1L, TimeUnit.SECONDS);
                int count = counter.get();

                // Disabling the read(...) method has no effect because Netty has no reason to exit its
                // internal reader loop and we end up reading all NUMBER_OF_REQUESTS.
                assertTrue("count=" + count, complete);
                assertEquals("count=" + count, NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD, count);
            } finally {
                client.close().sync();
            }
        } finally {
            server.close().sync();
        }
    }

    /**
     * This unit test is demonstrating that it's working with {@link ChannelConfig#setAutoRead(boolean)}
     * but it's conceptually strange that one has to disable auto reading to make it work.
     */
    @Test(timeout = 5000L)
    public void testDisableAutoReading() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);

        Channel server = newServer(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast(new ChannelDuplexHandler() {
                    private boolean reading = true;

                    @Override
                    public void read(ChannelHandlerContext ctx) throws Exception {
                        if (reading) {
                            ctx.read();
                        }
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ReferenceCountUtil.release(msg);

                        if (msg instanceof HttpRequest) {
                            counter.incrementAndGet();
                            latch.countDown();

                            // Disabling auto reading works but it gets tricky with complex pipelines
                            // and if there is more than one handler that fiddles with auto reading.
                            // How do handlers coordinate amongst each other when to enable reading?
                            //
                            // Wouldn't it be better if you could think of your pipeline something like
                            // an OSI layers stack and each handler can make their own local decisions?
                            reading = false;
                            ctx.channel().config().setAutoRead(false);
                        }
                    }
                });
            }
        });

        try {
            InetSocketAddress address = (InetSocketAddress) server.localAddress();
            Channel client = newClient(address, new ChannelInboundHandlerAdapter() {
            });

            try {
                // A bunch of HTTP requests concatenated together.
                ByteBuf request = newRequest(NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);
                client.writeAndFlush(request).sync();

                boolean complete = latch.await(1L, TimeUnit.SECONDS);
                int count = counter.get();

                assertFalse("count=" + count, complete);
                assertTrue("count=" + count, count > 1 && count < NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);
            } finally {
                client.close().sync();
            }
        } finally {
            server.close().sync();
        }
    }

    /**
     * This unit test shows basic {@link Interruptible#interrupt()} without disabling
     * auto reading.
     */
    @Test(timeout = 5000L)
    public void testBasicInterruptReading() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);

        Channel server = newServer(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast(new ChannelDuplexHandler() {
                    private boolean reading = true;
                    @Override
                    public void read(ChannelHandlerContext ctx) throws Exception {
                        if (reading) {
                            ctx.read();
                        }
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ReferenceCountUtil.release(msg);

                        if (msg instanceof HttpRequest) {
                            counter.incrementAndGet();
                            latch.countDown();

                            // Break out of the reader loop without outright disabling auto reading!
                            reading = false;
                            ((Interruptible) ctx.channel()).interrupt();
                        }
                    }
                });
            }
        });

        try {
            InetSocketAddress address = (InetSocketAddress) server.localAddress();
            Channel client = newClient(address, new ChannelInboundHandlerAdapter() {
            });

            try {
                // A bunch of HTTP requests concatenated together.
                ByteBuf request = newRequest(NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);
                client.writeAndFlush(request).sync();

                boolean complete = latch.await(1L, TimeUnit.SECONDS);
                int count = counter.get();

                assertFalse("count=" + count, complete);
                assertTrue("count=" + count, count > 1 && count < NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);
            } finally {
                client.close().sync();
            }
        } finally {
            server.close().sync();
        }
    }

    /**
     * Same as {@link #testBasicInterruptReading()} but with the addition of {@link Channel#read()}
     * and showing that a handler can break out of reading and can suppress further reading without
     * having to wield the global auto reading flag.
     */
    @Test(timeout = 5000L)
    public void testAdvancedInterruptReading() throws Exception {
        final AtomicBoolean reading = new AtomicBoolean(true);
        final AtomicReference<Channel> peerRef = new AtomicReference<Channel>();
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);

        Channel server = newServer(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast(new ChannelDuplexHandler() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        peerRef.set(ctx.channel());
                        ctx.fireChannelActive();
                    }

                    @Override
                    public void read(ChannelHandlerContext ctx) throws Exception {
                        if (reading.get()) {
                            ctx.read();
                        }
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ReferenceCountUtil.release(msg);

                        if (msg instanceof HttpRequest) {
                            counter.incrementAndGet();
                            latch.countDown();

                            // Break out of reading without outright disabling auto reading.
                            reading.set(false);
                            ((Interruptible) ctx.channel()).interrupt();
                        }
                    }
                });
            }
        });

        try {
            InetSocketAddress address = (InetSocketAddress) server.localAddress();
            Channel client = newClient(address, new ChannelInboundHandlerAdapter() {
            });

            try {
                // A bunch of HTTP requests concatenated together.
                ByteBuf request = newRequest(NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);
                client.writeAndFlush(request).sync();

                boolean complete1 = latch.await(1L, TimeUnit.SECONDS);
                int count1 = counter.get();

                assertFalse("count=" + count1, complete1);
                assertTrue("count=" + count1, count1 > 1 && count1 < NUMBER_OF_REQUESTS_IN_ONE_PAYLOAD);

                // The reading flag should be false.
                assertFalse(reading.get());

                // Let's try to call read() but one of the handlers in the pipeline is suppressing it.
                Channel peer = peerRef.get();
                peer.read();

                // Verify the above statement. Nothing should have changed. The read() call got intercepted
                // by the handler and no new bytes were consumed from the buffers or network wire.
                boolean complete2 = latch.await(1L, TimeUnit.SECONDS);
                int count2 = counter.get();

                assertFalse("count=" + count2, complete2);
                assertEquals(count1, count2);

                // Let's try it again. This time we're changing the handler's flag which suppresses reading
                // and it should work as expected.
                reading.set(true);
                peer.read();

                boolean complete3 = latch.await(1L, TimeUnit.SECONDS);
                int count3 = counter.get();

                assertFalse("count=" + count3, complete3);
                assertTrue("count=" + count3, count3 > count1);

                assertFalse(reading.get());
            } finally {
                client.close().sync();
            }
        } finally {
            server.close().sync();
        }
    }

    private static Channel newServer(ChannelHandler handler) throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.channel(NioServerSocketChannel.class)
                .group(GROUP)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(handler);

        return bootstrap.bind(RANDOM_PORT).sync().channel();
    }

    private static Channel newClient(InetSocketAddress address, ChannelHandler handler)
            throws Exception {
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.channel(NioSocketChannel.class)
                .group(GROUP)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(handler);

        return bootstrap.connect("localhost", address.getPort()).sync().channel();
    }

    private static ByteBuf newRequest(int count) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < count; i++) {
            sb.append("GET /hello HTTP/1.1\r\n")
                .append("host: example.com\r\n")
                .append("\r\n");
        }

        String value = sb.toString();
        return Unpooled.wrappedBuffer(value.getBytes(StandardCharsets.UTF_8));
    }
}
