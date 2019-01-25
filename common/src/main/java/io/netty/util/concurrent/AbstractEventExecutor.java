/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link EventExecutor} implementations.
 */
public abstract class AbstractEventExecutor extends AbstractExecutorService implements EventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractEventExecutor.class);

    static final long DEFAULT_SHUTDOWN_QUIET_PERIOD = 2;
    static final long DEFAULT_SHUTDOWN_TIMEOUT = 15;

    private final Collection<EventExecutor> selfCollection = Collections.singleton(this);

    @Override
    public EventExecutor next() {
        return this;
    }

    @Override
    public final boolean inEventLoop() {
        return inEventLoop(Thread.currentThread());
    }

    @Override
    public final Iterator<EventExecutor> iterator() {
        return selfCollection.iterator();
    }

    @Override
    public final Future<?> shutdownGracefully() {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public abstract void shutdown();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public <V> Promise<V> newPromise() {
        return new DefaultPromise<>(this);
    }

    @Override
    public <V> ProgressivePromise<V> newProgressivePromise() {
        return new DefaultProgressivePromise<>(this);
    }

    @Override
    public <V> Future<V> newSucceededFuture(V result) {
        return new SucceededFuture<>(this, result);
    }

    @Override
    public <V> Future<V> newFailedFuture(Throwable cause) {
        return new FailedFuture<>(this, cause);
    }

    @Override
    public final Future<?> submit(Runnable task) {
        return (Future<?>) super.submit(task);
    }

    @Override
    public final <T> Future<T> submit(Runnable task, T result) {
        return (Future<T>) super.submit(task, result);
    }

    @Override
    public final <T> Future<T> submit(Callable<T> task) {
        return (Future<T>) super.submit(task);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return newRunnableFuture(this.newPromise(), runnable, value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return newRunnableFuture(this.newPromise(), callable);
    }

    /**
     * Try to execute the given {@link Runnable} and just log if it throws a {@link Throwable}.
     */
    static void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            logger.warn("A task raised an exception. Task: {}", task, t);
        }
    }

    /**
     * Returns a new {@link RunnableFuture} build on top of the given {@link Promise} and {@link Callable}.
     *
     * This can be used if you want to override {@link #newTaskFor(Callable)} and return a different
     * {@link RunnableFuture}.
     */
    private static <V> RunnableFuture<V> newRunnableFuture(Promise<V> promise, Callable<V> task) {
        return new RunnableFutureAdapter<>(promise, task);
    }

    /**
     * Returns a new {@link RunnableFuture} build on top of the given {@link Promise} and {@link Runnable} and
     * {@code value}.
     *
     * This can be used if you want to override {@link #newTaskFor(Runnable, V)} and return a different
     * {@link RunnableFuture}.
     */
    private static <V> RunnableFuture<V> newRunnableFuture(Promise<V> promise, Runnable task, V value) {
        return new RunnableFutureAdapter<>(promise, Executors.callable(task, value));
    }
}
