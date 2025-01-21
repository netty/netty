/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.NettyRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonStickyEventExecutorGroupTest {
    private static final String PARAMETERIZED_NAME = "{index}: maxTaskExecutePerRun = {0}";

    @Test
    public void testInvalidGroup() {
        final EventExecutorGroup group = new DefaultEventExecutorGroup(1);
        try {
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    new NonStickyEventExecutorGroup(group);
                }
            });
        } finally {
            group.shutdownGracefully();
        }
    }

    public static Collection<Object[]> data() throws Exception {
        List<Object[]> params = new ArrayList<Object[]>();
        params.add(new Object[] {64});
        params.add(new Object[] {256});
        params.add(new Object[] {1024});
        params.add(new Object[] {Integer.MAX_VALUE});
        return params;
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testOrdering(int maxTaskExecutePerRun) throws Throwable {
        final int threads = NettyRuntime.availableProcessors() * 2;
        final EventExecutorGroup group = new UnorderedThreadPoolEventExecutor(threads);
        final NonStickyEventExecutorGroup nonStickyGroup = new NonStickyEventExecutorGroup(group, maxTaskExecutePerRun);
        try {
            final CountDownLatch startLatch = new CountDownLatch(1);
            final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
            List<Thread> threadList = new ArrayList<Thread>(threads);
            for (int i = 0 ; i < threads; i++) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            execute(nonStickyGroup, startLatch);
                        } catch (Throwable cause) {
                            error.compareAndSet(null, cause);
                        }
                    }
                });
                threadList.add(thread);
                thread.start();
            }
            startLatch.countDown();
            for (Thread t: threadList) {
                t.join();
            }
            Throwable cause = error.get();
            if (cause != null) {
                throw cause;
            }
        } finally {
            nonStickyGroup.shutdownGracefully();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    public void testRaceCondition(int maxTaskExecutePerRun) throws InterruptedException {
        EventExecutorGroup group = new UnorderedThreadPoolEventExecutor(1);
        NonStickyEventExecutorGroup nonStickyGroup = new NonStickyEventExecutorGroup(group, maxTaskExecutePerRun);

        try {
            EventExecutor executor = nonStickyGroup.next();

            for (int j = 0; j < 5000; j++) {
                final CountDownLatch firstCompleted = new CountDownLatch(1);
                final CountDownLatch latch = new CountDownLatch(2);
                for (int i = 0; i < 2; i++) {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            firstCompleted.countDown();
                            latch.countDown();
                        }
                    });
                    assertTrue(firstCompleted.await(1, TimeUnit.SECONDS));
                }

                assertTrue(latch.await(5, TimeUnit.SECONDS));
            }
        } finally {
            nonStickyGroup.shutdownGracefully();
        }
    }

    private static void execute(EventExecutorGroup group, CountDownLatch startLatch) throws Throwable {
        final EventExecutor executor = group.next();
        assertTrue(executor instanceof OrderedEventExecutor);
        final AtomicReference<Throwable> cause = new AtomicReference<Throwable>();
        final AtomicInteger last = new AtomicInteger();
        int tasks = 10000;
        List<Future<?>> futures = new ArrayList<Future<?>>(tasks);
        final CountDownLatch latch = new CountDownLatch(tasks);
        startLatch.await();

        for (int i = 1 ; i <= tasks; i++) {
            final int id = i;
            assertFalse(executor.inEventLoop());
            assertFalse(executor.inEventLoop(Thread.currentThread()));
            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        assertTrue(executor.inEventLoop(Thread.currentThread()));
                        assertTrue(executor.inEventLoop());

                        if (cause.get() == null) {
                            int lastId = last.get();
                            if (lastId >= id) {
                                cause.compareAndSet(null, new AssertionError(
                                        "Out of order execution id(" + id + ") >= lastId(" + lastId + ')'));
                            }
                            if (!last.compareAndSet(lastId, id)) {
                                cause.compareAndSet(null, new AssertionError("Concurrent execution of tasks"));
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }
            }));
        }
        latch.await();
        for (Future<?> future: futures) {
            future.syncUninterruptibly();
        }
        Throwable error = cause.get();
        if (error != null) {
            throw error;
        }
    }
}
