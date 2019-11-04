/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.microbench.concurrent;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class BurstCostExecutorsBenchmark extends AbstractMicrobenchmark {

    /**
     * This executor is useful as the best burst latency performer because it won't go to sleep and won't be hit by the
     * cost of being awaken on both offer/consumer side.
     */
    private static final class SpinExecutorService implements ExecutorService {

        private static final Runnable POISON_PILL = new Runnable() {
            @Override
            public void run() {
            }
        };
        private final Queue<Runnable> tasks;
        private final AtomicBoolean poisoned = new AtomicBoolean();
        private final Thread executorThread;

        SpinExecutorService(int maxTasks) {
            tasks = PlatformDependent.newFixedMpscQueue(maxTasks);
            executorThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    final Queue<Runnable> tasks = SpinExecutorService.this.tasks;
                    Runnable task;
                    while ((task = tasks.poll()) != POISON_PILL) {
                        if (task != null) {
                            task.run();
                        }
                    }
                }
            });
            executorThread.start();
        }

        @Override
        public void shutdown() {
            if (poisoned.compareAndSet(false, true)) {
                while (!tasks.offer(POISON_PILL)) {
                    // Just try again
                }
                try {
                    executorThread.join();
                } catch (InterruptedException e) {
                    //We're quite trusty :)
                }
            }
        }

        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isShutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            if (!tasks.offer(command)) {
                throw new RejectedExecutionException(
                        "If that happens, there is something wrong with the available capacity/burst size");
            }
        }
    }

    private enum ExecutorType {
        spinning,
        defaultEventExecutor,
        juc,
        nioEventLoop,
        epollEventLoop,
        kqueueEventLoop
    }

    @Param({ "1", "10" })
    private int burstLength;
    @Param({ "spinning", "epollEventLoop", "nioEventLoop", "defaultEventExecutor", "juc", "kqueueEventLoop" })
    private String executorType;
    @Param({ "0", "10" })
    private int work;

    private ExecutorService executor;
    private ExecutorService executorToShutdown;

    @Setup
    public void setup() {
        ExecutorType type = ExecutorType.valueOf(executorType);
        switch (type) {
        case spinning:
            //The case with 3 producers can have a peak of 3*burstLength offers:
            //4 is to leave some room between the offers and 1024 is to leave some room
            //between producer/consumer when work is > 0 and 1 producer.
            //If work = 0 then the task queue is supposed to be near empty most of the time.
            executor = new SpinExecutorService(Math.min(1024, burstLength * 4));
            executorToShutdown = executor;
            break;
        case defaultEventExecutor:
            executor = new DefaultEventExecutor();
            executorToShutdown = executor;
            break;
        case juc:
            executor = Executors.newSingleThreadScheduledExecutor();
            executorToShutdown = executor;
            break;
        case nioEventLoop:
            NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup(1);
            nioEventLoopGroup.setIoRatio(1);
            executor = nioEventLoopGroup.next();
            executorToShutdown = nioEventLoopGroup;
            break;
        case epollEventLoop:
            Epoll.ensureAvailability();
            EpollEventLoopGroup epollEventLoopGroup = new EpollEventLoopGroup(1);
            epollEventLoopGroup.setIoRatio(1);
            executor = epollEventLoopGroup.next();
            executorToShutdown = epollEventLoopGroup;
            break;
        case kqueueEventLoop:
            KQueue.ensureAvailability();
            KQueueEventLoopGroup kQueueEventLoopGroup = new KQueueEventLoopGroup(1);
            kQueueEventLoopGroup.setIoRatio(1);
            executor = kQueueEventLoopGroup.next();
            executorToShutdown = kQueueEventLoopGroup;
            break;
        }
    }

    @TearDown
    public void tearDown() {
        executorToShutdown.shutdown();
    }

    @State(Scope.Thread)
    public static class PerThreadState {
        //To reduce the benchmark noise we avoid using AtomicInteger that would
        //suffer of false sharing while reading/writing the counter due to the surrounding
        //instances on heap: thanks to JMH the "completed" field will be padded
        //avoiding false-sharing for free
        private static final AtomicIntegerFieldUpdater<PerThreadState> DONE_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(PerThreadState.class, "completed");
        private volatile int completed;

        private Runnable completeTask;

        @Setup
        public void setup(BurstCostExecutorsBenchmark bench) {
            final int work = bench.work;
            if (work > 0) {
                completeTask = new Runnable() {
                    @Override
                    public void run() {
                        Blackhole.consumeCPU(work);
                        //We can avoid the full barrier cost of a volatile set given that the
                        //benchmark is focusing on executors with a single threaded consumer:
                        //it would reduce the cost on consumer side while allowing to focus just
                        //to the threads hand-off/wake-up cost
                        DONE_UPDATER.lazySet(PerThreadState.this, completed + 1);
                    }
                };
            } else {
                completeTask = new Runnable() {
                    @Override
                    public void run() {
                        //We can avoid the full barrier cost of a volatile set given that the
                        //benchmark is focusing on executors with a single threaded consumer:
                        //it would reduce the cost on consumer side while allowing to focus just
                        //to the threads hand-off/wake-up cost
                        DONE_UPDATER.lazySet(PerThreadState.this, completed + 1);
                    }
                };
            }
        }

        /**
         * Single-writer reset of completed counter.
         */
        public void resetCompleted() {
            //We can avoid the full barrier cost of a volatile set given that
            //the counter can be reset from a single thread and it should be reset
            //only after any submitted tasks are completed
            DONE_UPDATER.lazySet(this, 0);
        }

        /**
         * It would spin-wait until at least {@code value} tasks are being completed.
         */
        public int spinWaitCompletionOf(int value) {
            while (true) {
                final int lastRead = this.completed;
                if (lastRead >= value) {
                    return lastRead;
                }
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(1)
    public int test1Producer(final PerThreadState state) {
        return executeBurst(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(2)
    public int test2Producers(final PerThreadState state) {
        return executeBurst(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(3)
    public int test3Producers(final PerThreadState state) {
        return executeBurst(state);
    }

    private int executeBurst(final PerThreadState state) {
        final ExecutorService executor = this.executor;
        final int burstLength = this.burstLength;
        final Runnable completeTask = state.completeTask;
        for (int i = 0; i < burstLength; i++) {
            executor.execute(completeTask);
        }
        final int value = state.spinWaitCompletionOf(burstLength);
        state.resetCompleted();
        return value;
    }
}
