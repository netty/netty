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
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.pool.ChannelPoolTestUtils.getLocalAddrId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AbstractChannelPoolMapTest {
    @Test(expected = ConnectException.class)
    public void testMap() throws Exception {
        EventLoopGroup group = new LocalEventLoopGroup();
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        final Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        AbstractChannelPoolMap<EventLoop, SimpleChannelPool> poolMap =
                new AbstractChannelPoolMap<EventLoop, SimpleChannelPool>() {
            @Override
            protected SimpleChannelPool newPool(EventLoop key) {
                return new SimpleChannelPool(cb.clone(key), new TestChannelPoolHandler());
            }
        };

        EventLoop loop = group.next();

        assertFalse(poolMap.iterator().hasNext());
        assertEquals(0, poolMap.size());

        SimpleChannelPool pool = poolMap.get(loop);
        assertEquals(1, poolMap.size());
        assertTrue(poolMap.iterator().hasNext());

        assertSame(pool, poolMap.get(loop));
        assertTrue(poolMap.remove(loop));
        assertFalse(poolMap.remove(loop));

        assertFalse(poolMap.iterator().hasNext());
        assertEquals(0, poolMap.size());

        pool.acquire().syncUninterruptibly();
        poolMap.close();
    }

    @Test
    public void testRemoveClosesChannelPool() {
        EventLoopGroup group = new LocalEventLoopGroup();
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        final Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        AbstractChannelPoolMap<EventLoop, TestPool> poolMap =
                new AbstractChannelPoolMap<EventLoop, TestPool>() {
                    @Override
                    protected TestPool newPool(EventLoop key) {
                        return new TestPool(cb.clone(key), new TestChannelPoolHandler());
                    }
                };

        EventLoop loop = group.next();

        TestPool pool = poolMap.get(loop);
        assertTrue(poolMap.remove(loop));

        // the pool should be closed eventually after remove
        pool.closeFuture.awaitUninterruptibly(1, TimeUnit.SECONDS);
        assertTrue(pool.closeFuture.isDone());
        poolMap.close();
    }

    @Test
    public void testCloseClosesPoolsImmediately() {
        EventLoopGroup group = new LocalEventLoopGroup();
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        final Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        AbstractChannelPoolMap<EventLoop, TestPool> poolMap =
                new AbstractChannelPoolMap<EventLoop, TestPool>() {
                    @Override
                    protected TestPool newPool(EventLoop key) {
                        return new TestPool(cb.clone(key), new TestChannelPoolHandler());
                    }
                };

        EventLoop loop = group.next();

        TestPool pool = poolMap.get(loop);
        assertFalse(pool.closeFuture.isDone());

        // the pool should be closed immediately after remove
        poolMap.close();
        assertTrue(pool.closeFuture.isDone());
    }

    private static final class TestChannelPoolHandler extends AbstractChannelPoolHandler {
        @Override
        public void channelCreated(Channel ch) throws Exception {
            // NOOP
        }
    }

    private static final class TestPool extends SimpleChannelPool {
        private final Promise<Void> closeFuture;

        TestPool(Bootstrap bootstrap, ChannelPoolHandler handler) {
            super(bootstrap, handler);
            EventExecutor executor = bootstrap.config().group().next();
            closeFuture = executor.newPromise();
        }

        @Override
        public Future<Void> closeAsync() {
            Future<Void> poolClose = super.closeAsync();
            poolClose.addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) throws Exception {
                    if (future.isSuccess()) {
                        closeFuture.setSuccess(null);
                    } else {
                        closeFuture.setFailure(future.cause());
                    }
                }
            });
            return poolClose;
        }
    }
}
