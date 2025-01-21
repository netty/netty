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
package io.netty.microbench.buffer;

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Threads(8)
public class ByteBufAllocatorConcurrentBenchmark  extends AbstractMicrobenchmark {

    private static final ByteBufAllocator unpooledAllocator = new UnpooledByteBufAllocator(true, true);
    private static final ByteBufAllocator pooledAllocator = PooledByteBufAllocator.DEFAULT;
    private static final ByteBufAllocator adaptiveAllocator = new AdaptiveByteBufAllocator();

    @Param({
            "00064",
            "00256",
            "01024",
            "04096",
    })
    public int size;

    @Benchmark
    public boolean allocateReleaseUnpooled() {
        return unpooledAllocator.directBuffer(size).release();
    }

    @Benchmark
    public boolean allocateReleasePooled() {
        return pooledAllocator.directBuffer(size).release();
    }

    @Benchmark
    public boolean allocateReleaseAdaptive() {
        return adaptiveAllocator.directBuffer(size).release();
    }
}
