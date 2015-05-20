/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@Threads(16)
public class PooledByteBufAllocatorFalseSharingBenchmark extends AbstractMicrobenchmark {
    private static final byte BYTE = 'a';

    private PooledByteBufAllocator allocator;
    private PooledByteBufAllocator allocatorCacheLineSize;

    @Setup
    public void setup() {
        allocator = new PooledByteBufAllocator(true, 4, 4, 8192, 11, 512, 256, 64, false);
        allocatorCacheLineSize = new PooledByteBufAllocator(true, 4, 4, 8192, 11, 512, 256, 64, true);
    }

    @Param({ "00004", "00256", "01024", "04096", "16384" })
    public int size;

    @Benchmark
    public void disablePowerofCacheLine(Blackhole hole) {
        alloc(allocator, size, hole);
    }

    @Benchmark
    public void enablePowerofCacheLine(Blackhole hole) {
        alloc(allocatorCacheLineSize, size, hole);
    }

    private static void alloc(ByteBufAllocator allocator, int size, Blackhole hole) {
        ByteBuf buffer = allocator.buffer(size);
        for (int a = 0; a < size; a++) {
            buffer.setByte(a, BYTE);
            hole.consume(buffer.getByte(a));
        }
        buffer.release();
    }
}
