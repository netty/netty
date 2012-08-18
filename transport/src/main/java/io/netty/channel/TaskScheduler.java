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

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TaskScheduler {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(TaskScheduler.class);

    private static final long SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(1);
    private static final long START_TIME = System.nanoTime();
    private static final AtomicLong nextTaskId = new AtomicLong();

    private static long nanoTime() {
        return System.nanoTime() - START_TIME;
    }

    private static long deadlineNanos(long delay) {
        return nanoTime() + delay;
    }

    private final BlockingQueue<ScheduledFutureTask<?>> taskQueue = new DelayQueue<ScheduledFutureTask<?>>();
    private final Thread thread;
    private final Object stateLock = new Object();
    private final Semaphore threadLock = new Semaphore(0);
    /** 0 - not started, 1 - started, 2 - shut down, 3 - terminated */
    private volatile int state;

    public TaskScheduler(ThreadFactory threadFactory) {
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }

        thread = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (;;) {
                        ScheduledFutureTask<?> task;
                        try {
                            task = taskQueue.take();
                            runTask(task);
                        } catch (InterruptedException e) {
                            // Waken up by interruptThread()
                        }

                        if (isShutdown() && taskQueue.peek() == null) {
                            break;
                        }
                    }
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
                        threadLock.release();
                        assert taskQueue.isEmpty();
                    }
                }
            }

            private void runTask(ScheduledFutureTask<?> task) {
                EventExecutor executor = task.executor;
                if (executor == null) {
                    task.run();
                } else {
                    if (executor.isShutdown()) {
                        task.cancel(false);
                    } else {
                        try {
                            task.executor.execute(task);
                        } catch (RejectedExecutionException e) {
                            task.cancel(false);
                        }
                    }
                }
            }

            private void cleanupTasks() {
                for (;;) {
                    boolean ran = false;
                    cancelScheduledTasks();
                    for (;;) {
                        final ScheduledFutureTask<?> task = taskQueue.poll();
                        if (task == null) {
                            break;
                        }

                        try {
                            runTask(task);
                            ran = true;
                        } catch (Throwable t) {
                            logger.warn("A task raised an exception.", t);
                        }
                    }

                    if (!ran && taskQueue.isEmpty()) {
                        break;
                    }
                }
            }
        });
    }

    private boolean inSameThread() {
        return Thread.currentThread() == thread;
    }

    public void shutdown() {
        boolean inSameThread = inSameThread();
        boolean wakeup = false;
        if (inSameThread) {
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
                    threadLock.release();
                    break;
                case 1:
                    state = 2;
                    wakeup = true;
                    break;
                }
            }
        }

        if (wakeup && !inSameThread && isShutdown()) {
            thread.interrupt();
        }
    }

    public boolean isShutdown() {
        return state >= 2;
    }

    public boolean isTerminated() {
        return state == 3;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        if (inSameThread()) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }

        if (threadLock.tryAcquire(timeout, unit)) {
            threadLock.release();
        }

        return isTerminated();
    }

    public ScheduledFuture<?> schedule(
            EventExecutor executor, Runnable command, long delay, TimeUnit unit) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
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
        return schedule(new ScheduledFutureTask<Void>(executor, command, null, deadlineNanos(unit.toNanos(delay))));
    }

    public <V> ScheduledFuture<V> schedule(
            EventExecutor executor, Callable<V> callable, long delay, TimeUnit unit) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
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
        return schedule(new ScheduledFutureTask<V>(executor, callable, deadlineNanos(unit.toNanos(delay))));
    }

    public ScheduledFuture<?> scheduleAtFixedRate(
            EventExecutor executor, Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
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
                executor, command, null, deadlineNanos(unit.toNanos(initialDelay)), unit.toNanos(period)));
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(
            EventExecutor executor, Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
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
                executor, command, null, deadlineNanos(unit.toNanos(initialDelay)), -unit.toNanos(delay)));
    }

    private <V> ScheduledFuture<V> schedule(ScheduledFutureTask<V> task) {
        if (isShutdown()) {
            reject();
        }
        taskQueue.add(task);
        if (isShutdown()) {
            task.cancel(false);
        }

        boolean started = false;
        if (!inSameThread()) {
            synchronized (stateLock) {
                if (state == 0) {
                    state = 1;
                    thread.start();
                    started = true;
                }
            }
        }

        if (started) {
            schedule(new ScheduledFutureTask<Void>(
                    null, new PurgeTask(), null,
                    deadlineNanos(SCHEDULE_PURGE_INTERVAL), -SCHEDULE_PURGE_INTERVAL));
        }

        return task;
    }

    private static void reject() {
        throw new RejectedExecutionException("event executor shut down");
    }

    private void cancelScheduledTasks() {
        if (taskQueue.isEmpty()) {
            return;
        }

        for (ScheduledFutureTask<?> task: taskQueue.toArray(new ScheduledFutureTask<?>[taskQueue.size()])) {
            task.cancel(false);
        }

        taskQueue.clear();
    }

    private class ScheduledFutureTask<V> extends FutureTask<V> implements ScheduledFuture<V> {

        private final EventExecutor executor;
        private final long id = nextTaskId.getAndIncrement();
        private long deadlineNanos;
        /* 0 - no repeat, >0 - repeat at fixed rate, <0 - repeat with fixed delay */
        private final long periodNanos;

        ScheduledFutureTask(EventExecutor executor, Runnable runnable, V result, long nanoTime) {
            super(runnable, result);
            this.executor = executor;
            deadlineNanos = nanoTime;
            periodNanos = 0;
        }

        ScheduledFutureTask(EventExecutor executor, Runnable runnable, V result, long nanoTime, long period) {
            super(runnable, result);
            if (period == 0) {
                throw new IllegalArgumentException(
                        String.format("period: %d (expected: != 0)", period));
            }
            this.executor = executor;
            deadlineNanos = nanoTime;
            periodNanos = period;
        }

        ScheduledFutureTask(EventExecutor executor, Callable<V> callable, long nanoTime) {
            super(callable);
            this.executor = executor;
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

    private final class PurgeTask implements Runnable {
        @Override
        public void run() {
            Iterator<ScheduledFutureTask<?>> i = taskQueue.iterator();
            while (i.hasNext()) {
                ScheduledFutureTask<?> task = i.next();
                if (task.isCancelled()) {
                    i.remove();
                }
            }
        }
    }
}
