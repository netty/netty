/*
 * Copyright 2025 The Netty Project
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
package io.netty.microbench.concurrent;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Measurement(iterations = 10)
@Warmup(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FastThreadLocalCheckBenchmark extends AbstractMicrobenchmark {

    @Param({ "8", "16", "32", "64", "128", "256"})
    public int eventLoops;
    private Thread[] eventLoopThreads;
    private CountDownLatch eventLoopControl;

    public FastThreadLocalCheckBenchmark() {
        super(true, true);
    }

    @Setup
    public void setup() {
        if (Thread.currentThread() instanceof FastThreadLocalThread) {
            throw new IllegalStateException("This benchmark must not be run in a FastThreadLocalThread");
        }

        long benchmarkThreadId = Thread.currentThread().getId();
        // create an ordered array of thread IDs which cause the maximum number of branches
        // but still making it fairly predictable for the CPU.
        long[] eventLoopIds = new long[eventLoops];
        for (int i = 0; i < eventLoops; i++) {
            eventLoopIds[i] = benchmarkThreadId + i + 1;
        }

        eventLoopControl = new CountDownLatch(1);
        CountDownLatch eventLoopsRegistered = new CountDownLatch(eventLoops);
        eventLoopThreads = new Thread[eventLoops];

        class FastThreadLocalThread extends Thread {
            private final long id;

            FastThreadLocalThread(long id) {
                this.id = id;
            }

            @Override
            public long getId() {
                return id;
            }

            @Override
            public void run() {
                io.netty.util.concurrent.FastThreadLocalThread.runWithFastThreadLocal(() -> {
                    try {
                        eventLoopsRegistered.countDown();
                        eventLoopControl.await();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                });
            }
        }

        int i = 0;
        for (long eventLoopId : eventLoopIds) {
            FastThreadLocalThread thread = new FastThreadLocalThread(eventLoopId);
            thread.start();
            eventLoopThreads[i++] = thread;
        }

        try {
            eventLoopsRegistered.await();
        } catch (InterruptedException e) {
            // wait till all event loops are registered.
        }
    }

    @Benchmark
    public boolean isFastThreadLocalThread() {
        return FastThreadLocalThread.currentThreadHasFastThreadLocal();
    }

    @TearDown
    public void tearDown() {
        eventLoopControl.countDown();
        for (Thread thread : eventLoopThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
}
