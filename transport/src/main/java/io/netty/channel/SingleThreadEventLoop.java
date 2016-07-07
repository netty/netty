/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.util.LongBiConsumer;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Abstract base class for {@link EventLoop}s that execute all its submitted tasks in a single thread.
 *
 */
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected static final int DEFAULT_MAX_PENDING_TASKS = Math.max(16,
            SystemPropertyUtil.getInt("io.netty.eventLoop.maxPendingTasks", Integer.MAX_VALUE));

    private final Queue<Runnable> tailTasks;
    private final LongBiConsumer runTimeoutUpdater = new LongBiConsumer() {
        @Override
        public void accept(long tailTasksStartTime, long tailTasksEndTime) {
            runTimeout -= tailTasksEndTime - tailTasksStartTime;
        }
    };
    private boolean lastRunTimedOut;
    private long runTimeout;

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
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (promise == null) {
            throw new NullPointerException("promise");
        }

        channel.unsafe().register(this, promise);
        return promise;
    }

    /**
     * Adds a task to be run once at the end of next (or current) {@code eventloop} iteration.
     *
     * @param task to be added.
     */
    final void executeAfterEventLoopIteration(Runnable task) {
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
    protected boolean wakesUpForTask(Runnable task) {
        return !(task instanceof NonWakeupRunnable);
    }

    @Override
    protected boolean runAllTasks(long timeoutNanos) {
        assert inEventLoop();
        runTimeout = timeoutNanos;
        if (lastRunTimedOut) {
            //If the last run timed out, then run tail tasks first, otherwise, tail tasks may be starved.
            lastRunTimedOut = false;
            if (runAllTasksFrom(tailTasks, timeoutNanos, runTimeoutUpdater) == DeadlineRunResult.TIMED_OUT
                || runTimeout <= 0) {
                /**
                 * Although, it didn't run any actual tasks, returning false, will indicate that there are no tasks
                 * available, which we are not sure of.
                 * Note: If every run of tail tasks and normal tasks time out, then this code will alternate, between
                 * running tail tasks first and at end, which is fair. This is the reason, lastRunTimedOut is not set
                 * to true here.
                 */
                return true;
            }
        }

        return super.runAllTasks(runTimeout);
    }

    @Override
    protected void afterRunningAllTasks(boolean timedOut) {
        lastRunTimedOut = timedOut;
        if (timedOut) {
            // Do not run tail tasks on time out. Next run of tasks will run the tail tasks first.
            return;
        }

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
     * Marker interface for {@link Runnable} that will not trigger an {@link #wakeup(boolean)} in all cases.
     */
    interface NonWakeupRunnable extends Runnable { }
}
