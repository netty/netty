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
package io.netty5.util.concurrent;

import org.junit.jupiter.api.Test;

import java.util.Queue;
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

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SingleThreadEventExecutorTest {
    public static final Runnable DUMMY_TASK = () -> {
    };

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
        var exception = assertThrows(
                CompletionException.class, () -> executor.shutdownGracefully().syncUninterruptibly());
        assertThat(exception).hasCauseInstanceOf(RejectedExecutionException.class);
        assertTrue(executor.isShutdown());
    }

    private static void executeShouldFail(Executor executor) {
        assertThrows(RejectedExecutionException.class, () -> executor.execute(DUMMY_TASK));
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
        assertEquals(thread.getId(), threadProperties.id());
        assertEquals(thread.getName(), threadProperties.name());
        assertEquals(thread.getPriority(), threadProperties.priority());
        assertEquals(thread.isAlive(), threadProperties.isAlive());
        assertEquals(thread.isDaemon(), threadProperties.isDaemon());
        assertTrue(threadProperties.stackTrace().length > 0);
        executor.shutdownGracefully();
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
                        submittedTasks.add(submit(DUMMY_TASK));
                    } catch (RejectedExecutionException e) {
                        // ignore, tasks are either accepted or rejected
                        rejects.incrementAndGet();
                    }
                }
                return result;
            }
        };

        // Start the loop
        executor.submit(DUMMY_TASK).sync();

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

        assertTimeoutPreemptively(ofSeconds(5), () -> {
            //add task
            TestRunnable beforeTask = new TestRunnable();
            executor.execute(beforeTask);

            //add scheduled task
            TestRunnable scheduledTask = new TestRunnable();
            Future<?> f = executor.schedule(scheduledTask , 1500, TimeUnit.MILLISECONDS);

            //add task
            TestRunnable afterTask = new TestRunnable();
            executor.execute(afterTask);

            f.sync();

            assertThat(beforeTask.ran.get()).isTrue();
            assertThat(scheduledTask.ran.get()).isTrue();
            assertThat(afterTask.ran.get()).isTrue();
        });
    }

    @Test
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

        assertTimeoutPreemptively(ofSeconds(5), () -> {
            //add scheduled task
            TestRunnable t = new TestRunnable();
            Future<?> f = executor.schedule(t, 1500, TimeUnit.MILLISECONDS);

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

            assertThat(t.ran.get()).isTrue();
        });
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
