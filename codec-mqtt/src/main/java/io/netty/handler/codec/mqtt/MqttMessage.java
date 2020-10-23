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

import io.netty.handler.codec.DecoderResult;
import io.netty.util.internal.StringUtil;

/**
 * Base class for all MQTT message types.
 */
public class MqttMessage {

    private final MqttFixedHeader mqttFixedHeader;
    private final Object variableHeader;
    private final Object payload;
    private final DecoderResult decoderResult;

    // Constants for fixed-header only message types with all flags set to 0 (see
    // https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Table_2.2_-)
    public static final MqttMessage PINGREQ = new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGREQ, false,
            MqttQoS.AT_MOST_ONCE, false, 0));

    public static final MqttMessage PINGRESP = new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGRESP, false,
            MqttQoS.AT_MOST_ONCE, false, 0));

    public static final MqttMessage DISCONNECT = new MqttMessage(new MqttFixedHeader(MqttMessageType.DISCONNECT, false,
            MqttQoS.AT_MOST_ONCE, false, 0));

    public MqttMessage(MqttFixedHeader mqttFixedHeader) {
        this(mqttFixedHeader, null, null);
    }

    public MqttMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader) {
        this(mqttFixedHeader, variableHeader, null);
    }

    public MqttMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Object payload) {
        this(mqttFixedHeader, variableHeader, payload, DecoderResult.SUCCESS);
    }

    public MqttMessage(
            MqttFixedHeader mqttFixedHeader,
            Object variableHeader,
            Object payload,
            DecoderResult decoderResult) {
        this.mqttFixedHeader = mqttFixedHeader;
        this.variableHeader = variableHeader;
        this.payload = payload;
        this.decoderResult = decoderResult;
    }

    public MqttFixedHeader fixedHeader() {
        return mqttFixedHeader;
    }

    public Object variableHeader() {
        return variableHeader;
    }

    public Object payload() {
        return payload;
    }

    public DecoderResult decoderResult() {
        return decoderResult;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
            .append('[')
            .append("fixedHeader=").append(fixedHeader() != null ? fixedHeader().toString() : "")
            .append(", variableHeader=").append(variableHeader() != null ? variableHeader.toString() : "")
            .append(", payload=").append(payload() != null ? payload.toString() : "")
            .append(']')
            .toString();
    }
}
