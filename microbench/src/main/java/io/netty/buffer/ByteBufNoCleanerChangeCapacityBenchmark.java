/*
 * Copyright 2017 The Netty Project
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
package io.netty.buffer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@Threads(16)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
public class ByteBufNoCleanerChangeCapacityBenchmark extends AbstractByteBufNoCleanerBenchmark {
    private static final int MAX_DIRECT_MEMORY_PER_THREAD = 1024 * 1024; // 1 mb per thread.

    @Param("1024")
    public int initialCapacity;

    @Benchmark
    public boolean capacityChange() {
        ByteBuf buffer = bufferType.newBuffer(initialCapacity);
        // Change capacity until we would exceed the 1mb per thread limit
        for (int newCapacity = initialCapacity << 1; newCapacity <= MAX_DIRECT_MEMORY_PER_THREAD;
             newCapacity += initialCapacity) {
            buffer.capacity(newCapacity);
        }
        return buffer.release();
    }
}
