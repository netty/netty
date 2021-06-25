/*
 * Copyright 2019 The Netty Project
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
package io.netty.channel;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class AbstractSingleThreadEventLoopTest {

    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testChannelsRegistered() throws Exception {
        EventLoopGroup group = newEventLoopGroup();
        final SingleThreadEventLoop loop = (SingleThreadEventLoop) group.next();

        try {
            final Channel ch1 = newChannel();
            final Channel ch2 = newChannel();

            int rc = registeredChannels(loop);
            boolean channelCountSupported = rc != -1;

            if (channelCountSupported) {
                assertEquals(0, registeredChannels(loop));
            }

            assertTrue(loop.register(ch1).syncUninterruptibly().isSuccess());
            assertTrue(loop.register(ch2).syncUninterruptibly().isSuccess());
            if (channelCountSupported) {
                checkNumRegisteredChannels(loop, 2);
            }

            assertTrue(ch1.deregister().syncUninterruptibly().isSuccess());
            if (channelCountSupported) {
                checkNumRegisteredChannels(loop, 1);
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void checkNumRegisteredChannels(SingleThreadEventLoop loop, int numChannels) throws Exception {
        // We need to loop as some EventLoop implementations may need some time to update the counter correctly.
        while (registeredChannels(loop) != numChannels) {
            Thread.sleep(50);
        }
    }

    // Only reliable if run from event loop
    private static int registeredChannels(final SingleThreadEventLoop loop) throws Exception {
        return loop.submit(new Callable<Integer>() {
            @Override
            public Integer call() {
                return loop.registeredChannels();
            }
        }).get(1, TimeUnit.SECONDS);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void shutdownBeforeStart() throws Exception {
        EventLoopGroup group = newEventLoopGroup();
        assertFalse(group.awaitTermination(2, TimeUnit.MILLISECONDS));
        group.shutdown();
        assertTrue(group.awaitTermination(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shutdownGracefullyZeroQuietBeforeStart() throws Exception {
        EventLoopGroup group = newEventLoopGroup();
        assertTrue(group.shutdownGracefully(0L, 2L, TimeUnit.SECONDS).await(200L));
    }

    // Copied from AbstractEventLoopTest
    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownGracefullyNoQuietPeriod() throws Exception {
        EventLoopGroup loop = newEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(loop)
        .channel(serverChannelClass())
        .childHandler(new ChannelInboundHandlerAdapter());

        // Not close the Channel to ensure the EventLoop is still shutdown in time.
        ChannelFuture cf = serverChannelClass() == LocalServerChannel.class
                ? b.bind(new LocalAddress("local")) : b.bind(0);
        cf.sync().channel();

        Future<?> f = loop.shutdownGracefully(0, 1, TimeUnit.MINUTES);
        assertTrue(loop.awaitTermination(600, TimeUnit.MILLISECONDS));
        assertTrue(f.syncUninterruptibly().isSuccess());
        assertTrue(loop.isShutdown());
        assertTrue(loop.isTerminated());
    }

    @Test
    public void shutdownGracefullyBeforeStart() throws Exception {
        EventLoopGroup group = newEventLoopGroup();
        assertTrue(group.shutdownGracefully(200L, 1000L, TimeUnit.MILLISECONDS).await(500L));
    }

    @Test
    public void gracefulShutdownAfterStart() throws Exception {
        EventLoop loop = newEventLoopGroup().next();
        final CountDownLatch latch = new CountDownLatch(1);
        loop.execute(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        });

        // Wait for the event loop thread to start.
        latch.await();

        // Request the event loop thread to stop.
        loop.shutdownGracefully(200L, 3000L, TimeUnit.MILLISECONDS);

        // Wait until the event loop is terminated.
        assertTrue(loop.awaitTermination(500L, TimeUnit.MILLISECONDS));

        assertRejection(loop);
    }

    @Test
    void testTaskQueuesDefault() {
        EventLoopGroup eventLoopGroup = newEventLoopGroup(null, null);
        Assumptions.assumeFalse(eventLoopGroup instanceof DefaultEventLoopGroup);
        SingleThreadEventLoop eventLoop = newEventLoop(eventLoopGroup);
        Class<?> defaultQueueType = newTaskQueue(eventLoop).getClass();
        assertEquals(defaultQueueType, eventLoop.taskQueue().getClass());
        assertEquals(defaultQueueType, eventLoop.tailTaskQueue().getClass());
    }

    @Test
    void testTaskQueuesCustom() {
        EventLoopGroup eventLoopGroup = newEventLoopGroup(ARRAYDEQUE_FACTORY, ARRAYDEQUE_FACTORY);
        Assumptions.assumeFalse(eventLoopGroup instanceof DefaultEventLoopGroup);
        SingleThreadEventLoop eventLoop = newEventLoop(eventLoopGroup);
        assertEquals(ArrayDeque.class, eventLoop.taskQueue().getClass());
        assertEquals(ArrayDeque.class, eventLoop.tailTaskQueue().getClass());
    }

    @Test
    void testTailTaskQueueCustom() {
        EventLoopGroup eventLoopGroup = newEventLoopGroup(null, ARRAYDEQUE_FACTORY);
        Assumptions.assumeFalse(eventLoopGroup instanceof DefaultEventLoopGroup);
        SingleThreadEventLoop eventLoop = newEventLoop(eventLoopGroup);
        Class<?> defaultQueueType = newTaskQueue(eventLoop).getClass();
        assertEquals(defaultQueueType, eventLoop.taskQueue().getClass());
        assertEquals(ArrayDeque.class, eventLoop.tailTaskQueue().getClass());
    }

    @Test
    void testTaskQueueCustom() {
        EventLoopGroup eventLoopGroup = newEventLoopGroup(ARRAYDEQUE_FACTORY, null);
        Assumptions.assumeFalse(eventLoopGroup instanceof DefaultEventLoopGroup);
        SingleThreadEventLoop eventLoop = newEventLoop(eventLoopGroup);
        Class<?> defaultQueueType = newTaskQueue(eventLoop).getClass();
        assertEquals(ArrayDeque.class, eventLoop.taskQueue().getClass());
        assertEquals(defaultQueueType, eventLoop.tailTaskQueue().getClass());
    }

    private static final Runnable NOOP = new Runnable() {
        @Override
        public void run() { }
    };

    private static final EventLoopTaskQueueFactory ARRAYDEQUE_FACTORY = new EventLoopTaskQueueFactory() {
        @Override
        public Queue<Runnable> newTaskQueue(int maxCapacity) {
            return new ArrayDeque<Runnable>();
        }
    };

    private static void assertRejection(EventExecutor loop) {
        try {
            loop.execute(NOOP);
            fail("A task must be rejected after shutdown() is called.");
        } catch (RejectedExecutionException e) {
            // Expected
        }
    }

    private static Queue<Runnable> newTaskQueue(SingleThreadEventLoop eventLoop) {
        return eventLoop.newTaskQueue(Integer.MAX_VALUE);
    }

    private static SingleThreadEventLoop newEventLoop(EventLoopGroup eventLoopGroup) {
        EventLoop eventLoop = eventLoopGroup.next();
        assert eventLoop instanceof SingleThreadEventLoop;
        return (SingleThreadEventLoop) eventLoop;
    }

    protected abstract EventLoopGroup newEventLoopGroup();
    protected abstract EventLoopGroup newEventLoopGroup(EventLoopTaskQueueFactory taskQueueFactory,
                                                        EventLoopTaskQueueFactory tailTaskQueueFactory);
    protected abstract Channel newChannel();
    protected abstract Class<? extends ServerChannel> serverChannelClass();
}
