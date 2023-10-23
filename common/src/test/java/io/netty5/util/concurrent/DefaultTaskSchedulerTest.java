/*
 * Copyright 2023 The Netty Project
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DefaultTaskSchedulerTest {

    private static final Runnable TEST_RUNNABLE = () -> {};

    private static final Callable<?> TEST_CALLABLE = Executors.callable(TEST_RUNNABLE);

    @Test
    public void schedule() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        int taskCount = 10;
        assertEmpty(scheduler);

        for (int i = 1; i <= taskCount; i++) {
            long deadlineNanos = executor.ticker().nanoTime() + i * TimeUnit.MILLISECONDS.toNanos(100);
            scheduler.schedule(new RunnableScheduledFutureAdapter<>(
                    executor, executor.newPromise(), TEST_CALLABLE, deadlineNanos, 0));
        }
        assertRemainTask(scheduler, taskCount);

        ticker.advanceMillis(500);
        for (int i = 0; i < 5; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertRemainTask(scheduler, 5);

        ticker.advanceMillis(300);
        for (int i = 0; i < 3; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertRemainTask(scheduler, 2);

        ticker.advanceMillis(200);
        for (int i = 0; i < 2; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertEmpty(scheduler);
    }

    @Test
    public void scheduleWithDelay_runnable() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        int taskCount = 10;
        assertEmpty(scheduler);

        for (int i = 1; i <= taskCount; i++) {
            scheduler.schedule(TEST_RUNNABLE, i * 100L, TimeUnit.MILLISECONDS);
        }
        assertRemainTask(scheduler, taskCount);

        ticker.advanceMillis(500);
        for (int i = 0; i < 5; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertRemainTask(scheduler, 5);

        ticker.advanceMillis(300);
        for (int i = 0; i < 3; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertRemainTask(scheduler, 2);

        ticker.advanceMillis(200);
        for (int i = 0; i < 2; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertEmpty(scheduler);
    }

    @Test
    public void scheduleWithDelay_callable() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        int taskCount = 10;
        assertEmpty(scheduler);

        for (int i = 1; i <= taskCount; i++) {
            scheduler.schedule(TEST_CALLABLE, i * 100L, TimeUnit.MILLISECONDS);
        }
        assertRemainTask(scheduler, taskCount);

        ticker.advanceMillis(500);
        for (int i = 0; i < 5; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertRemainTask(scheduler, 5);

        ticker.advanceMillis(300);
        for (int i = 0; i < 3; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertRemainTask(scheduler, 2);

        ticker.advanceMillis(200);
        for (int i = 0; i < 2; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertEmpty(scheduler);
    }

    @Test
    public void scheduleWithDelay_delayZero() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        assertEmpty(scheduler);

        scheduler.schedule(TEST_RUNNABLE, 0, TimeUnit.MILLISECONDS);
        scheduler.schedule(TEST_CALLABLE, 0, TimeUnit.MILLISECONDS);
        assertThat(scheduler.size()).isEqualTo(2);

        for (int i = 0; i < 2; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertEmpty(scheduler);
    }

    @Test
    public void scheduleWithDelay_delayNegative() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        assertEmpty(scheduler);

        scheduler.schedule(TEST_RUNNABLE, -1, TimeUnit.MILLISECONDS);
        scheduler.schedule(TEST_CALLABLE, -100, TimeUnit.MILLISECONDS);
        assertThat(scheduler.size()).isEqualTo(2);

        for (int i = 0; i < 2; i++) {
            assertThat(scheduler.pollScheduledTask()).isNotNull();
        }
        assertEmpty(scheduler);
    }

    @Test
    public void scheduleAtFixedRate() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        assertEmpty(scheduler);

        scheduler.scheduleAtFixedRate(TEST_RUNNABLE, 100L, 100L, TimeUnit.MILLISECONDS);
        assertRemainTask(scheduler, 1);

        ticker.advanceMillis(100);
        scheduler.pollScheduledTask().run();
        assertRemainTask(scheduler, 1);

        ticker.advanceMillis(150);
        scheduler.pollScheduledTask().run();
        assertRemainTask(scheduler, 1);

        ticker.advanceMillis(50);
        assertThat(scheduler.pollScheduledTask()).isNotNull();
        assertEmpty(scheduler);
    }

    @Test
    public void scheduleAtFixedRate_thrown() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        assertEmpty(scheduler);

        assertThatThrownBy(() -> scheduler
                .scheduleAtFixedRate(TEST_RUNNABLE, -1, 100L, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduler
                .scheduleAtFixedRate(TEST_RUNNABLE, 100, -1, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertEmpty(scheduler);
    }

    @Test
    public void scheduleWithFixedDelay() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        assertEmpty(scheduler);

        scheduler.scheduleWithFixedDelay(TEST_RUNNABLE, 100L, 100L, TimeUnit.MILLISECONDS);
        assertRemainTask(scheduler, 1);

        ticker.advanceMillis(100);
        scheduler.pollScheduledTask().run();
        assertRemainTask(scheduler, 1);

        ticker.advanceMillis(150);
        scheduler.pollScheduledTask().run();
        assertRemainTask(scheduler, 1);

        ticker.advanceMillis(50);
        assertRemainTask(scheduler, 1);

        ticker.advanceMillis(50);
        scheduler.pollScheduledTask().run();
        assertRemainTask(scheduler, 1);

        ticker.advanceMillis(100);
        assertThat(scheduler.pollScheduledTask()).isNotNull();
        assertEmpty(scheduler);
    }

    @Test
    public void scheduleWithFixedDelay_thrown() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        assertEmpty(scheduler);

        assertThatThrownBy(() -> scheduler
                .scheduleWithFixedDelay(TEST_RUNNABLE, -1, 100L, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduler
                .scheduleWithFixedDelay(TEST_RUNNABLE, 100L, -1, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertEmpty(scheduler);
    }


    @Test
    public void removeNextScheduledTask() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        int taskCount = 10;
        assertEmpty(scheduler);

        for (int i = 1; i <= taskCount; i++) {
            scheduler.schedule(TEST_RUNNABLE, i * 100L, TimeUnit.MILLISECONDS);
        }
        assertRemainTask(scheduler, taskCount);

        for (int i = 1; i <= taskCount; i++) {
            scheduler.removeNextScheduledTask();
        }
        assertEmpty(scheduler);
    }

    @Test
    public void removeScheduled() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();

        scheduler.schedule(TEST_CALLABLE,  100L, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(TEST_RUNNABLE, 100L, 100L, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(TEST_RUNNABLE, 100L, 100L, TimeUnit.MILLISECONDS);
        assertRemainTask(scheduler, 3);

        for (int i = 0; i < 3; i++) {
            scheduler.removeScheduled(scheduler.peekScheduledTask());
        }
        assertEmpty(scheduler);
    }

    @Test
    public void cancelScheduledTasks() {
        MockTicker ticker = Ticker.newMockTicker();
        AbstractScheduledEventExecutor executor = new TestScheduledEventExecutor(
                DefaultTaskSchedulerFactory.ISTANCE, ticker);
        TaskScheduler scheduler = executor.taskScheduler();
        int taskCount = 10;
        assertEmpty(scheduler);

        for (int i = 1; i <= taskCount; i++) {
            scheduler.schedule(TEST_RUNNABLE, i * 100L, TimeUnit.MILLISECONDS);
        }
        assertRemainTask(scheduler, taskCount);

        scheduler.cancelScheduledTasks();
        assertEmpty(scheduler);
    }

    private static void assertEmpty(TaskScheduler scheduler) {
        assertThat(scheduler.size()).isZero();
        assertThat(scheduler.isEmpty()).isTrue();
        assertThat(scheduler.peekScheduledTask()).isNull();;
        assertThat(scheduler.pollScheduledTask()).isNull();
    }

    private static void assertRemainTask(TaskScheduler scheduler, int taskCount) {
        assertThat(scheduler.size()).isEqualTo(taskCount);
        assertThat(scheduler.isEmpty()).isFalse();
        assertThat(scheduler.peekScheduledTask()).isNotNull();
        assertThat(scheduler.pollScheduledTask()).isNull();
    }

    private static final class TestScheduledEventExecutor extends AbstractScheduledEventExecutor {

        private final Ticker ticker;

        private TestScheduledEventExecutor(TaskSchedulerFactory taskSchedulerFactory, Ticker ticker) {
            super(taskSchedulerFactory);
            this.ticker = ticker;
        }

        @Override
        protected Ticker ticker() {
            return ticker;
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
}
