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

import io.netty.handler.codec.http.QueryStringDecoder.ParameterCollector;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Threads(1)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueryStringDecoderBenchmark extends AbstractMicrobenchmark {
    private static final Charset SHIFT_JIS = Charset.forName("Shift-JIS");
    private String singleResourceOneParameterUri;
    private String singleResourceThreeParametersUri;
    private String noDecodingUri;
    private String onlyDecodingUri;
    private String mixedDecodingUri;
    private String nonStandardDecodingUri;
    private static final ParameterCollector<Blackhole> BH_COLLECTOR = new ParameterCollector<Blackhole>() {
        @Override
        public void collect(String name, String value, Blackhole accumulator) {
            accumulator.consume(name);
            accumulator.consume(value);
        }
    };

    @Param({ "false", "true" })
    public boolean confuseJIT;

    @Setup
    public void setUp(Blackhole bh) {
        singleResourceOneParameterUri = "/updates?queries=5";
        singleResourceThreeParametersUri = "/updates?queries=5&foo=bar&cat=dog";
        noDecodingUri = "foo=bar&cat=dog";
        onlyDecodingUri = "%E3%81%BB%E3%81%92=%E3%81%BC%E3%81%91&%E3%81%AD%E3%81%93=%E3%81%84%E3%81%AC";
        mixedDecodingUri = "foo=bar%E3%81%BB%E3%81%92=%E3%81%BC%E3%81%91&cat=dog&" +
                           "&%E3%81%AD%E3%81%93=%E3%81%84%E3%81%AC";
        nonStandardDecodingUri = "%82%D9%82%B0=%82%DA%82%AF&%82%CB%82%B1=%82%A2%82%CA";
        if (confuseJIT) {
            for (int i = 0; i < 11000; i++) {
                singleResourceOneParameterNoMap(bh);
                singleResourceThreeParametersNoMap(bh);
                noDecodingNoMap(bh);
                onlyDecodingNoMap(bh);
                mixedDecodingNoMap(bh);
                nonStandardDecodingNoMap(bh);
            }
        }
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public Map<String, List<String>> singleResourceOneParameter() {
        return new QueryStringDecoder(singleResourceOneParameterUri).parameters();
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public Map<String, List<String>> singleResourceThreeParameters() {
        return new QueryStringDecoder(singleResourceThreeParametersUri).parameters();
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public Map<String, List<String>> noDecoding() {
        return new QueryStringDecoder(noDecodingUri, false).parameters();
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public Map<String, List<String>> onlyDecoding() {
        // ほげ=ぼけ&ねこ=いぬ
        return new QueryStringDecoder(onlyDecodingUri, false).parameters();
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public Map<String, List<String>> mixedDecoding() {
        // foo=bar&ほげ=ぼけ&cat=dog&ねこ=いぬ
        return new QueryStringDecoder(mixedDecodingUri, false).parameters();
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public Map<String, List<String>> nonStandardDecoding() {
        // ほげ=ぼけ&ねこ=いぬ in Shift-JIS
        return new QueryStringDecoder(nonStandardDecodingUri, SHIFT_JIS, false).parameters();
    }

    @Benchmark
    public void singleResourceOneParameterNoMap(Blackhole bh) {
        QueryStringDecoder.decodeParams(singleResourceOneParameterUri,
                                        HttpConstants.DEFAULT_CHARSET,
                                        true,
                                        QueryStringDecoder.getDefaultMaxParams(),
                                        false,
                                        BH_COLLECTOR, bh);
    }

    @Benchmark
    public void singleResourceThreeParametersNoMap(Blackhole bh) {
        QueryStringDecoder.decodeParams(singleResourceThreeParametersUri,
                                        HttpConstants.DEFAULT_CHARSET,
                                        true,
                                        QueryStringDecoder.getDefaultMaxParams(),
                                        false,
                                        BH_COLLECTOR, bh);
    }

    @Benchmark
    public void noDecodingNoMap(Blackhole bh) {
        QueryStringDecoder.decodeParams(noDecodingUri,
                                        HttpConstants.DEFAULT_CHARSET,
                                        false,
                                        QueryStringDecoder.getDefaultMaxParams(),
                                        false,
                                        BH_COLLECTOR, bh);
    }

    @Benchmark
    public void onlyDecodingNoMap(Blackhole bh) {
        // ほげ=ぼけ&ねこ=いぬ
        QueryStringDecoder.decodeParams(
                onlyDecodingUri,
                HttpConstants.DEFAULT_CHARSET,
                false,
                QueryStringDecoder.getDefaultMaxParams(),
                false,
                BH_COLLECTOR, bh);
    }

    @Benchmark
    public void mixedDecodingNoMap(Blackhole bh) {
        // foo=bar&ほげ=ぼけ&cat=dog&ねこ=いぬ
        QueryStringDecoder.decodeParams(mixedDecodingUri,
                                        HttpConstants.DEFAULT_CHARSET,
                                        false,
                                        QueryStringDecoder.getDefaultMaxParams(),
                                        false,
                                        BH_COLLECTOR, bh);
    }

    @Benchmark
    public void nonStandardDecodingNoMap(Blackhole bh) {
        // ほげ=ぼけ&ねこ=いぬ in Shift-JIS
        QueryStringDecoder.decodeParams(nonStandardDecodingUri,
                                        SHIFT_JIS, false, QueryStringDecoder.getDefaultMaxParams(),
                                        false,
                                        BH_COLLECTOR, bh);
    }
}
