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
