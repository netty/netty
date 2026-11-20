/*
 * Copyright 2024 The Netty Project
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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SingleThreadIoEventLoopTest {

    @Test
    void testDefaultIoRatio() {
        SingleThreadIoEventLoop loop = new SingleThreadIoEventLoop(null,
                Executors.defaultThreadFactory(), TestIoHandler::new);
        assertEquals(SingleThreadIoEventLoop.DEFAULT_IO_RATIO, loop.getIoRatio());
        loop.shutdownGracefully();
    }

    @Test
    void testSetIoRatio() {
        SingleThreadIoEventLoop loop = new SingleThreadIoEventLoop(null,
                Executors.defaultThreadFactory(), TestIoHandler::new);
        loop.setIoRatio(50);
        assertEquals(50, loop.getIoRatio());
        assertThrows(IllegalArgumentException.class, () -> loop.setIoRatio(0));
        assertThrows(IllegalArgumentException.class, () -> loop.setIoRatio(101));
        loop.shutdownGracefully();
    }

    @Test
    void testIsIoType() {
        class TestIoHandler2 extends TestIoHandler {
            TestIoHandler2(ThreadAwareExecutor executor) {
                super(executor);
            }
        }

        IoEventLoopGroup group = new SingleThreadIoEventLoop(null,
                Executors.defaultThreadFactory(), TestIoHandler::new);
        assertTrue(group.isIoType(TestIoHandler.class));
        assertFalse(group.isIoType(TestIoHandler2.class));
        group.shutdownGracefully();
    }

    static final class CompatibleTestIoHandler extends TestIoHandler {
        CompatibleTestIoHandler(ThreadAwareExecutor executor) {
            super(executor);
        }

        @Override
        public boolean isCompatible(Class<? extends IoHandle> handleType) {
            return handleType.equals(TestIoHandle.class);
        }
    }

    @Test
    void testIsCompatible() {
        IoHandle handle = new TestIoHandle() { };
        IoEventLoopGroup group = new SingleThreadIoEventLoop(null,
                Executors.defaultThreadFactory(), CompatibleTestIoHandler::new);
        assertTrue(group.isCompatible(TestIoHandle.class));
        assertFalse(group.isCompatible(handle.getClass()));
        group.shutdownGracefully();
    }

    // https://github.com/netty/netty/issues/18483
    @Test
    void testPrepareToDestroyCalledOnceOnShutdown() throws Exception {
        AtomicInteger prepareToDestroyCalls = new AtomicInteger();
        SingleThreadIoEventLoop loop = new SingleThreadIoEventLoop(null, Executors.defaultThreadFactory(),
                executor -> new CountingTestIoHandler(executor, prepareToDestroyCalls));
        // Start the event loop thread and let it go back to waiting for IO.
        loop.submit(() -> { }).sync();

        loop.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();

        assertThat(prepareToDestroyCalls).hasValue(1);
    }

    // https://github.com/netty/netty/issues/17897 and https://github.com/netty/netty/issues/18483
    @Test
    void testPrepareToDestroyCalledOnceWhenShutdownStartsWhileTaskIsRunning() throws Exception {
        AtomicInteger prepareToDestroyCalls = new AtomicInteger();
        SingleThreadIoEventLoop loop = new SingleThreadIoEventLoop(null, Executors.defaultThreadFactory(),
                executor -> new CountingTestIoHandler(executor, prepareToDestroyCalls));
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        loop.execute(() -> {
            taskStarted.countDown();
            try {
                releaseTask.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(taskStarted.await(10, TimeUnit.SECONDS)).isTrue();

        // The IO loop does not see the shutdown before the task returns, so prepareToDestroy() has to run after it.
        Future<?> shutdown = loop.shutdownGracefully(0, 15, TimeUnit.SECONDS);
        releaseTask.countDown();
        shutdown.sync();

        assertThat(prepareToDestroyCalls).hasValue(1);
    }

    private static final class CountingTestIoHandler extends TestIoHandler {
        private final AtomicInteger prepareToDestroyCalls;

        CountingTestIoHandler(ThreadAwareExecutor executor, AtomicInteger prepareToDestroyCalls) {
            super(executor);
            this.prepareToDestroyCalls = prepareToDestroyCalls;
        }

        @Override
        public void prepareToDestroy() {
            prepareToDestroyCalls.incrementAndGet();
        }
    }

    private static final class TestThreadFactory implements ThreadFactory {
        final LinkedBlockingQueue<Thread> threads = new LinkedBlockingQueue<>();
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            threads.add(thread);
            return thread;
        }
    }

    @Disabled("This test is flaky and will often stall the build")
    @Test
    void testSuspendingWhileRegistrationActive() throws Exception {
        TestThreadFactory threadFactory = new TestThreadFactory();
        IoEventLoop loop = new SingleThreadIoEventLoop(null, threadFactory,
                eventLoop -> new TestIoHandler(eventLoop) {
            @Override
            public boolean isCompatible(Class<? extends IoHandle> handleType) {
                return true;
            }
        });
        assertFalse(loop.isSuspended());
        IoRegistration registration = loop.register(new TestIoHandle()).sync().getNow();
        Thread currentThread = threadFactory.threads.take();
        assertTrue(currentThread.isAlive());
        assertTrue(loop.trySuspend());

        // Still should be alive as until the registration is cancelled we can not suspend the loop.
        assertTrue(currentThread.isAlive());

        registration.cancel();
        assertFalse(registration.isValid());

        // The current thread should be able to die now.
        currentThread.join();

        assertTrue(threadFactory.threads.isEmpty());
        loop.shutdownGracefully();
    }

    private static class TestIoHandler implements IoHandler {
        private final Semaphore semaphore = new Semaphore(0);
        private final ThreadAwareExecutor executor;

        TestIoHandler(ThreadAwareExecutor executor) {
            this.executor = executor;
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
                semaphore.acquire();
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
