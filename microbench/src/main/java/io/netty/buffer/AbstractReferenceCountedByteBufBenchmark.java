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
package io.netty.buffer;

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class AbstractReferenceCountedByteBufBenchmark extends AbstractMicrobenchmark {

    @Param({
            "0",
            "1",
            "10",
            "100",
            "1000",
            "10000",
    })
    public int delay;

    AbstractReferenceCountedByteBuf buf;

    @Setup
    public void setUp() {
        buf = (AbstractReferenceCountedByteBuf) Unpooled.buffer(1);
    }

    @TearDown
    public void tearDown() {
        buf.release();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public boolean retainReleaseUncontended() {
        buf.retain();
        delay();
        return buf.release();
    }

    private void delay() {
        if (delay > 0) {
            Blackhole.consumeCPU(delay);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public boolean createUseAndRelease(Blackhole useBuffer) {
        ByteBuf unpooled = Unpooled.buffer(1);
        useBuffer.consume(unpooled);
        delay();
        return unpooled.release();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @GroupThreads(4)
    public boolean retainReleaseContended() {
        buf.retain();
        delay();
        return buf.release();
    }
}
