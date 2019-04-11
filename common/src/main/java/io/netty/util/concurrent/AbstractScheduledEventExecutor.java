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

import static java.util.Objects.requireNonNull;

import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueue;
import io.netty.util.internal.PriorityQueueNode;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Abstract base class for {@link EventExecutor}s that want to support scheduling.
 */
public abstract class AbstractScheduledEventExecutor extends AbstractEventExecutor {
    static final long START_TIME = System.nanoTime();

    private static final Comparator<RunnableScheduledFutureNode<?>> SCHEDULED_FUTURE_TASK_COMPARATOR =
            Comparable::compareTo;

    private PriorityQueue<RunnableScheduledFutureNode<?>> scheduledTaskQueue;

    protected AbstractScheduledEventExecutor() {
    }

    /**
     * The time elapsed since initialization of this class in nanoseconds. This may return a negative number just like
     * {@link System#nanoTime()}.
     */
    public static long nanoTime() {
        return System.nanoTime() - START_TIME;
    }

    /**
     * The deadline (in nanoseconds) for a given delay (in nanoseconds).
     */
    static long deadlineNanos(long delay) {
        long deadlineNanos = nanoTime() + delay;
        // Guard against overflow
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    PriorityQueue<RunnableScheduledFutureNode<?>> scheduledTaskQueue() {
        if (scheduledTaskQueue == null) {
            scheduledTaskQueue = new DefaultPriorityQueue<>(
                    SCHEDULED_FUTURE_TASK_COMPARATOR,
                    // Use same initial capacity as java.util.PriorityQueue
                    11);
        }
        return scheduledTaskQueue;
    }

    private static boolean isNullOrEmpty(Queue<RunnableScheduledFutureNode<?>> queue) {
        return queue == null || queue.isEmpty();
    }

    /**
     * Cancel all scheduled tasks.
     *
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final void cancelScheduledTasks() {
        assert inEventLoop();
        PriorityQueue<RunnableScheduledFutureNode<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (isNullOrEmpty(scheduledTaskQueue)) {
            return;
        }

        final RunnableScheduledFutureNode<?>[] scheduledTasks =
                scheduledTaskQueue.toArray(new RunnableScheduledFutureNode<?>[0]);

        for (RunnableScheduledFutureNode<?> task: scheduledTasks) {
            task.cancel(false);
        }

        scheduledTaskQueue.clearIgnoringIndexes();
    }

    /**
     * @see #pollScheduledTask(long)
     */
    protected final RunnableScheduledFuture<?> pollScheduledTask() {
        return pollScheduledTask(nanoTime());
    }

    /**
     * Return the {@link Runnable} which is ready to be executed with the given {@code nanoTime}.
     * You should use {@link #nanoTime()} to retrieve the correct {@code nanoTime}.
     *
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final RunnableScheduledFuture<?> pollScheduledTask(long nanoTime) {
        assert inEventLoop();

        Queue<RunnableScheduledFutureNode<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        RunnableScheduledFutureNode<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        if (scheduledTask == null) {
            return null;
        }

        if (scheduledTask.deadlineNanos() <= nanoTime) {
            scheduledTaskQueue.remove();
            return scheduledTask;
        }
        return null;
    }

    /**
     * Return the nanoseconds when the next scheduled task is ready to be run or {@code -1} if no task is scheduled.
     *
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final long nextScheduledTaskNano() {
        Queue<RunnableScheduledFutureNode<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        RunnableScheduledFutureNode<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        if (scheduledTask == null) {
            return -1;
        }
        return Math.max(0, scheduledTask.deadlineNanos() - nanoTime());
    }

    final RunnableScheduledFuture<?> peekScheduledTask() {
        Queue<RunnableScheduledFutureNode<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (scheduledTaskQueue == null) {
            return null;
        }
        RunnableScheduledFutureNode<?> node = scheduledTaskQueue.peek();
        if (node == null) {
            return null;
        }
        return node;
    }

    /**
     * Returns {@code true} if a scheduled task is ready for processing.
     *
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected final boolean hasScheduledTasks() {
        assert inEventLoop();
        Queue<RunnableScheduledFutureNode<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        RunnableScheduledFutureNode<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        return scheduledTask != null && scheduledTask.deadlineNanos() <= nanoTime();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        requireNonNull(command, "command");
        requireNonNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        RunnableScheduledFuture<?> task = newScheduledTaskFor(Executors.callable(command),
                deadlineNanos(unit.toNanos(delay)), 0);
        return schedule(task);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        requireNonNull(callable, "callable");
        requireNonNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        RunnableScheduledFuture<V> task = newScheduledTaskFor(callable, deadlineNanos(unit.toNanos(delay)), 0);
        return schedule(task);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
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

        RunnableScheduledFuture<?> task = newScheduledTaskFor(Executors.<Void>callable(command, null),
                deadlineNanos(unit.toNanos(initialDelay)), unit.toNanos(period));
        return schedule(task);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
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

        RunnableScheduledFuture<?> task = newScheduledTaskFor(Executors.<Void>callable(command, null),
                deadlineNanos(unit.toNanos(initialDelay)), -unit.toNanos(delay));
        return schedule(task);
    }

    /**
     * Add the {@link RunnableScheduledFuture} for execution.
     */
    protected final <V> ScheduledFuture<V> schedule(final RunnableScheduledFuture<V> task) {
        if (inEventLoop()) {
            add0(task);
        } else {
            execute(() -> add0(task));
        }
        return task;
    }

    private <V> void add0(RunnableScheduledFuture<V> task) {
        final RunnableScheduledFutureNode node;
        if (task instanceof RunnableScheduledFutureNode) {
            node = (RunnableScheduledFutureNode) task;
        } else {
            node = new DefaultRunnableScheduledFutureNode<V>(task);
        }
        scheduledTaskQueue().add(node);
    }

    final void removeScheduled(final RunnableScheduledFutureNode<?> task) {
        if (inEventLoop()) {
            scheduledTaskQueue().removeTyped(task);
        } else {
            execute(() -> removeScheduled(task));
        }
    }

    /**
     * Returns a new {@link RunnableFuture} build on top of the given {@link Promise} and {@link Callable}.
     *
     * This can be used if you want to override {@link #newTaskFor(Callable)} and return a different
     * {@link RunnableFuture}.
     */
    protected static <V> RunnableScheduledFuture<V> newRunnableScheduledFuture(
            AbstractScheduledEventExecutor executor, Promise<V> promise, Callable<V> task,
            long deadlineNanos, long periodNanos) {
        return new RunnableScheduledFutureAdapter<V>(executor, promise, task, deadlineNanos, periodNanos);
    }

    /**
     * Returns a {@code RunnableScheduledFuture} for the given values.
     */
    protected <V> RunnableScheduledFuture<V> newScheduledTaskFor(
            Callable<V> callable, long deadlineNanos, long period) {
        return newRunnableScheduledFuture(this, this.newPromise(), callable, deadlineNanos, period);
    }

    interface RunnableScheduledFutureNode<V> extends PriorityQueueNode, RunnableScheduledFuture<V> { }

    private static final class DefaultRunnableScheduledFutureNode<V> implements RunnableScheduledFutureNode<V> {
        private final RunnableScheduledFuture<V> future;
        private int queueIndex = INDEX_NOT_IN_QUEUE;

        DefaultRunnableScheduledFutureNode(RunnableScheduledFuture<V> future) {
            this.future = future;
        }

        @Override
        public EventExecutor executor() {
            return future.executor();
        }

        @Override
        public long deadlineNanos() {
            return future.deadlineNanos();
        }

        @Override
        public long delayNanos() {
            return future.delayNanos();
        }

        @Override
        public long delayNanos(long currentTimeNanos) {
            return future.delayNanos(currentTimeNanos);
        }

        @Override
        public RunnableScheduledFuture<V> addListener(
                GenericFutureListener<? extends Future<? super V>> listener) {
            future.addListener(listener);
            return this;
        }

        @Override
        public RunnableScheduledFuture<V> addListeners(
                GenericFutureListener<? extends Future<? super V>>... listeners) {
            future.addListeners(listeners);
            return this;
        }

        @Override
        public RunnableScheduledFuture<V> removeListener(
                GenericFutureListener<? extends Future<? super V>> listener) {
            future.removeListener(listener);
            return this;
        }

        @Override
        public RunnableScheduledFuture<V> removeListeners(
                GenericFutureListener<? extends Future<? super V>>... listeners) {
            future.removeListeners(listeners);
            return this;
        }

        @Override
        public boolean isPeriodic() {
            return future.isPeriodic();
        }

        @Override
        public int priorityQueueIndex(DefaultPriorityQueue<?> queue) {
            return queueIndex;
        }

        @Override
        public void priorityQueueIndex(DefaultPriorityQueue<?> queue, int i) {
            queueIndex = i;
        }

        @Override
        public void run() {
            future.run();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(timeout, unit);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return future.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return future.compareTo(o);
        }

        @Override
        public RunnableFuture<V> sync() throws InterruptedException {
            future.sync();
            return this;
        }

        @Override
        public RunnableFuture<V> syncUninterruptibly() {
            future.syncUninterruptibly();
            return this;
        }

        @Override
        public RunnableFuture<V> await() throws InterruptedException {
            future.await();
            return this;
        }

        @Override
        public RunnableFuture<V> awaitUninterruptibly() {
            future.awaitUninterruptibly();
            return this;
        }

        @Override
        public boolean isSuccess() {
            return future.isSuccess();
        }

        @Override
        public boolean isCancellable() {
            return future.isCancellable();
        }

        @Override
        public Throwable cause() {
            return future.cause();
        }

        @Override
        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return future.await(timeout, unit);
        }

        @Override
        public boolean await(long timeoutMillis) throws InterruptedException {
            return future.await(timeoutMillis);
        }

        @Override
        public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
            return future.awaitUninterruptibly(timeout, unit);
        }

        @Override
        public boolean awaitUninterruptibly(long timeoutMillis) {
            return future.awaitUninterruptibly(timeoutMillis);
        }

        @Override
        public V getNow() {
            return future.getNow();
        }
    }
}
