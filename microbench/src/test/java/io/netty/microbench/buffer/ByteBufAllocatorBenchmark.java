/*
 * Copyright 2012 The Netty Project
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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * This class benchmarks different allocators with different allocation sizes.
 */
@State(Scope.Thread)
public class ByteBufAllocatorBenchmark extends AbstractMicrobenchmark {

    private final ByteBufAllocator unpooledHeapAllocator = new UnpooledByteBufAllocator(false);
    private final ByteBufAllocator unpooledDirectAllocator = new UnpooledByteBufAllocator(true);
    private final ByteBufAllocator pooledHeapAllocator = new PooledByteBufAllocator(false);
    private final ByteBufAllocator pooledDirectAllocator = new PooledByteBufAllocator(true);

    @GenerateMicroBenchmark
    public void unpooledHeapAllocAndFree_1_0() {
        ByteBuf buffer = unpooledHeapAllocator.buffer(0);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledHeapAllocAndFree_2_256() {
        ByteBuf buffer = unpooledHeapAllocator.buffer(256);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledHeapAllocAndFree_3_1024() {
        ByteBuf buffer = unpooledHeapAllocator.buffer(1024);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledHeapAllocAndFree_4_4096() {
        ByteBuf buffer = unpooledHeapAllocator.buffer(4096);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledHeapAllocAndFree_5_16384() {
        ByteBuf buffer = unpooledHeapAllocator.buffer(16384);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledHeapAllocAndFree_6_65536() {
        ByteBuf buffer = unpooledHeapAllocator.buffer(65536);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledDirectAllocAndFree_1_0() {
        ByteBuf buffer = unpooledDirectAllocator.buffer(0);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledDirectAllocAndFree_2_256() {
        ByteBuf buffer = unpooledDirectAllocator.buffer(256);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledDirectAllocAndFree_3_1024() {
        ByteBuf buffer = unpooledDirectAllocator.buffer(1024);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledDirectAllocAndFree_4_4096() {
        ByteBuf buffer = unpooledDirectAllocator.buffer(4096);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledDirectAllocAndFree_5_16384() {
        ByteBuf buffer = unpooledDirectAllocator.buffer(16384);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledDirectAllocAndFree_6_65536() {
        ByteBuf buffer = unpooledDirectAllocator.buffer(65536);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledHeapAllocAndFree_1_0() {
        ByteBuf buffer = pooledHeapAllocator.buffer(0);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledHeapAllocAndFree_2_256() {
        ByteBuf buffer = pooledHeapAllocator.buffer(256);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledHeapAllocAndFree_3_1024() {
        ByteBuf buffer = pooledHeapAllocator.buffer(1024);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledHeapAllocAndFree_4_4096() {
        ByteBuf buffer = pooledHeapAllocator.buffer(4096);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledHeapAllocAndFree_5_16384() {
        ByteBuf buffer = pooledHeapAllocator.buffer(16384);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledHeapAllocAndFree_6_65536() {
        ByteBuf buffer = pooledHeapAllocator.buffer(65536);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledDirectAllocAndFree_1_0() {
        ByteBuf buffer = pooledDirectAllocator.buffer(0);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledDirectAllocAndFree_2_256() {
        ByteBuf buffer = pooledDirectAllocator.buffer(256);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledDirectAllocAndFree_3_1024() {
        ByteBuf buffer = pooledDirectAllocator.buffer(1024);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledDirectAllocAndFree_4_4096() {
        ByteBuf buffer = pooledDirectAllocator.buffer(4096);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledDirectAllocAndFree_5_16384() {
        ByteBuf buffer = pooledDirectAllocator.buffer(16384);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledDirectAllocAndFree_6_65536() {
        ByteBuf buffer = pooledDirectAllocator.buffer(65536);
        buffer.release();
    }
}
