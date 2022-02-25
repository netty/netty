/*
 * Copyright 2016 The Netty Project
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

import static java.util.Objects.requireNonNull;


/**
 * <p>A promise combiner monitors the outcome of a number of discrete futures, then notifies a final, aggregate promise
 * when all of the combined futures are finished. The aggregate promise will succeed if and only if all of the combined
 * futures succeed. If any of the combined futures fail, the aggregate promise will fail. The cause failure for the
 * aggregate promise will be the failure for one of the failed combined futures; if more than one of the combined
 * futures fails, exactly which cause of failure will be assigned to the aggregate promise is undefined.</p>
 *
 * <p>Callers may populate a promise combiner with any number of futures to be combined via the
 * {@link PromiseCombiner#add(Future)} and {@link PromiseCombiner#addAll(Future[])} methods. When all futures to be
 * combined have been added, callers must provide an aggregate promise to be notified when all combined promises have
 * finished via the {@link PromiseCombiner#finish(Promise)} method.</p>
 *
 * <p>This implementation is <strong>NOT</strong> thread-safe and all methods must be called
 * from the {@link EventExecutor} thread.</p>
 */
public final class PromiseCombiner {
    private int expectedCount;
    private int doneCount;
    private Promise<Void> aggregatePromise;
    private Throwable cause;
    private final FutureListener<Object> listener = new FutureListener<>() {
        @Override
        public void operationComplete(final Future<?> future) {
            if (executor.inEventLoop()) {
                operationComplete0(future);
            } else {
                executor.execute(() -> operationComplete0(future));
            }
        }

        private void operationComplete0(Future<?> future) {
            assert executor.inEventLoop();
            ++doneCount;
            if (future.isFailed() && cause == null) {
                cause = future.cause();
            }
            if (doneCount == expectedCount && aggregatePromise != null) {
                tryPromise();
            }
        }
    };

    private final EventExecutor executor;

    /**
     * The {@link EventExecutor} to use for notifications. You must call {@link #add(Future)}, {@link #addAll(Future[])}
     * and {@link #finish(Promise)} from within the {@link EventExecutor} thread.
     *
     * @param executor the {@link EventExecutor} to use for notifications.
     */
    public PromiseCombiner(EventExecutor executor) {
        this.executor = requireNonNull(executor, "executor");
    }

    /**
     * Adds a new future to be combined. New futures may be added until an aggregate promise is added via the
     * {@link PromiseCombiner#finish(Promise)} method.
     *
     * @param future the future to add to this promise combiner
     */
    public void add(Future<?> future) {
        checkAddAllowed();
        checkInEventLoop();
        ++expectedCount;
        future.addListener(listener);
    }

    /**
     * Adds new futures to be combined. New futures may be added until an aggregate promise is added via the
     * {@link PromiseCombiner#finish(Promise)} method.
     *
     * @param futures the futures to add to this promise combiner
     */
    public void addAll(Future<?>... futures) {
        for (Future<?> future : futures) {
            add(future);
        }
    }

    /**
     * <p>Sets the promise to be notified when all combined futures have finished. If all combined futures succeed,
     * then the aggregate promise will succeed. If one or more combined futures fails, then the aggregate promise will
     * fail with the cause of one of the failed futures. If more than one combined future fails, then exactly which
     * failure will be assigned to the aggregate promise is undefined.</p>
     *
     * <p>After this method is called, no more futures may be added via the {@link PromiseCombiner#add(Future)} or
     * {@link PromiseCombiner#addAll(Future[])} methods.</p>
     *
     * @param aggregatePromise the promise to notify when all combined futures have finished
     */
    public void finish(Promise<Void> aggregatePromise) {
        requireNonNull(aggregatePromise, "aggregatePromise");
        checkInEventLoop();
        if (this.aggregatePromise != null) {
            throw new IllegalStateException("Already finished");
        }
        this.aggregatePromise = aggregatePromise;
        if (doneCount == expectedCount) {
            tryPromise();
        }
    }

    private void checkInEventLoop() {
        if (!executor.inEventLoop()) {
            throw new IllegalStateException("Must be called from EventExecutor thread");
        }
    }

    private boolean tryPromise() {
        return cause == null? aggregatePromise.trySuccess(null) : aggregatePromise.tryFailure(cause);
    }

    private void checkAddAllowed() {
        if (aggregatePromise != null) {
            throw new IllegalStateException("Adding promises is not allowed after finished adding");
        }
    }
}
