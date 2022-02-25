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

package io.netty5.util.concurrent;

import io.netty5.util.Signal;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty5.util.concurrent.ImmediateEventExecutor.INSTANCE;
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
        public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public Future<Void> terminationFuture() {
            return null;
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
        public Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
            return fail("Cannot schedule commands");
        }

        @Override
        public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
            return fail("Cannot schedule commands");
        }

        @Override
        public Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
            return fail("Cannot schedule commands");
        }

        @Override
        public Future<Void> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay,
                                                   TimeUnit unit) {
            return fail("Cannot schedule commands");
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return false;
        }

        @Override
        public void execute(Runnable task) {
            fail("Cannot schedule commands");
        }
    }

    @Test
    public void testCancelDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = new RejectingEventExecutor();

        DefaultPromise<Void> promise = new DefaultPromise<Void>(executor);
        assertTrue(promise.cancel());
        assertTrue(promise.isCancelled());
    }

    @Test
    public void testSuccessDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = new RejectingEventExecutor();

        Object value = new Object();
        DefaultPromise<Object> promise = new DefaultPromise<Object>(executor);
        promise.setSuccess(value);
        assertSame(value, promise.getNow());
    }

    @Test
    public void testFailureDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = new RejectingEventExecutor();

        Exception cause = new Exception();
        DefaultPromise<Void> promise = new DefaultPromise<Void>(executor);
        promise.setFailure(cause);
        assertTrue(promise.isFailed());
        assertFalse(promise.isSuccess());
        assertSame(cause, promise.cause());
    }

    @Test
    public void testCancellationExceptionIsThrownWhenBlockingGet() throws Exception {
        DefaultPromise<Void> promise = new DefaultPromise<>(INSTANCE);
        assertTrue(promise.cancel());
        assertThrows(CancellationException.class, promise::get);
    }

    @Test
    public void testCancellationExceptionIsThrownWhenBlockingGetWithTimeout() throws Exception {
        DefaultPromise<Void> promise = new DefaultPromise<>(INSTANCE);
        assertTrue(promise.cancel());
        assertThrows(CancellationException.class, () -> promise.get(1, TimeUnit.SECONDS));
    }

    @Test
    public void testCancellationExceptionIsReturnedAsCause() throws Exception {
        DefaultPromise<Void> promise = new DefaultPromise<>(INSTANCE);
        assertTrue(promise.cancel());
        assertThat(promise.cause()).isInstanceOf(CancellationException.class);
        assertTrue(promise.isFailed());
        assertTrue(promise.isDone());
    }

    @Test
    public void uncancellablePromiseIsNotDone() {
        DefaultPromise<Void> promise = new DefaultPromise<>(INSTANCE);
        promise.setUncancellable();
        assertFalse(promise.isDone());
        assertFalse(promise.isCancellable());
        assertFalse(promise.isCancelled());
    }

    @Test
    public void testStackOverflowWithImmediateEventExecutorA() throws Exception {
        testStackOverFlowChainedFuturesA(stackOverflowTestDepth(), INSTANCE, true);
        testStackOverFlowChainedFuturesA(stackOverflowTestDepth(), INSTANCE, false);
    }

    @Test
    public void testNoStackOverflowWithDefaultEventExecutorA() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            EventExecutor executor = new SingleThreadEventExecutor(executorService);
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
        testStackOverFlowChainedFuturesB(stackOverflowTestDepth(), INSTANCE, true);
        testStackOverFlowChainedFuturesB(stackOverflowTestDepth(), INSTANCE, false);
    }

    @Test
    public void testNoStackOverflowWithDefaultEventExecutorB() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            EventExecutor executor = new SingleThreadEventExecutor(executorService);
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
            final BlockingQueue<FutureListener<Void>> listeners = new LinkedBlockingQueue<>();
            int runs = 100000;

            for (int i = 0; i < runs; i++) {
                DefaultPromise<Void> promise = new DefaultPromise<>(executor);
                final FutureListener<Void> listener1 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<? extends Void> future) throws Exception {
                        listeners.add(this);
                    }
                };
                final FutureListener<Void> listener2 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<? extends Void> future) throws Exception {
                        listeners.add(this);
                    }
                };
                final FutureListener<Void> listener4 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<? extends Void> future) throws Exception {
                        listeners.add(this);
                    }
                };
                final FutureListener<Void> listener3 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<? extends Void> future) throws Exception {
                        listeners.add(this);
                        future.addListener(listener4);
                    }
                };

                GlobalEventExecutor.INSTANCE.execute(() -> promise.setSuccess(null));

                promise.addListener(listener1)
                       .addListener(listener2)
                       .addListener(listener3);

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
        TestEventExecutor executor = new TestEventExecutor();
        // Testing first execution path in DefaultPromise
        testListenerNotifyLater(1, executor);

        // Testing second execution path in DefaultPromise
        testListenerNotifyLater(2, executor);

        executor.shutdownGracefully().sync();
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
            final Map<Thread, DefaultPromise<Void>> promises = new HashMap<>();
            for (int i = 0; i < numberOfAttempts; i++) {
                final DefaultPromise<Void> promise = new DefaultPromise<>(executor);
                final Thread thread = new Thread(() -> promise.setSuccess(null));
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
    public void signalUncancellableCompletionValue() {
        DefaultPromise<Signal> promise = new DefaultPromise<>(INSTANCE);
        promise.setSuccess(Signal.valueOf(DefaultPromise.class, "UNCANCELLABLE"));
        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertFalse(promise.isFailed());
    }

    @Test
    public void signalSuccessCompletionValue() {
        DefaultPromise<Signal> promise = new DefaultPromise<>(INSTANCE);
        promise.setSuccess(Signal.valueOf(DefaultPromise.class, "SUCCESS"));
        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertFalse(promise.isFailed());
    }

    @Test
    public void setUncancellableGetNow() {
        DefaultPromise<String> promise = new DefaultPromise<>(INSTANCE);
        assertThrows(IllegalStateException.class, () -> promise.getNow());
        assertFalse(promise.isDone());
        assertTrue(promise.setUncancellable());
        assertThrows(IllegalStateException.class, () -> promise.getNow());
        assertFalse(promise.isDone());
        assertFalse(promise.isSuccess());
        assertFalse(promise.isFailed());

        promise.setSuccess("success");

        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertFalse(promise.isFailed());
        assertEquals("success", promise.getNow());
    }

    @Test
    public void cancellingUncancellablePromiseDoesNotCompleteIt() {
        DefaultPromise<Void> promise = new DefaultPromise<>(INSTANCE);
        promise.setUncancellable();
        promise.cancel();
        assertFalse(promise.isCancelled());
        assertFalse(promise.isDone());
        assertFalse(promise.isFailed());
        assertFalse(promise.isSuccess());
        promise.setSuccess(null);
        assertFalse(promise.isCancelled());
        assertTrue(promise.isDone());
        assertFalse(promise.isFailed());
        assertTrue(promise.isSuccess());
    }

    @Test
    public void throwUncheckedSync() throws InterruptedException {
        Exception exception = new Exception();
        DefaultPromise<String> promise = new DefaultPromise<>(INSTANCE);
        promise.setFailure(exception);
        assertTrue(promise.isFailed());

        try {
            promise.sync();
        } catch (CompletionException e) {
            assertSame(exception, e.getCause());
        }
    }

    @Test
    public void throwUncheckedSyncUninterruptibly() {
        Exception exception = new Exception();
        DefaultPromise<String> promise = new DefaultPromise<>(INSTANCE);
        promise.setFailure(exception);
        assertTrue(promise.isFailed());

        try {
            promise.syncUninterruptibly();
        } catch (CompletionException e) {
            assertSame(exception, e.getCause());
        }
    }

    @Test
    public void throwCancelled() throws InterruptedException {
        DefaultPromise<String> promise = new DefaultPromise<>(INSTANCE);
        promise.cancel();
        assertThrows(CancellationException.class, promise::sync);
    }

    @Test
    public void mustPassContextToContextListener() {
        DefaultPromise<Object> promise = new DefaultPromise<>(INSTANCE);
        Object context = new Object();
        Object result = new Object();
        promise.addListener(context, (ctx, future) -> {
            assertSame(context, ctx);
            assertSame(future, promise);
            assertSame(future.getNow(), result);
        });
        promise.setSuccess(result);
    }

    @Test
    public void mustPassNullContextToContextListener() {
        DefaultPromise<Object> promise = new DefaultPromise<>(INSTANCE);
        Object result = new Object();
        promise.addListener(null, (ctx, future) -> {
            assertNull(ctx);
            assertSame(future, promise);
            assertSame(future.getNow(), result);
        });
        promise.setSuccess(result);
    }

    @Test
    public void getNowOnUnfinishedPromiseMustThrow() {
        DefaultPromise<Object> promise = new DefaultPromise<>(INSTANCE);
        assertThrows(IllegalStateException.class, () -> promise.getNow());
    }

    @SuppressWarnings("ThrowableNotThrown")
    @Test
    public void causeOnUnfinishedPromiseMustThrow() {
        DefaultPromise<Object> promise = new DefaultPromise<>(INSTANCE);
        assertThrows(IllegalStateException.class, () -> promise.cause());
    }

    private static void testStackOverFlowChainedFuturesA(int promiseChainLength, final EventExecutor executor,
                                                         boolean runTestInExecutorThread)
            throws InterruptedException {
        final List<DefaultPromise<Void>> ps = new ArrayList<>(promiseChainLength);
        for (int i = 0; i < promiseChainLength; i++) {
            ps.add(null);
        }
        final CountDownLatch latch = new CountDownLatch(promiseChainLength);

        if (runTestInExecutorThread) {
            executor.execute(() -> testStackOverFlowChainedFuturesA(executor, ps, latch));
        } else {
            testStackOverFlowChainedFuturesA(executor, ps, latch);
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < ps.size(); ++i) {
            assertTrue(ps.get(i).isSuccess(), "index " + i);
        }
    }

    private static void testStackOverFlowChainedFuturesA(EventExecutor executor, final List<DefaultPromise<Void>> ps,
                                                         final CountDownLatch latch) {
        for (int i = 0; i < ps.size(); i ++) {
            final int finalI = i;
            DefaultPromise<Void> p = new DefaultPromise<>(executor);
            ps.set(i, p);
            p.addListener(future -> {
                if (finalI + 1 < ps.size()) {
                    ps.get(finalI + 1).setSuccess(null);
                }
                latch.countDown();
            });
        }

        ps.get(0).setSuccess(null);
    }

    private static void testStackOverFlowChainedFuturesB(int promiseChainLength, final EventExecutor executor,
                                                         boolean runTestInExecutorThread)
            throws InterruptedException {
        final List<DefaultPromise<Void>> ps = new ArrayList<>(promiseChainLength);
        for (int i = 0; i < promiseChainLength; i++) {
            ps.add(null);
        }
        final CountDownLatch latch = new CountDownLatch(promiseChainLength);

        if (runTestInExecutorThread) {
            executor.execute(() -> testStackOverFlowChainedFuturesB(executor, ps, latch));
        } else {
            testStackOverFlowChainedFuturesB(executor, ps, latch);
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < ps.size(); ++i) {
            assertTrue(ps.get(i).isSuccess(), "index " + i);
        }
    }

    private static void testStackOverFlowChainedFuturesB(EventExecutor executor, final List<DefaultPromise<Void>> ps,
                                                         final CountDownLatch latch) {
        for (int i = 0; i < ps.size(); i ++) {
            final int finalI = i;
            DefaultPromise<Void> p = new DefaultPromise<>(executor);
            ps.set(i, p);
            p.addListener(future -> future.addListener(future1 -> {
                if (finalI + 1 < ps.size()) {
                    ps.get(finalI + 1).setSuccess(null);
                }
                latch.countDown();
            }));
        }

        ps.get(0).setSuccess(null);
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
            final CountDownLatch latch2 = new CountDownLatch(1);
            final CountDownLatch latch3 = new CountDownLatch(1);

            DefaultPromise<Void> promise = new DefaultPromise<>(executor);

            // Add a listener before completion so "lateListener" is used next time we add a listener.
            promise.addListener(future -> assertTrue(state.compareAndSet(0, 1)));

            // Simulate write operation completing, which will execute listeners in another thread.
            if (cause == null) {
                promise.setSuccess(null);
            } else {
                promise.setFailure(cause);
            }

            // Add a "late listener"
            promise.addListener(future -> {
                assertTrue(state.compareAndSet(1, 2));
                latch1.countDown();
            });

            // Wait for the listeners and late listeners to be completed.
            latch1.await();
            assertEquals(2, state.get());

            // This is the important listener. A late listener that is added after all late listeners
            // have completed, and needs to update state before a read operation (on the same executor).
            executor.execute(() -> promise.addListener(future -> {
                assertTrue(state.compareAndSet(2, 3));
                latch2.countDown();
            }));
            latch2.await();

            // Simulate a read operation being queued up in the executor.
            executor.execute(() -> {
                // This is the key, we depend upon the state being set in the next listener.
                assertEquals(3, state.get());
                latch3.countDown();
            });

            latch3.await();
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    private static void testPromiseListenerAddWhenComplete(Throwable cause) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        DefaultPromise<Void> promise = new DefaultPromise<>(INSTANCE);
        promise.addListener(future ->
                promise.addListener(future1 -> latch.countDown()));
        if (cause == null) {
            promise.setSuccess(null);
        } else {
            promise.setFailure(cause);
        }
        latch.await();
    }

    private static void testListenerNotifyLater(int numListenersBefore, TestEventExecutor executor) throws Exception {
        int expectedCount = numListenersBefore + 2;
        final CountDownLatch latch = new CountDownLatch(expectedCount);
        final FutureListener<Void> listener = future -> latch.countDown();
        DefaultPromise<Void> promise = new DefaultPromise<>(executor);
        executor.execute(() -> {
            for (int i = 0; i < numListenersBefore; i++) {
                promise.addListener(listener);
            }
            promise.setSuccess(null);

            GlobalEventExecutor.INSTANCE.execute(() -> promise.addListener(listener));
            promise.addListener(listener);
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS),
            "Should have notified " + expectedCount + " listeners");
    }

    private static RuntimeException fakeException() {
        return new RuntimeException("fake exception");
    }
}
