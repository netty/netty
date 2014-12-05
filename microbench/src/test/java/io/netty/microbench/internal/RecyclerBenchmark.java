/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.microbench.internal;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.Recycler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class benchmarks a recyclers capability to provide instances in a multi thread scenario.
 *
 * For this benchmark Mode.Throughput it is not relevant but the number of offered instances by the recycler is.
 * This benchmark uses a class cloned from RecyclableArrayList because it needs to count new instances built.
 *
 * For 2 threads = 1 extractor thread and 1 recycler thread.
 * For 3 threads = 1 extractor thread and 2 recycler threads.
 * For 4 threads = 2 extractor threads and 2 recycler threads.
 */
@Threads(3)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
public class RecyclerBenchmark extends AbstractMicrobenchmark {

    @Param({ "00000", "00256", "01024", "04096", "16384", "65536" })
    public int size;

    // Benchmark state used to count instances obtained from recycler, new instances built by recycler and instances
    // recycled.
    // It also contains the queue used to share instances between threads.
    @State(Scope.Benchmark)
    public static class BenchState {
        // Maybe a better method exists to trigger some actions after warm-ups.
        public static final int WARMUP_ITERATIONS = RecyclerBenchmark.class.getAnnotation(Warmup.class).iterations();
        // Number that limit the queue size, when this number is reached only recyclers threads
        // (threads that are executing recycle) are allowed to operate.
        private static final int MAX_COLL_SIZE = 512;
        // Queue shared by threads that extract from pool and threads that recycle back into pool.
        private final ConcurrentLinkedQueue<TestList> collection = new ConcurrentLinkedQueue<TestList>();
        // Counter for instances in queue waiting to be recycled back into recycler's pool.
        public final AtomicInteger queueSize = new AtomicInteger(0);
        // Counter for instances obtained from recycler.
        public final AtomicInteger obtainedCounter = new AtomicInteger(0);
        // Current benchmark iteration number.
        public volatile int iteration;
        // Total number of new instances built in this benchmark.
        public volatile int totalNewInstances;
        // Total number of obtained instances in this benchmark.
        public volatile int totalObtained;

        @TearDown(Level.Iteration)
        public void tearDownIteration() {
            StringBuilder sb = new StringBuilder();
            sb.append(" | Instances Constructed: " + String.format("%9d", TestList.newInstanceCounter.get()));
            sb.append(" Instances Obtained: " + String.format("%9d", obtainedCounter.get()));
            sb.append(" Queue size: " + String.format("%6d", size()) + " | ");
            System.out.print(sb);

            if (++iteration == WARMUP_ITERATIONS) {
                // after warmup iteration we reset the counter for obtained instances.
                totalObtained = 0;
            }

            totalNewInstances += TestList.newInstanceCounter.getAndSet(0);
            totalObtained += obtainedCounter.getAndSet(0);
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() {
            StringBuilder sb = new StringBuilder();
            sb.append(" Total Instances Constructed: " + totalNewInstances);
            sb.append(" Total Instances Obtained: " + totalObtained);
            sb.append(" Constructed Percent: " + totalNewInstances / (float) totalObtained * 100 + '%');
            sb.append(" Queue size: " + size());
            System.out.println();
            System.out.println(sb);
        }

        public int size() {
            return queueSize.get();
        }

        public void offer(final TestList list) {
            collection.offer(list);
            obtainedCounter.incrementAndGet();
            queueSize.incrementAndGet();
        }

        public TestList poll() {
            TestList result = collection.poll();
            if (result != null) {
                queueSize.decrementAndGet();
            }
            return result;
        }
    }

    /**
     * Thread state used to classify threads into extractors (threads that obtain instances from recycler) and recyclers
     * (threads that recycle back instances into recycler).
     */
    @State(Scope.Thread)
    public static class ThreadState {
        private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
        public boolean isExtracting;

        public ThreadState() {
            final int id = ID_GENERATOR.getAndIncrement();
            isExtracting = id % 2 != 0;
        }
    }

    @Benchmark
    public void recycleProducerConsumer(BenchState benchState, ThreadState threadState) {
        if (threadState.isExtracting) {
            // Thread that extract instances from recycler.

            if (benchState.size() >= BenchState.MAX_COLL_SIZE) {
                // Threads that poll from queue are behind.
                Thread.yield();
                return;
            }

            TestList list = TestList.newInstance(size);
            benchState.offer(list);
        } else {
            // Thread that recycles instances back to recycler.

            TestList queueList = benchState.poll();
            if (queueList != null) {
                queueList.recycle();
            }
        }
    }

    // A clone for RecyclableArrayList class which counts new instances built.
    public static final class TestList extends ArrayList<Object> {

        private static final long serialVersionUID = -8605125654176467947L;

        private static final int DEFAULT_INITIAL_CAPACITY = 8;

        public static AtomicInteger newInstanceCounter = new AtomicInteger(0);

        private static final Recycler<TestList> RECYCLER = new Recycler<TestList>() {
            @Override
            protected TestList newObject(Recycler.Handle handle) {
                newInstanceCounter.incrementAndGet();
                return new TestList(handle);
            }
        };

        public static TestList newInstance() {
            return newInstance(DEFAULT_INITIAL_CAPACITY);
        }

        public static TestList newInstance(int minCapacity) {
            TestList ret = RECYCLER.get();
            ret.ensureCapacity(minCapacity);
            return ret;
        }

        private final Recycler.Handle handle;

        private TestList(Recycler.Handle handle) {
            this(handle, DEFAULT_INITIAL_CAPACITY);
        }

        private TestList(Recycler.Handle handle, int initialCapacity) {
            super(initialCapacity);
            this.handle = handle;
        }

        public boolean recycle() {
            clear();
            return handle.recycle();
        }
    }
}
