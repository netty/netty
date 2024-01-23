/*
 * Copyright 2023 The Netty Project
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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static io.netty.handler.codec.mqtt.MqttReasonCodes.Disconnect;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.Auth;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.PubAck;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.PubRec;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.PubRel;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.PubComp;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.SubAck;
import static io.netty.handler.codec.mqtt.MqttReasonCodes.UnsubAck;
import static org.junit.jupiter.api.Assertions.assertEquals;


class MqttReasonCodesTest {

    @Test
    public void givenADisconnectReasonCodeTheCorrectEnumerationValueIsReturned() {
        assertEquals(Disconnect.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED, Disconnect.valueOf((byte) 0xA2),
                "0xA2 must match 'wildcard subscriptions not supported'");
    }

    @Test
    public void testDisconnectReasonCodesCorrectlyMapToConstant() {
        for (Disconnect reasonCode : Disconnect.values()) {
            assertEquals(reasonCode, Disconnect.valueOf((byte) (reasonCode.byteValue() & 0xFF)),
                    "Disconnect hex doesn't match the proper constant");
        }
    }

    @Test
    public void testAuthReasonCodesCorrectlyMapToConstant() {
        for (Auth reasonCode : Auth.values()) {
            assertEquals(reasonCode, Auth.valueOf((byte) (reasonCode.byteValue() & 0xFF)),
                    "Auth hex doesn't match the proper constant");
        }
    }

    @Test
    public void testPubAckReasonCodesCorrectlyMapToConstant() {
        for (PubAck reasonCode : PubAck.values()) {
            assertEquals(reasonCode, PubAck.valueOf((byte) (reasonCode.byteValue() & 0xFF)),
                    "PubAck hex doesn't match the proper constant");
        }
    }

    @Test
    public void testPubRecReasonCodesCorrectlyMapToConstant() {
        for (PubRec reasonCode : PubRec.values()) {
            assertEquals(reasonCode, PubRec.valueOf((byte) (reasonCode.byteValue() & 0xFF)),
                    "PubRec hex doesn't match the proper constant");
        }
    }

    @Test
    public void testPubRelReasonCodesCorrectlyMapToConstant() {
        for (PubRel reasonCode : PubRel.values()) {
            assertEquals(reasonCode, PubRel.valueOf((byte) (reasonCode.byteValue() & 0xFF)),
                    "PubRel hex doesn't match the proper constant");
        }
    }

    @Test
    public void testPubCompReasonCodesCorrectlyMapToConstant() {
        for (PubComp reasonCode : PubComp.values()) {
            assertEquals(reasonCode, PubComp.valueOf((byte) (reasonCode.byteValue() & 0xFF)),
                    "PubComp hex doesn't match the proper constant");
        }
    }

    @Test
    public void testSubAckReasonCodesCorrectlyMapToConstant() {
        for (SubAck reasonCode : SubAck.values()) {
            assertEquals(reasonCode, SubAck.valueOf((byte) (reasonCode.byteValue() & 0xFF)),
                    "SubAck hex doesn't match the proper constant");
        }
    }

    @Test
    public void testUnsubAckReasonCodesCorrectlyMapToConstant() {
        for (UnsubAck reasonCode : UnsubAck.values()) {
            assertEquals(reasonCode, UnsubAck.valueOf((byte) (reasonCode.byteValue() & 0xFF)),
                    "UnsubAck hex doesn't match the proper constant");
        }
    }
}
