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

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
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

    @Benchmark
    public Map<String, List<String>> longMixedDecoding() {
        return new QueryStringDecoder("uniqid=D87B332EFD7643D2AWD9A47F494A536AA&" +
                "curidentity=0&bundle_id=com.anncded&app_id=1004&client_info=%7B%22" +
                "version%22%3A%2213.3.1%22%2C%22idfa%22%3A%2232CB5C8E-AA53-422A-9F9D-3E2F2DFAE489%22%2C%22" +
                "longitude%22%3A%22117.441926%22%2C%22start_time%22%3A%221583589271210%22%2C%22latitude%22%3A%" +
                "2238.969134%22%2C%22os%22%3A%22iOS%22%2C%22bssid%22%3A%2234%3A96%3A72%3A72%3A20%3A9e%22%2C%22" +
                "did%22%3A%22D2ADTrsIfqMjkjSGThC9QbmTpOumoq11hZScOSvlVjti8Xc3%22%2C%22end_time%22%3A%221583589268703" +
                "%22%2C%22ssid%22%3A%2234SA%22%2C%22resume_time%22%3A%221583589271210%22%2C%22model%22%3A%22iPhone12" +
                "%2C2%22%7D&v=8.020&t=BA2ZRQAIxATsFNFE3CTRSOQw8U2IHOQVh&sig=V2.01ccecs84916839c00604eb31bbd73b9e541" +
                "&req_time=1583589271285", false).parameters();
    }
}
