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

import io.netty5.util.concurrent.AbstractScheduledEventExecutor.RunnableScheduledFutureNode;
import io.netty5.util.internal.DefaultPriorityQueue;
import io.netty5.util.internal.PriorityQueue;

import java.util.Comparator;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/**
 * Default task scheduler for {@link AbstractScheduledEventExecutor} that want to support task scheduling.
 * Uses {@link DefaultPriorityQueue} to schdeule tasks.
 */
public final class DefaultTaskScheduler extends AbstractTaskScheduler {

    private static final Comparator<RunnableScheduledFutureNode<?>> SCHEDULED_FUTURE_TASK_COMPARATOR =
            Comparable::compareTo;
    private static final RunnableScheduledFutureNode<?>[]
            EMPTY_RUNNABLE_SCHEDULED_FUTURE_NODES = new RunnableScheduledFutureNode<?>[0];

    private final AbstractScheduledEventExecutor executor;
    private final PriorityQueue<RunnableScheduledFutureNode<?>> scheduledTaskQueue;

    public DefaultTaskScheduler(AbstractScheduledEventExecutor executor) {
        this.executor = requireNonNull(executor, "executor");
        scheduledTaskQueue = new DefaultPriorityQueue<>(
                SCHEDULED_FUTURE_TASK_COMPARATOR,
                // Use same initial capacity as java.util.PriorityQueue
                11);
    }

    @Override
    protected <V> RunnableScheduledFuture<V> newScheduledTaskFor(Callable<V> callable, long deadlineNanos,
                                                                 long period) {
        return new RunnableScheduledFutureAdapter<>(executor, executor.newPromise(), callable, deadlineNanos, period);
    }

    @Override
    protected Ticker ticker() {
        return executor.ticker();
    }

    @Override
    public <V> Future<V> schedule(RunnableScheduledFuture<V> task) {
        if (executor.inEventLoop()) {
            final RunnableScheduledFutureNode<V> node;
            if (task instanceof RunnableScheduledFutureNode) {
                node = (RunnableScheduledFutureNode<V>) task;
            } else {
                node = new DefaultRunnableScheduledFutureNode<>(task);
            }
            scheduledTaskQueue.add(node);
            return node;
        } else {
            executor.execute(() -> schedule(task));
            return task;
        }
    }

    @Override
    public RunnableScheduledFuture<?> peekScheduledTask() {
        return scheduledTaskQueue.peek();
    }

    @Override
    public void removeNextScheduledTask() {
        scheduledTaskQueue.remove();
    }

    @Override
    public void removeScheduled(RunnableScheduledFuture<?> task) {
        scheduledTaskQueue.remove(task);
    }

    @Override
    public void cancelScheduledTasks() {
        final RunnableScheduledFutureNode<?>[] scheduledTasks =
                scheduledTaskQueue.toArray(EMPTY_RUNNABLE_SCHEDULED_FUTURE_NODES);

        for (RunnableScheduledFutureNode<?> task : scheduledTasks) {
            task.cancel();
        }

        scheduledTaskQueue.clearIgnoringIndexes();
    }

    @Override
    public int size() {
        return scheduledTaskQueue.size();
    }

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
        public RunnableScheduledFuture<V> addListener(FutureListener<? super V> listener) {
            future.addListener(listener);
            return this;
        }

        @Override
        public <C> RunnableScheduledFuture<V> addListener(
                C context, FutureContextListener<? super C, ? super V> listener) {
            future.addListener(context, listener);
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
        public boolean cancel() {
            return future.cancel();
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
        public FutureCompletionStage<V> asStage() {
            return future.asStage();
        }

        @Override
        public int compareTo(RunnableScheduledFuture<?> o) {
            return future.compareTo(o);
        }

        @Override
        public boolean isSuccess() {
            return future.isSuccess();
        }

        @Override
        public boolean isFailed() {
            return future.isFailed();
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
        public V getNow() {
            return future.getNow();
        }
    }
}
