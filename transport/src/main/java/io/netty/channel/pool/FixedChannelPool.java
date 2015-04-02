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
package io.netty.channel.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.OneTimeTask;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link ChannelPool} implementation that takes another {@link ChannelPool} implementation and enfore a maximum
 * number of concurrent connections.
 *
 * @param <C>   the {@link Channel} type to pool.
 * @param <K>   the {@link ChannelPoolKey} that is used to store and lookup the {@link Channel}s.
 */
public final class FixedChannelPool<C extends Channel, K extends ChannelPoolKey> extends SimpleChannelPool<C, K> {
    private static final TimeoutException TIMEOUT_EXCEPTION =
            new TimeoutException("Acquire operation took longer then configured maximum time");
    static {
        TIMEOUT_EXCEPTION.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    enum AcquireTimeoutAction {
        /**
         * Create a new connection when the timeout is detected.
         */
        NewConnection,

        /**
         * Fail the {@link Future} of the acquire call with a {@link TimeoutException}.
         */
        Fail
    }

    private final EventExecutor executor;
    private final long acquireTimeoutNanos;
    private final Runnable timeoutTask;

    // There is no need to worry about synchronization as everything that modified the queue or counts is done
    // by the above EventExecutor.
    private final Queue<AcquireTask> pendingAcquireQueue = new ArrayDeque<AcquireTask>();
    private final int maxConnections;
    private final int maxPendingAcquires;
    private int acquiredChannelCount;
    private int pendingAcquireCount;

    /**
     * Creates a new instance using the {@link ActiveChannelHealthChecker} and a {@link ChannelPoolSegmentFactory} that
     * process things in LIFO order.
     *
     * @param bootstrap         the {@link Bootstrap} that is used for connections
     * @param handler           the {@link ChannelPoolHandler} that will be notified for the different pool actions
     * @param maxConnections    the numnber of maximal active connections, once this is reached new tries to acquire
     *                          a {@link Channel} will be delayed until a connection is returned to the pool again.
     */
    public FixedChannelPool(Bootstrap bootstrap,
                            ChannelPoolHandler<C, K> handler, int maxConnections) {
        this(bootstrap, handler, maxConnections, Integer.MAX_VALUE);
    }

    /**
     * Creates a new instance using the {@link ActiveChannelHealthChecker} and a {@link ChannelPoolSegmentFactory} that
     * process things in LIFO order.
     *
     * @param bootstrap             the {@link Bootstrap} that is used for connections
     * @param handler               the {@link ChannelPoolHandler} that will be notified for the different pool actions
     * @param maxConnections        the numnber of maximal active connections, once this is reached new tries to
     *                              acquire a {@link Channel} will be delayed until a connection is returned to the
     *                              pool again.
     * @param maxPendingAcquires    the maximum number of pending acquires. Once this is exceed acquire tries will
     *                              be failed.
     */
    public FixedChannelPool(Bootstrap bootstrap,
                            ChannelPoolHandler<C, K> handler, int maxConnections, int maxPendingAcquires) {
        super(bootstrap, handler);
        executor = bootstrap.group().next();
        timeoutTask = null;
        acquireTimeoutNanos = -1;
        this.maxConnections = maxConnections;
        this.maxPendingAcquires = maxPendingAcquires;
    }

    /**
     * Creates a new instance.
     *
     * @param bootstrap             the {@link Bootstrap} that is used for connections
     * @param handler               the {@link ChannelPoolHandler} that will be notified for the different pool actions
     * @param healthCheck           the {@link ChannelHealthChecker} that will be used to check if a {@link Channel} is
     *                              still healty when obtain from the {@link ChannelPool}
     * @param segmentFactory        the {@link ChannelPoolSegmentFactory} that will be used to create new
     *                              {@link ChannelPoolSegmentFactory.ChannelPoolSegment}s when needed
     * @param action                the {@link AcquireTimeoutAction} to use or {@code null} if non should be used.
     *                              In this case {@param acquireTimeoutMillis} must be {@code -1}.
     * @param acquireTimeoutMillis  the time (in milliseconds) after which an pending acquire must complete or
     *                              the {@link AcquireTimeoutAction} takes place.
     * @param maxConnections        the numnber of maximal active connections, once this is reached new tries to
     *                              acquire a {@link Channel} will be delayed until a connection is returned to the
     *                              pool again.
     * @param maxPendingAcquires    the maximum number of pending acquires. Once this is exceed acquire tries will
     *                              be failed.
     */
    public FixedChannelPool(Bootstrap bootstrap,
                            ChannelPoolHandler<C, K> handler,
                            ChannelHealthChecker<C, K> healthCheck,
                            ChannelPoolSegmentFactory<C, K> segmentFactory, AcquireTimeoutAction action,
                            final long acquireTimeoutMillis,
                            int maxConnections, int maxPendingAcquires) {
        super(bootstrap, handler, healthCheck, segmentFactory);
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections: " + maxConnections + " (expected: >= 1)");
        }
        if (maxPendingAcquires < 1) {
            throw new IllegalArgumentException("maxPendingAcquires: " + maxPendingAcquires + " (expected: >= 1)");
        }
        if (action == null && acquireTimeoutMillis == -1) {
            timeoutTask = null;
            acquireTimeoutNanos = -1;
        } else if (action == null && acquireTimeoutMillis != -1) {
            throw new NullPointerException("action");
        } else if (action != null && acquireTimeoutMillis < 0) {
            throw new IllegalArgumentException("acquireTimeoutMillis: " + acquireTimeoutMillis
                                               + " (acquireTimeoutMillis: >= 1)");
        } else {
            acquireTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMillis);
            switch (action) {
            case Fail:
                timeoutTask = new TimeoutTask() {
                    @Override
                    public void onTimeout(AcquireTask task) {
                        // Fail the promise as we timed out.
                        task.promise.setFailure(TIMEOUT_EXCEPTION);
                    }
                };
                break;
            case NewConnection:
                timeoutTask = new TimeoutTask() {
                    @Override
                    public void onTimeout(AcquireTask task) {
                        // Increment the acquire count and delegate to super to actually acquire a Channel which will
                        // create a new connetion.
                        ++acquiredChannelCount;

                        FixedChannelPool.super.acquire(task.key, task.promise);
                    }
                };
                break;
            default:
                throw new Error();
            }
        }
        executor = bootstrap.group().next();
        this.maxConnections = maxConnections;
        this.maxPendingAcquires = maxPendingAcquires;
    }

    @Override
    public Future<PooledChannel<C, K>> acquire(final K key, final Promise<PooledChannel<C, K>> promise) {
        try {
            if (executor.inEventLoop()) {
                acquire0(key, promise);
            } else {
                executor.execute(new OneTimeTask() {
                    @Override
                    public void run() {
                        acquire0(key, promise);
                    }
                });
            }
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
        return promise;
    }

    private void acquire0(K key, final Promise<PooledChannel<C, K>> promise) {
        assert executor.inEventLoop();

        if (acquiredChannelCount < maxConnections) {
            ++acquiredChannelCount;

            assert acquiredChannelCount > 0;

            // We need to create a new promise as we need to ensure the AcquireListener runs in the correct
            // EventLoop
            Promise<PooledChannel<C, K>> p = executor.newPromise();
            p.addListener(new AcquireListener(promise));
            super.acquire(key, p);
        } else {
            if (pendingAcquireCount >= maxPendingAcquires) {
                promise.setFailure(FULL_EXCEPTION);
            } else {
                AcquireTask task = new AcquireTask(key, promise);
                if (pendingAcquireQueue.offer(task)) {
                    ++pendingAcquireCount;

                    if (timeoutTask != null) {
                        task.timeoutFuture = executor.schedule(timeoutTask, acquireTimeoutNanos, TimeUnit.NANOSECONDS);
                    }
                } else {
                    promise.setFailure(FULL_EXCEPTION);
                }
            }

            assert pendingAcquireCount > 0;
        }
    }

    @Override
    SimplePooledChannel newPooledChannel(C channel, K key) {
        return new FixedPooledChannel(channel, key, this);
    }

    private void runTaskQueue() {
        while (acquiredChannelCount <= maxConnections) {
            AcquireTask  task = pendingAcquireQueue.poll();
            if (task == null) {
                break;
            }

            // Cancel the timeout if one was scheduled
            ScheduledFuture<?> timeoutFuture = task.timeoutFuture;
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }

            --pendingAcquireCount;
            ++acquiredChannelCount;

            super.acquire(task.key, task.promise);
        }

        // We should never have a negative value.
        assert pendingAcquireCount >= 0;
        assert acquiredChannelCount >= 0;
    }

    // AcquireTask extends AcquireListener to reduce object creations and so GC pressure
    private final class AcquireTask extends AcquireListener {
        final K key;
        final Promise<PooledChannel<C, K>> promise;
        final long creationTime = System.nanoTime();
        ScheduledFuture<?> timeoutFuture;

        public AcquireTask(final K key, Promise<PooledChannel<C, K>> promise) {
            super(promise);
            this.key = key;
            // We need to create a new promise as we need to ensure the AcquireListener runs in the correct
            // EventLoop.
            this.promise = executor.<PooledChannel<C, K>>newPromise().addListener(this);
        }
    }

    private abstract class TimeoutTask implements Runnable {
        @Override
        public final void run() {
            assert executor.inEventLoop();

            long expiredThresholdTime = System.nanoTime() - acquireTimeoutNanos;
            for (;;) {
                AcquireTask task = pendingAcquireQueue.peek();
                if (task == null || task.creationTime >= expiredThresholdTime) {
                    break;
                }
                pendingAcquireQueue.remove();

                --pendingAcquireCount;
                onTimeout(task);
            }
        }

        public abstract void onTimeout(AcquireTask task);
    }

    private class AcquireListener implements FutureListener<PooledChannel<C, K>> {
        private final Promise<PooledChannel<C, K>> originalPromise;

        AcquireListener(Promise<PooledChannel<C, K>> originalPromise) {
            this.originalPromise = originalPromise;
        }

        @Override
        public void operationComplete(Future<PooledChannel<C, K>> future) throws Exception {
            assert executor.inEventLoop();

            if (future.isSuccess()) {
                originalPromise.setSuccess(future.getNow());
            } else {
                // Something went wrong try to run pending acquire tasks.
                --acquiredChannelCount;

                // We should never have a negative value.
                assert acquiredChannelCount >= 0;

                // Run the pending acquire tasks before notify the original promise so if the user would
                // try to acquire again from the ChannelFutureListener and the pendingAcquireCount is >=
                // maxPendingAcquires we may be able to run some pending tasks first and so allow to add
                // more.
                runTaskQueue();
                originalPromise.setFailure(future.cause());
            }
        }
    }

    private final class FixedPooledChannel extends SimplePooledChannel {
        public FixedPooledChannel(C channel, K key, SimpleChannelPool<C, K> pool) {
            super(channel, key, pool);
        }

        @Override
        public Future<Void> releaseToPool(final Promise<Void> promise) {
            try {
                final Promise<Void> p = executor.newPromise();

                super.releaseToPool(p.addListener(new FutureListener<Void>() {

                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        assert executor.inEventLoop();
                        --acquiredChannelCount;

                        // We should never have a negative value.
                        assert acquiredChannelCount >= 0;

                        // Run the pending acquire tasks before notify the original promise so if the user would
                        // try to acquire again from the ChannelFutureListener and the pendingAcquireCount is >=
                        // maxPendingAcquires we may be able to run some pending tasks first and so allow to add
                        // more.
                        runTaskQueue();

                        if (future.isSuccess()) {
                            promise.setSuccess(null);
                        } else {
                            promise.setFailure(future.cause());
                        }
                    }
                }));
                return p;
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
            return promise;
        }
    }
}
