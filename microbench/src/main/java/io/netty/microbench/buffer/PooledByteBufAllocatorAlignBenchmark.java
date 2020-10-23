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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class PooledByteBufAllocatorAlignBenchmark extends
        AbstractMicrobenchmark {

    private static final Random rand = new Random();

    /**
     * Cache line power of 2.
     */
    private static final int CACHE_LINE_MAX = 256;

    /**
     * PRNG to walk the chunk randomly to avoid streaming reads.
     */
    private static final int OFFSET_ADD = CACHE_LINE_MAX * 1337;

    /**
     * Block of bytes to write/read. (Corresponds to int type)
     */
    private static final int BLOCK = 4;

    @Param({ "0", "64" })
    private int cacheAlign;

    @Param({ "01024", "04096", "16384", "65536", "1048576" })
    private int size;

    private ByteBuf pooledDirectBuffer;

    private byte[] bytes;

    private int sizeMask;

    private int alignOffset;

    @Setup
    public void doSetup() {
        PooledByteBufAllocator pooledAllocator = new PooledByteBufAllocator(true, 4, 4, 8192, 11, 0,
                0, 0, true, cacheAlign);
        pooledDirectBuffer = pooledAllocator.directBuffer(size + 64);
        sizeMask = size - 1;
        if (cacheAlign == 0) {
            long addr = pooledDirectBuffer.memoryAddress();
            // make sure address is miss-aligned
            if (addr % 64 == 0) {
                alignOffset = 63;
            }
            int off = 0;
            for (int c = 0; c < size; c++) {
                off = (off + OFFSET_ADD) & sizeMask;
                if ((addr + off + alignOffset) % BLOCK == 0) {
                    throw new IllegalStateException(
                            "Misaligned address is not really aligned");
                }
            }
        } else {
            alignOffset = 0;
            int off = 0;
            long addr = pooledDirectBuffer.memoryAddress();
            for (int c = 0; c < size; c++) {
                off = (off + OFFSET_ADD) & sizeMask;
                if ((addr + off) % BLOCK != 0) {
                    throw new IllegalStateException(
                            "Aligned address is not really aligned");
                }
            }
        }
        bytes = new byte[BLOCK];
        rand.nextBytes(bytes);
    }

    @TearDown
    public void doTearDown() {
        pooledDirectBuffer.release();
    }

    @Benchmark
    public void writeRead() {
        int off = 0;
        int lSize = size;
        int lSizeMask = sizeMask;
        int lAlignOffset = alignOffset;
        for (int i = 0; i < lSize; i++) {
            off = (off + OFFSET_ADD) & lSizeMask;
            pooledDirectBuffer.setBytes(off + lAlignOffset, bytes);
            pooledDirectBuffer.getBytes(off + lAlignOffset, bytes);
        }
    }

    @Benchmark
    public void write() {
        int off = 0;
        int lSize = size;
        int lSizeMask = sizeMask;
        int lAlignOffset = alignOffset;
        for (int i = 0; i < lSize; i++) {
            off = (off + OFFSET_ADD) & lSizeMask;
            pooledDirectBuffer.setBytes(off + lAlignOffset, bytes);
        }
    }

    @Benchmark
    public void read() {
        int off = 0;
        int lSize = size;
        int lSizeMask = sizeMask;
        int lAlignOffset = alignOffset;
        for (int i = 0; i < lSize; i++) {
            off = (off + OFFSET_ADD) & lSizeMask;
            pooledDirectBuffer.getBytes(off + lAlignOffset, bytes);
        }
    }
}
