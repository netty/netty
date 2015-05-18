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

import static io.netty.util.internal.StringUtil.asciiToLowerCase;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.HashCodeGenerator;
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@Threads(1)
@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@State(Scope.Benchmark)
public class PlatformDependentBenchmark extends AbstractMicrobenchmark {

    @Param({ "5", "10", "30", "50", "100", "1000", "10000", "100000" })
    private int size;
    private byte[] bytes1;
    private byte[] bytes2;
    private char[] chars1;
    private char[] chars2;
    private String string1;
    private String string2;
    private StringBuilder sb2;
    private HashCodeGenerator hasher = PlatformDependent.hashCodeGenerator();
    private HashCodeGenerator caseHasher = PlatformDependent.hashCodeGeneratorAsciiCaseInsensitive();

    @Setup(Level.Trial)
    public void setup() {
        bytes1 = new byte[size];
        bytes2 = new byte[size];
        chars1 = new char[size];
        chars2 = new char[size];
        for (int i = 0; i < size; i++) {
            bytes1[i] = bytes2[i] = (byte) i;
            chars1[i] = chars2[i] = (char) i;
        }
        string1 = new String(chars1);
        string2 = new String(chars2);
        sb2 = new StringBuilder(string2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeEqualsBytes() {
        return hasher.equals(bytes1, 0, bytes2, 0, bytes2.length);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean arraysEqualsBytes() {
        return Arrays.equals(bytes1, bytes2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeStringEquals() {
        return hasher.equals(string1, string2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean jdkStringEquals() {
        return string1.contentEquals(string2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeStringBuilderEquals() {
        return hasher.equals(string1, sb2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean jdkStringBuilderEquals() {
        return string1.contentEquals(sb2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int unsafeHashBytes() {
        return hasher.hashCode(bytes1);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int oldHashBytes() {
        int h = 0;
        for (int i = 0; i < bytes1.length; ++i) {
            h = h * 31 ^ bytes1[i] & 31;
        }
        return h;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int unsafeHashString() {
        return hasher.hashCode(string1, 0, string1.length());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int oldHashString() {
        int h = 0;
        for (int i = 0; i < string1.length(); ++i) {
            h = h * 31 ^ (byte) string1.charAt(i) & 31;
        }
        return h;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int unsafeHashCaseInsensitiveBytes() {
        return caseHasher.hashCode(bytes1, 0, bytes1.length);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int oldHashCaseInsensitiveBytes() {
        int h = 0;
        for (int i = 0; i < bytes1.length; ++i) {
            h = h * 31 ^ asciiToLowerCase(bytes1[i]) & 31;
        }
        return h;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int unsafeHashCaseInsensitiveString() {
        return caseHasher.hashCode(string1, 0, string1.length());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int oldHashCaseInsensitiveString() {
        int hash = 0;
        final int end = string1.length();
        for (int i = 0; i < end; i++) {
            hash = hash * 31 ^ asciiToLowerCase(string1.charAt(i)) & 31;
        }
        return hash;
    }
}
