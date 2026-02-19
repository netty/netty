/*
 * Copyright 2026 The Netty Project
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
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class VarHandleAccessBenchmark extends AbstractMicrobenchmark {

    private ByteBuf unsafeDirect;
    private ByteBuf unsafeHeap;

    @Setup
    public void setup() {
        unsafeDirect = new UnpooledUnsafeDirectByteBuf(ByteBufAllocator.DEFAULT, 8, 8);
        unsafeHeap = new UnpooledUnsafeHeapByteBuf(ByteBufAllocator.DEFAULT, 8, 8);
        unsafeDirect.setLong(0, 1L);
        unsafeHeap.setLong(0, 1L);
    }

    @TearDown
    public void tearDown() {
        unsafeDirect.release();
        unsafeHeap.release();
    }

    // UNALIGNED = false: should use VarHandle for multi-byte access
    @Benchmark
    @Fork(value = 1, jvmArgsAppend = "-Dio.netty.unalignedAccess=false")
    public long getLongDirectFalse() {
        return unsafeDirect.getLong(0);
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = "-Dio.netty.unalignedAccess=false")
    public long getLongHeapFalse() {
        return unsafeHeap.getLong(0);
    }
}
