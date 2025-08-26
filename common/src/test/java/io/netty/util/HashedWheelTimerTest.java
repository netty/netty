/*
 * Copyright 2013 The Netty Project
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
package io.netty.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HashedWheelTimerTest {

    @Test
    public void testScheduleTimeoutShouldNotRunBeforeDelay() throws InterruptedException {
        final Timer timer = new HashedWheelTimer();
        final CountDownLatch barrier = new CountDownLatch(1);
        final Timeout timeout = timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                fail("This should not have run");
                barrier.countDown();
            }
        }, 10, TimeUnit.SECONDS);
        assertFalse(barrier.await(3, TimeUnit.SECONDS));
        assertFalse(timeout.isExpired(), "timer should not expire");
        timer.stop();
    }

    @Test
    public void testScheduleTimeoutShouldRunAfterDelay() throws InterruptedException {
        final Timer timer = new HashedWheelTimer();
        final CountDownLatch barrier = new CountDownLatch(1);
        final Timeout timeout = timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                barrier.countDown();
            }
        }, 2, TimeUnit.SECONDS);
        assertTrue(barrier.await(3, TimeUnit.SECONDS));
        assertTrue(timeout.isExpired(), "timer should expire");
        timer.stop();
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testStopTimer() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(3);
        final Timer timerProcessed = new HashedWheelTimer();
        for (int i = 0; i < 3; i ++) {
            timerProcessed.newTimeout(new TimerTask() {
                @Override
                public void run(final Timeout timeout) throws Exception {
                    latch.countDown();
                }
            }, 1, TimeUnit.MILLISECONDS);
        }

        latch.await();
        assertEquals(0, timerProcessed.stop().size(), "Number of unprocessed timeouts should be 0");

        final Timer timerUnprocessed = new HashedWheelTimer();
        for (int i = 0; i < 5; i ++) {
            timerUnprocessed.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                }
            }, 5, TimeUnit.SECONDS);
        }
        Thread.sleep(1000L); // sleep for a second
        assertFalse(timerUnprocessed.stop().isEmpty(), "Number of unprocessed timeouts should be greater than 0");
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testTimerShouldThrowExceptionAfterShutdownForNewTimeouts() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(3);
        final Timer timer = new HashedWheelTimer();
        for (int i = 0; i < 3; i ++) {
            timer.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    latch.countDown();
                }
            }, 1, TimeUnit.MILLISECONDS);
        }

        latch.await();
        timer.stop();

        try {
            timer.newTimeout(createNoOpTimerTask(), 1, TimeUnit.MILLISECONDS);
            fail("Expected exception didn't occur.");
        } catch (IllegalStateException ignored) {
            // expected
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testTimerOverflowWheelLength() throws InterruptedException {
        final HashedWheelTimer timer = new HashedWheelTimer(
            Executors.defaultThreadFactory(), 100, TimeUnit.MILLISECONDS, 32);
        final CountDownLatch latch = new CountDownLatch(3);

        timer.newTimeout(new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
                timer.newTimeout(this, 100, TimeUnit.MILLISECONDS);
                latch.countDown();
            }
        }, 100, TimeUnit.MILLISECONDS);

        latch.await();
        assertFalse(timer.stop().isEmpty());
    }

    @Test
    public void testExecutionOnTime() throws InterruptedException {
        int tickDuration = 200;
        int timeout = 125;
        int maxTimeout = 2 * (tickDuration + timeout);
        final HashedWheelTimer timer = new HashedWheelTimer(tickDuration, TimeUnit.MILLISECONDS);
        final BlockingQueue<Long> queue = new LinkedBlockingQueue<Long>();

        int scheduledTasks = 100000;
        for (int i = 0; i < scheduledTasks; i++) {
            final long start = System.nanoTime();
            timer.newTimeout(new TimerTask() {
                @Override
                public void run(final Timeout timeout) throws Exception {
                    queue.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                }
            }, timeout, TimeUnit.MILLISECONDS);
        }

        for (int i = 0; i < scheduledTasks; i++) {
            long delay = queue.take();
            assertTrue(delay >= timeout && delay < maxTimeout,
                "Timeout + " + scheduledTasks + " delay " + delay + " must be " + timeout + " < " + maxTimeout);
        }

        timer.stop();
    }

    @Test
    public void testExecutionOnTaskExecutor() throws InterruptedException {
        int timeout = 10;

        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch timeoutLatch = new CountDownLatch(1);
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                try {
                    command.run();
                } finally {
                    latch.countDown();
                }
            }
        };
        final HashedWheelTimer timer = new HashedWheelTimer(Executors.defaultThreadFactory(), 100,
                TimeUnit.MILLISECONDS, 32, true, 2, executor);
        timer.newTimeout(new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
                timeoutLatch.countDown();
            }
        }, timeout, TimeUnit.MILLISECONDS);

        latch.await();
        timeoutLatch.await();
        timer.stop();
    }

    @Test
    public void testRejectedExecutionExceptionWhenTooManyTimeoutsAreAddedBackToBack() {
        HashedWheelTimer timer = new HashedWheelTimer(Executors.defaultThreadFactory(), 100,
            TimeUnit.MILLISECONDS, 32, true, 2);
        timer.newTimeout(createNoOpTimerTask(), 5, TimeUnit.SECONDS);
        timer.newTimeout(createNoOpTimerTask(), 5, TimeUnit.SECONDS);
        try {
            timer.newTimeout(createNoOpTimerTask(), 1, TimeUnit.MILLISECONDS);
            fail("Timer allowed adding 3 timeouts when maxPendingTimeouts was 2");
        } catch (RejectedExecutionException e) {
            // Expected
        } finally {
            timer.stop();
        }
    }

    @Test
    public void testNewTimeoutShouldStopThrowingRejectedExecutionExceptionWhenExistingTimeoutIsCancelled()
        throws InterruptedException {
        final int tickDurationMs = 100;
        final HashedWheelTimer timer = new HashedWheelTimer(Executors.defaultThreadFactory(), tickDurationMs,
            TimeUnit.MILLISECONDS, 32, true, 2);
        timer.newTimeout(createNoOpTimerTask(), 5, TimeUnit.SECONDS);
        Timeout timeoutToCancel = timer.newTimeout(createNoOpTimerTask(), 5, TimeUnit.SECONDS);
        assertTrue(timeoutToCancel.cancel());

        Thread.sleep(tickDurationMs * 5);

        final CountDownLatch secondLatch = new CountDownLatch(1);
        timer.newTimeout(createCountDownLatchTimerTask(secondLatch), 90, TimeUnit.MILLISECONDS);

        secondLatch.await();
        timer.stop();
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testNewTimeoutShouldStopThrowingRejectedExecutionExceptionWhenExistingTimeoutIsExecuted()
        throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final HashedWheelTimer timer = new HashedWheelTimer(Executors.defaultThreadFactory(), 25,
            TimeUnit.MILLISECONDS, 4, true, 2);
        timer.newTimeout(createNoOpTimerTask(), 5, TimeUnit.SECONDS);
        timer.newTimeout(createCountDownLatchTimerTask(latch), 90, TimeUnit.MILLISECONDS);

        latch.await();

        final CountDownLatch secondLatch = new CountDownLatch(1);
        timer.newTimeout(createCountDownLatchTimerTask(secondLatch), 90, TimeUnit.MILLISECONDS);

        secondLatch.await();
        timer.stop();
    }

    @Test()
    public void reportPendingTimeouts() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final HashedWheelTimer timer = new HashedWheelTimer();
        final Timeout t1 = timer.newTimeout(createNoOpTimerTask(), 100, TimeUnit.MINUTES);
        final Timeout t2 = timer.newTimeout(createNoOpTimerTask(), 100, TimeUnit.MINUTES);
        timer.newTimeout(createCountDownLatchTimerTask(latch), 90, TimeUnit.MILLISECONDS);

        assertEquals(3, timer.pendingTimeouts());
        t1.cancel();
        t2.cancel();
        latch.await();

        assertEquals(0, timer.pendingTimeouts());
        timer.stop();
    }

    @Test
    public void testOverflow() throws InterruptedException  {
        final HashedWheelTimer timer = new HashedWheelTimer();
        final CountDownLatch latch = new CountDownLatch(1);
        Timeout timeout = timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                latch.countDown();
            }
        }, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        assertFalse(latch.await(1, TimeUnit.SECONDS));
        timeout.cancel();
        timer.stop();
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testStopTimerCancelsPendingTasks() throws InterruptedException {
        final Timer timerUnprocessed = new HashedWheelTimer();
        for (int i = 0; i < 5; i ++) {
            timerUnprocessed.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                }
            }, 5, TimeUnit.SECONDS);
        }
        Thread.sleep(1000L); // sleep for a second

        for (Timeout timeout : timerUnprocessed.stop()) {
            assertTrue(timeout.isCancelled(), "All unprocessed tasks should be canceled");
        }
    }

    @org.junit.jupiter.api.Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void cancelWillCallCallback() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final HashedWheelTimer timer = new HashedWheelTimer();
        final Timeout t1 = timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                fail();
            }

            @Override
            public void cancelled(Timeout timeout) {
                latch.countDown();
            }
        }, 90, TimeUnit.MILLISECONDS);

        assertEquals(1, timer.pendingTimeouts());
        t1.cancel();
        latch.await();
    }

    @Test
    public void testPendingTimeoutsShouldBeCountedCorrectlyWhenTimeoutCancelledWithinGoalTick()
        throws InterruptedException {
        final HashedWheelTimer timer = new HashedWheelTimer();
        final CountDownLatch barrier = new CountDownLatch(1);
        // A total of 11 timeouts with the same delay are submitted, and they will be processed in the same tick.
        timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                barrier.countDown();
                Thread.sleep(1000);
            }
        }, 200, TimeUnit.MILLISECONDS);
        List<Timeout> timeouts = new ArrayList<Timeout>();
        for (int i = 0; i < 10; i++) {
            timeouts.add(timer.newTimeout(createNoOpTimerTask(), 200, TimeUnit.MILLISECONDS));
        }
        barrier.await();
        // The simulation here is that the timeout has been transferred to a bucket and is canceled before it is
        // actually expired in the goal tick.
        for (Timeout timeout : timeouts) {
            timeout.cancel();
        }
        Thread.sleep(2000);
        assertEquals(0, timer.pendingTimeouts());
        timer.stop();
    }

    private static TimerTask createNoOpTimerTask() {
        return new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
            }
        };
    }

    private static TimerTask createCountDownLatchTimerTask(final CountDownLatch latch) {
        return new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
                latch.countDown();
            }
        };
    }
}
