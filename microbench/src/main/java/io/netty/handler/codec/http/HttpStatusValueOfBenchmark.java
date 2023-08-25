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
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import io.netty.util.internal.SuppressJava6Requirement;
import org.openjdk.jmh.profile.LinuxPerfNormProfiler;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class HttpStatusValueOfBenchmark extends AbstractMicrobenchmark {
    private int[] data;
    private HttpStatusClass[] result;
    @Param({"1300", "2600", "5300", "11000", "23000"})
    private int size;
    private final BigDecimal bdZero = new BigDecimal("0.00");
    private final BigDecimal bdOne = new BigDecimal("1.00");
    private final BigDecimal bdLowest = new BigDecimal("0.01");
    private final DecimalFormat df = new DecimalFormat("##.##%");
    public HttpStatusValueOfBenchmark() {
        // disable assertion
        super(true);
    }

    @Setup(Level.Iteration)
    @SuppressJava6Requirement(reason = "suppress")
    public void setup(Blackhole bh) {
        if (size < 100) {
            throw new IllegalArgumentException("The size MUST > 100");
        }
        final SplittableRandom random = new SplittableRandom();
        // Equal the branch predictor.
        int equalDistributedArraySize = 16000;
        int[] dataSwitchCase = new int[equalDistributedArraySize];
        initDistributedData("dataSwitchCase", dataSwitchCase, random, 0.166, 0.166, 0.166,
                0.166, 0.166, 0.166, 0.0);
        for (int i = 0; i < equalDistributedArraySize; i++) {
            HttpStatusClass rs = HttpStatusClass.valueOf(dataSwitchCase[i]);
            bh.consume(rs);
        }
        data = new int[size];
        result = new HttpStatusClass[size];
        // Generate bench mark data.
        initDistributedData("data", data, random, 0.38, 0.30, 0.15,
                0.10, 0.05, 0.02, 0.0);
    }

    @SuppressJava6Requirement(reason = "suppress")
    private void initDistributedData(String desc, int[] setUpData, SplittableRandom random, double informationalRatio,
                                     double successRatio, double redirectionRatio, double clientErrorRatio,
                                     double serverErrorRatio, double unknownRatio, double negativeRatio) {
        BigDecimal[] bdArray = { BigDecimal.valueOf(informationalRatio), BigDecimal.valueOf(successRatio),
                BigDecimal.valueOf(redirectionRatio), BigDecimal.valueOf(clientErrorRatio),
                BigDecimal.valueOf(serverErrorRatio), BigDecimal.valueOf(unknownRatio),
                BigDecimal.valueOf(negativeRatio) };
        validateRatios(bdArray);

        int totalCount = 0;
        int informationalCount = (int) (setUpData.length * informationalRatio);
        totalCount += informationalCount;
        int successCount = (int) (setUpData.length * successRatio);
        totalCount += successCount;
        int redirectionCount = (int) (setUpData.length * redirectionRatio);
        totalCount += redirectionCount;
        int clientErrorCount = (int) (setUpData.length * clientErrorRatio);
        totalCount += clientErrorCount;
        int serverErrorCount = (int) (setUpData.length * serverErrorRatio);
        totalCount += serverErrorCount;
        int unknownCount = (int) (setUpData.length * unknownRatio);
        totalCount += unknownCount;
        int negativeCount = (int) (setUpData.length * negativeRatio);
        totalCount += negativeCount;

        double c1x = 0, c2x = 0, c3x = 0, c4x = 0, c5x = 0, c6x = 0, c7x = 0;
        for (int i = 0; i < totalCount;) {
            int code = random.nextInt(100, 800);
            if (HttpStatusClass.INFORMATIONAL.contains(code) && informationalCount-- > 0) {
                setUpData[i++] = code;
                ++c1x;
            }
            if (HttpStatusClass.SUCCESS.contains(code) && successCount-- > 0) {
                setUpData[i++] = code;
                ++c2x;
            }
            if (HttpStatusClass.REDIRECTION.contains(code) && redirectionCount-- > 0) {
                setUpData[i++] = code;
                ++c3x;
            }
            if (HttpStatusClass.CLIENT_ERROR.contains(code) && clientErrorCount-- > 0) {
                setUpData[i++] = code;
                ++c4x;
            }
            if (HttpStatusClass.SERVER_ERROR.contains(code) && serverErrorCount-- > 0) {
                setUpData[i++] = code;
                ++c5x;
            }
            // UNKNOWN:[600,700)
            if (code >= 600 && code < 700 && unknownCount-- > 0) {
                int origin = BigDecimal.valueOf(negativeRatio).compareTo(bdZero) > 0 ? 0 : Integer.MIN_VALUE;
                // Re-generate 'UNKNOWN' code.
                do {
                    code = random.nextInt(origin, Integer.MAX_VALUE);
                } while (code >= 100 && code < 600);
                setUpData[i++] = code;
                ++c6x;
            }
            // Negative:[700,800)
            if (code >= 700 && negativeCount-- > 0) {
                // Re-generate Negative code.
                code = random.nextInt(Integer.MIN_VALUE, 0);
                setUpData[i++] = code;
                ++c7x;
            }
        }

        for (int i = (totalCount - 1); i < setUpData.length; i++) {
            // Generate remaining elements from scope 1xx to 5xx
            int code = random.nextInt(100, 600);
            setUpData[i] = code;
            if (HttpStatusClass.INFORMATIONAL.contains(code)) {
                ++c1x;
            }
            if (HttpStatusClass.SUCCESS.contains(code)) {
                ++c2x;
            }
            if (HttpStatusClass.REDIRECTION.contains(code)) {
                ++c3x;
            }
            if (HttpStatusClass.CLIENT_ERROR.contains(code)) {
                ++c4x;
            }
            if (HttpStatusClass.SERVER_ERROR.contains(code)) {
                ++c5x;
            }
        }
//        printCodePercentage(desc, setUpData.length, c1x, c2x, c3x, c4x, c5x, c6x, c7x);
    }

    private void validateRatios(BigDecimal[] bdArray ) {
        BigDecimal bdSum = new BigDecimal("0.00");
        for (BigDecimal bdParam : bdArray) {
            if (bdParam.compareTo(bdZero) < 0) {
                throw new IllegalArgumentException("Ratio can NOT be negative");
            }
            if (bdParam.compareTo(bdZero) > 0 && bdParam.compareTo(bdLowest) < 0) {
                throw new IllegalArgumentException("If ratio != 0, then the ratio MUST >= 0.01");
            }
            bdSum = bdSum.add(bdParam);
        }
        if (bdSum.compareTo(bdOne) > 0) {
            throw new IllegalArgumentException("Sum of ratios MUST <= 1");
        }
    }

    private void printCodePercentage(String desc, int length, double c1x, double c2x, double c3x, double c4x, double c5x, double c6x, double c7x) {
        System.out.println("\n" + desc + "===>"
                +"INFORMATIONAL:" + df.format(c1x / length)
                + ", SUCCESS:" + df.format(c2x / length)
                + ", REDIRECTION:" + df.format(c3x / length)
                + ", CLIENT_ERROR:" + df.format(c4x / length)
                + ", SERVER_ERROR:" + df.format(c5x / length)
                + ", UNKNOWN:" + df.format(c6x / length)
                + ", NEGATIVE:" + df.format(c7x / length)
        );
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(LinuxPerfNormProfiler.class);
    }

    @Benchmark
    public HttpStatusClass[] valueOf() {
        for (int i = 0; i < size; ++i) {
            result[i] = HttpStatusClass.valueOf(data[i]);
        }
        return result;
    }
}
