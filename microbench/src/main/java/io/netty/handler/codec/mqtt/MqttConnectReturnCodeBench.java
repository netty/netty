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
public class MqttConnectReturnCodeBench extends AbstractMicrobenchmark {

    private static final byte[] DATASET = new byte[] {
            -103, -105, -100, -116, 0, 2, -100, 5, -125, 4, -118, -103, -123, 3, 0, -128, -119, 3, -99, 5, 0, -118,
            -100, 5, -102, -127, -116,
            -103, 4, -128, 0, -122, -101, -116, -128, -112, -124, -127, -118, -116, -107, -126, -119, -100, -121, -103,
            -103, -103, 3,
            -105, -126, 3, -105, -120, -127, -116, -100, -99, -122, -123, -101, -112, -101, 1, -100, 2, -116, 2, -101,
            -97, -100, -124, -123,
            -97, -112, -105, -118, -122, -120, 3, -102, -120, -103, 1, -119, -126, 3, -102, -125, -128, -125, -100,
            -123, 0, -99, -118, -101,
            -118, -112, -126, -128, 4, -121, -101, -124, 1, -118, -127, 1, -119, -120, -127, -100, -112, -124, 2, 5,
            -103, -126, -101, -119,
            -128, -122, -101, -119, -121, 4, -105, -103, 3, -122, 4, -119, -107, 4, -118, -121, -112, -118, 0, -100,
            -116, -124, -107, 3,
            -101, -125, -112, -102, -127, -105, -101, 5, -116, 4, -102, 0, -116, -101, -103, -99, -122, 2, -112, -118,
            -103, -118, 3, -105,
            -120, -99, -103, -116, -124, -124, -123, -128, -116, -101, -124, -122, -101, -122, -116, -118, 5, -97, 1,
            -122, -127, -124, 1, 5,
            4, -116, -112, -123, -123, -99, 4, -124, -126, -124, -103, 2, -119, -97, -116, -99, -101, -124, -97, -99, 0,
            -126, -105, -120,
            -123, -121, -100, -102, 3, -103, 0, -124, -123, -120, 0, -112, -118, -103, 1, -101, 0, -102, -99, 5, -125,
            -100, -116, -105,
            -122, -122, -99, 0, -121, -102, -102, 3, -121, -124, -116, 1, -122, -121, 2, 0, -101, -101, 5, -112, -101,
            -128, 4, -99,
            -105, -121, -118, 4, -122, -99, -103, 1, -102, -120, -99, -101, -103, 5, -120, -112, -118, -123, -128, -103,
            -120, 4, -100, -101,
            -107, -120, -99, -125, -119, -101, 5, 5, -107, -101, 3, -112, -122, -119, -122, -119, 0, -107, -127, 0,
            -122, 4, 0, -119,
            -97, 5, -119, -124, -102, -126, -107, -120, -100, -125, 4, -121, 1, -107, -100, -103, -105, -121, -123,
            -120, -100, 0, 5, -127,
            -128, -103, 3, -99, -121, -105, -101, -116, -127, -116, 1, 2, -105, -116, -126, 4, -123, -112, -105, -127,
            -128, -123, -119, -121,
            0, -119, 4, -119, -99, -97, 4, -112, -127, -103, -107, -124, -119, -102, -122, -122, -116, 1, -102, -107,
            -119, -102, -128, 3,
            -107, -99, -101, -126, -128, 4, -128, -103, 2, -127, -101, 4, -102, 1, -128, -97, -99, -112, -128, -107, 4,
            -118, -100, -125,
            -120, -116, 4, 3, 4, 2, -99, 1, -123, -120, -122, 5, -116, 2, -128, -101, -125, 3, 3, 3, -127, 5, 2, 4,
            -122, -118, 0, -128, -121, -125, -97, -127, -124, 2, -127, -97, -97, -112, 0, -128, -105, -116, -118, -126,
            -116, -119, -126, -127,
            -116, -97, -105, -102, -107, -101, -126, -123, -107, -125, -107, -125, -120, -116, -123, -128, -119, -103,
            -121, -126, -127,
            -112, -121, 5, -127, 4, -124, -99, 4, -127, -118, -107, -99, 4, -119, -116, -127, 4, 4, -112, -124, -102,
            -112, -99, -112, -121,
            -128, 5, -101, -121, -116, -125, -112, -121, -107, -116, -123, 1, -105, -97, -101, -107, -100, -107, -125,
            -121, -118, -123,
            -99, -105, 5, 0, -123, -116, -127, -97, -124, -105, -124, -124, -118, -112, -103, -116, -125, -127, 1, -124,
            5, -103, -128, 3, 2,
            -103, -127, -103, -112, 0, 5, -120, -101, 4, 3, 1, 4, 4, -99, 0, -121, -124, -103, 3, -118, -127, 2, 2, 2,
            -128, -119, 0,
            -122, -112, -124, -101, 3, -102, 3, 3, -118, -99, -120, -125, 2, -100, -120, 0, 3, -120, -127, -122, -122,
            -126, -127, -97,
            -120, -99, -122, -102, -123, -116, -118, -119, -112, 2, 5, -100, -101, 4, -120, -107, 5, 1, -119, 0, -125,
            -105, -101, -100,
            -125, 0, -119, -123, 3, -118, 2, -127, -122, -100, 4, -126, 4, -125, -121, -100, 0, -112, -123, -125, 0,
            -122, -101, -112,
            -119, -118, -127, -124, -128, -127, -102, -125, -103, -103, -126, -116, -107, -125, 0, 3, -123, -100, 3,
            -97, 2, -123, -121, -101,
            -100, -119, -101, -123, -118, 4, 5, -125, -107, -107, 1, -105, 3, -99, -126, -99, 0, -118, 5, -122, -116,
            -105, 2, -124,
            -116, -126, 2, -121, -118, -100, 3, 0, -124, 2, -128, -126, -100, -99, -101, 1, -120, 2, -112, -102, -101,
            5, -107, 1,
            4, 2, -124, -100, -123, 4, -122, -118, -107, -103, -121, -101, -128, -112, -127, -105, -118, 2, -125, -121,
            -101, 4, -126, -123,
            0, -102, -128, -119, -99, 3, 4, -97, -128, -119, -99, -107, -116, -99, -127, -100, -119, -127, -122, -102,
            -119, -118, -119, -103,
            -123, -100, 5, -127, -112, 1, -125, -103, 4, 4, -99, 2, -116, -118, -105, 5, -123, -101, -123, -97, 4, -116,
            2, -124,
            -116, -125, 1, -118, -118, -124, -120, -118, -120, 4, 5, -118, -97, 0, -127, -100, -121, -97, -97, -125,
            -120, -122, -126, -125,
            -100, -97, 1, -97, -116, -126, -97, 2, 4, 2, -101, -103, -124, 2, 4, -123, -124, -107, -120, -122, 1, -123,
            -97, -112,
            -126, 0, -97, 0, -102, -99, -125, -101, -103, 1, -118, 5, -120, -102, -101, -116, -125, -125, -116, -102,
            -120, -121, 3, -118,
            -118, -123, -128, -126, 4, -101, -100, -103, -100, -105, -126, -121, -118, 4, -126, -123, -120, -99, -105,
            -116, -127, -128,
            -119, -124, -100, -120, -101, -100, 5, -116, -119, -105, -99, -119, -103, -103, -101, -107, -102, 5, -107,
            -99, -102, -122, 2,
            -125, -126, 0, -97, -123, 1, 3, 1, 5, -127, 2, -112, -103, -125, -112, -124, -118, -99, 0, 4, 2, -118, 5,
            -128, -122, -120, 5,
            -121, -112, 2, 5, -102, -125, -116, -127, -128, -102, 0, 2, 5, -122, -126, -120, -127, -101, -102, 5, -100,
            -120, -107, -107,
            -126, -101, 5, 4, -125, 4, -124, -125, -119, -123, -103, 2, -123, -105, 0, 1, 3, -121, -101, 3, -107, -105,
            1, -105,
            -122, -124, 0, -103, -116, 0, -101, -127, -122, -118, -103, 1, -107, -123, 1, -121, -107, 4, -102, -101, 4,
            -127, -101, 3,
            -121, -103, -125, -124, -127, 5, -128, 1, 3, -119, -126, -119, -125, -112, -124
    };

