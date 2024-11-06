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
package io.netty.microbench.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SimpleByteBufPooledAllocatorBenchmark extends AbstractMicrobenchmark {

    public SimpleByteBufPooledAllocatorBenchmark() {
        super(true, false);
    }

    @Param({
            "123",
            "1234",
            "12345",
            "123456",
            "1234567",
    })
    public int size;

    @Param({
            "0",
            "5",
            "10",
            "100",
    })
    public long tokens;

    @Param({
            "true",
            "false",
    })
    public boolean useThreadCache;

    public ByteBufAllocator allocator;

    @Setup(Level.Trial)
    public void doSetup() {
        allocator = new PooledByteBufAllocator(
                PooledByteBufAllocator.defaultPreferDirect(),
                PooledByteBufAllocator.defaultNumHeapArena(),
                PooledByteBufAllocator.defaultNumDirectArena(),
                PooledByteBufAllocator.defaultPageSize(),
                PooledByteBufAllocator.defaultMaxOrder(),
                PooledByteBufAllocator.defaultTinyCacheSize(),
                PooledByteBufAllocator.defaultSmallCacheSize(),
                PooledByteBufAllocator.defaultNormalCacheSize(),
                useThreadCache);
    }

    @Benchmark
    public boolean getAndRelease() {
        ByteBuf buf = allocator.directBuffer(size);
        if (tokens > 0) {
            Blackhole.consumeCPU(tokens);
        }
        return buf.release();
    }
}
