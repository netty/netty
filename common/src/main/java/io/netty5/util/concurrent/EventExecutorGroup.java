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

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static io.netty5.util.concurrent.AbstractEventExecutor.DEFAULT_SHUTDOWN_QUIET_PERIOD;
import static io.netty5.util.concurrent.AbstractEventExecutor.DEFAULT_SHUTDOWN_TIMEOUT;

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
     * <p>
     * An executor group that "is shutting down" can still accept new tasks for a little while (the grace period),
     * but will eventually start rejecting new tasks.
     * At that point, the executor group will be {@linkplain #isShutdown() shut down}.
     *
     * @return {@code true} if all executors in this group have at least started shutting down, otherwise {@code false}.
     */
    boolean isShuttingDown();

    /**
     * Returns {@code true} if all {@link EventExecutor}s managed by this {@link EventExecutorGroup} have been
     * {@linkplain #shutdownGracefully() shut down gracefully} and moved past the grace period so that they are no
     * longer accepting any new tasks.
     * <p>
     * An executor group that "is shut down" might still be executing tasks that it has queued up, but it will no
     * longer be accepting any new tasks.
     * Once all running and queued tasks have completed, the executor group will be
     * {@linkplain #isTerminated() terminated}.
     *
     * @return {@code true} if all executors in this group have shut down and are no longer accepting any new tasks.
     */
    boolean isShutdown();

    /**
     * Returns {@code true} if all {@link EventExecutor}s managed by this {@link EventExecutorGroup} are
     * {@linkplain #isShutdown() shut down}, and all of their tasks have completed.
     *
     * @return {@code true} if all executors in this group have terminated.
     */
    default boolean isTerminated() {
        return terminationFuture().isDone();
    }

    /**
     * Wait for this {@link EventExecutorGroup} to {@linkplain #isTerminated() terminate}, up to the given timeout.
     *
     * @param timeout The non-negative maximum amount of time to wait for the executor group to terminate.
     * @param unit The non-null time unit of the timeout.
     * @return {@code true} if the executor group terminated within the specific timeout.
     * @throws InterruptedException If this thread was {@linkplain Thread#interrupt() interrupted} while waiting for
     * executor group to terminate.
     */
    default boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return terminationFuture().await(timeout, unit);
    }

    /**
     * Shortcut method for {@link #shutdownGracefully(long, long, TimeUnit)} with sensible default values.
     *
     * @return the {@link #terminationFuture()}
     */
    default Future<Void> shutdownGracefully() {
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
    Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * Returns the {@link Future} which is notified when all {@link EventExecutor}s managed by this
     * {@link EventExecutorGroup} have been terminated.
     *
     * @return The {@link Future} representing the termination of this {@link EventExecutorGroup}.
     */
    Future<Void> terminationFuture();

    /**
     * Returns one of the {@link EventExecutor}s managed by this {@link EventExecutorGroup}.
     */
    EventExecutor next();

    @Override
    Iterator<EventExecutor> iterator();

    /**
     * Submit the given task for execution in the next available {@link EventExecutor} in this group,
     * and return a future that produces a {@code null} result when the task completes.
     *
     * @param task The task that should be executed in this {@link EventExecutorGroup}.
     * @return A future that represents the completion of the submitted task.
     */
    default Future<Void> submit(Runnable task) {
        return next().submit(task);
    }

    /**
     * Submit the given task for execution in the next available {@link EventExecutor} in this group,
     * and return a future that produces the given result when the task completes.
     *
     * @param task The task that should be executed in this {@link EventExecutorGroup}.
     * @param result The value that the returned future will complete with, if the task completes successfully.
     * @param <T> The type of the future result.
     * @return A future that represents the completion of the submitted task.
     */
    default <T> Future<T> submit(Runnable task, T result) {
        return next().submit(task, result);
    }

    /**
     * Submit the given task for execution in the next available {@link EventExecutor} in this group,
     * and return a future that will return the result of the callable when the task completes.
     *
     * @param task The task that should be executed in this {@link EventExecutorGroup}.
     * @param <T> The type of the future result.
     * @return A future that represents the completion of the submitted task.
     */
    default <T> Future<T> submit(Callable<T> task) {
        return next().submit(task);
    }

    /**
     * Schedule the given task for execution after the given delay, in the next available {@link EventExecutor}
     * in this group, and return a future that produces a {@code null} result when the task completes.
     *
     * @param task The task that should be executed in this {@link EventExecutorGroup} after the given delay.
     * @param delay A positive time delay, in the given time unit.
     * @param unit The non-null time unit for the delay.
     * @return A future that represents the completion of the scheduled task.
     */
    default Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
        return next().schedule(task, delay, unit);
    }

    /**
     * Schedule the given task for execution after the given delay, in the next available {@link EventExecutor}
     * in this group, and return a future that will return the result of the callable when the task completes.
     *
     * @param task The task that should be executed in this {@link EventExecutorGroup} after the given delay.
     * @param delay A positive time delay, in the given time unit.
     * @param unit The non-null time unit for the delay.
     * @param <V> The type of the future result.
     * @return A future that represents the completion of the scheduled task.
     */
    default <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        return next().schedule(task, delay, unit);
    }

    /**
     * Schedule the given task for periodic execution in the next available {@link EventExecutor}.
     * The first execution will occur after the given initial delay, and the following repeated executions will occur
     * with the given period of time between each execution is started.
     * If the task takes longer to complete than the requested period, then the following executions will be delayed,
     * rather than allowing multiple instances of the task to run concurrently.
     * <p>
     * The task will be executed repeatedly until it either fails with an exception, or its future is
     * {@linkplain Future#cancel() cancelled}. The future thus will never complete successfully.
     *
     * @param task The task that should be scheduled to execute at a fixed rate in this {@link EventExecutorGroup}.
     * @param initialDelay The positive initial delay for the first task execution, in terms of the given time unit.
     * @param period The positive period for the execution frequency to use after the first execution has started,
     *               in terms of the given time unit.
     * @param unit The non-null time unit for the delay and period.
     * @return A future that represents the recurring task, and which can be cancelled to stop future executions.
     */
    default Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return next().scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    /**
     * Schedule the given task for periodic execution in the next available {@link EventExecutor}.
     * The first execution will occur after the given initial delay, and the following repeated executions will occur
     * with the given subsequent delay between one task completing and the next task starting.
     * The delay from the completion of one task, to the start of the next, stays unchanged regardless of how long a
     * task takes to complete.
     * <p>
     * This is in contrast to {@link #scheduleAtFixedRate(Runnable, long, long, TimeUnit)} which varies the delays
     * between the tasks in order to hit a given frequency.
     * <p>
     * The task will be executed repeatedly until it either fails with an exception, or its future is
     * {@linkplain Future#cancel() cancelled}. The future thus will never complete successfully.
     *
     * @param task The task that should be scheduled to execute with fixed delays in this {@link EventExecutorGroup}.
     * @param initialDelay The positive initial delay for the first task execution, in terms of the given time unit.
     * @param delay The positive subsequent delay between task, to use after the first execution has completed,
     *              in terms of the given time unit.
     * @param unit The non-null time unit for the delays.
     * @return A future that represents the recurring task, and which can be cancelled to stop future executions.
     */
    default Future<Void> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return next().scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

    @Override
    default void execute(Runnable task) {
        next().execute(task);
    }
}
