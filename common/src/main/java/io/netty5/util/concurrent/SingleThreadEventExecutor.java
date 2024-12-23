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
package io.netty5.util.concurrent;

import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.ThreadExecutorMap;
import org.jetbrains.annotations.Async.Execute;
import org.jetbrains.annotations.Async.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.Objects.requireNonNull;

/**
 * {@link OrderedEventExecutor}'s implementation that execute all its submitted tasks in a single thread.
 *
 */
public class SingleThreadEventExecutor extends AbstractScheduledEventExecutor implements OrderedEventExecutor {

    protected static final int DEFAULT_MAX_PENDING_EXECUTOR_TASKS = Math.max(16,
            SystemPropertyUtil.getInt("io.netty5.eventexecutor.maxPendingTasks", Integer.MAX_VALUE));

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventExecutor.class);

    private static final int ST_NOT_STARTED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_SHUTTING_DOWN = 3;
    private static final int ST_SHUTDOWN = 4;
    private static final int ST_TERMINATED = 5;

    private static final Runnable WAKEUP_TASK = () -> {
        // Do nothing.
    };
    private static final Runnable NOOP_TASK = () -> {
        // Do nothing.
    };

    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");
    private static final AtomicReferenceFieldUpdater<SingleThreadEventExecutor, ThreadProperties> PROPERTIES_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(
                    SingleThreadEventExecutor.class, ThreadProperties.class, "threadProperties");

    private final Queue<Runnable> taskQueue;

    private volatile Thread thread;
    @SuppressWarnings("unused")
    private volatile ThreadProperties threadProperties;
    private final Executor executor;
    private volatile boolean interrupted;

    private final CountDownLatch threadLock = new CountDownLatch(1);
    private final Set<Runnable> shutdownHooks = new LinkedHashSet<>();
    private final boolean addTaskWakesUp;
    private final RejectedExecutionHandler rejectedExecutionHandler;

    private long lastExecutionTime;

    @SuppressWarnings({ "FieldMayBeFinal", "unused" })
    private volatile int state = ST_NOT_STARTED;

    private volatile long gracefulShutdownQuietPeriod;
    private volatile long gracefulShutdownTimeout;
    private long gracefulShutdownStartTime;

    private final Promise<Void> terminationFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);

    /**
     * Create a new instance
     */
    public SingleThreadEventExecutor() {
        this(new DefaultThreadFactory(SingleThreadEventExecutor.class));
    }

    /**
     * Create a new instance
     *
     * @param threadFactory     the {@link ThreadFactory} which will be used for the used {@link Thread}
     */
    public SingleThreadEventExecutor(ThreadFactory threadFactory) {
        this(new ThreadPerTaskExecutor(threadFactory));
    }

    /**
     * Create a new instance
     *
     * @param threadFactory     the {@link ThreadFactory} which will be used for the used {@link Thread}
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    public SingleThreadEventExecutor(ThreadFactory threadFactory,
            int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this(new ThreadPerTaskExecutor(threadFactory), maxPendingTasks, rejectedHandler);
    }

    /**
     * Create a new instance
     *
     * @param executor          the {@link Executor} which will be used for executing
     */
    public SingleThreadEventExecutor(Executor executor) {
        this(executor, DEFAULT_MAX_PENDING_EXECUTOR_TASKS, RejectedExecutionHandlers.reject());
    }

    /**
     * Create a new instance
     *
     * @param executor          the {@link Executor} which will be used for executing
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    public SingleThreadEventExecutor(Executor executor, int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this.executor = ThreadExecutorMap.apply(executor, this);
        taskQueue = newTaskQueue(Math.max(16, maxPendingTasks));
        addTaskWakesUp = taskQueue instanceof BlockingQueue;
        rejectedExecutionHandler = requireNonNull(rejectedHandler, "rejectedHandler");
    }

    /**
     * Create a new {@link Queue} which will holds the tasks to execute. This default implementation will return a
     * {@link LinkedBlockingQueue} but if your sub-class of {@link SingleThreadEventExecutor} will not do any blocking
     * calls on the this {@link Queue} it may make sense to {@code @Override} this and return some more performant
     * implementation that does not support blocking operations at all.
     *
     * Be aware that the implementation of {@link #run()} depends on a {@link BlockingQueue} so you will need to
     * override {@link #run()} as well if you return a non {@link BlockingQueue} from this method.
     *
     * As this method is called from within the constructor you can only use the parameters passed into the method when
     * overriding this method.
     */
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<>(maxPendingTasks);
    }

    /**
     * Interrupt the current running {@link Thread}.
     */
    protected final void interruptThread() {
        Thread currentThread = thread;
        if (currentThread == null) {
            interrupted = true;
        } else {
            currentThread.interrupt();
        }
    }

    /**
     * @see Queue#poll()
     *
     * This method must be called from the {@link EventExecutor} thread.
     */
    protected final Runnable pollTask() {
        assert inEventLoop();

        for (;;) {
            Runnable task = taskQueue.poll();
            if (task == WAKEUP_TASK) {
                continue;
            }
            return task;
        }
    }

    /**
     * Take the next {@link Runnable} from the task queue and so will block if no task is currently present.
     * <p>
     * Be aware that this method will throw an {@link UnsupportedOperationException} if the task queue, which was
     * created via {@link #newTaskQueue(int)}, does not implement {@link BlockingQueue}.
     * </p>
     *
     * This method must be called from the {@link EventExecutor} thread.
     *
     * @return {@code null} if the executor thread has been interrupted or waken up.
     */
    protected final Runnable takeTask() {
        assert inEventLoop();
        if (!(taskQueue instanceof BlockingQueue)) {
            throw new UnsupportedOperationException();
        }

        BlockingQueue<Runnable> taskQueue = (BlockingQueue<Runnable>) this.taskQueue;
        for (;;) {
            RunnableScheduledFuture<?> scheduledTask = peekScheduledTask();
            if (scheduledTask == null) {
                Runnable task = null;
                try {
                    task = taskQueue.take();
                    if (task == WAKEUP_TASK) {
                        task = null;
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
                return task;
            } else {
                long delayNanos = scheduledTask.delayNanos();
                Runnable task = null;
                if (delayNanos > 0) {
                    try {
                        task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        // Waken up.
                        return null;
                    }
                }
                if (task == null) {
                    // We need to fetch the scheduled tasks now as otherwise there may be a chance that
                    // scheduled tasks are never executed if there is always one task in the taskQueue.
                    // This is for example true for the read task of OIO Transport
                    // See https://github.com/netty/netty/issues/1614
                    fetchFromTaskScheduler();
                    task = taskQueue.poll();
                }

                if (task != null) {
                    return task;
                }
            }
        }
    }

    private boolean fetchFromTaskScheduler() {
        long nanoTime = ticker().nanoTime();
        RunnableScheduledFuture<?> scheduledTask  = pollScheduledTask(nanoTime);
        while (scheduledTask != null) {
            if (!taskQueue.offer(scheduledTask)) {
                // No space left in the task queue add it back to the task scheduler so we pick it up again.
                schedule(scheduledTask);
                return false;
            }
            scheduledTask  = pollScheduledTask(nanoTime);
        }
        return true;
    }

    /**
     * @see Queue#isEmpty()
     */
    protected final boolean hasTasks() {
        return !taskQueue.isEmpty();
    }

    /**
     * Return the number of tasks that are pending for processing (excluding the scheduled tasks).
     */
    public final int pendingTasks() {
        return taskQueue.size();
    }

    /**
     * Add a task to the task queue, or throws a {@link RejectedExecutionException} if this instance was shutdown
     * before.
     */
    private void addTask(Runnable task) {
        if (!offerTask(task)) {
            rejectedExecutionHandler.rejected(task, this);
        }
    }

    /**
     * @see Queue#offer(Object)
     */
    protected final boolean offerTask(Runnable task) {
        requireNonNull(task, "task");
        if (isShutdown()) {
            reject();
        }
        return taskQueue.offer(task);
    }

    /**
     * @see Queue#remove(Object)
     */
    protected final boolean removeTask(Runnable task) {
        return taskQueue.remove(task);
    }

    /**
     * Poll all tasks from the task queue and run them via {@link Runnable#run()} method.
     *
     * This method must be called from the {@link EventExecutor} thread.
     *
     * @return {@code true} if and only if at least one task was run
     */
    private boolean runAllTasks() {
        boolean fetchedAll;
        do {
            fetchedAll = fetchFromTaskScheduler();
            Runnable task = pollTask();
            if (task == null) {
                return false;
            }

            do {
                try {
                    runTask(task);
                } catch (Throwable t) {
                    logger.warn("A task raised an exception.", t);
                }
            } while ((task = pollTask()) != null);
        } while (!fetchedAll); // keep on processing until we fetched all scheduled tasks.

        updateLastExecutionTime();
        return true;
    }

    private void runTask(@Execute Runnable task) {
        task.run();
    }

    /**
     * Poll all tasks from the task queue and run them via {@link Runnable#run()} method.
     *
     * This method must be called from the {@link EventExecutor} thread.
     *
     * @return the number of processed tasks.
     */
    protected int runAllTasks(int maxTasks) {
        assert inEventLoop();
        boolean fetchedAll;
        int processedTasks = 0;
        do {
            fetchedAll = fetchFromTaskScheduler();
            for (; processedTasks < maxTasks; processedTasks++) {
                Runnable task = pollTask();
                if (task == null) {
                    break;
                }

                try {
                    runTask(task);
                } catch (Throwable t) {
                    logger.warn("A task raised an exception.", t);
                }
            }
        } while (!fetchedAll && processedTasks < maxTasks); // keep on processing until we fetched all scheduled tasks.

        if (processedTasks > 0) {
            // Only call if we at least executed one task.
            updateLastExecutionTime();
        }
        return processedTasks;
    }

    /**
     * Returns the amount of time left until the scheduled task with the closest dead line is executed.
     *
     * This method must be called from the {@link EventExecutor} thread.
     */
    protected final long delayNanos(long currentTimeNanos) {
        assert inEventLoop();
        currentTimeNanos -= ticker().initialNanoTime();
        RunnableScheduledFuture<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return SCHEDULE_PURGE_INTERVAL;
        }

        return scheduledTask.delayNanos(currentTimeNanos);
    }

    /**
     * Returns the absolute point in time (relative to {@link #ticker().nanoTime()} ()}) at which the next
     * closest scheduled task should run or {@code -1} if none is scheduled at the mment.
     *
     * This method must be called from the {@link EventExecutor} thread.
     */
    protected final long deadlineNanos() {
        assert inEventLoop();
        RunnableScheduledFuture<?> scheduledTask = peekScheduledTask();
        return scheduledTask == null ? -1 : scheduledTask.deadlineNanos();
    }

    /**
     * Updates the internal timestamp that tells when a submitted task was executed most recently.
     * {@link #runAllTasks(int)} updates this timestamp automatically, and thus there's usually no need to call this
     * method.  However, if you take the tasks manually using {@link #takeTask()} or {@link #pollTask()}, you have to
     * call this method at the end of task execution loop if you execute a task for accurate quiet period checks.
     *
     * This method must be called from the {@link EventExecutor} thread.
     */
    protected final void updateLastExecutionTime() {
        assert inEventLoop();
        lastExecutionTime = ticker().nanoTime();
    }

    /**
     * Run tasks that are submitted to this {@link SingleThreadEventExecutor}.
     * The implementation depends on the fact that {@link #newTaskQueue(int)} returns a
     * {@link BlockingQueue}. If you change this by overriding {@link #newTaskQueue(int)}
     * be aware that you also need to override {@link #run()}.
     *
     * This method must be called from the {@link EventExecutor} thread.
     */
    protected void run() {
        assert inEventLoop();
        do {
            Runnable task = takeTask();
            if (task != null) {
                runTask(task);
                updateLastExecutionTime();
            }
        } while (!confirmShutdown());
    }

    /**
     * Do nothing, sub-classes may override.
     */
    protected void cleanup() {
        // NOOP
        assert inEventLoop();
    }

    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop) {
            // Use offer as we actually only need this to unblock the thread and if offer fails we do not care as there
            // is already something in the queue.
            taskQueue.offer(WAKEUP_TASK);
        }
    }

    @Override
    public final boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    /**
     * Add a {@link Runnable} which will be executed on shutdown of this instance
     */
    public final void addShutdownHook(final Runnable task) {
        if (inEventLoop()) {
            shutdownHooks.add(task);
        } else {
            execute(() -> shutdownHooks.add(task));
        }
    }

    /**
     * Remove a previous added {@link Runnable} as a shutdown hook
     */
    public final void removeShutdownHook(final Runnable task) {
        if (inEventLoop()) {
            shutdownHooks.remove(task);
        } else {
            execute(() -> shutdownHooks.remove(task));
        }
    }

    private boolean runShutdownHooks() {
        boolean ran = false;
        // Note shutdown hooks can add / remove shutdown hooks.
        while (!shutdownHooks.isEmpty()) {
            List<Runnable> copy = new ArrayList<>(shutdownHooks);
            shutdownHooks.clear();
            for (Runnable task: copy) {
                try {
                    runTask(task);
                } catch (Throwable t) {
                    logger.warn("Shutdown hook raised an exception.", t);
                } finally {
                    ran = true;
                }
            }
        }

        if (ran) {
            updateLastExecutionTime();
        }

        return ran;
    }

    @Override
    public final Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (quietPeriod < 0) {
            throw new IllegalArgumentException("quietPeriod: " + quietPeriod + " (expected >= 0)");
        }
        if (timeout < quietPeriod) {
            throw new IllegalArgumentException(
                    "timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        }
        requireNonNull(unit, "unit");

        if (isShuttingDown()) {
            return terminationFuture();
        }

        boolean inEventLoop = inEventLoop();
        boolean wakeup;
        int oldState;
        for (;;) {
            if (isShuttingDown()) {
                return terminationFuture();
            }
            int newState;
            wakeup = true;
            oldState = state;
            if (inEventLoop) {
                newState = ST_SHUTTING_DOWN;
            } else {
                switch (oldState) {
                    case ST_NOT_STARTED:
                    case ST_STARTED:
                        newState = ST_SHUTTING_DOWN;
                        break;
                    default:
                        newState = oldState;
                        wakeup = false;
                }
            }
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                break;
            }
        }
        gracefulShutdownQuietPeriod = unit.toNanos(quietPeriod);
        gracefulShutdownTimeout = unit.toNanos(timeout);

        if (ensureThreadStarted(oldState)) {
            return terminationFuture.asFuture();
        }

        if (wakeup) {
            taskQueue.offer(WAKEUP_TASK);
            if (!addTaskWakesUp) {
                wakeup(inEventLoop);
            }
        }

        return terminationFuture();
    }

    @Override
    public final Future<Void> terminationFuture() {
        return terminationFuture.asFuture();
    }

    @Override
    public final boolean isShuttingDown() {
        return state >= ST_SHUTTING_DOWN;
    }

    @Override
    public final boolean isShutdown() {
        return state >= ST_SHUTDOWN;
    }

    @Override
    public final boolean isTerminated() {
        return state == ST_TERMINATED;
    }

    /**
     * Confirm that the shutdown if the instance should be done now!
     *
     * This method must be called from the {@link EventExecutor} thread.
     */
    protected final boolean confirmShutdown() {
        return confirmShutdown0();
    }

    boolean confirmShutdown0() {
        assert inEventLoop();

        if (!isShuttingDown()) {
            return false;
        }

        cancelScheduledTasks();

        if (gracefulShutdownStartTime == 0) {
            gracefulShutdownStartTime = ticker().nanoTime();
        }

        if (runAllTasks() || runShutdownHooks()) {
            if (isShutdown()) {
                // Executor shut down - no new tasks anymore.
                return true;
            }

            // There were tasks in the queue. Wait a little bit more until no tasks are queued for the quiet period or
            // terminate if the quiet period is 0.
            // See https://github.com/netty/netty/issues/4241
            if (gracefulShutdownQuietPeriod == 0) {
                return true;
            }
            taskQueue.offer(WAKEUP_TASK);
            return false;
        }

        final long nanoTime = ticker().nanoTime();

        if (isShutdown() || nanoTime - gracefulShutdownStartTime > gracefulShutdownTimeout) {
            return true;
        }

        if (nanoTime - lastExecutionTime <= gracefulShutdownQuietPeriod) {
            // Check if any tasks were added to the queue every 100ms.
            // TODO: Change the behavior of takeTask() so that it returns on timeout.
            taskQueue.offer(WAKEUP_TASK);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            return false;
        }

        // No tasks were added for last quiet period - hopefully safe to shut down.
        // (Hopefully because we really cannot make a guarantee that there will be no execute() calls by a user.)
        return true;
    }

    @Override
    public final boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(unit, "unit");

        if (inEventLoop()) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }

        threadLock.await(timeout, unit);

        return isTerminated();
    }

    @Override
    public void execute(@Schedule Runnable task) {
        requireNonNull(task, "task");

        boolean inEventLoop = inEventLoop();
        addTask(task);
        if (!inEventLoop) {
            startThread();
            if (isShutdown()) {
                boolean reject = false;
                try {
                    if (removeTask(task)) {
                        reject = true;
                    }
                } catch (UnsupportedOperationException e) {
                    // The task queue does not support removal so the best thing we can do is to just move on and
                    // hope we will be able to pick-up the task before its completely terminated.
                    // In worst case we will log on termination.
                }
                if (reject) {
                    reject();
                }
            }
        }

        if (!addTaskWakesUp && wakesUpForTask(task)) {
            wakeup(inEventLoop);
        }
    }

    /**
     * Returns the {@link ThreadProperties} of the {@link Thread} that powers the {@link SingleThreadEventExecutor}.
     * If the {@link SingleThreadEventExecutor} is not started yet, this operation will start it and block until
     * it is fully started.
     *
     * @throws InterruptedException if this thread is interrupted while waiting for the thread to start.
     */
    public final ThreadProperties threadProperties() throws InterruptedException {
        ThreadProperties threadProperties = this.threadProperties;
        if (threadProperties == null) {
            Thread thread = this.thread;
            if (thread == null) {
                assert !inEventLoop();
                submit(NOOP_TASK).asStage().sync();
                thread = this.thread;
                assert thread != null;
            }

            threadProperties = new DefaultThreadProperties(thread);
            if (!PROPERTIES_UPDATER.compareAndSet(this, null, threadProperties)) {
                threadProperties = this.threadProperties;
            }
        }

        return threadProperties;
    }

    /**
     * Returns {@code true} if {@link #wakeup(boolean)} should be called for this {@link Runnable}, {@code false}
     * otherwise.
     */
    protected boolean wakesUpForTask(@SuppressWarnings("unused") Runnable task) {
        return true;
    }

    protected static void reject() {
        throw new RejectedExecutionException("event executor terminated");
    }

    // ScheduledExecutorService implementation

    private static final long SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    private void startThread() {
        if (state == ST_NOT_STARTED) {
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    doStartThread();
                    success = true;
                } finally {
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        }
    }

    private boolean ensureThreadStarted(int oldState) {
        if (oldState == ST_NOT_STARTED) {
            try {
                doStartThread();
            } catch (Throwable cause) {
                STATE_UPDATER.set(this, ST_TERMINATED);
                terminationFuture.tryFailure(cause);

                if (cause instanceof Error) {
                    // Rethrow errors so they can't be ignored.
                    throw cause;
                }
                return true;
            }
        }
        return false;
    }

    private void doStartThread() {
        assert thread == null;
        executor.execute(() -> {
            thread = Thread.currentThread();
            if (interrupted) {
                thread.interrupt();
            }

            boolean success = false;
            updateLastExecutionTime();
            try {
                run();
                success = true;
            } catch (Throwable t) {
                logger.warn("Unexpected exception from an event executor: ", t);
            } finally {
                for (;;) {
                    int oldState = state;
                    if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                            this, oldState, ST_SHUTTING_DOWN)) {
                        break;
                    }
                }

                // Check if confirmShutdown() was called at the end of the loop.
                if (success && gracefulShutdownStartTime == 0) {
                    if (logger.isErrorEnabled()) {
                        logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " +
                                SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must " +
                                "be called before run() implementation terminates.");
                    }
                }

                try {
                    // Run all remaining tasks and shutdown hooks. At this point the event loop
                    // is in ST_SHUTTING_DOWN state still accepting tasks which is needed for
                    // graceful shutdown with quietPeriod.
                    for (;;) {
                        if (confirmShutdown()) {
                            break;
                        }
                    }

                    // Now we want to make sure no more tasks can be added from this point. This is
                    // achieved by switching the state. Any new tasks beyond this point will be rejected.
                    for (;;) {
                        int oldState = state;
                        if (oldState >= ST_SHUTDOWN || STATE_UPDATER.compareAndSet(
                                this, oldState, ST_SHUTDOWN)) {
                            break;
                        }
                    }

                    // We have the final set of tasks in the queue now, no more can be added, run all remaining.
                    // No need to loop here, this is the final pass.
                    confirmShutdown();
                } finally {
                    try {
                        cleanup();
                    } finally {
                        // Lets remove all FastThreadLocals for the Thread as we are about to terminate and notify
                        // the future. The user may block on the future and once it unblocks the JVM may terminate
                        // and start unloading classes.
                        // See https://github.com/netty/netty/issues/6596.
                        FastThreadLocal.removeAll();

                        STATE_UPDATER.set(this, ST_TERMINATED);
                        threadLock.countDown();
                        int numUserTasks = drainTasks();
                        if (numUserTasks > 0 && logger.isWarnEnabled()) {
                            logger.warn("An event executor terminated with " +
                                    "non-empty task queue (" + numUserTasks + ')');
                        }
                        terminationFuture.setSuccess(null);
                    }
                }
            }
        });
    }

    final int drainTasks() {
        int numTasks = 0;
        for (;;) {
            Runnable runnable = taskQueue.poll();
            if (runnable == null) {
                break;
            }
            // WAKEUP_TASK should be just discarded as these are added internally.
            // The important bit is that we not have any user tasks left.
            if (WAKEUP_TASK != runnable) {
                numTasks++;
            }
        }
        return numTasks;
    }

    private static final class DefaultThreadProperties implements ThreadProperties {
        private final Thread t;

        DefaultThreadProperties(Thread t) {
            this.t = t;
        }

        @Override
        public State state() {
            return t.getState();
        }

        @Override
        public int priority() {
            return t.getPriority();
        }

        @Override
        public boolean isInterrupted() {
            return t.isInterrupted();
        }

        @Override
        public boolean isDaemon() {
            return t.isDaemon();
        }

        @Override
        public String name() {
            return t.getName();
        }

        @Override
        public long id() {
            return t.getId();
        }

        @Override
        public StackTraceElement[] stackTrace() {
            return t.getStackTrace();
        }

        @Override
        public boolean isAlive() {
            return t.isAlive();
        }
    }
}
