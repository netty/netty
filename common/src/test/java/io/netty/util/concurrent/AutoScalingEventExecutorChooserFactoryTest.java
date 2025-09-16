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

import io.netty.util.concurrent.AutoScalingEventExecutorChooserFactory.AutoScalingUtilizationMetric;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AutoScalingEventExecutorChooserFactoryTest {

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
                        updateLastExecutionTime();
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
            super(maxThreads,
                  new ThreadPerTaskExecutor(Executors.defaultThreadFactory()),
                  new AutoScalingEventExecutorChooserFactory(
                          minThreads, maxThreads, checkPeriod, unit, 0.4, 0.6,
                          maxThreads, maxThreads, 2),
                  ARGS);
        }

        @Override
        protected EventExecutor newChild(Executor executor, Object... args) {
            return new TestEventExecutor(this, executor);
        }
    }

    @Test
    @Timeout(30)
    void testScaleDown() throws InterruptedException {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 3, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            assertEquals(3, group.activeExecutorCount());
            Thread.sleep(200);

            // The monitor should have suspended 2 executors, leaving 1 active.
            assertEquals(1, group.activeExecutorCount());
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @RepeatedTest(6)
    @Timeout(30)
    void testScaleUp() throws Exception {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 3, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            waitForActiveExecutorCount(group, 1, 5, TimeUnit.SECONDS);
            assertEquals(1, group.activeExecutorCount());

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

            // The monitor will see high utilization on the active thread.
            // After 2 cycles (100 ms), it will decide to scale up.
            waitForActiveExecutorCount(group, 2, 5, TimeUnit.SECONDS);
            assertEquals(2, group.activeExecutorCount(),
                         "Should scale up to 2 after stressing one executor.");

            for (EventExecutor exec : group) {
                if (!exec.isSuspended()) {
                    ((TestEventExecutor) exec).setHighLoad(true);
                }
            }

            waitForActiveExecutorCount(group, 3, 5, TimeUnit.SECONDS);
            assertEquals(3, group.activeExecutorCount(),
                         "Should scale up to 3 after stressing two executors.");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(30)
    void testScaleDownDoesNotGoBelowMinThreads() throws InterruptedException {
        TestEventExecutorGroup group = new TestEventExecutorGroup(2, 4, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            Thread.sleep(200);
            assertEquals(2, group.activeExecutorCount(), "Should not scale below minThreads");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(30)
    void testScaleUpDoesNotExceedMaxThreads() throws Exception {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 2, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            Thread.sleep(200); // Allow time for initial scale-down to minThreads
            assertEquals(1, group.activeExecutorCount());

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
            while (group.activeExecutorCount() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(2, group.activeExecutorCount(), "Should scale up to maxThreads");

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

            assertEquals(2, group.activeExecutorCount(),
                         "Should not scale back down while load is high");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(30)
    void testSmarterPickingConsolidatesWorkOnActiveExecutor() throws InterruptedException {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 3, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (group.activeExecutorCount() > 1 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(1, group.activeExecutorCount(),
                         "Group should scale down to 1 active executor");

            // Simulate a slow trickle of new work (e.g., new connections) by calling next() a few times.
            for (int i = 0; i < 5; i++) {
                group.next().execute(() -> { });
                Thread.sleep(20);
            }

            assertEquals(1, group.activeExecutorCount(),
                         "Should consolidate the trickle of work onto the single active executor, without" +
                         " waking up the suspended ones");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(30)
    void testMetricsProvideCorrectUtilizationAndActiveExecutorCount() throws InterruptedException {
        TestEventExecutorGroup group = new TestEventExecutorGroup(1, 3, 50, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (group.activeExecutorCount() > 1 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(1, group.activeExecutorCount(), "Should have scaled down to 1 active executor.");

            TestEventExecutor activeExecutor = null;
            for (EventExecutor exec : group) {
                if (!exec.isSuspended()) {
                    activeExecutor = (TestEventExecutor) exec;
                    break;
                }
            }
            if (activeExecutor == null) {
                fail("Could not find an active executor.");
            }

            activeExecutor.setHighLoad(true);

            while (System.nanoTime() < deadline) {
                List<AutoScalingUtilizationMetric> utilizationMetrics = group.executorUtilizations();
                TestEventExecutor finalActiveExecutor = activeExecutor;
                double utilization = utilizationMetrics.stream()
                                                       .filter(metric -> metric.executor().equals(finalActiveExecutor))
                                                       .findFirst()
                        .map(AutoScalingUtilizationMetric::utilization)
                        .orElse(0.0);
                if (utilization > 0.4) {
                    break;
                }
                Thread.sleep(50);
            }

            assertEquals(1, group.activeExecutorCount(), "Active count should still be 1 before scaling up.");

            List<AutoScalingUtilizationMetric> utilizationMetrics = group.executorUtilizations();
            assertEquals(3, utilizationMetrics.size(), "Utilization list should report on all executors.");

            TestEventExecutor finalActiveExecutor2 = activeExecutor;
            double activeUtilization = utilizationMetrics.stream()
                                                         .filter(metric -> metric.executor()
                                                                                 .equals(finalActiveExecutor2))
                                                         .findFirst()
                                                         .map(AutoScalingUtilizationMetric::utilization)
                                                         .orElse(0.0);
            assertTrue(activeUtilization > 0.4,
                       "Active executor should have utilization above the scale-down threshold. " +
                       "Was: " + activeUtilization);

            TestEventExecutor finalActiveExecutor1 = activeExecutor;
            utilizationMetrics.stream()
                              .filter(metric -> metric.executor() != finalActiveExecutor1)
                              .forEach(metric -> {
                                  assertTrue(metric.executor().isSuspended(), "Other executors should be suspended.");
                                  assertEquals(0.0, metric.utilization(),
                                               "Suspended executor should have 0.0 utilization.");
                              });
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static void startAllExecutors(MultithreadEventExecutorGroup group) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(group.executorCount());
        for (EventExecutor executor : group) {
            executor.execute(startLatch::countDown);
        }
        startLatch.await();
    }

    private static void waitForActiveExecutorCount(TestEventExecutorGroup group, int expectedCount, long timeout,
                                                   TimeUnit unit) throws InterruptedException, TimeoutException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (group.activeExecutorCount() == expectedCount) {
                return;
            }
            Thread.sleep(10);
        }
        throw new TimeoutException("Timed out waiting for active executor count to become " + expectedCount +
                                   ". Final count was " + group.activeExecutorCount());
    }
}
