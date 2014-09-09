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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for {@link EventExecutorGroup} implementations that handles their tasks with multiple threads at
 * the same time.
 */
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    private final EventExecutor[] children;
    private final Set<EventExecutor> readonlyChildren;
    private final AtomicInteger childIndex = new AtomicInteger();
    private final AtomicInteger terminatedChildren = new AtomicInteger();
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);
    private final EventExecutorChooser chooser;

    /**
     * @param nEventExecutors           the number of {@link EventExecutor}s that will be used by this instance.
     *                                  If {@code executor} is {@code null} this number will also be the parallelism
     *                                  requested from the default executor. It is generally advised for the number
     *                                  of {@link EventExecutor}s and the number of {@link Thread}s used by the
     *                                  {@code executor} to lie very close together.
     * @param executorServiceFactory    the {@link ExecutorServiceFactory} to use, or {@code null} if the default
     *                                  should be used.
     * @param args                      arguments which will passed to each {@link #newChild(Executor, Object...)} call.
     */
    protected MultithreadEventExecutorGroup(int nEventExecutors,
                                            ExecutorServiceFactory executorServiceFactory,
                                            Object... args) {
        this(nEventExecutors, executorServiceFactory != null
                                ? executorServiceFactory.newExecutorService(nEventExecutors)
                                : null,
             true, args);
    }

    /**
     * @param nEventExecutors   the number of {@link EventExecutor}s that will be used by this instance.
     *                          If {@code executor} is {@code null} this number will also be the parallelism
     *                          requested from the default executor. It is generally advised for the number
     *                          of {@link EventExecutor}s and the number of {@link Thread}s used by the
     *                          {@code executor} to lie very close together.
     * @param executor          the {@link Executor} to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nEventExecutors, Executor executor, Object... args) {
        this(nEventExecutors, executor, false, args);
    }

    private MultithreadEventExecutorGroup(int nEventExecutors,
                                          Executor executor,
                                          boolean shutdownExecutor,
                                          Object... args) {
        if (nEventExecutors <= 0) {
            throw new IllegalArgumentException(
                    String.format("nEventExecutors: %d (expected: > 0)", nEventExecutors));
        }

        if (executor == null) {
            executor = newDefaultExecutorService(nEventExecutors);
            shutdownExecutor = true;
        }

        children = new EventExecutor[nEventExecutors];
        if (isPowerOfTwo(children.length)) {
            chooser = new PowerOfTwoEventExecutorChooser();
        } else {
            chooser = new GenericEventExecutorChooser();
        }

        for (int i = 0; i < nEventExecutors; i ++) {
            boolean success = false;
            try {
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
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

        final boolean shutdownExecutor0 = shutdownExecutor;
        final Executor executor0 = executor;
        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (terminatedChildren.incrementAndGet() == children.length) {
                    terminationFuture.setSuccess(null);
                    if (shutdownExecutor0) {
                        // This cast is correct because shutdownExecutor0 is only try if
                        // executor0 is of type ExecutorService.
                        ((ExecutorService) executor0).shutdown();
                    }
                }
            }
        };

        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    protected ExecutorService newDefaultExecutorService(int nEventExecutors) {
        return new DefaultExecutorServiceFactory(getClass()).newExecutorService(nEventExecutors);
    }

    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    /**
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    public final int executorCount() {
        return children.length;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <E extends EventExecutor> Set<E> children() {
        return (Set<E>) readonlyChildren;
    }

    /**
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     *
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l: children) {
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
        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l: children) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children) {
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

    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    private interface EventExecutorChooser {
        EventExecutor next();
    }

    private final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        @Override
        public EventExecutor next() {
            return children[childIndex.getAndIncrement() & children.length - 1];
        }
    }

    private final class GenericEventExecutorChooser implements EventExecutorChooser {
        @Override
        public EventExecutor next() {
            return children[Math.abs(childIndex.getAndIncrement() % children.length)];
        }
    }
}
