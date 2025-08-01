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
package io.netty.util.concurrent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class MultithreadEventExecutorGroupTest {

    private static void busyTask(long duration, TimeUnit unit) {
        long endTime = System.nanoTime() + unit.toNanos(duration);
        while (System.nanoTime() < endTime) {
            // Spin-wait to simulate CPU usage
        }
    }

    private static final class TestEventExecutor extends SingleThreadEventExecutor {
        private final AtomicBoolean highLoad = new AtomicBoolean(false);

        TestEventExecutor(EventExecutorGroup parent, Executor executor) {
            super(parent, executor, true, true, DEFAULT_MAX_PENDING_EXECUTOR_TASKS,
                  RejectedExecutionHandlers.reject());
        }

        void setHighLoad(boolean highLoad) {
            this.highLoad.set(highLoad);
        }

        @Override
        protected void run() {
            do {
                if (highLoad.get()) {
                    runAllTasks(TimeUnit.MILLISECONDS.toNanos(20));
                    long busyWorkStart = ticker().nanoTime();
                    busyTask(35, TimeUnit.MILLISECONDS);
                    long busyWorkEnd = ticker().nanoTime();
                    reportActiveIoTime(busyWorkEnd - busyWorkStart);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    boolean ranTask = runAllTasks();
                    if (ranTask) {
                        // If we ran tasks, immediately loop back to check highLoad state
                        continue;
                    }

                    // No immediate tasks available, sleep to avoid busy waiting
                    // This allows the thread to be responsive to state changes while staying idle
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } while (!confirmShutdown() && !canSuspend());
        }
    }

    private static final class TestEventExecutorGroup extends MultithreadEventExecutorGroup {
        private static final Object[] ARGS = new Object[0];

        TestEventExecutorGroup(int minThreads, int maxThreads, long checkPeriod, TimeUnit unit) {
            super(minThreads, maxThreads, checkPeriod, unit, 0.4, 0.6,
                  maxThreads, maxThreads, 2,
                  new ThreadPerTaskExecutor(Executors.defaultThreadFactory()),
                  DefaultEventExecutorChooserFactory.INSTANCE, ARGS);
        }

        @Override
        protected EventExecutor newChild(Executor executor, Object... args) {
            return new TestEventExecutor(this, executor);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testScaleDown() throws InterruptedException {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 3, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            assertEquals(3, countActiveExecutors(group));
            Thread.sleep(200);

            // The monitor should have suspended 2 executors, leaving 1 active.
            assertEquals(1, countActiveExecutors(group));
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testScaleUp() throws InterruptedException {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 3, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            Thread.sleep(200);
            assertEquals(1, countActiveExecutors(group));

            TestEventExecutor activeExecutor = null;
            for (EventExecutor exec : group) {
                if (!exec.isSuspended()) {
                    activeExecutor = (TestEventExecutor) exec;
                    break;
                }
            }
            if (activeExecutor == null) {
                fail("Could not find an active executor to stress.");
            }

            activeExecutor.setHighLoad(true);

            // The monitor will see high utilization on the active thread. After 2 cycles (100 ms),
            // it will decide to scale up.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (countActiveExecutors(group) < 2 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(2, countActiveExecutors(group),
                         "Should scale up to 2 after stressing one executor.");

            for (EventExecutor exec : group) {
                if (!exec.isSuspended()) {
                    ((TestEventExecutor) exec).setHighLoad(true);
                }
            }

            while (countActiveExecutors(group) < 3 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(3, countActiveExecutors(group),
                         "Should scale up to 3 after stressing two executors.");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testScaleDownDoesNotGoBelowMinThreads() throws InterruptedException {
        TestEventExecutorGroup group = new TestEventExecutorGroup(2, 4, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            Thread.sleep(200);
            assertEquals(2, countActiveExecutors(group), "Should not scale below minThreads");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testScaleUpDoesNotExceedMaxThreads() throws Exception {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 2, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            Thread.sleep(200); // Allow time for initial scale-down to minThreads
            assertEquals(1, countActiveExecutors(group));

            TestEventExecutor activeExecutor = null;
            for (EventExecutor exec : group) {
                if (!exec.isSuspended()) {
                    activeExecutor = (TestEventExecutor) exec;
                    break;
                }
            }
            if (activeExecutor == null) {
                fail("Could not find an active executor to stress.");
            }
            activeExecutor.setHighLoad(true);

            // Wait for the UtilizationMonitor to react and scale up.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (countActiveExecutors(group) < 2 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(2, countActiveExecutors(group), "Should scale up to maxThreads");

            // Now that we have scaled up, put all active executors under a high load
            // to prevent the new one from being scaled back down immediately.
            for (EventExecutor exec : group) {
                if (!exec.isSuspended()) {
                    ((TestEventExecutor) exec).setHighLoad(true);
                }
            }

            // Further calls to next() should not increase the count, and the group should
            // remain at its max size because both threads are now busy.
            group.next();
            Thread.sleep(200); // Give the monitor time to check again.

            assertEquals(2, countActiveExecutors(group),
                         "Should not scale back down while load is high");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testShutdownCancelsMonitor() throws Exception {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 2, 50, TimeUnit.MILLISECONDS);
        try {
            Field field = MultithreadEventExecutorGroup.class.getDeclaredField("utilizationMonitoringTask");
            field.setAccessible(true);
            Future<?> monitorTask = (Future<?>) field.get(group);

            assertFalse(monitorTask.isCancelled());
            group.shutdownGracefully().syncUninterruptibly();
            assertTrue(monitorTask.isCancelled(), "Shutdown should cancel the monitoring task");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to access internal field for testing", e);
        }
    }

    private static void startAllExecutors(MultithreadEventExecutorGroup group) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(group.executorCount());
        for (EventExecutor executor : group) {
            executor.execute(startLatch::countDown);
        }
        startLatch.await();
    }

    private static int countActiveExecutors(MultithreadEventExecutorGroup group) {
        int activeCount = 0;
        for (EventExecutor executor : group) {
            if (!executor.isSuspended()) {
                activeCount++;
            }
        }
        return activeCount;
    }
}
