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
import io.netty.util.internal.SuppressJava6Requirement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.LinuxPerfNormProfiler;
import org.openjdk.jmh.profile.ProfilerException;
import org.openjdk.jmh.profile.ProfilerFactory;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.ProfilerConfig;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@SuppressJava6Requirement(reason = "suppress")
public class HttpStatusValueOfBenchmark extends AbstractMicrobenchmark {
    @Param({"1300", "2600", "5300", "11000", "23000"})
    private int size;
    private static final SplittableRandom random = new SplittableRandom();
    private static final DecimalFormat df = new DecimalFormat("##.##%");
    private CircularLink head;
    private static final CircularLink polluteDistributeHead;
    private static final Map<Integer, CircularLink> sizeMap = new HashMap<Integer, CircularLink>();

    static {
        sizeMap.put(1300, initCircularLink(1300));
        sizeMap.put(2600, initCircularLink(2600));
        sizeMap.put(5300, initCircularLink(5300));
        sizeMap.put(11000, initCircularLink(11000));
        sizeMap.put(23000, initCircularLink(23000));
        polluteDistributeHead = initCircularLink(16000);
        fillPolluteDistributeData(16000, polluteDistributeHead);
    }

    @Setup(Level.Invocation)
    public void setup(Blackhole bh) {
        // Pollute the branch predictor.
        CircularLink cl = polluteDistributeHead;
        do {
            bh.consume(HttpStatusClass.valueOf(cl.value));
        } while ((cl = cl.next) != polluteDistributeHead);

        head = sizeMap.get(size);
        // Fill benchmark data.
        fillBenchMarkData(size, head);
    }

    @Benchmark
    public void valueOf(Blackhole bh) {
        CircularLink cl = head;
        do {
            bh.consume(HttpStatusClass.valueOf(cl.value));
        } while ((cl = cl.next) != head);
    }

    public HttpStatusValueOfBenchmark() {
        // disable assertion
        super(true);
    }

    private static void fillBenchMarkData(int size, CircularLink head) {
        double c1x = 0, c2x = 0, c3x = 0, c4x = 0, c5x = 0, c6x = 0;
        CircularLink cl = head;
        do {
            // [0, 100)
            int code = random.nextInt(0, 100);
            // 38%
            if (code < 38) {
                cl.value = random.nextInt(100, 200);
                ++c1x;
                continue;
            }
            // 30%
            if (code < 68) {
                cl.value = random.nextInt(200, 300);
                ++c2x;
                continue;
            }
            // 15%
            if (code < 83) {
                cl.value = random.nextInt(300, 400);
                ++c3x;
                continue;
            }
            // 10%
            if (code < 93) {
                cl.value = random.nextInt(400, 500);
                ++c4x;
                continue;
            }
            // 5%
            if (code < 98) {
                cl.value = random.nextInt(500, 600);
                ++c5x;
                continue;
            }
            // 2%
            cl.value = random.nextInt(-50, 50);
            ++c6x;
        } while (head != (cl = cl.next));
//        printCodePercentage("initBenchMarkData", size, c1x, c2x, c3x, c4x, c5x, c6x);
    }

    private static void fillPolluteDistributeData(int size, CircularLink head) {
        double c1x = 0, c2x = 0, c3x = 0, c4x = 0, c5x = 0, c6x = 0;
        CircularLink cl = head;
        do {
            // [0, 96)
            int code = random.nextInt(0, 96);
            // (100/6) %
            if (code < 16) {
                cl.value = random.nextInt(100, 200);
                ++c1x;
                continue;
            }
            // (100/6) %
            if (code < 32) {
                cl.value = random.nextInt(200, 300);
                ++c2x;
                continue;
            }
            // (100/6) %
            if (code < 48) {
                cl.value = random.nextInt(300, 400);
                ++c3x;
                continue;
            }
            // (100/6) %
            if (code < 64) {
                cl.value = random.nextInt(400, 500);
                ++c4x;
                continue;
            }
            // (100/6) %
            if (code < 80) {
                cl.value = random.nextInt(500, 600);
                ++c5x;
                continue;
            }
            // (100/6) %
            cl.value = random.nextInt(-50, 50);
            ++c6x;
        } while (head != (cl = cl.next));
//        printCodePercentage("fillEqualDistributeData", size, c1x, c2x, c3x, c4x, c5x, c6x);
    }

    private static CircularLink initCircularLink(int size) {
        CircularLink cl = new CircularLink();
        CircularLink head = cl;
        for (int i = 0 ; i < size - 1; i++) {
            cl.next = new CircularLink();
            cl = cl.next;
        }
        cl.next = head;
        return head;
    }

    private static final class CircularLink {
        private CircularLink next;
        private int value;
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
