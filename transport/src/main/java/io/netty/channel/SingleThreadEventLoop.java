/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.util.IntSupplier;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * Abstract base class for {@link EventLoop}s that execute all its submitted tasks in a single thread.
 *
 */
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected static final int DEFAULT_MAX_PENDING_TASKS = Math.max(16,
            SystemPropertyUtil.getInt("io.netty.eventLoop.maxPendingTasks", Integer.MAX_VALUE));

    protected static final boolean LOOP_LOAD =
            SystemPropertyUtil.getBoolean("io.netty.eventLoop.load", false);

    private final Queue<Runnable> tailTasks;

    protected SingleThreadEventLoop(EventLoopGroup parent, ThreadFactory threadFactory, boolean addTaskWakesUp) {
        this(parent, threadFactory, addTaskWakesUp, DEFAULT_MAX_PENDING_TASKS, RejectedExecutionHandlers.reject());
    }

    protected SingleThreadEventLoop(EventLoopGroup parent, Executor executor, boolean addTaskWakesUp) {
        this(parent, executor, addTaskWakesUp, DEFAULT_MAX_PENDING_TASKS, RejectedExecutionHandlers.reject());
    }

    protected SingleThreadEventLoop(EventLoopGroup parent, ThreadFactory threadFactory,
                                    boolean addTaskWakesUp, int maxPendingTasks,
                                    RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, threadFactory, addTaskWakesUp, maxPendingTasks, rejectedExecutionHandler);
        tailTasks = newTaskQueue(maxPendingTasks);
    }

    protected SingleThreadEventLoop(EventLoopGroup parent, Executor executor,
                                    boolean addTaskWakesUp, int maxPendingTasks,
                                    RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, addTaskWakesUp, maxPendingTasks, rejectedExecutionHandler);
        tailTasks = newTaskQueue(maxPendingTasks);
    }

    protected SingleThreadEventLoop(EventLoopGroup parent, Executor executor,
                                    boolean addTaskWakesUp, Queue<Runnable> taskQueue, Queue<Runnable> tailTaskQueue,
                                    RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, addTaskWakesUp, taskQueue, rejectedExecutionHandler);
        tailTasks = ObjectUtil.checkNotNull(tailTaskQueue, "tailTaskQueue");
    }

    @Override
    public EventLoopGroup parent() {
        return (EventLoopGroup) super.parent();
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return register(new DefaultChannelPromise(channel, this));
    }

    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }

    @Deprecated
    @Override
    public ChannelFuture register(final Channel channel, final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        ObjectUtil.checkNotNull(channel, "channel");
        channel.unsafe().register(this, promise);
        return promise;
    }

    /**
     * Adds a task to be run once at the end of next (or current) {@code eventloop} iteration.
     *
     * @param task to be added.
     */
    public final void executeAfterEventLoopIteration(Runnable task) {
        ObjectUtil.checkNotNull(task, "task");
        if (isShutdown()) {
            reject();
        }

        if (!tailTasks.offer(task)) {
            reject(task);
        }

        if (wakesUpForTask(task)) {
            wakeup(inEventLoop());
        }
    }

    /**
     * Removes a task that was added previously via {@link #executeAfterEventLoopIteration(Runnable)}.
     *
     * @param task to be removed.
     *
     * @return {@code true} if the task was removed as a result of this call.
     */
    final boolean removeAfterEventLoopIterationTask(Runnable task) {
        return tailTasks.remove(ObjectUtil.checkNotNull(task, "task"));
    }

    @Override
    protected void afterRunningAllTasks() {
        runAllTasksFrom(tailTasks);
    }

    @Override
    protected boolean hasTasks() {
        return super.hasTasks() || !tailTasks.isEmpty();
    }

    @Override
    public int pendingTasks() {
        return super.pendingTasks() + tailTasks.size();
    }

    /**
     * Returns the current load value of the {@link EventLoop} over a one-minute period,
     * or returns {@code -1} if operation is not supported.
     */
    public double loadAvg1() {
        return -1;
    }

    /**
     * Returns the current load value of the {@link EventLoop} over a five-minute period,
     * or returns {@code -1} if operation is not supported.
     */
    public double loadAvg5() {
        return -1;
    }

    /**
     * Returns the current load value of the {@link EventLoop} over a fifteen-minute period,
     * or returns {@code -1} if operation is not supported.
     */
    public double loadAvg15() {
        return -1;
    }

    /**
     * Returns the number of {@link Channel}s registered with this {@link EventLoop} or {@code -1}
     * if operation is not supported. The returned value is not guaranteed to be exact accurate and
     * should be viewed as a best effort.
     */
    @UnstableApi
    public int registeredChannels() {
        return -1;
    }

    /**
     * @return read-only iterator of active {@link Channel}s registered with this {@link EventLoop}.
     *         The returned value is not guaranteed to be exact accurate and
     *         should be viewed as a best effort. This method is expected to be called from within
     *         event loop.
     * @throws UnsupportedOperationException if operation is not supported by implementation.
     */
    @UnstableApi
    public Iterator<Channel> registeredChannelsIterator() {
        throw new UnsupportedOperationException("registeredChannelsIterator");
    }

    protected static final class ChannelsReadOnlyIterator<T extends Channel> implements Iterator<Channel> {
        private final Iterator<T> channelIterator;

        public ChannelsReadOnlyIterator(Iterable<T> channelIterable) {
            this.channelIterator =
                    ObjectUtil.checkNotNull(channelIterable, "channelIterable").iterator();
        }

        @Override
        public boolean hasNext() {
            return channelIterator.hasNext();
        }

        @Override
        public Channel next() {
            return channelIterator.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }

        @SuppressWarnings("unchecked")
        public static <T> Iterator<T> empty() {
            return (Iterator<T>) EMPTY;
        }

        private static final Iterator<Object> EMPTY = new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public Object next() {
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        };
    }

    protected static class EventLoopLoadTracker implements Runnable {

        /**
         * 1/exp(5sec/1min)
         */
        private static final double EXP_1 = Math.exp(-((double) 5) / 60);

        /**
         * 1/exp(5sec/5min)
         */
        private static final double EXP_5 = Math.exp(-((double) 5) / (5 * 60));

        /**
         * 1/exp(5sec/15min)
         */
        private static final double EXP_15 = Math.exp(-((double) 5) / (15 * 60));

        private final IntSupplier currentTasksFunction;

        private double loadAvg1;

        private double loadAvg5;

        private double loadAvg15;

        public EventLoopLoadTracker(IntSupplier currentTasksFunction) {
            this.currentTasksFunction = currentTasksFunction;
        }

        private void updateLoad() {
            try {
                int tasks = currentTasksFunction.get();
                loadAvg1 = loadAvg1 * EXP_1 + (double) tasks * (1 - EXP_1);
                loadAvg5 = loadAvg5 * EXP_5 + (double) tasks * (1 - EXP_5);
                loadAvg15 = loadAvg15 * EXP_15 + (double) tasks * (1 - EXP_15);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            updateLoad();
        }

        public double getLoadAvg1() {
            return loadAvg1;
        }

        public double getLoadAvg5() {
            return loadAvg5;
        }

        public double getLoadAvg15() {
            return loadAvg15;
        }
    }
}
