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
import io.netty.util.concurrent.DefaultEventExecutorGroup;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class BurstCostExecutorsBenchmark extends AbstractMicrobenchmark {

    private enum ExecutorType {
        defaultEventExecutor,
        juc,
        nioEventLoop,
        epollEventLoop,
        kqueueEventLoop
    }

    @Param({ "1", "10" })
    private int burstLength;
    @Param({ "epollEventLoop", "nioEventLoop", "defaultEventExecutor", "juc", "kqueueEventLoop" })
    private String executorType;
    @Param({ "0", "10" })
    private int work;
    @Param({ "1", "2" })
    private int consumers;

    private ExecutorService executor;

    @Setup
    public void setup() {
        ExecutorType type = ExecutorType.valueOf(executorType);
        switch (type) {
        case defaultEventExecutor:
            executor = new DefaultEventExecutorGroup(consumers);
            break;
        case juc:
            executor = Executors.newSingleThreadScheduledExecutor();
            break;
        case nioEventLoop:
            NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup(consumers);
            nioEventLoopGroup.setIoRatio(1);
            executor = nioEventLoopGroup;
            break;
        case epollEventLoop:
            Epoll.ensureAvailability();
            EpollEventLoopGroup epollEventLoopGroup = new EpollEventLoopGroup(consumers);
            epollEventLoopGroup.setIoRatio(1);
            executor = epollEventLoopGroup;
            break;
        case kqueueEventLoop:
            KQueue.ensureAvailability();
            KQueueEventLoopGroup kQueueEventLoopGroup = new KQueueEventLoopGroup(consumers);
            kQueueEventLoopGroup.setIoRatio(1);
            executor = kQueueEventLoopGroup;
            break;
        }
    }

    @TearDown
    public void tearDown() {
        executor.shutdown();
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
            final int consumers = bench.consumers;
            final int work = bench.work;
            //we are forced to create different versions of this Runnable, because we can't use lambdas:
            //lambdas would allow work and consumers fields to be trusted by the JVM if passed through capturing
            if (consumers == 1) {
                if (work > 0) {
                    completeTask = new Runnable() {
                        @Override
                        public void run() {
                            Blackhole.consumeCPU(work);
                            //We can avoid the full barrier cost of a volatile set when the
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
                            //We can avoid the full barrier cost of a volatile set when the
                            //benchmark is focusing on executors with a single threaded consumer:
                            //it would reduce the cost on consumer side while allowing to focus just
                            //to the threads hand-off/wake-up cost
                            DONE_UPDATER.lazySet(PerThreadState.this, completed + 1);
                        }
                    };
                }
            } else {
                if (work > 0) {
                    completeTask = new Runnable() {
                        @Override
                        public void run() {
                            Blackhole.consumeCPU(work);
                            DONE_UPDATER.getAndIncrement(PerThreadState.this);
                        }
                    };
                } else {
                    completeTask = new Runnable() {
                        @Override
                        public void run() {
                            DONE_UPDATER.getAndIncrement(PerThreadState.this);
                        }
                    };
                }
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
    @BenchmarkMode(Mode.AverageTime)
    @Threads(1)
    public int test1Producer(final PerThreadState state) {
        return executeBurst(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Threads(2)
    public int test2Producers(final PerThreadState state) {
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
