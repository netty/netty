/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.microbenchmark.common;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.collection.IntObjectHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class IntObjectHashMapBenchmark extends AbstractMicrobenchmark {
    private static final Long VALUE = Long.MAX_VALUE;

    @Param({ "10", "100", "1000", "10000", "100000" })
    int size;

    int[] keys;
    IntObjectHashMap<Long> map;
    Int2ObjectHashMap<Long> agronaMap;

    @Setup(Level.Iteration)
    public void setup() {
        map = new IntObjectHashMap<Long>();
        agronaMap = new Int2ObjectHashMap<Long>();

        // Create a 'size' # of random integers.
        Random r = new Random();
        Set<Integer> keySet = new HashSet<Integer>();
        while (keySet.size() < size) {
            keySet.add(r.nextInt());
        }

        // Store them in the keys array.
        keys = new int[size];
        int index = 0;
        for (Integer key : keySet) {
            keys[index++] = key;
        }
    }

    @Benchmark
    public void put(Blackhole bh) {
        for (int key : keys) {
            bh.consume(map.put(key, VALUE));
        }
    }

    @Benchmark
    public void agronaPut(Blackhole bh) {
        for (int key : keys) {
            bh.consume(agronaMap.put(key, VALUE));
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(IntObjectHashMapBenchmark.class.getSimpleName())
                .warmupIterations(5)
                .measurementIterations(5)
                .forks(1)
                .jvmArgs("-ea")
                .build();

        new Runner(opt).run();
    }
}
