/*
 * Copyright 2016 The Netty Project
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
package io.netty.microbench.concurrent;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.concurrent.FastThreadLocal;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;

/**
 * This class benchmarks the slow path of FastThreadLocal and the JDK ThreadLocal.
 */
@Threads(4)
@Measurement(iterations = 10, batchSize = 100)
public class FastThreadLocalSlowPathBenchmark extends AbstractMicrobenchmark {

    private static final Random rand = new Random();

    @SuppressWarnings("unchecked")
    private static final ThreadLocal<Integer>[] jdkThreadLocals = new ThreadLocal[128];
    @SuppressWarnings("unchecked")
    private static final FastThreadLocal<Integer>[] fastThreadLocals = new FastThreadLocal[jdkThreadLocals.length];

    static {
        for (int i = 0; i < jdkThreadLocals.length; i ++) {
            final int num = rand.nextInt();
            jdkThreadLocals[i] = new ThreadLocal<Integer>() {
                @Override
                protected Integer initialValue() {
                    return num;
                }
            };
            fastThreadLocals[i] = new FastThreadLocal<Integer>() {
                @Override
                protected Integer initialValue() {
                    return num;
                }
            };
        }
    }

    public FastThreadLocalSlowPathBenchmark() {
        super(true, true);
    }

    @Benchmark
    public void jdkThreadLocalGet(Blackhole bh) {
        for (ThreadLocal<Integer> i: jdkThreadLocals) {
            bh.consume(i.get());
        }
    }

    @Benchmark
    public void fastThreadLocal(Blackhole bh) {
        for (FastThreadLocal<Integer> i: fastThreadLocals) {
            bh.consume(i.get());
        }
    }
}
