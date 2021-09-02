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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for {@link EventExecutor} implementations.
 */
public abstract class AbstractEventExecutor implements EventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractEventExecutor.class);
    static final long DEFAULT_SHUTDOWN_QUIET_PERIOD = 2;
    static final long DEFAULT_SHUTDOWN_TIMEOUT = 15;

    private final Future<?> successfulVoidFuture = DefaultPromise.newSuccessfulPromise(this, null).asFuture();

    @Override
    public <V> Future<V> newSucceededFuture(V result) {
        if (result == null) {
            @SuppressWarnings("unchecked")
            Future<V> f = (Future<V>) successfulVoidFuture;
            return f;
        }
        return EventExecutor.super.newSucceededFuture(result);
    }

    @Override
    public final Future<?> submit(Runnable task) {
        var futureTask = newTaskFor(task, null);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public final <T> Future<T> submit(Runnable task, T result) {
        var futureTask = newTaskFor(task, result);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public final <T> Future<T> submit(Callable<T> task) {
        var futureTask = newTaskFor(task);
        execute(futureTask);
        return futureTask;
    }

    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return newRunnableFuture(newPromise(), runnable, value);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return newRunnableFuture(newPromise(), callable);
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
        return new RunnableFutureAdapter<>(promise, requireNonNull(task, "task"));
    }

    /**
     * Returns a new {@link RunnableFuture} build on top of the given {@link Promise} and {@link Runnable} and
     * {@code value}.
     */
    private static <V> RunnableFuture<V> newRunnableFuture(Promise<V> promise, Runnable task, V value) {
        return new RunnableFutureAdapter<>(promise, Executors.callable(requireNonNull(task, "task"), value));
    }
}
