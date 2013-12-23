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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Base class for all JMH benchmarks.
 */
public class AbstractMicrobenchmark {

    protected AbstractMicrobenchmark() {
    }

    protected static final int WARMUP_ITERATIONS = 5;
    protected static final int MEASURE_ITERATIONS = 5;
    protected static final int NUM_FORKS = 1;
    protected static final String JVM_ARGS = "-server -dsa -da -ea:io.netty... -Xms768m" +
        " -Xmx768m -XX:MaxDirectMemorySize=768m -XX:+AggressiveOpts -XX:+UseBiasedLocking" +
        " -XX:+UseFastAccessorMethods -XX:+UseStringCache -XX:+OptimizeStringConcat" +
        " -XX:+HeapDumpOnOutOfMemoryError -Dio.netty.noResourceLeakDetection";

    static {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    }

    @Test
    public void run() throws Exception {
        Options runnerOptions = new OptionsBuilder()
            .include(".*" + getClass().getSimpleName() + ".*")
            .jvmArgs(JVM_ARGS)
            .warmupIterations(WARMUP_ITERATIONS)
            .measurementIterations(MEASURE_ITERATIONS)
            .forks(NUM_FORKS)
            .build();

        new Runner(runnerOptions).run();
    }

}
