/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.group;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultChannelGroupTest {

    // Test for #1183
    @Test
    public void testNotThrowBlockingOperationException() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        ServerBootstrap b = new ServerBootstrap();
        b.group(group);
        b.childHandler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                allChannels.add(ctx.channel());
            }
        });
        b.channel(NioServerSocketChannel.class);

        ChannelFuture f = b.bind(0).syncUninterruptibly();

        if (f.isSuccess()) {
            allChannels.add(f.channel());
            allChannels.close().awaitUninterruptibly();
        }

        group.shutdownGracefully();
        group.terminationFuture().sync();
    }

    /**
     * Awaiting a group future while holding its monitor must not keep the child listener from completing it. The
     * listener runs on an event executor thread, so blocking it would stall everything else queued on that thread.
     */
    @Test
    @Timeout(value = 30)
    public void testAwaitWhileHoldingMonitorDoesNotBlockCompletion() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultChannelPromise child = new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE);
        Map<Channel, ChannelFuture> futures = new LinkedHashMap<Channel, ChannelFuture>();
        futures.put(channel, child);

        final DefaultChannelGroupFuture groupFuture = new DefaultChannelGroupFuture(
                new DefaultChannelGroup(GlobalEventExecutor.INSTANCE), futures, GlobalEventExecutor.INSTANCE);

        final CountDownLatch holdsMonitor = new CountDownLatch(1);
        final CountDownLatch returned = new CountDownLatch(1);
        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (groupFuture) {
                    holdsMonitor.countDown();
                    try {
                        groupFuture.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                returned.countDown();
            }
        });
        waiter.setDaemon(true);
        waiter.start();

        try {
            assertTrue(holdsMonitor.await(10, TimeUnit.SECONDS));
            child.setSuccess();

            assertTrue(returned.await(10, TimeUnit.SECONDS),
                    "The group future was not completed while its monitor was held");
            assertTrue(groupFuture.isDone());
        } finally {
            waiter.interrupt();
            waiter.join(TimeUnit.SECONDS.toMillis(10));
            channel.finishAndReleaseAll();
        }
    }
}
