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
package io.netty.channel;

import io.netty.channel.metrics.EventLoopMetrics;
import io.netty.channel.metrics.EventLoopMetricsFactory;
import io.netty.channel.metrics.NoEventLoopMetrics;
import io.netty.util.concurrent.ExecutorFactory;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.concurrent.DefaultExecutorFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for {@linkplain EventLoopGroup}s implementations that manage multiple
 * {@linkplain EventLoop} instances.
 */
public abstract class MultithreadEventLoopGroup
        extends MultithreadEventExecutorGroup<EventLoop, EventLoopMetrics> implements EventLoopGroup {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MultithreadEventLoopGroup.class);

    private static final int DEFAULT_EVENT_LOOP_THREADS;

    static {
        DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", Runtime.getRuntime().availableProcessors() * 2));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
        }
    }

    /**
     * @param nEventLoops   the number of {@linkplain EventLoop}s that will be used by this instance.
     *                      If {@code Executor} is {@code null} this will also be the number of threads
     *                      requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                      and the number of threads used by the {@code Executor} to lie very close together.
     *                      You can set this parameter to {@code 0} if you want the system to chose the number of
     *                      {@linkplain EventLoop}s.
     * @param executor      the {@link Executor} to use, or {@code null} if the default should be used.
     * @param scheduler     the {@link EventLoopScheduler} that, on every call to
     *                      {@link EventLoop#next()}, decides which {@link EventLoop} to return.
     * @param args          arguments which will passed to each
     *                      {@link #newChild(Executor, EventLoopMetrics, Object...)} call.
     */
    protected MultithreadEventLoopGroup(int nEventLoops,
                                        Executor executor,
                                        EventLoopScheduler scheduler,
                                        EventLoopMetricsFactory metricsFactory,
                                        Object... args) {
        super(nEventLoops == 0
                ? DEFAULT_EVENT_LOOP_THREADS
                : nEventLoops,
              executor, scheduler, metricsFactory, args);
    }

    /**
     * @param nEventLoops       the number of {@linkplain EventLoop}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for {@code nEventLoops}
     *                          and the number of threads used by the {@code Executor} to lie very close together.
     *                          You can set this parameter to {@code 0} if you want the system to chose the number of
     *                          {@linkplain EventLoop}s.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if the default should be used.
     * @param scheduler         the {@link EventLoopScheduler} that, on every call to
     *                          {@link EventLoop#next()}, decides which {@link EventLoop} to return.
     * @param args              arguments which will passed to each
     *                          {@link #newChild(Executor, EventLoopMetrics, Object...)} call.
     */
    protected MultithreadEventLoopGroup(int nEventLoops,
                                        ExecutorFactory executorFactory,
                                        EventLoopScheduler scheduler,
                                        EventLoopMetricsFactory metricsFactory,
                                        Object... args) {
        super(nEventLoops == 0
                ? DEFAULT_EVENT_LOOP_THREADS
                : nEventLoops,
              executorFactory, scheduler, metricsFactory, args);
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    protected abstract EventLoop newChild(Executor executor, EventLoopMetrics metrics, Object... args)
            throws Exception;

    @Override
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        return next().register(channel, promise);
    }

    @Override
    protected EventLoopScheduler newDefaultScheduler(int nEventLoops) {
        return isPowerOfTwo(nEventLoops)
                ? new PowerOfTwoRoundRobinEventLoopScheduler(nEventLoops)
                : new RoundRobinEventLoopScheduler(nEventLoops);
    }

    private static final class RoundRobinEventLoopScheduler extends AbstractEventLoopScheduler {

        private final AtomicInteger index = new AtomicInteger();

        RoundRobinEventLoopScheduler(int nEventLoops) {
            super(nEventLoops);
        }

        @Override
        public EventLoop next() {
            return children.get(Math.abs(index.getAndIncrement() % children.size()));
        }

        @Override
        public EventLoopMetrics newMetrics() {
            return NoEventLoopMetrics.INSTANCE;
        }
    }

    private static final class PowerOfTwoRoundRobinEventLoopScheduler extends AbstractEventLoopScheduler {

        private final AtomicInteger index = new AtomicInteger();

        PowerOfTwoRoundRobinEventLoopScheduler(int nEventLoops) {
            super(nEventLoops);
        }

        @Override
        public EventLoop next() {
            return children.get(index.getAndIncrement() & children.size() - 1);
        }

        @Override
        public EventLoopMetrics newMetrics() {
            return NoEventLoopMetrics.INSTANCE;
        }
    }

    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }
}
