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
package io.netty.microbenchmark.common;

import static io.netty.util.internal.StringUtil.asciiToLowerCase;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;

import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

@Threads(1)
@State(Scope.Benchmark)
public class AsciiStringBenchmark extends AbstractMicrobenchmark {

    public enum InputType {
        UPPER_CASE, LOWER_CASE, MIXED_CASE, RANDOM;
    }

    @Param({ "5", "10", "20", "50", "100" })
    private int size;

    @Param
    public InputType inputType;

    private AsciiString a;

    @Setup(Level.Trial)
    public void setup() {
        final int max = 255;
        final int min = 0;
        final int upperA = 'A';
        final int upperZ = 'Z';
        final int upperToLower = (int) 'a' - upperA;
        Random r = new Random();
        byte[] bytes = new byte[size];

        switch (inputType) {
        case UPPER_CASE:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) (r.nextInt((upperZ - upperA) + 1) + upperA);
            }
            break;
        case LOWER_CASE:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) ((r.nextInt((upperZ - upperA) + 1) + upperA) + upperToLower);
            }
            break;
        case MIXED_CASE:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) (r.nextInt((upperZ - upperA) + 1) + upperA);
                if ((i & 1) == 0) {
                    bytes[i] += upperToLower;
                }
            }
            break;
        case RANDOM:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) (r.nextInt((max - min) + 1) + min);
            }
            break;
        default:
            throw new Error();
        }

        a = new AsciiString(bytes, false);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int hashCodeCaseInsensitiveAscii() {
        return a.hashCodeCaseInsensitive();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int hashCodeCaseInsensitiveOldNettyAscii() throws Exception {
        ByteProcessor processor = new ByteProcessor() {
            private int hash;
            @Override
            public boolean process(byte value) throws Exception {
                hash = hash * 31 ^ asciiToLowerCase(value) & 31;
                return true;
            }

            @Override
            public int hashCode() {
                return hash;
            }
        };
        a.forEachByte(processor);
        return processor.hashCode();
    }
}
