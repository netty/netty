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
package io.netty.testsuite.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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

    private static final Runnable NOOP = new Runnable() {
        @Override
        public void run() { }
    };

    private static void assertRejection(EventExecutor loop) {
        try {
            loop.execute(NOOP);
            fail("A task must be rejected after shutdown() is called.");
        } catch (RejectedExecutionException e) {
            // Expected
        }
    }

    protected abstract EventLoopGroup newEventLoopGroup();
    protected abstract Channel newChannel();
    protected abstract Class<? extends ServerChannel> serverChannelClass();
}
