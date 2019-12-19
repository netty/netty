/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueryStringEncoderBenchmark extends AbstractMicrobenchmark {
    private String shortAscii;
    private String shortUtf8;
    private String shortAsciiFirst;

    private String longAscii;
    private String longUtf8;
    private String longAsciiFirst;

    @Setup
    public void setUp() {
        // Avoid constant pool for strings since it's common for at least values to not be constant.
        shortAscii = new String("foo".toCharArray());
        shortUtf8 = new String("ほげほげ".toCharArray());
        shortAsciiFirst = shortAscii + shortUtf8;
        longAscii = repeat(shortAscii, 100);
        longUtf8 = repeat(shortUtf8, 100);
        longAsciiFirst = longAscii + longUtf8;
    }

    @Benchmark
    public String shortAscii() {
        return encode(shortAscii);
    }

    @Benchmark
    public String shortUtf8() {
        return encode(shortUtf8);
    }

    @Benchmark
    public String shortAsciiFirst() {
        return encode(shortAsciiFirst);
    }

    @Benchmark
    public String longAscii() {
        return encode(longAscii);
    }

    @Benchmark
    public String longUtf8() {
        return encode(longUtf8);
    }

    @Benchmark
    public String longAsciiFirst() {
        return encode(longAsciiFirst);
    }

    private static String encode(String s) {
        QueryStringEncoder encoder = new QueryStringEncoder("");
        encoder.addParam(s, s);
        return encoder.toString();
    }

    private static String repeat(String s, int num) {
        StringBuilder sb = new StringBuilder(num * s.length());
        for (int i = 0; i < num; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
