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
 * See <a href="http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#suback">MQTTV3.1/suback</a>
 */
public final class MqttSubAckMessage extends MqttMessage {

    public MqttSubAckMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttMessageIdAndPropertiesVariableHeader variableHeader,
            MqttSubAckPayload payload) {
        super(mqttFixedHeader, variableHeader, payload);
    }

    public MqttSubAckMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttMessageIdVariableHeader variableHeader,
            MqttSubAckPayload payload) {
        this(mqttFixedHeader, variableHeader.withEmptyProperties(), payload);
    }

    @Override
    public MqttMessageIdVariableHeader variableHeader() {
        return (MqttMessageIdVariableHeader) super.variableHeader();
    }

    public MqttMessageIdAndPropertiesVariableHeader idAndPropertiesVariableHeader() {
        return (MqttMessageIdAndPropertiesVariableHeader) super.variableHeader();
    }

    @Override
    public MqttSubAckPayload payload() {
        return (MqttSubAckPayload) super.payload();
    }
}
