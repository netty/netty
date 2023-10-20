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

import io.netty5.util.concurrent.AbstractScheduledEventExecutor.RunnableScheduledFutureNode;
import io.netty5.util.internal.ScheduledTaskQueue;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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

    @Test
    public void testScheduledTaskQueueSupplier_scheduleRunnableZero() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor(TestScheduledTaskQueue::new);
        Future<?> future = executor.schedule(TEST_RUNNABLE, 0, TimeUnit.NANOSECONDS);
        assertEquals(0, getDelay(future));
        assertNotNull(executor.pollScheduledTask());
        assertNull(executor.pollScheduledTask());
    }

    @Test
    public void testScheduledTaskQueueSupplier_testScheduleRunnableNegative() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor(TestScheduledTaskQueue::new);
        Future<?> future = executor.schedule(TEST_RUNNABLE, -1, TimeUnit.NANOSECONDS);
        assertEquals(0, getDelay(future));
        assertNotNull(executor.pollScheduledTask());
        assertNull(executor.pollScheduledTask());
    }

    @Test
    public void testScheduledTaskQueueSupplier_testScheduleCallableZero() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor(TestScheduledTaskQueue::new);
        Future<?> future = executor.schedule(TEST_CALLABLE, 0, TimeUnit.NANOSECONDS);
        assertEquals(0, getDelay(future));
        assertNotNull(executor.pollScheduledTask());
        assertNull(executor.pollScheduledTask());
    }

    @Test
    public void testScheduledTaskQueueSupplier_testScheduleCallableNegative() {
        TestScheduledEventExecutor executor = new TestScheduledEventExecutor(TestScheduledTaskQueue::new);
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
        assertEquals(Long.MAX_VALUE, AbstractScheduledEventExecutor.deadlineNanos(
                Ticker.systemTicker().nanoTime(), Long.MAX_VALUE));
    }

    private static final class TestScheduledEventExecutor extends AbstractScheduledEventExecutor {
        private TestScheduledEventExecutor() {
        }

        private TestScheduledEventExecutor(
                Supplier<ScheduledTaskQueue<RunnableScheduledFutureNode<?>>> scheduledTaskQueueSupplier) {
            super(scheduledTaskQueueSupplier);
        }

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

    private static final class TestScheduledTaskQueue implements ScheduledTaskQueue<RunnableScheduledFutureNode<?>> {
        private final LinkedBlockingQueue<RunnableScheduledFutureNode<?>> scheduledTaskQueue
                = new LinkedBlockingQueue<>();

        @Override
        public boolean removeTyped(RunnableScheduledFutureNode<?> task) {
            return scheduledTaskQueue.remove(task);
        }

        @Override
        public void clearIgnoringIndexes() {
            scheduledTaskQueue.clear();
        }

        @Override
        public int size() {
            return scheduledTaskQueue.size();
        }

        @Override
        public boolean isEmpty() {
            return scheduledTaskQueue.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return scheduledTaskQueue.contains(o);
        }

        @NotNull
        @Override
        public Iterator<RunnableScheduledFutureNode<?>> iterator() {
            return scheduledTaskQueue.iterator();
        }

        @NotNull
        @Override
        public Object[] toArray() {
            return scheduledTaskQueue.toArray();
        }

        @NotNull
        @Override
        public <T> T[] toArray(@NotNull T[] a) {
            return scheduledTaskQueue.toArray(a);
        }

        @Override
        public boolean add(RunnableScheduledFutureNode<?> task) {
            return scheduledTaskQueue.add(task);
        }

        @Override
        public boolean remove(Object o) {
            return scheduledTaskQueue.remove(o);
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return scheduledTaskQueue.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends RunnableScheduledFutureNode<?>> c) {
            return scheduledTaskQueue.addAll(c);
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return scheduledTaskQueue.removeAll(c);
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return scheduledTaskQueue.retainAll(c);
        }

        @Override
        public void clear() {
            scheduledTaskQueue.clear();
        }

        @Override
        public boolean offer(RunnableScheduledFutureNode<?> task) {
            return scheduledTaskQueue.offer(task);
        }

        @Override
        public RunnableScheduledFutureNode<?> remove() {
            return scheduledTaskQueue.remove();
        }

        @Override
        public RunnableScheduledFutureNode<?> poll() {
            return scheduledTaskQueue.poll();
        }

        @Override
        public RunnableScheduledFutureNode<?> element() {
            return scheduledTaskQueue.element();
        }

        @Override
        public RunnableScheduledFutureNode<?> peek() {
            return scheduledTaskQueue.peek();
        }
    }
}
