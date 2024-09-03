/*
 * Copyright 2024 The Netty Project
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

package io.netty.testsuite_jpms.test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CodecMqttTest {

    private static final String CLIENT_ID = "RANDOM_TEST_CLIENT";
    private static final String WILL_TOPIC = "/my_will";
    private static final String WILL_MESSAGE = "gone";
    private static final String USER_NAME = "happy_user";
    private static final String PASSWORD = "123_or_no_pwd";
    private static final int KEEP_ALIVE_SECONDS = 600;

    @Test
    public void testCodec() {
        MqttConnectMessage msg = MqttMessageBuilders.connect()
                .clientId(CLIENT_ID)
                .protocolVersion(MqttVersion.MQTT_3_1)
                .username(USER_NAME)
                .password(PASSWORD.getBytes(CharsetUtil.UTF_8))
                .properties(MqttProperties.NO_PROPERTIES)
                .willRetain(true)
                .willQoS(MqttQoS.AT_LEAST_ONCE)
                .willFlag(true)
                .willTopic(WILL_TOPIC)
                .willMessage(WILL_MESSAGE.getBytes(CharsetUtil.UTF_8))
                .willProperties(MqttProperties.NO_PROPERTIES)
                .cleanSession(true)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .build();
        EmbeddedChannel channel = new EmbeddedChannel(MqttEncoder.INSTANCE, new MqttDecoder());
        assertTrue(channel.writeOutbound(msg));
        ByteBuf buffer = channel.readOutbound();
        assertNull(channel.readOutbound());
        channel.writeInbound(buffer);
        Object o = channel.readInbound();
        assertEquals(MqttConnectMessage.class, o.getClass());
        msg = (MqttConnectMessage) o;
        MqttConnectVariableHeader varHeaders = msg.variableHeader();
        assertEquals(MqttQoS.AT_LEAST_ONCE.value(), varHeaders.willQos());
        assertEquals(KEEP_ALIVE_SECONDS, varHeaders.keepAliveTimeSeconds());
        MqttConnectPayload payload = msg.payload();
        assertEquals(CLIENT_ID, payload.clientIdentifier());
        assertEquals(USER_NAME, payload.userName());
        assertEquals(PASSWORD, payload.password());
        assertEquals(WILL_TOPIC, payload.willTopic());
        assertEquals(WILL_MESSAGE, payload.willMessage());
        assertFalse(channel.finish());
    }
}
