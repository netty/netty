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

import io.netty.util.internal.StringUtil;

/**
 * Variable Header containing only Message Id
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#msg-id">MQTTV3.1/msg-id</a>
 */
public class MqttMessageIdVariableHeader {

    private final int messageId;

    public static MqttMessageIdVariableHeader from(int messageId) {
      if (messageId < 1 || messageId > 0xffff) {
        throw new IllegalArgumentException("messageId: " + messageId + " (expected: 1 ~ 65535)");
      }
      return new MqttMessageIdVariableHeader(messageId);
    }

    protected MqttMessageIdVariableHeader(int messageId) {
        this.messageId = messageId;
    }

    public int messageId() {
        return messageId;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
            .append('[')
            .append("messageId=").append(messageId)
            .append(']')
            .toString();
    }

    public MqttMessageIdAndPropertiesVariableHeader withEmptyProperties() {
        return new MqttMessageIdAndPropertiesVariableHeader(messageId, MqttProperties.NO_PROPERTIES);
    }

    MqttMessageIdAndPropertiesVariableHeader withDefaultEmptyProperties() {
        return withEmptyProperties();
    }
}
