/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket.aio;

import io.netty.channel.ChannelTaskScheduler;
import io.netty.channel.EventExecutor;
import io.netty.channel.EventLoopException;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.DetectionUtil;

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class AioEventLoopGroup extends MultithreadEventLoopGroup {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioEventLoopGroup.class);
    private static final AioChannelFinder CHANNEL_FINDER;

    static {
        AioChannelFinder finder;
        try {
            if (DetectionUtil.hasUnsafe()) {
                finder = new UnsafeAioChannelFinder();
            } else {
                finder = new ReflectiveAioChannelFinder();
            }
        } catch (Throwable t) {
            LOGGER.debug(String.format(
                    "Failed to instantiate the optimal %s implementation - falling back to %s.",
                    AioChannelFinder.class.getSimpleName(), ReflectiveAioChannelFinder.class.getSimpleName()), t);
            finder = new ReflectiveAioChannelFinder();
        }
        CHANNEL_FINDER = finder;
    }

    private final AioExecutorService groupExecutor = new AioExecutorService();
    final AsynchronousChannelGroup group;

    public AioEventLoopGroup() {
        this(0);
    }

    public AioEventLoopGroup(int nThreads) {
        this(nThreads, null);
    }

    public AioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
        try {
            group = AsynchronousChannelGroup.withThreadPool(groupExecutor);
        } catch (IOException e) {
            throw new EventLoopException("Failed to create an AsynchronousChannelGroup", e);
        }
    }

    @Override
    public void shutdown() {
        boolean interrupted = false;

        // Tell JDK not to accept any more registration request.  Note that the threads are not really shut down yet.
        try {
            group.shutdownNow();
        } catch (IOException e) {
            throw new EventLoopException("failed to shut down a channel group", e);
        }

        // Wait until JDK propagates the shutdown request on AsynchronousChannelGroup to the ExecutorService.
        // JDK will probably submit some final tasks to the ExecutorService before shutting down the ExecutorService,
        // so we have to ensure all tasks submitted by JDK are executed before calling super.shutdown() to really
        // shut down event loop threads.
        while (!groupExecutor.isTerminated()) {
            try {
                groupExecutor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        // Close all connections and shut down event loop threads.
        super.shutdown();

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected EventExecutor newChild(
            ThreadFactory threadFactory, ChannelTaskScheduler scheduler, Object... args) throws Exception {
        return new AioEventLoop(this, threadFactory, scheduler);
    }

    private final class AioExecutorService extends AbstractExecutorService {

        // It does not shut down the underlying EventExecutor - it merely pretends to be shut down.
        // The actual shut down is done by EventLoopGroup and EventLoop implementation.
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void shutdown() {
            latch.countDown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return latch.getCount() == 0;
        }

        @Override
        public boolean isTerminated() {
            return isShutdown();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            Class<? extends Runnable> commandType = command.getClass();
            if (commandType.getName().startsWith("sun.nio.ch.")) {
                executeAioTask(command);
            } else {
                next().execute(command);
            }
        }

        private void executeAioTask(Runnable command) {
            AbstractAioChannel ch = null;
            try {
                ch = CHANNEL_FINDER.findChannel(command);
            } catch (Throwable t) {
                // Ignore
            }

            EventExecutor l;
            if (ch != null) {
                l = ch.eventLoop();
            } else {
                l = next();
            }

            if (l.isShutdown()) {
                command.run();
            } else {
                l.execute(command);
            }
        }
    }
}
