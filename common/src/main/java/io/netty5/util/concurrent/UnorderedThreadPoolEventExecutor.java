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
package io.netty5.util.concurrent;

import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * {@link EventExecutor} implementation which makes no guarantees about the ordering of task execution that
 * are submitted because there may be multiple threads executing these tasks.
 * This implementation is most useful for protocols that do not need strict ordering.
 *
 * <strong>Because it provides no ordering care should be taken when using it!</strong>
 */
@SuppressWarnings("unchecked")
public final class UnorderedThreadPoolEventExecutor implements EventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(
            UnorderedThreadPoolEventExecutor.class);

    private final Promise<Void> terminationFuture = GlobalEventExecutor.INSTANCE.newPromise();
    private final InnerScheduledThreadPoolExecutor executor;

    /**
     * Calls {@link UnorderedThreadPoolEventExecutor#UnorderedThreadPoolEventExecutor(int, ThreadFactory)}
     * using {@link DefaultThreadFactory}.
     */
    public UnorderedThreadPoolEventExecutor(int corePoolSize) {
        DefaultThreadFactory threadFactory = new DefaultThreadFactory(UnorderedThreadPoolEventExecutor.class);
        executor = new InnerScheduledThreadPoolExecutor(this, corePoolSize, threadFactory);
    }

    /**
     * See {@link ScheduledThreadPoolExecutor#ScheduledThreadPoolExecutor(int, ThreadFactory)}
     */
    public UnorderedThreadPoolEventExecutor(int corePoolSize, ThreadFactory threadFactory) {
        executor = new InnerScheduledThreadPoolExecutor(this, corePoolSize, threadFactory);
    }

    /**
     * Calls {@link UnorderedThreadPoolEventExecutor#UnorderedThreadPoolEventExecutor(int,
     * ThreadFactory, java.util.concurrent.RejectedExecutionHandler)} using {@link DefaultThreadFactory}.
     */
    public UnorderedThreadPoolEventExecutor(int corePoolSize, RejectedExecutionHandler handler) {
        DefaultThreadFactory threadFactory = new DefaultThreadFactory(UnorderedThreadPoolEventExecutor.class);
        executor = new InnerScheduledThreadPoolExecutor(this, corePoolSize, threadFactory, handler);
    }

    /**
     * See {@link ScheduledThreadPoolExecutor#ScheduledThreadPoolExecutor(int, ThreadFactory, RejectedExecutionHandler)}
     */
    public UnorderedThreadPoolEventExecutor(int corePoolSize, ThreadFactory threadFactory,
                                            RejectedExecutionHandler handler) {
        executor = new InnerScheduledThreadPoolExecutor(this, corePoolSize, threadFactory, handler);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return false;
    }

    @Override
    public boolean isShuttingDown() {
        return isShutdown();
    }

    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        // TODO: At the moment this just calls shutdown but we may be able to do something more smart here which
        //       respects the quietPeriod and timeout.
        executor.shutdown();
        return terminationFuture();
    }

    @Override
    public Future<Void> terminationFuture() {
        return terminationFuture.asFuture();
    }

    @Override
    public Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
        return (Future<Void>) executor.schedule(task, delay, unit);
    }

    @Override
    public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        return (Future<V>) executor.schedule(task, delay, unit);
    }

    @Override
    public Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return (Future<Void>) executor.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    @Override
    public Future<Void> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return (Future<Void>) executor.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

    @Override
    public Future<Void> submit(Runnable task) {
        return (Future<Void>) executor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return (Future<T>) executor.submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return (Future<T>) executor.submit(task);
    }

    @Override
    public void execute(Runnable task) {
        executor.schedule(new NonNotifyRunnable(task), 0, NANOSECONDS);
    }

    /**
     * Return the task queue of the underlying {@link java.util.concurrent.Executor} instance.
     * <p>
     * Visible for testing.
     *
     * @return The task queue of this executor.
     */
    BlockingQueue<Runnable> getQueue() {
        return executor.getQueue();
    }

    /**
     * Note: this class has a natural ordering that is inconsistent with equals.
     */
    private static final class RunnableScheduledFutureTask<V> extends PromiseTask<V>
            implements RunnableScheduledFuture<V> {
        private final RunnableScheduledFuture<V> future;

        RunnableScheduledFutureTask(EventExecutor executor, Runnable runnable, RunnableScheduledFuture<V> future) {
            super(executor, runnable, null);
            this.future = future;
        }

        RunnableScheduledFutureTask(EventExecutor executor, Callable<V> callable, RunnableScheduledFuture<V> future) {
            super(executor, callable);
            this.future = future;
        }

        @Override
        public void run() {
            if (!isPeriodic()) {
                super.run();
            } else if (!isDone()) {
                try {
                    // Its a periodic task so we need to ignore the return value
                    future.run();
                } catch (Throwable cause) {
                    if (!tryFailureInternal(cause)) {
                        logger.warn("Failure during execution of task", cause);
                    }
                }
            }
        }

        @Override
        public boolean isPeriodic() {
            return future.isPeriodic();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return future.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return future.compareTo(o);
        }
    }

    // This is a special wrapper which we will be used in execute(...) to wrap the submitted Runnable. This is needed as
    // ScheduledThreadPoolExecutor.execute(...) will delegate to submit(...) which will then use decorateTask(...).
    // The problem with this is that decorateTask(...) needs to ensure we only do our own decoration if we not call
    // from execute(...) as otherwise we may end up creating an endless loop because DefaultPromise will call
    // EventExecutor.execute(...) when notify the listeners of the promise.
    //
    // See https://github.com/netty/netty/issues/6507
    private static final class NonNotifyRunnable implements Runnable {

        private final Runnable task;

        NonNotifyRunnable(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
        }
    }

    private static final class InnerScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
        private final EventExecutor eventExecutor;

        InnerScheduledThreadPoolExecutor(EventExecutor eventExecutor, int corePoolSize, ThreadFactory threadFactory) {
            super(corePoolSize, threadFactory);
            this.eventExecutor = eventExecutor;
        }

        InnerScheduledThreadPoolExecutor(EventExecutor eventExecutor, int corePoolSize, ThreadFactory threadFactory,
                                                RejectedExecutionHandler handler) {
            super(corePoolSize, threadFactory, handler);
            this.eventExecutor = eventExecutor;
        }

        @Override
        protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
            return runnable instanceof NonNotifyRunnable ?
                    task : new RunnableScheduledFutureTask<>(eventExecutor, runnable, task);
        }

        @Override
        protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
            return new RunnableScheduledFutureTask<>(eventExecutor, callable, task);
        }
    }
}
