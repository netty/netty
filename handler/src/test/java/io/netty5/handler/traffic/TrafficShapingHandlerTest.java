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

package io.netty5.handler.traffic;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.util.Attribute;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.EventExecutorGroup;
import io.netty5.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TrafficShapingHandlerTest {

    private static final long READ_LIMIT_BYTES_PER_SECOND = 1;
    private static final EventExecutorGroup SES = new SingleThreadEventExecutor();
    private static final MultithreadEventLoopGroup GROUP =
            new MultithreadEventLoopGroup(1, LocalHandler.newFactory());

    @AfterAll
    public static void destroy() {
        GROUP.shutdownGracefully();
        SES.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
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

    private static void testHandlerRemove0(final AbstractTrafficShapingHandler trafficHandler)
            throws Exception {
        Channel svrChannel = null;
        Channel ch = null;
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.channel(LocalServerChannel.class).group(GROUP, GROUP)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new ChannelHandler() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg)  {
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
                protected void initChannel(Channel ch) {
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
            ch.executor().submit(() -> {
                clientChannel.pipeline().remove("traffic-shaping");
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
