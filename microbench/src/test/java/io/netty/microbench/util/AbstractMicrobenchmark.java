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
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;
import org.junit.Test;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all JMH benchmarks.
 */
@Warmup(iterations = AbstractMicrobenchmark.DEFAULT_WARMUP_ITERATIONS)
@Measurement(iterations = AbstractMicrobenchmark.DEFAULT_MEASURE_ITERATIONS)
@Fork(AbstractMicrobenchmark.DEFAULT_FORKS)
@State(Scope.Thread)
public class AbstractMicrobenchmark {

    protected static final int DEFAULT_WARMUP_ITERATIONS = 10;
    protected static final int DEFAULT_MEASURE_ITERATIONS = 10;
    protected static final int DEFAULT_FORKS = 2;

    public static final class HarnessExecutor extends ThreadPoolExecutor {
        public HarnessExecutor(int maxThreads, String prefix) {
            super(0, maxThreads, 1L, TimeUnit.DAYS, new SynchronousQueue<Runnable>(),
                  new DefaultThreadFactory(prefix));
            System.out.println("Using harness executor");
        }
    }

    protected static final String[] JVM_ARGS = {
        "-server", "-dsa", "-da", "-ea:io.netty...", "-Xms768m", "-Xmx768m",
        "-XX:MaxDirectMemorySize=768m", "-XX:+AggressiveOpts", "-XX:+UseBiasedLocking",
        "-XX:+UseFastAccessorMethods", "-XX:+UseStringCache", "-XX:+OptimizeStringConcat",
        "-XX:+HeapDumpOnOutOfMemoryError", "-Dio.netty.noResourceLeakDetection",
        "-Dharness.executor=CUSTOM",
        "-Dharness.executor.class=io.netty.microbench.util.AbstractMicrobenchmark$HarnessExecutor"
    };

    static {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    }

    @Test
    public void run() throws Exception {
        String className = getClass().getSimpleName();

        ChainedOptionsBuilder runnerOptions = new OptionsBuilder()
            .include(".*" + className + ".*")
            .jvmArgs(JVM_ARGS);

        if (getWarmupIterations() > 0) {
            runnerOptions.warmupIterations(getWarmupIterations());
        }

        if (getMeasureIterations() > 0) {
            runnerOptions.measurementIterations(getMeasureIterations());
        }

        if (getForks() > 0) {
            runnerOptions.forks(getForks());
        }

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
        return SystemPropertyUtil.getInt("warmupIterations", -1);
    }

    protected int getMeasureIterations() {
        return SystemPropertyUtil.getInt("measureIterations", -1);
    }

    protected int getForks() {
        return SystemPropertyUtil.getInt("forks", -1);
    }

    protected String getReportDir() {
        return SystemPropertyUtil.get("perfReportDir");
    }
}
