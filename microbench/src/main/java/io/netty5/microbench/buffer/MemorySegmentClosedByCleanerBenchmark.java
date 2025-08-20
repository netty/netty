/*
 * Copyright 2020 The Netty Project
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
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.MemoryManager;
import io.netty5.buffer.memseg.SegmentMemoryManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedFuture;

@Warmup(iterations = 15, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(value = 5, jvmArgsAppend = { "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints" })
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class MemorySegmentClosedByCleanerBenchmark {
    private static final BufferAllocator heap;
    private static final BufferAllocator heapPooled;
    private static final BufferAllocator direct;
    private static final BufferAllocator directPooled;

    static {
        class Allocators {
            final BufferAllocator heap;
            final BufferAllocator pooledHeap;
            final BufferAllocator direct;
            final BufferAllocator pooledDirect;

            Allocators(BufferAllocator heap, BufferAllocator pooledHeap,
                       BufferAllocator direct, BufferAllocator pooledDirect) {
                this.heap = heap;
                this.pooledHeap = pooledHeap;
                this.direct = direct;
                this.pooledDirect = pooledDirect;
            }
        }

        var allocs = MemoryManager.using(new SegmentMemoryManager(), () -> {
            return new Allocators(BufferAllocator.onHeapUnpooled(), BufferAllocator.onHeapPooled(),
                    BufferAllocator.offHeapUnpooled(), BufferAllocator.offHeapPooled());
        });

        heap = allocs.heap;
        heapPooled = allocs.pooledHeap;
        direct = allocs.direct;
        directPooled = allocs.pooledDirect;
    }

    @Param({"heavy", "light"})
    public String workload;
    public boolean isHeavy;

    @Setup
    public void setUp() {
        if ("heavy".equals(workload)) {
            isHeavy = true;
        } else if ("light".equals(workload)) {
            isHeavy = false;
        } else {
            throw new IllegalArgumentException("Unsupported workload: " + workload);
        }
    }

    @Benchmark
    public Buffer explicitCloseHeap() throws Exception {
        try (Buffer buf = process(heap.allocate(256))) {
            return buf;
        }
    }

    @Benchmark
    public Buffer explicitPooledCloseHeap() throws Exception {
        try (Buffer buf = process(heapPooled.allocate(256))) {
            return buf;
        }
    }

    @Benchmark
    public Buffer explicitCloseDirect() throws Exception {
        try (Buffer buf = process(direct.allocate(256))) {
            return buf;
        }
    }

    @Benchmark
    public Buffer explicitPooledCloseDirect() throws Exception {
        try (Buffer buf = process(directPooled.allocate(256))) {
            return buf;
        }
    }

    @Benchmark
    public Buffer cleanerClose() throws Exception {
        return process(direct.allocate(256));
    }

    @Benchmark
    public Buffer cleanerClosePooled() throws Exception {
        return process(directPooled.allocate(256));
    }

    private Buffer process(Buffer buffer) throws Exception {
        // Simulate some async network server thingy, processing the buffer.
        var tlr = ThreadLocalRandom.current();
        if (isHeavy) {
            return completedFuture(buffer.send()).thenApplyAsync(send -> {
                try (Buffer buf = send.receive()) {
                    while (buf.writableBytes() > 0) {
                        buf.writeByte((byte) tlr.nextInt());
                    }
                    return buf.send();
                }
            }).thenApplyAsync(send -> {
                try (Buffer buf = send.receive()) {
                    byte b = 0;
                    while (buf.readableBytes() > 0) {
                        b += buf.readByte();
                    }
                    buf.fill(b);
                    return buf.send();
                }
            }).get().receive();
        } else {
            while (buffer.writableBytes() > 0) {
                buffer.writeByte((byte) tlr.nextInt());
            }
            byte b = 0;
            while (buffer.readableBytes() > 0) {
                b += buffer.readByte();
            }
            buffer.fill(b);
            return buffer;
        }
    }
}
