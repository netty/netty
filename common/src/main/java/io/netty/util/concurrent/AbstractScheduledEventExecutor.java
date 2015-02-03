/*
 * Copyright 2015 The Netty Project
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

import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link EventExecutor}s that want to support scheduling.
 */
public abstract class AbstractScheduledEventExecutor extends AbstractEventExecutor {

    Queue<ScheduledFutureTask<?>> scheduledTaskQueue;

    protected static long nanoTime() {
        return ScheduledFutureTask.nanoTime();
    }

    Queue<ScheduledFutureTask<?>> scheduledTaskQueue() {
        if (scheduledTaskQueue == null) {
            scheduledTaskQueue = new PriorityQueue<ScheduledFutureTask<?>>();
        }
        return scheduledTaskQueue;
    }

    /**
     * Cancel all scheduled tasks.
     *
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final void cancelDelayedTasks() {
        assert inEventLoop();
        Queue<ScheduledFutureTask<?>> delayedTaskQueue = scheduledTaskQueue;
        if (delayedTaskQueue == null || delayedTaskQueue.isEmpty()) {
            return;
        }

        final ScheduledFutureTask<?>[] delayedTasks =
                delayedTaskQueue.toArray(new ScheduledFutureTask<?>[delayedTaskQueue.size()]);

        for (ScheduledFutureTask<?> task: delayedTasks) {
            task.cancel(false);
        }

        delayedTaskQueue.clear();
    }

    /**
     * @see {@link #pollScheduledTask(long)}
     */
    protected final Runnable pollScheduledTask() {
        return pollScheduledTask(nanoTime());
    }

    /**
     * Return the {@link Runnable} which is ready to be executed with the given {@code nanoTime}.
     * You should use {@link #nanoTime()} to retrieve the the correct {@code nanoTime}.
     */
    protected final Runnable pollScheduledTask(long nanoTime) {
        assert inEventLoop();

        Queue<ScheduledFutureTask<?>> delayedTaskQueue = scheduledTaskQueue;
        ScheduledFutureTask<?> delayedTask = delayedTaskQueue == null ? null : delayedTaskQueue.peek();
        if (delayedTask == null) {
            return null;
        }

        if (delayedTask.deadlineNanos() <= nanoTime) {
            delayedTaskQueue.remove();
            return delayedTask;
        }
        return null;
    }

    /**
     * Return the nanoseconds when the next scheduled task is ready to be run or {@code -1} if no task is scheduled.
     */
    protected final long nextScheduledTaskNano() {
        assert checkInEventLoop();

        Queue<ScheduledFutureTask<?>> delayedTaskQueue = scheduledTaskQueue;
        ScheduledFutureTask<?> delayedTask = delayedTaskQueue == null ? null : delayedTaskQueue.peek();
        if (delayedTask == null) {
            return -1;
        }
        return Math.max(0, delayedTask.deadlineNanos() - nanoTime());
    }

    final ScheduledFutureTask<?> peekScheduledTask() {
        assert checkInEventLoop();

        Queue<ScheduledFutureTask<?>> delayedTaskQueue = scheduledTaskQueue;
        if (delayedTaskQueue == null) {
            return null;
        }
        return delayedTaskQueue.peek();
    }

    /**
     * Returns {@code true} if a scheduled task is ready for processing.
     */
    protected final boolean hasScheduledTasks() {
        assert checkInEventLoop();

        Queue<ScheduledFutureTask<?>> delayedTaskQueue = scheduledTaskQueue;
        ScheduledFutureTask<?> delayedTask = delayedTaskQueue == null ? null : delayedTaskQueue.peek();
        return delayedTask != null && delayedTask.deadlineNanos() <= nanoTime();
    }

    @Override
    public  ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (delay < 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: >= 0)", delay));
        }
        return schedule(new ScheduledFutureTask<Void>(
                this, command, null, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        if (callable == null) {
            throw new NullPointerException("callable");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (delay < 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: >= 0)", delay));
        }
        return schedule(new ScheduledFutureTask<V>(
                this, callable, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (period <= 0) {
            throw new IllegalArgumentException(
                    String.format("period: %d (expected: > 0)", period));
        }

        return schedule(new ScheduledFutureTask<Void>(
                this, Executors.<Void>callable(command, null),
                ScheduledFutureTask.deadlineNanos(unit.toNanos(initialDelay)), unit.toNanos(period)));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (delay <= 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: > 0)", delay));
        }

        return schedule(new ScheduledFutureTask<Void>(
                this, Executors.<Void>callable(command, null),
                ScheduledFutureTask.deadlineNanos(unit.toNanos(initialDelay)), -unit.toNanos(delay)));
    }

    <V> ScheduledFuture<V> schedule(final ScheduledFutureTask<V> task) {
        if (inEventLoop()) {
            scheduledTaskQueue().add(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    scheduledTaskQueue().add(task);
                }
            });
        }

        return task;
    }

    void purgeCancelledScheduledTasks() {
        assert checkInEventLoop();
        Queue<ScheduledFutureTask<?>> delayedTaskQueue = scheduledTaskQueue;
        if (delayedTaskQueue == null || delayedTaskQueue.isEmpty()) {
            return;
        }
        Iterator<ScheduledFutureTask<?>> i = delayedTaskQueue.iterator();
        while (i.hasNext()) {
            ScheduledFutureTask<?> task = i.next();
            if (task.isCancelled()) {
                i.remove();
            }
        }
    }

    boolean checkInEventLoop() {
        return true;
    }
}
