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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.AfterClass;
import org.junit.Test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.Attribute;
import io.netty.util.CharsetUtil;

public class TrafficShapingHandlerTest {

    private static final long READ_LIMIT_BYTES_PER_SECOND = 1;
    private static final ScheduledExecutorService SES = Executors.newSingleThreadScheduledExecutor();
    private static final DefaultEventLoopGroup GROUP = new DefaultEventLoopGroup(1);

    @AfterClass
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

    private void testHandlerRemove0(final AbstractTrafficShapingHandler trafficHandler)
            throws Exception {
        Channel svrChannel = null;
        Channel ch = null;
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.channel(LocalServerChannel.class).group(GROUP, GROUP)
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
            final LocalAddress svrAddr = new LocalAddress("foo");
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
