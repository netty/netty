/*
 * Copyright 2015 The Netty Project
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
package io.netty5.microbench.headers;

import io.netty5.handler.codec.http.headers.DefaultHttpHeaders;
import io.netty5.handler.codec.http2.headers.DefaultHttp2Headers;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.AsciiString;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

@Threads(1)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HeadersBenchmark extends AbstractMicrobenchmark {

    private static String toHttpName(String name) {
        return (name.startsWith(":")) ? name.substring(1) : name;
    }

    static String toHttp2Name(String name) {
        name = name.toLowerCase();
        return (name.equals("host")) ? "xhost" : name;
    }

    @Param({"SIX", "THIRTY"})
    ExampleHeaders.HeaderExample exampleHeader;

    AsciiString[] httpNames;
    AsciiString[] http2Names;
    AsciiString[] httpValues;

    DefaultHttpHeaders httpHeaders;
    DefaultHttp2Headers http2Headers;
    DefaultHttpHeaders emptyHttpHeaders;
    DefaultHttp2Headers emptyHttp2Headers;
    DefaultHttpHeaders emptyHttpHeadersNoValidate;
    DefaultHttp2Headers emptyHttp2HeadersNoValidate;

    @Setup(Level.Trial)
    public void setup() {
        Map<String, String> headers = ExampleHeaders.EXAMPLES.get(exampleHeader);
        httpNames = new AsciiString[headers.size()];
        http2Names = new AsciiString[headers.size()];
        httpValues = new AsciiString[headers.size()];
        httpHeaders = new DefaultHttpHeaders(16, false, false, false);
        http2Headers = new DefaultHttp2Headers(16, false, false, false);
        int idx = 0;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey();
            String httpName = toHttpName(name);
            String http2Name = toHttp2Name(name);
            String value = header.getValue();
            httpNames[idx] = new AsciiString(httpName);
            http2Names[idx] = new AsciiString(http2Name);
            httpValues[idx] = new AsciiString(value);
            httpHeaders.add(httpNames[idx], httpValues[idx]);
            http2Headers.add(http2Names[idx], httpValues[idx]);
            idx++;
        }
        emptyHttpHeaders = new DefaultHttpHeaders(16, true, true, true);
        emptyHttp2Headers = new DefaultHttp2Headers(16, true, true, true);
        emptyHttpHeadersNoValidate = new DefaultHttpHeaders(16, false, false, false);
        emptyHttp2HeadersNoValidate = new DefaultHttp2Headers(16, false, false, false);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpRemove(Blackhole bh) {
        for (AsciiString name : httpNames) {
            bh.consume(httpHeaders.remove(name));
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
        DefaultHttpHeaders headers = new DefaultHttpHeaders(16, false, false, false);
        for (int i = 0; i < httpNames.length; i++) {
            headers.add(httpNames[i], httpValues[i]);
        }
        return headers;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpIterate(Blackhole bh) {
        for (Entry<CharSequence, CharSequence> httpHeader : httpHeaders) {
            bh.consume(httpHeader);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2Remove(Blackhole bh) {
        for (AsciiString name : http2Names) {
            bh.consume(http2Headers.remove(name));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2Get(Blackhole bh) {
        for (AsciiString name : http2Names) {
            bh.consume(http2Headers.get(name));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public DefaultHttp2Headers http2Put() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers(16, false, false, false);
        for (int i = 0; i < http2Names.length; i++) {
            headers.add(http2Names[i], httpValues[i]);
        }
        return headers;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2Iterate(Blackhole bh) {
        for (Entry<CharSequence, CharSequence> entry : http2Headers) {
            bh.consume(entry);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpAddAllFastest(Blackhole bh) {
        bh.consume(emptyHttpHeadersNoValidate.add(httpHeaders));
        emptyHttpHeadersNoValidate.clear();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void httpAddAllFast(Blackhole bh) {
        bh.consume(emptyHttpHeaders.add(httpHeaders));
        emptyHttpHeaders.clear();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2AddAllFastest(Blackhole bh) {
        bh.consume(emptyHttp2HeadersNoValidate.add(http2Headers));
        emptyHttp2HeadersNoValidate.clear();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void http2AddAllFast(Blackhole bh) {
        bh.consume(emptyHttp2Headers.add(http2Headers));
        emptyHttp2Headers.clear();
    }
}
