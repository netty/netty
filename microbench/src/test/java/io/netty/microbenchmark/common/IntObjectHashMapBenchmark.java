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

    private int[] randomKeys;
    private int[] http2Keys;
    private IntObjectHashMap<Long> randomNettyMap = new IntObjectHashMap<Long>();
    private Int2ObjectHashMap<Long> randomAgronaMap = new Int2ObjectHashMap<Long>();
    private IntObjectHashMap<Long> http2NettyMap = new IntObjectHashMap<Long>();
    private Int2ObjectHashMap<Long> http2AgronaMap = new Int2ObjectHashMap<Long>();

    @Setup(Level.Iteration)
    public void setup() {
        // Create a 'size' # of random integers.
        Random r = new Random();
        Set<Integer> keySet = new HashSet<Integer>();
        while (keySet.size() < size) {
            keySet.add(r.nextInt());
        }

        // Create the random keys and pre-populate the random maps.
        randomKeys = new int[size];
        int index = 0;
        for (Integer key : keySet) {
            randomKeys[index++] = key;
            randomNettyMap.put(key, VALUE);
            randomAgronaMap.put(key, VALUE);
        }

        // Create the HTTP/2 keys and pre-populate the HTTP/2 maps.
        http2Keys = new int[size];
        index = 0;
        for (int key = 3; index < size; ++index, key += 2) {
            http2Keys[index] = key;
            http2NettyMap.put(key, VALUE);
            http2AgronaMap.put(key, VALUE);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void randomNettyPut(Blackhole bh) {
        nettyPut(bh, randomKeys);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void randomAgronaPut(Blackhole bh) {
        agronaPut(bh, randomKeys);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void randomNettyLookup(Blackhole bh) {
        nettyLookup(bh, randomKeys);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void randomAgronaLookup(Blackhole bh) {
        agronaLookup(bh, randomKeys);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void randomNettyRemove(Blackhole bh) {
        nettyRemove(bh, randomKeys, randomNettyMap);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void randomAgronaRemove(Blackhole bh) {
        agronaRemove(bh, randomKeys, randomAgronaMap);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void http2NettyPut(Blackhole bh) {
        nettyPut(bh, http2Keys);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void http2AgronaPut(Blackhole bh) {
        agronaPut(bh, http2Keys);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void http2NettyLookup(Blackhole bh) {
        nettyLookup(bh, http2Keys);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void http2AgronaLookup(Blackhole bh) {
        agronaLookup(bh, http2Keys);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void http2NettyRemove(Blackhole bh) {
        nettyRemove(bh, http2Keys, http2NettyMap);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void http2AgronaRemove(Blackhole bh) {
        agronaRemove(bh, http2Keys, http2AgronaMap);
    }

    private void nettyPut(Blackhole bh, int[] keys) {
        IntObjectHashMap<Long> map = new IntObjectHashMap<Long>();
        for (int key : keys) {
            bh.consume(map.put(key, VALUE));
        }
    }

    private void agronaPut(Blackhole bh, int[] keys) {
        Int2ObjectHashMap<Long> map = new Int2ObjectHashMap<Long>();
        for (int key : keys) {
            bh.consume(map.put(key, VALUE));
        }
    }

    private void nettyLookup(Blackhole bh, int[] keys) {
        for (int key : keys) {
            bh.consume(randomNettyMap.get(key));
        }
    }

    private void agronaLookup(Blackhole bh, int[] keys) {
        for (int key : keys) {
            bh.consume(randomAgronaMap.get(key));
        }
    }

    private void nettyRemove(Blackhole bh, int[] keys, IntObjectHashMap<Long> populatedMap) {
        IntObjectHashMap<Long> map = new IntObjectHashMap<Long>();
        map.putAll(populatedMap);
        for (int key : keys) {
            bh.consume(map.remove(key));
        }
    }

    private void agronaRemove(Blackhole bh, int[] keys, Int2ObjectHashMap<Long> populatedMap) {
        Int2ObjectHashMap<Long> map = new Int2ObjectHashMap<Long>();
        map.putAll(populatedMap);
        for (int key : keys) {
            bh.consume(map.remove(key));
        }
    }
}
