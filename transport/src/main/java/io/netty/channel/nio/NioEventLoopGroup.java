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
package io.netty.channel.nio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.metrics.EventLoopMetrics;
import io.netty.channel.EventLoopScheduler;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.metrics.EventLoopMetricsFactory;
import io.netty.util.concurrent.DefaultExecutorFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.metrics.EventExecutorMetrics;
import io.netty.util.concurrent.ExecutorFactory;
import io.netty.channel.EventLoopGroup;

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;

/**
 * A {@link MultithreadEventLoopGroup} implementation which is used for NIO {@link Selector} based
 * {@linkplain Channel}s.
 */
public class NioEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * Create a new instance that uses twice as many {@linkplain EventLoop}s as there are processors/cores available or
     * whatever is specified by {@code -Dio.netty.eventLoopThreads}. Further, the {@link Executor} returned by
     * {@link DefaultExecutorFactory} and the {@link SelectorProvider} returned by {@link SelectorProvider#provider()}
     * are used.
     */
    public NioEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance that uses the {@link DefaultExecutorFactory} and the {@link SelectorProvider} which
     * is returned by {@link SelectorProvider#provider()}.
     *
     * @param nEventLoops   the number of {@linkplain EventLoop}s that will be used by this group. This will also
     *                      be the number of threads requested from the {@link DefaultExecutorFactory}.
     */
    public NioEventLoopGroup(int nEventLoops) {
        this(nEventLoops, (Executor) null);
    }

    /**
     * Create a new instance that uses the {@link SelectorProvider} which is returned by
     * {@link SelectorProvider#provider()}.
     *
     * @param nEventLoops   the number of {@linkplain EventLoop}s that will be used by this instance.
     *                      If {@code Executor} is {@code null} this will also be the number of threads
     *                      requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                      and the number of threads used by the {@code Executor} to lie very close together.
     *                      You can set this parameter to {@code 0} if you want the system to chose the number of
     *                      {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param executor      the {@link Executor} to use, or {@code null} if the one returned
     *                      by {@link DefaultExecutorFactory} should be used.
     */
    public NioEventLoopGroup(int nEventLoops, Executor executor) {
        this(nEventLoops, executor, SelectorProvider.provider());
    }

    /**
     * Create a new instance that uses the {@link SelectorProvider} which is returned by
     * {@link SelectorProvider#provider()}.
     *
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if {@link DefaultExecutorFactory}
     *                          should be used.
     */
    public NioEventLoopGroup(int nEventLoops, ExecutorFactory executorFactory) {
        this(nEventLoops, executorFactory, SelectorProvider.provider());
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param executor          the {@link Executor} to use, or {@code null} if the one returned
     *                          by {@link DefaultExecutorFactory} should be used.
     * @param selectorProvider  the {@link SelectorProvider} to use. This value must not be {@code null}.
     */
    public NioEventLoopGroup(int nEventLoops, Executor executor, SelectorProvider selectorProvider) {
        this(nEventLoops, executor, (EventLoopScheduler) null, selectorProvider);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if {@link DefaultExecutorFactory}
     *                          should be used.
     * @param selectorProvider  the {@link SelectorProvider} to use. This value must not be {@code null}.
     */
    public NioEventLoopGroup(int nEventLoops, ExecutorFactory executorFactory, SelectorProvider selectorProvider) {
        this(nEventLoops, executorFactory, (EventLoopScheduler) null, selectorProvider);
    }

    /**
     * Create a new instance that uses the {@link SelectorProvider} which is returned by
     * {@link SelectorProvider#provider()}.
     *
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param scheduler         the {@link EventLoopScheduler} that, on every call to {@link EventLoopGroup#next()},
     *                          decides which {@link EventLoop} to return.
     */
    public NioEventLoopGroup(int nEventLoops, EventLoopScheduler scheduler) {
        this(nEventLoops, (Executor) null, scheduler, SelectorProvider.provider());
    }

    /**
     * Create a new instance that uses the {@link SelectorProvider} which is returned by
     * {@link SelectorProvider#provider()}.
     *
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param metricsFactory    provide your own {@link EventLoopMetricsFactory} to gather information for every
     *                          {@link EventLoop} of this group.
     */
    public NioEventLoopGroup(int nEventLoops, EventLoopMetricsFactory metricsFactory) {
        this(nEventLoops, (Executor) null, metricsFactory, SelectorProvider.provider());
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param executor          the {@link Executor} to use, or {@code null} if the one returned
     *                          by {@link DefaultExecutorFactory} should be used.
     * @param scheduler         the {@link EventLoopScheduler} that, on every call to {@link EventLoopGroup#next()},
     *                          decides which {@link EventLoop} to return.
     * @param selectorProvider  the {@link SelectorProvider} to use. This value must not be {@code null}.
     */
    public NioEventLoopGroup(int nEventLoops,
                             Executor executor,
                             EventLoopScheduler scheduler,
                             SelectorProvider selectorProvider) {
        super(nEventLoops, executor, scheduler, null, selectorProvider);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if {@link DefaultExecutorFactory}
     *                          should be used.
     * @param scheduler         the {@link EventLoopScheduler} that, on every call to {@link EventLoopGroup#next()},
     *                          decides which {@link EventLoop} to return.
     * @param selectorProvider  the {@link SelectorProvider} to use. This value must not be {@code null}.
     */
    public NioEventLoopGroup(int nEventLoops,
                             ExecutorFactory executorFactory,
                             EventLoopScheduler scheduler,
                             SelectorProvider selectorProvider) {
        super(nEventLoops, executorFactory, scheduler, null, selectorProvider);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param executor          the {@link Executor} to use, or {@code null} if the one returned
     *                          by {@link DefaultExecutorFactory} should be used.
     * @param metricsFactory    provide your own {@link EventLoopMetricsFactory} to gather information for every
     *                          {@link EventLoop} of this group.
     * @param selectorProvider  the {@link SelectorProvider} to use. This value must not be {@code null}.
     */
    public NioEventLoopGroup(int nEventLoops,
                             Executor executor,
                             EventLoopMetricsFactory metricsFactory,
                             SelectorProvider selectorProvider) {
        super(nEventLoops, executor, null, metricsFactory, selectorProvider);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s. See {@link #NioEventLoopGroup()} for details.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if {@link DefaultExecutorFactory}
     *                          should be used.
     * @param metricsFactory    provide your own {@link EventLoopMetricsFactory} to gather information for every
     *                          {@link EventLoop} of this group.
     * @param selectorProvider  the {@link SelectorProvider} to use. This value must not be {@code null}.
     */
    public NioEventLoopGroup(int nEventLoops,
                             ExecutorFactory executorFactory,
                             EventLoopMetricsFactory metricsFactory,
                             SelectorProvider selectorProvider) {
        super(nEventLoops, executorFactory, null, metricsFactory, selectorProvider);
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: children()) {
            ((NioEventLoop) e).setIoRatio(ioRatio);
        }
    }

    /**
     * Replaces the current {@link Selector}s of the child event loops with newly created {@link Selector}s to work
     * around the  infamous epoll 100% CPU bug.
     */
    public void rebuildSelectors() {
        for (EventExecutor e: children()) {
            ((NioEventLoop) e).rebuildSelector();
        }
    }

    @Override
    protected EventLoop newChild(Executor executor, EventLoopMetrics metrics, Object... args) throws Exception {
        return new NioEventLoop(this, executor, metrics, (SelectorProvider) args[0]);
    }
}
