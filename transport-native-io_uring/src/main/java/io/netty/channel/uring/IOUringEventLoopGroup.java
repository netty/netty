/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public final class IOUringEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * Create a new instance using the default number of threads and the default {@link ThreadFactory}.
     */
    public IOUringEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance using the specified number of threads and the default {@link ThreadFactory}.
     */
    public IOUringEventLoopGroup(int nThreads) {
        this(nThreads, (ThreadFactory) null);
    }

    /**
     * Create a new instance using the default number of threads and the given {@link ThreadFactory}.
     */
    public IOUringEventLoopGroup(ThreadFactory threadFactory) {
        this(0, threadFactory, 0);
    }

    /**
     * Create a new instance using the specified number of threads and the given {@link ThreadFactory}.
     */
    public IOUringEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, 0);
    }

    public IOUringEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, 0);
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the given
     * maximal size of the used ringbuffer.
     */
    public IOUringEventLoopGroup(int nThreads, ThreadFactory threadFactory, int ringSize) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), ringSize);
    }

    public IOUringEventLoopGroup(int nThreads, Executor executor, int ringsize) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE,
                ringsize, RejectedExecutionHandlers.reject());
    }

    private IOUringEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                                  int ringSize, RejectedExecutionHandler rejectedExecutionHandler) {
        this(nThreads, executor, chooserFactory, ringSize, rejectedExecutionHandler, null);
    }

    private IOUringEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                                  int ringSize, RejectedExecutionHandler rejectedExecutionHandler,
                                  EventLoopTaskQueueFactory queueFactory) {
        super(nThreads, executor, chooserFactory, ringSize, rejectedExecutionHandler, queueFactory);
    }

    //Todo
    @Override
    protected EventLoop newChild(Executor executor, Object... args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("Illegal amount of extra arguments");
        }
        int ringSize = (Integer) args[0];
        ObjectUtil.checkPositiveOrZero(ringSize, "ringSize");
        if (ringSize == 0) {
            ringSize = Native.DEFAULT_RING_SIZE;
        }
        RejectedExecutionHandler rejectedExecutionHandler = (RejectedExecutionHandler) args[1];
        EventLoopTaskQueueFactory taskQueueFactory = (EventLoopTaskQueueFactory) args[2];
        return new IOUringEventLoop(this, executor, ringSize, rejectedExecutionHandler, taskQueueFactory);
    }
}
