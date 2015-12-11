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

package io.netty.util.concurrent;

import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class DefaultPromiseTest {

    @Test(expected = CancellationException.class)
    public void testCancellationExceptionIsThrownWhenBlockingGet() throws InterruptedException, ExecutionException {
        final Promise<Void> promise = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
        promise.cancel(false);
        promise.get();
    }

    @Test(expected = CancellationException.class)
    public void testCancellationExceptionIsThrownWhenBlockingGetWithTimeout() throws InterruptedException,
            ExecutionException, TimeoutException {
        final Promise<Void> promise = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
        promise.cancel(false);
        promise.get(1, TimeUnit.SECONDS);
    }

    @Test
    public void testNoStackOverflowErrorWithImmediateEventExecutorA() throws Exception {
        final Promise<Void>[] p = new DefaultPromise[128];
        for (int i = 0; i < p.length; i ++) {
            final int finalI = i;
            p[i] = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
            p[i].addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (finalI + 1 < p.length) {
                        p[finalI + 1].setSuccess(null);
                    }
                }
            });
        }

        p[0].setSuccess(null);

        for (Promise<Void> a: p) {
            assertThat(a.isSuccess(), is(true));
        }
    }

    @Test
    public void testNoStackOverflowErrorWithImmediateEventExecutorB() throws Exception {
        final Promise<Void>[] p = new DefaultPromise[128];
        for (int i = 0; i < p.length; i ++) {
            final int finalI = i;
            p[i] = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
            p[i].addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    DefaultPromise.notifyListener(ImmediateEventExecutor.INSTANCE, future, new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            if (finalI + 1 < p.length) {
                                p[finalI + 1].setSuccess(null);
                            }
                        }
                    });
                }
            });
        }

        p[0].setSuccess(null);

        for (Promise<Void> a: p) {
            assertThat(a.isSuccess(), is(true));
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

                assertSame("Fail 1 during run " + i + " / " + runs, listener1, listeners.take());
                assertSame("Fail 2 during run " + i + " / " + runs, listener2, listeners.take());
                assertSame("Fail 3 during run " + i + " / " + runs, listener3, listeners.take());
                assertSame("Fail 4 during run " + i + " / " + runs, listener4, listeners.take());
                assertTrue("Fail during run " + i + " / " + runs, listeners.isEmpty());
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

    @Test(timeout = 2000)
    public void testPromiseListenerAddWhenCompleteFailure() throws Exception {
        testPromiseListenerAddWhenComplete(fakeException());
    }

    @Test(timeout = 2000)
    public void testPromiseListenerAddWhenCompleteSuccess() throws Exception {
        testPromiseListenerAddWhenComplete(null);
    }

    @Test(timeout = 2000)
    public void testLateListenerIsOrderedCorrectlySuccess() throws InterruptedException {
        testLateListenerIsOrderedCorrectly(null);
    }

    @Test(timeout = 2000)
    public void testLateListenerIsOrderedCorrectlyFailure() throws InterruptedException {
        testLateListenerIsOrderedCorrectly(fakeException());
    }

    /**
     * This test is mean to simulate the following sequence of events, which all take place on the I/O thread:
     * <ol>
     * <li>A write is done</li>
     * <li>The write operation completes, and the promise state is changed to done</li>
     * <li>A listener is added to the return from the write. The {@link FutureListener#operationComplete()} updates
     * state which must be invoked before the response to the previous write is read.</li>
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

        assertTrue("Should have notifed " + expectedCount + " listeners", latch.await(5, TimeUnit.SECONDS));
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
