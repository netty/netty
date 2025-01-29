/*
 * Copyright 2015 The Netty Project
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
package io.netty.util.concurrent;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SingleThreadEventExecutorTest {

    private static final class TestThread extends Thread {
        private final CountDownLatch startedLatch = new CountDownLatch(1);
        private final CountDownLatch runLatch = new CountDownLatch(1);

        TestThread(Runnable task) {
            super(task);
        }

        @Override
        public void start() {
            super.start();
            startedLatch.countDown();
        }

        @Override
        public void run() {
            runLatch.countDown();
            super.run();
        }

        void awaitStarted() throws InterruptedException {
            startedLatch.await();
        }

        void awaitRunnableExecution() throws InterruptedException {
            runLatch.await();
        }
    }

    private static final class TestThreadFactory implements ThreadFactory {
        final LinkedBlockingQueue<TestThread> threads = new LinkedBlockingQueue<>();
        @Override
        public Thread newThread(@NotNull Runnable r) {
            TestThread thread = new TestThread(r);
            threads.add(thread);
            return thread;
        }
    }

    private static final class SuspendingSingleThreadEventExecutor extends SingleThreadEventExecutor {

        SuspendingSingleThreadEventExecutor(ThreadFactory threadFactory) {
            super(null, threadFactory, false, true,
                    Integer.MAX_VALUE, RejectedExecutionHandlers.reject());
        }

        @Override
        protected void run() {
            while (!confirmShutdown() && !canSuspend()) {
                Runnable task = takeTask();
                if (task != null) {
                    task.run();
                }
            }
        }

        @Override
        protected void wakeup(boolean inEventLoop) {
            interruptThread();
        }
    }

    @Test
    void testSuspension() throws Exception {
        TestThreadFactory threadFactory = new TestThreadFactory();
        final SingleThreadEventExecutor executor = new SuspendingSingleThreadEventExecutor(threadFactory);
        LatchTask task1 = new LatchTask();
        executor.execute(task1);
        Thread currentThread = threadFactory.threads.take();
        assertTrue(executor.trySuspend());
        task1.await();

        // Let's wait till the current Thread did die....
        currentThread.join();

        // Should be suspended now, we should be able to also call trySuspend() again.
        assertTrue(executor.isSuspended());
        // There was no thread created as we did not try to execute something yet.
        assertTrue(threadFactory.threads.isEmpty());

        LatchTask task2 = new LatchTask();
        executor.execute(task2);
        // Suspendion was reset as a task was executed.
        assertFalse(executor.isSuspended());
        currentThread = threadFactory.threads.take();
        task2.await();

        executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
        currentThread.join();
        assertFalse(executor.isSuspended());
        assertTrue(executor.isShutdown());

        // Guarantee that al tasks were able to die...
        while ((currentThread = threadFactory.threads.poll()) != null) {
            currentThread.join();
        }
    }

    @Test
    void testNotSuspendedUntilScheduledTaskIsCancelled() throws Exception {
        TestThreadFactory threadFactory = new TestThreadFactory();
        final SingleThreadEventExecutor executor = new SuspendingSingleThreadEventExecutor(threadFactory);

        // Schedule a task which is so far in the future that we are sure it will not run at all.
        Future<?> future = executor.schedule(() -> { }, 1, TimeUnit.DAYS);
        TestThread currentThread = threadFactory.threads.take();
        // Let's wait until the thread is started
        currentThread.awaitStarted();
        currentThread.awaitRunnableExecution();
        assertTrue(executor.trySuspend());

        // Now cancel the task which should allow the suspension to let the thread die once we call trySuspend() again
        assertTrue(future.cancel(false));
        assertTrue(executor.trySuspend());

        currentThread.join();

        // Should be suspended now, we should be able to also call trySuspend() again.
        assertTrue(executor.trySuspend());
        assertTrue(executor.isSuspended());

        executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
        assertFalse(executor.isSuspended());
        assertTrue(executor.isShutdown());

        // Guarantee that al tasks were able to die...
        while ((currentThread = threadFactory.threads.poll()) != null) {
            currentThread.join();
        }
    }

    @Test
    void testNotSuspendedUntilScheduledTaskDidRun() throws Exception {
        TestThreadFactory threadFactory = new TestThreadFactory();
        final SingleThreadEventExecutor executor = new SuspendingSingleThreadEventExecutor(threadFactory);

        final CountDownLatch latch = new CountDownLatch(1);
        // Schedule a task which is so far in the future that we are sure it will not run at all.
        Future<?> future = executor.schedule(() -> {
            try {
                latch.await();
            } catch (InterruptedException ignore) {
                // ignore
            }
        }, 100, TimeUnit.MILLISECONDS);
        TestThread currentThread = threadFactory.threads.take();
        // Let's wait until the thread is started
        currentThread.awaitStarted();
        currentThread.awaitRunnableExecution();
        latch.countDown();
        assertTrue(executor.trySuspend());

        // Now wait till the scheduled task was run
        future.sync();

        currentThread.join();

        // Should be suspended now, we should be able to also call trySuspend() again.
        assertTrue(executor.trySuspend());
        assertTrue(executor.isSuspended());

        executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
        assertFalse(executor.isSuspended());
        assertTrue(executor.isShutdown());

        // Guarantee that al tasks were able to die...
        while ((currentThread = threadFactory.threads.poll()) != null) {
            currentThread.join();
        }
    }

    @Test
    public void testWrappedExecutorIsShutdown() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

       final SingleThreadEventExecutor executor =
               new SingleThreadEventExecutor(null, executorService, false) {
            @Override
            protected void run() {
                while (!confirmShutdown()) {
                    Runnable task = takeTask();
                    if (task != null) {
                        task.run();
                    }
                }
            }
        };

        executorService.shutdownNow();
        executeShouldFail(executor);
        executeShouldFail(executor);
        assertThrows(RejectedExecutionException.class, new Executable() {
            @Override
            public void execute() {
                executor.shutdownGracefully().syncUninterruptibly();
            }
        });
        assertTrue(executor.isShutdown());
    }

    private static void executeShouldFail(final Executor executor) {
        assertThrows(RejectedExecutionException.class, new Executable() {
            @Override
            public void execute() {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        // Noop.
                    }
                });
            }
        });
    }

    @Test
    public void testThreadProperties() {
        final AtomicReference<Thread> threadRef = new AtomicReference<Thread>();
        SingleThreadEventExecutor executor = new SingleThreadEventExecutor(
                null, new DefaultThreadFactory("test"), false) {
            @Override
            protected void run() {
                threadRef.set(Thread.currentThread());
                while (!confirmShutdown()) {
                    Runnable task = takeTask();
                    if (task != null) {
                        task.run();
                    }
                }
            }
        };
        ThreadProperties threadProperties = executor.threadProperties();

        Thread thread = threadRef.get();
        assertEquals(thread.getId(), threadProperties.id());
        assertEquals(thread.getName(), threadProperties.name());
        assertEquals(thread.getPriority(), threadProperties.priority());
        assertEquals(thread.isAlive(), threadProperties.isAlive());
        assertEquals(thread.isDaemon(), threadProperties.isDaemon());
        assertTrue(threadProperties.stackTrace().length > 0);
        executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testInvokeAnyInEventLoop() {
        testInvokeInEventLoop(true, false);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testInvokeAnyInEventLoopWithTimeout() {
        testInvokeInEventLoop(true, true);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testInvokeAllInEventLoop() {
        testInvokeInEventLoop(false, false);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testInvokeAllInEventLoopWithTimeout() {
        testInvokeInEventLoop(false, true);
    }

    private static void testInvokeInEventLoop(final boolean any, final boolean timeout) {
        final SingleThreadEventExecutor executor = new SingleThreadEventExecutor(null,
                Executors.defaultThreadFactory(), true) {
            @Override
            protected void run() {
                while (!confirmShutdown()) {
                    Runnable task = takeTask();
                    if (task != null) {
                        task.run();
                    }
                }
            }
        };
        try {
            assertThrows(RejectedExecutionException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    final Promise<Void> promise = executor.newPromise();
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Set<Callable<Boolean>> set = Collections.<Callable<Boolean>>singleton(
                                        new Callable<Boolean>() {
                                    @Override
                                    public Boolean call() throws Exception {
                                        promise.setFailure(new AssertionError("Should never execute the Callable"));
                                        return Boolean.TRUE;
                                    }
                                });
                                if (any) {
                                    if (timeout) {
                                        executor.invokeAny(set, 10, TimeUnit.SECONDS);
                                    } else {
                                        executor.invokeAny(set);
                                    }
                                } else {
                                    if (timeout) {
                                        executor.invokeAll(set, 10, TimeUnit.SECONDS);
                                    } else {
                                        executor.invokeAll(set);
                                    }
                                }
                                promise.setFailure(new AssertionError("Should never reach here"));
                            } catch (Throwable cause) {
                                promise.setFailure(cause);
                            }
                        }
                    });
                    promise.syncUninterruptibly();
                }
            });
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
    }

    static class LatchTask extends CountDownLatch implements Runnable {
        LatchTask() {
            super(1);
        }

        @Override
        public void run() {
            countDown();
        }
    }

    static class LazyLatchTask extends LatchTask { }

    @Test
    public void testLazyExecution() throws Exception {
        final SingleThreadEventExecutor executor = new SingleThreadEventExecutor(null,
                Executors.defaultThreadFactory(), false) {

            @Override
            protected boolean wakesUpForTask(final Runnable task) {
                return !(task instanceof LazyLatchTask);
            }

            @Override
            protected void run() {
                while (!confirmShutdown()) {
                    try {
                        synchronized (this) {
                            if (!hasTasks()) {
                                wait();
                            }
                        }
                        runAllTasks();
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail(e.toString());
                    }
                }
            }

            @Override
            protected void wakeup(boolean inEventLoop) {
                if (!inEventLoop) {
                    synchronized (this) {
                        notifyAll();
                    }
                }
            }
        };

        // Ensure event loop is started
        LatchTask latch0 = new LatchTask();
        executor.execute(latch0);
        assertTrue(latch0.await(100, TimeUnit.MILLISECONDS));
        // Pause to ensure it enters waiting state
        Thread.sleep(100L);

        // Submit task via lazyExecute
        LatchTask latch1 = new LatchTask();
        executor.lazyExecute(latch1);
        // Sumbit lazy task via regular execute
        LatchTask latch2 = new LazyLatchTask();
        executor.execute(latch2);

        // Neither should run yet
        assertFalse(latch1.await(100, TimeUnit.MILLISECONDS));
        assertFalse(latch2.await(100, TimeUnit.MILLISECONDS));

        // Submit regular task via regular execute
        LatchTask latch3 = new LatchTask();
        executor.execute(latch3);

        // Should flush latch1 and latch2 and then run latch3 immediately
        assertTrue(latch3.await(100, TimeUnit.MILLISECONDS));
        assertEquals(0, latch1.getCount());
        assertEquals(0, latch2.getCount());

        executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void testTaskAddedAfterShutdownNotAbandoned() throws Exception {

        // A queue that doesn't support remove, so tasks once added cannot be rejected anymore
        LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>() {
            @Override
            public boolean remove(Object o) {
                throw new UnsupportedOperationException();
            }
        };

        final Runnable dummyTask = new Runnable() {
            @Override
            public void run() {
            }
        };

        final LinkedBlockingQueue<Future<?>> submittedTasks = new LinkedBlockingQueue<Future<?>>();
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger rejects = new AtomicInteger();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        final SingleThreadEventExecutor executor = new SingleThreadEventExecutor(null, executorService, false,
                taskQueue, RejectedExecutionHandlers.reject()) {
            @Override
            protected void run() {
                while (!confirmShutdown()) {
                    Runnable task = takeTask();
                    if (task != null) {
                        task.run();
                    }
                }
            }

            @Override
            protected boolean confirmShutdown() {
                boolean result = super.confirmShutdown();
                // After shutdown is confirmed, scheduled one more task and record it
                if (result) {
                    attempts.incrementAndGet();
                    try {
                        submittedTasks.add(submit(dummyTask));
                    } catch (RejectedExecutionException e) {
                        // ignore, tasks are either accepted or rejected
                        rejects.incrementAndGet();
                    }
                }
                return result;
            }
        };

        // Start the loop
        executor.submit(dummyTask).sync();

        // Shutdown without any quiet period
        executor.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS).sync();

        // Ensure there are no user-tasks left.
        assertEquals(0, executor.drainTasks());

        // Verify that queue is empty and all attempts either succeeded or were rejected
        assertTrue(taskQueue.isEmpty());
        assertTrue(attempts.get() > 0);
        assertEquals(attempts.get(), submittedTasks.size() + rejects.get());
        for (Future<?> f : submittedTasks) {
            assertTrue(f.isSuccess());
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testTakeTask() throws Exception {
        final SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(null, Executors.defaultThreadFactory(), true) {
            @Override
            protected void run() {
                while (!confirmShutdown()) {
                    Runnable task = takeTask();
                    if (task != null) {
                        task.run();
                    }
                }
            }
        };

        //add task
        TestRunnable beforeTask = new TestRunnable();
        executor.execute(beforeTask);

        //add scheduled task
        TestRunnable scheduledTask = new TestRunnable();
        ScheduledFuture<?> f = executor.schedule(scheduledTask , 1500, TimeUnit.MILLISECONDS);

        //add task
        TestRunnable afterTask = new TestRunnable();
        executor.execute(afterTask);

        f.sync();

        assertThat(beforeTask.ran.get(), is(true));
        assertThat(scheduledTask.ran.get(), is(true));
        assertThat(afterTask.ran.get(), is(true));
        executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testTakeTaskAlwaysHasTask() throws Exception {
        //for https://github.com/netty/netty/issues/1614

        final SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(null, Executors.defaultThreadFactory(), true) {
            @Override
            protected void run() {
                while (!confirmShutdown()) {
                    Runnable task = takeTask();
                    if (task != null) {
                        task.run();
                    }
                }
            }
        };

        //add scheduled task
        TestRunnable t = new TestRunnable();
        final ScheduledFuture<?> f = executor.schedule(t, 1500, TimeUnit.MILLISECONDS);

        //ensure always has at least one task in taskQueue
        //check if scheduled tasks are triggered
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (!f.isDone()) {
                    executor.execute(this);
                }
            }
        });

        f.sync();

        assertThat(t.ran.get(), is(true));
        executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    private static final class TestRunnable implements Runnable {
        final AtomicBoolean ran = new AtomicBoolean();

        TestRunnable() {
        }

        @Override
        public void run() {
            ran.set(true);
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testExceptionIsPropagatedToTerminationFuture() throws Exception {
        final IllegalStateException exception = new IllegalStateException();
        final SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(null, Executors.defaultThreadFactory(), true) {
                    @Override
                    protected void run() {
                        throw exception;
                    }
                };

        // Schedule something so we are sure the run() method will be called.
        executor.execute(new Runnable() {
            @Override
            public void run() {
                // Noop.
            }
        });

        executor.terminationFuture().await();

        assertSame(exception, executor.terminationFuture().cause());
    }
}
