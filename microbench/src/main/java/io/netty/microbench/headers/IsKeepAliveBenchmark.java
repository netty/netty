/*
 * Copyright 2025 The Netty Project
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
package io.netty.microbench.headers;

import io.netty.handler.codec.http.*;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
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

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class IsKeepAliveBenchmark extends AbstractMicrobenchmark {

    public enum ConnectionClose {
        KeepAlive,
        Close,
        None
    }

    @Param
    public ConnectionClose close;
    // this is distributing equally branching for warmup purposes, although in the real world it likely
    // happens to be skewed towards one of the options!
    @Param({"false", "true"})
    public boolean warmup;

    FullHttpRequest request;

    @Setup
    public void setup(Blackhole bh) {
        request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "/index.html");
        HttpHeaders headers = request.headers();
        // values are usually strings decoded out of network buffers
        headers.set(HttpHeaderNames.HOST, "localhost");
        headers.set(HttpHeaderNames.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/131.0.0.0 Safari/537.36");
        headers.set(HttpHeaderNames.ACCEPT,
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.set(HttpHeaderNames.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        headers.set(HttpHeaderNames.ACCEPT_ENCODING, "gzip, deflate");
        headers.set(HttpHeaderNames.UPGRADE_INSECURE_REQUESTS, "1");
        headers.set(HttpHeaderNames.CACHE_CONTROL, "max-age=0");

        if (warmup) {
            // create 3 request variations for warmup based on the ConnectionClose values
            ConnectionClose[] cases = ConnectionClose.values();
            FullHttpRequest[] requests = new FullHttpRequest[cases.length];
            for (int i = 0; i < requests.length; i++) {
                requests[i] = request.copy();
                setConnectionClose(requests[i].headers(), cases[i]);
            }
            // warmup with mixed requests
            for (int i = 0; i < 100_000; i++) {
                for (FullHttpRequest httpRequest : requests) {
                    bh.consume(isKeepAlive(httpRequest));
                }
            }
        }
        // set the actual test case
        setConnectionClose(headers, close);
    }

    private static void setConnectionClose(HttpHeaders headers, ConnectionClose close) {
        switch (close) {
            case KeepAlive:
                headers.set(HttpHeaderNames.CONNECTION, "keep-alive");
                break;
            case Close:
                headers.set(HttpHeaderNames.CONNECTION, "close");
                break;
            case None:
                // omit header
                break;
        }
    }

    @Benchmark
    public boolean isKeepAlive() {
        return isKeepAlive(request);
    }

    @Benchmark
    @Fork(value = 2, jvmArgsAppend = "-XX:-DoEscapeAnalysis")
    public boolean isKeepAliveWithGarbage() {
        return isKeepAlive(request);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean isKeepAlive(HttpRequest request) {
        return HttpUtil.isKeepAlive(request);
    }
}
