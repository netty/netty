/*
 * Copyright 2020 The Netty Project
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
import io.netty.util.internal.SuppressJava6Requirement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
public class ByteBufIndexOfBenchmark extends AbstractMicrobenchmark {

    @Param({ "7", "16", "23", "32" })
    int size;

    @Param({ "4", "11" })
    int logPermutations;

    @Param({ "1" })
    int seed;

    int permutations;

    ByteBuf[] data;
    private int i;

    @Param({ "0" })
    private byte needleByte;

    @Param({ "true", "false" })
    private boolean direct;
    @Param({ "false", "true" })
    private boolean noUnsafe;

    @Param({ "false", "true" })
    private boolean pooled;

    @Setup(Level.Trial)
    @SuppressJava6Requirement(reason = "using SplittableRandom to reliably produce data")
    public void init() {
        System.setProperty("io.netty.noUnsafe", Boolean.valueOf(noUnsafe).toString());
        SplittableRandom random = new SplittableRandom(seed);
        permutations = 1 << logPermutations;
        this.data = new ByteBuf[permutations];
        final ByteBufAllocator allocator = pooled? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT;
        for (int i = 0; i < permutations; ++i) {
            data[i] = direct? allocator.directBuffer(size, size) : allocator.heapBuffer(size, size);
            for (int j = 0; j < size; j++) {
                int value = random.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
                // turn any found value into something different
                if (value == needleByte) {
                    if (needleByte != 1) {
                        value = 1;
                    } else {
                        value = 0;
                    }
                }
                data[i].setByte(j, value);
            }
            final int foundIndex = random.nextInt(Math.max(0, size - 8), size);
            data[i].setByte(foundIndex, needleByte);
        }
    }

    private ByteBuf getData() {
        return data[i++ & (permutations - 1)];
    }

    @Benchmark
    public int indexOf() {
        return getData().indexOf(0, size, needleByte);
    }

    @TearDown
    public void releaseBuffers() {
        for (ByteBuf buffer : data) {
            buffer.release();
        }
    }

}
