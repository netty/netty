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

import io.netty.util.internal.StringUtil;

/**
 * Variable Header for AUTH and DISCONNECT messages represented by {@link MqttMessage}
 */
public final class MqttReasonCodeAndPropertiesVariableHeader {

    private final byte reasonCode;
    private final MqttProperties properties;

    public static final byte REASON_CODE_OK = 0;

    public MqttReasonCodeAndPropertiesVariableHeader(byte reasonCode,
                                                     MqttProperties properties) {
        this.reasonCode = reasonCode;
        this.properties = MqttProperties.withEmptyDefaults(properties);
    }

    public byte reasonCode() {
        return reasonCode;
    }

    public MqttProperties properties() {
        return properties;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
            .append('[')
            .append("reasonCode=").append(reasonCode)
            .append(", properties=").append(properties)
            .append(']')
            .toString();
    }
}
