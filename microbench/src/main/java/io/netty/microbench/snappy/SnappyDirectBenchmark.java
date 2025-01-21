/*
 * Copyright 2024 The Netty Project
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
package io.netty.microbench.snappy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.compression.Snappy;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.AuxCounters.Type;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 3)
public class SnappyDirectBenchmark extends AbstractMicrobenchmark {

    @Param({ "true", "false" })
    public boolean reuseHashTable;
    private ByteBuf buffer;
    private Snappy snappy;
    private ByteBuf in;
    private ByteBuf out;

    @Param({ "4096", "2048", "1024", "512", "256", "128" })
    private int bufferSizeInBytes;

    @AuxCounters(value = Type.OPERATIONS)
    @State(Scope.Thread)
    public static class AllocationMetrics {
        private long inputSize;

        private long outputSize;

        public long compressedRatio() {
            return inputSize / outputSize;
        }
    }

    @Setup
    public void setup() throws UnsupportedEncodingException {
        ByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;
        buffer = allocator.buffer(bufferSizeInBytes);

        if (reuseHashTable) {
            snappy = Snappy.withHashTableReuse();
        } else {
            snappy = new Snappy();
        }

        byte[] compressibleByteArray = new byte[buffer.writableBytes()];
        Arrays.fill(compressibleByteArray, (byte) 1);
        buffer.writeBytes(compressibleByteArray);

        in = Unpooled.wrappedBuffer(compressibleByteArray);
        out = Unpooled.directBuffer();
    }

    @Benchmark
    public ByteBuf encode(AllocationMetrics allocationMetrics) {
        int length = in.readableBytes();
        snappy.encode(in, out, length);
        in.resetReaderIndex();
        allocationMetrics.inputSize += length;
        allocationMetrics.outputSize += out.readableBytes();
        out.setIndex(0, 0);

        return out;
    }

    @TearDown(Level.Trial)
    public void teardown() {
        buffer.release();
        buffer = null;
        out.release();
        out = null;
    }
}
