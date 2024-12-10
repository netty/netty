/*
 * Copyright 2023 The Netty Project
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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * A task scheduler for {@link EventExecutor}s that want to support task scheduling.
 *
 * @see AbstractScheduledEventExecutor
 */
public interface TaskScheduler {

    /**
     * Schedule the {@link RunnableScheduledFuture} task for execution.
     */
    <V> Future<V> schedule(RunnableScheduledFuture<V> task);

    /**
     * Schedule the given {@link Runnable} task for execution after the given delay.
     */
    Future<Void> schedule(Runnable command, long delay, TimeUnit unit);

    /**
     * Schedule the given {@link Callable} task for execution after the given delay.
     */
    <V> Future<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * Schedule the given {@link Runnable} task for periodic execution.
     * The first execution will occur after the given initial delay, and the following repeated executions will occur
     * with the given period of time between each execution is started.
     * If the task takes longer to complete than the requested period, then the following executions will be delayed,
     * rather than allowing multiple instances of the task to run concurrently.
     * <p>
     * The task will be executed repeatedly until it either fails with an exception, or its future is
     * {@linkplain Future#cancel() cancelled}. The future thus will never complete successfully.
     */
    Future<Void> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * Schedule the given {@link Runnable} task for periodic execution.
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
     */
    Future<Void> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

    RunnableScheduledFuture<?> peekScheduledTask();

    RunnableScheduledFuture<?> pollScheduledTask();

    /**
     * Return the task which is ready to be executed with the given {@code nanoTime}.
     */
    RunnableScheduledFuture<?> pollScheduledTask(long nanoTime);

    void removeNextScheduledTask();

    void removeScheduled(RunnableScheduledFuture<?> task);

    /**
     * Cancel all scheduled tasks.
     */
    void cancelScheduledTasks();

    int size();

    boolean isEmpty();
}
