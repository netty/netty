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

import static io.netty5.util.concurrent.AbstractScheduledEventExecutor.deadlineNanos;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.callable;

/**
 * Abstract base class for {@link TaskScheduler} that want to support scheduling.
 *
 * @see AbstractScheduledEventExecutor
 */
public abstract class AbstractTaskScheduler implements TaskScheduler {

    /**
     * Returns a {@link RunnableScheduledFuture} for the given values.
     */
    protected abstract  <V> RunnableScheduledFuture<V> newScheduledTaskFor(Callable<V> callable,
                                                                           long deadlineNanos, long period);

    protected abstract Ticker ticker();

    @Override
    public Future<Void> schedule(Runnable command, long delay, TimeUnit unit) {
        requireNonNull(command, "command");
        requireNonNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        RunnableScheduledFuture<Void> task = newScheduledTaskFor(
                callable(command, null), deadlineNanos(ticker().nanoTime(), unit.toNanos(delay)), 0);
        return schedule(task);
    }

    @Override
    public  <V> Future<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        requireNonNull(callable, "callable");
        requireNonNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        RunnableScheduledFuture<V> task = newScheduledTaskFor(
                callable, deadlineNanos(ticker().nanoTime(), unit.toNanos(delay)), 0);
        return schedule(task);
    }

    @Override
    public Future<Void> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        requireNonNull(command, "command");
        requireNonNull(unit, "unit");
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (period <= 0) {
            throw new IllegalArgumentException(
                    String.format("period: %d (expected: > 0)", period));
        }

        RunnableScheduledFuture<Void> task = newScheduledTaskFor(
                callable(command, null),
                deadlineNanos(ticker().nanoTime(), unit.toNanos(initialDelay)), unit.toNanos(period));
        return schedule(task);
    }

    @Override
    public Future<Void> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        requireNonNull(command, "command");
        requireNonNull(unit, "unit");
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (delay <= 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: > 0)", delay));
        }

        RunnableScheduledFuture<Void> task = newScheduledTaskFor(
                callable(command, null),
                deadlineNanos(ticker().nanoTime(), unit.toNanos(initialDelay)), -unit.toNanos(delay));
        return schedule(task);
    }

    @Override
    public RunnableScheduledFuture<?> pollScheduledTask() {
        return pollScheduledTask(ticker().nanoTime());
    }

    @Override
    public RunnableScheduledFuture<?> pollScheduledTask(long nanoTime) {
        RunnableScheduledFuture<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return null;
        }

        if (scheduledTask.deadlineNanos() <= nanoTime) {
            removeNextScheduledTask();
            return scheduledTask;
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }
}
