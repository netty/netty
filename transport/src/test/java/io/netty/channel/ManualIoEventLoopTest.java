/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel;

import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.MockTicker;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.Ticker;
import io.netty.util.internal.ThreadExecutorMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManualIoEventLoopTest {

    @Test
    public void testRunNow() throws Exception {
        Thread currentThread = Thread.currentThread();
        Semaphore semaphore = new Semaphore(0);
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(currentThread, executor ->
                new TestIoHandler(semaphore));
        assertEquals(0, eventLoop.runNow());

        TestRunnable runnable = new TestRunnable();
        eventLoop.execute(runnable);
        assertFalse(runnable.isDone());

        assertEquals(1, eventLoop.runNow());
        assertTrue(runnable.isDone());
        eventLoop.shutdown();
        while (!eventLoop.isTerminated()) {
            eventLoop.runNow();
        }

        eventLoop.terminationFuture().sync();
    }

    @Test
    public void testRun() throws Exception {
        Thread currentThread = Thread.currentThread();
        Semaphore semaphore = new Semaphore(0);
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(currentThread, executor ->
                new TestIoHandler(semaphore));

        long waitTime = TimeUnit.MILLISECONDS.toNanos(200);
        long current = System.nanoTime();
        assertEquals(0, eventLoop.run(waitTime));
        long actualNanos = System.nanoTime() - current;
        assertThat(actualNanos).isGreaterThanOrEqualTo(waitTime);

        TestRunnable runnable = new TestRunnable();
        eventLoop.execute(runnable);
        assertFalse(runnable.isDone());

        waitTime = TimeUnit.SECONDS.toNanos(1);
        current = System.nanoTime();
        assertEquals(1, eventLoop.run(waitTime));
        assertThat(waitTime).isGreaterThan(System.nanoTime() - current);

        assertTrue(runnable.isDone());
        eventLoop.shutdown();

        while (!eventLoop.isTerminated()) {
            eventLoop.runNow();
        }
        eventLoop.terminationFuture().sync();
    }

    @Test
    public void testShutdownOutSideOfOwningThread() throws Exception {
        Semaphore semaphore = new Semaphore(0);
        Thread ownerThread = new Thread();
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(ownerThread, executor ->
                new TestIoHandler(semaphore));
        eventLoop.shutdown();
        assertTrue(eventLoop.isShuttingDown());
        // we expect wakeup to be called!
        assertEquals(1, semaphore.availablePermits());
    }

    @Test
    public void testCallFromWrongThread() throws Exception {
        Thread thread = new Thread();
        Semaphore semaphore = new Semaphore(0);
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(thread, executor ->
                new TestIoHandler(semaphore));

        assertThrows(IllegalStateException.class, eventLoop::runNow);
        assertThrows(IllegalStateException.class, () -> eventLoop.run(10));
    }

    @Test
    public void testThreadEventExecutorMap() throws Exception {
        final BlockingQueue<EventExecutor> queue = new LinkedBlockingQueue<>();
        Semaphore semaphore = new Semaphore(0);
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(Thread.currentThread(), executor ->
                new TestIoHandler(semaphore));
        assertNull(ThreadExecutorMap.currentExecutor());
        eventLoop.execute(() -> queue.offer(ThreadExecutorMap.currentExecutor()));
        assertEquals(1, eventLoop.runNow());
        assertSame(eventLoop, queue.take());
        eventLoop.shutdown();

        while (!eventLoop.isTerminated()) {
            eventLoop.runNow();
        }
        eventLoop.terminationFuture().sync();
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testInvokeAnyInEventLoop() {
        testInvokeInEventLoop(true, false);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testInvokeAnyInEventLoopWithTimeout() {
        testInvokeInEventLoop(true, true);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testInvokeAllInEventLoop() {
        testInvokeInEventLoop(false, false);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testInvokeAllInEventLoopWithTimeout() {
        testInvokeInEventLoop(false, true);
    }

    private static void testInvokeInEventLoop(final boolean any, final boolean timeout) {
        Semaphore semaphore = new Semaphore(0);
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(Thread.currentThread(), executor ->
                new TestIoHandler(semaphore));
        try {
            assertThrows(RejectedExecutionException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    final Promise<Void> promise = eventLoop.newPromise();
                    eventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Set<Callable<Boolean>> set = Collections.<Callable<Boolean>>singleton(
                                        new Callable<Boolean>() {
                                            @Override
                                            public Boolean call() {
                                                promise.setFailure(
                                                        new AssertionError("Should never execute the Callable"));
                                                return Boolean.TRUE;
                                            }
                                        });
                                if (any) {
                                    if (timeout) {
                                        eventLoop.invokeAny(set, 10, TimeUnit.SECONDS);
                                    } else {
                                        eventLoop.invokeAny(set);
                                    }
                                } else {
                                    if (timeout) {
                                        eventLoop.invokeAll(set, 10, TimeUnit.SECONDS);
                                    } else {
                                        eventLoop.invokeAll(set);
                                    }
                                }
                                promise.setFailure(new AssertionError("Should never reach here"));
                            } catch (Throwable cause) {
                                promise.setFailure(cause);
                            }
                        }
                    });
                    while (!promise.isDone()) {
                        eventLoop.runNow();
                    }
                    promise.syncUninterruptibly();
                }
            });
        } finally {
            eventLoop.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            while (!eventLoop.isTerminated()) {
                eventLoop.runNow();
            }
            assertTrue(eventLoop.terminationFuture().isSuccess());
        }
    }

    @Test
    public void testDelayOwningThread() throws ExecutionException, InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(null, executor ->
                new TestIoHandler(semaphore));
        Thread thread = new Thread(() -> {
            eventLoop.setOwningThread(Thread.currentThread());
            assertTrue(eventLoop.inEventLoop());
            while (!eventLoop.isTerminated()) {
                eventLoop.runNow();
            }
        });

        assertFalse(eventLoop.inEventLoop());

        CompletableFuture<Void> cf = new CompletableFuture<>();
        eventLoop.execute(() -> {
            assertTrue(eventLoop.inEventLoop());
            cf.complete(null);
        });

        thread.start();
        cf.get();

        eventLoop.shutdownGracefully();
        thread.join();
    }

    @Test
    public void testRunWithoutOwner() throws ExecutionException, InterruptedException {
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(null, executor ->
                new TestIoHandler(new Semaphore(0)));

        // prior to setOwningThread, runNow is forbidden
        assertThrows(IllegalStateException.class, eventLoop::runNow);

        eventLoop.setOwningThread(Thread.currentThread());

        eventLoop.runNow(); // runs fine

        eventLoop.shutdownGracefully();
    }

    @Test
    public void testSetOwnerMultipleTimes() {
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(null, executor ->
                new TestIoHandler(new Semaphore(0)));
        eventLoop.setOwningThread(Thread.currentThread());
        assertThrows(IllegalStateException.class, () -> eventLoop.setOwningThread(Thread.currentThread()));

        eventLoop.shutdownGracefully();
    }

    @Test
    public void testTicker() {
        MockTicker ticker = Ticker.newMockTicker();
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(
                null, Thread.currentThread(), NioIoHandler.newFactory(), ticker);

        AtomicInteger counter = new AtomicInteger();
        eventLoop.schedule(counter::incrementAndGet, 60, TimeUnit.SECONDS);

        eventLoop.runNow();
        assertEquals(0, counter.get());

        ticker.advance(50, TimeUnit.SECONDS);
        eventLoop.runNow();
        assertEquals(0, counter.get());

        ticker.advance(20, TimeUnit.SECONDS);
        eventLoop.runNow();
        assertEquals(1, counter.get());

        eventLoop.shutdownGracefully();
    }

    private static final class TestRunnable implements Runnable {
        private boolean done;
        @Override
        public void run() {
            done = true;
        }

        boolean isDone() {
            return done;
        }
    }

    private static class TestIoHandler implements IoHandler {
        private final Semaphore semaphore;

        TestIoHandler(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void prepareToDestroy() {
            // NOOP
        }

        @Override
        public void destroy() {
            // NOOP
        }

        @Override
        public IoRegistration register(final IoHandle handle) {
            return new IoRegistration() {
                private final AtomicBoolean canceled = new AtomicBoolean();

                @Override
                public <T> T attachment() {
                    return null;
                }

                @Override
                public long submit(IoOps ops) {
                    return 0;
                }

                @Override
                public boolean cancel() {
                    return canceled.compareAndSet(false, true);
                }

                @Override
                public boolean isValid() {
                    return !canceled.get();
                }
            };
        }

        @Override
        public void wakeup() {
            semaphore.release();
        }

        @Override
        public int run(IoHandlerContext context) {
            try {
                if (context.canBlock()) {
                    if (context.deadlineNanos() != -1) {
                        long delay = context.delayNanos(System.nanoTime());
                        semaphore.tryAcquire(delay, TimeUnit.NANOSECONDS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 0;
        }

        @Override
        public boolean isCompatible(Class<? extends IoHandle> handleType) {
            return false;
        }
    }
}
