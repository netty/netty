/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.netty.channel.local.LocalChannel;
import io.netty.util.concurrent.DefaultExecutorFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.PausableEventExecutor;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class SingleThreadEventLoopTest {

    private static final Runnable NOOP = new Runnable() {
        @Override
        public void run() { }
    };

    private static Executor executor;

    private SingleThreadEventLoopA loopA;
    private SingleThreadEventLoopB loopB;

    @BeforeClass
    public static void newExecutor() {
        executor = new DefaultExecutorFactory("SingleThreadEventLoopTest").newExecutor(2);
    }

    @Before
    public void newEventLoop() {
        loopA = new SingleThreadEventLoopA();
        loopB = new SingleThreadEventLoopB();
    }

    @After
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
    @SuppressWarnings("deprecation")
    public void shutdownBeforeStart() throws Exception {
        loopA.shutdown();
        assertRejection(loopA);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void shutdownAfterStart() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        loopA.execute(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        });

        // Wait for the event loop thread to start.
        latch.await();

        // Request the event loop thread to stop.
        loopA.shutdown();
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
        loopA.schedule(new Runnable() {
            @Override
            public void run() {
                endTime.set(System.nanoTime());
            }
        }, 500, TimeUnit.MILLISECONDS).get();
        assertTrue(endTime.get() - startTime >= TimeUnit.MILLISECONDS.toNanos(500));
    }

    @Test
    public void scheduleTaskAtFixedRateA() throws Exception {
        testScheduleTaskAtFixedRate(loopA);
    }

    @Test
    public void scheduleTaskAtFixedRateB() throws Exception {
        testScheduleTaskAtFixedRate(loopB);
    }

    private static void testScheduleTaskAtFixedRate(EventLoop loopA) throws InterruptedException {
        final Queue<Long> timestamps = new LinkedBlockingQueue<Long>();
        ScheduledFuture<?> f = loopA.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                timestamps.add(System.nanoTime());
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(550);
        assertTrue(f.cancel(true));
        assertEquals(5, timestamps.size());

        // Check if the task was run without lag.
        verifyTimestampDeltas(timestamps, 90);
    }

    @Test
    public void scheduleLaggyTaskAtFixedRateA() throws Exception {
        testScheduleLaggyTaskAtFixedRate(loopA);
    }

    @Test
    public void scheduleLaggyTaskAtFixedRateB() throws Exception {
        testScheduleLaggyTaskAtFixedRate(loopB);
    }

    private static void testScheduleLaggyTaskAtFixedRate(EventLoop loopA) throws InterruptedException {
        final Queue<Long> timestamps = new LinkedBlockingQueue<Long>();
        ScheduledFuture<?> f = loopA.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                boolean empty = timestamps.isEmpty();
                timestamps.add(System.nanoTime());
                if (empty) {
                    try {
                        Thread.sleep(401);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(550);
        assertTrue(f.cancel(true));
        assertEquals(5, timestamps.size());

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
                assertTrue(diff >= TimeUnit.MILLISECONDS.toNanos(400));
            } else {
                assertTrue(diff <= TimeUnit.MILLISECONDS.toNanos(10));
            }
            previousTimestamp = t;
            i ++;
        }
    }

    @Test
    public void scheduleTaskWithFixedDelayA() throws Exception {
        testScheduleTaskWithFixedDelay(loopA);
    }

    @Test
    public void scheduleTaskWithFixedDelayB() throws Exception {
        testScheduleTaskWithFixedDelay(loopB);
    }

    private static void testScheduleTaskWithFixedDelay(EventLoop loopA) throws InterruptedException {
        final Queue<Long> timestamps = new LinkedBlockingQueue<Long>();
        ScheduledFuture<?> f = loopA.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                timestamps.add(System.nanoTime());
                try {
                    Thread.sleep(51);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(500);
        assertTrue(f.cancel(true));
        assertEquals(3, timestamps.size());

        // Check if the task was run without lag.
        verifyTimestampDeltas(timestamps, TimeUnit.MILLISECONDS.toNanos(150));
    }

    @Test
    @SuppressWarnings("deprecation")
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
            loopA.execute(task);
        }

        // At this point, the first task should be running and stuck at latch.await().
        while (ranTasks.get() == 0) {
            Thread.yield();
        }
        assertEquals(1, ranTasks.get());

        // Shut down the event loop to test if the other tasks are run before termination.
        loopA.shutdown();

        // Let the other tasks run.
        latch.countDown();

        // Wait until the event loop is terminated.
        while (!loopA.isTerminated()) {
            loopA.awaitTermination(1, TimeUnit.DAYS);
        }

        // Make sure loop.shutdown() above triggered wakeup().
        assertEquals(NUM_TASKS, ranTasks.get());
    }

    @Test(timeout = 10000)
    @SuppressWarnings("deprecation")
    public void testRegistrationAfterShutdown() throws Exception {
        loopA.shutdown();

        // Disable logging temporarily.
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> appenders = new ArrayList<Appender<ILoggingEvent>>();
        for (Iterator<Appender<ILoggingEvent>> i = root.iteratorForAppenders(); i.hasNext();) {
            Appender<ILoggingEvent> a = i.next();
            appenders.add(a);
            root.detachAppender(a);
        }

        try {
            ChannelFuture f = loopA.register(new LocalChannel());
            f.awaitUninterruptibly();
            assertFalse(f.isSuccess());
            assertThat(f.cause(), is(instanceOf(RejectedExecutionException.class)));
            assertFalse(f.channel().isOpen());
        } finally {
            for (Appender<ILoggingEvent> a: appenders) {
                root.addAppender(a);
            }
        }
    }

    @Test(timeout = 10000)
    @SuppressWarnings("deprecation")
    public void testRegistrationAfterShutdown2() throws Exception {
        loopA.shutdown();
        final CountDownLatch latch = new CountDownLatch(1);
        Channel ch = new LocalChannel();
        ChannelPromise promise = ch.newPromise();
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                latch.countDown();
            }
        });

        // Disable logging temporarily.
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> appenders = new ArrayList<Appender<ILoggingEvent>>();
        for (Iterator<Appender<ILoggingEvent>> i = root.iteratorForAppenders(); i.hasNext();) {
            Appender<ILoggingEvent> a = i.next();
            appenders.add(a);
            root.detachAppender(a);
        }

        try {
            ChannelFuture f = loopA.register(ch, promise);
            f.awaitUninterruptibly();
            assertFalse(f.isSuccess());
            assertThat(f.cause(), is(instanceOf(RejectedExecutionException.class)));

            // Ensure the listener was notified.
            assertFalse(latch.await(1, TimeUnit.SECONDS));
            assertFalse(ch.isOpen());
        } finally {
            for (Appender<ILoggingEvent> a: appenders) {
                root.addAppender(a);
            }
        }
    }

    @Test(timeout = 5000)
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

        assertTrue(System.nanoTime() - startTime >= TimeUnit.SECONDS.toNanos(1));
    }

    @Test(timeout = 5000)
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

    @Test(timeout = 10000)
    public void testScheduledTaskWakeupAfterDeregistration() throws Exception {
        int numtasks = 5;

        final List<Queue<Long>> timestampsPerTask = new ArrayList<Queue<Long>>(numtasks);
        final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<ScheduledFuture<?>>(numtasks);

        // start the eventloops
        loopA.execute(NOOP);
        loopB.execute(NOOP);

        LocalChannel channel = new LocalChannel();
        ChannelPromise registerPromise = channel.newPromise();
        channel.unsafe().register(loopA, registerPromise);
        registerPromise.sync();

        for (int i = 0; i < numtasks; i++) {
            Queue<Long> timestamps = new LinkedBlockingQueue<Long>();
            timestampsPerTask.add(timestamps);
            scheduledFutures.add(channel.eventLoop()
                    .scheduleAtFixedRate(new TimestampsRunnable(timestamps), 0, 100, TimeUnit.MILLISECONDS));
        }

        // Each task should be executed every 100ms.
        // Will give them additional 50ms to execute ten times.
        Thread.sleep(50 + 10 * 100);

        // Deregister must stop future execution of scheduled tasks.
        assertTrue(channel.deregister().sync().isSuccess());

        for (Queue<Long> timestamps : timestampsPerTask) {
            assertTrue(timestamps.size() >= 10);
            verifyTimestampDeltas(timestamps, TimeUnit.MICROSECONDS.toNanos(90));
            timestamps.clear();
        }

        // Because the Channel is deregistered no task must have executed since then.
        Thread.sleep(200);
        for (Queue<Long> timestamps : timestampsPerTask) {
            assertTrue(timestamps.isEmpty());
        }

        registerPromise = channel.newPromise();
        channel.unsafe().register(loopB, registerPromise);
        registerPromise.sync();

        // After the channel was registered with another eventloop the scheduled tasks should start executing again.
        // Same as above.
        Thread.sleep(50 + 10 * 100);

        // Cancel all scheduled tasks.
        for (ScheduledFuture<?> f : scheduledFutures) {
            assertTrue(f.cancel(true));
        }

        for (Queue<Long> timestamps : timestampsPerTask) {
            assertTrue(timestamps.size() >= 10);
            verifyTimestampDeltas(timestamps, TimeUnit.MICROSECONDS.toNanos(90));
        }
    }

    /**
     * Runnable that adds the current nano time to a list whenever
     * it's executed.
     */
    private static class TimestampsRunnable implements Runnable {

        private final Queue<Long> timestamps;

        TimestampsRunnable(Queue<Long> timestamps) {
            assertNotNull(timestamps);
            this.timestamps = timestamps;
        }

        @Override
        public void run() {
            timestamps.add(System.nanoTime());
        }
    }

    @Test(timeout = 10000)
    public void testDeregisterRegisterMultipleTimesWithTasks() throws Exception {
        final Queue<Long> timestamps = new LinkedBlockingQueue<Long>();
        // need a final counter, so it can be used in an inner class.
        final AtomicInteger i = new AtomicInteger(-1);

        // start the eventloops
        loopA.execute(NOOP);
        loopB.execute(NOOP);

        LocalChannel channel = new LocalChannel();
        boolean firstRun = true;
        ScheduledFuture<?> f = null;
        while (i.incrementAndGet() < 4) {
            ChannelPromise registerPromise = channel.newPromise();
            channel.unsafe().register(i.intValue() % 2 == 0 ? loopA : loopB, registerPromise);
            registerPromise.sync();

            if (firstRun) {
                f = channel.eventLoop().scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        assertTrue((i.intValue() % 2 == 0 ? loopA : loopB).inEventLoop());
                        timestamps.add(System.nanoTime());
                    }
                }, 0, 100, TimeUnit.MILLISECONDS);
                firstRun = false;
            }

            Thread.sleep(250);

            assertTrue(channel.deregister().sync().isSuccess());

            assertTrue("was " + timestamps.size(), timestamps.size() >= 2);
            verifyTimestampDeltas(timestamps, TimeUnit.MILLISECONDS.toNanos(90));
            timestamps.clear();
        }

        // cancel while the channel is deregistered
        assertFalse(channel.isRegistered());
        assertTrue(f.cancel(true));
        assertTrue(timestamps.isEmpty());

        // register again and check that it's not executed again.
        ChannelPromise registerPromise = channel.newPromise();
        channel.unsafe().register(loopA, registerPromise);
        registerPromise.sync();

        Thread.sleep(200);

        assertTrue(timestamps.isEmpty());
    }

    @Test(timeout = 10000)
    public void testDeregisterWithScheduleWithFixedDelayTask() throws Exception {
        testDeregisterWithPeriodicScheduleTask(PeriodicScheduleMethod.FIXED_DELAY);
    }

    @Test(timeout = 10000)
    public void testDeregisterWithScheduleAtFixedRateTask() throws Exception {
        testDeregisterWithPeriodicScheduleTask(PeriodicScheduleMethod.FIXED_RATE);
    }

    private void testDeregisterWithPeriodicScheduleTask(PeriodicScheduleMethod method) throws Exception {
        final Queue<Long> timestamps = new LinkedBlockingQueue<Long>();

        // start the eventloops
        loopA.execute(NOOP);
        loopB.execute(NOOP);

        LocalChannel channel = new LocalChannel();
        ChannelPromise registerPromise = channel.newPromise();
        channel.unsafe().register(loopA, registerPromise);
        registerPromise.sync();

        assertThat(channel.eventLoop(), instanceOf(PausableEventExecutor.class));
        assertSame(loopA, channel.eventLoop().unwrap());

        ScheduledFuture<?> scheduleFuture;
        if (PeriodicScheduleMethod.FIXED_RATE == method) {
            scheduleFuture = channel.eventLoop().scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    assertTrue(loopB.inEventLoop());
                    timestamps.add(System.nanoTime());
                }
            }, 100, 200, TimeUnit.MILLISECONDS);
        } else {
            scheduleFuture = channel.eventLoop().scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    assertTrue(loopB.inEventLoop());
                    timestamps.add(System.nanoTime());
                }
            }, 100, 200, TimeUnit.MILLISECONDS);
        }

        assertTrue(((PausableEventExecutor) channel.eventLoop()).isAcceptingNewTasks());
        ChannelFuture deregisterFuture = channel.deregister();
        assertFalse(((PausableEventExecutor) channel.eventLoop()).isAcceptingNewTasks());

        assertTrue(deregisterFuture.sync().isSuccess());

        timestamps.clear();
        Thread.sleep(1000);

        // no scheduled tasks must be executed after deregistration.
        assertTrue("size: " + timestamps.size(), timestamps.isEmpty());

        assertFalse(((PausableEventExecutor) channel.eventLoop()).isAcceptingNewTasks());
        registerPromise = channel.newPromise();
        channel.unsafe().register(loopB,  registerPromise);
        assertTrue(registerPromise.sync().isSuccess());
        assertTrue(((PausableEventExecutor) channel.eventLoop()).isAcceptingNewTasks());

        assertThat(channel.eventLoop(), instanceOf(PausableEventExecutor.class));
        assertSame(loopB, channel.eventLoop().unwrap());

        // 100ms internal delay + 1 second. Should be able to execute 5 tasks in that time.
        Thread.sleep(1150);
        assertTrue(scheduleFuture.cancel(true));

        assertTrue("was " + timestamps.size(), timestamps.size() >= 5);
        verifyTimestampDeltas(timestamps, TimeUnit.MILLISECONDS.toNanos(190));
    }

    private enum PeriodicScheduleMethod {
        FIXED_RATE, FIXED_DELAY
    }

    private static void verifyTimestampDeltas(Queue<Long> timestamps, long minDelta) {
        assertFalse(timestamps.isEmpty());
        long prev = timestamps.poll();
        for (Long timestamp : timestamps) {
            long delta = timestamp - prev;
            assertTrue(String.format("delta: %d, minDelta: %d", delta, minDelta), delta >= minDelta);
            prev = timestamp;
        }
    }

    @Test(timeout = 10000)
    public void testDeregisterWithScheduledTask() throws Exception {
        final AtomicBoolean oneTimeScheduledTaskExecuted = new AtomicBoolean(false);

        // start the eventloops
        loopA.execute(NOOP);
        loopB.execute(NOOP);

        LocalChannel channel = new LocalChannel();
        ChannelPromise registerPromise = channel.newPromise();
        channel.unsafe().register(loopA, registerPromise);
        registerPromise.sync();

        assertThat(channel.eventLoop(), instanceOf(PausableEventExecutor.class));
        assertSame(loopA, ((PausableEventExecutor) channel.eventLoop()).unwrap());

        io.netty.util.concurrent.ScheduledFuture<?> scheduleFuture = channel.eventLoop().schedule(new Runnable() {
            @Override
            public void run() {
                oneTimeScheduledTaskExecuted.set(true);
                assertTrue(loopB.inEventLoop());
            }
        }, 1, TimeUnit.SECONDS);

        assertTrue(((PausableEventExecutor) channel.eventLoop()).isAcceptingNewTasks());
        ChannelFuture deregisterFuture = channel.deregister();
        assertFalse(((PausableEventExecutor) channel.eventLoop()).isAcceptingNewTasks());

        assertTrue(deregisterFuture.sync().isSuccess());

        Thread.sleep(1000);

        registerPromise = channel.newPromise();
        assertFalse(oneTimeScheduledTaskExecuted.get());
        channel.unsafe().register(loopB, registerPromise);
        registerPromise.sync();

        assertThat(channel.eventLoop(), instanceOf(PausableEventExecutor.class));
        assertSame(loopB, ((PausableEventExecutor) channel.eventLoop()).unwrap());

        assertTrue(scheduleFuture.sync().isSuccess());
        assertTrue(oneTimeScheduledTaskExecuted.get());
    }

    private static class SingleThreadEventLoopA extends SingleThreadEventLoop {

        final AtomicInteger cleanedUp = new AtomicInteger();

        SingleThreadEventLoopA() {
            super(null, executor, true);
        }

        @Override
        protected void run() {
            Runnable task = takeTask();
            if (task != null) {
                task.run();
                updateLastExecutionTime();
            }

            if (confirmShutdown()) {
                cleanupAndTerminate(true);
            } else {
                scheduleExecution();
            }
        }

        @Override
        protected void cleanup() {
            cleanedUp.incrementAndGet();
        }
    }

    private static class SingleThreadEventLoopB extends SingleThreadEventLoop {

        private volatile Thread thread;
        private volatile boolean interrupted;

        SingleThreadEventLoopB() {
            super(null, executor, false);
        }

        @Override
        protected void run() {
            thread = Thread.currentThread();

            if (interrupted) {
                thread.interrupt();
                interrupted = false;
            }

            // We use LockSupport.parkNanos() and NOT Thread.sleep() to eliminate the overhead of creating a new
            // InterruptedException on each wakeup(true) call. This is needed for various reasons:
            //  - Throwable.fillInStackTrace() is expensive
            //  - GC pressure
            // See https://github.com/netty/netty/issues/2841
            //
            // This may wake-up spuriously but we not care and just move on.
            LockSupport.parkNanos(delayNanos(System.nanoTime()));

            // Clear interruption state if it was interrupted by wakeup(true)
            Thread.interrupted();

            runAllTasks();

            if (confirmShutdown()) {
                cleanupAndTerminate(true);
            } else {
                scheduleExecution();
            }
        }

        @Override
        protected void wakeup(boolean inEventLoop) {
            if (thread == null) {
                interrupted = true;
            } else {
                thread.interrupt();
            }
        }
    }
}
