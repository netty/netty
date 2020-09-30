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
package io.netty.handler.codec.mqtt;

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MqttQoSValueOfBench extends AbstractMicrobenchmark {

    private static final int[] DATASET = new int[] {
            2, 0, 2, 2, 2, 0, 1, 2, 0, 1, 0, 1, 128, 1, 1, 2, 0, 2, 2, 1, 2, 1, 2, 0, 1, 2, 1, 0, 2, 1, 1, 2, 1, 128,
            2, 2, 1, 2, 2, 1, 1, 128, 0, 128, 2, 2, 1, 1, 2, 128, 128, 128, 128, 128, 0, 2, 128, 128, 2, 1, 128, 1, 0,
            2, 1, 0, 2, 0, 2, 0, 2, 128, 2, 2, 128, 0, 128, 2, 128, 0, 0, 1, 2, 1, 128, 0, 2, 0, 2, 0, 0, 128, 2, 0,
            0, 2, 2, 0, 2, 128, 0, 2, 1, 1, 0, 2, 2, 128, 1, 128, 0, 0, 1, 128, 0, 2, 128, 0, 2, 2, 128, 2, 128, 128,
            0, 2, 128, 0, 1, 2, 2, 1, 128, 0, 1, 128, 1, 2, 0, 0, 2, 0, 128, 0, 1, 0, 0, 1, 0, 1, 2, 128, 128, 1, 2,
            128, 0, 1, 1, 1, 1, 0, 2, 1, 1, 128, 1, 2, 128, 128, 1, 0, 128, 1, 2, 2, 128, 2, 0, 0, 2, 2, 128, 1, 1,
            0, 0, 2, 0, 2, 2, 2, 1, 2, 0, 128, 0, 1, 128, 0, 2, 128, 1, 2, 1, 1, 1, 1, 1, 1, 1, 128, 1, 128, 1, 2,
            0, 0, 128, 128, 1, 2, 2, 0, 0, 0, 2, 128, 128, 0, 1, 128, 1, 128, 1, 2, 128, 2, 0, 0, 2, 128, 128, 0, 2,
            1, 1, 0, 0, 1, 128, 1, 128, 2, 128, 1, 1, 128, 1, 1, 1, 2, 2, 2, 128, 128, 0, 1, 1, 2, 128, 1, 0, 2, 2,
            2, 2, 1, 1, 0, 128, 1, 128, 2, 1, 1, 0, 128, 0, 2, 128, 128, 2, 0, 128, 128, 1, 128, 0, 1, 128, 0, 128,
            1, 1, 2, 128, 0, 2, 128, 2, 0, 128, 0, 1, 0, 0, 0, 2, 0, 2, 1, 2, 1, 2, 0, 2, 2, 128, 128, 2, 0, 2, 1,
            128, 1, 128, 1, 0, 128, 0, 0, 128, 1, 0, 128, 1, 0, 128, 0, 128, 1, 2, 1, 128, 2, 0, 1, 0, 1, 2, 1, 0,
            1, 2, 2, 2, 1, 0, 0, 128, 1, 2, 128, 0, 2, 128, 0, 1, 2, 128, 2, 1, 1, 2, 2, 0, 0, 2, 1, 1, 128, 0, 2,
            1, 128, 128, 128, 1, 1, 0, 128, 0, 0, 1, 128, 0, 2, 1, 2, 1, 0, 1, 0, 0, 1, 128, 1, 2, 128, 128, 2, 1, 1,
            0, 1, 0, 1, 2, 0, 128, 2, 0, 2, 1, 2, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 128, 0, 1, 2, 0, 0, 2, 2, 2, 0, 0,
            128, 1, 0, 0, 0, 1, 2, 128, 2, 0, 128, 128, 128, 128, 2, 2, 0, 1, 1, 0, 1, 128, 128, 1, 0, 0, 128, 128,
            1, 2, 128, 2, 128, 128, 128, 2, 0, 1, 128, 1, 0, 1, 2, 128, 128, 2, 2, 0, 0, 1, 1, 0, 1, 1, 0, 128, 0,
            0, 0, 128, 0, 0, 0, 128, 2, 1, 0, 1, 1, 0, 2, 1, 1, 0, 2, 1, 2, 1, 2, 0, 0, 2, 128, 128, 0, 0, 2, 128,
            0, 128, 2, 2, 128, 0, 128, 1, 1, 0, 0, 128, 2, 1, 2, 128, 0, 2, 128, 1, 0, 1, 2, 0, 0, 128, 128, 0, 1,
            2, 128, 1, 2, 2, 0, 128, 2, 1, 0, 1, 128, 128, 2, 0, 1, 0, 0, 2, 0, 128, 2, 2, 1, 128, 128, 128, 1, 0,
            128, 2, 128, 1, 128, 1, 1, 2, 0, 128, 0, 1, 0, 128, 0, 2, 2, 0, 0, 2, 128, 128, 2, 0, 128, 1, 128, 128,
            2, 1, 1, 2, 0, 2, 128, 0, 0, 0, 2, 2, 2, 0, 128, 0, 1, 2, 1, 128, 1, 2, 2, 0, 128, 1, 1, 1, 128, 2, 2,
            128, 0, 0, 1, 2, 1, 1, 2, 0, 1, 2, 128, 2, 2, 128, 128, 0, 128, 1, 1, 128, 128, 128, 2, 1, 1, 1, 2, 1,
            1, 0, 1, 128, 0, 2, 2, 0, 1, 2, 128, 128, 128, 2, 128, 128, 128, 2, 2, 2, 0, 128, 2, 128, 1, 0, 128,
            128, 2, 128, 0, 2, 1, 128, 128, 0, 1, 1, 0, 128, 0, 0, 2, 1, 0, 2, 1, 2, 128, 0, 128, 1, 128, 1, 0, 2,
            1, 1, 1, 2, 1, 0, 1, 128, 2, 2, 0, 1, 128, 1, 0, 1, 0, 0, 128, 0, 128, 2, 2, 128, 128, 1, 0, 128, 1, 0,
            2, 1, 128, 128, 0, 0, 0, 0, 128, 2, 2, 1, 128, 1, 0, 1, 128, 0, 128, 128, 1, 128, 0, 2, 2, 2, 0, 2, 0,
            1, 1, 2, 1, 1, 1, 128, 0, 2, 2, 2, 0, 2, 0, 1, 1, 1, 128, 128, 128, 128, 2, 128, 1, 0, 1, 1, 1, 1, 2, 2,
            1, 0, 128, 2, 128, 128, 0, 1, 128, 128, 128, 128, 128, 1, 0, 2, 0, 128, 0, 0, 2, 2, 0, 1, 2, 1, 0, 2, 128,
            0, 2, 2, 2, 0, 0, 1, 128, 2, 1, 128, 128, 1, 128, 0, 1, 128, 1, 1, 1, 2, 0, 128, 1, 128, 2, 2, 0, 2, 0,
            0, 0, 0, 0, 2, 128, 0, 1, 2, 0, 0, 2, 2, 2, 0, 0, 0, 1, 0, 128, 1, 0, 1, 1, 128, 128, 128, 1, 128, 0, 128,
            2, 1, 2, 1, 0, 1, 2, 128, 2, 1, 1, 2, 1, 128, 1, 2, 0, 2, 128, 2, 128, 2, 2, 1, 1, 128, 2, 2, 0, 128, 0, 2,
            1, 128, 128, 128, 0, 1, 2, 2, 2, 0, 0, 128, 1, 2, 2, 128, 128, 128, 1, 128, 0, 2, 2, 1, 2, 2, 2, 0, 0, 2,
            2, 0, 2, 2, 128, 0, 2, 128, 1, 0, 1, 128, 1, 0, 128, 1, 128, 1, 0, 1, 2, 1, 128, 1, 128, 2, 128, 128, 1,
            2, 128, 1, 2, 0, 2
    };

    int[] types;
    long next;
    long mask;

    @Setup
    public void initDataSet() {
        types = DATASET;
        next = 0;
        mask = types.length - 1;
        if (Integer.bitCount(types.length) != 1) {
            throw new AssertionError("The data set should contains power of 2 items");
        }
    }

    @Benchmark
    public MqttQoS getViaArray() {
        long next = this.next;
        int nextIndex = (int) (next & mask);
        MqttQoS mqttQoS = arrayValueOf(types[nextIndex]);
        this.next = next + 1;
        return mqttQoS;
    }

    @Benchmark
    public MqttQoS getViaSwitch() {
        long next = this.next;
        int nextIndex = (int) (next & mask);
        MqttQoS mqttQoS = MqttQoS.valueOf(types[nextIndex]);
        this.next = next + 1;
        return mqttQoS;
    }

    private static final MqttQoS[] VALUES;

    static {
        VALUES = new MqttQoS[129];
        for (MqttQoS value : MqttQoS.values()) {
            VALUES[value.value()] = value;
        }
    }

    public static MqttQoS arrayValueOf(int value) {
        MqttQoS mqttQoS = null;
        try {
            mqttQoS = VALUES[value];
            return mqttQoS;
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // nop
        }
        if (mqttQoS == null) {
            throw new IllegalArgumentException("invalid QoS: " + value);
        }
        return mqttQoS;
    }
}
