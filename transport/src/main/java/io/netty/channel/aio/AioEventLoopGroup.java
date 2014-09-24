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
package io.netty.channel.aio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopException;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * {@link AioEventLoopGroup} implementation which will handle AIO {@link Channel} implementations.
 */
public class AioEventLoopGroup extends MultithreadEventLoopGroup {
    private final AioExecutorService groupExecutor = new AioExecutorService();
    private final AsynchronousChannelGroup group;

    /**
     * Create a new instance which use the default number of threads of {@link #DEFAULT_EVENT_LOOP_THREADS}.
     */
    public AioEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance
     *
     * @param nThreads the number of threads that will be used by this instance
     */
    public AioEventLoopGroup(int nThreads) {
        this(nThreads, null);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads the number of threads that will be used by this instance
     * @param threadFactory the ThreadFactory to use, or {@code null} if the default should be used.
     */
    public AioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
        try {
            group = AsynchronousChannelGroup.withThreadPool(groupExecutor);
        } catch (IOException e) {
            throw new EventLoopException("Failed to create an AsynchronousChannelGroup", e);
        }
    }

    public AsynchronousChannelGroup channelGroup() {
        return group;
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
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
            } catch (InterruptedException ignored) {
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
    protected EventExecutor newChild(ThreadFactory threadFactory, Object... args) throws Exception {
        return new AioEventLoop(this, threadFactory);
    }

    private static final class AioExecutorService extends AbstractExecutorService {

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
            command.run();
        }
    }
}
