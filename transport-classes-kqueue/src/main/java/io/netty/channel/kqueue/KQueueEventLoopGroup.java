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

import io.netty.channel.Channel;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;


/**
 * @deprecated Use {@link MultiThreadIoEventLoopGroup} with {@link KQueueIoHandler#newFactory()}.
 */
@Deprecated
public final class KQueueEventLoopGroup extends MultiThreadIoEventLoopGroup {

    // This does not use static by design to ensure the class can be loaded and only do the check when its actually
    // instanced.
    {
        // Ensure JNI is initialized by the time this class is loaded by this time!
        KQueue.ensureAvailability();
    }

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(KQueueEventLoopGroup.class);

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
        super(nThreads, threadFactory, KQueueIoHandler.newFactory(maxEventsAtOnce, selectStrategyFactory),
                RejectedExecutionHandlers.reject());
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor, SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, KQueueIoHandler.newFactory(0, selectStrategyFactory),
                RejectedExecutionHandlers.reject());
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                               SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, KQueueIoHandler.newFactory(0, selectStrategyFactory),
                chooserFactory, RejectedExecutionHandlers.reject());
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                               SelectStrategyFactory selectStrategyFactory,
                               RejectedExecutionHandler rejectedExecutionHandler) {
        super(nThreads, executor,  KQueueIoHandler.newFactory(0, selectStrategyFactory), chooserFactory,
                rejectedExecutionHandler);
    }

    public KQueueEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                                SelectStrategyFactory selectStrategyFactory,
                                RejectedExecutionHandler rejectedExecutionHandler,
                                EventLoopTaskQueueFactory queueFactory) {
        super(nThreads, executor, KQueueIoHandler.newFactory(0, selectStrategyFactory), chooserFactory,
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
        super(nThreads, executor, KQueueIoHandler.newFactory(0, selectStrategyFactory), chooserFactory,
                rejectedExecutionHandler, taskQueueFactory, tailTaskQueueFactory);
    }

    /**
     * This method is a no-op.
     *
     * @deprecated
     */
    @Deprecated
    public void setIoRatio(int ioRatio) {
        LOGGER.debug("EpollEventLoopGroup.setIoRatio(int) logic was removed, this is a no-op");
    }

    @Override
    protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
        RejectedExecutionHandler rejectedExecutionHandler = null;
        EventLoopTaskQueueFactory taskQueueFactory = null;
        EventLoopTaskQueueFactory tailTaskQueueFactory = null;
        int argsLength = args.length;
        if (argsLength > 0) {
            rejectedExecutionHandler = (RejectedExecutionHandler) args[0];
        }
        if (argsLength > 1) {
            taskQueueFactory = (EventLoopTaskQueueFactory) args[2];
        }
        if (argsLength > 2) {
            tailTaskQueueFactory = (EventLoopTaskQueueFactory) args[1];
        }
        return new KQueueEventLoop(this, executor, ioHandlerFactory,
                KQueueEventLoop.newTaskQueue(taskQueueFactory),
                KQueueEventLoop.newTaskQueue(tailTaskQueueFactory),
                rejectedExecutionHandler);
    }

    private static final class KQueueEventLoop extends SingleThreadIoEventLoop {
        KQueueEventLoop(IoEventLoopGroup parent, Executor executor, IoHandlerFactory ioHandlerFactory,
                        Queue<Runnable> taskQueue, Queue<Runnable> tailTaskQueue,
                        RejectedExecutionHandler rejectedExecutionHandler) {
            super(parent, executor, ioHandlerFactory, taskQueue, tailTaskQueue, rejectedExecutionHandler);
        }

        static Queue<Runnable> newTaskQueue(
                EventLoopTaskQueueFactory queueFactory) {
            if (queueFactory == null) {
                return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
            }
            return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
        }

        private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
            // This event loop never calls takeTask()
            return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                    : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
        }

        @Override
        public int registeredChannels() {
            assert inEventLoop();
            return ((KQueueIoHandler) ioHandler()).numRegisteredChannels();
        }

        @Override
        public Iterator<Channel> registeredChannelsIterator() {
            assert inEventLoop();
            return ((KQueueIoHandler) ioHandler()).registeredChannelsList().iterator();
        }
    }
}
