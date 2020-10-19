/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueryStringDecoderBenchmark extends AbstractMicrobenchmark {

    private static final Charset SHIFT_JIS = Charset.forName("Shift-JIS");

    @Benchmark
    public Map<String, List<String>> noDecoding() {
        return new QueryStringDecoder("foo=bar&cat=dog", false).parameters();
    }

    @Benchmark
    public Map<String, List<String>> onlyDecoding() {
        // ほげ=ぼけ&ねこ=いぬ
        return new QueryStringDecoder("%E3%81%BB%E3%81%92=%E3%81%BC%E3%81%91&%E3%81%AD%E3%81%93=%E3%81%84%E3%81%AC",
                                      false)
                .parameters();
    }

    @Benchmark
    public Map<String, List<String>> mixedDecoding() {
        // foo=bar&ほげ=ぼけ&cat=dog&ねこ=いぬ
        return new QueryStringDecoder("foo=bar%E3%81%BB%E3%81%92=%E3%81%BC%E3%81%91&cat=dog&" +
                                      "&%E3%81%AD%E3%81%93=%E3%81%84%E3%81%AC", false)
                .parameters();
    }

    @Benchmark
    public Map<String, List<String>> nonStandardDecoding() {
        // ほげ=ぼけ&ねこ=いぬ in Shift-JIS
        return new QueryStringDecoder("%82%D9%82%B0=%82%DA%82%AF&%82%CB%82%B1=%82%A2%82%CA",
                                      SHIFT_JIS, false)
                .parameters();
    }
}
