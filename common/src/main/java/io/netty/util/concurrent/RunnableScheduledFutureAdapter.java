/*
 * Copyright 2013 The Netty Project
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
import io.netty.util.internal.StringUtil;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("ComparableImplementedButEqualsNotOverridden")
final class RunnableScheduledFutureAdapter<V> implements RunnableScheduledFuture<V>,
        AbstractScheduledEventExecutor.RunnableScheduledFutureNode<V> {
    private static final AtomicLong NEXT_TASK_ID = new AtomicLong();

    private final long id = NEXT_TASK_ID.getAndIncrement();
    private long deadlineNanos;
    /* 0 - no repeat, >0 - repeat at fixed rate, <0 - repeat with fixed delay */
    private final long periodNanos;

    private int queueIndex = INDEX_NOT_IN_QUEUE;

    private final AbstractScheduledEventExecutor executor;
    private final Promise<V> promise;
    private final Callable<V> callable;

    RunnableScheduledFutureAdapter(AbstractScheduledEventExecutor executor, Promise<V> promise, Callable<V> callable,
                                   long deadlineNanos, long periodNanos) {
        this.executor = requireNonNull(executor, "executor");
        this.promise = requireNonNull(promise, "promise");
        this.callable = requireNonNull(callable, "callable");
        this.deadlineNanos = deadlineNanos;
        this.periodNanos = periodNanos;
    }

    @Override
    public EventExecutor executor() {
        return executor;
    }

    @Override
    public long deadlineNanos() {
        return deadlineNanos;
    }

    @Override
    public long delayNanos() {
        return Math.max(0, deadlineNanos() - AbstractScheduledEventExecutor.nanoTime());
    }

    @Override
    public long delayNanos(long currentTimeNanos) {
        return Math.max(0, deadlineNanos() - (currentTimeNanos - AbstractScheduledEventExecutor.START_TIME));
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o) {
            return 0;
        }

        RunnableScheduledFutureAdapter<?> that = (RunnableScheduledFutureAdapter<?>) o;
        long d = deadlineNanos() - that.deadlineNanos();
        if (d < 0) {
            return -1;
        } else if (d > 0) {
            return 1;
        } else if (id < that.id) {
            return -1;
        } else if (id == that.id) {
            throw new Error();
        } else {
            return 1;
        }
    }

    @Override
    public void run() {
        try {
            if (!isPeriodic()) {
                if (promise.setUncancellable()) {
                    V result = callable.call();
                    promise.setSuccess(result);
                }
            } else {
                // check if is done as it may was cancelled
                if (!isCancelled()) {
                    callable.call();
                    if (!executor.isShutdown()) {
                        long p = periodNanos;
                        if (p > 0) {
                            deadlineNanos += p;
                        } else {
                            deadlineNanos = AbstractScheduledEventExecutor.nanoTime() - p;
                        }
                        if (!isCancelled()) {
                            executor.schedule(this);
                        }
                    }
                }
            }
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean canceled = promise.cancel(mayInterruptIfRunning);
        if (canceled) {
            executor.removeScheduled(this);
        }
        return canceled;
    }

    @Override
    public boolean isSuccess() {
        return promise.isSuccess();
    }

    @Override
    public boolean isCancellable() {
        return promise.isCancellable();
    }

    @Override
    public Throwable cause() {
        return promise.cause();
    }

    @Override
    public RunnableScheduledFuture<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        promise.addListener(listener);
        return this;
    }

    @Override
    public RunnableScheduledFuture<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        promise.addListeners(listeners);
        return this;
    }

    @Override
    public RunnableScheduledFuture<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        promise.removeListener(listener);
        return this;
    }

    @Override
    public RunnableScheduledFuture<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        promise.removeListeners(listeners);
        return this;
    }

    @Override
    public RunnableScheduledFuture<V> sync() throws InterruptedException {
        promise.sync();
        return this;
    }

    @Override
    public RunnableScheduledFuture<V> syncUninterruptibly() {
        promise.syncUninterruptibly();
        return this;
    }

    @Override
    public RunnableScheduledFuture<V> await() throws InterruptedException {
        promise.await();
        return this;
    }

    @Override
    public RunnableScheduledFuture<V> awaitUninterruptibly() {
        promise.awaitUninterruptibly();
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return promise.await(timeout, unit);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return promise.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return promise.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return promise.awaitUninterruptibly(timeoutMillis);
    }

    @Override
    public V getNow() {
        return promise.getNow();
    }

    @Override
    public boolean isPeriodic() {
        return periodNanos != 0;
    }

    @Override
    public boolean isCancelled() {
        return promise.isCancelled();
    }

    @Override
    public boolean isDone() {
        return promise.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return promise.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return promise.get(timeout, unit);
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
    public String toString() {
        StringBuilder buf = new StringBuilder(64)
                .append(StringUtil.simpleClassName(this))
                .append('@')
                .append(Integer.toHexString(hashCode()));

        if (!isDone()) {
            buf.append("(incomplete)");
        } else {
            Throwable cause = cause();
            if (cause != null) {
                buf.append("(failure: ")
                        .append(cause)
                        .append(')');
            } else {
                Object result = getNow();
                if (result == null) {
                    buf.append("(success)");
                } else {
                    buf.append("(success: ")
                            .append(result)
                            .append(')');
                }
            }
        }
        return buf.append(" task: ")
                .append(callable)
                .append(", id: ")
                .append(id)
                .append(", deadline: ")
                .append(deadlineNanos)
                .append(", period: ")
                .append(periodNanos)
                .append(')').toString();
    }
}
