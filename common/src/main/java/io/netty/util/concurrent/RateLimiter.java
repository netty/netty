/*
 * Copyright 2022 The Netty Project
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

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * A rate-limiting {@link Limiter} based on <a href="https://en.wikipedia.org/wiki/Token_bucket">Token Bucket</a>
 * algorithm.
 */
@UnstableApi
public class RateLimiter implements Limiter {

    private static final Runnable noOpReleaser = new Runnable() {
        @Override
        public void run() {
            // No-op.
        }
    };

    private static final AtomicIntegerFieldUpdater<RateLimiter> bucketLevelUpdater =
            AtomicIntegerFieldUpdater.newUpdater(RateLimiter.class, "bucketLevel");

    private static final AtomicLongFieldUpdater<RateLimiter> lastRefillTimeNanosUpdater =
            AtomicLongFieldUpdater.newUpdater(RateLimiter.class, "lastRefillTimeNanos");

    private static final AtomicIntegerFieldUpdater<RateLimiter> refillTaskScheduledUpdater =
            AtomicIntegerFieldUpdater.newUpdater(RateLimiter.class, "refillTaskScheduled");

    private final int bucketSize;
    private final int refillAmount;
    private final long refillIntervalNanos;
    private volatile int bucketLevel;
    private volatile long lastRefillTimeNanos;
    private volatile int refillTaskScheduled; // 0 - NOT scheduled, 1 - scheduled

    private final Queue<PendingHandler> pendingHandlers = new ConcurrentLinkedQueue<PendingHandler>();

    /**
     * Creates a new {@link RateLimiter}.
     *
     * @param bucketSize the initial and maximum number of tokens in the bucket.
     * @param refillAmount the number of tokens to refill at the specified {@code refillInterval}.
     * @param refillInterval how often the tokens should be refilled into the bucket.
     * @param refillIntervalUnit the {@link TimeUnit} of {@code refillInterval}.
     */
    public RateLimiter(int bucketSize, int refillAmount, long refillInterval, TimeUnit refillIntervalUnit) {
        this.bucketSize = ObjectUtil.checkPositive(bucketSize, "bucketSize");
        this.refillAmount = ObjectUtil.checkPositive(refillAmount, "refillAmount");
        refillIntervalNanos = refillIntervalUnit.toNanos(
                ObjectUtil.checkPositive(refillInterval, "refillInterval"));
        bucketLevel = bucketSize;
        lastRefillTimeNanos = System.nanoTime();
    }

    @VisibleForTesting
    int bucketLevel() {
        return bucketLevel;
    }

