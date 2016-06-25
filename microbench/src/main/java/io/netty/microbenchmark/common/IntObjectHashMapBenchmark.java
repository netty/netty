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
import org.agrona.collections.Int2ObjectHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class IntObjectHashMapBenchmark extends AbstractMicrobenchmark {
    private static final Long VALUE = Long.MAX_VALUE;

    public enum MapType {
        AGRONA,
        NETTY
    }

    public enum KeyDistribution {
        HTTP2,
        RANDOM
    }

    @Param({ "10", "100", "1000", "10000", "100000" })
    public int size;

    @Param
    public MapType mapType;

    @Param
    public KeyDistribution keyDistribution;

    private Environment environment;

    @Setup(Level.Trial)
    public void setup() {
        switch(mapType) {
            case AGRONA: {
                environment = new AgronaEnvironment();
                break;
            }
            case NETTY: {
                environment = new NettyEnvironment();
                break;
            }
            default: {
                throw new IllegalStateException("Invalid mapType: " + mapType);
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void put(Blackhole bh) {
        environment.put(bh);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void lookup(Blackhole bh) {
        environment.lookup(bh);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void remove(Blackhole bh) {
        environment.remove(bh);
    }

    private abstract class Environment {
        final int[] keys;
        Environment() {
            keys = new int[size];
            switch(keyDistribution) {
                case HTTP2:
                    for (int index = 0, key = 3; index < size; ++index, key += 2) {
                        keys[index] = key;
                    }
                    break;
                case RANDOM: {
                    // Create a 'size' # of random integers.
                    Random r = new Random();
                    Set<Integer> keySet = new HashSet<Integer>();
                    while (keySet.size() < size) {
                        keySet.add(r.nextInt());
                    }

                    int index = 0;
                    for (Integer key : keySet) {
                        keys[index++] = key;
                    }
                    break;
                }
                default: {
                    throw new IllegalStateException("Unknown keyDistribution: " + keyDistribution);
                }
            }
        }
        abstract void put(Blackhole bh);
        abstract void lookup(Blackhole bh);
        abstract void remove(Blackhole bh);
    }

    private class AgronaEnvironment extends Environment {
        private final Int2ObjectHashMap<Long> map = new Int2ObjectHashMap<Long>();

        AgronaEnvironment() {
            for (int key : keys) {
                map.put(key, VALUE);
            }
        }

        @Override
        void put(Blackhole bh) {
            Int2ObjectHashMap<Long> map = new Int2ObjectHashMap<Long>();
            for (int key : keys) {
                bh.consume(map.put(key, VALUE));
            }
        }

        @Override
        void lookup(Blackhole bh) {
            for (int key : keys) {
                bh.consume(map.get(key));
            }
        }

        @Override
        void remove(Blackhole bh) {
            Int2ObjectHashMap<Long> copy = new Int2ObjectHashMap<Long>();
            copy.putAll(map);
            for (int key : keys) {
                bh.consume(copy.remove(key));
            }
        }
    }

    private class NettyEnvironment extends Environment {
        private final IntObjectHashMap<Long> map = new IntObjectHashMap<Long>();

        NettyEnvironment() {
            for (int key : keys) {
                map.put(key, VALUE);
            }
        }

        @Override
        void put(Blackhole bh) {
            IntObjectHashMap<Long> map = new IntObjectHashMap<Long>();
            for (int key : keys) {
                bh.consume(map.put(key, VALUE));
            }
        }

        @Override
        void lookup(Blackhole bh) {
            for (int key : keys) {
                bh.consume(map.get(key));
            }
        }

        @Override
        void remove(Blackhole bh) {
            IntObjectHashMap<Long> copy = new IntObjectHashMap<Long>();
            copy.putAll(map);
            for (int key : keys) {
                bh.consume(copy.remove(key));
            }
        }
    }
}
