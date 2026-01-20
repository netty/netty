/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import org.jetbrains.annotations.Async.Execute;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link EventExecutor} implementations.
 */
public abstract class AbstractEventExecutor implements EventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractEventExecutor.class);

    static final long DEFAULT_SHUTDOWN_QUIET_PERIOD = 2;
    static final long DEFAULT_SHUTDOWN_TIMEOUT = 15;

    private final EventExecutorGroup parent;
    private final Collection<EventExecutor> selfCollection = Collections.<EventExecutor>singleton(this);

    private final Future<?> succeededFuture = new SucceededFuture<>(this, null);

    protected AbstractEventExecutor() {
        this(null);
    }

    protected AbstractEventExecutor(EventExecutorGroup parent) {
        this.parent = parent;
    }

    @Override
    public EventExecutorGroup parent() {
        return parent;
    }

    @Override
    public EventExecutor next() {
        return this;
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return selfCollection.iterator();
    }

    @Override
    public Future<?> shutdownGracefully() {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    @Override
    public final Future<?> submit(Runnable task) {
        Objects.requireNonNull(task, "task");
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        execute(ftask);
        return (Future<?>) ftask;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> Future<T> submit(Runnable task, T result) {
        Objects.requireNonNull(task, "task");
        RunnableFuture<T> ftask = newTaskFor(task, result);
        execute(ftask);
        return (Future<T>) ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public final <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        RunnableFuture<T> ftask = newTaskFor(task);
        execute(ftask);
        return (Future<T>) ftask;
    }

    protected final <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new PromiseTask<T>(this, runnable, value);
    }

    protected final <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new PromiseTask<T>(this, callable);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> Future<V> newSucceededFuture(V result) {
        if (result == null) {
            return (Future<V>) succeededFuture;
        }
        return EventExecutor.super.newSucceededFuture(result);
    }

    protected final <T> ScheduledFuture<T> newFailedScheduledFuture(Throwable cause) {
        return new FailedScheduledFuture<>(this, cause);
    }

    /**
     * Try to execute the given {@link Runnable} and just log if it throws a {@link Throwable}.
     */
    protected static void safeExecute(Runnable task) {
        try {
            runTask(task);
        } catch (Throwable t) {
            logger.warn("A task raised an exception. Task: {}", task, t);
        }
    }

    protected static void runTask(@Execute Runnable task) {
        task.run();
    }

    /**
     * Like {@link #execute(Runnable)} but does not guarantee the task will be run until either
     * a non-lazy task is executed or the executor is shut down.
     * <p>
     * The default implementation just delegates to {@link #execute(Runnable)}.
     * </p>
     */
    @UnstableApi
    public void lazyExecute(Runnable task) {
        execute(task);
    }

    /**
     *  @deprecated override {@link SingleThreadEventExecutor#wakesUpForTask} to re-create this behaviour
     *
     */
    @Deprecated
    public interface LazyRunnable extends Runnable { }
}
