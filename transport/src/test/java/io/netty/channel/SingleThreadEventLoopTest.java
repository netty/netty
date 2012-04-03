package io.netty.channel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SingleThreadEventLoopTest {

    private SingleThreadEventLoopImpl loop;

    @Before
    public void newEventLoop() {
        loop = new SingleThreadEventLoopImpl();
    }

    @After
    public void stopEventLoop() {
        if (!loop.isShutdown()) {
            loop.shutdown();
        }
        while (!loop.isTerminated()) {
            try {
                loop.awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        assertEquals(1, loop.cleanedUp.get());
    }

    @Test
    public void shutdownBeforeStart() throws Exception {
        loop.shutdown();
    }

    @Test
    public void shutdownAfterStart() throws Exception {
        final AtomicBoolean interrupted = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(2);
        loop.execute(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
                while (latch.getCount() > 0) {
                    try {
                        latch.await();
                    } catch (InterruptedException ignored) {
                        interrupted.set(true);
                    }
                }
            }
        });

        // Wait for the event loop thread to start.
        while (latch.getCount() >= 2) {
            Thread.yield();
        }

        // Request the event loop thread to stop - it will call wakeup(false) to interrupt the thread.
        loop.shutdown();

        // Make the task terminate by itself.
        latch.countDown();

        // Wait until the event loop is terminated.
        while (!loop.isTerminated()) {
            loop.awaitTermination(1, TimeUnit.DAYS);
        }

        // Make sure loop.shutdown() above triggered wakeup().
        assertTrue(interrupted.get());
    }

    @Test
    public void shutdownWithPendingTasks() throws Exception {
        final int NUM_TASKS = 3;
        final AtomicInteger ranTasks = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                ranTasks.incrementAndGet();
                while (latch.getCount() > 0) {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        // Ignored
                    }
                }
            }
        };

        for (int i = 0; i < NUM_TASKS; i ++) {
            loop.execute(task);
        }

        // At this point, the first task should be running and stuck at latch.await().
        while (ranTasks.get() == 0) {
            Thread.yield();
        }
        assertEquals(1, ranTasks.get());

        // Shut down the event loop to test if the other tasks are run before termination.
        loop.shutdown();

        // Let the other tasks run.
        latch.countDown();

        // Wait until the event loop is terminated.
        while (!loop.isTerminated()) {
            loop.awaitTermination(1, TimeUnit.DAYS);
        }

        // Make sure loop.shutdown() above triggered wakeup().
        assertEquals(NUM_TASKS, ranTasks.get());
    }

    private static class SingleThreadEventLoopImpl extends SingleThreadEventLoop {

        final AtomicInteger cleanedUp = new AtomicInteger();

        @Override
        protected void run() {
            for (;;) {
                Runnable task;
                try {
                    task = takeTask();
                    task.run();
                } catch (InterruptedException e) {
                    // Waken up by interruptThread()
                }

                if (isShutdown() && peekTask() == null) {
                    break;
                }
            }
        }

        protected void cleanup() {
            cleanedUp.incrementAndGet();
        }

        @Override
        protected void wakeup(boolean inEventLoop) {
            if (!inEventLoop) {
                interruptThread();
            }
        }

        @Override
        public void attach(Channel channel, ChannelFuture future) {
            // Untested
        }
    }
}
