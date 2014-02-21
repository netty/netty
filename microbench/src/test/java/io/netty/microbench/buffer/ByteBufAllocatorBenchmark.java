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
import org.openjdk.jmh.annotations.Param;

/**
 * This class benchmarks different allocators with different allocation sizes.
 */
public class ByteBufAllocatorBenchmark extends AbstractMicrobenchmark {

    private final ByteBufAllocator unpooledHeapAllocator = new UnpooledByteBufAllocator(false);
    private final ByteBufAllocator unpooledDirectAllocator = new UnpooledByteBufAllocator(true);
    private final ByteBufAllocator pooledHeapAllocator = new PooledByteBufAllocator(false);
    private final ByteBufAllocator pooledDirectAllocator = new PooledByteBufAllocator(true);

    @Param({ "00000", "00256", "01024", "04096", "16384", "65536" })
    public int size;

    @GenerateMicroBenchmark
    public void unpooledHeapAllocAndFree() {
        ByteBuf buffer = unpooledHeapAllocator.buffer(size);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void unpooledDirectAllocAndFree() {
        ByteBuf buffer = unpooledDirectAllocator.buffer(size);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledHeapAllocAndFree() {
        ByteBuf buffer = pooledHeapAllocator.buffer(size);
        buffer.release();
    }

    @GenerateMicroBenchmark
    public void pooledDirectAllocAndFree() {
        ByteBuf buffer = pooledDirectAllocator.buffer(size);
        buffer.release();
    }

}
