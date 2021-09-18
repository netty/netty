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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MqttMessageBuildersPacketIdTest {

    static Iterable<Integer> data() {
        // we take a subset of valid packetIds
        return Arrays.asList(
                0x0001,
                0x000F,
                0x00FF,
                0x0FFF,
                0xFFFF
        );
    }

    @ParameterizedTest()
    @MethodSource("data")
    public void testUnsubAckMessageIdAsShort(Integer id) {
        final MqttUnsubAckMessage msg = MqttMessageBuilders.unsubAck()
                .packetId(id.shortValue())
                .build();

        assertEquals(
                id.intValue(),
                msg.variableHeader().messageId()
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSubAckMessageIdAsShort(Integer id) {
        final MqttSubAckMessage msg = MqttMessageBuilders.subAck()
                .packetId(id.shortValue())
                .build();

        assertEquals(
                id.intValue(),
                msg.variableHeader().messageId()
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testPubAckMessageIdAsShort(Integer id) {
        final MqttMessage msg = MqttMessageBuilders.pubAck()
                .packetId(id.shortValue())
                .build();

        assertEquals(
                id.intValue(),
                ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId()
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testUnsubAckMessageIdAsInt(Integer id) {
        final MqttUnsubAckMessage msg = MqttMessageBuilders.unsubAck()
                .packetId(id)
                .build();

        assertEquals(
                id.intValue(),
                msg.variableHeader().messageId()
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSubAckMessageIdAsInt(Integer id) {
        final MqttSubAckMessage msg = MqttMessageBuilders.subAck()
                .packetId(id)
                .build();

        assertEquals(
                id.intValue(),
                msg.variableHeader().messageId()
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testPubAckMessageIdAsInt(Integer id) {
        final MqttMessage msg = MqttMessageBuilders.pubAck()
                .packetId(id)
                .build();

        assertEquals(
                id.intValue(),
                ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId()
        );
    }
}
