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
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
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

    private static void findStackOverflowDepth() {
        ++stackOverflowDepth;
        findStackOverflowDepth();
    }

    private static int stackOverflowTestDepth() {
        return max(stackOverflowDepth << 1, stackOverflowDepth);
    }

    @Test
    public void testCancelDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = Mockito.mock(EventExecutor.class);
        Mockito.when(executor.inEventLoop()).thenReturn(false);

        Promise<Void> promise = new DefaultPromise<Void>(executor);
        assertTrue(promise.cancel(false));
        Mockito.verify(executor, Mockito.never()).execute(Mockito.any(Runnable.class));
        assertTrue(promise.isCancelled());
    }

    @Test
    public void testSuccessDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = Mockito.mock(EventExecutor.class);
        Mockito.when(executor.inEventLoop()).thenReturn(false);

        Object value = new Object();
        Promise<Object> promise = new DefaultPromise<Object>(executor);
        promise.setSuccess(value);
        Mockito.verify(executor, Mockito.never()).execute(Mockito.any(Runnable.class));
        assertSame(value, promise.getNow());
    }

    @Test
    public void testFailureDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = Mockito.mock(EventExecutor.class);
        Mockito.when(executor.inEventLoop()).thenReturn(false);

        Exception cause = new Exception();
        Promise<Void> promise = new DefaultPromise<Void>(executor);
        promise.setFailure(cause);
        Mockito.verify(executor, Mockito.never()).execute(Mockito.any(Runnable.class));
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
        assertThat(promise.cause(), instanceOf(CancellationException.class));
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
                assertThat(System.nanoTime() - start, lessThan(wait));
            }
        } finally {
            if (executor != null) {
                executor.shutdownGracefully();
            }
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
            p[i].addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (finalI + 1 < p.length) {
                        p[finalI + 1].setSuccess(null);
                    }
                    latch.countDown();
                }
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

    private static void testStackOverFlowChainedFuturesB(EventExecutor executor, final Promise<Void>[] p,
                                                         final CountDownLatch latch) {
        for (int i = 0; i < p.length; i ++) {
            final int finalI = i;
            p[i] = new DefaultPromise<Void>(executor);
            p[i].addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    future.addListener(new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            if (finalI + 1 < p.length) {
                                p[finalI + 1].setSuccess(null);
                            }
                            latch.countDown();
                        }
                    });
                }
            });
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
            promise.addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    assertTrue(state.compareAndSet(0, 1));
                }
            });

            // Simulate write operation completing, which will execute listeners in another thread.
            if (cause == null) {
                promise.setSuccess(null);
            } else {
                promise.setFailure(cause);
            }

            // Add a "late listener"
            promise.addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    assertTrue(state.compareAndSet(1, 2));
                    latch1.countDown();
                }
            });

            // Wait for the listeners and late listeners to be completed.
            latch1.await();
            assertEquals(2, state.get());

            // This is the important listener. A late listener that is added after all late listeners
            // have completed, and needs to update state before a read operation (on the same executor).
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    promise.addListener(new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            assertTrue(state.compareAndSet(2, 3));
                            latch2.countDown();
                        }
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
        promise.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                promise.addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        latch.countDown();
                    }
                });
            }
        });
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
        final FutureListener<Void> listener = new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                latch.countDown();
            }
        };
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
