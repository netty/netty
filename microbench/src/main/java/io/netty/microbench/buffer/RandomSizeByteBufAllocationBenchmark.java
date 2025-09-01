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
package io.netty.microbench.buffer;

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.SuppressJava6Requirement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RandomSizeByteBufAllocationBenchmark extends AbstractMicrobenchmark {

    private static final int SEED = 42;
    // see adaptive allocator size classes
    private static final int[] SIZE_CLASSES = {
            32,
            64,
            128,
            256,
            512,
            640, // 512 + 128
            1024,
            1152, // 1024 + 128
            2048,
            2304, // 2048 + 256
            4096,
            4352, // 4096 + 256
            8192,
            8704, // 8192 + 512
            16384,
            16896, // 16384 + 512
    };

    public enum AllocatorType {
        JEMALLOC,
        ADAPTIVE
    }

    @Param({ "ADAPTIVE" })
    public AllocatorType allocatorType = AllocatorType.ADAPTIVE;
    @Param({ "128", "128000" })
    public int samples;

    private ByteBufAllocator allocator;
    private short[] sizeSamples;
    private int sampleMask;
    private int nextSampleIndex;

    static {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    }

    @Setup
    @SuppressJava6Requirement(reason = "using SplittableRandom to reliably produce data")
    public void init() {
        if (!(Thread.currentThread() instanceof FastThreadLocalThread)) {
            throw new IllegalStateException("This benchmark must be run with FastThreadLocalThread: run it with: " +
        "-Djmh.executor=CUSTOM -Djmh.executor.class=io.netty.microbench.util.AbstractMicrobenchmark$HarnessExecutor");
        }
        switch (allocatorType) {
        case JEMALLOC:
            allocator = new PooledByteBufAllocator(true);
            break;
        case ADAPTIVE:
            allocator = new AdaptiveByteBufAllocator(true, true);
            break;
        default:
            throw new IllegalArgumentException("Unknown allocator type: " + allocatorType);
        }
        samples = MathUtil.findNextPositivePowerOfTwo(samples);
        sampleMask = samples - 1;
        sizeSamples = new short[samples];
        SplittableRandom rnd = new SplittableRandom(SEED);
        // here we're not using random size [0, 16896] because if the size class is too large
        // it has more chances to be picked!
        for (int i = 0; i < samples; i++) {
            // pick a random size class
            int sizeClass = rnd.nextInt(SIZE_CLASSES.length);
            // now pick a random size within the size class
            short size =
                    (short) rnd.nextInt(sizeClass == 0? 0 : SIZE_CLASSES[sizeClass - 1] + 1, SIZE_CLASSES[sizeClass]);
            if (size < 0) {
                throw new IllegalArgumentException("Size sample out of range: " + size);
            }
            sizeSamples[i] = size;
        }
    }

    private int nextSize() {
        int index = nextSampleIndex;
        nextSampleIndex = (nextSampleIndex + 1) & sampleMask;
        return sizeSamples[index];
    }

    @Benchmark
    public void allocateAndRelease(Blackhole bh) {
        int size = nextSize();
        ByteBuf buffer = allocator.buffer(size);
        bh.consume(buffer);
        buffer.release();
    }
}
