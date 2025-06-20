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
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * This class benchmarks different allocators with different allocation sizes.
 */
@State(Scope.Benchmark)
public class ByteBufAllocateAndGrowBenchmark extends AbstractMicrobenchmark {

    private static final ByteBufAllocator pooledAllocator = PooledByteBufAllocator.DEFAULT;
    private static final ByteBufAllocator adaptiveAllocator = new AdaptiveByteBufAllocator();

    private static final int MAX_LIVE_BUFFERS = 2048;
    private static final Random rand = new Random();
    private static final ByteBuf[] pooledDirectBuffers = new ByteBuf[MAX_LIVE_BUFFERS];
    private static final ByteBuf[] adaptiveDirectBuffers = new ByteBuf[MAX_LIVE_BUFFERS];

    @Param({
            "00256",
            "01024",
            "04096",
            "16384",
            "65536",
    })
    public int size;

    @TearDown
    public void releaseBuffers() {
        List<ByteBuf[]> bufferLists = Arrays.asList(
                pooledDirectBuffers,
                adaptiveDirectBuffers);
        for (ByteBuf[] bufs : bufferLists) {
            for (ByteBuf buf : bufs) {
                if (buf != null && buf.refCnt() > 0) {
                    buf.release();
                }
            }
            Arrays.fill(bufs, null);
        }
    }

    @Benchmark
    public void pooledDirectAllocAndFree(BufStats stats) {
        int idx = rand.nextInt(pooledDirectBuffers.length);
        ByteBuf oldBuf = pooledDirectBuffers[idx];
        if (oldBuf != null) {
            oldBuf.release();
        }
        ByteBuf buf = pooledAllocator.directBuffer();
        expandBuffer(buf, stats);
        pooledDirectBuffers[idx] = buf;
    }

    @Benchmark
    public void adaptiveDirectAllocAndFree(BufStats stats) {
        int idx = rand.nextInt(adaptiveDirectBuffers.length);
        ByteBuf oldBuf = adaptiveDirectBuffers[idx];
        if (oldBuf != null) {
            oldBuf.release();
        }
        ByteBuf buf = adaptiveAllocator.directBuffer();
        expandBuffer(buf, stats);
        adaptiveDirectBuffers[idx] = buf;
    }

    private void expandBuffer(ByteBuf buf, BufStats stats) {
        stats.record(buf);
        while (buf.capacity() < size) {
            buf.capacity(2 * buf.capacity());
            stats.record(buf);
        }
    }

    @State(Scope.Thread)
    @AuxCounters
    public static class BufStats {
        long bufCounts;
        long bufSizeSum;
        long bufFastCapSum;

        void record(ByteBuf byteBuf) {
            bufCounts++;
            bufSizeSum += byteBuf.capacity();
            bufFastCapSum += byteBuf.maxFastWritableBytes();
        }

        public double avgSize() {
            return bufSizeSum / (double) bufCounts;
        }

        public double avgFastCap() {
            return bufFastCapSum / (double) bufCounts;
        }
    }
}
