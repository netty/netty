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

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class MultiThreadIoEventLoopGroupTest {

    private static class TestIoHandle implements IoHandle {
        @Override
        public void handle(IoRegistration registration, IoEvent readyOps) {
        }

        @Override
        public void close() {
        }
    }

    private static class TestIoHandlerFactory implements IoHandlerFactory {
        @Override
        public IoHandler newHandler(ThreadAwareExecutor ioExecutor) {
            return new IoHandler() {
                @Override public int run(IoHandlerContext context) {
                    return 0;
                }

                @Override public void wakeup() {
                }

                @Override
                public IoRegistration register(IoHandle handle) {
                    return new IoRegistration() {
                        private final AtomicBoolean valid = new AtomicBoolean(true);
                        @Override public <T> T attachment() {
                            return (T) handle;
                        }
                        @Override public long submit(IoOps ops) {
                            return 0;
                        }
                        @Override public boolean isValid() {
                            return valid.get();
                        }
                        @Override public boolean cancel() {
                            return valid.compareAndSet(true, false);
                        }
                    };
                }
                @Override public boolean isCompatible(Class<? extends IoHandle> handleType) {
                    return true;
                }
            };
        }
    }

    private static class TestableIoEventLoop extends SingleThreadIoEventLoop {
        private final AtomicBoolean simulateWorkload = new AtomicBoolean(false);

        TestableIoEventLoop(IoEventLoopGroup parent, Executor executor, IoHandlerFactory ioHandlerFactory) {
            super(parent, executor, ioHandlerFactory);
        }

        public void setSimulateWorkload(boolean active) {
            simulateWorkload.set(active);
            if (active) {
                execute(() -> { /* NO-OP */ });
            }
        }

        @Override
        protected void run() {
            do {
                if (simulateWorkload.get()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    reportActiveIoTime(TimeUnit.MILLISECONDS.toNanos(100));
                } else {
                    Runnable task = takeTask();
                    if (task != null) {
                        safeExecute(task);
                    }
                }
            } while (!confirmShutdown() && !canSuspend());
        }

        @Override
        protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
            return new LinkedBlockingQueue<>(maxPendingTasks);
        }
    }

    private static class TestMultiThreadIoEventLoopGroup extends MultiThreadIoEventLoopGroup {
        TestMultiThreadIoEventLoopGroup(int minThreads, int maxThreads, long checkPeriod, TimeUnit unit) {
            super(minThreads, maxThreads, checkPeriod, unit,
                  0.4, 0.8,
                  maxThreads, 1,
                  1,
                  new TestIoHandlerFactory());
        }

        @Override
        protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
            return new TestableIoEventLoop(this, executor, ioHandlerFactory);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testScalingWithIoRegistrationLifecycle() throws InterruptedException {
        TestMultiThreadIoEventLoopGroup group = new TestMultiThreadIoEventLoopGroup(0, 2, 200, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            assertEquals(2, countActiveExecutors(group));

            waitForSuspension(group, 1, 2000);
            assertEquals(1, countActiveExecutors(group), "One executor should have been suspended");

            TestableIoEventLoop activeExecutor = (TestableIoEventLoop) findActiveExecutor(group);
            assertNotNull(activeExecutor, "One executor should remain active");

            IoRegistration registration = activeExecutor.register(new TestIoHandle()).syncUninterruptibly().getNow();
            activeExecutor.setSimulateWorkload(true);

            Thread.sleep(450);

            // The executor will not be suspended because its workload generates utilization.
            assertFalse(activeExecutor.isSuspended(), "Executor with active handle should not be suspended");

            activeExecutor.setSimulateWorkload(false);
            assertTrue(registration.cancel());

            waitForSuspension(group, 2, 2000);
            assertTrue(activeExecutor.isSuspended(),
                       "Executor should suspend after handle is cancelled and idle");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testShouldNotSuspendExecutorWithActiveRegistration() throws InterruptedException {
        TestMultiThreadIoEventLoopGroup group = new TestMultiThreadIoEventLoopGroup(0, 2, 200, TimeUnit.MILLISECONDS);
        try {
            startAllExecutors(group);
            waitForSuspension(group, 1, 2000);
            assertEquals(1, countActiveExecutors(group), "One executor should have been suspended");

            TestableIoEventLoop activeExecutor = (TestableIoEventLoop) findActiveExecutor(group);
            assertNotNull(activeExecutor, "One executor should remain active");

            // Register a handle with the active executor. This makes registeredChannels() return 1.
            IoRegistration registration = activeExecutor.register(new TestIoHandle()).syncUninterruptibly().getNow();

            // The executor is now a candidate for suspension based on utilization,
            // but not based on registered channels.
            activeExecutor.setSimulateWorkload(false);

            // Wait for a few monitor cycles. The monitor will run and see the executor is idle.
            Thread.sleep(600);

            assertFalse(activeExecutor.isSuspended(),
                        "Executor with an active registration should not be suspended, even if idle");
            assertEquals(1, countActiveExecutors(group), "Only one executor should still be active");

            assertTrue(registration.cancel());
            waitForSuspension(group, 2, 2000);
            assertTrue(activeExecutor.isSuspended(),
                       "Executor should suspend after registration is cancelled and it remains idle");
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static void startAllExecutors(MultiThreadIoEventLoopGroup group) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(group.executorCount());
        for (EventExecutor executor : group) {
            executor.execute(latch::countDown);
        }
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Executors did not start in time");
    }

    private static int countActiveExecutors(MultiThreadIoEventLoopGroup group) {
        int activeCount = 0;
        for (EventExecutor executor : group) {
            if (!executor.isSuspended()) {
                activeCount++;
            }
        }
        return activeCount;
    }

    private static EventExecutor findActiveExecutor(MultiThreadIoEventLoopGroup group) {
        for (EventExecutor executor : group) {
            if (!executor.isSuspended()) {
                return executor;
            }
        }
        return null;
    }

    private static void waitForSuspension(MultiThreadIoEventLoopGroup group, int expectedSuspendedCount,
                                          long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (group.executorCount() - countActiveExecutors(group) >= expectedSuspendedCount) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for " + expectedSuspendedCount + " executor(s) to suspend.");
    }
}
