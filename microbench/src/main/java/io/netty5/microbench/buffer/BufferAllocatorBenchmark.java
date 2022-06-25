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
package io.netty5.microbench.buffer;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.pool.PooledBufferAllocator;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.Random;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;

/**
 * This class benchmarks different allocators with different allocation sizes.
 */
@State(Scope.Benchmark)
public class BufferAllocatorBenchmark extends AbstractMicrobenchmark {

    private static final BufferAllocator unpooledAllocator = BufferAllocator.offHeapUnpooled();
    private static final BufferAllocator pooledAllocator = new PooledBufferAllocator(
            MemoryManager.instance(), true, 4, 8192, 9, 0, 0, true, 0); // Disable thread-local cache

    private static final int MAX_LIVE_BUFFERS = 8192;
    private static final Random rand = new Random();
    private static final Buffer[] unpooledHeapBuffers = new Buffer[MAX_LIVE_BUFFERS];
    private static final Buffer[] unpooledDirectBuffers = new Buffer[MAX_LIVE_BUFFERS];
    private static final Buffer[] pooledHeapBuffers = new Buffer[MAX_LIVE_BUFFERS];
    private static final Buffer[] pooledDirectBuffers = new Buffer[MAX_LIVE_BUFFERS];
    private static final Buffer[] defaultPooledHeapBuffers = new Buffer[MAX_LIVE_BUFFERS];
    private static final Buffer[] defaultPooledDirectBuffers = new Buffer[MAX_LIVE_BUFFERS];

    @Param({ "00000", "00256", "01024", "04096", "16384", "65536" })
    public int size;

    @Benchmark
    public void unpooledAllocAndFree() {
        int idx = rand.nextInt(unpooledHeapBuffers.length);
        Buffer oldBuf = unpooledHeapBuffers[idx];
        if (oldBuf != null) {
            oldBuf.close();
        }
        unpooledHeapBuffers[idx] = unpooledAllocator.allocate(size);
    }

    @Benchmark
    public void pooledAllocAndFree() {
        int idx = rand.nextInt(pooledHeapBuffers.length);
        Buffer oldBuf = pooledHeapBuffers[idx];
        if (oldBuf != null) {
            oldBuf.close();
        }
        pooledHeapBuffers[idx] = pooledAllocator.allocate(size);
    }

    @Benchmark
    public void defaultPooledAllocAndFree() {
        int idx = rand.nextInt(defaultPooledHeapBuffers.length);
        Buffer oldBuf = defaultPooledHeapBuffers[idx];
        if (oldBuf != null) {
            oldBuf.close();
        }
        defaultPooledHeapBuffers[idx] = preferredAllocator().allocate(size);
    }
}
