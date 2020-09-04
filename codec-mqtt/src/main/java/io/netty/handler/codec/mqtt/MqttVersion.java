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

import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;

/**
 * Mqtt version specific constant values used by multiple classes in mqtt-codec.
 */
public enum MqttVersion {
    MQTT_3_1("MQIsdp", (byte) 3),
    MQTT_3_1_1("MQTT", (byte) 4),
    MQTT_5("MQTT", (byte) 5);

    private final String name;
    private final byte level;

    MqttVersion(String protocolName, byte protocolLevel) {
        name = ObjectUtil.checkNotNull(protocolName, "protocolName");
        level = protocolLevel;
    }

    public String protocolName() {
        return name;
    }

    public byte[] protocolNameBytes() {
        return name.getBytes(CharsetUtil.UTF_8);
    }

    public byte protocolLevel() {
        return level;
    }

    public static MqttVersion fromProtocolNameAndLevel(String protocolName, byte protocolLevel) {
        MqttVersion mv = null;
        switch (protocolLevel) {
        case 3:
            mv = MQTT_3_1;
            break;
        case 4:
            mv = MQTT_3_1_1;
            break;
        case 5:
            mv = MQTT_5;
            break;
        }
        if (mv == null) {
            throw new MqttUnacceptableProtocolVersionException(protocolName + "is unknown protocol name");
        }
        if (mv.name.equals(protocolName)) {
            return mv;
        }
        throw new MqttUnacceptableProtocolVersionException(protocolName + " and " + protocolLevel + " are not match");
    }
}
