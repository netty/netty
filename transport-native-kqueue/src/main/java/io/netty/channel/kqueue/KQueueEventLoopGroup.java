/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.internal.UnstableApi;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

@UnstableApi
public final class KQueueEventLoopGroup extends MultithreadEventLoopGroup {
    {
        // Ensure JNI is initialized by the time this class is loaded by this time!
        KQueue.ensureAvailability();
    }
    /**
     * Create a new instance using the default number of threads and the default {@link ThreadFactory}.
     */
    public KQueueEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance using the specified number of threads and the default {@link ThreadFactory}.
     */
    public KQueueEventLoopGroup(int nThreads) {
        this(nThreads, (ThreadFactory) null);
    }

    /**
     * Create a new instance using the default number of threads and the given {@link ThreadFactory}.
     */
    @SuppressWarnings("deprecation")
    public KQueueEventLoopGroup(ThreadFactory threadFactory) {
        this(0, threadFactory, 0);
    }

    /**
     * Create a new instance using the specified number of threads and the default {@link ThreadFactory}.
     */
    @SuppressWarnings("deprecation")
    public KQueueEventLoopGroup(int nThreads, SelectStrategyFactory selectStrategyFactory) {
        this(nThreads, (ThreadFactory) null, selectStrategyFactory);
    }

    /**
     * Create a new instance using the specified number of threads and the given {@link ThreadFactory}.
     */
    @SuppressWarnings("deprecation")
    public KQueueEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, 0);
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, DefaultSelectStrategyFactory.INSTANCE);
    }

    /**
     * Create a new instance using the specified number of threads and the given {@link ThreadFactory}.
     */
    @SuppressWarnings("deprecation")
    public KQueueEventLoopGroup(int nThreads, ThreadFactory threadFactory,
                                SelectStrategyFactory selectStrategyFactory) {
        this(nThreads, threadFactory, 0, selectStrategyFactory);
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the given
     * maximal amount of epoll events to handle per epollWait(...).
     *
     * @deprecated  Use {@link #KQueueEventLoopGroup(int)} or {@link #KQueueEventLoopGroup(int, ThreadFactory)}
     */
    @Deprecated
    public KQueueEventLoopGroup(int nThreads, ThreadFactory threadFactory, int maxEventsAtOnce) {
        this(nThreads, threadFactory, maxEventsAtOnce, DefaultSelectStrategyFactory.INSTANCE);
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the given
     * maximal amount of epoll events to handle per epollWait(...).
     *
     * @deprecated  Use {@link #KQueueEventLoopGroup(int)}, {@link #KQueueEventLoopGroup(int, ThreadFactory)}, or
     * {@link #KQueueEventLoopGroup(int, SelectStrategyFactory)}
     */
    @Deprecated
    public KQueueEventLoopGroup(int nThreads, ThreadFactory threadFactory, int maxEventsAtOnce,
                               SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, threadFactory, maxEventsAtOnce, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor, SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, 0, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                               SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, chooserFactory, 0, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                               SelectStrategyFactory selectStrategyFactory,
                               RejectedExecutionHandler rejectedExecutionHandler) {
        super(nThreads, executor, chooserFactory, 0, selectStrategyFactory, rejectedExecutionHandler);
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                                SelectStrategyFactory selectStrategyFactory,
                                RejectedExecutionHandler rejectedExecutionHandler,
                                EventLoopTaskQueueFactory queueFactory) {
        super(nThreads, executor, chooserFactory, 0, selectStrategyFactory,
                rejectedExecutionHandler, queueFactory);
    }

    /**
     * @param nThreads the number of threads that will be used by this instance.
     * @param executor the Executor to use, or {@code null} if default one should be used.
     * @param chooserFactory the {@link EventExecutorChooserFactory} to use.
     * @param selectStrategyFactory the {@link SelectStrategyFactory} to use.
     * @param rejectedExecutionHandler the {@link RejectedExecutionHandler} to use.
     * @param taskQueueFactory the {@link EventLoopTaskQueueFactory} to use for
     *                         {@link SingleThreadEventLoop#execute(Runnable)},
     *                         or {@code null} if default one should be used.
     * @param tailTaskQueueFactory the {@link EventLoopTaskQueueFactory} to use for
     *                             {@link SingleThreadEventLoop#executeAfterEventLoopIteration(Runnable)},
     *                             or {@code null} if default one should be used.
     */
    public KQueueEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                               SelectStrategyFactory selectStrategyFactory,
                               RejectedExecutionHandler rejectedExecutionHandler,
                               EventLoopTaskQueueFactory taskQueueFactory,
                               EventLoopTaskQueueFactory tailTaskQueueFactory) {
        super(nThreads, executor, chooserFactory, 0, selectStrategyFactory, rejectedExecutionHandler, taskQueueFactory,
                tailTaskQueueFactory);
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: this) {
            ((KQueueEventLoop) e).setIoRatio(ioRatio);
        }
    }

    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        Integer maxEvents = (Integer) args[0];
        SelectStrategyFactory selectStrategyFactory = (SelectStrategyFactory) args[1];
        RejectedExecutionHandler rejectedExecutionHandler = (RejectedExecutionHandler) args[2];
        EventLoopTaskQueueFactory taskQueueFactory = null;
        EventLoopTaskQueueFactory tailTaskQueueFactory = null;

        int argsLength = args.length;
        if (argsLength > 3) {
            taskQueueFactory = (EventLoopTaskQueueFactory) args[3];
        }
        if (argsLength > 4) {
            tailTaskQueueFactory = (EventLoopTaskQueueFactory) args[4];
        }
        return new KQueueEventLoop(this, executor, maxEvents,
                selectStrategyFactory.newSelectStrategy(),
                rejectedExecutionHandler, taskQueueFactory, tailTaskQueueFactory);
    }
}
