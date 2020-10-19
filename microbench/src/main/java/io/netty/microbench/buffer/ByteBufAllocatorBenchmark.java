/*
 * Copyright 2012 The Netty Project
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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.Random;

/**
 * This class benchmarks different allocators with different allocation sizes.
 */
@State(Scope.Benchmark)
public class ByteBufAllocatorBenchmark extends AbstractMicrobenchmark {

    private static final ByteBufAllocator unpooledAllocator = new UnpooledByteBufAllocator(true);
    private static final ByteBufAllocator pooledAllocator =
            new PooledByteBufAllocator(true, 4, 4, 8192, 11, 0, 0, 0, true, 0); // Disable thread-local cache

    private static final int MAX_LIVE_BUFFERS = 8192;
    private static final Random rand = new Random();
    private static final ByteBuf[] unpooledHeapBuffers = new ByteBuf[MAX_LIVE_BUFFERS];
    private static final ByteBuf[] unpooledDirectBuffers = new ByteBuf[MAX_LIVE_BUFFERS];
    private static final ByteBuf[] pooledHeapBuffers = new ByteBuf[MAX_LIVE_BUFFERS];
    private static final ByteBuf[] pooledDirectBuffers = new ByteBuf[MAX_LIVE_BUFFERS];
    private static final ByteBuf[] defaultPooledHeapBuffers = new ByteBuf[MAX_LIVE_BUFFERS];
    private static final ByteBuf[] defaultPooledDirectBuffers = new ByteBuf[MAX_LIVE_BUFFERS];

    @Param({ "00000", "00256", "01024", "04096", "16384", "65536" })
    public int size;

    @Benchmark
    public void unpooledHeapAllocAndFree() {
        int idx = rand.nextInt(unpooledHeapBuffers.length);
        ByteBuf oldBuf = unpooledHeapBuffers[idx];
        if (oldBuf != null) {
            oldBuf.release();
        }
        unpooledHeapBuffers[idx] = unpooledAllocator.heapBuffer(size);
    }

    @Benchmark
    public void unpooledDirectAllocAndFree() {
        int idx = rand.nextInt(unpooledDirectBuffers.length);
        ByteBuf oldBuf = unpooledDirectBuffers[idx];
        if (oldBuf != null) {
            oldBuf.release();
        }
        unpooledDirectBuffers[idx] = unpooledAllocator.directBuffer(size);
    }

    @Benchmark
    public void pooledHeapAllocAndFree() {
        int idx = rand.nextInt(pooledHeapBuffers.length);
        ByteBuf oldBuf = pooledHeapBuffers[idx];
        if (oldBuf != null) {
            oldBuf.release();
        }
        pooledHeapBuffers[idx] = pooledAllocator.heapBuffer(size);
    }

    @Benchmark
    public void pooledDirectAllocAndFree() {
        int idx = rand.nextInt(pooledDirectBuffers.length);
        ByteBuf oldBuf = pooledDirectBuffers[idx];
        if (oldBuf != null) {
            oldBuf.release();
        }
        pooledDirectBuffers[idx] = pooledAllocator.directBuffer(size);
    }

    @Benchmark
    public void defaultPooledHeapAllocAndFree() {
        int idx = rand.nextInt(defaultPooledHeapBuffers.length);
        ByteBuf oldBuf = defaultPooledHeapBuffers[idx];
        if (oldBuf != null) {
            oldBuf.release();
        }
        defaultPooledHeapBuffers[idx] = PooledByteBufAllocator.DEFAULT.heapBuffer(size);
    }

    @Benchmark
    public void defaultPooledDirectAllocAndFree() {
        int idx = rand.nextInt(defaultPooledDirectBuffers.length);
        ByteBuf oldBuf = defaultPooledDirectBuffers[idx];
        if (oldBuf != null) {
            oldBuf.release();
        }
        defaultPooledDirectBuffers[idx] = PooledByteBufAllocator.DEFAULT.directBuffer(size);
    }
}
