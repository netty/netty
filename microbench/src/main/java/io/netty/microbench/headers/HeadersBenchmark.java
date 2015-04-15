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
package io.netty.microbench.headers;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

@Threads(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HeadersBenchmark extends AbstractMicrobenchmark {

    @Param
    ExampleHeaders.HeaderExample exampleHeader;

    AsciiString[] httpNames;
    AsciiString[] httpValues;

    ByteString[] http2Names;
    ByteString[] http2Values;

    DefaultHttpHeaders httpHeaders;
    DefaultHttp2Headers http2Headers;

    @Setup(Level.Trial)
    public void setup() {
        Map<String, String> headers = ExampleHeaders.EXAMPLES.get(exampleHeader);
        httpNames = new AsciiString[headers.size()];
        httpValues = new AsciiString[headers.size()];
        http2Names = new ByteString[headers.size()];
        http2Values = new ByteString[headers.size()];
        httpHeaders = new DefaultHttpHeaders(false);
        http2Headers = new DefaultHttp2Headers();
        int idx = 0;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey();
            String value = header.getValue();
            httpNames[idx] = new AsciiString(name);
            httpValues[idx] = new AsciiString(value);
            http2Names[idx] = new ByteString(name, CharsetUtil.US_ASCII);
            http2Values[idx] = new ByteString(value, CharsetUtil.US_ASCII);
            idx++;
            httpHeaders.add(new AsciiString(name), new AsciiString(value));
            http2Headers.add(new ByteString(name, CharsetUtil.US_ASCII), new ByteString(value, CharsetUtil.US_ASCII));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpGet(Blackhole bh) {
        for (AsciiString name : httpNames) {
            bh.consume(httpHeaders.get(name));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public DefaultHttpHeaders httpPut() {
        DefaultHttpHeaders headers = new DefaultHttpHeaders(false);
        for (int i = 0; i < httpNames.length; i++) {
            headers.add(httpNames[i], httpValues[i]);
        }
        return headers;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpIterate(Blackhole bh) {
        for (Entry<CharSequence, CharSequence> entry : httpHeaders) {
            bh.consume(entry);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2Get(Blackhole bh) {
        for (ByteString name : http2Names) {
            bh.consume(http2Headers.get(name));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public DefaultHttp2Headers http2Put() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        for (int i = 0; i < httpNames.length; i++) {
            headers.add(httpNames[i], httpValues[i]);
        }
        return headers;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2IterateNew(Blackhole bh) {
        for (Entry<ByteString, ByteString> entry : http2Headers) {
            bh.consume(entry);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2IterateOld(Blackhole bh) {
        // This is how we had to iterate in the Http2HeadersEncoder when writing the frames on the wire
        // in order to ensure that reserved headers come first.
        for (Http2Headers.PseudoHeaderName pseudoHeader : Http2Headers.PseudoHeaderName.values()) {
            ByteString name = pseudoHeader.value();
            ByteString value = http2Headers.get(name);
            if (value != null) {
                bh.consume(value);
            }
        }
        for (Entry<ByteString, ByteString> entry : http2Headers) {
            final ByteString name = entry.getKey();
            final ByteString value = entry.getValue();
            if (!Http2Headers.PseudoHeaderName.isPseudoHeader(name)) {
                bh.consume(value);
            }
        }
    }
}
