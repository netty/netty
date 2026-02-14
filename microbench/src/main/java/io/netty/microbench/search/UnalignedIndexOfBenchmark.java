/*
 * Copyright 2026 The Netty Project
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
package io.netty.microbench.search;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(value = 1, jvmArgsAppend = "-Dio.netty.unalignedAccess=false")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class UnalignedIndexOfBenchmark extends AbstractMicrobenchmark {

    @Param({ "256", "2048" })
    public int haystackSize;

    private ByteBuf needle;
    private ByteBuf haystack;
    private byte[] needleBytes;
    private byte[] haystackBytes;

    @Setup
    public void setup() {
        Random rnd = new Random(123);
        needleBytes = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h' };
        haystackBytes = randomBytes(rnd, haystackSize, ' ', 127);
        needle = Unpooled.wrappedBuffer(needleBytes);
        haystack = Unpooled.wrappedBuffer(haystackBytes);
    }

    @TearDown
    public void tearDown() {
        needle.release();
        haystack.release();
    }

    @Benchmark
    public int indexOf() {
        return ByteBufUtil.indexOf(needle, haystack);
    }

    private static byte[] randomBytes(Random rnd, int size, int from, int to) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (from + rnd.nextInt(to - from + 1));
        }
        return bytes;
    }
}
