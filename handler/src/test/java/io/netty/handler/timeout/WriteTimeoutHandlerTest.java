/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.timeout;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;

public class WriteTimeoutHandlerTest {

    @Test
    public void testPromiseUseDifferentExecutor() throws Exception {
        EventExecutorGroup group1 = new DefaultEventExecutorGroup(1);
        EventExecutorGroup group2 = new DefaultEventExecutorGroup(1);
        EmbeddedChannel channel = new EmbeddedChannel(false, false);
        try {
            channel.pipeline().addLast(group1, new WriteTimeoutHandler(10000));
            final CountDownLatch latch = new CountDownLatch(1);
            channel.pipeline().addLast(group2, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.writeAndFlush("something").addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            latch.countDown();
                        }
                    });
                }
            });

            channel.register();
            latch.await();
            assertTrue(channel.finishAndReleaseAll());
        } finally {
            group1.shutdownGracefully();
            group2.shutdownGracefully();
        }
    }
}
