/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopChooser;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.metrics.MetricsCollector;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link EventLoopGroup} which uses epoll under the covers. Because of this
 * it only works on linux.
 */
public final class EpollEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * Create a new instance using the default number of threads and the default {@link ThreadFactory}.
     */
    public EpollEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance using the specified number of threads and the default {@link ThreadFactory}.
     */
    public EpollEventLoopGroup(int nThreads) {
        this(nThreads, (ThreadFactory) null);
    }

    /**
     * Create a new instance using the specified number of threads and the given {@link ThreadFactory}.
     */
    public EpollEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, 128);
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the given
     * maximal amount of epoll events to handle per epollWait(...).
     */
    public EpollEventLoopGroup(int nThreads, ThreadFactory threadFactory, int maxEventsAtOnce) {
        this(nThreads, threadFactory, null, maxEventsAtOnce);
    }

    /**
     * Create a new instance using the default number of threads and the default {@link ThreadFactory}.
     */
    public EpollEventLoopGroup(EventLoopChooser chooser) {
        this(0, chooser);
    }

    /**
     * Create a new instance using the specified number of threads and the default {@link ThreadFactory}.
     */
    public EpollEventLoopGroup(int nThreads, EventLoopChooser chooser) {
        this(nThreads, null, chooser);
    }

    /**
     * Create a new instance using the specified number of threads and the given {@link ThreadFactory}.
     */
    public EpollEventLoopGroup(int nThreads, ThreadFactory threadFactory, EventLoopChooser chooser) {
        this(nThreads, threadFactory, chooser, 128);
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the given
     * maximal amount of epoll events to handle per epollWait(...).
     */
    public EpollEventLoopGroup(
            int nThreads, ThreadFactory threadFactory, EventLoopChooser chooser, int maxEventsAtOnce) {
        super(nThreads, threadFactory, chooser, maxEventsAtOnce);
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: children()) {
            ((EpollEventLoop) e).setIoRatio(ioRatio);
        }
    }

    @Override
    protected EventLoop newChild(Executor executor, MetricsCollector metrics, Object... args) throws Exception {
        return new EpollEventLoop(this, executor, metrics, (Integer) args[0]);
    }
}
