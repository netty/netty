package io.netty.util;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

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
        barrier.await(3, TimeUnit.SECONDS);
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
                assertTrue("Running as expected", true);
                barrier.countDown();
            }
        }, 2, TimeUnit.SECONDS);
        barrier.await(3, TimeUnit.SECONDS);
        assertTrue("timer should expire", timeout.isExpired());
        timer.stop();
    }
// testTimerShouldThrowExceptionAfterShutdownForNewTimeouts
    @Test
    public void testStopTimer() throws InterruptedException {
        final Timer timerProcessed = new HashedWheelTimer();
        for (int i = 0; i < 3; i ++) {
            timerProcessed.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    assertTrue("Should run normally", true);
                }
            }, 1, TimeUnit.MILLISECONDS);
        }
        Thread.sleep(1000L); // sleep for a second
        assertEquals("Number of unprocessed timeouts should be 0", 0, timerProcessed.stop().size());

        final Timer timerUnprocessed = new HashedWheelTimer();
        for (int i = 0; i < 5; i ++) {
            timerUnprocessed.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    assertTrue("Should run normally", true);
                }
            }, 5, TimeUnit.SECONDS);
        }
        Thread.sleep(1000L); // sleep for a second
        assertTrue("Number of unprocessed timeouts should be greater than 0", timerUnprocessed.stop().size() > 0);
    }

    @Test(expected = IllegalStateException.class)
    public void testTimerShouldThrowExceptionAfterShutdownForNewTimeouts() throws InterruptedException {
        final Timer timer = new HashedWheelTimer();
        for (int i = 0; i < 3; i ++) {
            timer.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    assertTrue("Should run normally", true);
                }
            }, 1, TimeUnit.MILLISECONDS);
        }

        timer.stop();
        Thread.sleep(1000L); // sleep for a second

        timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                fail("This should not run");
            }
        }, 1, TimeUnit.SECONDS);
    }
}
