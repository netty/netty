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
import io.netty.util.concurrent.AutoScalingEventExecutorChooserFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class AbstractSingleThreadEventLoopTest {
    protected static final int SCALING_MIN_THREADS = 1;
    protected static final int SCALING_MAX_THREADS = 2;
    protected static final long SCALING_WINDOW_SECONDS = 100;
    protected static final TimeUnit SCALING_WINDOW_UNIT = MILLISECONDS;
    protected static final double SCALEDOWN_THRESHOLD = 0.2;
    protected static final double SCALEUP_THRESHOLD = 0.9;
    protected static final int SCALING_PATIENCE_CYCLES = 1;

    protected static final EventExecutorChooserFactory AUTO_SCALING_CHOOSER_FACTORY =
            new AutoScalingEventExecutorChooserFactory(SCALING_MIN_THREADS, SCALING_MAX_THREADS, SCALING_WINDOW_SECONDS,
                                                       SCALING_WINDOW_UNIT, SCALEDOWN_THRESHOLD, SCALEUP_THRESHOLD,
                                                       SCALING_MAX_THREADS, SCALING_MAX_THREADS, SCALING_PATIENCE_CYCLES
    );

    @Test
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
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testChannelsIteratorEmpty() throws Exception {
        assumeTrue(supportsChannelIteration());
        EventLoopGroup group = newEventLoopGroup();
        final SingleThreadEventLoop loop = (SingleThreadEventLoop) group.next();
        try {
            runBlockingOn(loop, new Runnable() {
                @Override
                public void run() {
                    final Iterator<Channel> iterator = loop.registeredChannelsIterator();

                    assertFalse(iterator.hasNext());
                    assertThrows(NoSuchElementException.class, new Executable() {
                        @Override
                        public void execute() {
                            iterator.next();
                        }
                    });
                }
            });
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testChannelsIterator() throws Exception {
        assumeTrue(supportsChannelIteration());
        EventLoopGroup group = newEventLoopGroup();
        final SingleThreadEventLoop loop = (SingleThreadEventLoop) group.next();
        try {
            final Channel ch1 = newChannel();
            final Channel ch2 = newChannel();
            loop.register(ch1).syncUninterruptibly();
            loop.register(ch2).syncUninterruptibly();
            assertEquals(2, registeredChannels(loop));

            runBlockingOn(loop, new Runnable() {
                @Override
                public void run() {
                    final Iterator<Channel> iterator = loop.registeredChannelsIterator();

                    assertTrue(iterator.hasNext());
                    Channel actualCh1 = iterator.next();
                    assertNotNull(actualCh1);

                    assertTrue(iterator.hasNext());
                    Channel actualCh2 = iterator.next();
                    assertNotNull(actualCh2);

                    Set<Channel> expected = new HashSet<Channel>(4);
                    expected.add(ch1);
                    expected.add(ch2);
                    expected.remove(actualCh1);
                    expected.remove(actualCh2);
                    assertTrue(expected.isEmpty());

                    assertFalse(iterator.hasNext());
                    assertThrows(NoSuchElementException.class, new Executable() {
                        @Override
                        public void execute() {
                            iterator.next();
                        }
                    });
                }
            });
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testChannelsIteratorRemoveThrows() throws Exception {
        assumeTrue(supportsChannelIteration());
        EventLoopGroup group = newEventLoopGroup();
        final SingleThreadEventLoop loop = (SingleThreadEventLoop) group.next();

        try {
            final Channel ch = newChannel();
            loop.register(ch).syncUninterruptibly();
            assertEquals(1, registeredChannels(loop));

            runBlockingOn(loop, new Runnable() {
                @Override
                public void run() {
                    assertThrows(UnsupportedOperationException.class, new Executable() {
                        @Override
                        public void execute() {
                            loop.registeredChannelsIterator().remove();
                        }
                    });
                }
            });
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    void schedulingAndCancellingTasks() throws Exception {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
            }
        };
        List<ScheduledFuture<?>> tasks = new ArrayList<ScheduledFuture<?>>();
        EventLoopGroup group = newEventLoopGroup();
        try {
            EventLoop eventLoop = group.next();
            for (int i = 0; i < 5000; i++) {
                tasks.add(eventLoop.scheduleAtFixedRate(runnable, 1, 1, MILLISECONDS));
                if (tasks.size() > 500) {
                    tasks.get(PlatformDependent.threadLocalRandom().nextInt(tasks.size())).cancel(false);
                }
            }
            for (ScheduledFuture<?> task : tasks) {
                task.cancel(false);
            }
            for (ScheduledFuture<?> task : tasks) {
                task.await();
            }
            for (ScheduledFuture<?> task : tasks) {
                if (!task.isCancelled()) {
                    task.sync();
                }
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @Timeout(30)
    public void testAutoScalingEventLoopGroupCanScaleDownAndBeUsed() throws Exception {
        EventLoopGroup group = newAutoScalingEventLoopGroup();
        if (group == null) {
            return;
        }
        try {
            startAllExecutors(group);
            assertEquals(SCALING_MAX_THREADS, countActiveExecutors(group),
                         "Group should start with max threads active.");

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (countActiveExecutors(group) > SCALING_MIN_THREADS && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }

            assertEquals(SCALING_MIN_THREADS, countActiveExecutors(group),
                         "Group did not scale down to min threads in time.");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(30)
    public void testSubmittingTaskWakesUpSuspendedExecutor() throws Exception {
        EventLoopGroup group = newAutoScalingEventLoopGroup();
        if (group == null) {
            return;
        }
        try {
            startAllExecutors(group);
            assertEquals(SCALING_MAX_THREADS, countActiveExecutors(group),
                         "Group should start with max threads.");

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (countActiveExecutors(group) > SCALING_MIN_THREADS && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertEquals(SCALING_MIN_THREADS, countActiveExecutors(group),
                         "Group did not scale down to min threads in time.");

            EventLoop suspendedLoop = null;
            for (EventExecutor exec : group) {
                if (exec.isSuspended()) {
                    suspendedLoop = (EventLoop) exec;
                    break;
                }
            }
            assertNotNull(suspendedLoop, "Could not find a suspended executor to test.");

            // Submit a task directly to the suspended loop, this should trigger the wake-up mechanism.
            Future<?> future = suspendedLoop.submit(() -> { });
            future.syncUninterruptibly();

            assertFalse(suspendedLoop.isSuspended(), "Executor should wake up after task submission.");
            assertEquals(SCALING_MAX_THREADS, countActiveExecutors(group),
                         "Active executor count should increase after wake-up.");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static int countActiveExecutors(EventLoopGroup group) {
        int activeCount = 0;
        for (EventExecutor executor : group) {
            if (!executor.isSuspended()) {
                activeCount++;
            }
        }
        return activeCount;
    }

    private static void startAllExecutors(EventLoopGroup group) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(SCALING_MAX_THREADS);
        for (EventExecutor executor : group) {
            executor.execute(startLatch::countDown);
        }
        startLatch.await();
    }

    private static void runBlockingOn(EventLoop eventLoop, final Runnable action) {
        final Promise<Void> promise = eventLoop.newPromise();
            eventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        action.run();
                        promise.setSuccess(null);
                    } catch (Throwable t) {
                        promise.tryFailure(t);
                    }
                }
            });
        try {
            promise.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Throwable cause = promise.cause();
        if (cause != null) {
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        }
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

    protected boolean supportsChannelIteration() {
        return false;
    }
    protected abstract EventLoopGroup newEventLoopGroup();
    protected abstract EventLoopGroup newAutoScalingEventLoopGroup();
    protected abstract Channel newChannel();
    protected abstract Class<? extends ServerChannel> serverChannelClass();
}
