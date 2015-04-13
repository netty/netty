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
package io.netty.microbench.internal;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.Arrays;

@Threads(1)
@State(Scope.Benchmark)
public class PlatformDependentBenchmark extends AbstractMicrobenchmark {

    @Param({ "10", "50", "100", "1000", "10000", "100000" })
    private String size;
    private byte[] bytes1;
    private byte[] bytes2;

    @Setup(Level.Trial)
    public void setup() {
        int size = Integer.parseInt(this.size);
        bytes1 = new byte[size];
        bytes2 = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes1[i] = (byte) i;
            bytes2[i] = (byte) i;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeBytesEqual() {
        return PlatformDependent.equals(bytes1, 0, bytes1.length, bytes2, 0, bytes2.length);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean arraysBytesEqual() {
        return Arrays.equals(bytes1, bytes2);
    }
}
