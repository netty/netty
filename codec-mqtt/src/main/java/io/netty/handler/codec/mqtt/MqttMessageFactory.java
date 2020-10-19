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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;

/**
 * Utility class with factory methods to create different types of MQTT messages.
 */
public final class MqttMessageFactory {

    public static MqttMessage newMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Object payload) {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT :
                return new MqttConnectMessage(
                        mqttFixedHeader,
                        (MqttConnectVariableHeader) variableHeader,
                        (MqttConnectPayload) payload);

            case CONNACK:
                return new MqttConnAckMessage(mqttFixedHeader, (MqttConnAckVariableHeader) variableHeader);

            case SUBSCRIBE:
                return new MqttSubscribeMessage(
                        mqttFixedHeader,
                        (MqttMessageIdVariableHeader) variableHeader,
                        (MqttSubscribePayload) payload);

            case SUBACK:
                return new MqttSubAckMessage(
                        mqttFixedHeader,
                        (MqttMessageIdVariableHeader) variableHeader,
                        (MqttSubAckPayload) payload);

            case UNSUBACK:
                return new MqttUnsubAckMessage(
                        mqttFixedHeader,
                        (MqttMessageIdVariableHeader) variableHeader,
                        (MqttUnsubAckPayload) payload);

            case UNSUBSCRIBE:
                return new MqttUnsubscribeMessage(
                        mqttFixedHeader,
                        (MqttMessageIdVariableHeader) variableHeader,
                        (MqttUnsubscribePayload) payload);

            case PUBLISH:
                return new MqttPublishMessage(
                        mqttFixedHeader,
                        (MqttPublishVariableHeader) variableHeader,
                        (ByteBuf) payload);

            case PUBACK:
                //Having MqttPubReplyMessageVariableHeader or MqttMessageIdVariableHeader
                return new MqttPubAckMessage(mqttFixedHeader, (MqttMessageIdVariableHeader) variableHeader);
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                //Having MqttPubReplyMessageVariableHeader or MqttMessageIdVariableHeader
                return new MqttMessage(mqttFixedHeader, variableHeader);

            case PINGREQ:
            case PINGRESP:
                return new MqttMessage(mqttFixedHeader);

            case DISCONNECT:
            case AUTH:
                //Having MqttReasonCodeAndPropertiesVariableHeader
                return new MqttMessage(mqttFixedHeader,
                        (MqttReasonCodeAndPropertiesVariableHeader) variableHeader);

            default:
                throw new IllegalArgumentException("unknown message type: " + mqttFixedHeader.messageType());
        }
    }

    public static MqttMessage newInvalidMessage(Throwable cause) {
        return new MqttMessage(null, null, null, DecoderResult.failure(cause));
    }

    public static MqttMessage newInvalidMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader,
                                                Throwable cause) {
        return new MqttMessage(mqttFixedHeader, variableHeader, null, DecoderResult.failure(cause));
    }

    private MqttMessageFactory() { }
}
