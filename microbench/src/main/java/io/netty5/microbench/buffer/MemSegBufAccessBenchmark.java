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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MemSegBufAccessBenchmark {
    public enum BBufType {
        DIRECT {
            @Override
            Buffer newBuffer() {
                return BufferAllocator.offHeapUnpooled().allocate(64);
            }
        },
        HEAP {
            @Override
            Buffer newBuffer() {
                return BufferAllocator.onHeapUnpooled().allocate(64);
            }
        },
//        COMPOSITE {
//            @Override
//            Buffer newBuffer() {
//                return Unpooled.wrappedBuffer(UNSAFE.newBuffer(), HEAP.newBuffer());
//            }
//        },
//        NIO {
//            @Override
//            Buffer newBuffer() {
//                return new NioFacade(BBuffer.allocateDirect(64));
//            }
//        }
        ;
        abstract Buffer newBuffer();
    }

    @Param
    public BBufType bufferType;

    @Param("8")
    public int batchSize; // applies only to readBatch benchmark

    @Setup
    public void setup() {
        buffer = bufferType.newBuffer();
        buffer.writerOffset(batchSize);
    }

    private Buffer buffer;

    @TearDown
    public void tearDown() {
        buffer.close();
    }

    @Benchmark
    public long setGetLong() {
        return buffer.setLong(0, 1).getLong(0);
    }

    @Benchmark
    public Buffer setLong() {
        return buffer.setLong(0, 1);
    }

    @Benchmark
    public int readBatch() {
        buffer.readerOffset(0);
        int result = 0;
        // WARNING!
        // Please do not replace this sum loop with a BlackHole::consume loop:
        // BlackHole::consume could prevent the JVM to perform certain optimizations
        // forcing ByteBuf::readByte to be executed in order.
        // The purpose of the benchmark is to mimic accesses on ByteBuf
        // as in a real (single-threaded) case ie without (compiler) memory barriers that would
        // disable certain optimizations or would make bounds checks (if enabled)
        // to happen on each access.
        for (int i = 0, size = batchSize; i < size; i++) {
            result += buffer.readByte();
        }
        return result;
    }
}
