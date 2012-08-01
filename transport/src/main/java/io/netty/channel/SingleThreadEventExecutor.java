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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public abstract class SingleThreadEventExecutor extends AbstractExecutorService implements EventExecutor {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SingleThreadEventExecutor.class);

    private static final long SCHEDULE_CHECK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(10);
    private static final long SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(1);
    private static final long START_TIME = System.nanoTime();
    private static final AtomicLong nextTaskId = new AtomicLong();

    static final ThreadLocal<SingleThreadEventExecutor> CURRENT_EVENT_LOOP =
            new ThreadLocal<SingleThreadEventExecutor>();

    public static SingleThreadEventExecutor currentEventLoop() {
        return CURRENT_EVENT_LOOP.get();
    }

    private static long nanoTime() {
        return System.nanoTime() - START_TIME;
    }

    private static long deadlineNanos(long delay) {
        return nanoTime() + delay;
    }

    private final Unsafe unsafe = new Unsafe() {
        @Override
        public EventExecutor nextChild() {
            return SingleThreadEventExecutor.this;
        }
    };

    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
    private final Thread thread;
    private final Object stateLock = new Object();
    private final Semaphore threadLock = new Semaphore(0);
    // TODO: Use PriorityQueue to reduce the locking overhead of DelayQueue.
    private final Queue<ScheduledFutureTask<?>> scheduledTasks = new DelayQueue<ScheduledFutureTask<?>>();
    private final Set<Runnable> shutdownHooks = new LinkedHashSet<Runnable>();
    /** 0 - not started, 1 - started, 2 - shut down, 3 - terminated */
    private volatile int state;
    private long lastCheckTimeNanos;
    private long lastPurgeTimeNanos;

    protected SingleThreadEventExecutor(ThreadFactory threadFactory) {
        thread = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                CURRENT_EVENT_LOOP.set(SingleThreadEventExecutor.this);
                try {
                    SingleThreadEventExecutor.this.run();
                } finally {
                    try {
                        // Run all remaining tasks and shutdown hooks.
                        try {
                            cleanupTasks();
                        } finally {
                            synchronized (stateLock) {
                                state = 3;
                            }
                        }
                        cleanupTasks();
                    } finally {
                        try {
                            cleanup();
                        } finally {
                            threadLock.release();
                            assert taskQueue.isEmpty();
                        }
                    }
                }
            }

            private void cleanupTasks() {
                for (;;) {
                    boolean ran = false;
                    cancelScheduledTasks();
                    ran |= runAllTasks();
                    ran |= runShutdownHooks();
                    if (!ran && !hasTasks()) {
                        break;
                    }
                }
            }
        });
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    protected void interruptThread() {
        thread.interrupt();
    }

    protected Runnable pollTask() {
        assert inEventLoop();

        Runnable task = taskQueue.poll();
        if (task != null) {
            return task;
        }

        if (fetchScheduledTasks()) {
            task = taskQueue.poll();
            return task;
        }

        return null;
    }

    protected Runnable takeTask() throws InterruptedException {
        assert inEventLoop();

        for (;;) {
            Runnable task = taskQueue.poll(SCHEDULE_CHECK_INTERVAL * 2 / 3, TimeUnit.NANOSECONDS);
            if (task != null) {
                return task;
            }
            fetchScheduledTasks();
            task = taskQueue.poll();
            if (task != null) {
                return task;
            }
        }
    }

    protected Runnable peekTask() {
        assert inEventLoop();

        Runnable task = taskQueue.peek();
        if (task != null) {
            return task;
        }

        if (fetchScheduledTasks()) {
            task = taskQueue.peek();
            return task;
        }

        return null;
    }

    protected boolean hasTasks() {
        assert inEventLoop();

        boolean empty = taskQueue.isEmpty();
        if (!empty) {
            return true;
        }

        if (fetchScheduledTasks()) {
            return !taskQueue.isEmpty();
        }

        return false;
    }

    protected void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (isShutdown()) {
            reject();
        }
        taskQueue.add(task);
    }

    protected boolean removeTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        return taskQueue.remove(task);
    }

    protected boolean runAllTasks() {
        boolean ran = false;
        for (;;) {
            final Runnable task = pollTask();
            if (task == null) {
                break;
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

    protected abstract void run();

    protected void cleanup() {
        // Do nothing. Subclasses will override.
    }

    protected abstract void wakeup(boolean inEventLoop);

    @Override
    public boolean inEventLoop() {
        return inEventLoop(Thread.currentThread());
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

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
        boolean inEventLoop = inEventLoop();
        boolean wakeup = false;
        if (inEventLoop) {
            synchronized (stateLock) {
                assert state == 1;
                state = 2;
                wakeup = true;
            }
        } else {
            synchronized (stateLock) {
                switch (state) {
                case 0:
                    state = 3;
                    try {
                        cleanup();
                    } finally {
                        threadLock.release();
                    }
                    break;
                case 1:
                    state = 2;
                    wakeup = true;
                    break;
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
        return state >= 2;
    }

    @Override
    public boolean isTerminated() {
        return state == 3;
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
                if (state == 0) {
                    state = 1;
                    thread.start();
                }
            }
            addTask(task);
            if (isShutdown() && removeTask(task)) {
                reject();
            }
            wakeup(false);
        }
    }

    private static void reject() {
        throw new RejectedExecutionException("event executor shut down");
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (delay < 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: >= 0)", delay));
        }
        return schedule(new ScheduledFutureTask<Void>(command, null, deadlineNanos(unit.toNanos(delay))));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        if (callable == null) {
            throw new NullPointerException("callable");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (delay < 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: >= 0)", delay));
        }
        return schedule(new ScheduledFutureTask<V>(callable, deadlineNanos(unit.toNanos(delay))));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (period <= 0) {
            throw new IllegalArgumentException(
                    String.format("period: %d (expected: > 0)", period));
        }

        return schedule(new ScheduledFutureTask<Void>(
                command, null, deadlineNanos(unit.toNanos(initialDelay)), unit.toNanos(period)));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (delay <= 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: > 0)", delay));
        }

        return schedule(new ScheduledFutureTask<Void>(
                command, null, deadlineNanos(unit.toNanos(initialDelay)), -unit.toNanos(delay)));
    }

    private <V> ScheduledFuture<V> schedule(ScheduledFutureTask<V> task) {
        if (isShutdown()) {
            reject();
        }
        scheduledTasks.add(task);
        if (isShutdown()) {
            task.cancel(false);
        }

        if (!inEventLoop()) {
            synchronized (stateLock) {
                if (state == 0) {
                    state = 1;
                    thread.start();
                }
            }
        } else {
            fetchScheduledTasks();
        }

        return task;
    }

    private boolean fetchScheduledTasks() {
        if (scheduledTasks.isEmpty()) {
            return false;
        }

        long nanoTime = nanoTime();
        if (nanoTime - lastPurgeTimeNanos >= SCHEDULE_PURGE_INTERVAL) {
            for (Iterator<ScheduledFutureTask<?>> i = scheduledTasks.iterator(); i.hasNext();) {
                ScheduledFutureTask<?> task = i.next();
                if (task.isCancelled()) {
                    i.remove();
                }
            }
        }

        if (nanoTime - lastCheckTimeNanos >= SCHEDULE_CHECK_INTERVAL) {
            boolean added = false;
            for (;;) {
                ScheduledFutureTask<?> task = scheduledTasks.poll();
                if (task == null) {
                    break;
                }

                if (!task.isCancelled()) {
                    if (isShutdown()) {
                        task.cancel(false);
                    } else {
                        taskQueue.add(task);
                        added = true;
                    }
                }
            }
            return added;
        }

        return false;
    }

    private void cancelScheduledTasks() {
        if (scheduledTasks.isEmpty()) {
            return;
        }

        for (ScheduledFutureTask<?> task: scheduledTasks.toArray(new ScheduledFutureTask<?>[scheduledTasks.size()])) {
            task.cancel(false);
        }
        scheduledTasks.clear();
    }

    private class ScheduledFutureTask<V> extends FutureTask<V> implements ScheduledFuture<V> {

        private final long id = nextTaskId.getAndIncrement();
        private long deadlineNanos;
        /* 0 - no repeat, >0 - repeat at fixed rate, <0 - repeat with fixed delay */
        private final long periodNanos;

        ScheduledFutureTask(Runnable runnable, V result, long nanoTime) {
            super(runnable, result);
            deadlineNanos = nanoTime;
            periodNanos = 0;
        }

        ScheduledFutureTask(Runnable runnable, V result, long nanoTime, long period) {
            super(runnable, result);
            if (period == 0) {
                throw new IllegalArgumentException(
                        String.format("period: %d (expected: != 0)", period));
            }
            deadlineNanos = nanoTime;
            periodNanos = period;
        }

        ScheduledFutureTask(Callable<V> callable, long nanoTime) {
            super(callable);
            deadlineNanos = nanoTime;
            periodNanos = 0;
        }

        public long deadlineNanos() {
            return deadlineNanos;
        }

        public long delayNanos() {
            return Math.max(0, deadlineNanos() - nanoTime());
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (this == o) {
                return 0;
            }

            ScheduledFutureTask<?> that = (ScheduledFutureTask<?>) o;
            long d = deadlineNanos() - that.deadlineNanos();
            if (d < 0) {
                return -1;
            } else if (d > 0) {
                return 1;
            } else if (id < that.id) {
                return -1;
            } else if (id == that.id) {
                throw new Error();
            } else {
                return 1;
            }
        }

        @Override
        public void run() {
            if (periodNanos == 0) {
                super.run();
            } else {
                boolean reset = runAndReset();
                if (reset && !isShutdown()) {
                    long p = periodNanos;
                    if (p > 0) {
                        deadlineNanos += p;
                    } else {
                        deadlineNanos = nanoTime() - p;
                    }

                    schedule(this);
                }
            }
        }
    }
}
