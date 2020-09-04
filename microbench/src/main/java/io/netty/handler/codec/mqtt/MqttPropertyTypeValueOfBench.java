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

import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
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
public class MqttPropertyTypeValueOfBench extends AbstractMicrobenchmark {

    private static final int[] DATASET = new int[] {
            28, 28, 40, 28, 36, 22, 23, 22, 11, 40, 18, 33, 8, 37, 23, 23, 33, 2, 2, 33, 18,
            31, 39, 41, 39, 25, 2, 28, 26, 38, 42, 9, 40, 3, 2, 19, 1, 8, 18, 38, 28, 9, 18,
            22, 42, 22, 37, 42, 23, 38, 22, 40, 2, 8, 40, 26, 1, 8, 17, 2, 37, 21, 41, 8, 37, 26, 1, 17,
            39, 40, 41, 41, 9, 28, 38, 38, 36, 23, 19, 9, 9, 11, 25, 11, 9, 35, 31, 25, 3, 39,
            17, 2, 35, 33, 21, 26, 34, 1, 34, 22, 33, 19, 17, 24, 9, 17, 40, 40, 11, 3, 19, 34,
            42, 42, 11, 22, 39, 1, 25, 37, 41, 21, 42, 39, 1, 22, 21, 8, 28, 21, 17, 3, 39, 24,
            33, 25, 9, 19, 42, 11, 42, 19, 2, 19, 35, 1, 11, 35, 42, 39, 9, 36, 36, 1, 23, 26,
            41, 2, 41, 38, 9, 37, 9, 33, 2, 26, 18, 39, 21, 21, 8, 24, 17, 40, 1, 35, 36, 37,
            21, 25, 1, 2, 24, 37, 3, 9, 42, 24, 28, 37, 24, 3, 35, 31, 11, 17, 42, 21, 23, 11,
            41, 22, 38, 23, 11, 42, 38, 39, 34, 28, 21, 24, 9, 2, 35, 36, 21, 9, 21, 1, 8, 18,
            3, 17, 24, 28, 40, 36, 21, 21, 3, 41, 41, 41, 22, 24, 19, 38, 39, 26, 3, 2, 41, 11,
            25, 34, 42, 38, 31, 21, 23, 34, 8, 36, 19, 19, 42, 39, 3, 42, 35, 11, 33, 9, 17, 21,
            18, 9, 42, 38, 24, 17, 34, 24, 3, 21, 23, 40, 25, 33, 11, 19, 31, 40, 24, 25, 23, 36,
            23, 17, 39, 17, 22, 34, 28, 18, 25, 42, 31, 24, 19, 40, 21, 38, 22, 42, 35, 37, 41, 1,
            17, 33, 3, 21, 42, 33, 2, 36, 36, 35, 42, 24, 37, 41, 8, 22, 36, 26, 42, 11, 41, 26,
            24, 34, 11, 9, 34, 19, 23, 41, 22, 3, 35, 24, 1, 36, 24, 3, 18, 33, 2, 42, 42, 18,
            19, 41, 38, 21, 34, 19, 40, 38, 19, 39, 21, 39, 42, 3, 36, 18, 22, 3, 25, 22, 28, 31,
            31, 23, 24, 19, 34, 26, 33, 34, 42, 18, 3, 42, 19, 24, 21, 31, 8, 42, 25, 24, 39, 35,
            3, 42, 31, 3, 18, 19, 24, 28, 3, 25, 39, 40, 40, 34, 33, 1, 41, 21, 17, 34, 31, 34,
            34, 8, 17, 17, 19, 21, 21, 9, 21, 39, 24, 1, 23, 8, 37, 37, 23, 21, 34, 42, 23, 18,
            42, 9, 34, 23, 24, 22, 11, 18, 18, 35, 24, 42, 23, 1, 31, 2, 9, 11, 24, 22, 34, 28,
            11, 23, 26, 25, 31, 19, 39, 11, 40, 24, 41, 2, 11, 23, 33, 42, 34, 9, 17, 28, 33, 28,
            2, 2, 21, 41, 42, 33, 33, 2, 8, 28, 19, 24, 36, 21, 36, 1, 19, 8, 1, 23, 21, 3,
            40, 28, 38, 22, 21, 19, 37, 2, 23, 8, 33, 8, 31, 25, 17, 40, 36, 22, 3, 41, 21, 22,
            41, 23, 3, 33, 26, 11, 33, 1, 9, 33, 40, 24, 11, 34, 8, 34, 19, 21, 34, 41, 19, 34,
            42, 26, 41, 37, 28, 24, 42, 11, 38, 35, 33, 2, 26, 21, 9, 25, 9, 18, 33, 24, 19, 2,
            11, 40, 37, 36, 19, 28, 40, 26, 41, 35, 21, 23, 28, 22, 19, 34, 3, 31, 36, 38, 25, 34,
            31, 40, 38, 3, 22, 9, 8, 40, 26, 9, 17, 11, 11, 31, 19, 3, 24, 23, 3, 2, 19, 9,
            28, 19, 28, 37, 18, 42, 38, 26, 37, 26, 39, 3, 33, 28, 17, 11, 25, 38, 34, 22, 34, 17,
            3, 1, 34, 38, 8, 2, 37, 25, 9, 11, 36, 23, 19, 8, 35, 24, 11, 11, 11, 25, 11, 11,
            11, 28, 17, 42, 19, 41, 40, 34, 38, 24, 28, 23, 39, 28, 41, 40, 3, 39, 34, 11, 25, 33,
            2, 1, 3, 26, 28, 2, 17, 18, 2, 41, 42, 37, 36, 33, 38, 33, 18, 3, 34, 37, 21, 37,
            23, 35, 21, 3, 9, 21, 34, 38, 22, 37, 28, 38, 8, 2, 31, 1, 38, 25, 40, 35, 37, 41,
            36, 31, 23, 21, 37, 3, 24, 17, 17, 8, 22, 8, 2, 23, 1, 17, 31, 38, 9, 23, 42, 41,
            2, 33, 11, 23, 33, 38, 17, 25, 1, 33, 37, 19, 8, 23, 41, 26, 39, 18, 18, 31, 17, 18,
            34, 3, 41, 34, 40, 9, 23, 33, 11, 40, 39, 34, 19, 40, 3, 2, 19, 17, 23, 33, 2, 19,
            26, 25, 36, 37, 34, 17, 39, 42, 22, 22, 19, 35, 22, 18, 18, 41, 40, 40, 26, 3, 19, 40,
            9, 1, 19, 41, 24, 9, 18, 1, 28, 31, 18, 3, 21, 11, 24, 3, 22, 11, 11, 37, 22, 8,
            2, 38, 3, 2, 37, 28, 11, 35, 18, 36, 9, 35, 21, 19, 42, 35, 24, 2, 2, 17, 18, 33,
            33, 34, 8, 37, 24, 42, 17, 37, 21, 1, 36, 38, 25, 40, 1, 22, 26, 28, 22, 33, 28, 1,
            33, 33, 33, 19, 40, 2, 36, 38, 33, 41, 2, 3, 31, 22, 1, 24, 18, 36, 28, 39, 28, 3,
            8, 35, 17, 18, 18, 8, 18, 22, 2, 25, 18, 41, 37, 21, 17, 28, 34, 1, 35, 25, 22, 38,
            17, 28, 19, 25, 35, 36, 39, 9, 21, 36, 39, 41, 22, 38, 39, 19, 34, 22, 40, 8, 1, 11,
            1, 31, 1, 17, 24, 23, 28, 21, 8, 37, 42, 33, 17, 24, 19, 18, 2, 42, 39, 36, 19, 2,
            34, 35, 36, 11, 9, 35, 2, 21, 3, 42, 28, 37, 24, 1, 38, 2, 11, 41, 33, 39, 25, 17,
            26, 39, 36, 37, 11, 25, 42, 17, 8, 31, 41, 21, 22, 2, 2, 24, 19, 21, 31, 34, 2, 39,
            39, 18, 1, 33, 28, 11, 34, 40, 17, 42,
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
    public MqttPropertyType getViaArray() {
        long next = this.next;
        int nextIndex = (int) (next & mask);
        MqttPropertyType type = MqttPropertyType.valueOf(types[nextIndex]);
        this.next = next + 1;
        return type;
    }

    @Benchmark
    public MqttPropertyType getViaSwitch() {
        long next = this.next;
        int nextIndex = (int) (next & mask);
        MqttPropertyType type = switchValueOf(types[nextIndex]);
        this.next = next + 1;
        return type;
    }

    private static MqttPropertyType switchValueOf(int type) {
        switch (type) {
        case 1:
            return MqttPropertyType.PAYLOAD_FORMAT_INDICATOR;
        case 2:
            return MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL;
        case 3:
            return MqttPropertyType.CONTENT_TYPE;
        case 8:
            return MqttPropertyType.RESPONSE_TOPIC;
        case 9:
            return MqttPropertyType.CORRELATION_DATA;
        case 11:
            return MqttPropertyType.SUBSCRIPTION_IDENTIFIER;
        case 17:
            return MqttPropertyType.SESSION_EXPIRY_INTERVAL;
        case 18:
            return MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER;
        case 19:
            return MqttPropertyType.SERVER_KEEP_ALIVE;
        case 21:
            return MqttPropertyType.AUTHENTICATION_METHOD;
        case 22:
            return MqttPropertyType.AUTHENTICATION_DATA;
        case 23:
            return MqttPropertyType.REQUEST_PROBLEM_INFORMATION;
        case 24:
            return MqttPropertyType.WILL_DELAY_INTERVAL;
        case 25:
            return MqttPropertyType.REQUEST_RESPONSE_INFORMATION;
        case 26:
            return MqttPropertyType.RESPONSE_INFORMATION;
        case 28:
            return MqttPropertyType.SERVER_REFERENCE;
        case 31:
            return MqttPropertyType.REASON_STRING;
        case 33:
            return MqttPropertyType.RECEIVE_MAXIMUM;
        case 34:
            return MqttPropertyType.TOPIC_ALIAS_MAXIMUM;
        case 35:
            return MqttPropertyType.TOPIC_ALIAS;
        case 36:
            return MqttPropertyType.MAXIMUM_QOS;
        case 37:
            return MqttPropertyType.RETAIN_AVAILABLE;
        case 38:
            return MqttPropertyType.USER_PROPERTY;
        case 39:
            return MqttPropertyType.MAXIMUM_PACKET_SIZE;
        case 40:
            return MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE;
        case 41:
            return MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE;
        case 42:
            return MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE;
        default:
            throw new IllegalArgumentException("unknown message type: " + type);
        }
    }
}
