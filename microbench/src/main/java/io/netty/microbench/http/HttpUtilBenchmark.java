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
package io.netty.microbench.http;

import io.netty.handler.codec.http.HttpUtil;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
public class HttpUtilBenchmark extends AbstractMicrobenchmark {
    private static final String uri = "https://github.com/netty/netty/blob/893508ce62a7f90464f8e4bf2ac28ecc73ce6608/" +
            "handler/src/main/java/io/netty/handler/ssl/util/BouncyCastleSelfSignedCertGenerator.java";

    @Benchmark
    public boolean checkIsEncodingSafeUri() {
        return HttpUtil.isEncodingSafeStartLineToken(uri);
    }
}
