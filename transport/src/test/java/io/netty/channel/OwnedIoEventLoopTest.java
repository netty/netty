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

import org.junit.jupiter.api.Test;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OwnedIoEventLoopTest {

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
        assertTrue(actualNanos >= waitTime, actualNanos + " >= " + waitTime);

        TestRunnable runnable = new TestRunnable();
        eventLoop.execute(runnable);
        assertFalse(runnable.isDone());

        waitTime = TimeUnit.SECONDS.toNanos(1);
        current = System.nanoTime();
        assertEquals(1, eventLoop.run(waitTime));
        assertTrue(System.nanoTime() - current < waitTime);

        assertTrue(runnable.isDone());
        eventLoop.shutdown();

        while (!eventLoop.isTerminated()) {
            eventLoop.runNow();
        }
        eventLoop.terminationFuture().sync();
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

    private static class TestIoHandle implements IoHandle {
        @Override
        public void handle(IoRegistration registration, IoEvent readyOps) {
            // NOOP
        }

        @Override
        public void close() {
            // NOOP
        }
    }
}
