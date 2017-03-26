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
package io.netty.microbench.internal;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class UnitializedArrayBenchmark extends AbstractMicrobenchmark {

    @Param({ "1", "10", "100", "1000", "10000", "100000" })
    private int size;

    @Setup(Level.Trial)
    public void setupTrial() {
        if (PlatformDependent.javaVersion() < 9) {
            throw new IllegalStateException("Needs Java9");
        }
        if (!PlatformDependent.hasUnsafe()) {
            throw new IllegalStateException("Needs Unsafe");
        }
    }

    @Override
    protected String[] jvmArgs() {
        // Ensure we minimize the GC overhead for this benchmark and also open up required package.
        // See also https://shipilev.net/jvm-anatomy-park/7-initialization-costs/
        return new String[] { "-XX:+UseParallelOldGC", "-Xmx8g", "-Xms8g",
                "-Xmn6g", "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED" };
    }

    @Benchmark
    public byte[] allocateInitializedByteArray() {
        return new byte[size];
    }

    @Benchmark
    public byte[] allocateUninitializedByteArray() {
        return PlatformDependent.allocateUninitializedArray(size);
    }
}
