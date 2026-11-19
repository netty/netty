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
package io.netty.microbench.buffer;

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Gets the memory addresses of direct {@link AdaptiveByteBufAllocator} buffers, as a gathering write to a socket does
 * for each buffer that it writes. It measures the throughput of the calls with warm caches at a monomorphic call site.
 */
@State(Scope.Benchmark)
public class AdaptiveByteBufMemoryAddressBenchmark extends AbstractMicrobenchmark {

    @Param({ "16" })
    public int buffers;

    @Param({ "8192" })
    public int size;

    private ByteBuf[] bufs;

    @Setup
    public void setup() {
        AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();
        bufs = new ByteBuf[buffers];
        for (int i = 0; i < buffers; i++) {
            bufs[i] = allocator.directBuffer(size);
            if (!bufs[i].hasMemoryAddress()) {
                throw new IllegalStateException("The buffers have no memory address");
            }
        }
    }

    @TearDown
    public void tearDown() {
        for (ByteBuf buf : bufs) {
            buf.release();
        }
    }

    @Benchmark
    public long memoryAddress() {
        long sum = 0;
        for (ByteBuf buf : bufs) {
            sum += buf.memoryAddress() + buf.readerIndex();
        }
        return sum;
    }
}
