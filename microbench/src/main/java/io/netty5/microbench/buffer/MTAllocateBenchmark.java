/*
 * Copyright 2022 The Netty Project
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
package io.netty5.microbench.buffer;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.concurrent.SingleThreadEventExecutor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This scenario performs a benchmark which simulates multiple threads doing many buffer allocations/releases.
 */
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 4, time = 5)
@Fork(value = 1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class MTAllocateBenchmark extends AbstractMicrobenchmark {

    /**
     * These executors are used to perform buffer intensitive allocations/releases from event-loop threads
     * (see testAllocateEventLoopThread method)
     */
    private SingleThreadEventExecutor[] execs;

    /**
     * JVM arguments.
     */
    @Override
    protected String[] jvmArgs() {
        return new String[] {
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+DebugNonSafepoints",
                "-Dio.netty5.leakDetection.level=disabled",
                "-Dio.netty5.buffer.leakDetectionEnabled=false",
                "-Dio.netty5.buffer.lifecycleTracingEnabled=false",
                // size of the shared cleaner pool used only by external threads (0=number of available processors)
                "-Dio.netty5.cleanerpool.size=0",
        };
    }

    /**
     * Creates event-loop threads with number of available processors)
     */
    @Setup
    public void setup() {
        execs = IntStream.range(0, Runtime.getRuntime().availableProcessors())
                .mapToObj(i -> new SingleThreadEventExecutor())
                .toArray(SingleThreadEventExecutor[]::new);
    }

    /**
     * Shutdown event-loop threads
     */
    @TearDown
    public void tearDown() {
        Stream.of(execs).forEach(eventExecutors -> eventExecutors.shutdownGracefully());
    }

    /**
     * Many external threads are allocating/dropping buffers concurrently.
     * The shared fixed-size cleaner pool will be used to distribute cleaners among
     * threads.
     */
    @Benchmark
    @Threads(Threads.MAX) // available processors
    @BenchmarkMode(Mode.Throughput)
    public void testAllocateExternalThread(Blackhole bh) {
        int size = ThreadLocalRandom.current().nextInt(1, 1024);
        try (Buffer buf = DefaultBufferAllocators.onHeapAllocator().allocate(size)) {
            bh.consume(buf);
        }
    }

    /**
     * Many event-loop threads are allocating/dropping many buffers concurrently.
     * There will be one Cleaner instance mapped to each event-loop thread.
     * Notice that using -Dio.netty5.cleanerpool.eventloop.usepool=true will
     * force event-loop threads to use the shared cleaner pool, but by default
     * this property is set to false.
     * @param bh
     * @throws InterruptedException
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void testAllocateEventLoopThread(Blackhole bh) throws InterruptedException {
        int cpus = Runtime.getRuntime().availableProcessors();
        final CountDownLatch latch = new CountDownLatch(cpus);

        Runnable task = () -> {
            for (int j = 0; j < 100000; j ++) {
                int size = ThreadLocalRandom.current().nextInt(1, 1024);
                try (Buffer buf = DefaultBufferAllocators.onHeapAllocator().allocate(size)) {
                    bh.consume(buf);
                }
            }
            latch.countDown();
        };

        IntStream.range(0, cpus).forEach(i -> execs[i].execute(task));
        latch.await();
    }
}
