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
package io.netty5.microbench.buffer;

import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.adapt.AdaptivePoolingAllocator;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Threads(8)
public class BufferAllocatorConcurrentBenchmark extends AbstractMicrobenchmark {

    private static final BufferAllocator unpooledAllocator = BufferAllocator.offHeapUnpooled();
    private static final BufferAllocator pooledAllocator = BufferAllocator.offHeapPooled();
    private static final BufferAllocator adaptiveAllocator = new AdaptivePoolingAllocator(true);

    @Param({ "00064"/*, "00256", "01024", "04096"*/ })
    public int size;

    @Benchmark
    public void allocateReleaseUnpooled() {
        unpooledAllocator.allocate(size).close();
    }

    @Benchmark
    public void allocateReleasePooled() {
        pooledAllocator.allocate(size).close();
    }

    @Benchmark
    public void allocateReleaseAdaptive() {
        adaptiveAllocator.allocate(size).close();
    }
}
