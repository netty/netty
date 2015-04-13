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
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class IntObjectHashMapBenchmark extends AbstractMicrobenchmark {
    private static final Long VALUE = Long.MAX_VALUE;

    @Param({ "10", "100", "1000", "10000", "100000" })
    private int size;

    private int[] keys;
    private IntObjectHashMap<Long> nettyMap = new IntObjectHashMap<Long>();
    private Int2ObjectHashMap<Long> agronaMap = new Int2ObjectHashMap<Long>();

    @Setup(Level.Iteration)
    public void setup() {
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

        for (int key : keys) {
            nettyMap.put(key, VALUE);
            agronaMap.put(key, VALUE);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void nettyPut(Blackhole bh) {
        IntObjectHashMap<Long> map = new IntObjectHashMap<Long>();
        for (int key : keys) {
            bh.consume(map.put(key, VALUE));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void agronaPut(Blackhole bh) {
        Int2ObjectHashMap<Long> map = new Int2ObjectHashMap<Long>();
        for (int key : keys) {
            bh.consume(map.put(key, VALUE));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void nettyLookup(Blackhole bh) {
        for (int key : keys) {
            bh.consume(nettyMap.get(key));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void agronaLookup(Blackhole bh) {
        for (int key : keys) {
            bh.consume(agronaMap.get(key));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void nettyRemove(Blackhole bh) {
        IntObjectHashMap<Long> map = new IntObjectHashMap<Long>();
        map.putAll(nettyMap);
        for (int key : keys) {
            bh.consume(map.remove(key));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void agronaRemove(Blackhole bh) {
        Int2ObjectHashMap<Long> map = new Int2ObjectHashMap<Long>();
        map.putAll(agronaMap);
        for (int key : keys) {
            bh.consume(map.remove(key));
        }
    }
}
