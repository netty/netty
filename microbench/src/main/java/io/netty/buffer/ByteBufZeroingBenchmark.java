/*
 * Copyright 2023 The Netty Project
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
package io.netty.buffer;

import io.netty.microbench.util.AbstractMicrobenchmark;
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

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class ByteBufZeroingBenchmark extends AbstractMicrobenchmark {

    @Param({
            "1",
            "2",
            "3",
            "4",
            "7",
            "8",
            "15",
            "64",
            "65",
            "1024",
    })
    private int bytes = 1024;
    @Param({
            "true",
            "false",
    })
    private boolean direct;
    @Param({
            "true",
            "false",
    })
    private boolean pooled;
    @Param({
            "false",
    })
    public String checkAccessible;

    @Param({
            "false",
    })
    public String checkBounds;
    @Param({
            "0",
            "1",
    })
    public int startOffset;
    private ByteBuf buffer;
    private int offset;

    @Setup
    public void setup() {
        System.setProperty("io.netty.buffer.checkAccessible", checkAccessible);
        System.setProperty("io.netty.buffer.checkBounds", checkBounds);
        ByteBufAllocator allocator = pooled? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT;
        int capacityRequired = startOffset + bytes;
        offset = 0;
        buffer = direct? alignedDirectAllocation(allocator, capacityRequired, 8) :
                allocator.heapBuffer(capacityRequired, capacityRequired);
        if (startOffset > 0) {
            offset += startOffset;
        }
    }

    private ByteBuf alignedDirectAllocation(ByteBufAllocator allocator, int bytes, final int alignment) {
        ByteBuf buffer = allocator.directBuffer(bytes + alignment, bytes + alignment);
        final long address = buffer.memoryAddress();
        final int remainder = (int) address % alignment;
        final int nextAlignedOffset = alignment - remainder;
        this.offset = nextAlignedOffset;
        return buffer;
    }

    @Benchmark
    public ByteBuf setZero() {
        final ByteBuf buffer = this.buffer;
        buffer.setZero(offset, bytes);
        return buffer;
    }

    @Benchmark
    public ByteBuf setBytes() {
        final ByteBuf buffer = this.buffer;
        final int offset = this.offset;
        for (int i = 0; i < bytes; i++) {
            buffer.setByte(offset + i, 0);
        }
        return buffer;
    }

    @TearDown
    public void teardown() {
        buffer.release();
    }

}
