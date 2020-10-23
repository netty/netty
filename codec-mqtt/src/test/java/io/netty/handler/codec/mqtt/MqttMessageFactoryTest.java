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

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static io.netty.handler.codec.mqtt.MqttTestUtils.validateProperties;
import static io.netty.handler.codec.mqtt.MqttTestUtils.validateSubscribePayload;
import static io.netty.handler.codec.mqtt.MqttTestUtils.validateUnsubscribePayload;

public class MqttMessageFactoryTest {
    private static final String SAMPLE_TOPIC = "a/b/c";
    private static final int SAMPLE_MESSAGE_ID = 123;

    @Test
    public void createUnsubAckV3() {
        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader =
                MqttMessageIdVariableHeader.from(SAMPLE_MESSAGE_ID);

        MqttMessage unsuback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

        assertEquals("Message type mismatch", MqttMessageType.UNSUBACK, unsuback.fixedHeader().messageType());
        MqttMessageIdAndPropertiesVariableHeader actualVariableHeader =
                (MqttMessageIdAndPropertiesVariableHeader) unsuback.variableHeader();
        assertEquals("MessageId mismatch", SAMPLE_MESSAGE_ID, actualVariableHeader.messageId());
        validateProperties(MqttProperties.NO_PROPERTIES, actualVariableHeader.properties());
        MqttUnsubAckPayload actualPayload = (MqttUnsubAckPayload) unsuback.payload();
        assertNotNull("payload", actualPayload);
        assertEquals("reason codes size", 0, actualPayload.unsubscribeReasonCodes().size());
    }

    @Test
    public void createUnsubAckV5() {
        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttProperties properties = new MqttProperties();
        String reasonString = "All right";
        properties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.REASON_STRING.value(),
                reasonString));
        MqttMessageIdAndPropertiesVariableHeader variableHeader =
                new MqttMessageIdAndPropertiesVariableHeader(SAMPLE_MESSAGE_ID, properties);
        MqttUnsubAckPayload payload = new MqttUnsubAckPayload((short) 0x80 /*unspecified error*/);

        MqttMessage unsuback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

        assertEquals("Message type mismatch", MqttMessageType.UNSUBACK, unsuback.fixedHeader().messageType());
        MqttMessageIdAndPropertiesVariableHeader actualVariableHeader =
                (MqttMessageIdAndPropertiesVariableHeader) unsuback.variableHeader();
        assertEquals("MessageId mismatch", SAMPLE_MESSAGE_ID, actualVariableHeader.messageId());
        validateProperties(properties, actualVariableHeader.properties());
        MqttUnsubAckPayload actualPayload = (MqttUnsubAckPayload) unsuback.payload();
        assertEquals("Reason code list doesn't match",
                payload.unsubscribeReasonCodes(),
                actualPayload.unsubscribeReasonCodes());
    }

    @Test
    public void createSubscribeV3() {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, AT_LEAST_ONCE, false, 0);

        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(SAMPLE_MESSAGE_ID);
        List<MqttTopicSubscription> subscriptions = new ArrayList<MqttTopicSubscription>();
        subscriptions.add(new MqttTopicSubscription(SAMPLE_TOPIC, MqttQoS.AT_MOST_ONCE));

        MqttSubscribePayload payload = new MqttSubscribePayload(subscriptions);

        MqttMessage subscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

        assertEquals("Message type mismatch", MqttMessageType.SUBSCRIBE, subscribe.fixedHeader().messageType());
        MqttMessageIdAndPropertiesVariableHeader actualVariableHeader =
                (MqttMessageIdAndPropertiesVariableHeader) subscribe.variableHeader();
        assertEquals("MessageId mismatch", SAMPLE_MESSAGE_ID, actualVariableHeader.messageId());
        validateProperties(MqttProperties.NO_PROPERTIES, actualVariableHeader.properties());
        MqttSubscribePayload actualPayload = (MqttSubscribePayload) subscribe.payload();
        validateSubscribePayload(payload, actualPayload);
    }

    @Test
    public void createSubscribeV5() {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, AT_LEAST_ONCE, false, 0);

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.UserProperty("correlationId", "111222"));
        MqttMessageIdAndPropertiesVariableHeader variableHeader =
                new MqttMessageIdAndPropertiesVariableHeader(SAMPLE_MESSAGE_ID, properties);
        List<MqttTopicSubscription> subscriptions = new ArrayList<MqttTopicSubscription>();
        subscriptions.add(new MqttTopicSubscription(SAMPLE_TOPIC, MqttQoS.AT_MOST_ONCE));

        MqttSubscribePayload payload = new MqttSubscribePayload(subscriptions);

        MqttMessage subscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

        assertEquals("Message type mismatch", MqttMessageType.SUBSCRIBE, subscribe.fixedHeader().messageType());
        MqttMessageIdAndPropertiesVariableHeader actualVariableHeader =
                (MqttMessageIdAndPropertiesVariableHeader) subscribe.variableHeader();
        assertEquals("MessageId mismatch", SAMPLE_MESSAGE_ID, actualVariableHeader.messageId());
        validateProperties(properties, actualVariableHeader.properties());
        MqttSubscribePayload actualPayload = (MqttSubscribePayload) subscribe.payload();
        validateSubscribePayload(payload, actualPayload);
    }

    @Test
    public void createUnsubscribeV3() {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, AT_LEAST_ONCE, false, 0);

        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(SAMPLE_MESSAGE_ID);

        List<String> topics = new ArrayList<String>();
        topics.add(SAMPLE_TOPIC);
        MqttUnsubscribePayload payload = new MqttUnsubscribePayload(topics);

        MqttMessage unsubscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

        assertEquals("Message type mismatch", MqttMessageType.UNSUBSCRIBE, unsubscribe.fixedHeader().messageType());
        MqttMessageIdAndPropertiesVariableHeader actualVariableHeader =
                (MqttMessageIdAndPropertiesVariableHeader) unsubscribe.variableHeader();
        assertEquals("MessageId mismatch", SAMPLE_MESSAGE_ID, actualVariableHeader.messageId());
        validateProperties(MqttProperties.NO_PROPERTIES, actualVariableHeader.properties());
        MqttUnsubscribePayload actualPayload = (MqttUnsubscribePayload) unsubscribe.payload();
        validateUnsubscribePayload(payload, actualPayload);
    }

    @Test
    public void createUnsubscribeV5() {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, AT_LEAST_ONCE, false, 0);

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.UserProperty("correlationId", "111222"));
        MqttMessageIdAndPropertiesVariableHeader variableHeader =
                new MqttMessageIdAndPropertiesVariableHeader(SAMPLE_MESSAGE_ID, properties);

        List<String> topics = new ArrayList<String>();
        topics.add(SAMPLE_TOPIC);
        MqttUnsubscribePayload payload = new MqttUnsubscribePayload(topics);

        MqttMessage unsubscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

        assertEquals("Message type mismatch", MqttMessageType.UNSUBSCRIBE, unsubscribe.fixedHeader().messageType());
        MqttMessageIdAndPropertiesVariableHeader actualVariableHeader =
                (MqttMessageIdAndPropertiesVariableHeader) unsubscribe.variableHeader();
        assertEquals("MessageId mismatch", SAMPLE_MESSAGE_ID, actualVariableHeader.messageId());
        validateProperties(properties, actualVariableHeader.properties());
        MqttUnsubscribePayload actualPayload = (MqttUnsubscribePayload) unsubscribe.payload();
        validateUnsubscribePayload(payload, actualPayload);
    }
}
