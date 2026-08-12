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

package io.netty.util.concurrent;

import io.netty.util.Signal;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static java.lang.Math.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultPromiseTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultPromiseTest.class);
    private static int stackOverflowDepth;

    @BeforeAll
    public static void beforeClass() {
        try {
            findStackOverflowDepth();
            throw new IllegalStateException("Expected StackOverflowError but didn't get it?!");
        } catch (StackOverflowError e) {
            logger.debug("StackOverflowError depth: {}", stackOverflowDepth);
        }
    }

    @SuppressWarnings("InfiniteRecursion")
    private static void findStackOverflowDepth() {
        ++stackOverflowDepth;
        findStackOverflowDepth();
    }

    private static int stackOverflowTestDepth() {
        return max(stackOverflowDepth << 1, stackOverflowDepth);
    }

    private static class RejectingEventExecutor extends AbstractEventExecutor {
        @Override
        public boolean isShuttingDown() {
            return false;
        }

        @Override
        public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public Future<?> terminationFuture() {
            return null;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return fail("Cannot schedule commands");
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return fail("Cannot schedule commands");
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return fail("Cannot schedule commands");
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                         TimeUnit unit) {
            return fail("Cannot schedule commands");
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            fail("Cannot schedule commands");
        }
    }

    @Test
    public void testCancelDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = new RejectingEventExecutor();

        Promise<Void> promise = new DefaultPromise<Void>(executor);
        assertTrue(promise.cancel(false));
        assertTrue(promise.isCancelled());
    }

    @Test
    public void testSuccessDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = new RejectingEventExecutor();

        Object value = new Object();
        Promise<Object> promise = new DefaultPromise<Object>(executor);
        promise.setSuccess(value);
        assertSame(value, promise.getNow());
    }

    @Test
    public void testFailureDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = new RejectingEventExecutor();

        Exception cause = new Exception();
        Promise<Void> promise = new DefaultPromise<Void>(executor);
        promise.setFailure(cause);
        assertSame(cause, promise.cause());
    }

    @Test
    public void testCancellationExceptionIsThrownWhenBlockingGet() {
        final Promise<Void> promise = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
        assertTrue(promise.cancel(false));
        assertThrows(CancellationException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                promise.get();
            }
        });
    }

    @Test
    public void testCancellationExceptionIsThrownWhenBlockingGetWithTimeout() {
        final Promise<Void> promise = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
        assertTrue(promise.cancel(false));
        assertThrows(CancellationException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                promise.get(1, TimeUnit.SECONDS);
            }
        });
    }

    @Test
    public void testCancellationExceptionIsReturnedAsCause() {
        final Promise<Void> promise = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
        assertTrue(promise.cancel(false));
        assertThat(promise.cause()).isInstanceOf(CancellationException.class);
    }

    @Test
    public void testStackOverflowWithImmediateEventExecutorA() throws Exception {
        testStackOverFlowChainedFuturesA(stackOverflowTestDepth(), ImmediateEventExecutor.INSTANCE, true);
        testStackOverFlowChainedFuturesA(stackOverflowTestDepth(), ImmediateEventExecutor.INSTANCE, false);
    }

    @Test
    public void testNoStackOverflowWithDefaultEventExecutorA() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            EventExecutor executor = new DefaultEventExecutor(executorService);
            try {
                testStackOverFlowChainedFuturesA(stackOverflowTestDepth(), executor, true);
                testStackOverFlowChainedFuturesA(stackOverflowTestDepth(), executor, false);
            } finally {
                executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            }
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void testNoStackOverflowWithImmediateEventExecutorB() throws Exception {
        testStackOverFlowChainedFuturesB(stackOverflowTestDepth(), ImmediateEventExecutor.INSTANCE, true);
        testStackOverFlowChainedFuturesB(stackOverflowTestDepth(), ImmediateEventExecutor.INSTANCE, false);
    }

    @Test
    public void testNoStackOverflowWithDefaultEventExecutorB() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            EventExecutor executor = new DefaultEventExecutor(executorService);
            try {
                testStackOverFlowChainedFuturesB(stackOverflowTestDepth(), executor, true);
                testStackOverFlowChainedFuturesB(stackOverflowTestDepth(), executor, false);
            } finally {
                executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            }
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void testListenerNotifyOrder() throws Exception {
        EventExecutor executor = new TestEventExecutor();
        try {
            final BlockingQueue<FutureListener<Void>> listeners = new LinkedBlockingQueue<FutureListener<Void>>();
            int runs = 100000;

            for (int i = 0; i < runs; i++) {
                final Promise<Void> promise = new DefaultPromise<Void>(executor);
                final FutureListener<Void> listener1 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        listeners.add(this);
                    }
                };
                final FutureListener<Void> listener2 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        listeners.add(this);
                    }
                };
                final FutureListener<Void> listener4 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        listeners.add(this);
                    }
                };
                final FutureListener<Void> listener3 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        listeners.add(this);
                        future.addListener(listener4);
                    }
                };

                GlobalEventExecutor.INSTANCE.execute(new Runnable() {
                    @Override
                    public void run() {
                        promise.setSuccess(null);
                    }
                });

                promise.addListener(listener1).addListener(listener2).addListener(listener3);

                assertSame(listener1, listeners.take(), "Fail 1 during run " + i + " / " + runs);
                assertSame(listener2, listeners.take(), "Fail 2 during run " + i + " / " + runs);
                assertSame(listener3, listeners.take(), "Fail 3 during run " + i + " / " + runs);
                assertSame(listener4, listeners.take(), "Fail 4 during run " + i + " / " + runs);
                assertTrue(listeners.isEmpty(), "Fail during run " + i + " / " + runs);
            }
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    public void testListenerNotifyLater() throws Exception {
        // Testing first execution path in DefaultPromise
        testListenerNotifyLater(1);

        // Testing second execution path in DefaultPromise
        testListenerNotifyLater(2);
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testPromiseListenerAddWhenCompleteFailure() throws Exception {
        testPromiseListenerAddWhenComplete(fakeException());
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testPromiseListenerAddWhenCompleteSuccess() throws Exception {
        testPromiseListenerAddWhenComplete(null);
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testLateListenerIsOrderedCorrectlySuccess() throws InterruptedException {
        testLateListenerIsOrderedCorrectly(null);
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testLateListenerIsOrderedCorrectlyFailure() throws InterruptedException {
        testLateListenerIsOrderedCorrectly(fakeException());
    }

    @Test
    public void testSignalRace() {
        final long wait = TimeUnit.NANOSECONDS.convert(10, TimeUnit.SECONDS);
        EventExecutor executor = null;
        try {
            executor = new TestEventExecutor();

            final int numberOfAttempts = 4096;
            final Map<Thread, DefaultPromise<Void>> promises = new HashMap<Thread, DefaultPromise<Void>>();
            for (int i = 0; i < numberOfAttempts; i++) {
                final DefaultPromise<Void> promise = new DefaultPromise<Void>(executor);
                final Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        promise.setSuccess(null);
                    }
                });
                promises.put(thread, promise);
            }

            for (final Map.Entry<Thread, DefaultPromise<Void>> promise : promises.entrySet()) {
                promise.getKey().start();
                final long start = System.nanoTime();
                promise.getValue().awaitUninterruptibly(wait, TimeUnit.NANOSECONDS);
                assertThat(System.nanoTime() - start).isLessThan(wait);
            }
        } finally {
            if (executor != null) {
                executor.shutdownGracefully();
            }
        }
    }

    @Test
    @Timeout(value = 30)
    public void testAwaitIsNotifiedWhenCompletedConcurrently() throws Exception {
        // Unlike testSignalRace() this awaits without a timeout, so a lost wake-up makes this test hang instead of
        // just being slow.
        for (int i = 0; i < 4096; i++) {
            final Promise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
            Thread completer = startWaiter(() -> promise.setSuccess(null));
            promise.await();
            completer.join();
        }
    }

    @Test
    @Timeout(value = 30)
    public void testAwaitNotifiesAllWaiters() throws Exception {
        final int waiterCount = 8;
        final Promise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
        final CountDownLatch done = new CountDownLatch(waiterCount);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final AtomicInteger completedButNotSuccessful = new AtomicInteger();

        List<Thread> waiters = new ArrayList<Thread>(waiterCount);
        try {
            for (int i = 0; i < waiterCount; i++) {
                waiters.add(startWaiter(() -> {
                    try {
                        promise.await();
                        if (!promise.isSuccess()) {
                            completedButNotSuccessful.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        error.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                }));
            }

            for (Thread waiter : waiters) {
                awaitBlocked(waiter);
            }
            promise.setSuccess(null);

            assertTrue(done.await(10, TimeUnit.SECONDS), "Not all waiters were notified");
            assertNull(error.get());
            assertEquals(0, completedButNotSuccessful.get());
        } finally {
            release(promise, waiters);
        }
    }

    @Test
    @Timeout(value = 30)
    public void testCancelNotifiesWaiter() throws Exception {
        final Promise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

        Thread waiter = startWaiter(() -> {
            try {
                promise.await();
                assertTrue(promise.isCancelled());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            awaitBlocked(waiter);

            assertTrue(promise.cancel(false));
            assertTrue(done.await(10, TimeUnit.SECONDS), "The waiter was not notified");
            assertNull(error.get());
        } finally {
            release(promise, waiter);
        }
    }

    @Test
    @Timeout(value = 30)
    public void testAwaitThrowsWhenInterrupted() throws Exception {
        final Promise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final AtomicBoolean interruptedAfterThrow = new AtomicBoolean();

        Thread waiter = startWaiter(() -> {
            try {
                promise.await();
                error.set(new AssertionError("await() was expected to be interrupted"));
            } catch (InterruptedException e) {
                // Throwing InterruptedException must have cleared the interrupted status.
                interruptedAfterThrow.set(Thread.currentThread().isInterrupted());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            awaitBlocked(waiter);

            waiter.interrupt();
            assertTrue(done.await(10, TimeUnit.SECONDS), "The waiter was not interrupted");
            assertNull(error.get());
            assertFalse(interruptedAfterThrow.get(), "The interrupted status was not cleared");
            assertFalse(promise.isDone());
        } finally {
            release(promise, waiter);
        }
    }

    @Test
    @Timeout(value = 30)
    public void testAwaitUninterruptiblyKeepsWaitingWhenInterrupted() throws Exception {
        final Promise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final AtomicBoolean interruptedOnReturn = new AtomicBoolean();

        Thread waiter = startWaiter(() -> {
            try {
                promise.awaitUninterruptibly();
                interruptedOnReturn.set(Thread.currentThread().isInterrupted());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            awaitBlocked(waiter);

            waiter.interrupt();
            assertFalse(done.await(200, TimeUnit.MILLISECONDS), "awaitUninterruptibly() returned before completion");

            promise.setSuccess(null);
            assertTrue(done.await(10, TimeUnit.SECONDS), "The waiter was not notified");
            assertNull(error.get());
            assertTrue(interruptedOnReturn.get(), "The interrupted status was not restored");
        } finally {
            release(promise, waiter);
        }
    }

    @Test
    @Timeout(value = 30)
    public void testAwaitWithTimeoutDoesNotReturnEarly() throws Exception {
        final Promise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
        final long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(100);

        long startTime = System.nanoTime();
        assertFalse(promise.await(timeoutNanos, TimeUnit.NANOSECONDS));
        assertThat(System.nanoTime() - startTime).isGreaterThanOrEqualTo(timeoutNanos);
        assertFalse(promise.isDone());
    }

    @Test
    @Timeout(value = 60)
    public void testTimedAwaitReportsInterruptRatherThanTimeout() throws Exception {
        // An interrupt that arrives around the moment the timeout expires must still be reported as an
        // InterruptedException. Reporting it as a plain timeout instead would leave the interrupted status set and
        // make Future.get(long, TimeUnit) throw a TimeoutException for a thread that was in fact interrupted.
        //
        // The interrupt is aimed at the deadline itself, which is the window that used to be handled wrongly. It
        // cannot be aimed exactly: an interrupt that lands after await(...) has already returned, but before the
        // waiter reads its own interrupted status, looks just like the defect from the outside. Those stragglers are
        // rare, while the defect turns well over half of the rounds into a silent timeout, so the two are told apart
        // by how often it happens rather than by a single round.
        final int rounds = 300;
        final long timeoutMillis = 3;
        int timedOutWhileInterrupted = 0;

        for (int i = 0; i < rounds; i++) {
            final Promise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
            final CountDownLatch done = new CountDownLatch(1);
            final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
            final AtomicBoolean swallowedInterrupt = new AtomicBoolean();

            Thread waiter = startWaiter(() -> {
                try {
                    if (!promise.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                        swallowedInterrupt.set(Thread.currentThread().isInterrupted());
                    }
                } catch (InterruptedException e) {
                    // Expected whenever the interrupt landed before await(...) returned.
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    done.countDown();
                }
            });
            try {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
                waiter.interrupt();

                assertTrue(done.await(10, TimeUnit.SECONDS), "The waiter did not return");
                assertNull(error.get());
                if (swallowedInterrupt.get()) {
                    timedOutWhileInterrupted++;
                }
            } finally {
                release(promise, waiter);
            }
        }

        assertThat(timedOutWhileInterrupted)
                .describedAs("rounds where await(...) reported a timeout although the thread had been interrupted")
                .isLessThan(rounds / 4);
    }

    @Test
    @Timeout(value = 30)
    public void testTimedOutWaitersAreRemoved() throws Exception {
        final DefaultPromise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
        for (int i = 0; i < 128; i++) {
            assertFalse(promise.await(1, TimeUnit.MILLISECONDS));
        }
        assertNull(waitersOf(promise), "The nodes of the timed out waiters were not reclaimed");
    }

    @Test
    @Timeout(value = 60)
    public void testConcurrentlyTimedOutWaitersAreRemoved() throws Exception {
        // Several threads unlink their nodes while others walk the very same stack, which is the only way to reach
        // the restart branches of the removal. A live waiter takes part as well: if a concurrent removal ever drops
        // it off the stack, nothing wakes it up again and this test times out.
        final int timingOutWaiters = 6;
        for (int round = 0; round < 64; round++) {
            final DefaultPromise<Void> promise = new DefaultPromise<Void>(new RejectingEventExecutor());
            final CyclicBarrier started = new CyclicBarrier(timingOutWaiters + 1);
            final CountDownLatch timedOut = new CountDownLatch(timingOutWaiters);
            final CountDownLatch blockingDone = new CountDownLatch(1);
            final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

            List<Thread> waiters = new ArrayList<Thread>(timingOutWaiters + 1);
            try {
                waiters.add(startWaiter(() -> {
                    try {
                        started.await(10, TimeUnit.SECONDS);
                        promise.await();
                    } catch (Throwable t) {
                        error.compareAndSet(null, t);
                    } finally {
                        blockingDone.countDown();
                    }
                }));
                for (int i = 0; i < timingOutWaiters; i++) {
                    waiters.add(startWaiter(() -> {
                        try {
                            started.await(10, TimeUnit.SECONDS);
                            for (int j = 0; j < 8; j++) {
                                promise.await(1, TimeUnit.MILLISECONDS);
                            }
                        } catch (Throwable t) {
                            error.compareAndSet(null, t);
                        } finally {
                            timedOut.countDown();
                        }
                    }));
                }

                assertTrue(timedOut.await(30, TimeUnit.SECONDS), "The timing out waiters got stuck");
                assertNull(error.get());
                // Only the waiter that has no timeout may be left behind, every timed out node must be unlinked.
                assertThat(waiterCount(promise)).isLessThanOrEqualTo(1);

                promise.setSuccess(null);
                assertTrue(blockingDone.await(30, TimeUnit.SECONDS), "The waiter was dropped off the stack");
                assertNull(error.get());
                assertNull(waitersOf(promise), "The stack was not drained on completion");
            } finally {
                release(promise, waiters);
            }
        }
    }

    private static Thread startWaiter(Runnable body) {
        Thread thread = new Thread(body);
        // Never keep the JVM alive: an assertion that fires before the promise is completed would otherwise leave
        // these threads parked for the rest of the surefire fork.
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void release(Promise<Void> promise, Thread... waiters) throws InterruptedException {
        release(promise, Arrays.asList(waiters));
    }

    /**
     * Complete the promise no matter how the test ended, so no waiter stays blocked, and wait for them to notice.
     */
    private static void release(Promise<Void> promise, List<Thread> waiters) throws InterruptedException {
        promise.trySuccess(null);
        for (Thread waiter : waiters) {
            waiter.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private static Object waitersOf(DefaultPromise<?> promise) throws Exception {
        Field field = DefaultPromise.class.getDeclaredField("waiters");
        field.setAccessible(true);
        return field.get(promise);
    }

    private static int waiterCount(DefaultPromise<?> promise) throws Exception {
        Object node = waitersOf(promise);
        int count = 0;
        while (node != null) {
            count++;
            Field next = node.getClass().getDeclaredField("next");
            next.setAccessible(true);
            node = next.get(node);
        }
        return count;
    }

    /**
     * Wait until the given thread is blocked, so the promise is completed while the thread really is waiting for it
     * and not before it even started to.
     */
    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (;;) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            if (state == Thread.State.TERMINATED) {
                fail("The thread terminated before it started to wait");
            }
            if (System.nanoTime() - deadline >= 0) {
                fail("The thread did not start to wait within 10 seconds, last seen state: " + state);
            }
            Thread.sleep(1);
        }
    }

    @Test
    public void signalUncancellableCompletionValue() {
        final Promise<Signal> promise = new DefaultPromise<Signal>(ImmediateEventExecutor.INSTANCE);
        promise.setSuccess(Signal.valueOf(DefaultPromise.class, "UNCANCELLABLE"));
        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
    }

    @Test
    public void signalSuccessCompletionValue() {
        final Promise<Signal> promise = new DefaultPromise<Signal>(ImmediateEventExecutor.INSTANCE);
        promise.setSuccess(Signal.valueOf(DefaultPromise.class, "SUCCESS"));
        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
    }

    @Test
    public void setUncancellableGetNow() {
        final Promise<String> promise = new DefaultPromise<String>(ImmediateEventExecutor.INSTANCE);
        assertNull(promise.getNow());
        assertTrue(promise.setUncancellable());
        assertNull(promise.getNow());
        assertFalse(promise.isDone());
        assertFalse(promise.isSuccess());

        promise.setSuccess("success");

        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertEquals("success", promise.getNow());
    }

    private static void testStackOverFlowChainedFuturesA(int promiseChainLength, final EventExecutor executor,
                                                         boolean runTestInExecutorThread)
            throws InterruptedException {
        final Promise<Void>[] p = new DefaultPromise[promiseChainLength];
        final CountDownLatch latch = new CountDownLatch(promiseChainLength);

        if (runTestInExecutorThread) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    testStackOverFlowChainedFuturesA(executor, p, latch);
                }
            });
        } else {
            testStackOverFlowChainedFuturesA(executor, p, latch);
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < p.length; ++i) {
            assertTrue(p[i].isSuccess(), "index " + i);
        }
    }

    private static void testStackOverFlowChainedFuturesA(EventExecutor executor, final Promise<Void>[] p,
                                                         final CountDownLatch latch) {
        for (int i = 0; i < p.length; i ++) {
            final int finalI = i;
            p[i] = new DefaultPromise<Void>(executor);
            p[i].addListener((FutureListener<Void>) future -> {
                if (finalI + 1 < p.length) {
                    p[finalI + 1].setSuccess(null);
                }
                latch.countDown();
            });
        }

        p[0].setSuccess(null);
    }

    private static void testStackOverFlowChainedFuturesB(int promiseChainLength, final EventExecutor executor,
                                                         boolean runTestInExecutorThread)
            throws InterruptedException {
        final Promise<Void>[] p = new DefaultPromise[promiseChainLength];
        final CountDownLatch latch = new CountDownLatch(promiseChainLength);

        if (runTestInExecutorThread) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    testStackOverFlowChainedFuturesB(executor, p, latch);
                }
            });
        } else {
            testStackOverFlowChainedFuturesB(executor, p, latch);
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < p.length; ++i) {
            assertTrue(p[i].isSuccess(), "index " + i);
        }
    }

    private static void testStackOverFlowChainedFuturesB(EventExecutor executor, final Promise<Void>[] p,
                                                         final CountDownLatch latch) {
        for (int i = 0; i < p.length; i ++) {
            final int finalI = i;
            p[i] = new DefaultPromise<Void>(executor);
            p[i].addListener((FutureListener<Void>) future -> future.addListener(
                    (FutureListener<Void>) f -> {
                if (finalI + 1 < p.length) {
                    p[finalI + 1].setSuccess(null);
                }
                latch.countDown();
            }));
        }

        p[0].setSuccess(null);
    }

    /**
     * This test is mean to simulate the following sequence of events, which all take place on the I/O thread:
     * <ol>
     * <li>A write is done</li>
     * <li>The write operation completes, and the promise state is changed to done</li>
     * <li>A listener is added to the return from the write. The {@link FutureListener#operationComplete(Future)}
     * updates state which must be invoked before the response to the previous write is read.</li>
     * <li>The write operation</li>
     * </ol>
     */
    private static void testLateListenerIsOrderedCorrectly(Throwable cause) throws InterruptedException {
        final EventExecutor executor = new TestEventExecutor();
        try {
            final AtomicInteger state = new AtomicInteger();
            final CountDownLatch latch1 = new CountDownLatch(1);
            final CountDownLatch latch2 = new CountDownLatch(2);
            final Promise<Void> promise = new DefaultPromise<Void>(executor);

            // Add a listener before completion so "lateListener" is used next time we add a listener.
            promise.addListener((FutureListener<Void>) future -> assertTrue(state.compareAndSet(0, 1)));

            // Simulate write operation completing, which will execute listeners in another thread.
            if (cause == null) {
                promise.setSuccess(null);
            } else {
                promise.setFailure(cause);
            }

            // Add a "late listener"
            promise.addListener((FutureListener<Void>) future -> {
                assertTrue(state.compareAndSet(1, 2));
                latch1.countDown();
            });

            // Wait for the listeners and late listeners to be completed.
            latch1.await();
            assertEquals(2, state.get());

            // This is the important listener. A late listener that is added after all late listeners
            // have completed, and needs to update state before a read operation (on the same executor).
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    promise.addListener((FutureListener<Void>) future -> {
                        assertTrue(state.compareAndSet(2, 3));
                        latch2.countDown();
                    });
                }
            });

            // Simulate a read operation being queued up in the executor.
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // This is the key, we depend upon the state being set in the next listener.
                    assertEquals(3, state.get());
                    latch2.countDown();
                }
            });

            latch2.await();
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    private static void testPromiseListenerAddWhenComplete(Throwable cause) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Promise<Void> promise = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
        promise.addListener((FutureListener<Void>) future ->
                promise.addListener((FutureListener<Void>) f -> latch.countDown()));
        if (cause == null) {
            promise.setSuccess(null);
        } else {
            promise.setFailure(cause);
        }
        latch.await();
    }

    private static void testListenerNotifyLater(final int numListenersBefore) throws Exception {
        EventExecutor executor = new TestEventExecutor();
        int expectedCount = numListenersBefore + 2;
        final CountDownLatch latch = new CountDownLatch(expectedCount);
        final FutureListener<Void> listener = future -> latch.countDown();
        final Promise<Void> promise = new DefaultPromise<Void>(executor);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numListenersBefore; i++) {
                    promise.addListener(listener);
                }
                promise.setSuccess(null);

                GlobalEventExecutor.INSTANCE.execute(new Runnable() {
                    @Override
                    public void run() {
                        promise.addListener(listener);
                    }
                });
                promise.addListener(listener);
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS),
            "Should have notified " + expectedCount + " listeners");
        executor.shutdownGracefully().sync();
    }

    private static final class TestEventExecutor extends SingleThreadEventExecutor {
        TestEventExecutor() {
            super(null, Executors.defaultThreadFactory(), true);
        }

        @Override
        protected void run() {
            for (;;) {
                Runnable task = takeTask();
                if (task != null) {
                    task.run();
                    updateLastExecutionTime();
                }

                if (confirmShutdown()) {
                    break;
                }
            }
        }
    }

    private static RuntimeException fakeException() {
        return new RuntimeException("fake exception");
    }
}
