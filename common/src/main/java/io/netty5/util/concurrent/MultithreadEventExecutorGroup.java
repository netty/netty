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
package io.netty5.util.concurrent;

import io.netty5.util.internal.EmptyArrays;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty5.util.internal.ObjectUtil.checkPositive;

/**
 * {@link EventExecutorGroup} implementation that handles their tasks with multiple threads at
 * the same time.
 */
public class MultithreadEventExecutorGroup implements EventExecutorGroup {

    private final EventExecutor[] children;
    private final List<EventExecutor> readonlyChildren;
    private final AtomicInteger terminatedChildren = new AtomicInteger();
    private final Promise<Void> terminationFuture = GlobalEventExecutor.INSTANCE.newPromise();
    private final boolean powerOfTwo;

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or {@code null} if the default should be used.
     */
    public MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SingleThreadEventExecutor.DEFAULT_MAX_PENDING_EXECUTOR_TASKS,
                RejectedExecutionHandlers.reject());
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the {@link Executor} to use, or {@code null} if the default should be used.
     */
    public MultithreadEventExecutorGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SingleThreadEventExecutor.DEFAULT_MAX_PENDING_EXECUTOR_TASKS,
                RejectedExecutionHandlers.reject());
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the {@link ThreadFactory} to use, or {@code null} if the default should be used.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    public MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory,
                                         int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this(nThreads, threadFactory, maxPendingTasks, rejectedHandler, EmptyArrays.EMPTY_OBJECTS);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    public MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                         int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this(nThreads, executor, maxPendingTasks, rejectedHandler, EmptyArrays.EMPTY_OBJECTS);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or {@code null} if the default should be used.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     * @param args              arguments which will passed to each {@link #newChild(Executor, int,
     * RejectedExecutionHandler, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, int maxPendingTasks,
                                            RejectedExecutionHandler rejectedHandler, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory),
                maxPendingTasks, rejectedHandler, args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     * @param args              arguments which will passed to each {@link #newChild(Executor, int,
     * RejectedExecutionHandler, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, int maxPendingTasks,
                                            RejectedExecutionHandler rejectedHandler, Object... args) {
        checkPositive(nThreads, "nThreads");

        if (executor == null) {
            executor = new ThreadPerTaskExecutor(new DefaultThreadFactory(getClass()));
        }

        children = new EventExecutor[nThreads];
        powerOfTwo = isPowerOfTwo(children.length);
        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                children[i] = newChild(executor, maxPendingTasks, rejectedHandler, args);
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event executor", e);
            } finally {
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }

                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
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

        final FutureListener<Object> terminationListener = future -> {
            if (terminatedChildren.incrementAndGet() == children.length) {
                terminationFuture.setSuccess(null);
            }
        };

        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }
        readonlyChildren = Collections.unmodifiableList(Arrays.asList(children));
    }

    // Use a 'long' counter to avoid non-round-robin behaviour at the 32-bit overflow boundary.
    // The 64-bit long solves this by placing the overflow so far into the future, that no system
    // will encounter this in practice.
    private final AtomicLong idx = new AtomicLong();

    /**
     * The {@link EventExecutor}s that are used by this {@link MultithreadEventExecutorGroup}.
     */
    protected final List<EventExecutor> executors() {
        return readonlyChildren;
    }

    /**
     * Returns the next {@link EventExecutor} to use. The default implementation will use round-robin, but you may
     * override this to change the selection algorithm.
     */
    @Override
    public EventExecutor next() {
        if (powerOfTwo) {
            return children[(int) idx.getAndIncrement() & children.length - 1];
        }
        return children[(int) Math.abs(idx.getAndIncrement() % children.length)];
    }

    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return executors().iterator();
    }

    /**
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    public final int executorCount() {
        return executors().size();
    }

    /**
     * Create a new EventExecutor which will later then accessible via the {@link #next()} method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     *
     * As this method is called from within the constructor you can only use the parameters passed into the method when
     * overriding this method.
     */
    protected EventExecutor newChild(Executor executor,  int maxPendingTasks,
                                     RejectedExecutionHandler rejectedExecutionHandler,
                                     Object... args) {
        assert args.length == 0;
        return new SingleThreadEventExecutor(executor, maxPendingTasks, rejectedExecutionHandler);
    }

    @Override
    public final Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l: children) {
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        return terminationFuture();
    }

    @Override
    public final Future<Void> terminationFuture() {
        return terminationFuture.asFuture();
    }

    @Override
    public final boolean isShuttingDown() {
        for (EventExecutor l: children) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final boolean isShutdown() {
        for (EventExecutor l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final boolean isTerminated() {
        for (EventExecutor l: children) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop: for (EventExecutor l: children) {
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
