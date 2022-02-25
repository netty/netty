/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.netty5.channel.local.LocalChannel;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SingleThreadEventLoopTest {

    private static final Runnable NOOP = () -> { };

    private SingleThreadEventLoopA loopA;
    private SingleThreadEventLoopB loopB;

    @BeforeEach
    public void newEventLoop() {
        loopA = new SingleThreadEventLoopA();
        loopB = new SingleThreadEventLoopB();
    }

    @AfterEach
    public void stopEventLoop() {
        if (!loopA.isShuttingDown()) {
            loopA.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
        if (!loopB.isShuttingDown()) {
            loopB.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }

        while (!loopA.isTerminated()) {
            try {
                loopA.awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        assertEquals(1, loopA.cleanedUp.get());

        while (!loopB.isTerminated()) {
            try {
                loopB.awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    @Test
    public void shutdownBeforeStart() throws Exception {
        loopA.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).await();
        assertRejection(loopA);
    }

    @Test
    public void shutdownAfterStart() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        loopA.execute(latch::countDown);

        // Wait for the event loop thread to start.
        latch.await();

        // Request the event loop thread to stop.
        loopA.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).await();
        assertRejection(loopA);

        assertTrue(loopA.isShutdown());

        // Wait until the event loop is terminated.
        while (!loopA.isTerminated()) {
            loopA.awaitTermination(1, TimeUnit.DAYS);
        }
    }

    private static void assertRejection(EventExecutor loop) {
        try {
            loop.execute(NOOP);
            fail("A task must be rejected after shutdown() is called.");
        } catch (RejectedExecutionException e) {
            // Expected
        }
    }

    @Test
    public void scheduleTaskA() throws Exception {
        testScheduleTask(loopA);
    }

    @Test
    public void scheduleTaskB() throws Exception {
        testScheduleTask(loopB);
    }

    private static void testScheduleTask(EventLoop loopA) throws InterruptedException, ExecutionException {
        long startTime = System.nanoTime();
        final AtomicLong endTime = new AtomicLong();
        loopA.schedule(() -> endTime.set(System.nanoTime()), 500, TimeUnit.MILLISECONDS).get();
        assertThat(endTime.get() - startTime,
                   is(greaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(500))));
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void scheduleTaskAtFixedRateA() throws Exception {
        testScheduleTaskAtFixedRate(loopA);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void scheduleTaskAtFixedRateB() throws Exception {
        testScheduleTaskAtFixedRate(loopB);
    }

    private static void testScheduleTaskAtFixedRate(EventLoop loopA) throws InterruptedException {
        final Queue<Long> timestamps = new LinkedBlockingQueue<>();
        final int expectedTimeStamps = 5;
        final CountDownLatch allTimeStampsLatch = new CountDownLatch(expectedTimeStamps);
        Future<?> f = loopA.scheduleAtFixedRate(() -> {
            timestamps.add(System.nanoTime());
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore
            }
            allTimeStampsLatch.countDown();
        }, 100, 100, TimeUnit.MILLISECONDS);
        allTimeStampsLatch.await();
        assertTrue(f.cancel());
        Thread.sleep(300);
        assertEquals(expectedTimeStamps, timestamps.size());

        // Check if the task was run without a lag.
        Long firstTimestamp = null;
        long cnt = 0;
        for (Long t: timestamps) {
            if (firstTimestamp == null) {
                firstTimestamp = t;
                continue;
            }

            long timepoint = t - firstTimestamp;
            assertThat(timepoint, is(greaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(100 * cnt + 80))));
            assertThat(timepoint, is(lessThan(TimeUnit.MILLISECONDS.toNanos(100 * (cnt + 1) + 20))));

            cnt ++;
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void scheduleLaggyTaskAtFixedRateA() throws Exception {
        testScheduleLaggyTaskAtFixedRate(loopA);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void scheduleLaggyTaskAtFixedRateB() throws Exception {
        testScheduleLaggyTaskAtFixedRate(loopB);
    }

    private static void testScheduleLaggyTaskAtFixedRate(EventLoop loopA) throws InterruptedException {
        final Queue<Long> timestamps = new LinkedBlockingQueue<>();
        final int expectedTimeStamps = 5;
        final CountDownLatch allTimeStampsLatch = new CountDownLatch(expectedTimeStamps);
        Future<?> f = loopA.scheduleAtFixedRate(() -> {
            boolean empty = timestamps.isEmpty();
            timestamps.add(System.nanoTime());
            if (empty) {
                try {
                    Thread.sleep(401);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            allTimeStampsLatch.countDown();
        }, 100, 100, TimeUnit.MILLISECONDS);
        allTimeStampsLatch.await();
        assertTrue(f.cancel());
        Thread.sleep(300);
        assertEquals(expectedTimeStamps, timestamps.size());

        // Check if the task was run with lag.
        int i = 0;
        Long previousTimestamp = null;
        for (Long t: timestamps) {
            if (previousTimestamp == null) {
                previousTimestamp = t;
                continue;
            }

            long diff = t.longValue() - previousTimestamp.longValue();
            if (i == 0) {
                assertThat(diff, is(greaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(400))));
            } else {
                assertThat(diff, is(lessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(10))));
            }
            previousTimestamp = t;
            i ++;
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void scheduleTaskWithFixedDelayA() throws Exception {
        testScheduleTaskWithFixedDelay(loopA);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void scheduleTaskWithFixedDelayB() throws Exception {
        testScheduleTaskWithFixedDelay(loopB);
    }

    private static void testScheduleTaskWithFixedDelay(EventLoop loopA) throws InterruptedException {
        final Queue<Long> timestamps = new LinkedBlockingQueue<>();
        final int expectedTimeStamps = 3;
        final CountDownLatch allTimeStampsLatch = new CountDownLatch(expectedTimeStamps);
        Future<?> f = loopA.scheduleWithFixedDelay(() -> {
            timestamps.add(System.nanoTime());
            try {
                Thread.sleep(51);
            } catch (InterruptedException e) {
                // Ignore
            }
            allTimeStampsLatch.countDown();
        }, 100, 100, TimeUnit.MILLISECONDS);
        allTimeStampsLatch.await();
        assertTrue(f.cancel());
        Thread.sleep(300);
        assertEquals(expectedTimeStamps, timestamps.size());

        // Check if the task was run without a lag.
        Long previousTimestamp = null;
        for (Long t: timestamps) {
            if (previousTimestamp == null) {
                previousTimestamp = t;
                continue;
            }

            assertThat(t.longValue() - previousTimestamp.longValue(),
                       is(greaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(150))));
            previousTimestamp = t;
        }
    }

    @Test
    public void shutdownWithPendingTasks() throws Exception {
        final int NUM_TASKS = 3;
        final AtomicInteger ranTasks = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        final Runnable task = () -> {
            ranTasks.incrementAndGet();
            while (latch.getCount() > 0) {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    // Ignored
                }
            }
        };

        for (int i = 0; i < NUM_TASKS; i ++) {
            loopA.execute(task);
        }

        // At this point, the first task should be running and stuck at latch.await().
        while (ranTasks.get() == 0) {
            Thread.yield();
        }
        assertEquals(1, ranTasks.get());

        // Shut down the event loop to test if the other tasks are run before termination.
        loopA.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);

        // Let the other tasks run.
        latch.countDown();

        // Wait until the event loop is terminated.
        while (!loopA.isTerminated()) {
            loopA.awaitTermination(1, TimeUnit.DAYS);
        }

        // Make sure loop.shutdown() above triggered wakeup().
        assertEquals(NUM_TASKS, ranTasks.get());
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testRegistrationAfterShutdown() throws Exception {
        loopA.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).await();

        // Disable logging temporarily.
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> i = root.iteratorForAppenders(); i.hasNext();) {
            Appender<ILoggingEvent> a = i.next();
            appenders.add(a);
            root.detachAppender(a);
        }

        try {
            Channel channel = new LocalChannel(loopA);
            Future<Void> f = channel.register();
            f.awaitUninterruptibly();
            assertFalse(f.isSuccess());
            assertThat(f.cause(), is(instanceOf(RejectedExecutionException.class)));
            // TODO: What to do in this case ?
            //assertFalse(f.channel().isOpen());
        } finally {
            for (Appender<ILoggingEvent> a: appenders) {
                root.addAppender(a);
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testRegistrationAfterShutdown2() throws Exception {
        loopA.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).await();
        final CountDownLatch latch = new CountDownLatch(1);
        Channel ch = new LocalChannel(loopA);

        // Disable logging temporarily.
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> i = root.iteratorForAppenders(); i.hasNext();) {
            Appender<ILoggingEvent> a = i.next();
            appenders.add(a);
            root.detachAppender(a);
        }

        try {
            Future<Void> f = ch.register().addListener(future -> latch.countDown());
            f.awaitUninterruptibly();
            assertFalse(f.isSuccess());
            assertThat(f.cause(), is(instanceOf(RejectedExecutionException.class)));

            // Ensure the listener was notified.
            assertFalse(latch.await(1, TimeUnit.SECONDS));
            // TODO: What to do in this case ?
            //assertFalse(ch.isOpen());
        } finally {
            for (Appender<ILoggingEvent> a: appenders) {
                root.addAppender(a);
            }
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testGracefulShutdownQuietPeriod() throws Exception {
        loopA.shutdownGracefully(1, Integer.MAX_VALUE, TimeUnit.SECONDS);
        // Keep Scheduling tasks for another 2 seconds.
        for (int i = 0; i < 20; i ++) {
            Thread.sleep(100);
            loopA.execute(NOOP);
        }

        long startTime = System.nanoTime();

        assertThat(loopA.isShuttingDown(), is(true));
        assertThat(loopA.isShutdown(), is(false));

        while (!loopA.isTerminated()) {
            loopA.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
        }

        assertThat(System.nanoTime() - startTime,
                   is(greaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(1))));
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testGracefulShutdownTimeout() throws Exception {
        loopA.shutdownGracefully(2, 2, TimeUnit.SECONDS);
        // Keep Scheduling tasks for another 3 seconds.
        // Submitted tasks must be rejected after 2 second timeout.
        for (int i = 0; i < 10; i ++) {
            Thread.sleep(100);
            loopA.execute(NOOP);
        }

        try {
            for (int i = 0; i < 20; i ++) {
                Thread.sleep(100);
                loopA.execute(NOOP);
            }
            fail("shutdownGracefully() must reject a task after timeout.");
        } catch (RejectedExecutionException e) {
            // Expected
        }

        assertThat(loopA.isShuttingDown(), is(true));
        assertThat(loopA.isShutdown(), is(true));
    }

    private static class SingleThreadEventLoopA extends SingleThreadEventExecutor implements EventLoop {

        final AtomicInteger cleanedUp = new AtomicInteger();

        SingleThreadEventLoopA() {
            super(Executors.defaultThreadFactory());
        }

        @Override
        public EventLoop next() {
            return (EventLoop) super.next();
        }

        @Override
        protected void run() {
            do {
                Runnable task = takeTask();
                if (task != null) {
                    task.run();
                    updateLastExecutionTime();
                }

            } while (!confirmShutdown());
        }

        @Override
        protected void cleanup() {
            cleanedUp.incrementAndGet();
        }

        @Override
        public Unsafe unsafe() {
            return null;
        }
    }

    private static class SingleThreadEventLoopB extends SingleThreadEventExecutor implements EventLoop {

        SingleThreadEventLoopB() {
            super(Executors.defaultThreadFactory());
        }

        @Override
        public EventLoop next() {
            return (EventLoop) super.next();
        }

        @Override
        protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
            return new ConcurrentLinkedQueue<Runnable>();
        }

        @Override
        protected void run() {
            do {
                try {
                    Thread.sleep(TimeUnit.NANOSECONDS.toMillis(delayNanos(System.nanoTime())));
                } catch (InterruptedException e) {
                    // Waken up by interruptThread()
                }

                runAllTasks(Integer.MAX_VALUE);

            } while (!confirmShutdown());
        }

        @Override
        protected void wakeup(boolean inEventLoop) {
            interruptThread();
        }

        @Override
        public Unsafe unsafe() {
            return new Unsafe() {
                @Override
                public void register(Channel channel)  {
                    // NOOP
                }

                @Override
                public void deregister(Channel channel) {
                    // NOOP
                }
            };
        }
    }
}
