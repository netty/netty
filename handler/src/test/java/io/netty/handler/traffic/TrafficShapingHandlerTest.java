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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.Attribute;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("FIX implementation")
public class TrafficShapingHandlerTest {

    private static final long READ_LIMIT_BYTES_PER_SECOND = 1;
    private static final EventExecutorGroup SES = new DefaultEventExecutorGroup(1);
    private static final long WRITE_LIMIT_BYTES_PER_SECOND = 1;
    private static final EventLoopGroup GROUP = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());

    @AfterAll
    public static void destroy() {
        GROUP.shutdownGracefully();
        SES.shutdownGracefully();
    }

    @Test
    public void testHandlerRemove() throws Exception {
        testHandlerRemove0(new ChannelTrafficShapingHandler(0, READ_LIMIT_BYTES_PER_SECOND));
        GlobalTrafficShapingHandler trafficHandler1 =
                new GlobalTrafficShapingHandler(SES.next(), 0, READ_LIMIT_BYTES_PER_SECOND);
        try {
            testHandlerRemove0(trafficHandler1);
        } finally {
            trafficHandler1.release();
        }
        GlobalChannelTrafficShapingHandler trafficHandler2 =
                new GlobalChannelTrafficShapingHandler(SES.next(), 0,
                        READ_LIMIT_BYTES_PER_SECOND, 0, READ_LIMIT_BYTES_PER_SECOND);
        try {
            testHandlerRemove0(trafficHandler2);
        } finally {
            trafficHandler2.release();
        }
    }

    @Test
    public void testQueuedWritesReleasedAndFailedOnClose() throws Exception {
        testQueuedWritesReleasedAndFailedOnClose0(new ChannelTrafficShapingHandler(
                WRITE_LIMIT_BYTES_PER_SECOND, 0, 0));

        GlobalTrafficShapingHandler trafficHandler1 =
                new GlobalTrafficShapingHandler(SES.next(), WRITE_LIMIT_BYTES_PER_SECOND, 0, 0);
        try {
            testQueuedWritesReleasedAndFailedOnClose0(trafficHandler1);
        } finally {
            trafficHandler1.release();
        }

        GlobalChannelTrafficShapingHandler trafficHandler2 =
                new GlobalChannelTrafficShapingHandler(SES.next(), WRITE_LIMIT_BYTES_PER_SECOND, 0,
                        WRITE_LIMIT_BYTES_PER_SECOND, 0, 0);
        try {
            testQueuedWritesReleasedAndFailedOnClose0(trafficHandler2);
        } finally {
            trafficHandler2.release();
        }
    }

    private static void testQueuedWritesReleasedAndFailedOnClose0(AbstractTrafficShapingHandler trafficHandler)
            throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(trafficHandler);
        ByteBufHolder holder = new DefaultByteBufHolder(Unpooled.buffer(64).writeZero(64));
        Promise<Void> promise = ch.newPromise();

        ch.writeOneOutbound(holder, promise);
        assertFalse(promise.isDone());
        assertNull(ch.readOutbound());
        assertEquals(1, holder.refCnt());

        ch.close().syncUninterruptibly();
        assertEquals(0, holder.refCnt());
        assertTrue(promise.isDone());
        assertTrue(promise.cause() instanceof ClosedChannelException);
        assertFalse(ch.finishAndReleaseAll());
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
                            ch.pipeline().addLast(new ChannelInboundHandler() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    ctx.writeAndFlush(msg);
                                }
                            });
                        }
                    });
            final LocalAddress svrAddr = new LocalAddress("foo");
            svrChannel = serverBootstrap.bind(svrAddr).get();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.channel(LocalChannel.class).group(GROUP).handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast("traffic-shaping", trafficHandler);
                }
            });
            ch = bootstrap.connect(svrAddr).get();
            Attribute<Runnable> attr = ch.attr(AbstractTrafficShapingHandler.REOPEN_TASK);
            assertNull(attr.get());
            ch.writeAndFlush(Unpooled.wrappedBuffer("foo".getBytes(CharsetUtil.UTF_8)));
            ch.writeAndFlush(Unpooled.wrappedBuffer("bar".getBytes(CharsetUtil.UTF_8))).await();
            assertNotNull(attr.get());
            final Channel clientChannel = ch;
            ch.executor().submit(new Runnable() {
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