    byte[] types;
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
    public MqttConnectReturnCode getViaArray() {
        long next = this.next;
        int nextIndex = (int) (next & mask);
        MqttConnectReturnCode code = MqttConnectReturnCode.valueOf(types[nextIndex]);
        this.next = next + 1;
        return code;
    }

    @Benchmark
    public MqttConnectReturnCode getViaSwitch() {
        long next = this.next;
        int nextIndex = (int) (next & mask);
        MqttConnectReturnCode code = switchValueOf(types[nextIndex]);
        this.next = next + 1;
        return code;
    }

    public static MqttConnectReturnCode switchValueOf(byte b) {
        switch (b) {
        case 0:
            return MqttConnectReturnCode.CONNECTION_ACCEPTED;
        case 1:
            return MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION;
        case 2:
            return MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
        case 3:
            return MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
        case 4:
            return MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;
        case 5:
            return MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
        case -128:
            return MqttConnectReturnCode.CONNECTION_REFUSED_UNSPECIFIED_ERROR;
        case -127:
            return MqttConnectReturnCode.CONNECTION_REFUSED_MALFORMED_PACKET;
        case -126:
            return MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR;
        case -125:
            return MqttConnectReturnCode.CONNECTION_REFUSED_IMPLEMENTATION_SPECIFIC;
        case -124:
            return MqttConnectReturnCode.CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION;
        case -123:
            return MqttConnectReturnCode.CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID;
        case -122:
            return MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USERNAME_OR_PASSWORD;
        case -121:
            return MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5;
        case -120:
            return MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE_5;
        case -119:
            return MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_BUSY;
        case -118:
            return MqttConnectReturnCode.CONNECTION_REFUSED_BANNED;
        case -116:
            return MqttConnectReturnCode.CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD;
        case -112:
            return MqttConnectReturnCode.CONNECTION_REFUSED_TOPIC_NAME_INVALID;
        case -107:
            return MqttConnectReturnCode.CONNECTION_REFUSED_PACKET_TOO_LARGE;
        case -105:
            return MqttConnectReturnCode.CONNECTION_REFUSED_QUOTA_EXCEEDED;
        case -103:
            return MqttConnectReturnCode.CONNECTION_REFUSED_PAYLOAD_FORMAT_INVALID;
        case -102:
            return MqttConnectReturnCode.CONNECTION_REFUSED_RETAIN_NOT_SUPPORTED;
        case -101:
            return MqttConnectReturnCode.CONNECTION_REFUSED_QOS_NOT_SUPPORTED;
        case -100:
            return MqttConnectReturnCode.CONNECTION_REFUSED_USE_ANOTHER_SERVER;
        case -99:
            return MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_MOVED;
        case -97:
            return MqttConnectReturnCode.CONNECTION_REFUSED_CONNECTION_RATE_EXCEEDED;
        default:
            throw new IllegalArgumentException("unknown connect return code: " + (b & 0xFF));
        }
    }
}
