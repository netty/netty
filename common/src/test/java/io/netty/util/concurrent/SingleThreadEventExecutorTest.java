/*
 * Copyright 2015 The Netty Project
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
package io.netty.util.concurrent;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class SingleThreadEventExecutorTest {

    @Test
    public void testWrappedExecutorIsShutdown() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        SingleThreadEventExecutor executor = new SingleThreadEventExecutor(executorService) {
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
        try {
            executor.shutdownGracefully().syncUninterruptibly();
            Assert.fail();
        } catch (CompletionException expected) {
            // expected
            Assert.assertThat(expected.getCause(), CoreMatchers.instanceOf(RejectedExecutionException.class));
        }
        Assert.assertTrue(executor.isShutdown());
    }

    private static void executeShouldFail(Executor executor) {
        try {
            executor.execute(() -> {
                // Noop.
            });
            Assert.fail();
        } catch (RejectedExecutionException expected) {
            // expected
        }
    }

    @Test
    public void testThreadProperties() {
        final AtomicReference<Thread> threadRef = new AtomicReference<Thread>();
        SingleThreadEventExecutor executor = new SingleThreadEventExecutor(new DefaultThreadFactory("test")) {
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
        Assert.assertEquals(thread.getId(), threadProperties.id());
        Assert.assertEquals(thread.getName(), threadProperties.name());
        Assert.assertEquals(thread.getPriority(), threadProperties.priority());
        Assert.assertEquals(thread.isAlive(), threadProperties.isAlive());
        Assert.assertEquals(thread.isDaemon(), threadProperties.isDaemon());
        Assert.assertTrue(threadProperties.stackTrace().length > 0);
        executor.shutdownGracefully();
    }

    @Test(expected = RejectedExecutionException.class, timeout = 3000)
    public void testInvokeAnyInEventLoop() throws Throwable {
        try {
            testInvokeInEventLoop(true, false);
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

    @Test(expected = RejectedExecutionException.class, timeout = 3000)
    public void testInvokeAnyInEventLoopWithTimeout() throws Throwable {
        try {
            testInvokeInEventLoop(true, true);
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

    @Test(expected = RejectedExecutionException.class, timeout = 3000)
    public void testInvokeAllInEventLoop() throws Throwable {
        try {
            testInvokeInEventLoop(false, false);
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

    @Test(expected = RejectedExecutionException.class, timeout = 3000)
    public void testInvokeAllInEventLoopWithTimeout() throws Throwable {
        try {
            testInvokeInEventLoop(false, true);
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

    private static void testInvokeInEventLoop(final boolean any, final boolean timeout) {
        final SingleThreadEventExecutor executor = new SingleThreadEventExecutor(Executors.defaultThreadFactory()) {
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
            final Promise<Void> promise = executor.newPromise();
            executor.execute(() -> {
                try {
                    Set<Callable<Boolean>> set = Collections.singleton(() -> {
                        promise.setFailure(new AssertionError("Should never execute the Callable"));
                        return Boolean.TRUE;
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
            });
            promise.syncUninterruptibly();
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
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

        final Runnable dummyTask = () -> {
        };

        final LinkedBlockingQueue<Future<?>> submittedTasks = new LinkedBlockingQueue<Future<?>>();
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger rejects = new AtomicInteger();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        final SingleThreadEventExecutor executor = new SingleThreadEventExecutor(executorService, Integer.MAX_VALUE,
                RejectedExecutionHandlers.reject()) {

            @Override
            protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
                return taskQueue;
            }

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
            protected boolean confirmShutdown0() {
                boolean result = super.confirmShutdown0();
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
        Assert.assertEquals(0, executor.drainTasks());

        // Verify that queue is empty and all attempts either succeeded or were rejected
        Assert.assertTrue(taskQueue.isEmpty());
        Assert.assertTrue(attempts.get() > 0);
        Assert.assertEquals(attempts.get(), submittedTasks.size() + rejects.get());
        for (Future<?> f : submittedTasks) {
            Assert.assertTrue(f.isSuccess());
        }
    }

    @Test(timeout = 5000)
    public void testTakeTask() throws Exception {
        final SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(Executors.defaultThreadFactory()) {
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
    }

    @Test(timeout = 5000)
    public void testTakeTaskAlwaysHasTask() throws Exception {
        //for https://github.com/netty/netty/issues/1614

        final SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(Executors.defaultThreadFactory()) {
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
        ScheduledFuture<?> f = executor.schedule(t, 1500, TimeUnit.MILLISECONDS);

        final Runnable doNothing = () -> { };
        final AtomicBoolean stop = new AtomicBoolean(false);

        //ensure always has at least one task in taskQueue
        //check if scheduled tasks are triggered
        try {
            new Thread(() -> {
                while (!stop.get()) {
                    executor.execute(doNothing);
                }
            }).start();

            f.sync();

            assertThat(t.ran.get(), is(true));
        } finally {
            stop.set(true);
        }
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
}
