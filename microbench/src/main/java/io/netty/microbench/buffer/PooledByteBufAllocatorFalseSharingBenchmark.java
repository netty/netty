/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@Threads(8)
public class PooledByteBufAllocatorFalseSharingBenchmark extends AbstractMicrobenchmark {
    private static final byte BYTE = 'a';
    private final AtomicInteger index = new AtomicInteger();
    private final int threads = PooledByteBufAllocatorFalseSharingBenchmark.class.getAnnotation(Threads.class).value();
    private final ByteBuf[] buffers = new ByteBuf[threads];
    private final ByteBuf[] buffersNoFalseSharing = new ByteBuf[threads];

    @Param({ "00512" }) //, "01024", "02048", "04096", "08192" })
    public int size;

    @Setup
    public void setup() {
        fillBuffers(buffers, size, new PooledByteBufAllocator(true, 2, 2, 8192, 11, 0, 0, 0, false));
        fillBuffers(buffersNoFalseSharing, size, new PooledByteBufAllocator(true, 2, 2, 8192, 11, 0, 0, 0, true));
        index.set(0);
    }

    private static void fillBuffers(ByteBuf[] buffers, int size, ByteBufAllocator allocator) {
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = allocator.directBuffer(size);
        }
    }

    @TearDown
    public void destroy() {
        release(buffers);
        release(buffersNoFalseSharing);
    }

    private static void release(ByteBuf[] buffers) {
        for (ByteBuf buf: buffers) {
            buf.release();
        }
    }

    @Benchmark
    public void falseSharing() {
        benchmark(buffers[index()]);
    }

    @Benchmark
    public void noFalseSharing() {
        benchmark(buffersNoFalseSharing[index()]);
    }

    private int index() {
        return index.getAndIncrement() & threads - 1;
    }

    private static void benchmark(ByteBuf buffer) {
        buffer.setByte(0, BYTE);
    }
}
