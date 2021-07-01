/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.nio;

import io.netty.channel.Channel;
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

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link MultithreadEventLoopGroup} implementations which is used for NIO {@link Selector} based {@link Channel}s.
 */
public class NioEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * Create a new instance using the default number of threads, the default {@link ThreadFactory} and
     * the {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance using the specified number of threads, {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(int nThreads) {
        this(nThreads, (Executor) null);
    }

    /**
     * Create a new instance using the default number of threads, the given {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(ThreadFactory threadFactory) {
        this(0, threadFactory, SelectorProvider.provider());
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SelectorProvider.provider());
    }

    public NioEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SelectorProvider.provider());
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the given
     * {@link SelectorProvider}.
     */
    public NioEventLoopGroup(
            int nThreads, ThreadFactory threadFactory, final SelectorProvider selectorProvider) {
        this(nThreads, threadFactory, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory,
        final SelectorProvider selectorProvider, final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, threadFactory, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(
            int nThreads, Executor executor, final SelectorProvider selectorProvider) {
        this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory,
                RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory,
                             final RejectedExecutionHandler rejectedExecutionHandler) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory, rejectedExecutionHandler);
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory,
                             final RejectedExecutionHandler rejectedExecutionHandler,
                             final EventLoopTaskQueueFactory taskQueueFactory) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory,
                rejectedExecutionHandler, taskQueueFactory);
    }

    /**
     * @param nThreads the number of threads that will be used by this instance.
     * @param executor the Executor to use, or {@code null} if default one should be used.
     * @param chooserFactory the {@link EventExecutorChooserFactory} to use.
     * @param selectorProvider the {@link SelectorProvider} to use.
     * @param selectStrategyFactory the {@link SelectStrategyFactory} to use.
     * @param rejectedExecutionHandler the {@link RejectedExecutionHandler} to use.
     * @param taskQueueFactory the {@link EventLoopTaskQueueFactory} to use for
     *                         {@link SingleThreadEventLoop#execute(Runnable)},
     *                         or {@code null} if default one should be used.
     * @param tailTaskQueueFactory the {@link EventLoopTaskQueueFactory} to use for
     *                             {@link SingleThreadEventLoop#executeAfterEventLoopIteration(Runnable)},
     *                             or {@code null} if default one should be used.
     */
    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             SelectorProvider selectorProvider,
                             SelectStrategyFactory selectStrategyFactory,
                             RejectedExecutionHandler rejectedExecutionHandler,
                             EventLoopTaskQueueFactory taskQueueFactory,
                             EventLoopTaskQueueFactory tailTaskQueueFactory) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory,
                rejectedExecutionHandler, taskQueueFactory, tailTaskQueueFactory);
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: this) {
            ((NioEventLoop) e).setIoRatio(ioRatio);
        }
    }

    /**
     * Replaces the current {@link Selector}s of the child event loops with newly created {@link Selector}s to work
     * around the  infamous epoll 100% CPU bug.
     */
    public void rebuildSelectors() {
        for (EventExecutor e: this) {
            ((NioEventLoop) e).rebuildSelector();
        }
    }

    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        SelectorProvider selectorProvider = (SelectorProvider) args[0];
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
        return new NioEventLoop(this, executor, selectorProvider,
                selectStrategyFactory.newSelectStrategy(),
                rejectedExecutionHandler, taskQueueFactory, tailTaskQueueFactory);
    }
}
