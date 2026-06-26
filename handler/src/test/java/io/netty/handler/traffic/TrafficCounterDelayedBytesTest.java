/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.traffic;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link TrafficCounter#lastDelayedReadBytes()} and
 * {@link TrafficCounter#lastDelayedWriteBytes()} correctly reflect the number of bytes
 * that were throttled during the last accounting interval.
 *
 * <p>Covers the enhancement requested in
 * <a href="https://github.com/netty/netty/issues/5739">GitHub issue #5739</a>:
 * expose throttling facts to subclasses via {@code doAccounting()} without requiring
 * instrumentation of the handler internals.
 */
public class TrafficCounterDelayedBytesTest {

    private static final long CHECK_INTERVAL_MS = 200;
    private static final long THROTTLE_LIMIT = 1L; // 1 byte/s — any real payload is throttled

    private static final EventLoopGroup GROUP =
            new MultiThreadIoEventLoopGroup(2, LocalIoHandler.newFactory());

    @AfterAll
    static void destroy() {
        GROUP.shutdownGracefully();
    }

    /**
     * When the read limit is set aggressively low and a large message arrives,
     * {@code lastDelayedReadBytes()} must be non-zero after {@code doAccounting()} fires,
     * and {@code lastDelayedWriteBytes()} must remain zero.
     */
    @Test
    public void testLastDelayedReadBytesNonZeroWhenThrottled() throws Exception {
        final CountDownLatch throttleSeen = new CountDownLatch(1);
        final AtomicLong capturedDelayedRead = new AtomicLong();
        final AtomicLong capturedDelayedWrite = new AtomicLong();

        ChannelTrafficShapingHandler trafficHandler = new ChannelTrafficShapingHandler(
                0, THROTTLE_LIMIT, CHECK_INTERVAL_MS) {
            @Override
            protected void doAccounting(TrafficCounter counter) {
                long delayed = counter.lastDelayedReadBytes();
                if (delayed > 0 && throttleSeen.getCount() > 0) {
                    capturedDelayedRead.set(delayed);
                    capturedDelayedWrite.set(counter.lastDelayedWriteBytes());
                    throttleSeen.countDown();
                }
            }
        };

        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            serverChannel = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(GROUP)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    // echo back so the client channel's read limit is exercised
                                    ctx.writeAndFlush(msg);
                                }
                            });
                        }
                    })
                    .bind(new LocalAddress("test-delayed-read"))
                    .sync().channel();

            clientChannel = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(GROUP)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(trafficHandler);
                        }
                    })
                    .connect(new LocalAddress("test-delayed-read"))
                    .sync().channel();

            // Send 1024 bytes — far exceeds the 1 byte/s read limit on the client
            clientChannel.writeAndFlush(
                    Unpooled.wrappedBuffer(new byte[1024])).sync();

            assertTrue(throttleSeen.await(5, TimeUnit.SECONDS),
                    "doAccounting() never observed non-zero lastDelayedReadBytes");

            assertTrue(capturedDelayedRead.get() > 0,
                    "Expected lastDelayedReadBytes > 0, was: " + capturedDelayedRead.get());
            assertEquals(0, capturedDelayedWrite.get(),
                    "Expected lastDelayedWriteBytes == 0 when only read is throttled");
        } finally {
            if (clientChannel != null) clientChannel.close().sync();
            if (serverChannel != null) serverChannel.close().sync();
        }
    }

    /**
     * When the write limit is set aggressively low and a large message is written,
     * {@code lastDelayedWriteBytes()} must be non-zero after {@code doAccounting()} fires,
     * and {@code lastDelayedReadBytes()} must remain zero.
     */
    @Test
    public void testLastDelayedWriteBytesNonZeroWhenThrottled() throws Exception {
        final CountDownLatch throttleSeen = new CountDownLatch(1);
        final AtomicLong capturedDelayedRead = new AtomicLong();
        final AtomicLong capturedDelayedWrite = new AtomicLong();

        ChannelTrafficShapingHandler trafficHandler = new ChannelTrafficShapingHandler(
                THROTTLE_LIMIT, 0, CHECK_INTERVAL_MS) {
            @Override
            protected void doAccounting(TrafficCounter counter) {
                long delayed = counter.lastDelayedWriteBytes();
                if (delayed > 0 && throttleSeen.getCount() > 0) {
                    capturedDelayedWrite.set(delayed);
                    capturedDelayedRead.set(counter.lastDelayedReadBytes());
                    throttleSeen.countDown();
                }
            }
        };

        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            serverChannel = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(GROUP)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
                        }
                    })
                    .bind(new LocalAddress("test-delayed-write"))
                    .sync().channel();

            clientChannel = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(GROUP)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(trafficHandler);
                        }
                    })
                    .connect(new LocalAddress("test-delayed-write"))
                    .sync().channel();

            // Send 1024 bytes — far exceeds the 1 byte/s write limit
            clientChannel.writeAndFlush(
                    Unpooled.wrappedBuffer(new byte[1024])).sync();

            assertTrue(throttleSeen.await(5, TimeUnit.SECONDS),
                    "doAccounting() never observed non-zero lastDelayedWriteBytes");

            assertTrue(capturedDelayedWrite.get() > 0,
                    "Expected lastDelayedWriteBytes > 0, was: " + capturedDelayedWrite.get());
            assertEquals(0, capturedDelayedRead.get(),
                    "Expected lastDelayedReadBytes == 0 when only write is throttled");
        } finally {
            if (clientChannel != null) clientChannel.close().sync();
            if (serverChannel != null) serverChannel.close().sync();
        }
    }

    /**
     * When no limits are configured, both delayed byte counters must be zero
     * after {@code doAccounting()} fires.
     */
    @Test
    public void testDelayedBytesZeroWhenNoThrottling() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicLong capturedDelayedRead = new AtomicLong(-1);
        final AtomicLong capturedDelayedWrite = new AtomicLong(-1);

        ChannelTrafficShapingHandler trafficHandler = new ChannelTrafficShapingHandler(
                0, 0, CHECK_INTERVAL_MS) {
            @Override
            protected void doAccounting(TrafficCounter counter) {
                if (latch.getCount() > 0) {
                    capturedDelayedRead.set(counter.lastDelayedReadBytes());
                    capturedDelayedWrite.set(counter.lastDelayedWriteBytes());
                    latch.countDown();
                }
            }
        };

        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            serverChannel = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(GROUP)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
                        }
                    })
                    .bind(new LocalAddress("test-no-throttle"))
                    .sync().channel();

            clientChannel = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(GROUP)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(trafficHandler);
                        }
                    })
                    .connect(new LocalAddress("test-no-throttle"))
                    .sync().channel();

            clientChannel.writeAndFlush(
                    Unpooled.wrappedBuffer(new byte[1024])).sync();

            assertTrue(latch.await(5, TimeUnit.SECONDS),
                    "doAccounting() did not fire within timeout");

            assertEquals(0, capturedDelayedRead.get(),
                    "Expected lastDelayedReadBytes == 0 when no read limit configured");
            assertEquals(0, capturedDelayedWrite.get(),
                    "Expected lastDelayedWriteBytes == 0 when no write limit configured");
        } finally {
            if (clientChannel != null) clientChannel.close().sync();
            if (serverChannel != null) serverChannel.close().sync();
        }
    }

    /**
     * After an interval where throttling occurred, if the next interval has no throttling,
     * the delayed byte counters must reset to zero — they must not accumulate across intervals.
     */
    @Test
    public void testDelayedBytesResetToZeroAfterQuietInterval() throws Exception {
        final CountDownLatch throttleSeen = new CountDownLatch(1);
        final CountDownLatch quietSeen = new CountDownLatch(1);
        final AtomicLong lastDelayedRead = new AtomicLong(-1);

        ChannelTrafficShapingHandler trafficHandler = new ChannelTrafficShapingHandler(
                0, THROTTLE_LIMIT, CHECK_INTERVAL_MS) {
            @Override
            protected void doAccounting(TrafficCounter counter) {
                long delayed = counter.lastDelayedReadBytes();
                lastDelayedRead.set(delayed);
                if (throttleSeen.getCount() > 0) {
                    if (delayed > 0) {
                        throttleSeen.countDown();
                    }
                } else if (quietSeen.getCount() > 0) {
                    quietSeen.countDown();
                }
            }
        };

        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            serverChannel = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(GROUP)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ctx.writeAndFlush(msg);
                                }
                            });
                        }
                    })
                    .bind(new LocalAddress("test-reset"))
                    .sync().channel();

            clientChannel = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(GROUP)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(trafficHandler);
                        }
                    })
                    .connect(new LocalAddress("test-reset"))
                    .sync().channel();

            // First interval: send throttled traffic
            clientChannel.writeAndFlush(
                    Unpooled.wrappedBuffer(new byte[1024])).sync();

            assertTrue(throttleSeen.await(5, TimeUnit.SECONDS),
                    "First doAccounting() with delayed bytes did not fire within timeout");

            // No more traffic — next tick must see zero
            assertTrue(quietSeen.await(5, TimeUnit.SECONDS),
                    "Second doAccounting() (quiet interval) did not fire within timeout");

            assertEquals(0, lastDelayedRead.get(),
                    "Expected lastDelayedReadBytes to reset to zero in a quiet interval — " +
                    "counters must not accumulate across intervals");
        } finally {
            if (clientChannel != null) clientChannel.close().sync();
            if (serverChannel != null) serverChannel.close().sync();
        }
    }
}
