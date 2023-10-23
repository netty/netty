/*
 * Copyright 2015 The Netty Project
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

import io.netty5.util.internal.PriorityQueueNode;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for {@link EventExecutor}s that want to support scheduling.
 */
public abstract class AbstractScheduledEventExecutor extends AbstractEventExecutor {

    private TaskScheduler taskScheduler;
    private TaskSchedulerFactory taskSchedulerFactory;

    protected AbstractScheduledEventExecutor() {
    }

    protected AbstractScheduledEventExecutor(TaskSchedulerFactory taskSchedulerFactory) {
        this.taskSchedulerFactory = requireNonNull(taskSchedulerFactory, "taskSchedulerFactory");
    }

    /**
     * Return the {@link Ticker} that provides the time source.
     */
    protected Ticker ticker() {
        return Ticker.systemTicker();
    }

    static long deadlineNanos(long nanoTime, long delay) {
        long deadlineNanos = nanoTime + delay;
        // Guard against overflow
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    TaskScheduler taskScheduler() {
        if (taskScheduler == null && taskSchedulerFactory != null) {
            taskScheduler = taskSchedulerFactory.newTaskScheduler(this);
        }
        if (taskScheduler == null) {
            taskScheduler = DefaultTaskSchedulerFactory.ISTANCE.newTaskScheduler(this);
        }
        return taskScheduler;
    }

    private static boolean isNullOrEmpty(TaskScheduler taskScheduler) {
        return taskScheduler == null || taskScheduler.isEmpty();
    }

    /**
     * Cancel all scheduled tasks.
     * <p>
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final void cancelScheduledTasks() {
        assert inEventLoop();
        TaskScheduler taskScheduler = this.taskScheduler;
        if (isNullOrEmpty(taskScheduler)) {
            return;
        }
        taskScheduler.cancelScheduledTasks();
    }

    /**
     * @see #pollScheduledTask(long)
     */
    protected final RunnableScheduledFuture<?> pollScheduledTask() {
        return pollScheduledTask(ticker().nanoTime());
    }

    /**
     * Return the {@link Runnable} which is ready to be executed with the given {@code nanoTime}. You should use {@link
     * #ticker().nanoTime()} to retrieve the correct {@code nanoTime}.
     * <p>
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final RunnableScheduledFuture<?> pollScheduledTask(long nanoTime) {
        assert inEventLoop();
        TaskScheduler taskScheduler = this.taskScheduler;
        return taskScheduler == null? null : taskScheduler.pollScheduledTask(nanoTime);
    }

    /**
     * Return the nanoseconds when the next scheduled task is ready to be run or {@code -1} if no task is scheduled.
     * <p>
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final long nextScheduledTaskNano() {
        assert inEventLoop();
        RunnableScheduledFuture<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return -1;
        }
        return Math.max(0, scheduledTask.deadlineNanos() - ticker().nanoTime());
    }

    final RunnableScheduledFuture<?> peekScheduledTask() {
        TaskScheduler taskScheduler = this.taskScheduler;
        return taskScheduler == null? null : taskScheduler.peekScheduledTask();
    }

    /**
     * Returns {@code true} if a scheduled task is ready for processing.
     * <p>
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final boolean hasScheduledTasks() {
        assert inEventLoop();
        RunnableScheduledFuture<?> scheduledTask = peekScheduledTask();
        return scheduledTask != null && scheduledTask.deadlineNanos() <= ticker().nanoTime();
    }

    @Override
    public Future<Void> schedule(Runnable command, long delay, TimeUnit unit) {
        return taskScheduler().schedule(command, delay, unit);
    }

    @Override
    public <V> Future<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return taskScheduler().schedule(callable, delay, unit);
    }

    @Override
    public Future<Void> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return taskScheduler().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public Future<Void> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return taskScheduler().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    /**
     * Schedule the {@link RunnableScheduledFuture} for execution.
     */
    protected final <V> Future<V> schedule(final RunnableScheduledFuture<V> task) {
        return taskScheduler().schedule(task);
    }

    final void removeScheduled(final RunnableScheduledFuture<?> task) {
        if (inEventLoop()) {
            taskScheduler().removeScheduled(task);
        } else {
            execute(() -> removeScheduled(task));
        }
    }

    interface RunnableScheduledFutureNode<V> extends PriorityQueueNode, RunnableScheduledFuture<V> {
    }
}
