/*
 * Copyright 2016 The Netty Project
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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.ReadOnlyHttp2Headers;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Threads(1)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ReadOnlyHttp2HeadersBenchmark extends AbstractMicrobenchmark {
    private AsciiString[] headerNames;
    private AsciiString[] headerValues;

    @Param({ "1", "5", "10", "20" })
    public int headerCount;

    private final AsciiString path = new AsciiString("/BigDynamicPayload");
    private final AsciiString authority = new AsciiString("io.netty");

    @Setup
    public void setUp() throws Exception {
        headerNames = new AsciiString[headerCount];
        headerValues = new AsciiString[headerCount];
        for (int i = 0; i < headerCount; ++i) {
            headerNames[i] = new AsciiString("key-" + i);
            headerValues[i] = new AsciiString(UUID.randomUUID().toString());
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int defaultTrailers() {
        Http2Headers headers = new DefaultHttp2Headers(false);
        for (int i = 0; i < headerCount; ++i) {
            headers.add(headerNames[i], headerValues[i]);
        }
        return iterate(headers);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int readOnlyTrailers() {
        return iterate(ReadOnlyHttp2Headers.trailers(false, buildPairs()));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int defaultClientHeaders() {
        Http2Headers headers = new DefaultHttp2Headers(false);
        for (int i = 0; i < headerCount; ++i) {
            headers.add(headerNames[i], headerValues[i]);
        }
        headers.method(HttpMethod.POST.asciiName());
        headers.scheme(HttpScheme.HTTPS.name());
        headers.path(path);
        headers.authority(authority);
        return iterate(headers);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int readOnlyClientHeaders() {
        return iterate(ReadOnlyHttp2Headers.clientHeaders(false, HttpMethod.POST.asciiName(), path,
                                                          HttpScheme.HTTPS.name(), authority, buildPairs()));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int defaultServerHeaders() {
        Http2Headers headers = new DefaultHttp2Headers(false);
        for (int i = 0; i < headerCount; ++i) {
            headers.add(headerNames[i], headerValues[i]);
        }
        headers.status(HttpResponseStatus.OK.codeAsText());
        return iterate(headers);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int readOnlyServerHeaders() {
        return iterate(ReadOnlyHttp2Headers.serverHeaders(false, HttpResponseStatus.OK.codeAsText(), buildPairs()));
    }

    private static int iterate(Http2Headers headers) {
        int length = 0;
        for (Map.Entry<CharSequence, CharSequence> entry : headers) {
            length += entry.getKey().length() + entry.getValue().length();
        }
        return length;
    }

    private AsciiString[] buildPairs() {
        AsciiString[] headerPairs = new AsciiString[headerCount * 2];
        for (int i = 0, j = 0; i < headerCount; ++i, ++j) {
            headerPairs[j] = headerNames[i];
            headerPairs[++j] = headerValues[i];
        }
        return headerPairs;
    }
}
