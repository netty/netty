/*
 * Copyright 2023 The Netty Project
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
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.LinuxPerfNormProfiler;
import org.openjdk.jmh.profile.ProfilerException;
import org.openjdk.jmh.profile.ProfilerFactory;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.ProfilerConfig;

import java.text.DecimalFormat;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class HttpStatusValueOfBenchmark extends AbstractMicrobenchmark {
    private static final SplittableRandom random = new SplittableRandom();
    private static final DecimalFormat df = new DecimalFormat("##.##%");
    private static final int[] data_1300 = new int[1300];
    private static final int[] data_2600 = new int[2600];
    private static final int[] data_5300 = new int[5300];
    private static final int[] data_11000 = new int[11000];
    private static final int[] data_23000 = new int[23000];
    private static final boolean ENABLE_POLLUTE = false;

    @Setup(Level.Invocation)
    public void setup(Blackhole bh, BenchmarkParams benchmarkParams) {
        switch (benchmarkParams.getOpsPerInvocation()) {
            case 1300 :
                polluteBranchIfEnabled(bh, data_1300);
                fillBenchMarkData(data_1300);
                break;
            case 2600 :
                polluteBranchIfEnabled(bh, data_2600);
                fillBenchMarkData(data_2600);
                break;
            case 5300 :
                polluteBranchIfEnabled(bh, data_5300);
                fillBenchMarkData(data_5300);
                break;
            case 11000 :
                polluteBranchIfEnabled(bh, data_11000);
                fillBenchMarkData(data_11000);
                break;
            case 23000 :
                polluteBranchIfEnabled(bh, data_23000);
                fillBenchMarkData(data_23000);
                break;
        }
    }

    @Benchmark
    @OperationsPerInvocation(1300)
    public void valueOf_1300(Blackhole bh) {
        for (int code : data_1300) {
            bh.consume(HttpStatusClass.valueOf(code));
        }
    }

    @Benchmark
    @OperationsPerInvocation(2600)
    public void valueOf_2600(Blackhole bh) {
        for (int code : data_2600) {
            bh.consume(HttpStatusClass.valueOf(code));
        }
    }

    @Benchmark
    @OperationsPerInvocation(5300)
    public void valueOf_5300(Blackhole bh) {
        for (int code : data_5300) {
            bh.consume(HttpStatusClass.valueOf(code));
        }
    }

    @Benchmark
    @OperationsPerInvocation(11000)
    public void valueOf_11000(Blackhole bh) {
        for (int code : data_11000) {
            bh.consume(HttpStatusClass.valueOf(code));
        }
    }

    @Benchmark
    @OperationsPerInvocation(23000)
    public void valueOf_23000(Blackhole bh) {
        for (int code : data_23000) {
            bh.consume(HttpStatusClass.valueOf(code));
        }
    }

    public HttpStatusValueOfBenchmark() {
        // disable assertion
        super(true);
    }

    private static void polluteBranchIfEnabled(Blackhole bh, int[] polluteData) {
        if (ENABLE_POLLUTE) {
            fillPolluteData(polluteData);
            for (int code : polluteData) {
                bh.consume(HttpStatusClass.valueOf(code));
            }
        }
    }

    private static void fillBenchMarkData(int[] benchMarkData) {
        double c1x = 0, c2x = 0, c3x = 0, c4x = 0, c5x = 0, c6x = 0;
        for (int i = 0; i < benchMarkData.length;) {
            // [0, 100)
            int code = random.nextInt(0, 100);
            // 38%
            if (code < 38) {
                benchMarkData[i++] = random.nextInt(100, 200);
                ++c1x;
                continue;
            }
            // 30%
            if (code < 68) {
                benchMarkData[i++] = random.nextInt(200, 300);
                ++c2x;
                continue;
            }
            // 15%
            if (code < 83) {
                benchMarkData[i++] = random.nextInt(300, 400);
                ++c3x;
                continue;
            }
            // 10%
            if (code < 93) {
                benchMarkData[i++] = random.nextInt(400, 500);
                ++c4x;
                continue;
            }
            // 5%
            if (code < 98) {
                benchMarkData[i++] = random.nextInt(500, 600);
                ++c5x;
                continue;
            }
            // 2%
            benchMarkData[i++] = random.nextInt(-50, 50);
            ++c6x;
        }
//        printCodePercentage("fillBenchMarkData", benchMarkData.length, c1x, c2x, c3x, c4x, c5x, c6x);
    }

    private static void fillPolluteData(int[] polluteData) {
        double c1x = 0, c2x = 0, c3x = 0, c4x = 0, c5x = 0, c6x = 0;
        for (int i = 0; i < polluteData.length;) {
            // [0, 96)
            int code = random.nextInt(0, 96);
            // (100/6) %
            if (code < 16) {
                polluteData[i++] = random.nextInt(100, 200);
                ++c1x;
                continue;
            }
            // (100/6) %
            if (code < 32) {
                polluteData[i++] = random.nextInt(200, 300);
                ++c2x;
                continue;
            }
            // (100/6) %
            if (code < 48) {
                polluteData[i++] = random.nextInt(300, 400);
                ++c3x;
                continue;
            }
            // (100/6) %
            if (code < 64) {
                polluteData[i++] = random.nextInt(400, 500);
                ++c4x;
                continue;
            }
            // (100/6) %
            if (code < 80) {
                polluteData[i++] = random.nextInt(500, 600);
                ++c5x;
                continue;
            }
            // (100/6) %
            polluteData[i++] = random.nextInt(-50, 50);
            ++c6x;
        }
//        printCodePercentage("fillPolluteData", polluteData.length, c1x, c2x, c3x, c4x, c5x, c6x);
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        Class<LinuxPerfNormProfiler> profilerClass = LinuxPerfNormProfiler.class;
        try {
            ProfilerFactory.getProfilerOrException(new ProfilerConfig(profilerClass.getCanonicalName()));
        } catch (ProfilerException t) {
            // Fall back to default.
            return super.newOptionsBuilder();
        }
        return super.newOptionsBuilder().addProfiler(profilerClass);
    }

    private static void printCodePercentage(String desc, int length, double c1x, double c2x, double c3x, double c4x,
                                            double c5x, double c6x) {
        System.out.println("\n" + desc + "===>"
                + "INFORMATIONAL:" + df.format(c1x / length)
                + ", SUCCESS:" + df.format(c2x / length)
                + ", REDIRECTION:" + df.format(c3x / length)
                + ", CLIENT_ERROR:" + df.format(c4x / length)
                + ", SERVER_ERROR:" + df.format(c5x / length)
                + ", UNKNOWN:" + df.format(c6x / length)
        );
    }
}
