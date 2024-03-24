/*
 * Copyright 2020 The Netty Project
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
import io.netty.buffer.UnpooledNewVersion;
import io.netty.buffer.UnpooledOldVersion;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.CharsetUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class UnpooledBenchmark extends AbstractMicrobenchmark {
    @Param({"100", "500", "1000"})
    private int length;

    private String asciiString;
    private char[] asciiCharArray;

    private String utf8String;
    private char[] utf8CharArray;

    @Setup
    public void setup() {
        // Use buffer sizes that will also allow to write UTF-8 without grow the buffer
        StringBuilder asciiSequence = new StringBuilder(length);
        char[] asciiSeed = "abcdefg".toCharArray();
        for (int i = 0; i < length; i++) {
            asciiSequence.append(asciiSeed[i % asciiSeed.length]);
        }
        asciiString = asciiSequence.toString();
        asciiCharArray = asciiString.toCharArray();

        // Generate some mixed UTF-8 String for benchmark
        StringBuilder utf8Sequence = new StringBuilder(length);
        char[] utf8Seed = "UTF-8äÄ∏ŒŒ".toCharArray();
        for (int i = 0; i < length; i++) {
            utf8Sequence.append(utf8Seed[i % utf8Seed.length]);
        }
        utf8String = utf8Sequence.toString();
        utf8CharArray = utf8String.toCharArray();
    }

    @Benchmark
    @Group("ascii_string")
    public void copiedBufferAscii(Blackhole bh) {
        ByteBuf byteBuf = UnpooledNewVersion.copiedBuffer(asciiString, CharsetUtil.US_ASCII);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("ascii_string")
    public void copiedBufferAsciiOldVersion(Blackhole bh) {
        ByteBuf byteBuf = UnpooledOldVersion.copiedBuffer(asciiString, CharsetUtil.US_ASCII);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("utf8_string")
    public void copiedBufferUtf8(Blackhole bh) {
        ByteBuf byteBuf = UnpooledNewVersion.copiedBuffer(utf8String, CharsetUtil.UTF_8);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("utf8_string")
    public void copiedBufferUtf8OldVersion(Blackhole bh) {
        ByteBuf byteBuf = UnpooledOldVersion.copiedBuffer(utf8String, CharsetUtil.UTF_8);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("ascii_string_offset")
    public void copiedBufferAsciiStartLength(Blackhole bh) {
        ByteBuf byteBuf = UnpooledNewVersion.copiedBuffer(asciiString, length / 3, length / 3, CharsetUtil.US_ASCII);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("ascii_string_offset")
    public void copiedBufferAsciiStartLengthOldVersion(Blackhole bh) {
        ByteBuf byteBuf = UnpooledOldVersion.copiedBuffer(asciiString, length / 3, length / 3, CharsetUtil.US_ASCII);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("utf8_string_offset")
    public void copiedBufferUtf8StartLength(Blackhole bh) {
        ByteBuf byteBuf = UnpooledNewVersion.copiedBuffer(utf8String, length / 3, length / 3, CharsetUtil.UTF_8);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("utf8_string_offset")
    public void copiedBufferUtf8StartLengthOldVersion(Blackhole bh) {
        ByteBuf byteBuf = UnpooledOldVersion.copiedBuffer(utf8String, length / 3, length / 3, CharsetUtil.UTF_8);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("ascii_array")
    public void copiedBufferAsciiViaArray(Blackhole bh) {
        ByteBuf byteBuf = UnpooledNewVersion.copiedBuffer(asciiCharArray, CharsetUtil.US_ASCII);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("ascii_array")
    public void copiedBufferAsciiViaArrayOldVersion(Blackhole bh) {
        ByteBuf byteBuf = UnpooledOldVersion.copiedBuffer(asciiCharArray, CharsetUtil.US_ASCII);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("utf8_array")
    public void copiedBufferUtf8ViaArray(Blackhole bh) {
        ByteBuf byteBuf = UnpooledNewVersion.copiedBuffer(utf8CharArray, CharsetUtil.UTF_8);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("utf8_array")
    public void copiedBufferUtf8ViaArrayOldVersion(Blackhole bh) {
        ByteBuf byteBuf = UnpooledOldVersion.copiedBuffer(utf8CharArray, CharsetUtil.UTF_8);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("ascii_array_offset")
    public void copiedBufferAsciiViaArrayStartLength(Blackhole bh) {
        ByteBuf byteBuf = UnpooledNewVersion.copiedBuffer(asciiCharArray, length / 3, length / 3, CharsetUtil.US_ASCII);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("ascii_array_offset")
    public void copiedBufferAsciiViaArrayStartLengthOldVersion(Blackhole bh) {
        ByteBuf byteBuf = UnpooledOldVersion.copiedBuffer(asciiCharArray, length / 3, length / 3, CharsetUtil.US_ASCII);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("utf8_array_offset")
    public void copiedBufferUtf8ViaArrayStartLength(Blackhole bh) {
        ByteBuf byteBuf = UnpooledNewVersion.copiedBuffer(utf8CharArray, length / 3, length / 3, CharsetUtil.UTF_8);
        byteBuf.release();
        bh.consume(byteBuf);
    }

    @Benchmark
    @Group("utf8_array_offset")
    public void copiedBufferUtf8ViaArrayStartLengthOldVersion(Blackhole bh) {
        ByteBuf byteBuf = UnpooledOldVersion.copiedBuffer(utf8CharArray, length / 3, length / 3, CharsetUtil.UTF_8);
        byteBuf.release();
        bh.consume(byteBuf);
    }
}
