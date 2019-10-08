/*
 * Copyright 2019 The Netty Project
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
package io.netty.testsuite.transport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultithreadEventLoopGroup;
import org.junit.Test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

public abstract class AbstractSingleThreadEventLoopTest {

    @Test
    @SuppressWarnings("deprecation")
    public void shutdownBeforeStart() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(newIoHandlerFactory());
        assertFalse(group.awaitTermination(2, TimeUnit.MILLISECONDS));
        group.shutdown();
        assertTrue(group.awaitTermination(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shutdownGracefullyZeroQuietBeforeStart() throws Exception {
        EventLoopGroup group =  new MultithreadEventLoopGroup(newIoHandlerFactory());
        assertTrue(group.shutdownGracefully(0L, 2L, TimeUnit.SECONDS).await(200L));
    }

    // Copied from AbstractEventLoopTest
    @Test(timeout = 5000)
    public void testShutdownGracefullyNoQuietPeriod() throws Exception {
        EventLoopGroup loop = new MultithreadEventLoopGroup(newIoHandlerFactory());
        ServerBootstrap b = new ServerBootstrap();
        b.group(loop)
                .channel(serverChannelClass())
                .childHandler(new ChannelHandler() { });

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
        EventLoopGroup group = new MultithreadEventLoopGroup(newIoHandlerFactory());
        assertTrue(group.shutdownGracefully(200L, 1000L, TimeUnit.MILLISECONDS).await(500L));
    }

    @Test
    public void gracefulShutdownAfterStart() throws Exception {
        EventLoop loop = new MultithreadEventLoopGroup(newIoHandlerFactory()).next();
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

    protected abstract IoHandlerFactory newIoHandlerFactory();
    protected abstract Class<? extends ServerChannel> serverChannelClass();
}
