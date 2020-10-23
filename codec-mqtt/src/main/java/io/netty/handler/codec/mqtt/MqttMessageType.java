/*
 * Copyright 2014 The Netty Project
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

/**
 * MQTT Message Types.
 */
public enum MqttMessageType {
    CONNECT(1),
    CONNACK(2),
    PUBLISH(3),
    PUBACK(4),
    PUBREC(5),
    PUBREL(6),
    PUBCOMP(7),
    SUBSCRIBE(8),
    SUBACK(9),
    UNSUBSCRIBE(10),
    UNSUBACK(11),
    PINGREQ(12),
    PINGRESP(13),
    DISCONNECT(14),
    AUTH(15);

    private static final MqttMessageType[] VALUES;

    static {
        // this prevent values to be assigned with the wrong order
        // and ensure valueOf to work fine
        final MqttMessageType[] values = values();
        VALUES = new MqttMessageType[values.length + 1];
        for (MqttMessageType mqttMessageType : values) {
            final int value = mqttMessageType.value;
            if (VALUES[value] != null) {
                throw new AssertionError("value already in use: " + value);
            }
            VALUES[value] = mqttMessageType;
        }
    }

    private final int value;

    MqttMessageType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static MqttMessageType valueOf(int type) {
        if (type <= 0 || type >= VALUES.length) {
            throw new IllegalArgumentException("unknown message type: " + type);
        }
        return VALUES[type];
    }
}

