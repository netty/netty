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

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AbstractScheduledEventExecutorTest {
    private static final Runnable TEST_RUNNABLE = () -> {
    };

    private static final Callable<?> TEST_CALLABLE = Executors.callable(TEST_RUNNABLE);

    @Test
    public void testScheduleRunnableZero() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor();
        Future<?> future = executor.schedule(TEST_RUNNABLE, 0, TimeUnit.NANOSECONDS);
        assertEquals(0, getDelay(future));
        assertNotNull(executor.pollScheduledTask());
        assertNull(executor.pollScheduledTask());
    }

    @Test
    public void testScheduleRunnableNegative() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor();
        Future<?> future = executor.schedule(TEST_RUNNABLE, -1, TimeUnit.NANOSECONDS);
        assertEquals(0, getDelay(future));
        assertNotNull(executor.pollScheduledTask());
        assertNull(executor.pollScheduledTask());
    }

    @Test
    public void testScheduleCallableZero() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor();
        Future<?> future = executor.schedule(TEST_CALLABLE, 0, TimeUnit.NANOSECONDS);
        assertEquals(0, getDelay(future));
        assertNotNull(executor.pollScheduledTask());
        assertNull(executor.pollScheduledTask());
    }

    @Test
    public void testScheduleCallableNegative() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor();
        Future<?> future = executor.schedule(TEST_CALLABLE, -1, TimeUnit.NANOSECONDS);
        assertEquals(0, getDelay(future));
        assertNotNull(executor.pollScheduledTask());
        assertNull(executor.pollScheduledTask());
    }

    private static long getDelay(Future<?> future) {
        return ((RunnableScheduledFuture<?>) future).delayNanos();
    }

    @Test
    public void testScheduleAtFixedRateRunnableZero() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor();
        assertThrows(IllegalArgumentException.class,
            () -> executor.scheduleAtFixedRate(TEST_RUNNABLE, 0, 0, TimeUnit.DAYS));
    }

    @Test
    public void testScheduleAtFixedRateRunnableNegative() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor();
        assertThrows(IllegalArgumentException.class,
            () -> executor.scheduleAtFixedRate(TEST_RUNNABLE, 0, -1, TimeUnit.DAYS));
    }

    @Test
    public void testScheduleWithFixedDelayZero() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor();
        assertThrows(IllegalArgumentException.class,
            () -> executor.scheduleWithFixedDelay(TEST_RUNNABLE, 0, -1, TimeUnit.DAYS));
    }

    @Test
    public void testScheduleWithFixedDelayNegative() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor();
        assertThrows(IllegalArgumentException.class,
            () -> executor.scheduleWithFixedDelay(TEST_RUNNABLE, 0, -1, TimeUnit.DAYS));
    }

    @Test
    public void testDeadlineNanosNotOverflow() {
        assertEquals(Long.MAX_VALUE, AbstractScheduledEventExecutor.deadlineNanos(Long.MAX_VALUE));
    }

    private static final class TestScheduledEventExecutor extends AbstractScheduledEventExecutor {
        @Override
        public boolean isShuttingDown() {
            return false;
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return true;
        }

        @Override
        public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> terminationFuture() {
            throw new UnsupportedOperationException();
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
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public void execute(Runnable task) {
            throw new UnsupportedOperationException();
        }
    }
}
