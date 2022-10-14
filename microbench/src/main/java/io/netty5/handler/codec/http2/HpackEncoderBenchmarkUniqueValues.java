/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.AsciiString;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Fork(5)
@Threads(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class HpackEncoderBenchmarkUniqueValues extends AbstractMicrobenchmark {

    @Param({"fewHeaders", "manyPaths", "tracesWithUniqueValues"})
    private String type;

    private final AsciiString[] PATHS = generateRandomPaths(20);

    private final Random r = new Random();

    private final Http2Headers[] http2Headers = new Http2Headers[1000];

    private final HpackEncoder[] hpackEncoder = new HpackEncoder[1000];

    private Buffer output;

    @Setup
    public void setup() throws Http2Exception {
        output = DefaultBufferAllocators.onHeapAllocator().allocate(256);
        for (int i = 0; i < http2Headers.length; i++) {
            Http2Headers headers = Http2Headers.newHeaders();
            if (type.equals("tracesWithUniqueValues")) {
                headers.add(AsciiString.of("traceid"), randomAsciiString(20));
            }
            headers.add(AsciiString.of("key1"), AsciiString.of("value1"));
            headers.add(AsciiString.of("key12"), AsciiString.of("value12"));
            headers.add(AsciiString.of("key123"), AsciiString.of("value123"));
            if (type.equals("manyPaths")) {
                headers.add(AsciiString.of(":path"), AsciiString.of("/path/to/" + PATHS[r.nextInt(PATHS.length)]));
            }
            headers.add(AsciiString.of(":method"), AsciiString.of("POST"));
            headers.add(AsciiString.of("content-encoding"), AsciiString.of("grpc-encoding"));
            http2Headers[i] = headers;
        }

        for (int i = 0; i < hpackEncoder.length; i++) {
            hpackEncoder[i] = new HpackEncoder();
            for (Http2Headers headers: http2Headers) {
                output.resetOffsets();
                hpackEncoder[i].encodeHeaders(3, output, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            }
        }
    }

    @TearDown
    public void tearDown() {
        output.close();
    }

    @Benchmark
    public void encode(Blackhole bh) throws Exception {
        output.resetOffsets();

        Http2Headers headers = http2Headers[r.nextInt(http2Headers.length)];

        // select between many encoders to prevent the encoder state from staying in the cpu L1 cache.
        HpackEncoder encoder = hpackEncoder[r.nextInt(hpackEncoder.length)];
        encoder.encodeHeaders(3, output, headers, Http2HeadersEncoder.NEVER_SENSITIVE);

        bh.consume(output);
    }

    private static AsciiString[] generateRandomPaths(int size) {
        AsciiString[] paths = new AsciiString[size];
        for (int i = 0; i < size; i++) {
            paths[i] = randomAsciiString(20);
        }
        return paths;
    }

    private static AsciiString randomAsciiString(int length) {
        return AsciiString.of(HpackHeader.createHeaders(1, 10, length, true).get(0).value);
    }

}
