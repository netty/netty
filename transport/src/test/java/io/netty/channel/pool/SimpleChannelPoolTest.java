/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.*;

public class SimpleChannelPoolTest {
    private static final String LOCAL_ADDR_ID = "test.id";

    @Test
    public void testAcquire() throws Exception {
        EventLoopGroup group = new LocalEventLoopGroup();
        LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(group)
          .channel(LocalChannel.class);

        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
              }
          });

        // Start server
        Channel sc = sb.bind(addr).sync().channel();
        CountingChannelPoolHandler handler = new CountingChannelPoolHandler();

        ChannelPool<Channel, ChannelPoolKey> pool =
                new SimpleChannelPool<Channel, ChannelPoolKey>(cb, handler);

        ChannelPoolKey key = new DefaultChannelPoolKey(addr);
        ChannelPoolKey key2 = new DefaultChannelPoolKey(addr, group.next());

        PooledChannel<Channel, ChannelPoolKey> channel = pool.acquire(key).sync().getNow();

        channel.release().syncUninterruptibly();

        Channel channel2 = pool.acquire(key).sync().getNow();
        assertSame(channel, channel2);
        assertEquals(1, handler.channelCount());

        PooledChannel<Channel, ChannelPoolKey> channel3 = pool.acquire(key2).sync().getNow();
        assertNotSame(channel, channel3);
        assertEquals(2, handler.channelCount());
        channel3.release().syncUninterruptibly();

        // Should fail on multiple release calls.
        try {
            channel3.release().syncUninterruptibly();
            fail();
        } catch (IllegalStateException e) {
            // Should fail
        }

        assertEquals(1, handler.acquiredCount());
        assertEquals(2, handler.releasedCount());

        sc.close().sync();
        channel2.close().sync();
        channel3.close().sync();
        group.shutdownGracefully();
    }

    @Test
    public void testBoundedChannelPoolSegment() throws Exception {
        EventLoopGroup group = new LocalEventLoopGroup();
        LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(group)
          .channel(LocalChannel.class);

        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
              }
          });

        // Start server
        Channel sc = sb.bind(addr).sync().channel();
        CountingChannelPoolHandler handler = new CountingChannelPoolHandler();

        ChannelPool<Channel, ChannelPoolKey> pool = new SimpleChannelPool<Channel, ChannelPoolKey>(cb, handler,
                ActiveChannelHealthChecker.instance(), new ChannelPoolSegmentFactory<Channel, ChannelPoolKey>() {
                    @Override
                    public ChannelPoolSegment<Channel, ChannelPoolKey> newSegment() {
                        // Bounded queue with a maximal capacity of 1
                        return ChannelPoolSegmentFactories.newFifoSegment(
                                new LinkedBlockingQueue<PooledChannel<Channel, ChannelPoolKey>>(1));
                    }
                });

        ChannelPoolKey key = new DefaultChannelPoolKey(addr);

        PooledChannel<Channel, ChannelPoolKey> channel = pool.acquire(key).sync().getNow();
        PooledChannel<Channel, ChannelPoolKey> channel2 = pool.acquire(key).sync().getNow();

        channel.release().syncUninterruptibly();
        try {
            channel2.release().syncUninterruptibly();
            fail();
        } catch (IllegalStateException e) {
            // Should fail
        }

        channel2.close().sync();

        assertEquals(2, handler.channelCount());
        assertEquals(0, handler.acquiredCount());
        assertEquals(1, handler.releasedCount());
        sc.close().sync();
        channel.close().sync();
        channel2.close().sync();
        group.shutdownGracefully();
    }
}
