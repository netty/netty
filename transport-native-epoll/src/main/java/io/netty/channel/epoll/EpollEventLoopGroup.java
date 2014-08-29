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
import io.netty.channel.metrics.EventLoopMetrics;
import io.netty.channel.EventLoopScheduler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.metrics.EventLoopMetricsFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.metrics.EventExecutorMetrics;
import io.netty.util.concurrent.ExecutorFactory;

import java.util.concurrent.Executor;
import io.netty.util.concurrent.DefaultExecutorFactory;


/**
 * A {@link MultithreadEventLoopGroup} which uses <a href="http://en.wikipedia.org/wiki/Epoll">epoll</a> under the
 * covers. This {@link EventLoopGroup} works only on Linux systems!
 */
public final class EpollEventLoopGroup extends MultithreadEventLoopGroup {

    private static final int DEFAULT_MAX_EVENTS_AT_ONCE = 128;

    /**
     * Create a new instance that uses twice as many {@linkplain EventLoop}s as there are processors/cores available or
     * whatever is specified by {@code -Dio.netty.eventLoopThreads}. Further, the {@link Executor} returned by
     * {@link DefaultExecutorFactory} is used.
     */
    public EpollEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance that uses the {@link DefaultExecutorFactory}.
     *
     * @param nEventLoops   the number of {@linkplain EventLoop}s that will be used by this group. This will also
     *                      be the number of threads requested from the {@link DefaultExecutorFactory}.
     */
    public EpollEventLoopGroup(int nEventLoops) {
        this(nEventLoops, (Executor) null);
    }

    /**
     * @param nEventLoops   the number of {@linkplain EventLoop}s that will be used by this instance.
     *                      If {@code Executor} is {@code null} this will also be the number of threads
     *                      requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                      and the number of threads used by the {@code Executor} to lie very close together.
     *                      You can set this parameter to {@code 0} if you want the system to chose the number of
     *                      {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param executor      the {@link Executor} to use, or {@code null} if the one returned
     *                      by {@link DefaultExecutorFactory} should be used.
     */
    public EpollEventLoopGroup(int nEventLoops, Executor executor) {
        this(nEventLoops, executor, DEFAULT_MAX_EVENTS_AT_ONCE);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if {@link DefaultExecutorFactory}
     *                          should be used.
     */
    public EpollEventLoopGroup(int nEventLoops, ExecutorFactory executorFactory) {
        this(nEventLoops, executorFactory, DEFAULT_MAX_EVENTS_AT_ONCE);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param executor          the {@link Executor} to use, or {@code null} if the one returned
     *                          by {@link DefaultExecutorFactory} should be used.
     * @param maxEventsAtOnce   the maximum number of epoll events to handle per epollWait(...). The default is
     *                          {@value #DEFAULT_MAX_EVENTS_AT_ONCE}.
     */
    public EpollEventLoopGroup(int nEventLoops, Executor executor, int maxEventsAtOnce) {
        super(nEventLoops, executor, null, null, maxEventsAtOnce);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if {@link DefaultExecutorFactory}
     *                          should be used.
     * @param maxEventsAtOnce   the maximum number of epoll events to handle per epollWait(...). The default is
     *                          {@value #DEFAULT_MAX_EVENTS_AT_ONCE}.
     */
    public EpollEventLoopGroup(int nEventLoops, ExecutorFactory executorFactory, int maxEventsAtOnce) {
        this(nEventLoops, executorFactory, (EventLoopScheduler) null, maxEventsAtOnce);
    }

    /**
     * Create a new instance that uses the {@link DefaultExecutorFactory}.
     *
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param scheduler         the {@link EventLoopScheduler} that, on every call to {@link EventLoopGroup#next()},
     *                          decides which {@link EventLoop} to return.
     */
    public EpollEventLoopGroup(int nEventLoops, EventLoopScheduler scheduler) {
        super(nEventLoops, (Executor) null, scheduler, null, DEFAULT_MAX_EVENTS_AT_ONCE);
    }

    /**
     * Create a new instance that uses the {@link DefaultExecutorFactory}.
     *
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param metricsFactory    provide your own {@link EventLoopMetricsFactory} to gather information for every
     *                          {@link EventLoop} of this group.
     */
    public EpollEventLoopGroup(int nEventLoops, EventLoopMetricsFactory metricsFactory) {
        super(nEventLoops, (Executor) null, null, metricsFactory, DEFAULT_MAX_EVENTS_AT_ONCE);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param executor          the {@link Executor} to use, or {@code null} if the one returned
     *                          by {@link DefaultExecutorFactory} should be used.
     * @param scheduler         the {@link EventLoopScheduler} that, on every call to {@link EventLoopGroup#next()},
     *                          decides which {@link EventLoop} to return.
     * @param maxEventsAtOnce   the maximum number of epoll events to handle per epollWait(...). The default is
     *                          {@value #DEFAULT_MAX_EVENTS_AT_ONCE}.
     */
    public EpollEventLoopGroup(int nEventLoops,
                               Executor executor,
                               EventLoopScheduler scheduler,
                               int maxEventsAtOnce) {
        super(nEventLoops, executor, scheduler, null, maxEventsAtOnce);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if {@link DefaultExecutorFactory}
     *                          should be used.
     * @param scheduler         the {@link EventLoopScheduler} that, on every call to {@link EventLoopGroup#next()},
     *                          decides which {@link EventLoop} to return.
     * @param maxEventsAtOnce   the maximum number of epoll events to handle per epollWait(...). The default is
     *                          {@value #DEFAULT_MAX_EVENTS_AT_ONCE}.
     */
    public EpollEventLoopGroup(int nEventLoops,
                               ExecutorFactory executorFactory,
                               EventLoopScheduler scheduler,
                               int maxEventsAtOnce) {
        super(nEventLoops, executorFactory, scheduler, null, maxEventsAtOnce);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param executor          the {@link Executor} to use, or {@code null} if the one returned
     *                          by {@link DefaultExecutorFactory} should be used.
     * @param metricsFactory    provide your own {@link EventLoopMetricsFactory} to gather information for every
     *                          {@link EventLoop} of this group.
     * @param maxEventsAtOnce   the maximum number of epoll events to handle per epollWait(...). The default is
     *                          {@value #DEFAULT_MAX_EVENTS_AT_ONCE}.
     */
    public EpollEventLoopGroup(int nEventLoops,
                               Executor executor,
                               EventLoopMetricsFactory metricsFactory,
                               int maxEventsAtOnce) {
        super(nEventLoops, executor, null, metricsFactory, maxEventsAtOnce);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #EpollEventLoopGroup()} for details.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if {@link DefaultExecutorFactory}
     *                          should be used.
     * @param metricsFactory    provide your own {@link EventLoopMetricsFactory} to gather information for every
     *                          {@link EventLoop} of this group.
     * @param maxEventsAtOnce   the maximum number of epoll events to handle per epollWait(...). The default is
     *                          {@value #DEFAULT_MAX_EVENTS_AT_ONCE}.
     */
    public EpollEventLoopGroup(int nEventLoops,
                               ExecutorFactory executorFactory,
                               EventLoopMetricsFactory metricsFactory,
                               int maxEventsAtOnce) {
        super(nEventLoops, executorFactory, null, metricsFactory, maxEventsAtOnce);
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
    protected EventLoop newChild(Executor executor, EventLoopMetrics metrics, Object... args) throws Exception {
        return new EpollEventLoop(this, executor, metrics, (Integer) args[0]);
    }
}
