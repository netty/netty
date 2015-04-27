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
import io.netty.channel.pool.FixedChannelPool.AcquireTimeoutAction;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public class FixedChannelPoolTest {
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
        ChannelPoolKey key = new DefaultChannelPoolKey(addr);
        CountingChannelPoolHandler handler = new CountingChannelPoolHandler();

        ChannelPool<Channel, ChannelPoolKey> pool  = new FixedChannelPool<Channel, ChannelPoolKey>(
                cb, handler, 1, Integer.MAX_VALUE);

        PooledChannel<Channel, ChannelPoolKey> channel = pool.acquire(key).sync().getNow();
        Future<PooledChannel<Channel, ChannelPoolKey>> future = pool.acquire(key);
        assertFalse(future.isDone());

        channel.release().syncUninterruptibly();
        assertTrue(future.await(1, TimeUnit.SECONDS));

        Channel channel2 = future.getNow();
        assertSame(channel, channel2);
        assertEquals(1, handler.channelCount());

        assertEquals(1, handler.acquiredCount());
        assertEquals(1, handler.releasedCount());

        sc.close().sync();
        channel2.close().sync();
        group.shutdownGracefully();
    }

    @Test(expected = TimeoutException.class)
    public void testAcquireTimeout() throws Exception {
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
        ChannelPoolHandler<Channel, ChannelPoolKey> handler = new TestChannelPoolHandler();
        ChannelPool<Channel, ChannelPoolKey> pool  = new FixedChannelPool<Channel, ChannelPoolKey>(
                cb, handler, ActiveChannelHealthChecker.instance(),
                ChannelPoolSegmentFactories.newLifoFactory(), AcquireTimeoutAction.Fail, 500,
                1, Integer.MAX_VALUE);

        ChannelPoolKey key = new DefaultChannelPoolKey(addr);
        Channel channel = pool.acquire(key).sync().getNow();
        Future<PooledChannel<Channel, ChannelPoolKey>> future = pool.acquire(key);
        try {
            future.sync();
        } finally {
            sc.close().sync();
            channel.close().sync();
            group.shutdownGracefully();
        }
    }

    @Test
    public void testAcquireNewConnection() throws Exception {
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
        ChannelPoolHandler<Channel, ChannelPoolKey> handler = new TestChannelPoolHandler();
        ChannelPool<Channel, ChannelPoolKey> pool  = new FixedChannelPool<Channel, ChannelPoolKey>(
                cb, handler, ActiveChannelHealthChecker.instance(),
                ChannelPoolSegmentFactories.newLifoFactory(), AcquireTimeoutAction.NewConnection, 500,
                1, Integer.MAX_VALUE);

        ChannelPoolKey key = new DefaultChannelPoolKey(addr);
        Channel channel = pool.acquire(key).sync().getNow();
        Channel channel2 = pool.acquire(key).sync().getNow();
        assertNotSame(channel, channel2);
        sc.close().sync();
        channel.close().sync();
        channel2.close().sync();
        group.shutdownGracefully();
    }

    @Test(expected = IllegalStateException.class)
    public void testAcquireBoundQueue() throws Exception {
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
        ChannelPoolHandler<Channel, ChannelPoolKey> handler = new TestChannelPoolHandler();
        ChannelPool<Channel, ChannelPoolKey> pool  = new FixedChannelPool<Channel, ChannelPoolKey>(
                cb, handler,
                1, 1);

        ChannelPoolKey key = new DefaultChannelPoolKey(addr);
        Channel channel = pool.acquire(key).sync().getNow();
        Future<PooledChannel<Channel, ChannelPoolKey>> future = pool.acquire(key);
        assertFalse(future.isDone());

        try {
            pool.acquire(key).sync();
        } finally {
            sc.close().sync();
            channel.close().sync();
            group.shutdownGracefully();
        }
    }

    private static final class TestChannelPoolHandler extends AbstractChannelPoolHandler<Channel, ChannelPoolKey> {
        @Override
        public void channelCreated(@SuppressWarnings("unused") PooledChannel<Channel, ChannelPoolKey> ch)
                throws Exception {
            // NOOP
        }
    }
}
