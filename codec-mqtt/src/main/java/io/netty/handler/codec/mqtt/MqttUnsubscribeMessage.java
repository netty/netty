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
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#unsubscribe">
 *     MQTTV3.1/unsubscribe</a>
 */
public final class MqttUnsubscribeMessage extends MqttMessage {

    public MqttUnsubscribeMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttMessageIdAndPropertiesVariableHeader variableHeader,
            MqttUnsubscribePayload payload) {
        super(mqttFixedHeader, variableHeader, payload);
    }

    public MqttUnsubscribeMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttMessageIdVariableHeader variableHeader,
            MqttUnsubscribePayload payload) {
        this(mqttFixedHeader, variableHeader.withDefaultEmptyProperties(), payload);
    }

    @Override
    public MqttMessageIdVariableHeader variableHeader() {
        return (MqttMessageIdVariableHeader) super.variableHeader();
    }

    public MqttMessageIdAndPropertiesVariableHeader idAndPropertiesVariableHeader() {
        return (MqttMessageIdAndPropertiesVariableHeader) super.variableHeader();
    }

    @Override
    public MqttUnsubscribePayload payload() {
        return (MqttUnsubscribePayload) super.payload();
    }
}
