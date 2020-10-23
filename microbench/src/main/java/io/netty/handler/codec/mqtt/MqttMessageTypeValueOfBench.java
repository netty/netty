/*
 * Copyright 2020 The Netty Project
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
public class MqttMessageTypeValueOfBench extends AbstractMicrobenchmark {

    private static final int[] DATASET = new int[] {
            9, 5, 2, 9, 8, 4, 3, 7, 7, 9, 6, 11, 10, 4, 10, 6, 12, 6, 14, 8, 2, 2, 5, 7, 13, 3, 10, 12, 6, 2, 8, 1, 6,
            4, 10, 8, 13, 9, 9, 2, 7, 12, 2, 3, 12, 9, 3, 12,
            11, 4, 10, 11, 9, 10, 5, 9, 4, 14, 6, 10, 13, 9, 12, 7, 5, 3, 1, 2, 7, 11, 1, 8, 4, 9, 5, 11, 14, 6, 3, 4,
            3, 1, 12, 9, 6, 1, 10, 2, 9, 5, 10, 4, 5, 1, 8,
            2, 11, 9, 7, 10, 14, 9, 12, 14, 6, 13, 6, 14, 6, 1, 3, 1, 10, 13, 13, 2, 2, 8, 7, 10, 9, 9, 4, 7, 13, 4, 10,
            3, 14, 14, 4, 3, 6, 7, 13, 13, 2, 3, 13, 5, 2,
            14, 11, 1, 5, 6, 14, 13, 12, 3, 9, 10, 1, 4, 1, 1, 13, 5, 8, 1, 8, 2, 7, 9, 14, 13, 2, 11, 10, 11, 5, 9, 13,
            13, 12, 11, 6, 1, 7, 11, 1, 11, 7, 8, 1, 13,
            12, 1, 5, 10, 2, 13, 4, 8, 2, 14, 8, 8, 9, 14, 12, 11, 1, 10, 6, 7, 2, 1, 12, 11, 8, 9, 10, 13, 2, 12, 3, 8,
            1, 13, 11, 8, 6, 4, 5, 8, 5, 12, 10, 9, 4, 7,
            2, 1, 11, 6, 7, 11, 5, 1, 5, 2, 7, 7, 14, 14, 3, 2, 1, 8, 5, 7, 4, 13, 13, 7, 8, 2, 14, 1, 12, 7, 8, 8, 3,
            9, 8, 1, 11, 10, 13, 10, 2, 1, 12, 5, 3, 3, 12, 5,
            7, 12, 13, 10, 14, 9, 2, 4, 12, 4, 10, 10, 2, 9, 2, 7, 5, 6, 2, 14, 10, 3, 4, 5, 8, 1, 14, 13, 1, 2, 5, 11,
            8, 6, 8, 3, 8, 13, 12, 8, 2, 12, 6, 2, 5, 4, 13,
            5, 11, 11, 5, 12, 9, 9, 9, 6, 4, 4, 11, 14, 12, 9, 3, 4, 12, 10, 10, 6, 3, 2, 12, 3, 2, 10, 8, 7, 10, 12,
            13, 1, 2, 7, 13, 2, 13, 4, 13, 14, 10, 14, 7, 5,
            11, 10, 9, 9, 1, 9, 10, 3, 9, 1, 13, 7, 9, 7, 1, 8, 14, 2, 6, 11, 2, 2, 11, 4, 10, 10, 9, 4, 4, 13, 7, 2, 1,
            4, 14, 6, 11, 5, 2, 5, 9, 5, 8, 4, 5, 6, 2, 12, 2,
            5, 2, 14, 3, 11, 5, 4, 14, 14, 2, 7, 7, 2, 3, 11, 2, 10, 9, 13, 3, 4, 2, 10, 1, 2, 10, 7, 7, 6, 8, 8, 12,
            14, 8, 13, 1, 9, 5, 9, 1, 14, 2, 5, 5, 5, 3, 13, 11,
            9, 6, 11, 1, 10, 13, 4, 7, 9, 6, 3, 4, 11, 8, 13, 3, 13, 12, 7, 7, 5, 9, 11, 3, 9, 6, 5, 6, 6, 11, 9, 2, 7,
            1, 12, 7, 5, 8, 11, 4, 9, 10, 11, 12, 7, 8, 1, 2,
            3, 14, 3, 9, 11, 9, 7, 4, 4, 4, 8, 4, 4, 2, 5, 8, 2, 11, 7, 13, 2, 14, 3, 6, 7, 14, 12, 6, 9, 11, 10, 9, 6,
            10, 6, 14, 4, 1, 7, 12, 4, 13, 10, 2, 2, 3, 3, 14,
            14, 2, 9, 12, 3, 9, 7, 6, 12, 8, 9, 5, 11, 13, 14, 14, 4, 1, 11, 14, 5, 9, 7, 14, 7, 13, 7, 14, 3, 14, 2, 8,
            2, 5, 10, 12, 14, 9, 11, 3, 14, 8, 12, 12, 5, 2,
            6, 2, 1, 14, 12, 8, 14, 1, 11, 14, 8, 9, 9, 1, 12, 13, 7, 8, 10, 5, 8, 5, 14, 13, 14, 3, 14, 2, 9, 12, 3,
            10, 3, 2, 4, 3, 5, 5, 10, 10, 13, 10, 7, 6, 4, 2, 10,
            8, 14, 2, 7, 1, 2, 7, 13, 2, 3, 6, 14, 3, 8, 12, 3, 4, 12, 6, 3, 10, 6, 14, 9, 1, 6, 3, 14, 7, 1, 7, 2, 12,
            9, 5, 9, 6, 13, 5, 11, 13, 11, 10, 1, 14, 9, 13, 8,
            12, 14, 14, 8, 13, 2, 6, 14, 2, 2, 9, 12, 9, 7, 2, 11, 4, 6, 8, 10, 12, 10, 11, 2, 9, 9, 5, 4, 3, 4, 4, 10,
            3, 1, 12, 13, 9, 8, 1, 9, 9, 4, 2, 7, 3, 4, 11, 11,
            8, 10, 14, 5, 14, 1, 10, 10, 13, 5, 6, 13, 14, 5, 7, 11, 4, 13, 3, 14, 7, 2, 10, 13, 2, 4, 14, 5, 1, 12, 3,
            13, 11, 2, 11, 14, 2, 5, 8, 13, 4, 13, 13, 3, 3,
            3, 13, 6, 11, 5, 3, 2, 13, 9, 2, 10, 8, 3, 11, 4, 6, 12, 14, 6, 2, 14, 1, 2, 6, 8, 4, 12, 8, 11, 9, 1, 7, 1,
            10, 4, 10, 9, 9, 3, 11, 5, 10, 8, 9, 4, 13, 4,
            5, 7, 12, 14, 12, 6, 1, 2, 10, 9, 10, 12, 1, 2, 6, 9, 5, 13, 4, 6, 11, 7, 1, 3, 10, 2, 1, 13, 14, 3, 5, 5,
            5, 7, 14, 9, 9, 3, 12, 1, 1, 1, 3, 12, 6, 9, 7, 8, 1,
            8, 2, 8, 13, 1, 11, 11, 1, 4, 10, 4, 3, 10, 3, 2, 2, 8, 2, 4, 13, 14, 4, 12, 14, 7, 6, 7, 13, 7, 11, 13, 12,
            14, 1, 14, 3, 4, 13, 12, 10, 5, 12, 12, 4, 5, 6,
            9, 12, 13, 3, 4, 13, 8, 14, 3, 2, 8, 5, 6, 13, 8, 7, 4, 5, 8, 14, 8, 14, 7, 5, 4, 9, 12, 12, 10, 3, 1, 12,
            5, 1, 11, 6, 10, 5, 14, 4, 5, 13, 8, 11, 13, 4, 9,
            9, 7, 6, 2, 2, 5, 12, 13, 13, 6, 11, 13, 12, 10, 6, 7, 1, 2, 6, 1, 9, 10, 14, 7, 9, 2, 2, 2, 8, 8, 11, 14,
            12, 9, 13, 1
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
    public MqttMessageType getViaArray() {
        long next = this.next;
        int nextIndex = (int) (next & mask);
        MqttMessageType type = MqttMessageType.valueOf(types[nextIndex]);
        this.next = next + 1;
        return type;
    }

    @Benchmark
    public MqttMessageType getViaSwitch() {
        long next = this.next;
        int nextIndex = (int) (next & mask);
        MqttMessageType type = switchValueOf(types[nextIndex]);
        this.next = next + 1;
        return type;
    }

    private static MqttMessageType switchValueOf(int type) {
        switch (type) {
        case 1:
            return MqttMessageType.CONNECT;
        case 2:
            return MqttMessageType.CONNACK;
        case 3:
            return MqttMessageType.PUBLISH;
        case 4:
            return MqttMessageType.PUBACK;
        case 5:
            return MqttMessageType.PUBREC;
        case 6:
            return MqttMessageType.PUBREL;
        case 7:
            return MqttMessageType.PUBCOMP;
        case 8:
            return MqttMessageType.SUBSCRIBE;
        case 9:
            return MqttMessageType.SUBACK;
        case 10:
            return MqttMessageType.UNSUBSCRIBE;
        case 11:
            return MqttMessageType.UNSUBACK;
        case 12:
            return MqttMessageType.PINGREQ;
        case 13:
            return MqttMessageType.PINGRESP;
        case 14:
            return MqttMessageType.DISCONNECT;
        default:
            throw new IllegalArgumentException("unknown message type: " + type);
        }
    }
}
