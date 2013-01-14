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

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link EventExecutor}'s that execute all its submitted tasks in a single thread.
 *
 */
public abstract class SingleThreadEventExecutor extends AbstractExecutorService implements EventExecutor {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SingleThreadEventExecutor.class);

    /**
     * Wait at least 2 seconds after shutdown() until there are no pending tasks anymore.
     * @see #confirmShutdown()
     */
    private static final long SHUTDOWN_DELAY_NANOS = TimeUnit.SECONDS.toNanos(2);

    static final ThreadLocal<SingleThreadEventExecutor> CURRENT_EVENT_LOOP =
            new ThreadLocal<SingleThreadEventExecutor>();

    private static final int ST_NOT_STARTED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_SHUTDOWN = 3;
    private static final int ST_TERMINATED = 4;

    private static final Runnable WAKEUP_TASK = new Runnable() {
        @Override
        public void run() {
            // Do nothing.
        }
    };

    /**
     * Return the {@link SingleThreadEventExecutor} which belongs the current {@link Thread}.
     */
    public static SingleThreadEventExecutor currentEventLoop() {
        return CURRENT_EVENT_LOOP.get();
    }

    private final EventExecutorGroup parent;
    private final Queue<Runnable> taskQueue;
    private final Thread thread;
    private final Object stateLock = new Object();
    private final Semaphore threadLock = new Semaphore(0);
    private final ChannelTaskScheduler scheduler;
    private final Set<Runnable> shutdownHooks = new LinkedHashSet<Runnable>();
    private volatile int state = ST_NOT_STARTED;
    private long lastAccessTimeNanos;

    /**
     * Create a new instance
     *
     * @param parent            the {@link EventExecutorGroup} which is the parent of this instance and belongs to it
     * @param threadFactory     the {@link ThreadFactory} which will be used for the used {@link Thread}
     * @param scheduler         the {@link ChannelTaskScheduler} which will be used to schedule Tasks for later
     *                          execution
     */
    protected SingleThreadEventExecutor(
            EventExecutorGroup parent, ThreadFactory threadFactory, ChannelTaskScheduler scheduler) {
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }
        if (scheduler == null) {
            throw new NullPointerException("scheduler");
        }

        this.parent = parent;
        this.scheduler = scheduler;

        thread = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                CURRENT_EVENT_LOOP.set(SingleThreadEventExecutor.this);
                boolean success = false;
                try {
                    SingleThreadEventExecutor.this.run();
                    success = true;
                } catch (Throwable t) {
                    logger.warn("Unexpected exception from an event executor: ", t);
                    shutdown();
                } finally {
                    // Check if confirmShutdown() was called at the end of the loop.
                    if (success && lastAccessTimeNanos == 0) {
                        logger.error(
                                "Buggy " + EventExecutor.class.getSimpleName() + " implementation; " +
                                SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must be called " +
                                "before run() implementation terminates.");
                    }

                    try {
                        // Run all remaining tasks and shutdown hooks.
                        for (;;) {
                            if (confirmShutdown()) {
                                break;
                            }
                        }
                        synchronized (stateLock) {
                            state = ST_TERMINATED;
                        }
                    } finally {
                        try {
                            cleanup();
                        } finally {
                            threadLock.release();
                            if (!taskQueue.isEmpty()) {
                                logger.warn(
                                        "An event executor terminated with " +
                                        "non-empty task queue (" + taskQueue.size() + ')');
                            }
                        }
                    }
                }
            }
        });

        taskQueue = newTaskQueue();
    }

    /**
     * Create a new {@link Queue} which will holds the tasks to execute. This default implementation will return a
     * {@link LinkedBlockingQueue} but if your sub-class of {@link SingleThreadEventExecutor} will not do any blocking
     * calls on the this {@link Queue} it may make sense to {@code @Override} this and return some more performant
     * implementation that does not support blocking operations at all.
     */
    protected Queue<Runnable> newTaskQueue() {
        return new LinkedBlockingQueue<Runnable>();
    }

    @Override
    public EventExecutorGroup parent() {
        return parent;
    }

    @Override
    public EventExecutor next() {
        return this;
    }

    /**
     * Interrupt the current running {@link Thread}.
     */
    protected void interruptThread() {
        thread.interrupt();
    }

    /**
     * @see {@link Queue#poll()}
     */
    protected Runnable pollTask() {
        assert inEventLoop();
        return taskQueue.poll();
    }

    /**
     * Take the next {@link Runnable} from the task queue and so will block if no task is currently present.
     *
     * Be aware that this method will throw an {@link UnsupportedOperationException} if the task queue, which was
     * created via {@link #newTaskQueue()}, does not implement {@link BlockingQueue}.
     */
    protected Runnable takeTask() throws InterruptedException {
        assert inEventLoop();
        if (taskQueue instanceof BlockingQueue) {
            return ((BlockingQueue<Runnable>) taskQueue).take();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * @see {@link Queue#peek()}
     */
    protected Runnable peekTask() {
        assert inEventLoop();
        return taskQueue.peek();
    }

    /**
     * @see {@link Queue#isEmpty()}
     */
    protected boolean hasTasks() {
        assert inEventLoop();
        return !taskQueue.isEmpty();
    }

    /**
     * Add a task to the task queue, or throws a {@link RejectedExecutionException} if this instance was shutdown
     * before.
     */
    protected void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (isTerminated()) {
            reject();
        }
        taskQueue.add(task);
    }

    /**
     * @see {@link Queue#remove(Object)}
     */
    protected boolean removeTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        return taskQueue.remove(task);
    }

    /**
     * Poll all tasks from the task queue and run them via {@link Runnable#run()} method.
     */
    protected boolean runAllTasks() {
        boolean ran = false;
        for (;;) {
            final Runnable task = pollTask();
            if (task == null) {
                break;
            }

            if (task == WAKEUP_TASK) {
                continue;
            }

            try {
                task.run();
                ran = true;
            } catch (Throwable t) {
                logger.warn("A task raised an exception.", t);
            }
        }
        return ran;
    }

    /**
     *
     */
    protected abstract void run();

    /**
     * Do nothing, sub-classes may override
     */
    protected void cleanup() {
        // NOOP
    }

    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop || state == ST_SHUTDOWN) {
            addTask(WAKEUP_TASK);
        }
    }

    @Override
    public boolean inEventLoop() {
        return inEventLoop(Thread.currentThread());
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    /**
     * Add a {@link Runnable} which will be executed on shutdown of this instance
     */
    public void addShutdownHook(final Runnable task) {
        if (inEventLoop()) {
            shutdownHooks.add(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.add(task);
                }
            });
        }
    }

    /**
     * Remove a previous added {@link Runnable} as a shutdown hook
     */
    public void removeShutdownHook(final Runnable task) {
        if (inEventLoop()) {
            shutdownHooks.remove(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.remove(task);
                }
            });
        }
    }

    private boolean runShutdownHooks() {
        boolean ran = false;
        // Note shutdown hooks can add / remove shutdown hooks.
        while (!shutdownHooks.isEmpty()) {
            List<Runnable> copy = new ArrayList<Runnable>(shutdownHooks);
            shutdownHooks.clear();
            for (Runnable task: copy) {
                try {
                    task.run();
                    ran = true;
                } catch (Throwable t) {
                    logger.warn("Shutdown hook raised an exception.", t);
                }
            }
        }
        return ran;
    }

    @Override
    public void shutdown() {
        if (isShutdown()) {
            return;
        }

        boolean inEventLoop = inEventLoop();
        boolean wakeup = true;

        if (inEventLoop) {
            synchronized (stateLock) {
                assert state == ST_STARTED;
                state = ST_SHUTDOWN;
            }
        } else {
            synchronized (stateLock) {
                switch (state) {
                case ST_NOT_STARTED:
                    state = ST_SHUTDOWN;
                    thread.start();
                    break;
                case ST_STARTED:
                    state = ST_SHUTDOWN;
                    break;
                default:
                    wakeup = false;
                }
            }
        }

        if (wakeup) {
            wakeup(inEventLoop);
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return state >= ST_SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state == ST_TERMINATED;
    }

    /**
     * Confirm that the shutdown if the instance should be done now!
     */
    protected boolean confirmShutdown() {
        if (!isShutdown()) {
            throw new IllegalStateException("must be invoked after shutdown()");
        }
        if (!inEventLoop()) {
            throw new IllegalStateException("must be invoked from an event loop");
        }

        if (runAllTasks() || runShutdownHooks()) {
            // There were tasks in the queue. Wait a little bit more until no tasks are queued for SHUTDOWN_DELAY_NANOS.
            lastAccessTimeNanos = 0;
            wakeup(true);
            return false;
        }

        if (lastAccessTimeNanos == 0 || System.nanoTime() - lastAccessTimeNanos < SHUTDOWN_DELAY_NANOS) {
            if (lastAccessTimeNanos == 0) {
                lastAccessTimeNanos = System.nanoTime();
            }

            // Check if any tasks were added to the queue every 100ms.
            // TODO: Change the behavior of takeTask() so that it returns on timeout.
            wakeup(true);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            return false;
        }

        // No tasks were added for last SHUTDOWN_DELAY_NANOS - hopefully safe to shut down.
        // (Hopefully because we really cannot make a guarantee that there will be no execute() calls by a user.)
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        if (inEventLoop()) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }

        if (threadLock.tryAcquire(timeout, unit)) {
            threadLock.release();
        }

        return isTerminated();
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }

        if (inEventLoop()) {
            addTask(task);
            wakeup(true);
        } else {
            synchronized (stateLock) {
                if (state == ST_NOT_STARTED) {
                    state = ST_STARTED;
                    thread.start();
                }
            }
            addTask(task);
            if (isTerminated() && removeTask(task)) {
                reject();
            }
            wakeup(false);
        }
    }

    private static void reject() {
        throw new RejectedExecutionException("event executor terminated");
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduler.schedule(this, command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return scheduler.schedule(this, callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(this, command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(this, command, initialDelay, delay, unit);
    }
}
