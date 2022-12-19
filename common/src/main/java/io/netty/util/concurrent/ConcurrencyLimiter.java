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

import static io.netty.util.internal.ObjectUtil.checkPositive;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jetbrains.annotations.VisibleForTesting;

import io.netty.util.internal.UnstableApi;

/**
 * A simple {@link Limiter} that limits the number of permits allowed concurrently.
 * It is similar to {@link Semaphore} in its behavior, except that it's asynchronous and callback-driven.
 * <pre>{@code
 * limit = new SimpleConcurrencyLimit(42);
 * handler = new ConcurrencyLimitHandler() {
 *   public void permitAcquired(Runnable releaser) {
 *     System.err.println("Acquired a permit!");
 *     ...
 *     // Release the acquired permit.
 *     releaser.run();
 *   }
 *
 *   public void permitAcquisitionTimedOut() {
 *     System.err.println("Failed to acquire a permit due to timeout.");
 *   }
 * }
 *
 * limit.acquire(eventLoop, handler, 10, TimeUnit.SECONDS);
 * }</pre>
 */
@UnstableApi
public class ConcurrencyLimiter implements Limiter {

    private static final AtomicIntegerFieldUpdater<ConcurrencyLimiter> permitsInUseUpdater =
            AtomicIntegerFieldUpdater.newUpdater(ConcurrencyLimiter.class, "permitsInUse");

    private volatile int permitsInUse;
    private final int maxPermits;
    private final Releaser releaser = new Releaser();
    private final Queue<PendingHandler> pendingHandlers = new ConcurrentLinkedQueue<PendingHandler>();

    public ConcurrencyLimiter(int maxPermits) {
        this.maxPermits = checkPositive(maxPermits, "maxPermits");
    }

    @VisibleForTesting
    int permitsInUse() {
        return permitsInUse;
    }

    @Override
    public void acquire(EventExecutor executor, final LimiterCallback callback,
                        long timeout, TimeUnit unit) {
        for (;;) {
            final int currentPermitsInUse = permitsInUse;
            if (currentPermitsInUse == maxPermits) {
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

            final int nextPermitsInUse = currentPermitsInUse + 1;
            if (permitsInUseUpdater.compareAndSet(this, currentPermitsInUse, nextPermitsInUse)) {
                if (executor.inEventLoop()) {
                    callback.permitAcquired(releaser);
                } else {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.permitAcquired(releaser);
                        }
                    });
                }
                return;
            }
        }
    }

    private void acquire(final PendingHandler pendingHandler) {
        for (;;) {
            final int currentPermitsInUse = permitsInUse;
            if (currentPermitsInUse == maxPermits) {
                pendingHandlers.add(pendingHandler);
                return;
            }

            final int nextPermitsInUse = currentPermitsInUse + 1;
            if (permitsInUseUpdater.compareAndSet(this, currentPermitsInUse, nextPermitsInUse)) {
                final EventExecutor executor = pendingHandler.executor;
                if (executor.inEventLoop()) {
                    pendingHandler.notifyAcquisition(releaser);
                } else {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            pendingHandler.notifyAcquisition(releaser);
                        }
                    });
                }
                return;
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
               "{maxPermits=" + maxPermits +
               ", permitsInUse=" + permitsInUse +
               '}';
    }

    private class Releaser implements Runnable {
        @Override
        public void run() {
            for (;;) {
                final int currentPermitInUse = permitsInUse;
                final int nextPermitInUse = currentPermitInUse - 1;
                assert nextPermitInUse >= 0 : "Released more than acquired";
                if (permitsInUseUpdater.compareAndSet(ConcurrencyLimiter.this,
                                                      currentPermitInUse, nextPermitInUse)) {
                    final PendingHandler pendingHandler = pendingHandlers.poll();
                    if (pendingHandler == null) {
                        return;
                    }

                    // XXX(trustin): Do we need to consider starvation?
                    acquire(pendingHandler);
                    return;
                }
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

        void notifyAcquisition(Releaser releaser) {
            if (timeoutFuture != null) {
                if (!timeoutFuture.cancel(false)) {
                    return;
                }
            }

            handler.permitAcquired(releaser);
        }
    }
}
