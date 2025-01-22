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
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SingleThreadIoEventLoopTest {

    @Test
    void testIsIoType() {
        class TestIoHandler2 extends TestIoHandler {
            TestIoHandler2(IoExecutor executor) {
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
        CompatibleTestIoHandler(IoExecutor executor) {
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

    private static final class TestThreadFactory implements ThreadFactory {
        final LinkedBlockingQueue<Thread> threads = new LinkedBlockingQueue<>();
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            threads.add(thread);
            return thread;
        }
    }

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
        registration.cancelFuture().sync();

        // The current thread should be able to die now.
        currentThread.join();

        assertTrue(threadFactory.threads.isEmpty());
        loop.shutdownGracefully();
    }

    private static class TestIoHandler implements IoHandler {
        private final Semaphore semaphore = new Semaphore(0);
        private final IoExecutor executor;

        TestIoHandler(IoExecutor executor) {
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
                private final Promise<?> cancellationPromise = executor.newPromise();
                @Override
                public long submit(IoOps ops) {
                    return 0;
                }

                @Override
                public void cancel() {
                    cancellationPromise.trySuccess(null);
                }

                @Override
                public IoHandler ioHandler() {
                    return TestIoHandler.this;
                }

                @Override
                public Future<?> cancelFuture() {
                    return cancellationPromise;
                }
            };
        }

        @Override
        public void wakeup() {
            semaphore.release();
        }

        @Override
        public int run(IoExecutorContext context) {
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
