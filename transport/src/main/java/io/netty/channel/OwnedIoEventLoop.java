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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.OrderedEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link IoEventLoop} implementation that is owned by the user and so needs to be driven by the user with the given
 * {@link Thread}. This means that the user is responsible to call either {@link #run()} or {@link #waitAndRun(long)}
 * to execute IO or tasks that were submitted to this {@link IoEventLoop}.
 * <p>
 * This is for <strong>advanced use-cases only</strong>, where the user wants to own the {@link Thread} that drives the
 * {@link IoEventLoop} to also do other work. That said care must be taken that the {@link #run() or
 * {@link #waitAndRun()}} methods are called in a timely fashion.
 */
public final class OwnedIoEventLoop extends AbstractScheduledEventExecutor
        implements OrderedEventExecutor, IoEventLoop {

    private static final int ST_STARTED = 4;
    private static final int ST_SHUTTING_DOWN = 5;
    private static final int ST_SHUTDOWN = 6;
    private static final int ST_TERMINATED = 7;
    private final AtomicInteger state = new AtomicInteger();
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
    private final IoHandlerContext blockingContext = new BlockingIoHandlerContext(Long.MAX_VALUE);
    private final Thread owningThread;
    private final IoHandler handler;

    private volatile long gracefulShutdownQuietPeriod;
    private volatile long gracefulShutdownTimeout;
    private long gracefulShutdownStartTime;
    private long lastExecutionTime;

    /**
     * Create a new {@link IoEventLoop} that is owned by the user and so needs to be driven by the user with the given
     * {@link Thread}. This means that the user is responsible to call either {@link #run()} or
     * {@link #waitAndRun(long)} to execute IO or tasks that were submitted to this {@link IoEventLoop}.
     *
     *
     * @param owningThread      the {@link Thread} that executed the IO and tasks for this {@link IoEventLoop}. The
     *                          user will use this {@link Thread} to call {@link #run()} or {@link #waitAndRun(long)} to
     *                          make progress.
     * @param factory           the {@link IoHandlerFactory} that will be used to create the {@link IoHandler} that is
     *                         used by this {@link IoEventLoop}.
     */
    public OwnedIoEventLoop(Thread owningThread, IoHandlerFactory factory) {
        this.owningThread = Objects.requireNonNull(owningThread, "owningThread");
        this.handler = factory.newHandler(this);
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
            lastExecutionTime = getCurrentTimeNanos();
        }
        return numRun;
    }

    private int run(IoHandlerContext context) {
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
    }

    /**
     * Run all ready IO and tasks for this {@link IoEventLoop}.
     * This methods will <strong>NOT</strong> block and wait for IO / tasks to be ready, it will just
     * return directly if there is nothing to do.
     * <p>
     * <strong>Must be called from the owning {@link Thread}</strong>
     *
     * @return the number of IO and tasks executed.
     */
    public int run() {
        checkCurrentThread();
        return run(nonBlockingContext);
    }

    /**
     * Run all ready IO and tasks for this {@link IoEventLoop}.
     * This methods will  block and wait for IO / tasks to be ready if there is nothing to process atm.
     *
     * @param nanos     the maximum amount of nanoseconds to wait before returning.
     * @return          the number of IO and tasks executed.
     */
    public int waitAndRun(long nanos) {
        checkCurrentThread();
        if (nanos <= 0) {
            return run(blockingContext);
        }
        return run(new BlockingIoHandlerContext(nanos));
    }

    private void checkCurrentThread() {
        if (!inEventLoop(Thread.currentThread())) {
            throw new IllegalStateException();
        }
    }

    @Override
    public OwnedIoEventLoop next() {
        return this;
    }

    @Override
    public IoEventLoopGroup parent() {
        return this;
    }

    public ChannelFuture register(Channel channel) {
        return register(new DefaultChannelPromise(channel, this));
    }

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
    public void shutdown() {
        if (isShutdown()) {
            return;
        }

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
                newState = ST_SHUTDOWN;
            } else {
                switch (oldState) {
                    case ST_STARTED:
                    case ST_SHUTTING_DOWN:
                        newState = ST_SHUTDOWN;
                        break;
                    default:
                        newState = oldState;
                        wakeup = false;
                }
            }
            if (state.compareAndSet(oldState, newState)) {
                break;
            }
        }

        if (wakeup) {
            handler.wakeup();
        }
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return this.owningThread == thread;
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        ObjectUtil.checkPositiveOrZero(quietPeriod, "quietPeriod");
        if (timeout < quietPeriod) {
            throw new IllegalArgumentException(
                    "timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        }
        ObjectUtil.checkNotNull(unit, "unit");

        if (isShuttingDown()) {
            return terminationFuture();
        }

        gracefulShutdownQuietPeriod = unit.toNanos(quietPeriod);
        gracefulShutdownTimeout = unit.toNanos(timeout);

        shutdown();

        return terminationFuture();
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
        }
        if (!inEventLoop()) {
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
            gracefulShutdownStartTime = getCurrentTimeNanos();
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

        final long nanoTime = getCurrentTimeNanos();

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

    private class BlockingIoHandlerContext implements IoHandlerContext {
        private final long maxBlockingNanos;

        BlockingIoHandlerContext(long maxBlockingNanos) {
            this.maxBlockingNanos = maxBlockingNanos;
        }

        @Override
        public boolean canBlock() {
            assert inEventLoop();
            return !hasTasks() && !hasScheduledTasks();
        }

        @Override
        public long delayNanos(long currentTimeNanos) {
            assert inEventLoop();
            return OwnedIoEventLoop.this.delayNanos(currentTimeNanos, maxBlockingNanos);
        }

        @Override
        public long deadlineNanos() {
            assert inEventLoop();
            long next = OwnedIoEventLoop.this.nextScheduledTaskDeadlineNanos();
            if (next == -1) {
                return maxBlockingNanos;
            }
            return Math.min(next, maxBlockingNanos);
        }
    };
}
