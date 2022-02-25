/*
 * Copyright 2017 The Netty Project
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnorderedThreadPoolEventExecutorTest {

    // See https://github.com/netty/netty/issues/6507
    @Test
    public void testNotEndlessExecute() throws Exception {
        UnorderedThreadPoolEventExecutor executor = new UnorderedThreadPoolEventExecutor(1);

        try {
            final CountDownLatch latch = new CountDownLatch(3);
            Runnable task = latch::countDown;
            executor.execute(task);
            Future<?> future = executor.submit(task).addListener((FutureListener<Object>) future1 -> latch.countDown());
            latch.await();
            future.syncUninterruptibly();

            // Now just check if the queue stays empty multiple times. This is needed as the submit to execute(...)
            // by DefaultPromise may happen in an async fashion
            for (int i = 0; i < 10000; i++) {
                assertTrue(executor.getQueue().isEmpty());
            }
        } finally {
            executor.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void scheduledAtFixedRateMustRunTaskRepeatedly() throws InterruptedException {
        UnorderedThreadPoolEventExecutor executor = new UnorderedThreadPoolEventExecutor(1);
        final CountDownLatch latch = new CountDownLatch(3);
        Future<?> future = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        }, 1, 1, TimeUnit.MILLISECONDS);
        try {
            latch.await();
        } finally {
            future.cancel();
            executor.shutdownGracefully();
        }
    }

    @Test
    public void testGetReturnsCorrectValueOnSuccess() throws Exception {
        UnorderedThreadPoolEventExecutor executor = new UnorderedThreadPoolEventExecutor(1);
        try {
            final String expected = "expected";
            Future<String> f = executor.submit(new Callable<String>() {
                @Override
                public String call() {
                    return expected;
                }
            });

            assertEquals(expected, f.get());
        } finally {
            executor.shutdownGracefully();
        }
    }

    @Test
    public void testGetReturnsCorrectValueOnFailure() throws Exception {
        UnorderedThreadPoolEventExecutor executor = new UnorderedThreadPoolEventExecutor(1);
        try {
            final RuntimeException cause = new RuntimeException();
            Future<String> f = executor.submit(new Callable<String>() {
                @Override
                public String call() {
                    throw cause;
                }
            });

            assertSame(cause, f.await().cause());
        } finally {
            executor.shutdownGracefully();
        }
    }

    @Test
    public void futuresMustHaveCorrectExecutor() {
        UnorderedThreadPoolEventExecutor executor = new UnorderedThreadPoolEventExecutor(1);
        Runnable runnable = () -> {
        };
        Callable<Void> callable = () -> null;
        Future<Void> future = null;

        try {
            future = executor.schedule(runnable, 0, TimeUnit.MILLISECONDS);
            assertSame(executor, future.executor());

            future.cancel();
            future = executor.schedule(callable, 0, TimeUnit.MILLISECONDS);
            assertSame(executor, future.executor());

            future.cancel();
            future = executor.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.MILLISECONDS);
            assertSame(executor, future.executor());

            future.cancel();
            future = executor.scheduleWithFixedDelay(runnable, 0, 1, TimeUnit.MILLISECONDS);
            assertSame(executor, future.executor());

            future.cancel();
            future = executor.submit(runnable);
            assertSame(executor, future.executor());

            future.cancel();
            future = executor.submit(callable);
            assertSame(executor, future.executor());
        } finally {
            future.cancel();
            executor.shutdownGracefully();
        }
    }
}
