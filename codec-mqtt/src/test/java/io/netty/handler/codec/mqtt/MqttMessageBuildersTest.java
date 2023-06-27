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

import io.netty.handler.codec.mqtt.MqttMessageBuilders.PropertiesInitializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MqttMessageBuildersTest {

    @Test
    public void testConnAckWithProperties() {
        final MqttConnAckMessage ackMsg = MqttMessageBuilders.connAck()
                .properties(new PropertiesInitializer<MqttMessageBuilders.ConnAckPropertiesBuilder>() {
            @Override
            public void apply(MqttMessageBuilders.ConnAckPropertiesBuilder builder) {
                builder.assignedClientId("client1234");
                builder.userProperty("custom_property", "value");
            }
        }).build();

        final String clientId = (String) ackMsg.variableHeader()
                .properties()
                .getProperty(MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER.value())
                .value();

        assertEquals("client1234", clientId);
    }

}
