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
package io.netty.util.concurrent;

import io.netty.util.metrics.EventExecutorMetrics;
import io.netty.util.metrics.EventExecutorMetricsFactory;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for {@linkplain EventExecutorGroup}s implementations that manage multiple
 * {@linkplain EventExecutor} instances.
 */
public abstract class MultithreadEventExecutorGroup<T1 extends EventExecutor, T2 extends EventExecutorMetrics<T1>>
        extends AbstractEventExecutorGroup {

    private final AtomicInteger terminatedChildren = new AtomicInteger();
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);
    private final EventExecutorScheduler<T1, T2> scheduler;

    /**
     * @param nEventExecutors   the number of {@linkplain EventExecutor}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for
     *                          {@code nEventExecutors} and the number of threads used by the {@code Executor} to lie
     *                          very close together.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if the default should be used.
     * @param scheduler         the {@link EventExecutorScheduler} that, on every call to
     *                          {@link EventExecutorGroup#next()}, decides which {@link EventExecutor} to return.
     * @param args              arguments which will passed to each
     *                          {@link #newChild(Executor, EventExecutorMetrics, Object...)} call.
     */
    protected MultithreadEventExecutorGroup(int nEventExecutors, ExecutorFactory executorFactory,
                                            EventExecutorScheduler<T1, T2> scheduler,
                                            EventExecutorMetricsFactory<T2> metricsFactory,
                                            Object... args) {
        this(nEventExecutors,
             executorFactory == null
                ? null
                : executorFactory.newExecutor(nEventExecutors),
             scheduler, metricsFactory, args);
    }

    /**
     * @param nEventExecutors   the number of {@linkplain EventExecutor}s that will be used by this instance.
     *                          If {@code Executor} is {@code null} this will also be the number of threads
     *                          requested from the {@link DefaultExecutorFactory}. It is advised for
     *                          {@code nEventExecutors} and the number of threads used by the {@code Executor} to lie
     *                          very close together.
     * @param executor          the {@link Executor} to use, or {@code null} if the one returned
     *                          by {@link DefaultExecutorFactory} should be used.
     * @param scheduler         the {@link EventExecutorScheduler} that, on every call to
     *                          {@link EventExecutorGroup#next()}, decides which {@link EventExecutor} to return.
     * @param args              arguments which will passed to each
     *                          {@link #newChild(Executor, EventExecutorMetrics, Object...)} call.
     */
    protected MultithreadEventExecutorGroup(final int nEventExecutors, Executor executor,
                                            EventExecutorScheduler<T1, T2> scheduler,
                                            EventExecutorMetricsFactory<T2> metricsFactory,
                                            Object... args) {
        if (nEventExecutors <= 0) {
            throw new IllegalArgumentException(
                    String.format("nEventExecutors: %d (expected: > 0)", nEventExecutors));
        }

        if (executor == null) {
            executor = newDefaultExecutor(nEventExecutors);
        }

        this.scheduler = scheduler != null
                            ? scheduler
                            : newDefaultScheduler(nEventExecutors);

        for (int i = 0; i < nEventExecutors; i ++) {
            boolean success = false;
            try {
                T2 metrics = metricsFactory != null
                                ? metricsFactory.newMetrics()
                                : this.scheduler.newMetrics();
                T1 child = newChild(executor, metrics, args);
                metrics.init(child);
                this.scheduler.addChild(child, metrics);

                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    for (EventExecutor e : children()) {
                        e.shutdownGracefully();
                    }

                    for (EventExecutor e : children()) {
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (terminatedChildren.incrementAndGet() == nEventExecutors) {
                    terminationFuture.setSuccess(null);
                }
            }
        };

        for (EventExecutor e : children()) {
            e.terminationFuture().addListener(terminationListener);
        }
    }

    protected Executor newDefaultExecutor(int nEventExecutors) {
        return new DefaultExecutorFactory(getClass()).newExecutor(nEventExecutors);
    }

    /**
     * Returns the {@link EventExecutorScheduler} object to use by default.
     */
    protected abstract EventExecutorScheduler<T1, T2> newDefaultScheduler(int nEventExecutors);

    @Override
    public T1 next() {
        return scheduler.next();
    }

    /**
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    public final int executorCount() {
        return children().size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Set<T1> children() {
        return scheduler.children();
    }

    /**
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     *
     */
    protected abstract T1 newChild(Executor executor, T2 metrics, Object... args) throws Exception;

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l: children()) {
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() {
        for (EventExecutor l: children()) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l: children()) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children()) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children()) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop: for (EventExecutor l: children()) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    break loop;
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }
}
