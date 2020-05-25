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

import org.junit.Assert;
import org.junit.Test;

import io.netty.util.concurrent.AbstractEventExecutor.LazyRunnable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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

        SingleThreadEventExecutor executor = new SingleThreadEventExecutor(null, executorService, false) {
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
        } catch (RejectedExecutionException expected) {
            // expected
        }
        Assert.assertTrue(executor.isShutdown());
    }

    private static void executeShouldFail(Executor executor) {
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // Noop.
                }
            });
            Assert.fail();
        } catch (RejectedExecutionException expected) {
            // expected
        }
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
        Assert.assertEquals(thread.getId(), threadProperties.id());
        Assert.assertEquals(thread.getName(), threadProperties.name());
        Assert.assertEquals(thread.getPriority(), threadProperties.priority());
        Assert.assertEquals(thread.isAlive(), threadProperties.isAlive());
        Assert.assertEquals(thread.isDaemon(), threadProperties.isDaemon());
        Assert.assertTrue(threadProperties.stackTrace().length > 0);
        executor.shutdownGracefully();
    }

    @Test(expected = RejectedExecutionException.class, timeout = 3000)
    public void testInvokeAnyInEventLoop() {
        testInvokeInEventLoop(true, false);
    }

    @Test(expected = RejectedExecutionException.class, timeout = 3000)
    public void testInvokeAnyInEventLoopWithTimeout() {
        testInvokeInEventLoop(true, true);
    }

    @Test(expected = RejectedExecutionException.class, timeout = 3000)
    public void testInvokeAllInEventLoop() {
        testInvokeInEventLoop(false, false);
    }

    @Test(expected = RejectedExecutionException.class, timeout = 3000)
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
            final Promise<Void> promise = executor.newPromise();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Set<Callable<Boolean>> set = Collections.<Callable<Boolean>>singleton(new Callable<Boolean>() {
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
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
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

    static class LazyLatchTask extends LatchTask implements LazyRunnable { }

    @Test
    public void testLazyExecution() throws Exception {
        final SingleThreadEventExecutor executor = new SingleThreadEventExecutor(null,
                Executors.defaultThreadFactory(), false) {
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
                        Assert.fail(e.toString());
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

    @Test(timeout = 5000)
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
    }

    @Test(timeout = 5000)
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
        ScheduledFuture<?> f = executor.schedule(t, 1500, TimeUnit.MILLISECONDS);

        final Runnable doNothing = new Runnable() {
            @Override
            public void run() {
                //NOOP
            }
        };
        final AtomicBoolean stop = new AtomicBoolean(false);

        //ensure always has at least one task in taskQueue
        //check if scheduled tasks are triggered
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!stop.get()) {
                        executor.execute(doNothing);
                    }
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
