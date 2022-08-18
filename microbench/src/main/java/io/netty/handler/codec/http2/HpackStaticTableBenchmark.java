/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.CharsetUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import io.netty.util.AsciiString;

@Fork(1)
@Threads(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HpackStaticTableBenchmark extends AbstractMicrobenchmark {

    private static final CharSequence X_CONTENT_ENCODING =
            new AsciiString("x-content-encoding".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence X_GZIP = new AsciiString("x-gzip".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence SCHEME = new AsciiString(":scheme".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence HTTP = new AsciiString("http".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence HTTPS = new AsciiString("https".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence STATUS = new AsciiString(":status".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence STATUS_200 = new AsciiString("200".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence STATUS_500 = new AsciiString("500".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence AUTHORITY =
            new AsciiString(":authority".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence AUTHORITY_NETTY =
            new AsciiString("netty.io".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence USER_AGENT =
            new AsciiString("user-agent".getBytes(CharsetUtil.US_ASCII), false);
    private static final CharSequence USER_AGENT_CURL =
            new AsciiString("curl/7.64.1".getBytes(CharsetUtil.US_ASCII), false);

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int lookupNoNameMatch() {
        return HpackStaticTable.getIndexInsensitive(X_CONTENT_ENCODING, X_GZIP);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int lookupNameAndValueMatchFirst() {
        return HpackStaticTable.getIndexInsensitive(STATUS, STATUS_200);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int lookupNameAndValueMatchLast() {
        return HpackStaticTable.getIndexInsensitive(STATUS, STATUS_500);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int lookupNameOnlyMatchBeginTable() {
        return HpackStaticTable.getIndexInsensitive(AUTHORITY, AUTHORITY_NETTY);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int lookupHttp() {
        return HpackStaticTable.getIndexInsensitive(SCHEME, HTTP);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int lookupHttps() {
        return HpackStaticTable.getIndexInsensitive(SCHEME, HTTPS);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int lookupNameOnlyMatchEndTable() {
        return HpackStaticTable.getIndexInsensitive(USER_AGENT, USER_AGENT_CURL);
    }
}
