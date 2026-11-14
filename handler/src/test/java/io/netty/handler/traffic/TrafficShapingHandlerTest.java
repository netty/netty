/*
 * Copyright 2020 The Netty Project
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

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.Attribute;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrafficShapingHandlerTest {

    private static final long READ_LIMIT_BYTES_PER_SECOND = 1;
    private static final long WRITE_LIMIT_BYTES_PER_SECOND = 1;
    private static final ScheduledExecutorService SES = Executors.newSingleThreadScheduledExecutor();
    private static final DefaultEventLoopGroup GROUP = new DefaultEventLoopGroup(1);

    @AfterAll
    public static void destroy() {
        GROUP.shutdownGracefully();
        SES.shutdown();
    }

    @Test
    public void testHandlerRemove() throws Exception {
        testHandlerRemove0(new ChannelTrafficShapingHandler(0, READ_LIMIT_BYTES_PER_SECOND));
        GlobalTrafficShapingHandler trafficHandler1 =
                new GlobalTrafficShapingHandler(SES, 0, READ_LIMIT_BYTES_PER_SECOND);
        try {
            testHandlerRemove0(trafficHandler1);
        } finally {
            trafficHandler1.release();
        }
        GlobalChannelTrafficShapingHandler trafficHandler2 =
                new GlobalChannelTrafficShapingHandler(SES, 0,
                        READ_LIMIT_BYTES_PER_SECOND, 0, READ_LIMIT_BYTES_PER_SECOND);
        try {
            testHandlerRemove0(trafficHandler2);
        } finally {
            trafficHandler2.release();
        }
    }

    @Test
    public void testQueuedWritesReleasedAndFailedOnClose() {
        testQueuedWritesReleasedAndFailedOnClose0(new ChannelTrafficShapingHandler(
                WRITE_LIMIT_BYTES_PER_SECOND, 0, 0));

        GlobalTrafficShapingHandler trafficHandler1 =
                new GlobalTrafficShapingHandler(SES, WRITE_LIMIT_BYTES_PER_SECOND, 0, 0);
        try {
            testQueuedWritesReleasedAndFailedOnClose0(trafficHandler1);
        } finally {
            trafficHandler1.release();
        }

        GlobalChannelTrafficShapingHandler trafficHandler2 =
                new GlobalChannelTrafficShapingHandler(SES, WRITE_LIMIT_BYTES_PER_SECOND, 0,
                        WRITE_LIMIT_BYTES_PER_SECOND, 0, 0);
        try {
            testQueuedWritesReleasedAndFailedOnClose0(trafficHandler2);
        } finally {
            trafficHandler2.release();
        }
    }

    private static void testQueuedWritesReleasedAndFailedOnClose0(AbstractTrafficShapingHandler trafficHandler) {
        EmbeddedChannel ch = new EmbeddedChannel(trafficHandler);
        ByteBufHolder holder = new DefaultByteBufHolder(Unpooled.buffer(64).writeZero(64));
        ChannelPromise promise = ch.newPromise();

        ch.writeOneOutbound(holder, promise);
        assertFalse(promise.isDone());
        assertNull(ch.readOutbound());
        assertEquals(1, holder.refCnt());

        ch.close().syncUninterruptibly();
        assertEquals(0, holder.refCnt());
        assertTrue(promise.isDone());
        assertInstanceOf(ClosedChannelException.class, promise.cause());
        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testChannelsWithCollidingHashCodesDoNotShareState() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        GlobalTrafficShapingHandler handler1 =
                new GlobalTrafficShapingHandler(executor, 100, 0, 1000, 500);
        try {
            testChannelsWithCollidingHashCodesDoNotShareState0(handler1);
        } finally {
            handler1.release();
        }
        GlobalChannelTrafficShapingHandler handler2 =
                new GlobalChannelTrafficShapingHandler(executor, 100, 0, 100, 0, 1000, 500);
        try {
            testChannelsWithCollidingHashCodesDoNotShareState0(handler2);
        } finally {
            handler2.release();
        }
        executor.shutdownNow();
    }

    private static void testChannelsWithCollidingHashCodesDoNotShareState0(
            AbstractTrafficShapingHandler handler) {
        EmbeddedChannel a = new EmbeddedChannel(new CollidingChannelId("A"), handler);
        EmbeddedChannel b = new EmbeddedChannel(new CollidingChannelId("B"), handler);
        try {
            if (handler instanceof GlobalChannelTrafficShapingHandler) {
                assertEquals(2, ((GlobalChannelTrafficShapingHandler) handler).channelTrafficCounters().size());
            }
            ChannelFuture writeOnB = b.writeAndFlush(Unpooled.wrappedBuffer(new byte[1000]));
            assertFalse(writeOnB.isDone());
            a.close().syncUninterruptibly();
            assertTrue(b.isActive());
            assertNull(writeOnB.cause());
            long queuesSize = handler instanceof GlobalTrafficShapingHandler ?
                    ((GlobalTrafficShapingHandler) handler).queuesSize() :
                    ((GlobalChannelTrafficShapingHandler) handler).queuesSize();
            assertEquals(1000, queuesSize);
        } finally {
            a.finishAndReleaseAll();
            b.finishAndReleaseAll();
        }
    }

    /**
     * A {@link ChannelId} with a constant {@code hashCode}, like two distinct
     * {@code DefaultChannelId}s whose 32-bit hashes collide.
     */
    private static final class CollidingChannelId implements ChannelId {
        private final String name;

        CollidingChannelId(String name) {
            this.name = name;
        }

        @Override
        public String asShortText() {
            return name;
        }

        @Override
        public String asLongText() {
            return name;
        }

        @Override
        public int compareTo(ChannelId o) {
            return name.compareTo(o.asLongText());
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CollidingChannelId && ((CollidingChannelId) o).name.equals(name);
        }
    }

    private void testHandlerRemove0(final AbstractTrafficShapingHandler trafficHandler)
            throws Exception {
        Channel svrChannel = null;
        Channel ch = null;
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.channel(LocalServerChannel.class).group(GROUP)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    ctx.writeAndFlush(msg);
                                }
                            });
                        }
                    });
            final LocalAddress svrAddr = new LocalAddress(TrafficShapingHandlerTest.class);
            svrChannel = serverBootstrap.bind(svrAddr).sync().channel();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.channel(LocalChannel.class).group(GROUP).handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast("traffic-shaping", trafficHandler);
                }
            });
            ch = bootstrap.connect(svrAddr).sync().channel();
            Attribute<Runnable> attr = ch.attr(AbstractTrafficShapingHandler.REOPEN_TASK);
            assertNull(attr.get());
            ch.writeAndFlush(Unpooled.wrappedBuffer("foo".getBytes(CharsetUtil.UTF_8)));
            ch.writeAndFlush(Unpooled.wrappedBuffer("bar".getBytes(CharsetUtil.UTF_8))).await();
            assertNotNull(attr.get());
            final Channel clientChannel = ch;
            ch.eventLoop().submit(new Runnable() {
                @Override
                public void run() {
                    clientChannel.pipeline().remove("traffic-shaping");
                }
            }).await();
            //the attribute--reopen task must be released.
            assertNull(attr.get());
        } finally {
            if (ch != null) {
                ch.close().sync();
            }
            if (svrChannel != null) {
                svrChannel.close().sync();
            }
        }
    }

}
