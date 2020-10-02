/*
 * Copyright 2020 The Netty Project
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
package io.netty.util;

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.IdentityHashMap;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class DefaultAttributeMapBenchmark extends AbstractMicrobenchmark {

    @Param({ "8", "32", "128" })
    private int keyCount;
    private AttributeKey<Integer>[] keys;
    private IdentityHashMap<AttributeKey<Integer>, Attribute<Integer>> identityHashMap;
    private DefaultAttributeMap attributes;

    @State(Scope.Thread)
    public static class KeySequence {

        long nextKey;

        @Setup(Level.Iteration)
        public void reset() {
            nextKey = 0;
        }

        public long next() {
            return nextKey++;
        }
    }

    @Setup
    public void init() {
        if (Integer.bitCount(keyCount) != 1) {
            throw new AssertionError("keyCount should cbe a power of 2");
        }
        attributes = new DefaultAttributeMap();
        keys = new AttributeKey[keyCount];
        identityHashMap = new IdentityHashMap<AttributeKey<Integer>, Attribute<Integer>>(keyCount);
        for (int i = 0; i < keyCount; i++) {
            final AttributeKey<Integer> key = AttributeKey.valueOf(Integer.toString(i));
            keys[i] = key;
            final Attribute<Integer> attribute = attributes.attr(key);
            identityHashMap.put(key, attribute);
        }
    }

    @Benchmark
    @Threads(3)
    public Attribute<Integer> nextAttributeIdentityHashMap(KeySequence sequence) {
        long next = sequence.next();
        AttributeKey<Integer>[] keys = this.keys;
        AttributeKey<Integer> key = keys[(int) (next & keys.length - 1)];
        return identityHashMap.get(key);
    }

    @Benchmark
    @Threads(3)
    public boolean hasAttributeIdentityHashMap(KeySequence sequence) {
        long next = sequence.next();
        AttributeKey<Integer>[] keys = this.keys;
        AttributeKey<Integer> key = keys[(int) (next & keys.length - 1)];
        return identityHashMap.containsKey(key);
    }

    @Benchmark
    @Threads(3)
    public void mixedAttributeIdentityHashMap(KeySequence sequence, Blackhole hole) {
        long next = sequence.next();
        AttributeKey<Integer>[] keys = this.keys;
        AttributeKey<Integer> key = keys[(int) (next & keys.length - 1)];
        if (next % 2 == 0) {
            hole.consume(identityHashMap.get(key));
        } else {
            hole.consume(identityHashMap.containsKey(key));
        }
    }

    @Benchmark
    @Threads(3)
    public Attribute<Integer> nextAttributeAttributeMap(KeySequence sequence) {
        long next = sequence.next();
        AttributeKey<Integer>[] keys = this.keys;
        AttributeKey<Integer> key = keys[(int) (next & keys.length - 1)];
        return attributes.attr(key);
    }

    @Benchmark
    @Threads(3)
    public boolean nextHasAttributeAttributeMap(KeySequence sequence) {
        long next = sequence.next();
        AttributeKey<Integer>[] keys = this.keys;
        AttributeKey<Integer> key = keys[(int) (next & keys.length - 1)];
        return attributes.hasAttr(key);
    }

    @Benchmark
    @Threads(3)
    public void mixedAttributeAttributeMap(KeySequence sequence, Blackhole hole) {
        long next = sequence.next();
        AttributeKey<Integer>[] keys = this.keys;
        AttributeKey<Integer> key = keys[(int) (next & keys.length - 1)];
        if (next % 2 == 0) {
            hole.consume(attributes.attr(key));
        } else {
            hole.consume(attributes.hasAttr(key));
        }
    }
}
