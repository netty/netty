/*
 * Copyright 2014 The Netty Project
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

/**
 * See <a href="http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#unsuback">MQTTV3.1/unsuback</a>
 */
public final class MqttUnsubAckMessage extends MqttMessage {

    public MqttUnsubAckMessage(MqttFixedHeader mqttFixedHeader,
                               MqttMessageIdAndPropertiesVariableHeader variableHeader,
                               MqttUnsubAckPayload payload) {
        super(mqttFixedHeader, variableHeader, MqttUnsubAckPayload.withEmptyDefaults(payload));
    }

    public MqttUnsubAckMessage(MqttFixedHeader mqttFixedHeader,
                               MqttMessageIdVariableHeader variableHeader,
                               MqttUnsubAckPayload payload) {
        this(mqttFixedHeader, fallbackVariableHeader(variableHeader), payload);
    }
    public MqttUnsubAckMessage(MqttFixedHeader mqttFixedHeader,
                               MqttMessageIdVariableHeader variableHeader) {
        this(mqttFixedHeader, variableHeader, null);
    }

    private static MqttMessageIdAndPropertiesVariableHeader fallbackVariableHeader(
            MqttMessageIdVariableHeader variableHeader) {
        if (variableHeader instanceof MqttMessageIdAndPropertiesVariableHeader) {
            return (MqttMessageIdAndPropertiesVariableHeader) variableHeader;
        }
        return new MqttMessageIdAndPropertiesVariableHeader(variableHeader.messageId(), MqttProperties.NO_PROPERTIES);
    }

    @Override
    public MqttMessageIdVariableHeader variableHeader() {
        return (MqttMessageIdVariableHeader) super.variableHeader();
    }

    public MqttMessageIdAndPropertiesVariableHeader idAndPropertiesVariableHeader() {
        return (MqttMessageIdAndPropertiesVariableHeader) super.variableHeader();
    }

    @Override
    public MqttUnsubAckPayload payload() {
        return (MqttUnsubAckPayload) super.payload();
    }
}
