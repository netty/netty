/*
 * Copyright 2013 The Netty Project
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
package io.netty.util;

import com.google.common.base.Supplier;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        assertFalse("timer should not expire", timeout.isExpired());
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
        assertTrue("timer should expire", timeout.isExpired());
        timer.stop();
    }

    @Test
    public void testStopTimer() throws InterruptedException {
        final Timer timerProcessed = new HashedWheelTimer();
        for (int i = 0; i < 3; i ++) {
            timerProcessed.newTimeout(createNoOpTimerTask(), 1, TimeUnit.MILLISECONDS);
        }
        Thread.sleep(1000L); // sleep for a second
        assertEquals("Number of unprocessed timeouts should be 0", 0, timerProcessed.stop().size());

        final Timer timerUnprocessed = new HashedWheelTimer();
        for (int i = 0; i < 5; i ++) {
            timerUnprocessed.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                }
            }, 5, TimeUnit.SECONDS);
        }
        Thread.sleep(1000L); // sleep for a second
        assertFalse("Number of unprocessed timeouts should be greater than 0", timerUnprocessed.stop().isEmpty());
    }

    @Test
    public void testTimerShouldThrowExceptionAfterShutdownForNewTimeouts() throws InterruptedException {
        final Timer timer = new HashedWheelTimer();
        for (int i = 0; i < 3; i ++) {
            timer.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                }
            }, 1, TimeUnit.MILLISECONDS);
        }

        timer.stop();

        waitUntilTrue(1000, new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                try {
                    timer.newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            fail("This should not run");
                        }
                    }, 1, TimeUnit.SECONDS);
                    return false;
                } catch (IllegalStateException e) {
                    return true;
                }
            }
        });
    }

    @Test
    public void testTimerOverflowWheelLength() throws InterruptedException {
        final HashedWheelTimer timer = new HashedWheelTimer(
            Executors.defaultThreadFactory(), 100, TimeUnit.MILLISECONDS, 32);
        final AtomicInteger counter = new AtomicInteger();

        timer.newTimeout(new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
                counter.incrementAndGet();
                timer.newTimeout(this, 1, TimeUnit.SECONDS);
            }
        }, 1, TimeUnit.SECONDS);
        waitUntilTrue(3500, new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return 3 == counter.get();
            }
        });
        timer.stop();
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
            assertTrue("Timeout + " + scheduledTasks + " delay " + delay + " must be " + timeout + " < " + maxTimeout,
                delay >= timeout && delay < maxTimeout);
        }

        timer.stop();
    }

    @Test
    public void testRejectedExecutionExceptionWhenTooManyTimeoutsAreAddedBackToBack() {
        HashedWheelTimer timer = new HashedWheelTimer(Executors.defaultThreadFactory(), 100,
            TimeUnit.MILLISECONDS, 32, true, 2);
        timer.newTimeout(createNoOpTimerTask(), 1, TimeUnit.SECONDS);
        timer.newTimeout(createNoOpTimerTask(), 1, TimeUnit.SECONDS);
        try {
            timer.newTimeout(createNoOpTimerTask(), 1, TimeUnit.SECONDS);
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
        final HashedWheelTimer timer = new HashedWheelTimer(Executors.defaultThreadFactory(), 100,
            TimeUnit.MILLISECONDS, 32, true, 2);
        timer.newTimeout(createNoOpTimerTask(), 1, TimeUnit.SECONDS);
        Timeout timeoutToCancel = timer.newTimeout(createNoOpTimerTask(), 1, TimeUnit.SECONDS);
        timeoutToCancel.cancel();
        waitUntilNewTimeoutCanBeAdded(200, timer, 1000);
    }

    @Test
    public void testNewTimeoutShouldStopThrowingRejectedExecutionExceptionWhenExistingTimeoutIsExecuted()
        throws InterruptedException {
        final HashedWheelTimer timer = new HashedWheelTimer(Executors.defaultThreadFactory(), 25,
            TimeUnit.MILLISECONDS, 4, true, 2);
        timer.newTimeout(createNoOpTimerTask(), 1, TimeUnit.SECONDS);
        timer.newTimeout(createNoOpTimerTask(), 90, TimeUnit.MILLISECONDS);
        waitUntilNewTimeoutCanBeAdded(200, timer, 200);
        timer.stop();
    }

    private static void waitUntilNewTimeoutCanBeAdded(final long timeoutMilli,
        final HashedWheelTimer timer, final long newTimeoutDelay) {
        waitUntilTrue(timeoutMilli, new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                try {
                    timer.newTimeout(createNoOpTimerTask(), newTimeoutDelay, TimeUnit.MILLISECONDS);
                    return true;
                } catch (RejectedExecutionException e) {
                    return false;
                }
            }
        });
    }

    private static void waitUntilTrue(long timeoutMilli, Supplier<Boolean> condition) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMilli) {
            if (condition.get()) {
                return;
            }
        }
        throw new AssertionError("Timed out waiting for condition to be true.");
    }

    private static TimerTask createNoOpTimerTask() {
        return new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
            }
        };
    }
}
