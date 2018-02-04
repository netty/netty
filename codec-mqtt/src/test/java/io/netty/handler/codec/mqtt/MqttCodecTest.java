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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MqttEncoder and MqttDecoder.
 */
public class MqttCodecTest {

    private static final String CLIENT_ID = "RANDOM_TEST_CLIENT";
    private static final String WILL_TOPIC = "/my_will";
    private static final String WILL_MESSAGE = "gone";
    private static final String USER_NAME = "happy_user";
    private static final String PASSWORD = "123_or_no_pwd";

    private static final int KEEP_ALIVE_SECONDS = 600;

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    @Mock
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

    @Mock
    private final Channel channel = mock(Channel.class);

    private final MqttDecoder mqttDecoder = new MqttDecoder();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    public void testConnectMessageForMqtt31() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1);
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnectMessage decodedMessage = (MqttConnectMessage) out.get(0);

        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnectVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateConnectPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testConnectMessageForMqtt311() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1_1);
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnectMessage decodedMessage = (MqttConnectMessage) out.get(0);

        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnectVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateConnectPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testConnectMessageWithNonZeroReservedFlagForMqtt311() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1_1);
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);
        try {
            // Set the reserved flag in the CONNECT Packet to 1
            byteBuf.setByte(9, byteBuf.getByte(9) | 0x1);
            final List<Object> out = new LinkedList<Object>();
            mqttDecoder.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);
            assertTrue(decodedMessage.decoderResult().isFailure());
            Throwable cause = decodedMessage.decoderResult().cause();
            assertTrue(cause instanceof DecoderException);
            assertEquals("non-zero reserved flag", cause.getMessage());
        } finally {
            byteBuf.release();
        }
    }

    @Test
    public void testConnectMessageNoPassword() throws Exception {
        final MqttConnectMessage message = createConnectMessage(MqttVersion.MQTT_3_1_1, null, PASSWORD);

        try {
            ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);
        } catch (Exception cause) {
            assertTrue(cause instanceof DecoderException);
        }
    }

    @Test
    public void testConnAckMessage() throws Exception {
        final MqttConnAckMessage message = createConnAckMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnAckMessage decodedMessage = (MqttConnAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnAckVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
    }

    @Test
    public void testPublishMessage() throws Exception {
        final MqttPublishMessage message = createPublishMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttPublishMessage decodedMessage = (MqttPublishMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePublishVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validatePublishPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testPubAckMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.PUBACK);
    }

    @Test
    public void testPubRecMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.PUBREC);
    }

    @Test
    public void testPubRelMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.PUBREL);
    }

    @Test
    public void testPubCompMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.PUBCOMP);
    }

    @Test
    public void testSubscribeMessage() throws Exception {
        final MqttSubscribeMessage message = createSubscribeMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttSubscribeMessage decodedMessage = (MqttSubscribeMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateSubscribePayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testSubAckMessage() throws Exception {
        final MqttSubAckMessage message = createSubAckMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttSubAckMessage decodedMessage = (MqttSubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateSubAckPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testSubAckMessageWithFailureInPayload() throws Exception {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);
        MqttSubAckPayload mqttSubAckPayload = new MqttSubAckPayload(MqttQoS.FAILURE.value());
        MqttSubAckMessage message =
                new MqttSubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubAckPayload);

        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        MqttSubAckMessage decodedMessage = (MqttSubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateSubAckPayload(message.payload(), decodedMessage.payload());
        assertEquals(1, decodedMessage.payload().grantedQoSLevels().size());
        assertEquals(MqttQoS.FAILURE, MqttQoS.valueOf(decodedMessage.payload().grantedQoSLevels().get(0)));
    }

    @Test
    public void testUnSubscribeMessage() throws Exception {
        final MqttUnsubscribeMessage message = createUnsubscribeMessage();
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttUnsubscribeMessage decodedMessage = (MqttUnsubscribeMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateUnsubscribePayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testUnsubAckMessage() throws Exception {
        testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType.UNSUBACK);
    }

    @Test
    public void testPingReqMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MqttMessageType.PINGREQ);
    }

    @Test
    public void testPingRespMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MqttMessageType.PINGRESP);
    }

    @Test
    public void testDisconnectMessage() throws Exception {
        testMessageWithOnlyFixedHeader(MqttMessageType.DISCONNECT);
    }

    @Test
    public void testUnknownMessageType() throws Exception {

        final MqttMessage message = createMessageWithFixedHeader(MqttMessageType.PINGREQ);
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);
        try {
            // setting an invalid message type (15, reserved and forbidden by MQTT 3.1.1 spec)
            byteBuf.setByte(0, 0xF0);
            final List<Object> out = new LinkedList<Object>();
            mqttDecoder.decode(ctx, byteBuf, out);

            assertEquals("Expected one object but got " + out.size(), 1, out.size());

            final MqttMessage decodedMessage = (MqttMessage) out.get(0);
            assertTrue(decodedMessage.decoderResult().isFailure());
            Throwable cause = decodedMessage.decoderResult().cause();
            assertTrue(cause instanceof IllegalArgumentException);
            assertEquals("unknown message type: 15", cause.getMessage());
        } finally {
            byteBuf.release();
        }
    }

    private void testMessageWithOnlyFixedHeader(MqttMessageType messageType) throws Exception {
        MqttMessage message = createMessageWithFixedHeader(messageType);
        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
    }

    private void testMessageWithOnlyFixedHeaderAndMessageIdVariableHeader(MqttMessageType messageType)
            throws Exception {
        MqttMessage message = createMessageWithFixedHeaderAndMessageIdVariableHeader(messageType);

        ByteBuf byteBuf = MqttEncoder.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateMessageIdVariableHeader(
                (MqttMessageIdVariableHeader) message.variableHeader(),
                (MqttMessageIdVariableHeader) decodedMessage.variableHeader());
    }

    // Factory methods of different MQTT
    // Message types to help testing

    private static MqttMessage createMessageWithFixedHeader(MqttMessageType messageType) {
        return new MqttMessage(new MqttFixedHeader(messageType, false, MqttQoS.AT_MOST_ONCE, false, 0));
    }

    private static MqttMessage createMessageWithFixedHeaderAndMessageIdVariableHeader(MqttMessageType messageType) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(
                        messageType,
                        false,
                        messageType == MqttMessageType.PUBREL ? MqttQoS.AT_LEAST_ONCE :  MqttQoS.AT_MOST_ONCE,
                        false,
                        0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);
        return new MqttMessage(mqttFixedHeader, mqttMessageIdVariableHeader);
    }

    private static MqttConnectMessage createConnectMessage(MqttVersion mqttVersion) {
        return createConnectMessage(mqttVersion, USER_NAME, PASSWORD);
    }

    private static MqttConnectMessage createConnectMessage(MqttVersion mqttVersion, String username, String password) {
        return MqttMessageBuilders.connect()
                .clientId(CLIENT_ID)
                .protocolVersion(mqttVersion)
                .username(username)
                .password(password)
                .willRetain(true)
                .willQoS(MqttQoS.AT_LEAST_ONCE)
                .willFlag(true)
                .willTopic(WILL_TOPIC)
                .willMessage(WILL_MESSAGE)
                .cleanSession(true)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .build();
    }

    private static MqttConnAckMessage createConnAckMessage() {
        return MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(true)
                .build();
    }

    private static MqttPublishMessage createPublishMessage() {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE, true, 0);
        MqttPublishVariableHeader mqttPublishVariableHeader = new MqttPublishVariableHeader("/abc", 1234);
        ByteBuf payload =  ALLOCATOR.buffer();
        payload.writeBytes("whatever".getBytes(CharsetUtil.UTF_8));
        return new MqttPublishMessage(mqttFixedHeader, mqttPublishVariableHeader, payload);
    }

    private static MqttSubscribeMessage createSubscribeMessage() {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, true, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);

        List<MqttTopicSubscription> topicSubscriptions = new LinkedList<MqttTopicSubscription>();
        topicSubscriptions.add(new MqttTopicSubscription("/abc", MqttQoS.AT_LEAST_ONCE));
        topicSubscriptions.add(new MqttTopicSubscription("/def", MqttQoS.AT_LEAST_ONCE));
        topicSubscriptions.add(new MqttTopicSubscription("/xyz", MqttQoS.EXACTLY_ONCE));

        MqttSubscribePayload mqttSubscribePayload = new MqttSubscribePayload(topicSubscriptions);
        return new MqttSubscribeMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubscribePayload);
    }

    private static MqttSubAckMessage createSubAckMessage() {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);
        MqttSubAckPayload mqttSubAckPayload = new MqttSubAckPayload(1, 2, 0);
        return new MqttSubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubAckPayload);
    }

    private static MqttUnsubscribeMessage createUnsubscribeMessage() {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, true, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(12345);

        List<String> topics = new LinkedList<String>();
        topics.add("/abc");
        topics.add("/def");
        topics.add("/xyz");

        MqttUnsubscribePayload mqttUnsubscribePayload = new MqttUnsubscribePayload(topics);
        return new MqttUnsubscribeMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttUnsubscribePayload);
    }

    // Helper methods to compare expected and actual
    // MQTT messages

    private static void validateFixedHeaders(MqttFixedHeader expected, MqttFixedHeader actual) {
        assertEquals("MqttFixedHeader MqttMessageType mismatch ", expected.messageType(), actual.messageType());
        assertEquals("MqttFixedHeader Qos mismatch ", expected.qosLevel(), actual.qosLevel());
    }

    private static void validateConnectVariableHeader(
            MqttConnectVariableHeader expected,
            MqttConnectVariableHeader actual) {
        assertEquals("MqttConnectVariableHeader Name mismatch ", expected.name(), actual.name());
        assertEquals(
                "MqttConnectVariableHeader KeepAliveTimeSeconds mismatch ",
                expected.keepAliveTimeSeconds(),
                actual.keepAliveTimeSeconds());
        assertEquals("MqttConnectVariableHeader Version mismatch ", expected.version(), actual.version());
        assertEquals("MqttConnectVariableHeader WillQos mismatch ", expected.willQos(), actual.willQos());

        assertEquals("MqttConnectVariableHeader HasUserName mismatch ", expected.hasUserName(), actual.hasUserName());
        assertEquals("MqttConnectVariableHeader HasPassword mismatch ", expected.hasPassword(), actual.hasPassword());
        assertEquals(
                "MqttConnectVariableHeader IsCleanSession mismatch ",
                expected.isCleanSession(),
                actual.isCleanSession());
        assertEquals("MqttConnectVariableHeader IsWillFlag mismatch ", expected.isWillFlag(), actual.isWillFlag());
        assertEquals(
                "MqttConnectVariableHeader IsWillRetain mismatch ",
                expected.isWillRetain(),
                actual.isWillRetain());
    }

    private static void validateConnectPayload(MqttConnectPayload expected, MqttConnectPayload actual) {
        assertEquals(
                "MqttConnectPayload ClientIdentifier mismatch ",
                expected.clientIdentifier(),
                actual.clientIdentifier());
        assertEquals("MqttConnectPayload UserName mismatch ", expected.userName(), actual.userName());
        assertEquals("MqttConnectPayload Password mismatch ", expected.password(), actual.password());
        assertTrue(
                "MqttConnectPayload Password bytes mismatch ",
                Arrays.equals(expected.passwordInBytes(), actual.passwordInBytes()));
        assertEquals("MqttConnectPayload WillMessage mismatch ", expected.willMessage(), actual.willMessage());
        assertTrue(
                "MqttConnectPayload WillMessage bytes mismatch ",
                Arrays.equals(expected.willMessageInBytes(), actual.willMessageInBytes()));
        assertEquals("MqttConnectPayload WillTopic mismatch ", expected.willTopic(), actual.willTopic());
    }

    private static void validateConnAckVariableHeader(
            MqttConnAckVariableHeader expected,
            MqttConnAckVariableHeader actual) {
        assertEquals(
                "MqttConnAckVariableHeader MqttConnectReturnCode mismatch",
                expected.connectReturnCode(),
                actual.connectReturnCode());
    }

    private static void validatePublishVariableHeader(
            MqttPublishVariableHeader expected,
            MqttPublishVariableHeader actual) {
        assertEquals("MqttPublishVariableHeader TopicName mismatch ", expected.topicName(), actual.topicName());
        assertEquals("MqttPublishVariableHeader MessageId mismatch ", expected.messageId(), actual.messageId());
    }

    private static void validatePublishPayload(ByteBuf expected, ByteBuf actual) {
        assertEquals("PublishPayload mismatch ", 0, expected.compareTo(actual));
    }

    private static void validateMessageIdVariableHeader(
            MqttMessageIdVariableHeader expected,
            MqttMessageIdVariableHeader actual) {
        assertEquals("MqttMessageIdVariableHeader MessageId mismatch ", expected.messageId(), actual.messageId());
    }

    private static void validateSubscribePayload(MqttSubscribePayload expected, MqttSubscribePayload actual) {
        List<MqttTopicSubscription> expectedTopicSubscriptions = expected.topicSubscriptions();
        List<MqttTopicSubscription> actualTopicSubscriptions = actual.topicSubscriptions();

        assertEquals(
                "MqttSubscribePayload TopicSubscriptionList size mismatch ",
                expectedTopicSubscriptions.size(),
                actualTopicSubscriptions.size());
        for (int i = 0; i < expectedTopicSubscriptions.size(); i++) {
            validateTopicSubscription(expectedTopicSubscriptions.get(i), actualTopicSubscriptions.get(i));
        }
    }

    private static void validateTopicSubscription(
            MqttTopicSubscription expected,
            MqttTopicSubscription actual) {
        assertEquals("MqttTopicSubscription TopicName mismatch ", expected.topicName(), actual.topicName());
        assertEquals(
                "MqttTopicSubscription Qos mismatch ",
                expected.qualityOfService(),
                actual.qualityOfService());
    }

    private static void validateSubAckPayload(MqttSubAckPayload expected, MqttSubAckPayload actual) {
        assertArrayEquals(
                "MqttSubAckPayload GrantedQosLevels mismatch ",
                expected.grantedQoSLevels().toArray(),
                actual.grantedQoSLevels().toArray());
    }

    private static void validateUnsubscribePayload(MqttUnsubscribePayload expected, MqttUnsubscribePayload actual) {
        assertArrayEquals(
                "MqttUnsubscribePayload TopicList mismatch ",
                expected.topics().toArray(),
                actual.topics().toArray());
    }
}
