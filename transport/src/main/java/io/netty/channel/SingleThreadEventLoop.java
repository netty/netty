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

import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link EventLoop} that execute all its submitted tasks in a single thread and uses an {@link IoHandler} for
 * IO processing.
 */
public class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected static final int DEFAULT_MAX_PENDING_TASKS = Math.max(16,
            SystemPropertyUtil.getInt("io.netty.eventLoop.maxPendingTasks", Integer.MAX_VALUE));

    // TODO: Is this a sensible default ?
    protected static final int DEFAULT_MAX_TASKS_PER_RUN = Math.max(1,
            SystemPropertyUtil.getInt("io.netty.eventLoop.maxTaskPerRun", 1024 * 4));

    private final IoExecutionContext context = new IoExecutionContext() {
        @Override
        public boolean canBlock() {
            assert inEventLoop();
            return !SingleThreadEventLoop.this.hasTasks() && !SingleThreadEventLoop.this.hasScheduledTasks();
        }

        @Override
        public long delayNanos(long currentTimeNanos) {
            assert inEventLoop();
            return SingleThreadEventLoop.this.delayNanos(currentTimeNanos);
        }

        @Override
        public long deadlineNanos() {
            assert inEventLoop();
            return SingleThreadEventLoop.this.deadlineNanos();
        }
    };

    private final Unsafe unsafe = new Unsafe() {
        @Override
        public void register(Channel channel) throws Exception {
            SingleThreadEventLoop.this.register(channel);
        }

        @Override
        public void deregister(Channel channel) throws Exception {
            SingleThreadEventLoop.this.deregister(channel);
        }
    };

    private final IoHandler ioHandler;
    private final int maxTasksPerRun;

    /**
     * Create a new instance
     *
     * @param threadFactory     the {@link ThreadFactory} which will be used for the used {@link Thread}
     * @param ioHandler         the {@link IoHandler} to use.
     */
    public SingleThreadEventLoop(ThreadFactory threadFactory, IoHandler ioHandler) {
        this(threadFactory, ioHandler, DEFAULT_MAX_PENDING_TASKS, RejectedExecutionHandlers.reject());
    }

    /**
     * Create a new instance
     *
     * @param executor          the {@link Executor} which will be used to run this {@link EventLoop}.
     * @param ioHandler         the {@link IoHandler} to use.
     */
    public SingleThreadEventLoop(Executor executor, IoHandler ioHandler) {
        this(executor, ioHandler, DEFAULT_MAX_PENDING_TASKS, RejectedExecutionHandlers.reject());
    }

    /**
     * Create a new instance
     *
     * @param threadFactory     the {@link ThreadFactory} which will be used for the used {@link Thread}
     * @param ioHandler         the {@link IoHandler} to use.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    public SingleThreadEventLoop(ThreadFactory threadFactory,
                                 IoHandler ioHandler, int maxPendingTasks,
                                 RejectedExecutionHandler rejectedHandler) {
        this(threadFactory, ioHandler, maxPendingTasks, rejectedHandler, DEFAULT_MAX_TASKS_PER_RUN);
    }

    /**
     * Create a new instance
     *
     * @param executor          the {@link Executor} which will be used to run this {@link EventLoop}.
     * @param ioHandler         the {@link IoHandler} to use.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    public SingleThreadEventLoop(Executor executor,
                                 IoHandler ioHandler, int maxPendingTasks,
                                 RejectedExecutionHandler rejectedHandler) {
        this(executor, ioHandler, maxPendingTasks, rejectedHandler, DEFAULT_MAX_TASKS_PER_RUN);
    }

    /**
     * Create a new instance
     *
     * @param threadFactory     the {@link ThreadFactory} which will be used for the used {@link Thread}
     * @param ioHandler         the {@link IoHandler} to use.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     * @param maxTasksPerRun    the maximum number of tasks per {@link EventLoop} run that will be processed
     *                          before trying to handle IO again.
     */
    public SingleThreadEventLoop(ThreadFactory threadFactory,
                                 IoHandler ioHandler, int maxPendingTasks,
                                 RejectedExecutionHandler rejectedHandler, int maxTasksPerRun) {
        super(threadFactory, maxPendingTasks, rejectedHandler);
        this.ioHandler = ObjectUtil.checkNotNull(ioHandler, "ioHandler");
        this.maxTasksPerRun = ObjectUtil.checkPositive(maxTasksPerRun, "maxTasksPerRun");
    }

    /**
     * Create a new instance
     *
     * @param executor          the {@link Executor} which will be used to run this {@link EventLoop}.
     * @param ioHandler         the {@link IoHandler} to use.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     * @param maxTasksPerRun    the maximum number of tasks per {@link EventLoop} run that will be processed
     *                          before trying to handle IO again.
     */
    public SingleThreadEventLoop(Executor executor,
                                 IoHandler ioHandler, int maxPendingTasks,
                                 RejectedExecutionHandler rejectedHandler, int maxTasksPerRun) {
        super(executor, maxPendingTasks, rejectedHandler);
        this.ioHandler = ObjectUtil.checkNotNull(ioHandler, "ioHandler");
        this.maxTasksPerRun = ObjectUtil.checkPositive(maxTasksPerRun, "maxTasksPerRun");
    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    @Override
    public final EventLoop next() {
        return this;
    }

    @Override
    protected final boolean wakesUpForTask(Runnable task) {
        return !(task instanceof NonWakeupRunnable);
    }

    /**
     * Marker interface for {@link Runnable} that will not trigger an {@link #wakeup(boolean)} in all cases.
     */
    interface NonWakeupRunnable extends Runnable { }

    @Override
    public final Unsafe unsafe() {
        return unsafe;
    }

    // Methods that a user can override to easily add instrumentation and other things.

    @Override
    protected void run() {
        assert inEventLoop();
        do {
            runIo();
            if (isShuttingDown()) {
                ioHandler.prepareToDestroy();
            }
            runAllTasks(maxTasksPerRun);
        } while (!confirmShutdown());
    }

    /**
     * Called when IO will be processed for all the {@link Channel}s on this {@link SingleThreadEventLoop}.
     * This method returns the number of {@link Channel}s for which IO was processed.
     *
     * This method must be called from the {@link EventLoop} thread.
     */
    protected int runIo() {
        assert inEventLoop();
        return ioHandler.run(context);
    }

    /**
     * Called once a {@link Channel} should be registered on this {@link SingleThreadEventLoop}.
     *
     * This method must be called from the {@link EventLoop} thread.
     */
    protected void register(Channel channel) throws Exception {
        assert inEventLoop();
        ioHandler.register(channel);
    }

    /**
     * Called once a {@link Channel} should be deregistered from this {@link SingleThreadEventLoop}.
     */
    protected void deregister(Channel channel) throws Exception {
        assert inEventLoop();
        ioHandler.deregister(channel);
    }

    @Override
    protected final void wakeup(boolean inEventLoop) {
        ioHandler.wakeup(inEventLoop);
    }

    @Override
    protected final void cleanup() {
        assert inEventLoop();
        ioHandler.destroy();
    }
}
