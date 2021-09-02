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
package io.netty.util.concurrent;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.util.concurrent.AbstractEventExecutor.DEFAULT_SHUTDOWN_QUIET_PERIOD;
import static io.netty.util.concurrent.AbstractEventExecutor.DEFAULT_SHUTDOWN_TIMEOUT;

/**
 * The {@link EventExecutorGroup} is responsible for providing the {@link EventExecutor}'s to use
 * via its {@link #next()} method. Besides this, it is also responsible for handling their
 * life-cycle and allows shutting them down in a global fashion.
 *
 */
public interface EventExecutorGroup extends Iterable<EventExecutor>, Executor {

    /**
     * Returns {@code true} if and only if all {@link EventExecutor}s managed by this {@link EventExecutorGroup}
     * are being {@linkplain #shutdownGracefully() shut down gracefully} or was {@linkplain #isShutdown() shut down}.
     */
    boolean isShuttingDown();

    boolean isShutdown();

    boolean isTerminated();

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Shortcut method for {@link #shutdownGracefully(long, long, TimeUnit)} with sensible default values.
     *
     * @return the {@link #terminationFuture()}
     */
    default Future<?> shutdownGracefully() {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * Signals this executor that the caller wants the executor to be shut down.  Once this method is called,
     * {@link #isShuttingDown()} starts to return {@code true}, and the executor prepares to shut itself down.
     * This method ensures that no tasks are submitted for <i>'the quiet period'</i> (usually a couple seconds) before
     * it shuts itself down. If a task is submitted during the quiet period, it is guaranteed to be accepted and the
     * quiet period will start over.
     *
     * @param quietPeriod the quiet period as described in the documentation
     * @param timeout     the maximum amount of time to wait until the executor is
     * {@linkplain #isShuttingDown() shutting down} regardless if a task was submitted during the quiet period.
     * @param unit        the unit of {@code quietPeriod} and {@code timeout}
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * Returns the {@link Future} which is notified when all {@link EventExecutor}s managed by this
     * {@link EventExecutorGroup} have been terminated.
     */
    Future<?> terminationFuture();

    /**
     * Returns one of the {@link EventExecutor}s managed by this {@link EventExecutorGroup}.
     */
    EventExecutor next();

    @Override
    Iterator<EventExecutor> iterator();

    default Future<?> submit(Runnable task) {
        return next().submit(task);
    }

    default <T> Future<T> submit(Runnable task, T result) {
        return next().submit(task, result);
    }

    default <T> Future<T> submit(Callable<T> task) {
        return next().submit(task);
    }

    default Future<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return next().schedule(command, delay, unit);
    }

    default <V> Future<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return next().schedule(callable, delay, unit);
    }

    default Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return next().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    default Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return next().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    default void execute(Runnable command) {
        next().execute(command);
    }
}