    @Override
    public void acquire(EventExecutor executor, final LimiterCallback callback, long timeout, TimeUnit unit) {
        for (;;) {
            final int oldBucketLevel = bucketLevel;
            if (oldBucketLevel == 0) {
                // No tokens are available; wait until tokens are re-filled into the bucket.
                final PendingHandler pendingHandler = new PendingHandler(executor, callback);
                if (timeout > 0) {
                    final ScheduledFuture<?> timeoutFuture = executor.schedule(new Runnable() {
                        @Override
                        public void run() {
                            if (pendingHandler.isTimeoutFutureDone()) {
                                return;
                            }

                            pendingHandler.handler.permitAcquisitionTimedOut();
                        }
                    }, timeout, unit);
                    pendingHandler.setTimeoutFuture(timeoutFuture);
                }

                pendingHandlers.add(pendingHandler);
                return;
            }

            if (!bucketLevelUpdater.compareAndSet(this, oldBucketLevel, oldBucketLevel - 1)) {
                continue;
            }

            scheduleTokenRefill(executor);
            if (executor.inEventLoop()) {
                callback.permitAcquired(noOpReleaser);
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.permitAcquired(noOpReleaser);
                    }
                });
            }
            break;
        }
    }

    private void scheduleTokenRefill(final EventExecutor executor) {
        if (refillTaskScheduled != 0) {
            // Someone else scheduled the task already.
            return;
        }

        if (!refillTaskScheduledUpdater.compareAndSet(this, 0, 1)) {
            // Someone else scheduled the task meanwhile.
            return;
        }

        boolean taskSubmitted = false;
        try {
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        refillTokens();
                        notifyPendingHandlers();
                    } finally {
                        final PendingHandler pendingHandler = pendingHandlers.peek();
                        if (pendingHandler != null) {
                            boolean scheduledAgain = false;
                            try {
                                pendingHandler.executor.schedule(this, nextDelayNanos(), TimeUnit.NANOSECONDS);
                                scheduledAgain = true;
                            } finally {
                                if (!scheduledAgain) {
                                    refillTaskScheduled = 0;
                                }
                            }
                        } else {
                            refillTaskScheduled = 0;
                        }
                    }
                }
            }, nextDelayNanos(), TimeUnit.NANOSECONDS);
            taskSubmitted = true;
        } finally {
            if (!taskSubmitted) {
                refillTaskScheduled = 0;
            }
        }
    }

    private long nextDelayNanos() {
        final long lastRefillTimeNanos = this.lastRefillTimeNanos;
        final long currentTimeNanos = System.nanoTime();
        final long elapsedTimeNanos = currentTimeNanos - lastRefillTimeNanos;

        long nextRefillTimeNanos =
                lastRefillTimeNanos + elapsedTimeNanos / refillIntervalNanos * refillIntervalNanos;
        if (elapsedTimeNanos % refillIntervalNanos != 0) {
            nextRefillTimeNanos += refillIntervalNanos;
        }

        return nextRefillTimeNanos - currentTimeNanos;
    }

    private void refillTokens() {
        for (;;) {
            final long currentTimeNanos = System.nanoTime();
            final long lastRefillTimeNanos = this.lastRefillTimeNanos;
            final long elapsedTimeNanos = currentTimeNanos - lastRefillTimeNanos;
            final long numRefills = elapsedTimeNanos / refillIntervalNanos;
            final long actualRefillAmount = numRefills * refillAmount;
            final int oldBucketLevel = bucketLevel;
            final int newBucketLevel;
            if (oldBucketLevel + actualRefillAmount >= bucketSize) {
                newBucketLevel = bucketSize;
            } else {
                newBucketLevel = (int) (oldBucketLevel + actualRefillAmount);
            }

            if (bucketLevelUpdater.compareAndSet(this, oldBucketLevel, newBucketLevel)) {
                lastRefillTimeNanosUpdater.addAndGet(this, numRefills * refillIntervalNanos);
                // Successfully refilled the bucket.
                break;
            }
        }
    }

    private void notifyPendingHandlers() {
        for (;;) {
            if (pendingHandlers.isEmpty()) {
                break;
            }

            final int oldBucketLevel = bucketLevel;
            if (oldBucketLevel == 0) {
                break;
            }

            final int newBucketLevel = oldBucketLevel - 1;
            if (!bucketLevelUpdater.compareAndSet(this, oldBucketLevel, newBucketLevel)) {
                continue;
            }

            final PendingHandler pendingHandler = pendingHandlers.poll();
            if (pendingHandler == null) {
                // Someone removed the pending handler from the queue.
                // Roll back the bucket level change.
                bucketLevelUpdater.incrementAndGet(this);
                break;
            }

            final EventExecutor executor = pendingHandler.executor;
            if (executor.inEventLoop()) {
                pendingHandler.notifyAcquisition();
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        pendingHandler.notifyAcquisition();
                    }
                });
            }
        }
    }

    private static final class PendingHandler {
        final EventExecutor executor;
        final LimiterCallback handler;
        ScheduledFuture<?> timeoutFuture;

        PendingHandler(EventExecutor executor, LimiterCallback handler) {
            this.executor = executor;
            this.handler = handler;
        }

        void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
            this.timeoutFuture = timeoutFuture;
        }

        boolean isTimeoutFutureDone() {
            return timeoutFuture != null && timeoutFuture.isDone();
        }

        void notifyAcquisition() {
            if (timeoutFuture != null) {
                if (!timeoutFuture.cancel(false)) {
                    return;
                }
            }

            handler.permitAcquired(noOpReleaser);
        }
    }
}
