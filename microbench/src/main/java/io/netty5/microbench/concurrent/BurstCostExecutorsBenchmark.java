/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.microbench.concurrent;

import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.epoll.Epoll;
import io.netty5.channel.epoll.EpollHandler;
import io.netty5.channel.kqueue.KQueue;
import io.netty5.channel.kqueue.KQueueHandler;
import io.netty5.channel.nio.NioHandler;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.concurrent.DefaultThreadFactory;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.EventExecutorGroup;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.concurrent.RejectedExecutionHandlers;
import io.netty5.util.concurrent.SingleThreadEventExecutor;
import io.netty5.util.concurrent.UnorderedThreadPoolEventExecutor;
import io.netty5.util.internal.PlatformDependent;
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

import java.util.Collections;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class BurstCostExecutorsBenchmark extends AbstractMicrobenchmark {

    /**
     * This executor is useful as the best burst latency performer because it won't go to sleep and won't be hit by the
     * cost of being awaken on both offer/consumer side.
     */
    private static final class SpinExecutorService implements EventExecutorGroup {
        private static final Runnable POISON_PILL = () -> {
        };
        private final Queue<Runnable> tasks;
        private final AtomicBoolean poisoned = new AtomicBoolean();
        private final Thread executorThread;
        private final Promise<Void> terminationFuture = ImmediateEventExecutor.INSTANCE.newPromise();

        SpinExecutorService(int maxTasks) {
            tasks = PlatformDependent.newFixedMpscQueue(maxTasks);
            executorThread = new Thread(() -> {
                Runnable task;
                while ((task = tasks.poll()) != POISON_PILL) {
                    if (task != null) {
                        task.run();
                    }
                }
            });
            executorThread.start();
        }

        @Override
        public boolean isShuttingDown() {
            return poisoned.get();
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
        public Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable task) {
            if (!tasks.offer(task)) {
                throw new RejectedExecutionException(
                        "If that happens, there is something wrong with the available capacity/burst size");
            }
        }

        @Override
        public Future<Void> shutdownGracefully() {
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
            terminationFuture.trySuccess(null);
            return terminationFuture.asFuture();
        }

        @Override
        public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            return shutdownGracefully();
        }

        @Override
        public Future<Void> terminationFuture() {
            return terminationFuture.asFuture();
        }

        @Override
        public EventExecutor next() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<EventExecutor> iterator() {
            return Collections.emptyIterator();
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

    private EventExecutorGroup executor;
    private EventExecutorGroup executorToShutdown;

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
            executor = new SingleThreadEventExecutor();
            executorToShutdown = executor;
            break;
        case juc:
            executor = new UnorderedThreadPoolEventExecutor(1);
            executorToShutdown = executor;
            break;
        case nioEventLoop:
            EventLoopGroup nioEventLoopGroup = new MultithreadEventLoopGroup(1,
                    new DefaultThreadFactory(MultithreadEventLoopGroup.class), NioHandler.newFactory(),
                    Integer.MAX_VALUE, RejectedExecutionHandlers.reject(), Integer.MAX_VALUE);
            executor = nioEventLoopGroup.next();
            executorToShutdown = nioEventLoopGroup;
            break;
        case epollEventLoop:
            Epoll.ensureAvailability();
            EventLoopGroup epollEventLoopGroup = new MultithreadEventLoopGroup(1,
                    new DefaultThreadFactory(MultithreadEventLoopGroup.class), EpollHandler.newFactory(),
                    Integer.MAX_VALUE, RejectedExecutionHandlers.reject(), Integer.MAX_VALUE);
            executor = epollEventLoopGroup.next();
            executorToShutdown = epollEventLoopGroup;
            break;
        case kqueueEventLoop:
            KQueue.ensureAvailability();
            EventLoopGroup kqueueEventLoopGroup = new MultithreadEventLoopGroup(1,
                    new DefaultThreadFactory(MultithreadEventLoopGroup.class), KQueueHandler.newFactory(),
                    Integer.MAX_VALUE, RejectedExecutionHandlers.reject(), Integer.MAX_VALUE);
            executor = kqueueEventLoopGroup.next();
            executorToShutdown = kqueueEventLoopGroup;
            break;
        }
    }

    @TearDown
    public void tearDown() {
        executorToShutdown.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
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
                completeTask = () -> {
                    Blackhole.consumeCPU(work);
                    //We can avoid the full barrier cost of a volatile set given that the
                    //benchmark is focusing on executors with a single threaded consumer:
                    //it would reduce the cost on consumer side while allowing to focus just
                    //to the threads hand-off/wake-up cost
                    DONE_UPDATER.lazySet(this, completed + 1);
                };
            } else {
                completeTask = () -> {
                    //We can avoid the full barrier cost of a volatile set given that the
                    //benchmark is focusing on executors with a single threaded consumer:
                    //it would reduce the cost on consumer side while allowing to focus just
                    //to the threads hand-off/wake-up cost
                    DONE_UPDATER.lazySet(this, completed + 1);
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
                final int lastRead = completed;
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
        final EventExecutorGroup executor = this.executor;
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
