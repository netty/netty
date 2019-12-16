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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueryStringEncoderBenchmark extends AbstractMicrobenchmark {
    private static final String SHORT_ASCII = "foo";
    private static final String SHORT_UTF8 = "ほげほげ";
    private static final String SHORT_ASCII_FIRST = SHORT_ASCII + SHORT_UTF8;

    private static final String LONG_ASCII = repeat(SHORT_ASCII, 100);
    private static final String LONG_UTF8 = repeat(SHORT_UTF8, 100);
    private static final String LONG_ASCII_FIRST = LONG_ASCII + LONG_UTF8;

    @Benchmark
    public String shortAscii() {
        return encode(SHORT_ASCII);
    }

    @Benchmark
    public String shortUtf8() {
        return encode(SHORT_UTF8);
    }

    @Benchmark
    public String shortAsciiFirst() {
        return encode(SHORT_ASCII_FIRST);
    }

    @Benchmark
    public String longAscii() {
        return encode(LONG_ASCII);
    }

    @Benchmark
    public String longUtf8() {
        return encode(LONG_UTF8);
    }

    @Benchmark
    public String longAsciiFirst() {
        return encode(LONG_ASCII_FIRST);
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
