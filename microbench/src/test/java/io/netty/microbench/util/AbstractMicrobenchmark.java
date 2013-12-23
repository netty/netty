/*
 * Copyright 2012 The Netty Project
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
package io.netty.microbench.util;

import io.netty.util.ResourceLeakDetector;
import org.junit.Test;
import org.openjdk.jmh.output.OutputFormatType;
import org.openjdk.jmh.output.results.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;

/**
 * Base class for all JMH benchmarks.
 */
public class AbstractMicrobenchmark {

    protected static final String WARMUP_ITERATIONS = "10";
    protected static final String MEASURE_ITERATIONS = "10";
    protected static final String NUM_FORKS = "2";

    protected static final String JVM_ARGS = "-server -dsa -da -ea:io.netty... -Xms768m" +
        " -Xmx768m -XX:MaxDirectMemorySize=768m -XX:+AggressiveOpts -XX:+UseBiasedLocking" +
        " -XX:+UseFastAccessorMethods -XX:+UseStringCache -XX:+OptimizeStringConcat" +
        " -XX:+HeapDumpOnOutOfMemoryError -Dio.netty.noResourceLeakDetection";

    static {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    }

    @Test
    public void run() throws Exception {
        String className = getClass().getSimpleName();

        ChainedOptionsBuilder runnerOptions = new OptionsBuilder()
            .include(".*" + className + ".*")
            .jvmArgs(JVM_ARGS)
            .warmupIterations(getWarmupIterations())
            .measurementIterations(getMeasureIterations())
            .forks(getForks());

        if (getReportDir() != null) {
            String filePath = getReportDir() + className + ".json";
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            } else {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            runnerOptions.resultFormat(ResultFormatType.JSON);
            runnerOptions.result(filePath);
        }

        new Runner(runnerOptions.build()).run();
    }

    protected int getWarmupIterations() {
        return Integer.parseInt(System.getProperty("warmupIterations", WARMUP_ITERATIONS));
    }

    protected int getMeasureIterations() {
        return Integer.parseInt(System.getProperty("measureIterations", MEASURE_ITERATIONS));
    }

    protected int getForks() {
        return Integer.parseInt(System.getProperty("forks", NUM_FORKS));
    }

    protected String getReportDir() {
        return System.getProperty("perfReportDir", null);
    }
}
