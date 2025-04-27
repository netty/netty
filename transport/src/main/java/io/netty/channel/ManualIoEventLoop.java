/*
 * Copyright 2025 The Netty Project
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

import io.netty.util.concurrent.AbstractScheduledEventExecutor;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.Ticker;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadExecutorMap;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link IoEventLoop} implementation that is owned by the user and so needs to be driven by the user manually with the
 * given {@link Thread}. This means that the user is responsible to call either {@link #runNow()} or {@link #run(long)}
 * to execute IO and tasks that were submitted to this {@link IoEventLoop}.
 * <p>
 * This is for <strong>advanced use-cases only</strong>, where the user wants to own the {@link Thread} that drives the
 * {@link IoEventLoop} to also do other work. Care must be taken that the {@link #runNow() or
 * {@link #waitAndRun()}} methods are called in a timely fashion.
 */
public final class ManualIoEventLoop extends AbstractScheduledEventExecutor implements IoEventLoop {
    private static final int ST_STARTED = 0;
    private static final int ST_SHUTTING_DOWN = 1;
    private static final int ST_SHUTDOWN = 2;
    private static final int ST_TERMINATED = 3;

    private final AtomicInteger state;
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);
    private final Queue<Runnable> taskQueue = PlatformDependent.newMpscQueue();
    private final IoHandlerContext nonBlockingContext = new IoHandlerContext() {
        @Override
        public boolean canBlock() {
            assert inEventLoop();
            return false;
        }

        @Override
        public long delayNanos(long currentTimeNanos) {
            assert inEventLoop();
            return 0;
        }

        @Override
        public long deadlineNanos() {
            assert inEventLoop();
            return -1;
        }
    };
    private final BlockingIoHandlerContext blockingContext = new BlockingIoHandlerContext();
    private final IoEventLoopGroup parent;
    private final AtomicReference<Thread> owningThread;
    private final IoHandler handler;
    private final Ticker ticker;

    private volatile long gracefulShutdownQuietPeriod;
    private volatile long gracefulShutdownTimeout;
    private long gracefulShutdownStartTime;
    private long lastExecutionTime;
    private boolean initialized;

    /**
     * Create a new {@link IoEventLoop} that is owned by the user and so needs to be driven by the user with the given
     * {@link Thread}. This means that the user is responsible to call either {@link #runNow()} or
     * {@link #run(long)} to execute IO or tasks that were submitted to this {@link IoEventLoop}.
     *
     * @param owningThread      the {@link Thread} that executes the IO and tasks for this {@link IoEventLoop}. The
     *                          user will use this {@link Thread} to call {@link #runNow()} or {@link #run(long)} to
     *                          make progress.
     * @param factory           the {@link IoHandlerFactory} that will be used to create the {@link IoHandler} that is
     *                          used by this {@link IoEventLoop}.
     */
    public ManualIoEventLoop(Thread owningThread, IoHandlerFactory factory) {
        this(null, owningThread, factory);
    }

    /**
     * Create a new {@link IoEventLoop} that is owned by the user and so needs to be driven by the user with the given
     * {@link Thread}. This means that the user is responsible to call either {@link #runNow()} or
     * {@link #run(long)} to execute IO or tasks that were submitted to this {@link IoEventLoop}.
     *
     * @param parent            the parent {@link IoEventLoopGroup} or {@code null} if no parent.
     * @param owningThread      the {@link Thread} that executes the IO and tasks for this {@link IoEventLoop}. The
     *                          user will use this {@link Thread} to call {@link #runNow()} or {@link #run(long)} to
     *                          make progress. If {@code null}, must be set later using
     *                          {@link #setOwningThread(Thread)}.
     * @param factory           the {@link IoHandlerFactory} that will be used to create the {@link IoHandler} that is
     *                          used by this {@link IoEventLoop}.
     */
    public ManualIoEventLoop(IoEventLoopGroup parent, Thread owningThread, IoHandlerFactory factory) {
        this(parent, owningThread, factory, Ticker.systemTicker());
    }

    /**
     * Create a new {@link IoEventLoop} that is owned by the user and so needs to be driven by the user with the given
     * {@link Thread}. This means that the user is responsible to call either {@link #runNow()} or
     * {@link #run(long)} to execute IO or tasks that were submitted to this {@link IoEventLoop}.
     *
     * @param parent            the parent {@link IoEventLoopGroup} or {@code null} if no parent.
     * @param owningThread      the {@link Thread} that executes the IO and tasks for this {@link IoEventLoop}. The
     *                          user will use this {@link Thread} to call {@link #runNow()} or {@link #run(long)} to
     *                          make progress. If {@code null}, must be set later using
     *                          {@link #setOwningThread(Thread)}.
     * @param factory           the {@link IoHandlerFactory} that will be used to create the {@link IoHandler} that is
     *                          used by this {@link IoEventLoop}.
     * @param ticker            The {@link #ticker()} to use for this event loop. Note that the {@link IoHandler} does
     *                          not use the ticker, so if the ticker advances faster than system time, you may have to
     *                          {@link #wakeup()} this event loop manually.
     */
    public ManualIoEventLoop(IoEventLoopGroup parent, Thread owningThread, IoHandlerFactory factory, Ticker ticker) {
        this.parent = parent;
        this.owningThread = new AtomicReference<>(owningThread);
        this.handler = factory.newHandler(this);
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        state = new AtomicInteger(ST_STARTED);
    }

    @Override
    public Ticker ticker() {
        return ticker;
    }

    private int runAllTasks() {
        assert inEventLoop();
        int numRun = 0;
        boolean fetchedAll;
        do {
            fetchedAll = fetchFromScheduledTaskQueue(taskQueue);
            for (;;) {
                Runnable task = taskQueue.poll();
                if (task == null) {
                    break;
                }
                safeExecute(task);
                numRun++;
            }
        } while (!fetchedAll); // keep on processing until we fetched all scheduled tasks.
        if (numRun > 0) {
            lastExecutionTime = ticker.nanoTime();
        }
        return numRun;
    }

    private int run(IoHandlerContext context) {
        if (!initialized) {
            if (owningThread.get() == null) {
                throw new IllegalStateException("Owning thread not set");
            }
            initialized = true;
            handler.initialize();
        }
        EventExecutor old = ThreadExecutorMap.setCurrentExecutor(this);
        try {
            if (isShuttingDown()) {
                if (terminationFuture.isDone()) {
                    // Already completely terminated
                    return 0;
                }
                // Run all tasks before prepare to destroy.
                int run = runAllTasks();
                handler.prepareToDestroy();
                if (confirmShutdown()) {
                    // Destroy the handler now and run all remaining tasks.
                    try {
                        handler.destroy();
                        for (;;) {
                            int r = runAllTasks();
                            run += r;
                            if (r == 0) {
                                break;
                            }
                        }
                    } finally {
                        state.set(ST_TERMINATED);
                        terminationFuture.setSuccess(null);
                    }
                }
                return run;
            }
            int run = handler.run(context);
            // Now run all tasks.
            return run + runAllTasks();
        } finally {
            ThreadExecutorMap.setCurrentExecutor(old);
        }
    }

    /**
     * Run all ready IO and tasks for this {@link IoEventLoop}.
     * This methods will <strong>NOT</strong> block and wait for IO / tasks to be ready, it will just
     * return directly if there is nothing to do.
     * <p>
     * <strong>Must be called from the owning {@link Thread} that was passed as an parameter on construction.</strong>
     *
     * @return the number of IO and tasks executed.
     */
    public int runNow() {
        checkCurrentThread();
        return run(nonBlockingContext);
    }

    /**
     * Run all ready IO and tasks for this {@link IoEventLoop}.
     * This methods will block and wait for IO / tasks to be ready if there is nothing to process atm for the given
     * {@code waitNanos}.
     * <p>
     * <strong>Must be called from the owning {@link Thread} that was passed as an parameter on construction.</strong>
     *
     * @param waitNanos the maximum amount of nanoseconds to wait before returning. IF {@code 0} it will block until
     *                  there is some IO / tasks ready, if {@code -1} will not block at all and just return directly
     *                  if there is nothing to run (like {@link #runNow()}).
     * @return          the number of IO and tasks executed.
     */
    public int run(long waitNanos) {
        checkCurrentThread();

        final IoHandlerContext context;
        if (waitNanos < 0) {
            context = nonBlockingContext;
        } else {
            context = blockingContext;
            blockingContext.maxBlockingNanos = waitNanos == 0 ? Long.MAX_VALUE : waitNanos;
        }
        return run(context);
    }

    private void checkCurrentThread() {
        if (!inEventLoop(Thread.currentThread())) {
            throw new IllegalStateException();
        }
    }

    /**
     * Force a wakeup and so the {@link #run(long)} method will unblock and return even if there was nothing to do.
     */
    public void wakeup() {
        if (isShuttingDown()) {
            return;
        }
        handler.wakeup();
    }

    @Override
    public ManualIoEventLoop next() {
        return this;
    }

    @Override
    public IoEventLoopGroup parent() {
        return parent;
    }

    @Deprecated
    @Override
    public ChannelFuture register(Channel channel) {
        return register(new DefaultChannelPromise(channel, this));
    }

    @Deprecated
    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }

    @Override
    public Future<IoRegistration> register(final IoHandle handle) {
        Promise<IoRegistration> promise = newPromise();
        if (inEventLoop()) {
            registerForIo0(handle, promise);
        } else {
            execute(() -> registerForIo0(handle, promise));
        }

        return promise;
    }

    private void registerForIo0(final IoHandle handle, Promise<IoRegistration> promise) {
        assert inEventLoop();
        final IoRegistration registration;
        try {
            registration = handler.register(handle);
        } catch (Exception e) {
            promise.setFailure(e);
            return;
        }
        promise.setSuccess(registration);
    }

    @Deprecated
    @Override
    public ChannelFuture register(final Channel channel, final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        ObjectUtil.checkNotNull(channel, "channel");
        channel.unsafe().register(this, promise);
        return promise;
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return handler.isCompatible(handleType);
    }

    @Override
    public boolean isIoType(Class<? extends IoHandler> handlerType) {
        return handler.getClass().equals(handlerType);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return this.owningThread.get() == thread;
    }

    /**
     * Set the owning thread that will call {@link #run}. May only be called once, and only if the owning thread was
     * not set in the constructor already.
     *
     * @param owningThread The owning thread
     */
    public void setOwningThread(Thread owningThread) {
        Objects.requireNonNull(owningThread, "owningThread");
        if (!this.owningThread.compareAndSet(null, owningThread)) {
            throw new IllegalStateException("Owning thread already set");
        }
    }

    private void shutdown0(long quietPeriod, long timeout, int shutdownState) {
        boolean inEventLoop = inEventLoop();
        boolean wakeup;
        int oldState;
        for (;;) {
            if (isShuttingDown()) {
                return;
            }
            int newState;
            wakeup = true;
            oldState = state.get();
            if (inEventLoop) {
                newState = shutdownState;
            } else if (oldState == ST_STARTED) {
                newState = shutdownState;
            } else {
                newState = oldState;
                wakeup = false;
            }

            if (state.compareAndSet(oldState, newState)) {
                break;
            }
        }
        if (quietPeriod != -1) {
            gracefulShutdownQuietPeriod = quietPeriod;
        }
        if (timeout != -1) {
            gracefulShutdownTimeout = timeout;
        }

        if (wakeup) {
            handler.wakeup();
        }
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        ObjectUtil.checkPositiveOrZero(quietPeriod, "quietPeriod");
        if (timeout < quietPeriod) {
            throw new IllegalArgumentException(
                    "timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        }
        ObjectUtil.checkNotNull(unit, "unit");

        shutdown0(unit.toNanos(quietPeriod), unit.toNanos(timeout), ST_SHUTTING_DOWN);
        return terminationFuture();
    }

    @Override
    @Deprecated
    public void shutdown() {
        shutdown0(-1, -1, ST_SHUTDOWN);
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    public boolean isShuttingDown() {
        return state.get() >= ST_SHUTTING_DOWN;
    }

    @Override
    public boolean isShutdown() {
        return state.get() >= ST_SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state.get() == ST_TERMINATED;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return terminationFuture.await(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        boolean inEventLoop = inEventLoop();
        if (inEventLoop) {
            if (isShutdown()) {
                throw new RejectedExecutionException("event executor terminated");
            }
        }
        taskQueue.add(command);
        if (!inEventLoop) {
            if (isShutdown()) {
                boolean reject = false;
                try {
                    if (taskQueue.remove(command)) {
                        reject = true;
                    }
                } catch (UnsupportedOperationException e) {
                    // The task queue does not support removal so the best thing we can do is to just move on and
                    // hope we will be able to pick-up the task before its completely terminated.
                    // In worst case we will log on termination.
                }
                if (reject) {
                    throw new RejectedExecutionException("event executor terminated");
                }
            }
            handler.wakeup();
        }
    }

    private boolean hasTasks() {
        return !taskQueue.isEmpty();
    }

    private boolean confirmShutdown() {
        if (!isShuttingDown()) {
            return false;
        }

        if (!inEventLoop()) {
            throw new IllegalStateException("must be invoked from an event loop");
        }

        cancelScheduledTasks();

        if (gracefulShutdownStartTime == 0) {
            gracefulShutdownStartTime = ticker.nanoTime();
        }

        if (runAllTasks() > 0) {
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
            return false;
        }

        final long nanoTime = ticker.nanoTime();

        if (isShutdown() || nanoTime - gracefulShutdownStartTime > gracefulShutdownTimeout) {
            return true;
        }

        if (nanoTime - lastExecutionTime <= gracefulShutdownQuietPeriod) {
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
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        // We need to check if the method was called from within the EventLoop as this would cause a deadlock.
        throwIfInEventLoop("invokeAny");
        return super.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // We need to check if the method was called from within the EventLoop as this would cause a deadlock.
        throwIfInEventLoop("invokeAny");
        return super.invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        // We need to check if the method was called from within the EventLoop as this would cause a deadlock.
        throwIfInEventLoop("invokeAll");
        return super.invokeAll(tasks);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        // We need to check if the method was called from within the EventLoop as this would cause a deadlock.
        throwIfInEventLoop("invokeAll");
        return super.invokeAll(tasks, timeout, unit);
    }

    private void throwIfInEventLoop(String method) {
        if (inEventLoop()) {
            throw new RejectedExecutionException(
                    "Calling " + method + " from within the EventLoop is not allowed as it would deadlock");
        }
    }

    private final class BlockingIoHandlerContext implements IoHandlerContext {
        long maxBlockingNanos = Long.MAX_VALUE;

        @Override
        public boolean canBlock() {
            assert inEventLoop();
            return !hasTasks() && !hasScheduledTasks();
        }

        @Override
        public long delayNanos(long currentTimeNanos) {
            assert inEventLoop();
            return Math.min(maxBlockingNanos, ManualIoEventLoop.this.delayNanos(currentTimeNanos, maxBlockingNanos));
        }

        @Override
        public long deadlineNanos() {
            assert inEventLoop();
            long next = nextScheduledTaskDeadlineNanos();
            long maxDeadlineNanos = ticker.nanoTime() + maxBlockingNanos;
            if (next == -1) {
                return maxDeadlineNanos;
            }
            return Math.min(next, maxDeadlineNanos);
        }
    };
}
